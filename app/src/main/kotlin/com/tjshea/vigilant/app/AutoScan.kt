package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.data.alerts.EvAlert
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.scanner.Agreement
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

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
        return state.cnoCandidates(now).filter { state.livePick(it, now).ev >= minEv }
    }

    /** CNO bets to alert on: [cnoCandidates] whose books agree it's +EV at the price shown. Links not yet resolved. */
    fun cno(state: UiState, minEv: Double, now: Long): List<EvAlert> {
        val snap = state.cno.snapshot ?: return emptyList()
        return cnoCandidates(state, minEv, now).mapNotNull { pick ->
            val view = state.booksAt(pick.row.key, now)?.view ?: return@mapNotNull null
            // Judged at the newest Novig price, as the green check is ([UiState.cnoAgrees]).
            val live = state.livePrice(pick.row, now)?.takeIf { it.atMs > snap.fetchedAtMs }
            val judged = if (live != null) pick.row.copy(odds = live.american) else pick.row
            val check = CnoBooks.check(view, judged, pick.live, preferListOdds = (live?.atMs ?: snap.fetchedAtMs) > view.fetchedAtMs)
            if (check.verdict != CnoBooks.Verdict.CONFIRMED) return@mapNotNull null
            val shown = state.livePick(pick, now)
            val link = state.cnoLinks[CnoFeed.linkKey(pick.row)]
            EvAlert(
                scanner = SCANNER_CNO, key = MiniWindow.cnoKey(pick.row), outcomeId = CnoFeed.outcomeIdOf(link),
                bet = pick.row.bet, market = pick.row.market, event = pick.row.event, american = shown.row.odds, ev = shown.ev,
                books = check.twoSided, agreeing = check.agreeing, startsAtMs = pick.row.startsAtMs, link = link, exact = link != null,
            )
        }
    }

    /** Vigilant's bets to alert on: the feed at [now] at or over [minEv], at a Novig price read in the last few minutes, that the books agree on. */
    fun vigilant(state: UiState, minEv: Double, now: Long): List<EvAlert> {
        if (minEv <= 0.0) return emptyList()
        return state.feedAt(now).mapNotNull { o ->
            val ev = o.evPercent ?: return@mapNotNull null
            val quote = o.quote ?: return@mapNotNull null
            if (ev < minEv || o.priceIsOld(now) || now - (o.bookFetchedAtMs ?: 0L) > FRESH_PRICE_MS) return@mapNotNull null
            val agreement = Agreement.of(o)
            if (!agreement.agrees) return@mapNotNull null
            val link = AppBook.betLink(o.outcome.outcomeId, o.outcome.bookRef, state.settings.bookState)
            EvAlert(
                scanner = SCANNER_VIGILANT, key = o.key, outcomeId = o.outcome.outcomeId,
                bet = o.selection, market = o.marketLabel, event = o.eventName, american = quote.priceAmerican, ev = ev,
                books = agreement.twoSided, agreeing = agreement.agreeing, startsAtMs = o.event.startsTs, link = link, exact = link != null,
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
    /** Never two cycles closer than this, however long the last one took. */
    const val MIN_GAP_MS = 30_000L

    /** [minutes] after the last cycle started (no drift from long scans); now when none has run. */
    fun nextAtMs(lastStartMs: Long?, minutes: Int, now: Long): Long =
        lastStartMs?.let { maxOf(it + minutes.coerceAtLeast(1) * 60_000L, now + MIN_GAP_MS) } ?: now
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
 * Nothing here runs on its own: no loop, no timer. The service's alarm calls [cycle].
 */
class AutoScanner(private val app: Application, private val c: AppContainer, private val clock: () -> Long = System::currentTimeMillis) {

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
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mutex = Mutex()

    val running: Boolean get() = mutex.isLocked

    /** One background scan. False when one is already running (or auto-scan is off). */
    suspend fun cycle(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val settings = c.currentSettings()
            if (settings.autoScan == AutoScanMode.OFF) return false
            val start = clock()
            _status.update { it.copy(running = true, step = "Starting", lastStartMs = start, lastError = null) }
            val alerts = ArrayList<EvAlert>()
            val errors = ArrayList<String>()
            try {
                c.ensureLoaded()
                if (settings.autoScan.cno) {
                    _status.update { it.copy(step = "Reading CrazyNinjaOdds") }
                    runCatching { alerts += cnoCheck(settings) }.onFailure { if (it is CancellationException) throw it; errors += "CNO: ${it.message ?: it.javaClass.simpleName}" }
                }
                if (settings.autoScan.vigilant && settings.leagues.isNotEmpty()) {
                    _status.update { it.copy(step = "Vigilant scan") }
                    runCatching { alerts += vigilantScan(settings) }.onFailure { if (it is CancellationException) throw it; errors += "Vigilant: ${it.message ?: it.javaClass.simpleName}" }
                }
                val sent = send(alerts)
                _status.update { it.copy(lastFound = alerts.distinctBy { a -> a.dedupeKey }.size, lastAlerts = sent) }
            } finally {
                _status.update { it.copy(running = false, step = null, lastEndMs = clock(), lastError = errors.firstOrNull()) }
            }
            return true
        } finally {
            mutex.unlock()
        }
    }

    /**
     * The alerts a Vigilant scan that just ended while Vigilant was off screen should send (Tj's own
     * Scan, left running in the background): the same rules as a background cycle.
     */
    suspend fun afterScan(result: ScanResult?, settings: ScanSettings) {
        if (result == null || settings.alertMinEv <= 0.0) return
        val state = snapshot(settings).copy(result = result).indexed(clock())
        send(AlertPicks.vigilant(state, settings.alertMinEv, clock()))
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

    private suspend fun cnoCheck(settings: ScanSettings): List<EvAlert> {
        // The auto-scan choice asked for CNO, whatever the app's own scanner switch shows.
        val s = if (settings.cnoOn) settings else settings.copy(scanner = ScannerMode.BOTH)
        runCatching { c.cno.load() }
        val url = UiState(settings = s, loaded = true).cnoUrl
        // At most one read per 3 s: a read the widget just made is used as it is.
        c.cno.refresh(url, s.cnoFilters)
        if (s.alertMinEv <= 0.0) return emptyList()
        var state = snapshot(s)
        val wanted = AlertPicks.cnoCandidates(state, s.alertMinEv, clock()).take(CNO_CHECK_TOP)
        if (wanted.isEmpty()) return emptyList()
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
        state = snapshot(s)
        return AlertPicks.cno(state, s.alertMinEv, clock())
    }

    private suspend fun vigilantScan(settings: ScanSettings): List<EvAlert> {
        val before = c.runner.state.value.finished
        // Tj's own scan may be running: then its result is this cycle's.
        c.startVigilantScan(settings, runCatching { c.tracker.all() }.getOrDefault(emptyList()))
        val run = c.runner.state.first { !it.scanning && it.finished > before }
        if (settings.alertMinEv <= 0.0) return emptyList()
        val state = snapshot(settings).copy(result = run.result).indexed(clock())
        return AlertPicks.vigilant(state, settings.alertMinEv, clock())
    }

    /**
     * Posts the alerts not sent before, best EV first, at most [MAX_ALERTS] a cycle: CNO bets get their
     * exact bet-slip link first (Novig's catalog, else CNO's), and a bet both scanners list alerts once.
     * Returns how many went out.
     */
    private suspend fun send(alerts: List<EvAlert>): Int {
        if (alerts.isEmpty()) return 0
        var fresh = c.alertLog.unseen(alerts)
        if (fresh.isEmpty()) return 0
        fresh = fresh.take(MAX_ALERTS * 2).map { a -> if (a.link != null || a.scanner != AlertPicks.SCANNER_CNO) a else withLink(a) }
        // Resolved links name Novig's outcome: the same bet from the other scanner drops out here.
        fresh = c.alertLog.unseen(fresh).take(MAX_ALERTS)
        if (fresh.isEmpty()) return 0
        val posted = EvAlerts.post(app, fresh)
        if (posted > 0) runCatching { c.alertLog.record(fresh) }
        return posted
    }

    private suspend fun withLink(a: EvAlert): EvAlert {
        val row = c.cno.state.value.snapshot?.rows?.firstOrNull { MiniWindow.cnoKey(it) == a.key } ?: return a
        val found = runCatching { c.betLink(row) }.getOrNull() ?: return a
        return a.copy(link = found.link, exact = found.exact, outcomeId = CnoFeed.outcomeIdOf(found.link) ?: a.outcomeId)
    }

    companion object {
        /** CNO bets at or over the alert minimum whose books and Novig price a cycle reads. */
        const val CNO_CHECK_TOP = 8

        /** Most alerts one cycle posts (the best EVs); the rest show in the app. */
        const val MAX_ALERTS = 5
    }
}
