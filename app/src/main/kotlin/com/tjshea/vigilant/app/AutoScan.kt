package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.data.alerts.EvAlert
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.scanner.Agreement
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.ClosingLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Which bets are worth a push alert (Tj, 2026-09-28: "positive EV bets of 3% or higher and multiple
 * books agree on the price"): only bets the lists would show (placed and removed ones left out, inside
 * "Starts within", every filter applied), at or over the alert minimum, and backed by the books:
 * CNO's by the green check's test on its game page ([CnoBooks.check]), Vigilant's by the same test on
 * the books behind its fair line ([Agreement]). Pure: the background cycle and the tests call it.
 */
object AlertPicks {

    /**
     * CNO's bets at or over [minEv] at the price shown (Novig's price now when it was read), best first:
     * the ones whose books and live price a cycle reads before judging them.
     */
    fun cnoCandidates(state: UiState, minEv: Double, now: Long): List<CnoPick> {
        if (minEv <= 0.0) return emptyList()
        val hours = state.settings.trapEarlyHours
        // The trap guard's first rule ([TrapGuard.early]): a game too far off is neither alerted on nor bet, so its books aren't read for either.
        return state.cnoCandidates(now).filter { state.livePick(it, now).ev >= minEv && !TrapGuard.isEarly(it.row.startsAtMs, now, hours) }
    }

    /** CNO's bets at or over [minEv] that the trap guard leaves alone for starting too far off (the auto-bet's report counts them). */
    fun tooEarly(state: UiState, minEv: Double, now: Long): Int {
        if (minEv <= 0.0 || state.settings.trapEarlyHours <= 0) return 0
        return state.cnoCandidates(now).count { TrapGuard.isEarly(it.row.startsAtMs, now, state.settings.trapEarlyHours) && state.livePick(it, now).ev >= minEv }
    }

    /**
     * A CNO bet at or over the minimum, with what its game page's books say about it ([CnoBooks.check]) judged at Novig's newest price: what an
     * alert and the auto-bet both decide from, so the two can't disagree about a bet. [live]: Novig's own price for it read in the last minute,
     * null when there isn't one (the auto-bet places nothing on a price it didn't just read).
     */
    data class CnoChecked(val pick: CnoPick, val shown: CnoPick, val check: CnoBooks.Check, val live: com.tjshea.vigilant.data.cno.LivePrice?, val link: String?)

    /** [cnoCandidates] that have a books read (fresh enough to compare: [UiState.booksAt]), each with its [CnoChecked.check], best first as listed. */
    fun cnoChecked(state: UiState, minEv: Double, now: Long): List<CnoChecked> {
        val snap = state.cno.snapshot ?: return emptyList()
        return cnoCandidates(state, minEv, now).mapNotNull { pick ->
            val view = state.booksAt(pick.row.key, now)?.view ?: return@mapNotNull null
            // Judged at the newest Novig price, as the green check is ([UiState.cnoAgrees]).
            val livePrice = state.livePrice(pick.row, now)
            val live = livePrice?.takeIf { it.atMs > snap.fetchedAtMs }
            val judged = if (live != null) pick.row.copy(odds = live.american) else pick.row
            val check = CnoBooks.check(view, judged, pick.live, preferListOdds = (live?.atMs ?: snap.fetchedAtMs) > view.fetchedAtMs)
            CnoChecked(pick, state.livePick(pick, now), check, livePrice, state.cnoLinks[CnoFeed.linkKey(pick.row)])
        }
    }

    /** CNO bets to alert on: [cnoCandidates] whose books agree it's +EV at the price shown. Links not yet resolved. */
    fun cno(state: UiState, minEv: Double, now: Long): List<EvAlert> =
        cnoChecked(state, minEv, now).mapNotNull { (pick, shown, check, _, link) ->
            if (check.verdict != CnoBooks.Verdict.CONFIRMED) return@mapNotNull null
            EvAlert(
                scanner = SCANNER_CNO, key = MiniWindow.cnoKey(pick.row), outcomeId = CnoFeed.outcomeIdOf(link),
                bet = pick.row.bet, market = pick.row.market, event = pick.row.event, american = shown.row.odds, ev = shown.ev,
                books = check.twoSided, agreeing = check.agreeing, startsAtMs = pick.row.startsAtMs, link = link, exact = link != null,
                stake = state.settings.slipStakeFor(com.tjshea.vigilant.app.ui.cnoStake(shown, state.settings)),
                league = pick.row.league, gameUrl = pick.row.gameUrl, betUrl = pick.row.betUrl,
                fair = com.tjshea.vigilant.data.cno.CnoChecks.fairProbability(pick.row), live = pick.live, book = pick.row.book,
            )
        }

