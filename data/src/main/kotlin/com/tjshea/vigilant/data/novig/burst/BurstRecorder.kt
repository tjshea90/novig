package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookChange
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.scanner.TrapGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TreeMap

/** What the recorder is doing, for Settings and Diagnostics. */
data class BurstStatus(
    val running: Boolean = false,
    val sinceMs: Long? = null,
    val leagues: Set<String> = emptySet(),
    val games: Int = 0,
    val lines: Int = 0,
    val updates: Long = 0L,
    val open: Int = 0,
    val windows: Int = 0,
    val lastWindowMs: Long? = null,
    val problem: String? = null,
)

/** A market's book rebuilt from the changes that arrived, in the order they arrived: what the phone saw, so a paper trade is judged on exactly that. */
internal class ReplayBook(private val marketId: String) {
    private val levels = HashMap<String, TreeMap<Int, Long>>()
    private var valid = false

    fun apply(changes: List<BookChange>) {
        for (c in changes) when (c.kind) {
            BookChange.Kind.CLEAR -> { levels.clear(); valid = true }
            BookChange.Kind.ADD -> levels.getOrPut(c.outcome) { TreeMap() }.merge(c.priceMilli, c.qty, Long::plus)
            BookChange.Kind.REMOVE -> levels[c.outcome]?.let { m ->
                val left = (m[c.priceMilli] ?: 0L) - c.qty
                if (left <= 0L) m.remove(c.priceMilli) else m[c.priceMilli] = left
            }
            BookChange.Kind.STALE -> valid = false
        }
    }

    /** The book as of now, or null until the first snapshot or after a gap. */
    fun book(atMs: Long): NovigBook? =
        if (!valid) null else NovigBook(marketId, 0L, levels.mapValues { (_, m) -> m.descendingMap().map { (p, q) -> BidLevel(p, q) } }, atMs)
}

/**
 * The score-burst recorder (Tj, 2026-10-06: "Build a no orders recorder of the score burst idea to see if it works with my current setup and novig key"; RESEARCH.md §95).
 *
 * It watches the moneyline, spread and total books of the live games of the picked leagues on its OWN websocket connection ([newFeed]: the key's READ scope, so nothing in
 * here can place or cancel an order, whatever the code did), finds the moments a cover costs under $1 after the in-play fee ([CoverWindows]), and for each one asks: if an
 * order of Tj's had been sent the moment the window was seen, with HIS measured delays ([LatencyModel]: the signed round trip to Novig and the push delay), would the cover still
 * have been there? ([PaperTrader]). Every window and its paper trades go to the [BurstJournal]. [BurstStudy] turns the journal into the verdict.
 *
 * One consumer coroutine owns all the state and handles messages in the order they were queued (book pushes with their arrival time, the clock's ticks, the catalog, the push-delay
 * probe's trades), so replaying the same messages gives the same result; the socket thread only hands work on.
 */
