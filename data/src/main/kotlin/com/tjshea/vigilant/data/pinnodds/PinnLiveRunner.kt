package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.livebid.LiveBidBookView
import com.tjshea.vigilant.data.livebid.LiveBidConfig
import com.tjshea.vigilant.data.livebid.LiveBidDesk
import com.tjshea.vigilant.data.livebid.LiveBidFair
import com.tjshea.vigilant.data.livebid.LiveBidJudge
import com.tjshea.vigilant.data.livebid.LiveBidKeep
import com.tjshea.vigilant.data.livebid.LiveBidSkip
import com.tjshea.vigilant.data.livebid.LiveBidVerdict
import com.tjshea.vigilant.data.livebid.LiveBidView
import com.tjshea.vigilant.data.livebid.LiveBidWant
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookChange
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
import kotlinx.coroutines.currentCoroutineContext
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

/** What the runner is told each time it looks: Tj's rules and the leagues he wants (Novig's names). [takerOn]: the lag taker judges and trades; off, the feed runs only for the live bids. */
data class LiveConfig(val rules: LiveRules, val method: DevigMethod, val leagues: Set<String>, val takerOn: Boolean = true)

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
    /** Live bids: Novig lines judged for a bid (both sides count), and why not (words, counts). */
    val bidTargets: Int = 0,
    /** Live bids: how many line judgments have been made, why the judge is not running (null = it is), and the last error it hit (it carries on after one). */
    val bidJudged: Long = 0,
    val bidGate: String? = null,
    val bidError: String? = null,
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
    /** Where the post-score probes of the moneyline go ([ReopenStudy]); null = none are taken. */
    private val reopenJournal: DayJournal<ReopenProbe>? = null,
    /** The live bid desk ([LiveBidDesk]): told which bids are justified, vouched for and pulled, from this runner's one Pinnodds feed and one Novig feed. Null = no live bids. */
    private val bids: LiveBidDesk? = null,
    private val bidConfig: () -> LiveBidConfig = { LiveBidConfig.OFF },
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
        class Book(val marketId: String, val atMs: Long, val changes: List<BookChange>) : Msg
        class Catalog(val events: List<NovigEvent>, val markets: List<NovigMarket>, val atMs: Long) : Msg
        class Recorded(val record: LiveRecord, val candidate: LiveCandidate) : Msg
        class Tick(val atMs: Long) : Msg
    }

    private class Follow(val dueMs: Long, val record: LiveRecord, val candidate: LiveCandidate, val offsetSec: Int)

    /** One reading of a moneyline [offsetSec] after a score at [scoreAtMs]. */
    private class Probe(val dueMs: Long, val target: LiveTarget, val scoreAtMs: Long, val offsetSec: Int)

    // ---- consumer state (touched only by the consumer) ----------------------------------------------------------------------------
    private var book = PinnBook()
    private var matchedPairs: List<LiveMatcher.Pair> = emptyList()
    private var lastAltMs = 0L
    @Volatile private var altMap: Map<String, List<com.tjshea.vigilant.data.novig.lab.AltQuote>> = emptyMap()
    private var catalogEvents: List<NovigEvent> = emptyList()
    private var catalogMarkets: List<NovigMarket> = emptyList()
    private val targetsByEvent = HashMap<Long, List<LiveTarget>>()
    private val targetsByMarket = HashMap<String, LiveTarget>()
    private val armedUntil = HashMap<Long, Long>()
    private val follows = PriorityQueue<Follow>(compareBy { it.dueMs })
    private val probes = PriorityQueue<Probe>(compareBy { it.dueMs })
    private var evaluations = 0L
    private var candidates = 0L
    private val skips = HashMap<String, Int>()
    private var lastCandidate: String? = null
    private var watched = 0
    private var matchedGames = 0
    private var lastRematchMs = 0L
    private var lastEventCount = -1
    private var catalogDirty = false
    private var lastStandingMs = 0L

    private fun reset() {
        book = PinnBook(config().method)
        catalogEvents = emptyList(); catalogMarkets = emptyList(); targetsByEvent.clear(); targetsByMarket.clear(); armedUntil.clear(); follows.clear(); probes.clear()
        evaluations = 0; candidates = 0; skips.clear(); lastOffer.clear(); lastBidBookMs.clear(); lastBidJudgeMs = 0L; bidTargetsNow = 0; bidJudged = 0L; bidGate = null; bidError = null; lastCandidate = null; watched = 0; matchedGames = 0; lastRematchMs = 0; lastEventCount = -1; catalogDirty = false
    }

    private suspend fun runLoop() {
        reset()
        val queue = Channel<Msg>(Channel.UNLIMITED)
        val novig = newFeed { marketId, atMs, changes -> queue.trySend(Msg.Book(marketId, atMs, changes)) }
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
            // The feed that justified the live bids is gone: they come down (Novig removes any that this misses at their ttl).
            withContext(NonCancellable) { runCatching { bids?.stopAll("the live feed stopped") } }
            feedSource = null
            pending = null
            runCatching { pinn.stop() }
            runCatching { novig.close() }
        }
    }

    private suspend fun discover(queue: Channel<Msg>) {
        while (currentCoroutineContext().isActive) {
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
                is Msg.Book -> onNovigBook(msg.marketId, now, novig, msg.changes)
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
        val cfg = config()
        // 65% of the frames on a busy night are prematch price updates (topic ending /pre): with pregame off they are not even parsed (battery).
        if (!cfg.rules.pregame && isPrematchFrame(msg.text)) return
        val obj = parsePinnFrame(msg.text) ?: return
        if (book.method != cfg.method) book.method = cfg.method
        val rejudge = HashSet<Long>()
        for (c in book.apply(obj, msg.atMs)) {
            // The live bids are re-judged at once on anything that can change what a bid is worth: a price, a score, a danger frame, a closed line (a pull must not wait for the next tick).
            if (c.kind != PinnChange.Kind.GONE) rejudge += c.eventId
            when (c.kind) {
                // Only a MAIN line's change arms a game: an alternate line moves constantly and is not what the targets are priced from.
                PinnChange.Kind.PRICE -> if (book.events[c.eventId]?.lines?.get(c.key)?.alternate != true) armedUntil[c.eventId] = msg.atMs + (if (book.events[c.eventId]?.live == false) cfg.rules.preMoveWindowMs else cfg.rules.moveWindowMs) + EXTRA_ARM_MS
                PinnChange.Kind.SCORE -> scheduleProbes(c.eventId, msg.atMs)
                PinnChange.Kind.GONE -> {
                    armedUntil.remove(c.eventId)
                    targetsByEvent.remove(c.eventId)?.forEach { t ->
                        targetsByMarket.remove(t.market.marketId)
                        t.outcomeBySide.values.forEach { o -> bids?.pull(o, LiveBidSkip.NOT_LIVE) }
                    }
                }
                else -> {}
            }
        }
        if (rejudge.isNotEmpty() && bids != null) judgeBids(msg.atMs, novig, only = rejudge)
    }

    private fun onNovigBook(marketId: String, now: Long, novig: PushedBooks, changes: List<BookChange>) {
        val t = targetsByMarket[marketId] ?: return
        if (bids != null) {
            // Trades on this market are what a paper bid is filled by; a real bid's own fill is looked for at once.
            bids.onBook(marketId, now, changes)
            if (now - (lastBidBookMs[marketId] ?: 0L) >= BID_BOOK_GAP_MS) {
                lastBidBookMs[marketId] = now
                judgeBids(now, novig, only = setOf(t.pinnEventId), market = marketId)
            }
        }
        if (!config().takerOn) return
        // A Novig change is judged at once when Pinnacle moved lately (the lag tests), or in "any edge" mode, which has no move to wait for.
        if ((armedUntil[t.pinnEventId] ?: 0L) < now && !config().rules.trigger.sweeps) return
        evaluate(t, now, novig)
    }

    private fun onTick(now: Long, novig: PushedBooks, pinn: PinnFeedSource) {
        // Live bids: every matched line is judged twice a second, so a bid is vouched for (or pulled) at least that often.
        if (bids != null && now - lastBidJudgeMs >= BID_EVERY_MS) { lastBidJudgeMs = now; judgeBids(now, novig) }
        // Armed games: judged on every tick (the settle time passes between a Pinnacle change and its first judgment).
        val armed = armedUntil.entries.iterator()
        while (armed.hasNext()) {
            val (eventId, until) = armed.next()
            if (until < now) { armed.remove(); continue }
            targetsByEvent[eventId]?.forEach { t -> evaluate(t, now, novig) }
        }
        // "Any edge" has no Pinnacle move to arm on: every matched game is looked at once a second.
        if (config().rules.trigger.sweeps && now - lastStandingMs >= STANDING_EVERY_MS) {
            lastStandingMs = now
            targetsByEvent.values.forEach { ts -> ts.forEach { t -> evaluate(t, now, novig) } }
        }
        while (follows.isNotEmpty() && follows.peek().dueMs <= now) capture(follows.poll(), now, novig)
        while (probes.isNotEmpty() && probes.peek().dueMs <= now) probe(probes.poll(), now, novig)
        val events = book.events.size
        if (catalogDirty || events != lastEventCount && now - lastRematchMs > REMATCH_MS) rematch(novig, now)
        if (now % 30_000L < tickMs) book.prune(now)
        if (now - lastAltMs >= ALT_EVERY_MS) { lastAltMs = now; altMap = matchedPairs.mapNotNull { p -> (book.events[p.linesEventId ?: p.pinnEventId])?.let { p.event.eventId to com.tjshea.vigilant.data.novig.lab.PinnAltQuotes.quotes(it, p.swapped) } }.toMap() }
    }

    /** Pinnacle's live main and alternate spreads and totals for the Novig game [novigEventId] as the lab recorder reads them (a snapshot taken every few seconds on the consumer; empty when the game is not matched). */
    fun altQuotes(novigEventId: String): List<com.tjshea.vigilant.data.novig.lab.AltQuote> = altMap[novigEventId].orEmpty()

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
        val pairs = LiveMatcher.matchEvents(book.events.values.filter { eligibleNow(it, now, cfg.rules) }, catalogEvents, now) { pe, ne -> sameStage(pe, ne) }
        matchedGames = pairs.size
        matchedPairs = pairs
        val byEvent = HashMap<Long, List<LiveTarget>>()
        val ordered = ArrayList<Pair<Double, String>>()
        for (p in pairs) {
            if (book.events[p.pinnEventId] == null) continue
            // How far a spread's or total's line is from Pinnacle's own main line (null: Pinnacle has no such line, so nothing prices it). A tennis target reads its own child's lines.
            fun distance(t: LiveTarget): Double? {
                val mains = book.events[t.pinnEventId]?.lines?.values?.filter { it.period == 0 && !it.alternate && it.open }?.associateBy { it.type } ?: return null
                return when (t.type) {
                    PinnLineType.MONEYLINE -> if (mains[PinnLineType.MONEYLINE] != null) 0.0 else null
                    PinnLineType.SPREAD -> mains[PinnLineType.SPREAD]?.points?.let { abs((if (t.swapped) -it else it) - t.strike!!) }
                    PinnLineType.TOTAL -> mains[PinnLineType.TOTAL]?.points?.let { abs(it - t.strike!!) }
                }
            }
            val ts = LiveMatcher.targets(p, catalogMarkets).filter { t -> distance(t)?.let { it <= WATCH_BAND } == true }
            for (t in ts) {
                byEvent[t.pinnEventId] = (byEvent[t.pinnEventId] ?: emptyList()) + t
                ordered += (if (t.type == PinnLineType.MONEYLINE) 0.0 else 1.0 + distance(t)!!) to t.market.marketId
            }
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
        if (!config().takerOn) return
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

    // ---- live bids (Tj, 2026-10-10; RESEARCH.md §123-§124) ----------------------------------------------------------------------------------------------------------------------

    private val lastBidBookMs = HashMap<String, Long>()
    private var lastBidJudgeMs = 0L
    private var bidTargetsNow = 0
    @Volatile private var bidGate: String? = null
    @Volatile private var bidError: String? = null
    private var bidJudged = 0L

    /**
     * Judges the matched lines for live bids and tells the desk what is justified: for each side of each matched line, [LiveBidJudge.keep] vouches for (or pulls) a bid already up and
     * [LiveBidJudge.want] offers a new one. [only] limits it to those Pinnacle events (a frame just changed them), [market] to one Novig market (its book just changed). The desk pulls a
     * bid nobody vouches for within a few seconds, so a judgment that never comes (a stalled consumer, a dropped feed) takes the bids down by itself.
     */
    private fun judgeBids(now: Long, novig: PushedBooks, only: Set<Long>? = null, market: String? = null) {
        val desk = bids ?: return
        val bc = bidConfig()
        if (!bc.on) { bidGate = "the live bid switch is off, or STOP ALL or the pause is on"; return }
        val pinn = feedSource ?: run { bidGate = "the Pinnodds feed is not open"; return }
        bidGate = null
        val problem = feedProblem(now, novig, pinn)
        var n = 0
        // One bad line must never stop the engine (the lag taker shares this loop): the error is kept and shown, and the next line is judged.
        for ((eventId, ts) in targetsByEvent) {
            if (only != null && eventId !in only) continue
            for (t in ts) {
                if (market != null && t.market.marketId != market) continue
                n++
                try {
                    judgeBidTarget(desk, t, now, novig, bc, problem)
                    bidJudged++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    bidError = "${t.market.description}: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
                }
            }
        }
        if (only == null) bidTargetsNow = n
    }

    private fun judgeBidTarget(desk: LiveBidDesk, t: LiveTarget, now: Long, novig: PushedBooks, bc: LiveBidConfig, problem: String?) {
        val q = bc.quality
        val outcomes = t.outcomeBySide
        fun off(reason: String) {
            for (o in outcomes.values) { desk.pull(o, reason); desk.noWant(o, reason) }
        }
        val pe = book.events[t.pinnEventId] ?: return off(LiveBidSkip.NOT_LIVE)
        val kindOff = when (t.type) {
            PinnLineType.MONEYLINE -> !q.moneyline
            PinnLineType.SPREAD -> !q.spread
            PinnLineType.TOTAL -> !q.total
        }
        if (kindOff) return off(LiveBidSkip.KIND_OFF)
        if (pe.sportId == PinnBook.TENNIS_SPORT_ID && !q.tennis) return off(LiveBidSkip.TENNIS_OFF)
        if (q.onlyLeagues.isNotEmpty() && t.event.league !in q.onlyLeagues) return off(LiveBidSkip.LEAGUE_OFF)
        val line = t.line(pe) ?: return off(LiveBidSkip.NO_LINE)
        val mb = novig.live(listOf(t.market.marketId))[t.market.marketId] ?: return off(LiveBidSkip.NO_BOOK)
        val own = desk.ownLevels()
        val scoreAge = if (pe.scoreAtMs > 0L) now - pe.scoreAtMs else null
        val dangerAge = if (pe.volatileUntilMs > 0L) now - (pe.volatileUntilMs - PinnBook.VOLATILE_MS) else null
        for ((side, outcomeId) in outcomes) {
            val f = LiveBidFair.of(line, side, q.devig)
            if (f != null) desk.noteFair(outcomeId, f.fair, now)
            val b = LiveBidBookView.of(mb, t.market, outcomeId, own)
            val view = LiveBidView(
                nowMs = now, problem = problem, pinnLive = pe.live, novigLive = t.event.status == NovigEvent.STATUS_LIVE, lineOpen = line.open, fair = f?.fair,
                overround = f?.overround ?: line.overround, limit = line.maxRisk, quietMs = now - pe.lastFrameAtMs, sinceChangeMs = now - line.changedAtMs, scoreAgeMs = scoreAge,
                dangerAgeMs = dangerAge, bestBid = b.bestBid, offer = b.offer, fee = t.market.fee,
            )
            val held = desk.held(outcomeId)
            if (held != null) {
                when (val k = LiveBidJudge.keep(view, q, held)) {
                    LiveBidKeep.Keep -> desk.keepAlive(outcomeId, now)
                    is LiveBidKeep.Pull -> desk.pull(outcomeId, k.reason)
                }
            }
            when (val v = LiveBidJudge.want(view, q)) {
                is LiveBidVerdict.Skip -> desk.noWant(outcomeId, v.reason)
                is LiveBidVerdict.Post -> {
                    val (label, selection) = describe(t, side, outcomeId)
                    desk.want(
                        LiveBidWant(
                            atMs = now, outcomeId = outcomeId, marketId = t.market.marketId, eventId = t.event.eventId, pinnEventId = t.pinnEventId, league = t.event.league,
                            eventName = t.event.description, startsTs = t.event.startsTs, marketLabel = label, selection = selection, verdict = v, fee = t.market.fee,
                            score = pe.score?.let { "${it.first}-${it.second}" }, clock = pe.clock,
                        ),
                    )
                }
            }
        }
    }

    /** The market's name and the selection as the Tracker shows them ("Moneyline" / "Alabama", "Spread" / "Alabama -3.5", "Total" / "Over 45.5"). */
    private fun describe(t: LiveTarget, side: PinnSide, outcomeId: String): kotlin.Pair<String, String> {
        val outcomeName = t.market.outcomes.firstOrNull { it.outcomeId == outcomeId }?.name ?: ""
        val matchup = t.event.matchup
        val novigHome = (side == PinnSide.HOME) != t.swapped
        val team = if (novigHome) matchup?.home else matchup?.away
        return when (t.type) {
            PinnLineType.MONEYLINE -> "Moneyline" to (team ?: outcomeName)
            PinnLineType.SPREAD -> "Spread" to "${team ?: outcomeName.substringBeforeLast(' ')} ${signed(if (novigHome) t.strike!! else -t.strike!!)}"
            PinnLineType.TOTAL -> "Total" to outcomeName
        }
    }

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
        return LiveCandidate(t, side, outcomeId, v, line.key, line.points, target, fee, score, pe.clock, now, line.changedAtMs, if (pe.scoreAtMs > 0L) now - pe.scoreAtMs else -1L, t.event.status)
    }

    private fun signed(x: Double): String = (if (x > 0) "+" else "") + (if (x == Math.floor(x)) x.toInt().toString() else x.toString())

    private fun scheduleFollows(m: Msg.Recorded) {
        val base = m.record.atMs
        for (s in LiveTradeLimits.FOLLOW_UP_SECONDS) follows += Follow(base + s * 1000L, m.record, m.candidate, s)
    }

    /** A score changed in [eventId]'s game: its matched moneylines are read at once and at each of [ReopenStudy.OFFSETS] after (no order is sent). */
    private fun scheduleProbes(eventId: Long, atMs: Long) {
        if (reopenJournal == null || probes.size > MAX_PROBES) return
        val group = book.events[eventId]?.groupId ?: return
        for (ts in targetsByEvent.values) for (t in ts) {
            if (t.type != PinnLineType.MONEYLINE || book.events[t.pinnEventId]?.groupId != group) continue
            for (off in ReopenStudy.OFFSETS) probes += Probe(atMs + off * 1000L, t, atMs, off)
        }
    }

    private fun probe(p: Probe, now: Long, novig: PushedBooks) {
        val journal = reopenJournal ?: return
        val t = p.target
        val line = book.events[t.pinnEventId]?.let { t.line(it) }
        val mb = novig.live(listOf(t.market.marketId))[t.market.marketId]
        fun ask(side: PinnSide): Double? = t.outcomeBySide[side]?.let { oid -> mb?.takeLadder(t.market, oid)?.minOfOrNull { it.price } }
        runCatching {
            journal.append(
                ReopenProbe(
                    atMs = now, scoreAtMs = p.scoreAtMs, offsetSec = p.offsetSec, eventId = t.event.eventId, league = t.event.league, marketId = t.market.marketId,
                    fairHome = line?.fair?.get(PinnSide.HOME), fairAway = line?.fair?.get(PinnSide.AWAY), askHome = ask(PinnSide.HOME), askAway = ask(PinnSide.AWAY), lineOpen = line?.open == true,
                ),
            )
        }
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
            candidates = candidates, skips = skips.toMap(), lastCandidate = lastCandidate, bidTargets = bidTargetsNow, bidJudged = bidJudged, bidGate = bidGate, bidError = bidError,
            problem = (pinn.state.value as? PinnSocketState.Down)?.message ?: novig.problemSince(_status.value.sinceMs ?: 0L),
        )
    }

    companion object {
        /** How often the alternate-line snapshot for the lab is rebuilt. */
        const val ALT_EVERY_MS = 5_000L

        /** True for a `live` envelope whose topic ends in `/pre` (read from the first 160 characters: the envelope puts `topic` before the record). */
        internal fun isPrematchFrame(text: String): Boolean {
            val head = text.substring(0, minOf(160, text.length))
            return head.contains("\"topic\":\"") && head.contains("/pre\"")
        }

        /**
         * Novig leagues beyond Vigilant's own eleven that Pinnacle also prices live (Tj, 2026-10-08: "all sports may be read and auto bet"): soccer's moneyline is a three-way market that is not
         * matched yet, but its spreads and totals are; the rest have a plain two-way moneyline. The study recorder used the same names against Novig's catalog without a refusal.
         */
        val EXTRA_LEAGUES = setOf("NCAAWB", "CFL", "MLS", "EPL", "Bundesliga", "Serie A", "La Liga", "Ligue 1", "Champions League", "Europa League", "KBO", "NPB")

        /** Live bids are judged at least this often, and on a Novig book change no more than every [BID_BOOK_GAP_MS] per market. */
        const val BID_EVERY_MS = 500L
        const val BID_BOOK_GAP_MS = 200L

        const val DISCOVER_MS = 30_000L
        const val TICK_MS = 100L
        const val STATUS_EVERY_MS = 1_000L
        const val STANDING_EVERY_MS = 1_000L

        /** A bet still on offer is handed to the trader again no sooner than this (it is busy or on cooldown for longer anyway). */
        const val REOFFER_MS = 1_000L
        const val REMATCH_MS = 1_000L

        /** A game stays armed this long past the move window: the settle time and a Novig book change can land just after it. */
        const val EXTRA_ARM_MS = 2_000L

        /** The most post-score readings waiting at once (a busy night of scores cannot grow the queue without end). */
        const val MAX_PROBES = 4_000

        /** A Pinnacle matchup silent this long is not matched (a kicked-off or pulled one stops being sent). */
        const val MATCH_FRESH_MS = 10 * 60_000L

        /** The feed is "quiet" when nothing, not even a heartbeat, came for this long (the server pings every 30 s, so a healthy feed is never quiet longer than that). */
        const val FEED_STALE_MS = 45_000L

        /** Spreads and totals this close to Pinnacle's own line are held open on the Novig feed; farther ones can't be priced by Pinnacle's main line. */
        const val WATCH_BAND = 3.0
        const val PREGAME_HORIZON_MS = 6 * 3_600_000L
        val MARKET_TYPES = listOf("MONEY", "SPREAD", "TOTAL")
    }
}
