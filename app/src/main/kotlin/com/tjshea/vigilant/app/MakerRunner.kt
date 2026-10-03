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

    /**
     * One pass. Auto-make on: the bids Vigilant's latest scan wants are posted, moved and cancelled. Auto-make off: the bids Tj approved by hand are
     * watched (fills recorded, taken down when no longer worth it, never re-posted), and new bids are recommended to approve or deny
     * ([ScanSettings.makerRecommend]). Scanning paused: every bid comes down. No scan yet, or one still running: only fills and expiries are read.
     * Null when betting through the API isn't set up. [why] is logged.
     */
    suspend fun run(why: String): MakerDesk.Report? = passes.withLock {
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
            // No scan yet, or one still running (its lines come in a league at a time): only fills and expiries until it's done.
            if ((result == null || run.scanning || result.partial) && stop == null) {
                val fills = desk.settleOnly()
                notifyFills(fills, desk.bids())
                _status.update { it.copy(running = false, lastAtMs = now, problem = null) }
                return@withLock null
            }
            val wallet = runCatching { c.wallet.fresh()?.dollars }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            val report = desk.cycle(
                MakerLines.from(result, s, now), MakerRules.of(s), stop, s.apiMaxPerDay, wallet,
                denied = c.makerDenials.outcomes(), autoPost = s.maker,
            )
            notifyFills(report.fills, desk.bids())
            if (report.placed > 0 || report.cancelled > 0 || report.fills.isNotEmpty()) {
                c.eventLog.info("MAKER", "$why: ${report.placed} posted, ${report.cancelled} cancelled, ${report.fills.size} filled, ${report.resting} resting" + (report.stopped?.let { " ($it)" } ?: ""))
            }
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
        val denied = c.makerDenials.outcomes()
        val run = scan()
        val decisions = MakerQuote.decideAll(MakerLines.from(run.result, settings, now), MakerRules.of(settings), now, held).map { d ->
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
        if (outcomeId in c.makerDenials.outcomes()) return MakerDesk.DENIED
        val now = clock()
        val bids = desk.bids()
        val held = c.tracker.all().filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId } +
            bids.filter { it.active }.map { it.outcomeId }
        val line = MakerLines.from(scan().result, s, now).firstOrNull { it.outcomeId == outcomeId } ?: return "That line isn't in the latest scan any more"
        val rules = MakerRules.of(s)
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
        val fresh = c.makerRecommended.unseen(posts.map { it.line.outcomeId to it.line.startsTs }, clock())
        val pick = posts.filter { it.line.outcomeId in fresh }.sortedWith(compareBy({ it.price }, { -it.evAtFair })).take(MAX_RECOMMENDED)
        if (pick.isEmpty()) return
        pick.forEach { MakerNotes.recommend(app, it) }
        c.makerRecommended.mark(pick.map { it.line.outcomeId to it.line.startsTs }, clock())
        c.eventLog.count("maker.recommended", pick.size.toLong())
    }

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
        /** The most bids recommended (notified) in one pass. */
        const val MAX_RECOMMENDED = 3
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

    suspend fun mark(sides: List<Pair<String, Long>>, now: Long) {
        store.update { list -> list.filter { it.startsTs > now } + sides.map { (o, s) -> Seen(o, s, now) } }
    }
}

/** The notification for a filled bid (its own each: the bet's id is the tag). */
object MakerNotes {
    private const val ID_FILLED = 5_100_000

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
