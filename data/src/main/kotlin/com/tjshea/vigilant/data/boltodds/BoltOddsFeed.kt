package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * BoltOdds as a source of fair lines (BOLTODDS_API.md §7): the two websockets ([lines] for moneylines, spreads, totals, team totals and the 1st-half / F5 / 1st-inning lines; [props] for player props), their
 * boards, and the cached `get_info` / `get_markets` that decide what to subscribe to. A scan does not ask BoltOdds anything: it reads what the sockets have already delivered ([events]), which costs nothing
 * however often it is asked, and the first read after a connection waits (up to [READY_WAIT_MS]) for the snapshot.
 *
 * What is subscribed: the sports of the leagues picked in Settings that BoltOdds carries; the sportsbooks among `get_info`'s that are the books Vigilant prices with (or every book, [com.tjshea.vigilant.data.scanner.ApiGates.boltAllBooks]);
 * and the market names among `get_markets`' that [BoltMarkets] can use. If `get_info` or `get_markets` cannot be read the subscription goes without that filter (the board keeps only what it can use anyway).
 */
class BoltOddsFeed(
    val client: BoltOddsClient,
    http: OkHttpClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    wsUrl: String = BoltOddsStream.DEFAULT_WS_URL,
) {
    private val socketHttp = BoltOddsStream.socketClient(http)

    val lines = BoltOddsStream("lines", socketHttp, client, BoltBoard(clock) { it != LineKind.PLAYER_PROP }, scope, wsUrl, clock)
    val props = BoltOddsStream("props", socketHttp, client, BoltBoard(clock) { it == LineKind.PLAYER_PROP }, scope, wsUrl, clock)

    private class Kept<T>(val atMs: Long, val value: T)
    private val metaLock = Mutex()
    private var infoKept: Kept<BoltInfo>? = null
    private var marketsKept: Kept<Map<String, Map<String, List<String>>>>? = null
    private var marketsFor: Pair<List<String>, List<String>>? = null

    @Volatile var lastPlan: String? = null
        private set

    /** The latest reason `get_info` / `get_markets` could not be read, for Diagnostics. */
    @Volatile var metaError: String? = null
        private set

    /** True while at least one socket that a scan uses is connected. */
    val connected: Boolean get() = lines.live || props.live

    /** The sportsbooks and sports BoltOdds lists for this key (cached six hours; a failed refresh keeps the old list). */
    suspend fun info(): BoltInfo? = metaLock.withLock {
        val kept = infoKept
        if (kept != null && clock() - kept.atMs < META_TTL_MS) return@withLock kept.value
        try {
            client.info().also { infoKept = Kept(clock(), it); metaError = null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            metaError = client.redact(e.message ?: e.javaClass.simpleName).take(160)
            // Not asked again for a while after a failure, so a scan does not spend a GET (12 a minute) on every league.
            infoKept = kept?.let { Kept(clock() - META_TTL_MS + RETRY_MS, it.value) }
            kept?.value
        }
    }

    private suspend fun markets(sports: List<String>, books: List<String>): Map<String, Map<String, List<String>>>? = metaLock.withLock {
        val kept = marketsKept
        if (kept != null && marketsFor == (sports to books) && clock() - kept.atMs < META_TTL_MS) return@withLock kept.value
        try {
            client.markets(sports, books).also { marketsKept = Kept(clock(), it); marketsFor = sports to books; metaError = null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            metaError = client.redact(e.message ?: e.javaClass.simpleName).take(160)
            marketsKept = Kept(clock() - META_TTL_MS + RETRY_MS, kept?.value ?: emptyMap())
            marketsFor = sports to books
            kept?.value
        }
    }

    /** The BoltOdds leagues among the selected ones, in Settings' order. */
    private fun leaguesOf(settings: ScanSettings): List<League> = settings.selectedLeagues.filter { BoltBooks.supports(it) }

    /**
     * Subscribes both sockets for [settings] (waking them if they are asleep) and returns them. The props socket is only used while player props are among the market families.
     */
    suspend fun arrange(settings: ScanSettings): List<BoltOddsStream> {
        val leagues = leaguesOf(settings)
        if (leagues.isEmpty()) return emptyList()
        val info = info()
        val sportsByLeague = leagues.associateWith { BoltBooks.sportsFor(it, info?.sports) }
        val sports = sportsByLeague.values.flatten().distinct().sorted()
        val wanted = BoltBooks.wanted(settings)
        val books = info?.let { BoltBooks.slugsFor(wanted, it.sportsbooks) }.orEmpty()
        val markets = markets(sports, books)
        val wantProps = MarketFamily.PLAYER_PROPS in settings.families
        fun names(props: Boolean): List<String> = if (markets == null) emptyList() else markets.values.flatMap { bySport ->
            bySport.flatMap { (sport, list) ->
                val league = BoltBooks.leagueOfSport(sport) ?: return@flatMap emptyList()
                list.filter { BoltMarkets.usableName(league, it, props) }
            }
        }.distinct().sorted()
        val out = ArrayList<BoltOddsStream>(2)
        val lineMarkets = names(false)
        lines.want(BoltFilters(sports = sports, sportsbooks = books, markets = lineMarkets))
        out += lines
        if (wantProps) {
            props.want(BoltFilters(sports = sports, sportsbooks = books, markets = names(true)))
            out += props
        } else {
            props.close()
        }
        lastPlan = "sports ${sports.size} · books ${books.size.takeIf { it > 0 } ?: "all"} · line markets ${lineMarkets.size.takeIf { it > 0 } ?: "all"}"
        return out
    }

    /**
     * [league]'s reference events from the sockets' boards: lines ([props] false) or player props. Waits for the first snapshot after a connection; if the socket cannot be reached throws, so the scan says why.
     */
    suspend fun events(league: League, settings: ScanSettings, props: Boolean): List<RefEvent> {
        if (!BoltBooks.supports(league)) return emptyList()
        val streams = arrange(settings)
        val stream = if (props) this.props else lines
        if (stream !in streams) return emptyList()
        stream.use()
        val ready = stream.awaitReady(READY_WAIT_MS)
        if (!ready && !stream.live) {
            throw com.tjshea.vigilant.data.reference.ReferenceException("BoltOdds: the ${stream.name} socket is not connected (${stream.lastError ?: "connecting"})")
        }
        val info = info()
        val sports = BoltBooks.sportsFor(league, info?.sports)
        val ctx = BoltContext(
            league = league, wanted = BoltBooks.wanted(settings), families = settings.families, includeLive = settings.includeLive, nowMs = clock(),
            aliveAt = { book, sport -> stream.aliveAt(book, sport) },
        )
        return BoltConvert.events(stream.board.gamesOf(sports), ctx)
    }

    private val socketWsUrl = wsUrl

    /** A socket of its own (Pro allows 3 at once; the lines and props sockets are 2) for Test key's listening probe: its own board, nothing shared. */
    fun probeStream(): BoltOddsStream = BoltOddsStream("probe", socketHttp, client, BoltBoard(clock), scope, socketWsUrl, clock)

    /** Both sockets down and idle (the switch went off, or the app is closing). */
    fun close() {
        lines.close()
        props.close()
    }

    companion object {
        const val META_TTL_MS = 6 * 3_600_000L
        const val RETRY_MS = 5 * 60_000L

        /** How long the first read after a connection waits for the snapshot. */
        const val READY_WAIT_MS = 25_000L
    }
}
