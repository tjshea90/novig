package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs

/** What the runner is told each time it looks: Tj's rules and the leagues he wants (Novig's names). */
data class LiveConfig(val rules: LiveRules, val method: DevigMethod, val leagues: Set<String>)

/** The Pinnodds source of frames: the socket in the app, a fake in tests. */
interface PinnFeedSource {
    fun start()
    fun stop()
    val state: StateFlow<PinnSocketState>
    val lastFrameAtMs: Long
}

data class LiveRunnerStatus(
    val running: Boolean = false,
    val sinceMs: Long? = null,
    val socket: String = "off",
    val pinnEvents: Int = 0,
    val pinnLive: Int = 0,
    val novigGames: Int = 0,
    val matched: Int = 0,
    val targets: Int = 0,
    val watched: Int = 0,
    val frames: Long = 0,
    val frameAgeMs: Long? = null,
    val evaluations: Long = 0,
    val candidates: Long = 0,
    val skips: Map<String, Int> = emptyMap(),
    val problem: String? = null,
    val lastCandidate: String? = null,
)

/**
 * Pinnacle live against Novig live (Tj, 2026-10-08: "compare the pinnacle web socket to the novig web socket ... auto bet all odds on novig that lag fair devigged live odds from pinnodds").
 *
 * One consumer coroutine owns all the state and handles messages in the order they were queued (Pinnodds frames, Novig book pushes with their arrival time, the catalog, the clock's ticks),
 * so replaying the same messages gives the same result; the socket threads only hand work on. A Pinnacle price change **arms** its game for [LiveRules.moveWindowMs]; an armed game's matched Novig
 * markets are judged ([LiveEdge.judge]) every [TICK_MS] and at once on each Novig book change. A [LiveVerdict.Bet] goes to the [trader] (which sends nothing in paper mode).
 * Each decision is followed up at 30 and 120 s: what Pinnacle's fair and Novig's ask were then, for the verdict on whether the edge was real.
 */
