package com.tjshea.vigilant.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The auto-bet (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all, including automatic bets in the background as
 * the cno scanner is on in the background"): inside a background auto-scan cycle, after CrazyNinjaOdds' list, Novig's price now and each best
 * bet's books were read, every CNO bet that passes Tj's criteria ([AutoBet.judge]) is placed through Novig's API from the Vigilant wallet, best
 * edge first, one order at a time, at most [AutoBet.MAX_PER_CYCLE] a cycle.
 *
 * It is the Bet sheet's own path with nobody to confirm ([ApiBetPlacer.placeAuto]: pregame only, fair odds fresh, the edge still there at the
 * real book price, the per-bet and per-day limits, an `IOC` at a ceiling that is never chased), plus what an unattended bet needs:
 *  - **The wallet** is read before the cycle's bets and caps each stake; with less than a dollar left it places nothing and says so once.
 *  - **The bet found on Novig must be the bet shown**: the book's price for the outcome matched must be the price the bet was judged at
 *    ([AutoBet.PRICE_TOLERANCE]); one bet per Novig market, so the other side of a line is never bet after the first.
 *  - **A lost answer stops it.** An order whose answer never came back (nothing says whether it filled) HALTS auto-bet ([ScanSettings.autoBetHalted])
 *    until Tj resumes it: it is never re-sent, and the next cycle can't bet the same thing again.
 *  - **Everything is tracked** exactly as a Bet-sheet bet ([BetTracker.logApi] from the fills, hidden from the lists like a ✓), flagged
 *    [TrackedBet.auto]; a notification for each bet placed and one for each stop.
 * Nothing here runs on its own: [AutoScanner.cycle] calls [run].
 */
