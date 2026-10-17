package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.burst.LadderLine
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** What Live light has done, for the settings page and Diagnostics. */
data class LiveLightStatus(
    val running: Boolean = false,
    val games: Int = 0,
    val markets: Int = 0,
    val withScore: Int = 0,
    /** Pushes from Novig's live feed that reached a watched market, and scans they (and score changes) caused. */
    val pushes: Long = 0,
    val scans: Long = 0,
    val offers: Long = 0,
    /** Bids the model asked the desk for, and bids it took down again. */
    val bidWants: Long = 0,
    val bidPulls: Long = 0,
    val lastPushMs: Long = 0,
    val lastScanMs: Long = 0,
    /** How long the latest scan took from the push to the offer (ms), and the slowest of the last ones. */
    val lastScanTookMs: Long = 0,
    val skipped: Map<String, Int> = emptyMap(),
    val last: String? = null,
    val problem: String? = null,
    /** One line per watched game (its name, the score and time left, the closest call and why it was not bet): what the Bids tab and Diagnostics show so a quiet lane can be read. */
    val gameLines: Map<String, String> = emptyMap(),
)

/**
 * Live light (Tj, 2026-10-14): obviously wrong or stale live prices, found as they appear. It rides Novig's own websocket (one shared connection, [PushedBooks]) for the prices of every live game's moneyline,
 * spread and total markets, and ESPN's scoreboard (every [stateMs]) for the score and clock. A push on a watched market, or a change of score, re-judges that one game at once ([LiveLightScan]: the score
 * and clock alone say where the game is going; [TailScan]: Novig's own liquid lines do), and what it finds is handed to the tail taker ([onOffer]) behind ITS switches and limits. Nothing here places an
 * order. A game whose score is older than [STATE_MAX_AGE_MS], whose Novig market is closed, or whose team can't be matched to the score is never judged.
 */