    /** Vigilant's bets to alert on: the feed at [now] at or over [minEv], at a Novig price read in the last few minutes, that the books agree on. */
    fun vigilant(state: UiState, minEv: Double, now: Long): List<EvAlert> {
        if (minEv <= 0.0) return emptyList()
        return state.feedAt(now).mapNotNull { o ->
            val ev = o.evPercent ?: return@mapNotNull null
            val quote = o.quote ?: return@mapNotNull null
            if (ev < minEv || o.priceIsOld(now) || now - (o.bookFetchedAtMs ?: 0L) > FRESH_PRICE_MS) return@mapNotNull null
            if (TrapGuard.isEarly(o.event.startsTs, now, state.settings.trapEarlyHours)) return@mapNotNull null
            val agreement = Agreement.of(o)
            if (!agreement.agrees) return@mapNotNull null
            val link = AppBook.betLink(o.outcome.outcomeId, o.outcome.bookRef, state.settings.bookState)
            EvAlert(
                scanner = SCANNER_VIGILANT, key = o.key, outcomeId = o.outcome.outcomeId,
                bet = o.selection, market = o.marketLabel, event = o.eventName, american = quote.priceAmerican, ev = ev,
                books = agreement.twoSided, agreeing = agreement.agreeing, startsAtMs = o.event.startsTs, link = link, exact = link != null,
                stake = state.settings.slipStakeFor(o.suggestedStake),
                league = o.league.displayName, marketId = o.market.marketId, fair = o.fairProbability, live = o.isLive, book = AppBook.name,
            )
        }
    }

    const val SCANNER_CNO = "CNO"
    const val SCANNER_VIGILANT = "Vigilant"

    /** A Vigilant bet alerts only on a Novig price read this recently (a scan's end re-reads its early edges). */
    const val FRESH_PRICE_MS = 3 * 60_000L
}

/** When the next background scan is due. */
object AutoScanClock {
    /** Never two cycles closer than this, however long the last one took (at the 15 s interval and slower; see [minGapMs]). */
    const val MIN_GAP_MS = 5_000L

    /** The least wait after a cycle that ran long: [MIN_GAP_MS], but 1 s at the 5 s interval, whose cadence would otherwise be a cycle plus 5 s, never 5 s. */
    fun minGapMs(seconds: Int): Long = if (seconds < 15) 1_000L else MIN_GAP_MS

    /** [seconds] after the last cycle started (no drift from long scans); [minGapMs] after now when that has passed; now when none has run. */
    fun nextAtMs(lastStartMs: Long?, seconds: Int, now: Long): Long =
        lastStartMs?.let { maxOf(it + seconds.coerceAtLeast(1) * 1_000L, now + minGapMs(seconds)) } ?: now

    /**
     * How recent a read of a bet in its last [ClosingLine.TRUE_CLOSE_MS] must be to skip it this cycle, when cycles are faster than the usual
     * 5 minutes ([com.tjshea.vigilant.data.tracker.BetRecheck.CLOSING_FRESH_MS]): the cycle's own gap, but never under a minute (one CNO page
     * per bet per minute at most, however fast the cycle). Null at 5 minutes and slower: the usual rule is already as fast as the cycle.
     */
    fun closingFreshMs(seconds: Int): Long? =
        if (seconds * 1_000L >= com.tjshea.vigilant.data.tracker.BetRecheck.CLOSING_FRESH_MS) null else maxOf(seconds, 60) * 1_000L

    /** Alarm jitter: a Vigilant scan counts as due this much before its gap has fully passed. */
    private const val JITTER_MS = 5_000L