class BurstRecorder(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    /** Opens the recorder's own connection on the READ key, told of every book change; null when no key is connected. */
    private val newFeed: (BookListener) -> PushedBooks?,
    /** One signed `POST /v3/echo` (free): its round trip is the delay of an order's path. Throws on failure. */
    private val echo: suspend () -> Unit,
    /** A market's recent trades from the public route (the engine's own time on each): the other end of the push delay. */
    private val trades: suspend (marketId: String) -> List<TrapGuard.Trade>,
    private val journal: BurstJournal,
    private val clock: () -> Long = System::currentTimeMillis,
    val latency: LatencyModel = LatencyModel(),
    private val discoverEveryMs: Long = DISCOVER_MS,
    private val echoEveryMs: Long = ECHO_MS,
    private val probeEveryMs: Long = PROBE_MS,
    private val tickMs: Long = TICK_MS,
    /** Where the journal is written: the disk's dispatcher (tests use their own so a write is done when they look). */
    private val ioContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
) {
    private val _status = MutableStateFlow(BurstStatus())
    val status: StateFlow<BurstStatus> = _status.asStateFlow()

    @Volatile
    private var job: Job? = null

    @Volatile
    private var capDollars = 0.0

    val running: Boolean get() = job?.isActive == true

    /** Starts recording the live games of [leagues] (Novig's league names), paper-trading each leg at no more than [capDollars] (Tj's own per-bet limit; 0 = no limit). No-op when running. */
    @Synchronized
    fun start(leagues: Set<String>, capDollars: Double) {
        this.capDollars = capDollars
        if (running || leagues.isEmpty()) return
        _status.value = BurstStatus(running = true, sinceMs = clock(), leagues = leagues)
        job = scope.launch { runLoop(leagues) }
    }

    /** Stops: every open window is closed and written, every game's coverage is written, the connection is closed. */
    @Synchronized
    fun stop(why: String? = null) {
        val j = job ?: return
        job = null
        j.cancel()
        _status.value = _status.value.copy(running = false, problem = why ?: _status.value.problem)
    }

    // ---- the loop -----------------------------------------------------------------------------------------------------------

    private sealed interface Msg {
        class Book(val marketId: String, val atMs: Long, val changes: List<BookChange>) : Msg
        class Catalog(val events: List<NovigEvent>, val lines: List<LadderLine>, val atMs: Long) : Msg
        class Tick(val atMs: Long) : Msg
        class Trades(val marketId: String, val trades: List<TrapGuard.Trade>) : Msg
    }

    private class Game(val event: NovigEvent, val fromMs: Long, var lines: List<LadderLine>) {
        val windows = CoverWindows()
        var updates = 0L
    }

    private class Eval(val latency: Latency, val dueMs: Long, var result: ProfileResult? = null)

    private class Pending(val game: Game, val window: OpenWindow, val evals: List<Eval>, var closed: ClosedWindow? = null)

    private class Fill(val marketId: String, val outcome: String, val priceMilli: Int, val qty: Long, val atMs: Long, var matched: Boolean = false)

    private suspend fun runLoop(leagues: Set<String>) {
        reset()
        val queue = Channel<Msg>(Channel.UNLIMITED)
        val listener = BookListener { marketId, atMs, changes -> queue.trySend(Msg.Book(marketId, atMs, changes)) }
        val feed = newFeed(listener)
        if (feed == null) {
            _status.value = _status.value.copy(running = false, problem = "No Novig key is connected: the recorder needs the read key's live feed (Settings › Betting & Novig account).")
            return
        }
        val helpers = ArrayList<Job>()
        try {
            helpers += scope.launch { discover(leagues, feed, queue) }
            helpers += scope.launch { echoLoop() }
            helpers += scope.launch { while (isActive) { delay(tickMs); queue.trySend(Msg.Tick(clock())) } }
            helpers += scope.launch { probeLoop(queue) }
            consume(queue)
        } finally {
            helpers.forEach { it.cancel() }
            runCatching { feed.close() }
            // Written even when cancelled: a stop must not lose what was seen.
            withContext(kotlinx.coroutines.NonCancellable) { shutdown(queue) }
        }
    }

    private suspend fun discover(leagues: Set<String>, feed: PushedBooks, queue: Channel<Msg>) {
        val statuses = listOf(NovigEvent.STATUS_LIVE, NovigEvent.STATUS_DELAYED)
        while (currentCoroutineIsActive()) {
            try {
                val live = source.events(leagues, statuses).filter { it.status in statuses }
                val ids = live.mapTo(HashSet()) { it.eventId }
                val lines = if (live.isEmpty()) emptyList() else source.markets(leagues, Ladders.TYPES, statuses).filter { it.eventId in ids && it.isOpen }.mapNotNull(Ladders::line)
                if (lines.isNotEmpty()) feed.watch(lines.map { it.marketId })
                queue.trySend(Msg.Catalog(live, lines, clock()))
                _status.value = _status.value.copy(problem = feed.problemSince(_status.value.sinceMs ?: 0L))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = _status.value.copy(problem = "Novig's catalog: ${e.message ?: e.javaClass.simpleName}")
            }
            delay(discoverEveryMs)
        }
    }

    private suspend fun currentCoroutineIsActive() = kotlin.coroutines.coroutineContext[Job]?.isActive != false

    private suspend fun echoLoop() {
        while (currentCoroutineIsActive()) {
            val t0 = clock()
            try {
                echo()
                latency.addRoundTrip(clock() - t0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A refusal (451, a lost connection) is not a sample; the status already carries the feed's own problem.
            }
            delay(echoEveryMs)
        }
    }

    /** A few seconds apart, the recent trades of the one or two busiest live games' moneylines: the engine's time on a fill the books also showed. */
    private suspend fun probeLoop(queue: Channel<Msg>) {
        var turn = 0
        while (currentCoroutineIsActive()) {
            delay(probeEveryMs)
            val targets = probeTargets
            if (targets.isEmpty()) continue
            val id = targets[turn++ % targets.size]
            try {
                queue.trySend(Msg.Trades(id, trades(id)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    @Volatile
    private var probeTargets: List<String> = emptyList()

    // ---- the consumer (all state lives here) -----------------------------------------------------------------------------------

    private val games = HashMap<String, Game>()
    private val replay = HashMap<String, ReplayBook>()
    private val byMarket = HashMap<String, LadderLine>()
    private val pending = ArrayList<Pending>()
    private val fills = ArrayDeque<Fill>()
    private val matchedTrades = HashSet<String>()
    private var windowsSeen = 0
    private var updates = 0L
    private var lastWindowMs: Long? = null

    private fun reset() {
        games.clear(); replay.clear(); byMarket.clear(); pending.clear(); fills.clear(); matchedTrades.clear()
        windowsSeen = 0; updates = 0L; lastWindowMs = null; probeTargets = emptyList()
    }

    private suspend fun consume(queue: Channel<Msg>) {
        for (msg in queue) {
            when (msg) {
                is Msg.Book -> onBook(msg)
                is Msg.Tick -> onTick(msg.atMs)
                is Msg.Catalog -> onCatalog(msg)
                is Msg.Trades -> onTrades(msg)
            }
        }
    }

    private fun book(marketId: String, atMs: Long): NovigBook? = replay[marketId]?.book(atMs)

    private suspend fun onCatalog(m: Msg.Catalog) {
        val liveIds = m.events.mapTo(HashSet()) { it.eventId }
        for (e in m.events) {
            val lines = m.lines.filter { it.eventId == e.eventId }
            val g = games[e.eventId]
            if (g == null) games[e.eventId] = Game(e, m.atMs, lines) else g.lines = lines
        }
        // A game that left the live list is over (or off): its windows end now, its coverage is written.
        val gone = games.keys.filter { it !in liveIds }
        for (id in gone) finishGame(games.remove(id)!!, m.atMs)
        byMarket.clear()
        m.lines.forEach { byMarket[it.marketId] = it }
        probeTargets = games.values.sortedByDescending { it.updates }.mapNotNull { g -> g.lines.firstOrNull { it.kind == LadderKind.MARGIN && it.threshold == 0.0 }?.marketId }.take(PROBE_GAMES)
        publish(m.atMs)
    }

    private suspend fun onBook(m: Msg.Book) {
        // Orders that would have reached Novig before this push are judged on the books as they were before it.
        drain(m.atMs)
        val line = byMarket[m.marketId]
        replay.getOrPut(m.marketId) { ReplayBook(m.marketId) }.apply(m.changes)
        updates++
        if (line == null) return
        val game = games[line.eventId] ?: return
        game.updates++
        for (c in m.changes) if (c.kind == BookChange.Kind.REMOVE && c.reason == "fill") {
            fills.addLast(Fill(m.marketId, c.outcome, c.priceMilli, c.qty, m.atMs))
            while (fills.size > MAX_FILLS) fills.removeFirst()
        }
        val ladder = game.lines.filter { it.ladderKey == line.ladderKey }
        val opened = game.windows.onBook(ladder, { id -> book(id, m.atMs) }, line, m.atMs)
        if (opened.isNotEmpty()) {
            val profiles = latency.profiles()
            for (w in opened) {
                windowsSeen++
                lastWindowMs = m.atMs
                pending += Pending(game, w, profiles.map { Eval(it, w.openedMs + it.totalMs) })
            }
            drain(m.atMs)
        }
        publish(m.atMs)
    }

    private suspend fun onTick(atMs: Long) {
        drain(atMs)
        for (g in games.values) for (c in g.windows.sweep(atMs)) closed(g, c)
        finalize(atMs)
        publish(atMs)
    }

    private fun closed(g: Game, c: ClosedWindow) {
        pending.firstOrNull { it.window === c.window && it.game === g }?.closed = c
    }

    /** Runs every paper trade that is due by [upTo] on the books as they are now. */
    private fun drain(upTo: Long) {
        for (p in pending) for (e in p.evals) {
            if (e.result != null || e.dueMs > upTo) continue
            val open = p.window.first
            val yesNow = CoverMath.leg(book(open.lo.marketId, upTo), open.lo, yes = true)
            val noNow = CoverMath.leg(book(open.hi.marketId, upTo), open.hi, yes = false)
            val free = PaperTrader.trade(open, yesNow, noNow, 0.0)
            val capped = PaperTrader.trade(open, yesNow, noNow, capDollars)
            e.result = ProfileResult(e.latency.name, e.latency.totalMs, free.outcome.name, free.contracts, free.pnl, capped.contracts, capped.pnl)
        }
    }

    /** Writes the windows that have closed and have all their paper trades. */
    private suspend fun finalize(atMs: Long) {
        val done = pending.filter { it.closed != null && it.evals.all { e -> e.result != null } }
        if (done.isEmpty()) return
        pending.removeAll(done.toSet())
        val records = done.map { p -> record(p) }
        write(atMs, records)
    }

    private fun record(p: Pending): WindowRecord {
        val first = p.window.first
        return WindowRecord(
            p.game.event.league, p.game.event.eventId, p.game.event.description, "${first.lo.label} YES / ${first.hi.label} NOT",
            p.window.openedMs, p.closed!!.durationMs, first.yes.price, first.no.price, first.contracts, first.net, p.window.peakNet, p.window.peakContracts, p.window.updates,
            p.evals.mapNotNull { it.result },
        )
    }

    private suspend fun finishGame(g: Game, atMs: Long) {
        for (c in g.windows.closeAll(atMs)) closed(g, c)
        drain(Long.MAX_VALUE)
        finalize(atMs)
        write(atMs, listOf(GameRecord(g.event.league, g.event.eventId, g.event.description, g.fromMs, atMs, g.lines.size, g.updates)))
    }

    private suspend fun write(atMs: Long, lines: List<BurstLine>) {
        try {
            withContext(ioContext) { journal.append(atMs, lines) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = _status.value.copy(problem = "The burst journal could not be written: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun shutdown(queue: Channel<Msg>) {
        val now = clock()
        queue.close()
        for (g in games.values.toList()) finishGame(g, now)
        games.clear()
        _status.value = _status.value.copy(running = false, games = 0, lines = 0, open = 0)
    }

    private fun onTrades(m: Msg.Trades) {
        for (t in m.trades) {
            val id = "${m.marketId}|${t.outcomeId}|${t.price}|${t.contracts}|${t.atMs}"
            if (!matchedTrades.add(id)) continue
            val milli = Math.round(t.price * 1000).toInt()
            // The fill the books showed for this trade: the same market, side and price, the nearest in time (the same size first).
            val fill = fills.filter { !it.matched && it.marketId == m.marketId && it.outcome == t.outcomeId && it.priceMilli == milli && it.atMs >= t.atMs - 2_000L && it.atMs <= t.atMs + 10_000L }
                .minByOrNull { (if (it.qty == t.contracts) 0L else 100_000L) + Math.abs(it.atMs - t.atMs) } ?: continue
            fill.matched = true
            latency.addPushDelay(fill.atMs - t.atMs)
        }
        if (matchedTrades.size > MAX_FILLS * 4) matchedTrades.clear()
    }

    private fun publish(atMs: Long) {
        _status.value = _status.value.copy(
            games = games.size, lines = games.values.sumOf { it.lines.size }, updates = updates, open = games.values.sumOf { it.windows.openCount }, windows = windowsSeen, lastWindowMs = lastWindowMs,
        )
    }

    companion object {
        /** How often the live games and their lines are looked up again: a game starts or ends now and then, so a minute is soon enough. */
        const val DISCOVER_MS = 60_000L
        const val ECHO_MS = 20_000L
        const val PROBE_MS = 6_000L

        /** A tick every 50 ms closes windows and runs the paper trades that are due: the grace is 150 ms. */
        const val TICK_MS = 50L
        const val PROBE_GAMES = 2
        const val MAX_FILLS = 400
    }
}