class PinnLiveRunner(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    /** Opens the Novig book feed (a key's websocket), told of every change; null when no key is connected. */
    private val newFeed: (BookListener) -> PushedBooks?,
    /** Opens the Pinnodds feed, handing each text frame on; the runner starts and stops it. */
    private val openFeed: ((String, Long) -> Unit) -> PinnFeedSource,
    private val trader: PinnLiveTrader,
    private val config: () -> LiveConfig,
    private val followJournal: DayJournal<LiveFollow>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val discoverEveryMs: Long = DISCOVER_MS,
    private val tickMs: Long = TICK_MS,
) {
    private val _status = MutableStateFlow(LiveRunnerStatus())
    val status: StateFlow<LiveRunnerStatus> = _status.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var previous: Job? = null
    val running: Boolean get() = job?.isActive == true

    @Synchronized
    fun start() {
        if (running) return
        _status.value = LiveRunnerStatus(running = true, sinceMs = clock(), socket = "connecting")
        val prev = previous
        job = scope.launch {
            prev?.join()
            runLoop()
        }
    }

    @Synchronized
    fun stop(why: String? = null) {
        val j = job ?: return
        job = null
        previous = j
        j.cancel()
        _status.value = _status.value.copy(running = false, socket = "off", problem = why ?: _status.value.problem)
    }

    private sealed interface Msg {
        class Frame(val text: String, val atMs: Long) : Msg
        class Book(val marketId: String, val atMs: Long) : Msg
        class Catalog(val events: List<NovigEvent>, val markets: List<NovigMarket>, val atMs: Long) : Msg
        class Recorded(val record: LiveRecord, val candidate: LiveCandidate) : Msg
        class Tick(val atMs: Long) : Msg
    }

    private class Follow(val dueMs: Long, val record: LiveRecord, val candidate: LiveCandidate, val offsetSec: Int)

    // ---- consumer state (touched only by the consumer) ----------------------------------------------------------------------------
    private var book = PinnBook()
    private var catalogEvents: List<NovigEvent> = emptyList()
    private var catalogMarkets: List<NovigMarket> = emptyList()
    private val targetsByEvent = HashMap<Long, List<LiveTarget>>()
    private val targetsByMarket = HashMap<String, LiveTarget>()
    private val armedUntil = HashMap<Long, Long>()
    private val follows = PriorityQueue<Follow>(compareBy { it.dueMs })
    private var evaluations = 0L
    private var candidates = 0L
    private val skips = HashMap<String, Int>()
    private var lastCandidate: String? = null
    private var watched = 0
    private var matchedGames = 0
    private var lastRematchMs = 0L
    private var lastEventCount = -1
    private var catalogDirty = false

    private fun reset() {
        book = PinnBook(config().method)
        catalogEvents = emptyList(); catalogMarkets = emptyList(); targetsByEvent.clear(); targetsByMarket.clear(); armedUntil.clear(); follows.clear()
        evaluations = 0; candidates = 0; skips.clear(); lastOffer.clear(); lastCandidate = null; watched = 0; matchedGames = 0; lastRematchMs = 0; lastEventCount = -1; catalogDirty = false
    }

    private suspend fun runLoop() {
        reset()
        val queue = Channel<Msg>(Channel.UNLIMITED)
        val novig = newFeed { marketId, atMs, _ -> queue.trySend(Msg.Book(marketId, atMs)) }
        if (novig == null) {
            _status.value = _status.value.copy(running = false, socket = "off", problem = "No Novig key is connected: the live feed needs a key's live books (Settings › Betting & Novig account).")
            return
        }
        val pinn = openFeed { text, at -> queue.trySend(Msg.Frame(text, at)) }
        feedSource = pinn
        pending = queue
        val helpers = ArrayList<Job>()
        try {
            pinn.start()
            helpers += scope.launch { discover(queue) }
            helpers += scope.launch { while (isActive) { delay(tickMs); queue.trySend(Msg.Tick(clock())) } }
            consume(queue, novig, pinn)
        } finally {
            helpers.forEach { it.cancel() }
            feedSource = null
            pending = null
            runCatching { pinn.stop() }
            runCatching { novig.close() }
        }
    }

    private suspend fun discover(queue: Channel<Msg>) {
        while (isActive) {
            try {
                val cfg = config()
                val statuses = buildList {
                    add(NovigEvent.STATUS_LIVE); add(NovigEvent.STATUS_DELAYED)
                    if (cfg.rules.pregame) add(NovigEvent.STATUS_PREGAME)
                }
                val before = if (cfg.rules.pregame) clock() + PREGAME_HORIZON_MS else null
                val events = source.events(cfg.leagues, statuses, before).filter { it.status in statuses }
                val ids = events.mapTo(HashSet()) { it.eventId }
                val markets = if (events.isEmpty()) emptyList() else source.markets(cfg.leagues, MARKET_TYPES, statuses, before).filter { it.eventId in ids && it.isOpen }
                queue.trySend(Msg.Catalog(events, markets, clock()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = _status.value.copy(problem = "Novig's catalog: ${e.message ?: e.javaClass.simpleName}")
            }
            delay(discoverEveryMs)
        }
    }

    private suspend fun consume(queue: Channel<Msg>, novig: PushedBooks, pinn: PinnFeedSource) {
        var lastStatusMs = 0L
        for (msg in queue) {
            val now = clock()
            when (msg) {
                is Msg.Frame -> onFrame(msg, novig)
                is Msg.Book -> onNovigBook(msg.marketId, now, novig)
                is Msg.Catalog -> { catalogEvents = msg.events; catalogMarkets = msg.markets; catalogDirty = true; rematch(novig, now) }
                is Msg.Recorded -> scheduleFollows(msg)
                is Msg.Tick -> {
                    onTick(now, novig, pinn)
                    if (now - lastStatusMs >= STATUS_EVERY_MS) { lastStatusMs = now; publish(pinn, novig, now) }
                }
            }
        }
    }

    private fun onFrame(msg: Msg.Frame, novig: PushedBooks) {
        val obj = parsePinnFrame(msg.text) ?: return
        val cfg = config()
        if (book.method != cfg.method) book.method = cfg.method
        for (c in book.apply(obj, msg.atMs)) {
            when (c.kind) {
                PinnChange.Kind.PRICE -> armedUntil[c.eventId] = msg.atMs + cfg.rules.moveWindowMs + EXTRA_ARM_MS
                PinnChange.Kind.GONE -> { armedUntil.remove(c.eventId); targetsByEvent.remove(c.eventId)?.forEach { targetsByMarket.remove(it.market.marketId) } }
                else -> {}
            }
        }
    }

    private fun onNovigBook(marketId: String, now: Long, novig: PushedBooks) {
        val t = targetsByMarket[marketId] ?: return
        if ((armedUntil[t.pinnEventId] ?: 0L) < now) return
        evaluate(t, now, novig)
    }

    private fun onTick(now: Long, novig: PushedBooks, pinn: PinnFeedSource) {
        // Armed games: judged on every tick (the settle time passes between a Pinnacle change and its first judgment).
        val armed = armedUntil.entries.iterator()
        while (armed.hasNext()) {
            val (eventId, until) = armed.next()
            if (until < now) { armed.remove(); continue }
            targetsByEvent[eventId]?.forEach { t -> evaluate(t, now, novig) }
        }
        while (follows.isNotEmpty() && follows.peek().dueMs <= now) capture(follows.poll(), now, novig)
        val events = book.events.size
        if (catalogDirty || events != lastEventCount && now - lastRematchMs > REMATCH_MS) rematch(novig, now)
        if (now % 30_000L < tickMs) book.prune(now)
    }

    private fun feedProblem(now: Long, novig: PushedBooks, pinn: PinnFeedSource): String? {
        if (pinn.state.value !is PinnSocketState.Live) return "Pinnodds feed not connected"
        if (now - pinn.lastFrameAtMs > FEED_STALE_MS) return "Pinnodds feed quiet"
        return novig.problemSince(_status.value.sinceMs ?: 0L)?.let { "Novig live feed problem" }
    }

    @Volatile private var feedSource: PinnFeedSource? = null

    /** When each Novig outcome was last handed to the trader: a bet that stays on offer is not handed over again every tick. */
    private val lastOffer = HashMap<String, Long>()

    /** Which Novig games are which Pinnacle matchups, and which of their markets are worth holding open (the moneyline, and the spreads and totals near Pinnacle's own lines). */
    private fun rematch(novig: PushedBooks, now: Long) {
        catalogDirty = false
        lastRematchMs = now
        lastEventCount = book.events.size
        val cfg = config()
        val pairs = LiveMatcher.matchEvents(book.events.values.filter { eligibleNow(it, now, cfg.rules) }, catalogEvents, now)
            .filter { p -> book.events[p.pinnEventId]?.let { pe -> sameStage(pe, p.event) } == true }
        matchedGames = pairs.size
        val byEvent = HashMap<Long, List<LiveTarget>>()
        val ordered = ArrayList<Pair<Double, String>>()
        for (p in pairs) {
            val pe = book.events[p.pinnEventId] ?: continue
            val mains = pe.lines.values.filter { it.period == 0 && !it.alternate && it.open }.associateBy { it.type }
            val ts = LiveMatcher.targets(p, catalogMarkets).filter { t ->
                when (t.type) {
                    PinnLineType.MONEYLINE -> true
                    PinnLineType.SPREAD -> mains[PinnLineType.SPREAD]?.points?.let { abs((if (t.swapped) -it else it) - t.strike!!) <= WATCH_BAND } == true
                    PinnLineType.TOTAL -> mains[PinnLineType.TOTAL]?.points?.let { abs(it - t.strike!!) <= WATCH_BAND } == true
                }
            }
            byEvent[p.pinnEventId] = ts
            for (t in ts) ordered += (if (t.type == PinnLineType.MONEYLINE) 0.0 else 1.0 + abs(t.strike ?: 0.0)) to t.market.marketId
        }
        targetsByEvent.clear(); targetsByEvent.putAll(byEvent)
        targetsByMarket.clear()
        byEvent.values.flatten().forEach { targetsByMarket[it.market.marketId] = it }
        val ids = ordered.sortedBy { it.first }.map { it.second }
        watched = ids.size
        if (ids.isNotEmpty()) novig.watch(ids)
    }

    /** A Pinnacle matchup worth matching now: heard from lately, one of the game's own books, and (live) live or (prematch) not yet started. */
    private fun eligibleNow(e: PinnEvent, now: Long, rules: LiveRules): Boolean =
        e.regular && now - e.lastFrameAtMs < MATCH_FRESH_MS && if (e.live) true else rules.pregame && e.startMs > now

    /** A live Pinnacle matchup goes with a live Novig game, a prematch one with a pregame Novig game: never a stale prematch matchup with a game already under way. */
    private fun sameStage(pe: PinnEvent, ne: NovigEvent): Boolean =
        if (pe.live) ne.status == NovigEvent.STATUS_LIVE || ne.status == NovigEvent.STATUS_DELAYED else ne.status == NovigEvent.STATUS_PREGAME

    private fun evaluate(t: LiveTarget, now: Long, novig: PushedBooks) {
        val pe = book.events[t.pinnEventId] ?: return
        val line = t.line(pe) ?: return
        val cfg = config()
        val problem = feedProblem(now, novig, feedSource ?: return)
        val mb = novig.live(listOf(t.market.marketId))[t.market.marketId]
        val fee = t.market.fee
        for ((side, outcomeId) in t.outcomeBySide) {
            evaluations++
            if (problem != null) { count(problem); continue }
            if (mb == null) { count(LiveSkip.STALE_BOOK); continue }
            if (fee == null) { count("market has no fee data"); continue }
            val live = t.event.status != NovigEvent.STATUS_PREGAME
            val ladder = mb.takeLadder(t.market, outcomeId)
            when (val v = LiveEdge.judge(pe, line, side, now, ladder, fee, live, cfg.rules)) {
                is LiveVerdict.Skip -> count(v.reason)
                is LiveVerdict.Bet -> {
                    if (now - (lastOffer[outcomeId] ?: 0L) < REOFFER_MS) continue
                    lastOffer[outcomeId] = now
                    candidates++
                    val cand = candidate(t, pe, line, side, outcomeId, v, fee, now, cfg)
                    lastCandidate = "${cand.betTarget.selection} @ ${"%.3f".format(Locale.US, v.ask)} · fair ${"%.3f".format(Locale.US, v.fair)} · ${"%.1f".format(Locale.US, v.ev * 100)}% EV"
                    trader.offer(cand) { rec -> pending?.trySend(Msg.Recorded(rec, cand)) }
                }
            }
        }
    }

    @Volatile private var pending: Channel<Msg>? = null

    private fun count(reason: String) {
        skips[reason] = (skips[reason] ?: 0) + 1
    }

    private fun candidate(t: LiveTarget, pe: PinnEvent, line: PinnLine, side: PinnSide, outcomeId: String, v: LiveVerdict.Bet, fee: com.tjshea.vigilant.engine.MarketFee, now: Long, cfg: LiveConfig): LiveCandidate {
        val outcomeName = t.market.outcomes.firstOrNull { it.outcomeId == outcomeId }?.name ?: ""
        val matchup = t.event.matchup
        val novigHome = (side == PinnSide.HOME) != t.swapped
        val team = if (novigHome) matchup?.home else matchup?.away
        val (marketLabel, selection) = when (t.type) {
            PinnLineType.MONEYLINE -> "Moneyline" to (team ?: outcomeName)
            PinnLineType.SPREAD -> "Spread" to "${team ?: outcomeName.substringBeforeLast(' ')} ${signed(if (novigHome) t.strike!! else -t.strike!!)}"
            PinnLineType.TOTAL -> "Total" to outcomeName
        }
        val american = Odds.probabilityToAmerican((v.ask + v.fee).coerceIn(0.001, 0.999))
        val score = pe.score?.let { "${it.first}-${it.second}" }
        val target = BetTarget(
            market = t.market, outcomeId = outcomeId, league = t.event.league, eventName = t.event.description, startsTs = t.event.startsTs,
            marketLabel = marketLabel, selection = selection, fair = v.fair, fairAsOfMs = line.changedAtMs, source = BetTracker.SOURCE_PINNODDS,
            basis = FairBasis(FairBasis.SOURCE_PINNODDS, listOf("Pinnacle"), 1), auto = true,
            atBet = AtBet(
                atMs = now, how = AtBet.HOW_AUTO, scanner = "Pinnodds live", league = t.event.league, live = t.event.status != NovigEvent.STATUS_PREGAME, american = american,
                ev = v.ev, fair = v.fair, fairAmerican = Odds.probabilityToAmerican(v.fair.coerceIn(0.001, 0.999)), fairMethod = "Pinnacle live, ${cfg.method.displayName} devig",
                fairBooks = listOf("Pinnacle"), fairSharp = listOf("Pinnacle"),
            ),
        )
        return LiveCandidate(t, side, outcomeId, v, line.key, line.points, target, fee, score, pe.clock, now, line.changedAtMs)
    }

    private fun signed(x: Double): String = (if (x > 0) "+" else "") + (if (x == Math.floor(x)) x.toInt().toString() else x.toString())

    private fun scheduleFollows(m: Msg.Recorded) {
        val base = m.record.atMs
        for (s in LiveTradeLimits.FOLLOW_UP_SECONDS) follows += Follow(base + s * 1000L, m.record, m.candidate, s)
    }

    /** What the same line says now, for the bet's verdict against the later price. */
    private fun capture(f: Follow, now: Long, novig: PushedBooks) {
        val t = f.candidate.target
        val pe = book.events[t.pinnEventId]
        val line = pe?.let { t.line(it) }
        val fair = line?.takeIf { it.open }?.fair?.get(f.candidate.side)
        val ask = novig.live(listOf(t.market.marketId))[t.market.marketId]?.takeLadder(t.market, f.candidate.outcomeId)?.minOfOrNull { it.price }
        runCatching { followJournal.append(LiveFollow(f.record.id, now, f.offsetSec, fair, ask, closed = line == null || !line.open)) }
    }

    private fun publish(pinn: PinnFeedSource, novig: PushedBooks, now: Long) {
        val socket = when (val s = pinn.state.value) {
            is PinnSocketState.Off -> "off"
            is PinnSocketState.Connecting -> "connecting"
            is PinnSocketState.Live -> "live"
            is PinnSocketState.Down -> s.message
        }
        _status.value = _status.value.copy(
            running = job?.isActive == true, socket = socket, pinnEvents = book.events.size, pinnLive = book.events.values.count { it.live }, novigGames = catalogEvents.size, matched = matchedGames,
            targets = targetsByMarket.size, watched = watched, frames = book.frames, frameAgeMs = pinn.lastFrameAtMs.takeIf { it > 0 }?.let { now - it }, evaluations = evaluations,
            candidates = candidates, skips = skips.toMap(), lastCandidate = lastCandidate,
            problem = (pinn.state.value as? PinnSocketState.Down)?.message ?: novig.problemSince(_status.value.sinceMs ?: 0L),
        )
    }

    companion object {
        const val DISCOVER_MS = 30_000L
        const val TICK_MS = 100L
        const val STATUS_EVERY_MS = 1_000L

        /** A bet still on offer is handed to the trader again no sooner than this (it is busy or on cooldown for longer anyway). */
        const val REOFFER_MS = 1_000L
        const val REMATCH_MS = 5_000L

        /** A game stays armed this long past the move window: the settle time and a Novig book change can land just after it. */
        const val EXTRA_ARM_MS = 2_000L

        /** A Pinnacle matchup silent this long is not matched (a kicked-off or pulled one stops being sent). */
        const val MATCH_FRESH_MS = 10 * 60_000L

        /** The feed is "quiet" when nothing, not even a heartbeat, came for this long. */
        const val FEED_STALE_MS = 20_000L

        /** Spreads and totals this close to Pinnacle's own line are held open on the Novig feed; farther ones can't be priced by Pinnacle's main line. */
        const val WATCH_BAND = 3.0
        const val PREGAME_HORIZON_MS = 6 * 3_600_000L
        val MARKET_TYPES = listOf("MONEY", "SPREAD", "TOTAL")
    }
}