    /**
     * Whether a cycle at [seconds] runs Vigilant's own scan (Tj, 2026-10-01: CNO may be read every 15 s, but a Vigilant scan spends API credits
     * and takes ~100 s): every cycle when they're [gapSeconds] ([ScanSettings.vigilantGapSeconds]: the usual [ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS], or the
     * low-usage bids' pace) or more apart, else only once that long has passed since the last one started ([lastVigilantStartMs]; null = none yet this run of the app).
     */
    fun vigilantDue(lastVigilantStartMs: Long?, seconds: Int, now: Long, gapSeconds: Int = ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS): Boolean =
        seconds >= gapSeconds || lastVigilantStartMs == null || now - lastVigilantStartMs + JITTER_MS >= gapSeconds * 1_000L
}

/**
 * The background auto-scan (Tj, 2026-09-28: "run the app in the background and it will continue
 * scanning … auto scan either cno or both cno and vigilant every 5 10 20 30 or 40 minutes … even if
 * the app is not open on the screen"). One [cycle] at a time, run by [AutoScanService]:
 *
 *  1. CrazyNinjaOdds' list is read (one request), and for its bets at or over the alert minimum,
 *     Novig's price now (one order book each) and every book's price on CNO's game page (one page
 *     every few seconds, re-used for four minutes): the green check's reads, for the top few only.
 *  2. With "CNO + Vigilant", Vigilant's own scan, exactly as the Scan button runs it (its results are
 *     what the app shows when opened, and its API credits are spent the same way).
 *  3. Each bet that qualifies ([AlertPicks]) and hasn't alerted before ([com.tjshea.vigilant.data.alerts.AlertLog])
 *     gets a notification that opens it in Novig ([EvAlerts]).
 *
 * Nothing here runs on its own: no loop, no timer. [AutoScanService] calls [cycle], from its own loop while it holds the CPU awake
 * ([KeepAwake]) and from its alarm otherwise.
 *
 * Fast intervals (Tj, 2026-10-01: 15 s, 30 s, 1 min, 3 min): the CNO half is cheap and runs every cycle; Vigilant's own scan, which spends API
 * credits and takes ~100 s, starts at most every [ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS] ([AutoScanClock.vigilantDue]), and the cycle
 * after it reads CNO again at once.
 */
