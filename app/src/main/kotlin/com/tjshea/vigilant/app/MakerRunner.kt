package com.tjshea.vigilant.app

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Make orders in the app (Tj, 2026-10-03: "build the system in the app … It may need a separate section in the app"; RESEARCH.md §70): runs the
 * [MakerDesk] on Vigilant's latest scan. Called (suspending; the caller owns each pass) after every finished Vigilant scan and by every background
 * cycle ([AutoScanner.cycle]), and by the Make tab's buttons; the container takes every bid down the moment bids are switched off or scanning is
 * paused ([AppContainer] collects [ScanSettings.makerNow]). Each fill posts a notification and is in the Tracker as a bet ([TrackedBet.maker]).
 */
class MakerRunner(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Vigilant's latest scan (its result is what bids are judged on). */
    private val scan: () -> com.tjshea.vigilant.data.scanner.ScanRun = { c.runner.state.value },
    /** The desk on the Vigilant wallet; null when betting through the API isn't set up. */
    private val desk: () -> MakerDesk? = { c.makerDesk() },
    /** A Novig market's newest trades (public, one request): what the trap guard's move rule reads before a game line gets a bid ([withMoves]). */
    private val recentTrades: suspend (String) -> List<com.tjshea.vigilant.data.scanner.TrapGuard.Trade> =
        { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.novig.trades(id) } },
) {
    /** What the Make tab shows: the last pass and the bids each line would get now. */
    data class Status(
        val running: Boolean = false,
        val lastAtMs: Long? = null,
        val lastReport: MakerDesk.Report? = null,
        val problem: String? = null,
        val decisions: List<MakerDecision> = emptyList(),
        val decisionsAtMs: Long? = null,
        /** When the scan behind [decisions] was priced; null = no scan yet. */
        val scanAtMs: Long? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** One pass at a time (the desk's own order lock keeps orders apart from bets; this keeps two passes from judging the same lines). */
    private val passes = Mutex()

    /** Novig's newest trades per market (when read, the trades), for the trap guard's move rule on game-line bids ([withMoves]). */
    private val moveReads = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<com.tjshea.vigilant.data.scanner.TrapGuard.Trade>>>()

    /**
     * [lines] with what Novig's own trades say about each game-line side about to get a bid ([MakerRules.novigMove], [MakerLines.moveWanted], RESEARCH.md
     * §72): one public request per market, kept [MOVE_READ_MS], at most [MAX_MOVE_READS] a pass ([read] false: only what's kept, no request; the tab's
     * preview). A read that fails stops nothing (that line is judged without it). Game lines are off for bids by default, so by default this reads nothing.
     */
    private suspend fun withMoves(lines: List<com.tjshea.vigilant.data.novig.trading.maker.MakerLine>, rules: MakerRules, now: Long, read: Boolean):
        List<com.tjshea.vigilant.data.novig.trading.maker.MakerLine> {
        val wanted = MakerLines.moveWanted(lines, rules, now)
        if (wanted.isEmpty()) return lines
        moveReads.entries.removeIf { now - it.value.first > MOVE_READ_MS }
        if (read) {
            for (id in wanted.sortedBy { it.startsTs }.map { it.marketId }.distinct().filter { !moveReads.containsKey(it) }.take(MAX_MOVE_READS)) {
                val trades = try {
                    recentTrades(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    c.eventLog.count("maker.move.unread")
                    continue
                }
                moveReads[id] = now to trades
            }
        }
        return MakerLines.withMoves(lines, wanted, moveReads.mapValues { it.value.second }, now)
    }

    /**
     * One pass. Auto-make on: the bids Vigilant's latest scan wants are posted, moved and cancelled. Auto-make off: the bids Tj approved by hand are
     * watched (fills recorded, taken down when no longer worth it, never re-posted), and new bids are recommended to approve or deny
     * ([ScanSettings.makerRecommend]). Scanning paused: every bid comes down. No scan yet: only fills and expiries are read. A scan still running is
     * judged as far as it has got (its finished leagues; a bid on a line it hasn't judged yet stays up). Null when betting through the API isn't set
     * up. [why] is logged. [minGapMs]: skipped (null) when a pass started less than this long ago (the background cycle's, while a scan's own passes
     * run every [AppContainer.MAKER_SCAN_PASS_MS]: Tj's v0.53.0 file had both running, a pass every ~10 s).
     */
    suspend fun run(why: String, minGapMs: Long = 0L): MakerDesk.Report? = passes.withLock {
        if (minGapMs > 0) _status.value.lastAtMs?.let { last -> if (clock() - last < minGapMs) return@withLock null }
        val desk = desk() ?: return@withLock null
        val s = c.currentSettings()
        val any = desk.bids().any { it.active }
        if (!s.maker && !any) {
            val decisions = preview(s)
            if (s.makerRecommend && !s.paused && AppBook.isNovig) recommend(decisions)
            return@withLock null
        }
        val run = scan()
        val result = run.result
        _status.update { it.copy(running = true) }
        try {
            val now = clock()
            val stop = stopReason(s)
            // No scan yet in this process: only fills and expiries. A scan still running is judged as far as it got (Tj, 2026-10-03: "I had auto make
            // bids turned on, but it didn't actually make any bids by itself": a scan took 8 minutes and every pass waited for its end, by when the fair
            // prices it read first were going old): its finished leagues' lines are bid on, and a bid whose line it hasn't judged yet stays up.
            if (result == null && stop == null) {
                val fills = desk.settleOnly()
                notifyFills(fills, desk.bids())
                _status.update { it.copy(running = false, lastAtMs = now, problem = null) }
                return@withLock null
            }
            val partial = result?.partial == true
            val wallet = runCatching { c.wallet.fresh()?.dollars }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            val rules = MakerRules.of(s)
            val report = desk.cycle(
                withMoves(MakerLines.from(result, s, now), rules, now, read = stop == null), rules, stop, s.apiMaxPerDay, wallet,
                denied = c.makerDenials.outcomes(clock()), autoPost = s.maker, partial = partial,
            )
            notifyFills(report.fills, desk.bids())
            if (report.placed > 0 || report.cancelled > 0 || report.fills.isNotEmpty()) {
                // Whether Novig holds a resting bid's cost from the balance isn't in its docs (NOVIG_API.md §17): the wallet just after posting says.
                val walletNote = if (report.placed > 0 && wallet != null) {
                    val after = runCatching { c.wallet.fresh(maxAgeMs = 0L)?.dollars }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                    val up = desk.bids().filter { it.resting }.sumOf { it.restingDollars }
                    after?.let { " · wallet ${money(wallet)} → ${money(it)} with ${money(up)} resting" }.orEmpty()
                } else ""
                val waiting = report.waiting.entries.sumOf { it.value }.takeIf { it > 0 }?.let { n -> " · $n waiting: ${report.waiting.maxBy { it.value }.key}" }.orEmpty()
                c.eventLog.info(
                    "MAKER",
                    "$why${if (partial) " (scan running)" else ""}: ${report.placed} posted, ${report.cancelled} cancelled, ${report.fills.size} filled, ${report.resting} resting" +
                        (report.stopped?.let { " ($it)" } ?: "") + waiting + walletNote,
                )
            }
            if (report.placed > 0) c.eventLog.count("maker.posted.auto", report.placed.toLong())
            report.problems.forEach { p -> runCatching { c.problems.add("Make orders", p) } }
            _status.update {
                it.copy(
                    running = false, lastAtMs = now, lastReport = report, problem = report.problems.firstOrNull(),
                    decisions = report.decisions, decisionsAtMs = now, scanAtMs = result?.computedAtMs,
                )
            }
            if (!s.maker && stop == null && s.makerRecommend) recommend(report.decisions)
            report
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            val text = (e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName
            runCatching { c.problems.add("Make orders", text) }
            _status.update { it.copy(running = false, problem = text) }
            null
        }
    }

    /** The bids each line of the latest scan would get now, posting nothing (the tab with auto-make off, or between passes). */
    suspend fun preview(s: ScanSettings? = null): List<MakerDecision> {
        val settings = s ?: c.currentSettings()
        val now = clock()
        val bids = desk()?.bids().orEmpty()
        val resting = bids.filter { it.resting }.mapTo(HashSet()) { it.outcomeId }
        val busy = bids.filter { it.active && !it.resting }.mapTo(HashSet()) { it.outcomeId }
        val held = c.tracker.all().filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId } - resting + busy
        val denied = c.makerDenials.outcomes(clock())
        val run = scan()
        val rules = MakerRules.of(settings)
        val lines = withMoves(MakerLines.withoutOwn(MakerLines.from(run.result, settings, now), bids), rules, now, read = false)
        val decisions = MakerQuote.decideAll(lines, rules, now, held).map { d ->
            if (d is MakerDecision.Post && d.line.outcomeId in denied) MakerDecision.Skip(d.line, MakerDesk.DENIED) else d
        }
        _status.update { it.copy(decisions = decisions, decisionsAtMs = now, scanAtMs = run.result?.computedAtMs) }
        return decisions
    }

    /**
     * Tj's Approve (or Post) on one line: worked out again on the latest scan at this moment (the fair must still be fresh and the bid still at least
     * the margin under it), then posted. Null when posted, else why not.
     */
    suspend fun post(outcomeId: String): String? {
        val desk = desk() ?: return "Betting through Novig's API isn't set up (Settings › Betting & Novig account)"
        val s = c.currentSettings()
        if (s.paused) return "Scanning is paused: resume it to post bids"
        if (outcomeId in c.makerDenials.outcomes(clock())) return MakerDesk.DENIED
        val now = clock()
        val bids = desk.bids()
        val held = c.tracker.all().filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId } +
            bids.filter { it.active }.map { it.outcomeId }
        val rules = MakerRules.of(s)
        val line = withMoves(MakerLines.withoutOwn(MakerLines.from(scan().result, s, now).filter { it.outcomeId == outcomeId }, bids), rules, now, read = true)
            .firstOrNull() ?: return "That line isn't in the latest scan any more"
        val wallet = runCatching { c.wallet.fresh()?.dollars }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        return when (val d = MakerQuote.decide(line, rules, now, held)) {
            is MakerDecision.Skip -> d.why
            is MakerDecision.Post -> when {
                wallet != null && d.cost > wallet + 1e-9 -> "The wallet has ${com.tjshea.vigilant.app.ui.Format.money(wallet)}, under this bid's ${com.tjshea.vigilant.app.ui.Format.money(d.cost)}"
                else -> desk.post(d, rules).also { if (it == null) { MakerNotes.cancelRecommendation(app, outcomeId); preview(s) } }
            }
        }
    }

    /** Tj's Deny: no bid on that side (by hand or auto-make) before its game starts, and a resting one comes down. Null when done, else why not. */
    suspend fun deny(outcomeId: String, startsTs: Long? = null, selection: String? = null): String? {
        val line = _status.value.decisions.firstOrNull { it.line.outcomeId == outcomeId }?.line
        val bid = desk()?.bids()?.lastOrNull { it.outcomeId == outcomeId }
        val starts = startsTs ?: line?.startsTs ?: bid?.startsTs ?: return "That line isn't in the latest scan any more"
        c.makerDenials.deny(outcomeId, starts, selection ?: line?.selection ?: bid?.selection ?: "")
        MakerNotes.cancelRecommendation(app, outcomeId)
        bid?.takeIf { it.resting && it.orderId != null }?.let { desk()?.cancel(it.orderId!!) }
        preview()
        return null
    }

    suspend fun undoDeny(outcomeId: String) {
        c.makerDenials.undo(outcomeId)
        preview()
    }

    /** Tj's Cancel on one bid: it comes down and its side is denied (else auto-make would post it again at the next pass). */
    suspend fun cancel(orderId: String): String? {
        val desk = desk() ?: return null
        val bid = desk.bids().firstOrNull { it.orderId == orderId }
        val why = desk.cancel(orderId)
        if (why == null && bid != null) c.makerDenials.deny(bid.outcomeId, bid.startsTs, bid.selection)
        preview()
        return why
    }

    /** Every bid down ([why] on each). How many cancels Novig took; null when betting isn't set up. */
    suspend fun cancelAll(why: String): Int? {
        val desk = desk() ?: return null
        if (desk.bids().none { it.active }) return 0
        return desk.cancelAll(why).also {
            c.eventLog.info("MAKER", "every bid cancelled: $why")
            preview()
        }
    }

    /** Auto-make switched off: the bids it posted come down; the ones Tj approved by hand stay. */
    suspend fun cancelAuto(why: String): Int? {
        val desk = desk() ?: return null
        if (desk.bids().none { it.active && it.auto }) return 0
        return desk.cancelAuto(why).also {
            c.eventLog.info("MAKER", "auto-make's bids cancelled: $why")
            preview()
        }
    }

    /**
     * New bids worth approving, as notifications with Approve and Deny (Tj, 2026-10-03: "recommend bets to make and I manually approve or deny them"):
     * each side once before its game, best first, at most [MAX_RECOMMENDED] a pass.
     */
    private suspend fun recommend(decisions: List<MakerDecision>) {
        val posts = decisions.filterIsInstance<MakerDecision.Post>()
        if (posts.isEmpty()) return
        val now = clock()
        val fresh = c.makerRecommended.unseen(posts.map { it.line.outcomeId to it.line.startsTs }, now)
        // A busy slate prices hundreds of lines: the best few a pass, and no more than [MAX_RECOMMENDED_PER_HOUR] an hour (the tab lists the rest).
        val room = (MAX_RECOMMENDED_PER_HOUR - c.makerRecommended.since(now - 3_600_000L)).coerceAtLeast(0)
        val pick = posts.filter { it.line.outcomeId in fresh }.sortedWith(compareBy({ it.price }, { -it.evAtFair })).take(minOf(MAX_RECOMMENDED, room))
        if (pick.isEmpty()) return
        pick.forEach { MakerNotes.recommend(app, it) }
        c.makerRecommended.mark(pick.map { it.line.outcomeId to it.line.startsTs }, clock())
        c.eventLog.count("maker.recommended", pick.size.toLong())
    }

    private fun money(v: Double) = com.tjshea.vigilant.app.ui.Format.money(v)

    private fun stopReason(s: ScanSettings): String? = when {
        !AppBook.isNovig -> "Make orders are for Novig"
        s.paused -> "Scanning is paused"
        else -> null
    }

    private fun notifyFills(bets: List<TrackedBet>, bids: List<MakerBid>) {
        for (bet in bets) {
            val bid = bids.firstOrNull { it.betId == bet.id || it.orderId == bet.orderId } ?: continue
            MakerNotes.filled(app, bet, bid)
            c.eventLog.count("maker.filled")
        }
    }

    companion object {
        /** The most bids recommended (notified) in one pass, and in an hour. */
        const val MAX_RECOMMENDED = 3
        const val MAX_RECOMMENDED_PER_HOUR = 6

        /** The background cycle's pass is skipped when another started less than this long ago ([run]'s minGapMs). */
        const val BACKGROUND_GAP_MS = 15_000L

        /** How long one read of a market's trades serves the move rule (the auto-bet's cooldown: a move is judged on the last 15 min and the hour). */
        const val MOVE_READ_MS = 2 * 60_000L

        /** The most markets whose trades one pass reads (public route, paced: [com.tjshea.vigilant.data.novig.NovigPublicClient]). */
        const val MAX_MOVE_READS = 6
    }
}

/** Sides already recommended (files/maker_recommended.json): each side once before its game. */
class MakerRecommended(file: java.io.File) {
    @kotlinx.serialization.Serializable
    data class Seen(val outcomeId: String, val startsTs: Long, val atMs: Long)

    private val store = com.tjshea.vigilant.data.store.JsonFileStore(file, kotlinx.serialization.builtins.ListSerializer(Seen.serializer()), { emptyList() })

    suspend fun unseen(sides: List<Pair<String, Long>>, now: Long): Set<String> {
        val seen = store.read().filter { it.startsTs > now }.mapTo(HashSet()) { it.outcomeId }
        return sides.map { it.first }.filterTo(HashSet()) { it !in seen }
    }

    /** How many were recommended since [fromMs]. */
    suspend fun since(fromMs: Long): Int = store.read().count { it.atMs >= fromMs }

    suspend fun mark(sides: List<Pair<String, Long>>, now: Long) {
        store.update { list -> list.filter { it.startsTs > now } + sides.map { (o, s) -> Seen(o, s, now) } }
    }
}

/** The notifications of make orders: a filled bid, and a bid recommended to approve or deny (each its own: tagged by the bet or the side). */
object MakerNotes {
    private const val ID_FILLED = 5_100_000
    private const val ID_RECOMMEND = 5_100_001

    /** Bids to approve: their own channel, so Tj can set how loud they are apart from the bets placed. */
    const val CHANNEL_RECOMMEND = "maker_recommend"

    const val ACTION_APPROVE = "com.tjshea.vigilant.action.BID_APPROVE"
    const val ACTION_DENY = "com.tjshea.vigilant.action.BID_DENY"
    const val EXTRA_OUTCOME = "outcome"
    const val EXTRA_STARTS = "starts"
    const val EXTRA_SELECTION = "selection"

    fun ensureChannel(context: android.content.Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_RECOMMEND, "Bids to approve", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A bid Vigilant recommends posting on Novig (auto-make off): Approve posts it, Deny skips that side until its game."
            },
        )
    }

    /** "Bid to approve · +4.5% EV · Lamar Jackson Over 224.5". */
    fun recommendTitle(d: MakerDecision.Post): String =
        "Bid to approve · ${String.format(Locale.US, "%+.1f%%", d.evAtFair * 100)} EV · ${d.line.selection}"

    /** "Bid +120 (fair +111) · $9.95 · Passing Yards · BAL @ DAL · good for 8 min". */
    fun recommendText(d: MakerDecision.Post, now: Long): String {
        val f = com.tjshea.vigilant.app.ui.Format
        val left = ((d.restUntilMs - now) / 60_000).coerceAtLeast(1)
        return "Bid ${f.american(d.price)} (fair ${f.american(d.line.fair ?: d.price)}) · ${f.money(d.cost)} · ${d.line.marketLabel} · ${d.line.eventName} · " +
            "its fair is good for $left min: Approve re-checks it first"
    }

    fun recommend(app: Application, d: MakerDecision.Post, now: Long = System.currentTimeMillis()) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        val text = recommendText(d, now)
        val n = NotificationCompat.Builder(app, CHANNEL_RECOMMEND)
            .setSmallIcon(R.drawable.ic_bids)
            .withWallet(app)
            .setContentTitle(recommendTitle(d))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            // The fair behind it goes old: so does the recommendation.
            .setTimeoutAfter((d.restUntilMs - now).coerceAtLeast(60_000L))
            .setContentIntent(EvAlerts.openVigilant(app, ID_RECOMMEND))
            .addAction(0, "Approve", action(app, ACTION_APPROVE, d))
            .addAction(0, "Deny", action(app, ACTION_DENY, d))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(d.line.outcomeId, ID_RECOMMEND, n) }
    }

    private fun action(app: Application, what: String, d: MakerDecision.Post): android.app.PendingIntent {
        val i = android.content.Intent(app, MakerActionReceiver::class.java).setAction(what)
            .putExtra(EXTRA_OUTCOME, d.line.outcomeId).putExtra(EXTRA_STARTS, d.line.startsTs).putExtra(EXTRA_SELECTION, d.line.selection)
        return android.app.PendingIntent.getBroadcast(
            app, (what + d.line.outcomeId).hashCode(), i, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** What came of an Approve from the notification: "Bid posted …" or why not (the same notification, replaced). */
    fun approved(app: Application, outcomeId: String, selection: String, why: String?) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        val title = if (why == null) "Bid posted · $selection" else "Not posted · $selection"
        val text = why ?: "Post-only: it rests on Novig until someone takes it, and comes down when its fair moves against it. A fill is a bet in the Tracker."
        val n = NotificationCompat.Builder(app, CHANNEL_RECOMMEND)
            .setSmallIcon(R.drawable.ic_bids)
            .withWallet(app)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_RECOMMEND))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(outcomeId, ID_RECOMMEND, n) }
    }

    fun cancelRecommendation(app: Application, outcomeId: String) {
        runCatching { NotificationManagerCompat.from(app).cancel(outcomeId, ID_RECOMMEND) }
    }

    /** "Bid filled $4.85 · +4.0% EV · Player Over 50.5". */
    fun title(bet: TrackedBet): String {
        val ev = bet.evPercentAtBet?.let { String.format(Locale.US, " · %+.1f%% EV", it * 100) }.orEmpty()
        return "Bid filled ${String.format(Locale.US, "$%.2f", bet.stake)}$ev · ${bet.selection}"
    }

    /** "+105 (bid) · fair +96 · 1,000 of 1,000 contracts · Player Receiving Yards · A @ B". */
    fun text(bet: TrackedBet, bid: MakerBid): String {
        val odds = bet.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: "?"
        val fair = com.tjshea.vigilant.engine.Odds.formatAmerican(com.tjshea.vigilant.engine.Odds.probabilityToAmerican(bid.fair.coerceIn(0.001, 0.999)))
        return "$odds (your bid) · fair $fair · ${String.format(Locale.US, "%,d", bet.contracts ?: 0)} of ${String.format(Locale.US, "%,d", bid.contracts)} contracts · ${bet.marketLabel} · ${bet.eventName}"
    }

    fun filled(app: Application, bet: TrackedBet, bid: MakerBid) {
        if (!ScanService.canNotify(app)) return
        AutoBetNotes.ensureChannel(app)
        val text = text(bet, bid)
        val n = NotificationCompat.Builder(app, AutoBetNotes.CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title(bet))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_FILLED))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(bet.id, ID_FILLED, n) }
    }
}

/** The recommendation's Approve and Deny ([MakerNotes.recommend]): run in the background, no screen; [goAsync] keeps it alive until done. */
class MakerActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val app = context.applicationContext as? VigilantApp ?: return
        val outcome = intent.getStringExtra(MakerNotes.EXTRA_OUTCOME) ?: return
        val selection = intent.getStringExtra(MakerNotes.EXTRA_SELECTION).orEmpty()
        val starts = intent.getLongExtra(MakerNotes.EXTRA_STARTS, 0L).takeIf { it > 0 }
        val pending = runCatching { goAsync() }.getOrNull()
        app.container.appScope.launch {
            try {
                when (intent.action) {
                    MakerNotes.ACTION_APPROVE -> {
                        val why = runCatching { app.container.maker.post(outcome) }.getOrElse { it.message ?: it.javaClass.simpleName }
                        MakerNotes.approved(app, outcome, selection, why)
                    }
                    MakerNotes.ACTION_DENY -> {
                        runCatching { app.container.maker.deny(outcome, starts, selection) }
                        MakerNotes.cancelRecommendation(app, outcome)
                    }
                }
            } finally {
                pending?.finish()
            }
        }
    }
}