class LiveLightRunner(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    private val newView: (BookListener) -> PushedBooks?,
    private val fetch: suspend (url: String) -> Fetched?,
    private val onOffer: (TailOffer) -> Unit,
    /** A second live source for the moneyline (BoltOdds' consensus): lifts the cap on how large an edge can be believed. Null = Novig and the score alone. */
    private val confirm: () -> LiveConfirm? = { null },
    private val leagues: () -> Set<String>,
    /** The live bid desk Live light's bids go through, or null while the bids switch is off (nothing is wanted and any bid still up is pulled). */
    private val bids: () -> LightBidDesk? = { null },
    private val bidRules: () -> LiveLightBidRules = { LiveLightBidRules() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val rules: LiveLightRules = LiveLightRules(),
    private val tailRules: TailRules = TailRules(),
    private val catalogMs: Long = CATALOG_MS,
    private val stateMs: Long = STATE_MS,
    private val debounceMs: Long = DEBOUNCE_MS,
    /** The status flow is rewritten at most this often (a push can come many times a second; every rewrite wakes whatever reads it). */
    private val publishMs: Long = PUBLISH_MS,
) {
    private class Game(val ev: NovigEvent, val markets: List<NovigMarket>, val lines: List<LadderLine>) {
        val marketIds: List<String> = markets.map { it.marketId }
    }

    private class Feed(val game: EspnGame, val atMs: Long)

    private val _status = MutableStateFlow(LiveLightStatus())
    val status: StateFlow<LiveLightStatus> = _status.asStateFlow()

    private val lock = Any()
    @Volatile private var cur = LiveLightStatus()
    @Volatile private var lastPubMs = 0L

    /** Changes the status and publishes it, but not more than once per [publishMs] unless [force]d: readers of [status] never see a flood. */
    private fun upd(force: Boolean = false, f: (LiveLightStatus) -> LiveLightStatus) {
        synchronized(lock) {
            cur = f(cur)
            val t = System.currentTimeMillis()
            if (force || publishMs <= 0L || t - lastPubMs >= publishMs) { lastPubMs = t; _status.value = cur }
        }
    }

    /** Publishes what has changed since the last publication (the heartbeat calls this twice a second). */
    private fun flush() = synchronized(lock) { if (_status.value !== cur) { lastPubMs = System.currentTimeMillis(); _status.value = cur } }

    @Volatile private var games: Map<String, Game> = emptyMap()
    @Volatile private var marketEvent: Map<String, String> = emptyMap()
    private val feeds = ConcurrentHashMap<String, Feed>()
    private val dirty = ConcurrentHashMap.newKeySet<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val offered = HashMap<String, Pair<Long, Double>>()
    @Volatile private var view: PushedBooks? = null
    private var jobs: List<Job> = emptyList()
    @Volatile private var pushSinceMs = 0L

    val running: Boolean get() = jobs.any { it.isActive }

    /** True while the push lane is doing the work: watching live games and hearing from Novig lately. The slower lab then leaves tail offers to it. */
    fun carrying(now: Long = clock()): Boolean = running && games.isNotEmpty() && now - cur.lastPushMs < CARRY_MS

    @Synchronized
    fun start() {
        if (running) return
        upd(force = true) { LiveLightStatus(running = true) }
        bind()
        jobs = listOf(
            scope.launch { guarded("catalog") { catalogLoop() } },
            scope.launch { guarded("score") { stateLoop() } },
            scope.launch { guarded("scan") { scanLoop() } },
            scope.launch { guarded("beat") { heartbeat() } },
        )
    }

    /** A bid nobody vouched for within seconds is pulled by the desk: while bids are on every game is judged again each [HEARTBEAT_MS], push or no push. */
    private suspend fun heartbeat() {
        while (scope.isActive) {
            delay(HEARTBEAT_MS)
            flush()
            if (bids() != null && games.isNotEmpty()) { dirty += games.keys; signal.trySend(Unit) }
        }
    }

    /** Opens the view of Novig's live feed and listens to it (the loops are started by [start]; a test drives the steps by hand after this). */
    internal fun bind() {
        dirty.clear()
        val listener = BookListener { marketId, atMs, _ ->
            val ev = marketEvent[marketId] ?: return@BookListener
            dirty += ev
            pushSinceMs = atMs
            upd { it.copy(pushes = it.pushes + 1, lastPushMs = clock()) }
            signal.trySend(Unit)
        }
        view = newView(listener)?.also { it.open() }
    }

    @Synchronized
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        view?.close()
        view = null
        games = emptyMap()
        marketEvent = emptyMap()
        feeds.clear()
        dirty.clear()
        allMarkets = emptyList(); marketsAtMs = 0L; knownEvents = emptySet()
        upd(force = true) { it.copy(running = false, games = 0, markets = 0, withScore = 0) }
    }

    private suspend fun guarded(what: String, body: suspend () -> Unit) {
        while (scope.isActive) {
            try {
                body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {   // a lane that decides bets must never take the app down, and must never stop for one bad frame
                upd { it.copy(problem = "$what: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()) }
                delay(RETRY_MS)
            }
        }
    }

    private fun setLine(eventId: String, line: String) {
        if (cur.gameLines[eventId] == line) return
        upd { it.copy(gameLines = it.gameLines + (eventId to line)) }
    }

    /** The closest call among [sides] in words: the side with the best edge over what Novig asks, and the first rule it fails (or that it passed). */
    internal fun closestCall(sides: List<LiveLightScan.SideFair>): String {
        val priced = sides.filter { !it.ask.isNaN() && it.contracts > 0 }
        val best = priced.maxByOrNull { it.fair / (it.ask + com.tjshea.vigilant.engine.Fees.takerFee(it.ask.coerceIn(0.001, 0.999), it.line.fee, eventLive = true)) }
            ?: return "nothing offered to buy"
        val fee = com.tjshea.vigilant.engine.Fees.takerFee(best.ask.coerceIn(0.001, 0.999), best.line.fee, eventLive = true)
        val edge = best.fair / (best.ask + fee) - 1.0
        fun pct(d: Double) = "%.1f".format(java.util.Locale.US, d * 100)
        val why = when {
            best.fair < rules.minFair -> "the model is only ${pct(best.fair)}% sure (needs ${pct(rules.minFair)}%)"
            edge < rules.minEdge -> "the edge is under ${pct(rules.minEdge)}%"
            best.contracts < rules.minContracts -> "only ${best.contracts} contracts offered"
            best.ask > rules.maxAsk -> "the price is over ${pct(rules.maxAsk)}c"
            else -> "meets the model's rules: it is the taker's limits that decide (stake, edge minimum, caps)"
        }
        return "${best.line.label} ${best.side.lowercase()}: model ${pct(best.fair)}% vs ask ${pct(best.ask)}c = edge ${if (edge >= 0) "+" else ""}${pct(edge)}% · $why"
    }

    private fun skip(why: String) {
        upd { it.copy(skipped = it.skipped + (why to (it.skipped[why] ?: 0) + 1)) }
    }

    // ---- the games: Novig's live events and their markets, re-read every [catalogMs] and pinned on the feed -------------------------------------------------------------------------

    private suspend fun catalogLoop() {
        while (scope.isActive) {
            readCatalog()
            // The strikes worth watching move with the score: read again every [catalogMs], or at once when a game's first score arrives.
            var waited = 0L
            while (waited < catalogMs && !recatalog) { delay(500L); waited += 500L }
            recatalog = false
        }
    }

    @Volatile private var recatalog = false
    private var allMarkets: List<NovigMarket> = emptyList()
    private var marketsAtMs = 0L
    private var knownEvents: Set<String> = emptySet()

    /** The game as the model sees it, from ESPN's [e]; null when its clock can't be read. */
    private fun stateOf(ev: NovigEvent, e: EspnGame): GameState? {
        val sport = LabClock.sportOf(ev.league) ?: return null
        val frac = LabClock.fractionLeft(ev.league, e.period, e.clockSec) ?: return null
        return GameState(sport, e.homeScore, e.awayScore, frac)
    }

    internal suspend fun readCatalog() {
        val statuses = listOf(NovigEvent.STATUS_LIVE, NovigEvent.STATUS_DELAYED)
        val ls = leagues()
        val live = source.events(ls, statuses).sortedBy { it.startsTs }
        if (live.isEmpty()) {
            games = emptyMap(); marketEvent = emptyMap()
            view?.watchLive(emptyList())
            upd { it.copy(games = 0, markets = 0, withScore = 0, problem = null) }
            return
        }
        val ids = live.map { it.eventId }.toSet()
        // The market list is large (every alternate strike of every live game of the picked leagues): it is read once in [MARKETS_TTL_MS], and again at once only for a game that was not live before.
        val nowMs = clock()
        if (nowMs - marketsAtMs >= MARKETS_TTL_MS || (ids - knownEvents).isNotEmpty()) {
            allMarkets = source.markets(ls, Ladders.TYPES, statuses)
            marketsAtMs = nowMs
            knownEvents = ids
        }
        val all = allMarkets.filter { it.eventId in ids && it.isOpen }
        val room = minOf(MAX_MARKETS, view?.room() ?: MAX_MARKETS)
        val built = LinkedHashMap<String, Game>()
        var budget = room
        // The games furthest along first: they are the ones that can be decided.
        for (ev in live) {
            val ms = all.filter { it.eventId == ev.eventId }
            val allLines = ms.mapNotNull { Ladders.line(it) }
            if (allLines.isEmpty()) continue
            // Every alternate spread and total is its own market; the stream budget goes to the strikes that can be wrong now (LiveLightLines).
            val lines = LiveLightLines.window(allLines, ev.league, feeds[ev.eventId]?.game?.let { e -> stateOf(ev, e) }, feeds[ev.eventId]?.game, keep = games[ev.eventId]?.marketIds?.toSet().orEmpty())
            val keepIds = lines.mapTo(HashSet()) { it.marketId }
            val keep = ms.filter { it.marketId in keepIds }
            if (keep.size > budget) continue
            budget -= keep.size
            built[ev.eventId] = Game(ev, keep, lines)
        }
        games = built
        marketEvent = built.values.flatMap { g -> g.marketIds.map { it to g.ev.eventId } }.toMap()
        view?.watchLive(marketEvent.keys.toList())
        feeds.keys.retainAll(built.keys)
        upd { it.copy(gameLines = it.gameLines.filterKeys { k -> k in built.keys }) }
        upd { it.copy(games = built.size, markets = marketEvent.size, withScore = built.keys.count { feeds.containsKey(it) }, problem = null) }
    }

    // ---- the score: ESPN's scoreboard every few seconds; a change of score or clock is a reason to judge the game again --------------------------------------------------------------------

    private suspend fun stateLoop() {
        while (scope.isActive) {
            readScores()
            delay(stateMs)
        }
    }

    internal suspend fun readScores() {
        val now = clock()
        val g = games
        for (league in g.values.map { it.ev.league }.toSet()) {
            val path = LabClock.ESPN_PATHS[league] ?: continue
            val boards = LabClock.scoreboardUrls(league, path).mapNotNull { u -> fetch(u)?.let { LabClock.parseEspn(it.body) } }.flatten().ifEmpty { null } ?: continue
            for (game in g.values.filter { it.ev.league == league }) {
                val m = game.ev.matchup ?: continue
                val e = boards.filter { it.live }.firstOrNull { TeamMatcher.similarity(it.home, m.home) >= 0.5 && TeamMatcher.similarity(it.away, m.away) >= 0.5 }
                    ?: run {
                        if (!feeds.containsKey(game.ev.eventId)) setLine(game.ev.eventId, "${game.ev.description}: no score (ESPN's ${league} scoreboard has no live game with these teams)")
                        null
                    } ?: continue
                val before = feeds[game.ev.eventId]?.game
                if (before == null) recatalog = true
                feeds[game.ev.eventId] = Feed(e, now)
                if (before == null || before.homeScore != e.homeScore || before.awayScore != e.awayScore || before.period != e.period || before.clockSec != e.clockSec) {
                    dirty += game.ev.eventId
                    signal.trySend(Unit)
                }
            }
        }
        upd { it.copy(withScore = g.keys.count { feeds.containsKey(it) }) }
    }

    // ---- the judgment: one game, at once -----------------------------------------------------------------------------------------------------------------------------------------------

    private suspend fun scanLoop() {
        while (scope.isActive) {
            signal.receive()
            val first = clock()
            delay(debounceMs)   // a burst of pushes is one look
            val ids = dirty.toList().also { dirty.removeAll(it.toSet()) }
            for (id in ids) scanGame(id, first)
        }
    }

    internal fun scanGame(eventId: String, triggeredAtMs: Long = clock()) {
        val game = games[eventId] ?: return
        val now = clock()
        val feed = feeds[eventId] ?: return skip("no score for the game")
        if (now - feed.atMs > STATE_MAX_AGE_MS) return skip("score older than ${STATE_MAX_AGE_MS / 1000} s")
        val sport = LabClock.sportOf(game.ev.league) ?: return
        val frac = LabClock.fractionLeft(game.ev.league, feed.game.period, feed.game.clockSec) ?: return skip("clock not readable (overtime or unknown)")
        val st = GameState(sport, feed.game.homeScore, feed.game.awayScore, frac)
        val books = view?.liveTop(game.marketIds).orEmpty()
        if (books.isEmpty()) return skip("no books from the live feed")
        val points = game.lines.mapNotNull { line ->
            val b = books[line.marketId] ?: return@mapNotNull null
            if (b.lifecycle?.closed == true) null else LadderPoint(line, b)
        }
        if (points.isEmpty()) return skip("markets closed")
        val marginOf = { ref: String -> feed.game.marginOf(ref) }
        val sides = LiveLightScan.sides(points, game.ev.league, st, marginOf, rules)
        manageBids(game, sides, feed.game, now)
        val scoreText = "${feed.game.away} ${feed.game.awayScore} @ ${feed.game.home} ${feed.game.homeScore} · ${"%.0f".format(java.util.Locale.US, frac * 100)}% left · ${points.size} of ${game.lines.size} lines priced"
        setLine(eventId, "$scoreText · closest: ${if (sides.isEmpty()) "the model cannot judge this game yet" else closestCall(sides)}")
        val curve = LiveLightCurve.scan(points, game.ev.league, st, sides.associate { it.outcomeId to it.fair })
        val found = LiveLightScan.scan(points, game.ev.league, st, marginOf, rules) + curve + TailScan.scan(points, st, marginOf, tailRules).map { it.copy(rule = TailTaker.CONSERVATIVE) }
        upd { it.copy(scans = it.scans + 1, lastScanMs = now, lastScanTookMs = (clock() - triggeredAtMs).coerceAtLeast(0)) }
        // An outcome the score calls decided is judged by the more cautious of the score models; a curve bet is only for an outcome they do not call.
        for (c in found.groupBy { it.outcomeId }.values.map { g -> g.filter { it.rule != LiveLightCurve.RULE }.minByOrNull { it.fair } ?: g.first() }.sortedByDescending { it.edge }) {
            val prev = offered[c.outcomeId]
            if (prev != null && now - prev.first < REOFFER_MS && c.ask >= prev.second - 1e-9) continue
            val line = game.lines.firstOrNull { it.marketId == c.marketId } ?: continue
            var cand = c
            var second: Double? = null
            if (line.kind == com.tjshea.vigilant.data.novig.burst.LadderKind.MARGIN && line.threshold == 0.0) {
                val pRef = confirm()?.moneyline(game.ev, line.ref, now)
                val pBet = pRef?.let { if (c.side == "YES") it else 1.0 - it }
                if (pBet != null && pBet >= CONFIRM_MIN) {
                    // Priced by the more cautious of the two views, two points under the second one.
                    val fair = minOf(c.fair, pBet - CONFIRM_MARGIN)
                    val fee = com.tjshea.vigilant.engine.Fees.takerFee(c.ask.coerceIn(0.001, 0.999), line.fee, eventLive = true)
                    cand = c.copy(fair = fair, edge = fair / (c.ask + fee) - 1.0)
                    second = pBet
                }
            }
            if (cand.edge > TailTradeLimits.MAX_EDGE && second == null) { skip("edge over ${(TailTradeLimits.MAX_EDGE * 100).toInt()}% and no second live source agrees"); continue }
            val offer = (TailOffers.build(game.ev, game.markets, points, st, cand, now) ?: continue).copy(confirm = second)
            offered[c.outcomeId] = now to c.ask
            upd { it.copy(offers = it.offers + 1, last = "${c.rule} · ${offer.target.selection} · ${"%.1f".format(java.util.Locale.US, c.edge * 100)}% edge · ${offer.state}") }
            runCatching { onOffer(offer) }
        }
    }

    /** The bids for one game: every side the model calls decided gets a bid under its fair (the desk's own limits decide how many go up); a bid up whose side is no longer clear comes down. */
    private fun manageBids(game: Game, sides: List<LiveLightScan.SideFair>, e: EspnGame, now: Long) {
        val desk = bids()
        if (desk == null) return
        val br = bidRules()
        val score = "${e.homeScore}-${e.awayScore}"
        val clock = "P${e.period} ${"%d:%02d".format(java.util.Locale.US, (e.clockSec / 60).toInt(), (e.clockSec % 60).toInt())}"
        val seen = HashSet<String>()
        for (sf in sides) {
            seen += sf.outcomeId
            desk.noteFair(sf.outcomeId, sf.fair, now)
            val held = desk.held(sf.outcomeId)
            if (held != null) {
                val why = LiveLightBids.keep(sf, held, br)
                if (why != null) { desk.pull(sf.outcomeId, why); upd { it.copy(bidPulls = it.bidPulls + 1) } } else desk.keepAlive(sf.outcomeId, now)
                continue
            }
            when (val v = LiveLightBids.judge(sf, br)) {
                is com.tjshea.vigilant.data.livebid.LiveBidVerdict.Post -> {
                    desk.want(LiveLightBids.want(game.ev, sf, v, score, clock, now))
                    upd { it.copy(bidWants = it.bidWants + 1) }
                }
                is com.tjshea.vigilant.data.livebid.LiveBidVerdict.Skip -> desk.noWant(sf.outcomeId, "light bid: ${v.reason}")
            }
        }
        // A bid up on an outcome the model can no longer judge (the clock went unreadable, the game entered overtime) does not stay on a guess.
        for (line in game.lines) for (id in listOf(line.yesOutcomeId, line.noOutcomeId)) {
            if (id !in seen && desk.held(id) != null) { desk.pull(id, "the game is no longer judged"); upd { it.copy(bidPulls = it.bidPulls + 1) } }
        }
    }

    companion object {
        const val HEARTBEAT_MS = 1_000L
        const val PUBLISH_MS = 500L

        /** The market list (every live alternate line of every picked league: megabytes of JSON) is read again this often, or at once when a game that was not live is. */
        const val MARKETS_TTL_MS = 90_000L

        /** Markets the lane may hold on the feed: a bbo market costs 8 of the key's 512 stream tokens, and the scan and the bids share them. */
        const val MAX_MARKETS = 48
        const val CATALOG_MS = 30_000L
        const val STATE_MS = 3_000L
        const val DEBOUNCE_MS = 120L
        const val RETRY_MS = 2_000L

        /** A score older than this is not what the game is now: nothing is judged on it. */
        const val STATE_MAX_AGE_MS = 12_000L

        /** The same outcome is handed to the taker again only after this long, or at a better price. */
        const val REOFFER_MS = 1_500L

        /** Pushes heard within this long = the lane is carrying the load. */
        const val CARRY_MS = 60_000L

        /** A second live source counts only when it too calls the bet at least this likely, priced [CONFIRM_MARGIN] under it. */
        const val CONFIRM_MIN = 0.85
        const val CONFIRM_MARGIN = 0.02
    }
}