class AutoScanner(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Whether the screen is off and Android's Doze is on, as a cycle starts ([CycleLog]'s evidence that scanning goes on while the phone idles). */
    private val phone: () -> Pair<Boolean, Boolean> = { screenOffAndDozing(app) },
    /** Starts Vigilant's scan for a cycle (false: not started, one may already be running); tests stand in their own. */
    private val startVigilant: suspend (ScanSettings, List<com.tjshea.vigilant.data.tracker.TrackedBet>) -> Boolean = { s, bets -> c.startVigilantScan(s, bets, background = true) },
) {

    data class Status(
        val running: Boolean = false,
        /** What the running cycle is doing ("Reading CNO", "Vigilant scan"). */
        val step: String? = null,
        val lastStartMs: Long? = null,
        val lastEndMs: Long? = null,
        /** Bets at or over the alert minimum that the books agreed on, last cycle. */
        val lastFound: Int = 0,
        /** Alerts sent last cycle (new bets only). */
        val lastAlerts: Int = 0,
        val lastError: String? = null,
        /** Check odds now holds the focus: this cycle didn't run ([FocusGate]); one runs as soon as the check ends. */
        val pausedForCheck: Boolean = false,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mutex = Mutex()

    /** When this run of the app last started a background Vigilant scan ([AutoScanClock.vigilantDue]). Only [cycle] touches it, under [mutex]. */
    private var lastVigilantStartMs: Long? = null

    val running: Boolean get() = mutex.isLocked

    /** The check that held the focus is over: the status stops saying so. */
    fun resumed() = _status.update { if (it.pausedForCheck) it.copy(pausedForCheck = false) else it }

    /**
     * One background scan. False when one is already running (or auto-scan is off, or scanning paused). [forceVigilant]: Tj tapped Scan now, so
     * Vigilant's own scan runs even if one started less than [ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS] ago ([AutoScanClock.vigilantDue]).
     */
    suspend fun cycle(forceVigilant: Boolean = false): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val settings = c.currentSettings()
            if (settings.activeAutoScan == AutoScanMode.OFF) return false
            // Check odds now has CNO's pace, the APIs and the phone to itself (Tj, 2026-10-01): no CNO read, no auto-bet and no scan from this cycle, and
            // the schedule goes on (the next one is armed as ever; the check's end starts one). Nothing is lost that the check doesn't read itself.
            if (c.focus.active(clock())) {
                _status.update { it.copy(pausedForCheck = true) }
                return false
            }
            val start = clock()
            val afterPause = _status.value.pausedForCheck
            val (screenOff, dozing) = runCatching { phone() }.getOrDefault(false to false)
            _status.update { it.copy(running = true, step = "Starting", lastStartMs = start, lastError = null, pausedForCheck = false) }
            val alerts = ArrayList<EvAlert>()
            val errors = ArrayList<String>()
            try {
                c.ensureLoaded()
                // The wallet's balance for this cycle's notifications (Tj, 2026-10-02 21:51Z): read again only when the last is over 30 s old.
                runCatching { c.wallet.fresh() }.onFailure { if (it is CancellationException) throw it }
                // The scanner choice is the master switch: CNO only puts Vigilant's scan to sleep here too (no API credits spent in the
                // background), Vigilant only does the same for CNO ([ScanSettings.autoScansVigilant], [ScanSettings.autoScansCno]).
                if (settings.autoScansCno) {
                    _status.update { it.copy(step = "Reading CrazyNinjaOdds") }
                    // Auto-bet places nothing on a price CNO listed a while ago: Novig's own price now is read whatever the live-price setting says.
                    val s = if (settings.autoBetsNow) settings.copy(cnoLivePrices = true) else settings
                    runCatching { timed("cno") { cnoRead(s) } }.onFailure { if (it is CancellationException) throw it; errors += "CNO: ${it.message ?: it.javaClass.simpleName}" }
                    // Bets first (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all"), then the alerts: a bet just placed is
                    // out of the candidates, so it doesn't also alert.
                    if (s.autoBetsNow) {
                        _status.update { it.copy(step = "Auto-bet") }
                        runCatching { timed("autobet") { c.autoBet.run(s, snapshot(s)) } }.onFailure { if (it is CancellationException) throw it; errors += "Auto-bet: ${it.message ?: it.javaClass.simpleName}" }
                        // The wallet ran out and auto-bet put Vigilant to sleep (Tj, 2026-10-02 ~22:10Z): the rest of this cycle doesn't run either.
                        if (c.currentSettings().paused) return true
                    }
                    runCatching { timed("alerts") { alerts += cnoAlerts(s) } }.onFailure { if (it is CancellationException) throw it; errors += "CNO: ${it.message ?: it.javaClass.simpleName}" }
                    // The closing line of the open bets about to start, for the Tracker's CLV (Tj, 2026-09-29): the last read before the start.
                    _status.update { it.copy(step = "Open bets about to start") }
                    runCatching {
                        timed("closing") {
                            c.recheck.captureClosing()
                            // Faster than every 5 minutes (Tj, 2026-10-01): bets in their last 15 minutes are re-read at this cycle's pace, so the last read
                            // before the start (the close) is as near the start as the schedule is, not up to 5 minutes before it.
                            AutoScanClock.closingFreshMs(settings.autoScanSeconds)?.let { c.recheck.captureClosing(withinMs = ClosingLine.TRUE_CLOSE_MS, freshMs = it) }
                        }
                    }.onFailure { if (it is CancellationException) throw it; errors += "Tracker: ${it.message ?: it.javaClass.simpleName}" }
                }
                // Locks on the bets already placed (Tj, 2026-10-02 ~18:50Z), whatever the scanner: only Novig's books for the markets the subaccount holds.
                if (settings.autoLocksNow) {
                    _status.update { it.copy(step = "Auto-lock") }
                    runCatching { timed("autolock") { c.autoLock.run(settings) } }.onFailure { if (it is CancellationException) throw it; errors += "Auto-lock: ${it.message ?: it.javaClass.simpleName}" }
                }
                var scanned = false
                if (settings.autoScansVigilant && settings.leagues.isNotEmpty() && (forceVigilant || AutoScanClock.vigilantDue(lastVigilantStartMs, settings.autoScanSeconds, clock(), settings.vigilantGapSeconds))) {
                    _status.update { it.copy(step = "Vigilant scan") }
                    lastVigilantStartMs = clock()
                    scanned = true
                    runCatching { timed("vigilant") { vigilantScan(settings) } }.onFailure { if (it is CancellationException) throw it; errors += "Vigilant: ${it.message ?: it.javaClass.simpleName}" }
                }
                // Make orders (RESEARCH.md §70): fills, expiries, the start coming up and fair prices going old are checked every cycle; a cycle that
                // scanned has its pass from the scan's end ([AppContainer]).
                if (!settings.paused && AppBook.isNovig && (settings.maker || settings.makerRecommend) && !scanned) {
                    _status.update { it.copy(step = "Make orders") }
                    runCatching { timed("maker") { c.maker.run("background cycle", minGapMs = MakerRunner.BACKGROUND_GAP_MS) } }.onFailure { if (it is CancellationException) throw it; errors += "Make orders: ${it.message ?: it.javaClass.simpleName}" }
                }
                val sent = send(alerts)
                _status.update { it.copy(lastFound = alerts.distinctBy { a -> a.dedupeKey }.size, lastAlerts = sent) }
            } finally {
                _status.update { it.copy(running = false, step = null, lastEndMs = clock(), lastError = errors.firstOrNull()) }
                withContext(NonCancellable) {
                    // The scan study's log of what this cycle read, finished and written to the disk before the wake lock goes (the study's watchers do the same as
                    // things arrive, but in the alarm-only mode they get no CPU once the cycle ends; Tj, 2026-10-03: "confirm that all the betting data is being logged
                    // even when the app is backgrounded but in auto scan background mode"). Bounded, and never an error of the cycle.
                    runCatching { kotlinx.coroutines.withTimeoutOrNull(STUDY_SYNC_MS) { c.studySync.catchUp(start) } }
                    errors.forEach { e -> runCatching { c.problems.add("Background auto-scan", e) } }
                    runCatching { c.cycleLog.record(start, clock(), settings.autoScanSeconds, screenOff, dozing, afterPause) }
                    // The flight recorder: how long the cycle took (Diagnostics' performance block), and a line when it ran long.
                    val tookMs = clock() - start
                    c.perf.add("cycle.ms", tookMs.toDouble())
                    c.eventLog.count("cycle.runs")
                    if (errors.isNotEmpty()) c.eventLog.count("cycle.errors")
                    if (slowCycle(tookMs, settings.autoScanSeconds)) {
                        c.eventLog.warn("CYCLE", "a background cycle took ${tookMs / 1_000} s (its interval is ${ScanSettings.intervalLabel(settings.autoScanSeconds)})", tookMs)
                    }
                }
            }
            return true
        } finally {
            mutex.unlock()
        }
    }

    /** Runs [block] and records how long it took as the flight recorder's `cycle.step.<name>` (Diagnostics says which step is the slow one). */
    private inline fun <T> timed(name: String, block: () -> T): T {
        val t = clock()
        try {
            return block()
        } finally {
            c.perf.add("cycle.step.$name", (clock() - t).toDouble())
        }
    }

    /**
     * The alerts a Vigilant scan that just ended while Vigilant was off screen should send (Tj's own
     * Scan, left running in the background): the same rules as a background cycle.
     */
    suspend fun afterScan(result: ScanResult?, settings: ScanSettings): Int {
        if (result == null || settings.alertMinEv <= 0.0) return 0
        val state = snapshot(settings).copy(result = result).indexed(clock())
        return send(AlertPicks.vigilant(state, settings.alertMinEv, clock()))
    }

    /** Every list's inputs as the app would show them now, for [settings]: one [UiState], no screen needed. */
    private suspend fun snapshot(settings: ScanSettings): UiState = UiState(
        settings = settings,
        loaded = true,
        cno = c.cno.state.value,
        books = c.cno.books.value,
        placed = (c.placed.flow.value ?: runCatching { c.placed.load() }.getOrNull())?.bets.orEmpty(),
        bets = runCatching { c.tracker.all() }.getOrDefault(emptyList()),
        cnoLinks = c.cno.links.value,
        novigLive = c.live.prices.value,
        result = c.runner.state.value.result,
    ).indexed(clock())

    /**
     * The lowest edge a cycle reads books for: the alert minimum, and the auto-bet's when it's on (it may be lower). Null = nothing needs reading
     * beyond CNO's list.
     */
    private fun readThreshold(s: ScanSettings): Double? =
        listOfNotNull(s.alertMinEv.takeIf { it > 0.0 }, AutoBet.rules(s).minEv.takeIf { s.autoBetsNow }).minOrNull()

    /**
     * CNO's list, then for its best bets Novig's price now and every book's odds on CNO's game page (the green check's reads), all of it for
     * [AlertPicks] and [AutoBettor] to judge. Only reached with the CNO scanner on ([ScanSettings.autoScansCno]).
     */
    private suspend fun cnoRead(s: ScanSettings) {
        runCatching { c.cno.load() }
        val url = UiState(settings = s, loaded = true).cnoUrl
        // At most one read per 3 s: a read the widget just made is used as it is.
        c.cno.refresh(url, s.cnoFilters)
        val minEv = readThreshold(s) ?: return
        var state = snapshot(s)
        val wanted = AlertPicks.cnoCandidates(state, minEv, clock()).take(if (s.autoBetsNow) AUTO_BET_CHECK_TOP else CNO_CHECK_TOP)
        if (wanted.isEmpty()) return
        // Novig's price now: a bet CNO listed a while ago may be gone. Novig's own order books.
        if (AppBook.isNovig && s.cnoLivePrices) runCatching { c.live.readNow(wanted.map { it.row }) }
        // Each bet's books on CNO's game page, as the green check reads them: one every few seconds.
        var read = 0
        for (pick in wanted) {
            val now = clock()
            if ((c.cno.state.value.pausedUntilMs ?: 0L) > now) break
            val have = c.cno.books.value[pick.row.key]?.view
            if (have != null && now - have.fetchedAtMs < CnoFeed.AGREE_TTL_MS) continue
            if (read++ > 0) delay(CnoFeed.AGREE_GAP_MS)
            runCatching { c.cno.loadBooks(pick.row, maxAgeMs = CnoFeed.AGREE_TTL_MS) }.onFailure { if (it is CancellationException) throw it }
        }
    }

    /** The alerts the cycle's reads support: [AlertPicks.cno] over the newest state. */
    private suspend fun cnoAlerts(s: ScanSettings): List<EvAlert> {
        if (s.alertMinEv <= 0.0) return emptyList()
        val state = snapshot(s)
        val alerts = AlertPicks.cno(state, s.alertMinEv, clock())
        if (alerts.isEmpty()) return alerts
        val now = clock()
        // The sharp veto (the default, Tj 2026-10-02 17:01Z): an alert whose bet the sharpest book for its kind says isn't +EV is dropped (free: its book page).
        if (s.sharpAlerts == com.tjshea.vigilant.data.scanner.SharpMode.VETO) {
            return SharpGate.unvetoedAlerts(alerts, AlertPicks.cnoChecked(state, s.alertMinEv, now), { state.booksAt(it.pick.row.key, now)?.view }, s.sharpVetoMinEv) {
                c.eventLog.count("sharp.alertveto.${it.verdict}")
                if (it.vetoed && (it.ev ?: 0.0) > 0.0) c.eventLog.count(AutoBettor.SHARP_BAR_ALERT_COUNTER)
            }
        }
        // Sharp-book confirmation for the alerts (Tj, 2026-10-02): only a bet that would alert now is asked about, and only then is a feed called.
        val rules = com.tjshea.vigilant.data.scanner.SharpConfirm.rules(s, autoBet = false) ?: return alerts
        return SharpGate.confirmedAlerts(alerts, c.alertLog.unseen(alerts), AlertPicks.cnoChecked(state, s.alertMinEv, now)) { item ->
            val row = item.shown.row
            SharpGate.check(
                c.sharp, rules, com.tjshea.vigilant.data.reference.SharpBooks.Bet(row.league, row.event, row.startsAtMs, row.market, row.bet),
                state.booksAt(item.pick.row.key, now)?.view, row.odds, item.pick.live, now,
            ).also { c.eventLog.count("sharp.alert.${it.verdict}") }
        }
    }

    /**
     * Starts Vigilant's scan and returns: its alerts go out when it ends ([vigilantAlerts], owned by the app's scope), and the cycles go on meanwhile.
     * The cycle used to wait the scan out (Tj's v0.52.0 file: "a background cycle took 456 s (its interval is 30 sec)"), and for those minutes CNO
     * wasn't read and auto-bet placed nothing. Tj's own scan may be running already: then its end is this one's.
     */
    private suspend fun vigilantScan(settings: ScanSettings) {
        val before = c.runner.state.value.finished
        val bets = runCatching { c.tracker.all() }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        val started = startVigilant(settings, bets)
        if (!started && !c.runner.state.value.scanning) return
        if (settings.alertMinEv <= 0.0) return
        vigilantAlerts?.takeIf { it.isActive }?.let { return }
        vigilantAlerts = c.appScope.launch {
            val run = c.runner.state.first { !it.scanning && it.finished > before }
            val s = c.currentSettings()
            if (s.alertMinEv <= 0.0 || !s.autoScansVigilant) return@launch
            runCatching {
                val state = snapshot(s).copy(result = run.result).indexed(clock())
                send(AlertPicks.vigilant(state, s.alertMinEv, clock()))
            }.onFailure { if (it is CancellationException) throw it; runCatching { c.problems.add("Background auto-scan", "Vigilant alerts: ${it.message ?: it.javaClass.simpleName}") } }
        }
    }

    /** The watch that sends a background Vigilant scan's alerts when it ends (one at a time). */
    @Volatile private var vigilantAlerts: kotlinx.coroutines.Job? = null

    /**
     * Posts the alerts not sent before, best EV first, at most [MAX_ALERTS] a cycle: CNO bets get their
     * exact bet-slip link first (Novig's catalog, else CNO's), and a bet both scanners list alerts once.
     * Returns how many went out.
     */
    private suspend fun send(alerts: List<EvAlert>): Int = sending.withLock {
        if (alerts.isEmpty()) return@withLock 0
        var fresh = c.alertLog.unseen(alerts)
        if (fresh.isEmpty()) return@withLock 0
        fresh = fresh.take(MAX_ALERTS * 2).map { a -> if (a.link != null || a.scanner != AlertPicks.SCANNER_CNO) a else withLink(a) }
        // Resolved links name Novig's outcome: the same bet from the other scanner drops out here.
        fresh = c.alertLog.unseen(fresh).take(MAX_ALERTS)
        if (fresh.isEmpty()) return@withLock 0
        val posted = EvAlerts.post(app, fresh)
        if (posted > 0) {
            runCatching { c.alertLog.record(fresh) }
            c.eventLog.info("ALERT", "sent $posted +EV alert${if (posted == 1) "" else "s"}: " + fresh.take(posted).joinToString("; ") { "${it.bet} (${it.scanner}, ${"%+.1f".format(java.util.Locale.US, it.ev * 100)}%)" }.take(160))
            c.eventLog.count("alerts.sent", posted.toLong())
        }
        posted
    }

    /**
     * One [send] at a time: a background cycle waiting on Tj's own scan and that scan's end both
     * send, and "not alerted yet" then "record it" must not interleave (the same bet twice).
     */
    private val sending = Mutex()

    private suspend fun withLink(a: EvAlert): EvAlert {
        val row = c.cno.state.value.snapshot?.rows?.firstOrNull { MiniWindow.cnoKey(it) == a.key } ?: return a
        val found = runCatching { c.betLink(row) }.getOrNull() ?: return a
        return a.copy(link = found.link, exact = found.exact, outcomeId = CnoFeed.outcomeIdOf(found.link) ?: a.outcomeId)
    }

    companion object {
        /** The most a cycle's end waits for the scan study's catch-up (a wide read, a few pages' checks, one write). */
        const val STUDY_SYNC_MS = 25_000L

        /** A cycle that took more than three intervals (and over half a minute) is worth a line in the timeline: the schedule couldn't be kept. */
        fun slowCycle(tookMs: Long, autoScanSeconds: Int): Boolean = tookMs > maxOf(30_000L, 3L * autoScanSeconds * 1_000L)

        /** CNO bets at or over the alert minimum whose books and Novig price a cycle reads. */
        const val CNO_CHECK_TOP = 8

        /** The same with auto-bet on: a few more, since each one may be bet (the books are kept 4 minutes, so a repeat costs nothing). */
        const val AUTO_BET_CHECK_TOP = 10

        /** Most alerts one cycle posts (the best EVs); the rest show in the app. */
        const val MAX_ALERTS = 5

        /** (screen off, Doze on) now. */
        fun screenOffAndDozing(context: android.content.Context): Pair<Boolean, Boolean> {
            val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return false to false
            return !pm.isInteractive to pm.isDeviceIdleMode
        }
    }
}