class AutoBettor(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The placer, or null when betting through the API isn't set up. */
    private val placer: () -> ApiBetPlacer? = { c.autoBetPlacer() },
    /** The Vigilant wallet's balance now; null when betting isn't set up. May throw Novig's refusal. */
    private val wallet: suspend () -> Double? = {
        val t = c.trading
        val address = c.subaccountKeyId
        if (t == null || address == null) null else withContext(Dispatchers.IO) { t.balance(address) }
    },
    /** [row]'s exact Novig bet as a target (market read, outcome found), or null when it can't be found for certain. */
    private val resolve: suspend (CnoRow) -> BetTarget? = { row -> resolveOnNovig(c, row) },
    private val notes: AutoBetNotes = AutoBetNotes,
    /**
     * The sharp-book check for one candidate (Tj, 2026-10-02): rules, the bet, CNO's game page for it, Novig's price now, live?, now. Only asked when
     * Settings switch it on and the bet passed everything else.
     */
    private val sharp: suspend (SharpConfirm.Rules, SharpBooks.Bet, CnoBooksView?, Int, Boolean, Long) -> SharpConfirm.Result =
        { rules, bet, view, odds, live, at -> SharpGate.check(c.sharp, rules, bet, view, odds, live, at) },
    /** How long nothing is sent after Novig refuses an order ([FAIL_BACKOFF_MS]). */
    private val failBackoffMs: Long = FAIL_BACKOFF_MS,
) {

    /** What the last [run] did, for Settings, Diagnostics and the tests. */
    data class Report(
        /** CNO bets looked at (candidates with their books read). */
        val looked: Int = 0,
        /** Of those, how many passed Tj's criteria. */
        val passed: Int = 0,
        val placed: List<TrackedBet> = emptyList(),
        /** Why bets weren't placed, reason → how many (a few distinct ones). */
        val skipped: Map<String, Int> = emptyMap(),
        /** Why the run stopped early (wallet, daily limit, Novig refused, halted), or null. */
        val stopped: String? = null,
        val walletEmpty: Boolean = false,
        val halted: Boolean = false,
    ) {
        val staked: Double get() = placed.sumOf { it.stake }
    }

    data class Status(
        val lastRunMs: Long? = null,
        val last: Report = Report(),
        /** Why it can't place anything at all right now (not set up, wallet unreadable): shown in Settings. */
        val blocker: String? = null,
        /** The wallet's balance when last read. */
        val balance: Double? = null,
        /** Bets placed and dollars staked since the app process started. */
        val placedSinceStart: Int = 0,
        val stakedSinceStart: Double = 0.0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Bets refused or not found recently, by CNO row key → when they may be tried again. */
    private val cooldown = HashMap<String, Long>()

    /**
     * The most Novig refused as too small this run (`ORDER_TOO_SMALL`; its threshold isn't published), 0 = none yet: a stake at or under it is
     * skipped, not sent again. A fresh run asks again, in case Novig changed it.
     */
    private var tooSmallBelow = 0.0

    /** The wallet-empty note went out and the wallet hasn't refilled since. */
    private var walletEmptyNoted = false

    /** The last stop note posted and when: the same one isn't posted again for [NOTE_REPEAT_MS]. */
    private var lastStopNote: Pair<String, Long>? = null

    /** Novig refused an order (the location check, KYC, a wallet it won't take) or the day's limit was reached: nothing is sent again until [failedUntilMs], whatever the interval. */
    private var failedUntilMs = 0L
    private var failure: String? = null

    /**
     * One pass for [settings] over [state] (the CNO list, live prices and books as the cycle just read them). Never throws for a bet's own
     * trouble: it ends up in the [Report]. Cancelled cleanly: a bet's order, once sent, is always followed to its end and recorded.
     */
    suspend fun run(settings: ScanSettings, state: UiState): Report {
        val now = clock()
        val rules = AutoBet.rules(settings)
        // Whoever calls, a halted or switched-off auto-bet places nothing.
        if (!settings.autoBet) return finish(now, Report(), blocker = "Auto-bet is off")
        settings.autoBetHalted?.let { return finish(now, Report(halted = true), blocker = "stopped: $it (Settings › Betting › Resume auto-bet)") }
        failure?.takeIf { now < failedUntilMs }?.let { return finish(now, Report(stopped = it), blocker = it) }
        val placer = placer()
        if (!AppBook.isNovig || placer == null) return finish(now, Report(), blocker = "Betting through Novig's API isn't set up (Settings › Betting › Enable betting)")
        cooldown.entries.removeAll { it.value <= now }

        val skipped = LinkedHashMap<String, Int>()
        fun skip(reason: String) { skipped.merge(reason, 1, Int::plus) }

        // Pregame bets at Novig whose books were read, best edge first. Each is judged on Novig's price read in the last minute: a bet with no
        // such price isn't placed (its edge is whatever it was a while ago).
        val all = AlertPicks.cnoChecked(state, rules.minEv, now)
        val passing = ArrayList<AlertPicks.CnoChecked>()
        for (item in all.distinctBy { it.pick.row.key }.sortedByDescending { it.shown.ev }) {
            val row = item.pick.row
            val startsAt = row.startsAtMs
            when {
                !ApiBetTargets.atNovig(row) -> skip("not priced at Novig")
                item.pick.live || startsAt == null || startsAt - now < MIN_LEAD_MS -> skip("not pregame (live betting isn't available)")
                item.live == null -> skip("no Novig price read in the last minute")
                (cooldown[row.key] ?: 0L) > now -> skip("tried a moment ago")
                else -> AutoBet.judge(rules, item.shown.ev, item.check, item.shown.row.odds)?.let(::skip)
                    ?: sharpReason(item, settings, state, now)?.let(::skip)
                    ?: passing.add(item)
            }
        }
        if (passing.isEmpty()) {
            return finish(now, Report(looked = all.size, skipped = skipped))
        }

        val balance0 = try {
            wallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped), "Couldn't read the wallet: ${e.advice}")
        } catch (e: Exception) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped), "Couldn't read the wallet: ${e.message ?: e.javaClass.simpleName}")
        } ?: return finish(now, Report(looked = all.size, passed = passing.size, skipped = skipped), blocker = "Betting through Novig's API isn't set up (Settings › Betting › Enable betting)")
        var balance = balance0
        if (balance >= AutoBet.MIN_STAKE) walletEmptyNoted = false

        // One bet per Novig market (either side) while any is open: the other side of a line is never bet after the first.
        val openMarkets = c.tracker.all().filter { it.status == BetStatus.PENDING && it.marketId.isNotBlank() }.mapTo(HashSet()) { it.marketId }
        val limits = BetLimits(maxStake = rules.maxStake, maxPerDay = settings.apiMaxPerDay, minEv = rules.minEv, maxOdds = rules.maxOdds)
        val placed = ArrayList<TrackedBet>()
        var stopped: String? = null
        var walletEmpty = false
        var halted = false

        for (item in passing) {
            currentCoroutineContext().ensureActive()
            if (placed.size >= AutoBet.MAX_PER_CYCLE) { stopped = "placed ${AutoBet.MAX_PER_CYCLE} this cycle (the best edges first); the rest wait for the next"; break }
            val row = item.pick.row
            val stake = when (val s = AutoBet.stake(rules, item.shown.row, settings.bankroll, balance)) {
                is AutoBet.Stake.WalletEmpty -> { walletEmpty = true; stopped = "the wallet has ${money(balance)}, under a cent"; break }
                is AutoBet.Stake.Skip -> { skip(s.reason); continue }
                is AutoBet.Stake.Amount -> s.dollars
            }
            if (stake <= tooSmallBelow + 1e-9) { skip("Novig refused an order of ${money(tooSmallBelow)} as too small, and this one is no bigger"); continue }
            val target = try {
                resolve(row)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (target == null) {
                cooldown[row.key] = now + NOT_FOUND_COOLDOWN_MS
                skip("its exact bet couldn't be found on Novig")
                continue
            }
            // Novig's price for this bet was read from one outcome's book, and its bet slip was found by the catalog: they must be the same outcome.
            val priced = item.live?.outcomeId
            if (priced != null && priced != target.outcomeId) { cooldown[row.key] = now + NOT_FOUND_COOLDOWN_MS; skip("Novig's price and its bet slip name different outcomes"); continue }
            if (target.market.marketId in openMarkets) { skip("a bet in this Novig market is already open"); continue }

            // The order is marked in flight BEFORE it can be sent (saved, and auto-bet stays stopped until it's cleared): if the process dies after
            // Novig takes the order and before the Tracker has it (Tj's v0.38.0 report: the app crashed, out of memory), the next cycle would find
            // the same bet again with nothing on record and place it twice. The marker survives that; a restart finds auto-bet stopped, with why.
            val marker = inFlightNote(target, stake)
            withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = marker) } } }
            // Once an order may be on its way it is followed to its end and recorded, whatever happens to this coroutine.
            val result = try {
                withContext(Dispatchers.IO + NonCancellable) { placer.placeAuto(target.copy(auto = true), stake, limits, AutoBet.priceOf(item.shown.row)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}).")
            }
            // A definitive answer (placed, nothing filled, refused, Novig said no) clears the marker; a lost answer replaces it below.
            if (result !is PlaceResult.Unconfirmed) {
                withContext(NonCancellable) { runCatching { c.settingsStore.update { if (it.autoBetHalted == marker) it.copy(autoBetHalted = null) else it } } }
            }
            when (result) {
                is PlaceResult.Placed -> {
                    val bet = result.bet
                    placed += bet
                    openMarkets += target.market.marketId
                    balance -= bet.stake
                    withContext(NonCancellable) { markPlaced(c, target, result, clock()) }
                    notes.placed(app, target, bet, item.check, walletLeft = balance, sharp = sharpSaid[row.key]?.takeIf { it.confirmed }?.detail)
                }
                is PlaceResult.NotFilled -> { cooldown[row.key] = now + AutoBet.COOLDOWN_MS; skip("nobody was selling at that price") }
                is PlaceResult.Refused -> {
                    cooldown[row.key] = now + AutoBet.COOLDOWN_MS
                    if (result.tooSmall) tooSmallBelow = maxOf(tooSmallBelow, stake)
                    if (result.reason.contains("daily limit")) {
                        stopped = "your daily limit of ${money(settings.apiMaxPerDay)} for API bets is reached"
                        failure = "waiting: $stopped (checked again every ${failBackoffMs / 60_000L} minutes)"
                        failedUntilMs = now + failBackoffMs
                        break
                    }
                    skip(result.reason.take(REASON_CHARS))
                }
                is PlaceResult.Failed -> {
                    stopped = "Novig refused: ${result.message}"
                    failure = "waiting after Novig refused an order: ${result.message}"
                    failedUntilMs = now + failBackoffMs
                    break
                }
                is PlaceResult.Unconfirmed -> {
                    // Nothing says whether it filled: never again until Tj has looked (Novig, then Settings › Betting › Resume).
                    halted = true
                    stopped = "an order's answer was lost, so auto-bet is stopped: ${result.message}"
                    withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = result.message) } } }
                    break
                }
            }
        }
        if (walletEmpty && !walletEmptyNoted) {
            walletEmptyNoted = true
            notes.stopped(app, "Wallet empty", "Auto-bet is waiting: the Vigilant wallet has ${money(balance)}. Add money in Settings › Betting.")
        }
        val report = Report(looked = all.size, passed = passing.size, placed = placed, skipped = skipped, stopped = stopped, walletEmpty = walletEmpty, halted = halted)
        if (stopped != null && !walletEmpty) postStop(now, stopped, halted)
        return finish(now, report, balance = balance)
    }

    /** What the sharp book said about each bet that passed (CNO row key → the numbers), for the bet's pop-up and Diagnostics. */
    private val sharpSaid = HashMap<String, SharpConfirm.Result>()

    /**
     * Why the sharp-book check (Settings › Betting, off by default) doesn't let [item] through, or null: it is off, or a fresh sharp book's own devigged
     * price makes the bet +EV at Novig's price now ([SharpConfirm]). Asked last, after every other criterion: only a bet about to be placed costs a feed call.
     */
    private suspend fun sharpReason(item: AlertPicks.CnoChecked, settings: ScanSettings, state: UiState, now: Long): String? {
        val rules = SharpConfirm.rules(settings, autoBet = true) ?: return null
        val row = item.shown.row
        val bet = SharpBooks.Bet(row.league, row.event, row.startsAtMs, row.market, row.bet)
        val result = try {
            sharp(rules, bet, state.booksAt(item.pick.row.key, now)?.view, row.odds, item.pick.live, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SharpConfirm.Result(SharpConfirm.Verdict.UNAVAILABLE, reason = "couldn't get a fresh ${rules.label} price (${e.message ?: e.javaClass.simpleName})".take(REASON_CHARS))
        }
        if (sharpSaid.size > SHARP_SAID_KEEP) sharpSaid.clear()
        sharpSaid[item.pick.row.key] = result
        c.eventLog.count("sharp.autobet.${result.verdict}")
        if (result.verdict == SharpConfirm.Verdict.UNAVAILABLE) c.eventLog.warn("SHARP", result.reason ?: "the sharp check couldn't ask")
        return result.reason
    }

    private fun postStop(now: Long, why: String, halted: Boolean) {
        val last = lastStopNote
        if (last != null && last.first == why && now - last.second < NOTE_REPEAT_MS) return
        // A stop that's only "placed 5 this cycle" isn't news.
        if (why.startsWith("placed ")) return
        lastStopNote = why to now
        notes.stopped(app, if (halted) "Auto-bet stopped" else "Auto-bet paused", why.replaceFirstChar { it.uppercase() })
    }

    private fun stopFor(now: Long, report: Report, why: String): Report {
        postStop(now, why, halted = false)
        return finish(now, report.copy(stopped = why))
    }

    /** The flight recorder's view of a run (Tj, 2026-10-02): the funnel in counters (looked → passed → placed, and why not), each bet placed and each stop as an event. */
    private fun record(report: Report, blocker: String?) {
        val log = c.eventLog
        if (report.looked > 0) {
            log.count("autobet.runs")
            log.count("autobet.looked", report.looked.toLong())
            log.count("autobet.passed", report.passed.toLong())
        }
        report.skipped.forEach { (reason, n) -> log.count("autobet.skip.$reason", n.toLong()) }
        report.placed.forEach { b ->
            log.count("autobet.placed")
            log.info("AUTOBET", "placed ${b.selection} ${money(b.stake)}${b.american?.let { " at ${if (it > 0) "+$it" else "$it"}" } ?: ""}" + (b.evPercentAtBet?.let { String.format(Locale.US, " (%+.1f%% EV)", it * 100) } ?: ""))
        }
        report.stopped?.takeIf { !it.startsWith("placed ") }?.let { log.warn("AUTOBET", "stopped: $it") }
        blocker?.takeIf { it != "Auto-bet is off" }?.let { log.warn("AUTOBET", "can't place bets: $it") }
    }

    private fun finish(now: Long, report: Report, blocker: String? = null, balance: Double? = null): Report {
        runCatching { record(report, blocker) }
        _status.update {
            it.copy(
                lastRunMs = now, last = report, blocker = blocker, balance = balance ?: it.balance,
                placedSinceStart = it.placedSinceStart + report.placed.size, stakedSinceStart = it.stakedSinceStart + report.staked,
            )
        }
        return report
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    companion object {
        /** How many sharp-check answers are kept for the pop-ups before they're dropped (a run's bets are a handful). */
        private const val SHARP_SAID_KEEP = 200

        /** A game starting sooner than this isn't bet: Novig may be moving it to live, and the price is about to jump. */
        const val MIN_LEAD_MS = 60_000L

        /** What the saved halt says while an order may be on its way ([run]): a restart that finds it knows what to check. */
        fun inFlightNote(target: BetTarget, stake: Double): String =
            "Vigilant stopped while an order for ${target.selection} (${String.format(Locale.US, "$%.2f", stake)}) was being placed, so nothing says whether Novig filled it. " +
                "Check Novig and the Tracker's Sync with Novig's fills before resuming."

        /** A bet whose Novig outcome wasn't found isn't searched for again at once. */
        const val NOT_FOUND_COOLDOWN_MS = 5 * 60_000L

        /** After Novig refuses an order, nothing is tried for this long. */
        const val FAIL_BACKOFF_MS = 5 * 60_000L

        /** The same stop isn't announced again for this long. */
        const val NOTE_REPEAT_MS = 60 * 60_000L

        private const val REASON_CHARS = 90

        /** [row]'s exact bet on Novig, as the Bet sheet finds it ([NovigBetFinder]), with its market; null unless the outcome is in that market. */
        suspend fun resolveOnNovig(c: AppContainer, row: CnoRow): BetTarget? {
            val found = withContext(Dispatchers.IO) { runCatching { c.betFinder.find(row) }.getOrNull() } as? NovigBetFinder.Found.Bet ?: return null
            val market = found.market ?: found.marketId?.let { id -> withContext(Dispatchers.IO) { runCatching { c.novig.market(id) }.getOrNull() } } ?: return null
            if (market.outcomes.none { it.outcomeId == found.outcomeId }) return null
            return ApiBetTargets.of(row, found, market, c.cno.state.value.snapshot?.dataAtMs)
        }

        /** A line for Settings and Diagnostics: what the last run did. */
        fun line(s: Status, now: Long): String {
            val r = s.last
            if (s.lastRunMs == null) return "No check yet: it runs with the next background CNO scan."
            val at = "Last check ${com.tjshea.vigilant.app.ui.Format.age(s.lastRunMs, now)}"
            s.blocker?.let { return "$at: $it" }
            val parts = listOfNotNull(
                if (r.placed.isNotEmpty()) "placed ${r.placed.size} (${String.format(Locale.US, "$%.2f", r.staked)})" else null,
                "${r.looked} looked at, ${r.passed} met your criteria".takeIf { r.looked > 0 || r.placed.isEmpty() },
                r.stopped?.let { "stopped: $it" },
                r.skipped.entries.sortedByDescending { it.value }.take(2).takeIf { it.isNotEmpty() }?.joinToString("; ", "skipped: ") { "${it.value} ${it.key}" },
            )
            return "$at: ${parts.joinToString(" · ")}"
        }
    }
}

/** The bet leaves the +EV and CNO lists the way a ✓ makes it ([PlacedBets]): it's placed. The Bet sheet and the auto-bet both do this after an API bet. */
internal suspend fun markPlaced(c: AppContainer, target: BetTarget, placed: PlaceResult.Placed, now: Long) {
    val key = target.placedKey ?: return
    runCatching {
        c.placed.mark(
            com.tjshea.vigilant.data.tracker.PlacedBet(
                key = key, title = target.selection, detail = "${target.marketLabel} · ${target.eventName}", odds = MiniWindow.american(placed.bet.american ?: 0),
                placedAtMs = now, startsAtMs = target.startsTs, event = target.eventName, market = target.marketLabel,
                outcomeId = target.outcomeId, league = target.league,
            ),
        )
    }
}

/**
 * The auto-bet's notifications (Tj, 2026-10-01: "Make a push notification for every automatic bet, so I can see each bet placed and the stake and
 * EV"): one of its own for each bet placed (it spent money while nobody looked), on a HIGH-importance channel so it pops up, with the stake and the
 * EV in the title and the odds, books and what's left in the wallet under it; and one for each stop that needs Tj.
 */
object AutoBetNotes {
    /** Stops (wallet empty, a lost order, Novig refusing): the v0.39.0 channel, left as it is on a phone that already has it. */
    const val CHANNEL = "auto_bet"

    /**
     * Each bet placed. A channel's importance can't be raised by the app once it exists, and v0.39.0's bets were posted on [CHANNEL] at normal
     * importance (no pop-up): the bets get this new one.
     */
    const val CHANNEL_BET = "auto_bet_placed"
    private const val ID_PLACED = 5_000_000
    private const val ID_STOP = 4_999_999
    private const val ID_SAMPLE = 4_999_998

    fun ensureChannel(context: android.content.Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Auto-bet stops", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When auto-bet stops (wallet empty, a lost order, Novig refusing)."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BET, "Auto-bet bets placed", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A pop-up for every bet auto-bet places with your wallet: the stake, the EV and the odds."
                enableVibration(true)
            },
        )
    }

    /** "Auto-bet $4.20 · +3.4% EV · Over 5.5": the stake and the edge up front, so the collapsed notification already says what was bet. */
    fun title(bet: TrackedBet, target: BetTarget): String {
        val ev = bet.evPercentAtBet?.let { String.format(Locale.US, " · %+.1f%% EV", it * 100) }.orEmpty()
        return "Auto-bet ${String.format(Locale.US, "$%.2f", bet.stake)}$ev · ${target.selection}"
    }

    /** "+120 · 4 of 5 books agree · Moneyline · A @ B · wallet $2.10 left". */
    fun text(bet: TrackedBet, target: BetTarget, check: CnoBooks.Check, walletLeft: Double? = null): String {
        val odds = bet.american?.let { if (it > 0) "+$it" else "$it" } ?: "?"
        val left = walletLeft?.let { " · wallet ${String.format(Locale.US, "$%.2f", it.coerceAtLeast(0.0))} left" }.orEmpty()
        return "$odds · ${check.agreeing} of ${check.twoSided} books agree · ${target.marketLabel} · ${target.eventName}$left"
    }

    /** Why a bet's notification wouldn't show on this phone (Android's permission or settings), or null when it will. */
    fun blocked(context: android.content.Context): String? {
        if (!ScanService.canNotify(context)) return "Notifications are not allowed for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Notifications are switched off for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        val importance = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_BET)?.importance
        if (importance == NotificationManager.IMPORTANCE_NONE) return "The \"Auto-bet bets placed\" notification channel is switched off (Android Settings › Apps › Vigilant › Notifications)"
        return null
    }

    fun placed(app: Application, target: BetTarget, bet: TrackedBet, check: CnoBooks.Check, walletLeft: Double? = null, sharp: String? = null) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        // The sharp book that confirmed it (Tj, 2026-10-02), when the check is on: "Pinnacle +2.1% (devigged, 45 sec old, via PinnWire)".
        val text = text(bet, target, check, walletLeft) + (sharp?.let { " · sharp: $it" } ?: "")
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(title(bet, target))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_PLACED))
            .build()
        // Its own notification each (the bet's id is the tag): two bets in one cycle never replace one another.
        runCatching { NotificationManagerCompat.from(app).notify(bet.id, ID_PLACED, n) }
    }

    /** A made-up bet on the real channel ("Send a test notification"): what the next real one looks like, and whether it shows at all. */
    fun sample(app: Application): Boolean {
        if (!ScanService.canNotify(app)) return false
        ensureChannel(app)
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle("Auto-bet \$0.37 · +4.2% EV · Test bet")
            .setContentText("+117 · 3 of 3 books agree · Moneyline · A @ B · wallet \$2.10 left")
            .setStyle(NotificationCompat.BigTextStyle().bigText("This is a test: it's what a real auto-bet notification looks like. +117 · 3 of 3 books agree · Moneyline · A @ B · wallet \$2.10 left"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_SAMPLE))
            .build()
        return runCatching { NotificationManagerCompat.from(app).notify("auto-bet-test", ID_SAMPLE, n) }.isSuccess
    }

    fun stopped(app: Application, title: String, text: String) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_STOP))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(ID_STOP, n) }
    }
}
