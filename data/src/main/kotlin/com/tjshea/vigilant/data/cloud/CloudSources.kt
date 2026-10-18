package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.boltodds.BoltGamesSource
import com.tjshea.vigilant.data.boltodds.BoltOddsClient
import com.tjshea.vigilant.data.boltodds.BoltOddsFeed
import com.tjshea.vigilant.data.boltodds.BoltPropsSource
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.InjuryIndex
import com.tjshea.vigilant.data.kalshi.KalshiCredentials
import com.tjshea.vigilant.data.kalshi.KalshiSigner
import com.tjshea.vigilant.data.kalshi.KalshiStream
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.OddsPapi4Client
import com.tjshea.vigilant.data.reference.OddsPapiClient
import com.tjshea.vigilant.data.reference.OddsPapiFeed
import com.tjshea.vigilant.data.reference.Op4Budget
import com.tjshea.vigilant.data.reference.Op4Catalog
import com.tjshea.vigilant.data.reference.Op4Feed
import com.tjshea.vigilant.data.reference.Op4GamesSource
import com.tjshea.vigilant.data.reference.Op4PropsSource
import com.tjshea.vigilant.data.reference.Op4Store
import com.tjshea.vigilant.data.reference.OpGamesSource
import com.tjshea.vigilant.data.reference.OpPropsSource
import com.tjshea.vigilant.data.reference.ParlayInjuries
import com.tjshea.vigilant.data.reference.ParlayPeriodSource
import com.tjshea.vigilant.data.reference.ParlayPropsSource
import com.tjshea.vigilant.data.reference.ParlaySourceQuality
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PlayerOut
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.SgoGamesSource
import com.tjshea.vigilant.data.reference.SgoPropsSource
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.reference.UnabatedClient
import com.tjshea.vigilant.data.reference.UnabatedFeed
import com.tjshea.vigilant.data.reference.UnabatedGamesSource
import com.tjshea.vigilant.data.reference.UnabatedPropsSource
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * The fair-odds feeds, built for the GitHub engine from repository secrets (Tj, 2026-10-14: the scans run on GitHub's runners). It mirrors the phone's wiring (`AppContainer.allReferenceSources`) so
 * the cloud prices a bet from the same sources, in the same order, under the same on/off switches: **a feed that is switched off in the settings, or has no secret, is not in the list and is never
 * called** (Tj, 2026-10-11).
 *
 * Left out on purpose, and why:
 *  - **Pinnodds** (one WebSocket per account, and a second connection evicts the app's; Tj, 2026-10-09) stays on the phone.
 *  - **BoltOdds** is here since 2026-10-15 (Tj: "allow me to send my novig key and boltodds key to GitHub for rapid scanning"): the engine opens the two odds sockets itself (lines, props) while BoltOdds is switched on and its
 *    socket switch is on (off = the REST snapshot), and reads their boards from memory like the phone does. The plan allows 3 connections: the engine uses 2, so the phone's own sockets (the live lane, the streaming engine)
 *    and the Test key's probe share the one left.
 *  - **Kalshi's signed key** (an API key can trade; placed in a repository secret only on Tj's word of 2026-10-16, TCL45): the engine reads with it and runs Kalshi's socket; without it, Kalshi's public markets unsigned.
 *
 * [keys] maps a [CloudKeys] name to the secret's value (comma, space or newline separated for several), read once per call so a rotated secret is picked up by the next job.
 */
class CloudSources(
    private val http: OkHttpClient,
    private val json: Json,
    private val usage: UsageMeter,
    private val scope: CoroutineScope,
    private val stateDir: File,
    private val keys: (String) -> List<String>,
) {
    private val injuries = InjuryIndex()

    private val sgoClient = SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { keys(CloudKeys.SGO) }, usage), json)
    private val sgoGames = SgoGamesSource(sgoClient)
    private val sgoProps = SgoPropsSource(sgoClient, injuries = injuries)

    private val opClient = OddsPapiClient(http, KeyPool(QuotaPolicy.ODDSPAPI, { keys(CloudKeys.ODDSPAPI) }, usage))
    private val opFeed = OddsPapiFeed(opClient)
    private val opGames = OpGamesSource(opFeed)
    private val opProps = OpPropsSource(opFeed)

    private val unabatedClient = UnabatedClient(http, { keys(CloudKeys.UNABATED) }, usage)
    private val unabatedFeed = UnabatedFeed(unabatedClient, scope)
    private val unabatedGames = UnabatedGamesSource(unabatedFeed)
    private val unabatedProps = UnabatedPropsSource(unabatedFeed)

    private val op4Client = OddsPapi4Client(http, KeyPool(QuotaPolicy.ODDSPAPI_FREE, { keys(CloudKeys.ODDSPAPI_FREE) }, usage), { keys(CloudKeys.ODDSPAPI_FREE) })
    private val op4Budget = Op4Budget(op4Client)
    private val op4Store = Op4Store(File(stateDir, "oddspapi_free_cache.json"))
    private val op4Feed = Op4Feed(op4Client, Op4Catalog(op4Client, op4Budget, op4Store, lenient = true), op4Budget, op4Store)
    private val op4Games = Op4GamesSource(op4Feed)
    private val op4Props = Op4PropsSource(op4Feed)

    private val boltFeed = BoltOddsFeed(BoltOddsClient(http, { keys(CloudKeys.BOLT) }, usage), http, scope)
    private val boltGames = BoltGamesSource(boltFeed)
    private val boltProps = BoltPropsSource(boltFeed)

    private val polymarket = PolymarketClient(http, json, usage = usage)
    private val sxbet = com.tjshea.vigilant.data.reference.SxBetClient(http, json, { keys(CloudKeys.SXBET).firstOrNull().orEmpty() }, usage, socketOn = { sxSocketWanted }, scope = scope)
    /**
     * Kalshi's signed key and socket (Tj, 2026-10-16, chat: "I want to send my kalshi key to GitHub so GitHub can use the socket for faster odds"; he accepted that an API key can trade, which is why this
     * is his word and not a default): the phone's sealed key goes up as [CloudKeys.KALSHI_KEY_ID] + [CloudKeys.KALSHI_KEY_PEM]. The engine only READS with it (signed market reads and the socket's price pushes);
     * no code path here places a Kalshi order. Without the secrets, or with the socket switch off, it is the unsigned REST reads as before.
     */
    private val kalshiSigner: KalshiSigner? by lazy {
        val id = CloudKeys.rawEnv(CloudKeys.KALSHI_KEY_ID); val pem = CloudKeys.rawEnv(CloudKeys.KALSHI_KEY_PEM)
        if (id.isEmpty() || pem.isEmpty()) null else KalshiSigner.of(KalshiCredentials(id, pem)).getOrNull()
    }
    @Volatile private var kalshiSocketWanted = false
    private val kalshiStream = KalshiStream(KalshiStream.socketClient(http), { kalshiSigner }, scope)
    private val kalshi = KalshiClient(
        http, json, altBaseUrl = KalshiClient.ALT_URL, usage = usage,
        signer = { kalshiSigner }, stream = kalshiStream, socketOn = { kalshiSocketWanted },
    )
    /** The engine's sockets in words, one line each (the Live dashboard on the phone shows them while the engine is the scan). */
    fun socketLines(s: ScanSettings, now: Long = System.currentTimeMillis()): List<String> {
        fun ago(ms: Long) = if (ms <= 0L) "never" else "${((now - ms) / 1000L).coerceAtLeast(0)} s ago"
        return buildList {
            add("Engine BoltOdds: socket ${if (s.gates.boltSocket) "ON" else "OFF"} · lines ${if (boltFeed.lines.live) "live" else "not live"} · props ${if (boltFeed.props.live) "live" else "not live"}")
            add("Engine Kalshi: " + (if (kalshiSigner == null) "no key on GitHub (public reads)" else "socket ${if (kalshiSocketWanted) "ON" else "OFF"} · ${if (kalshiStream.live) "live" else "not live"} · ${kalshiStream.subscribedCount} of ${kalshiStream.wantedCount} markets"))
            val sx = sxbet.stream
            add("Engine SX Bet: " + (if (!s.gates.useSxBet) "API off" else if (sx == null) "socket ${if (sxSocketWanted) "ON" else "OFF"} · no connection yet (opens with the first live game or scan)" else "socket ${if (sxSocketWanted) "ON" else "OFF"} · ${sx.health()} · last push ${ago(sx.lastMessageMs)} · ${sx.fixtureStatus.values.count { it == 2 }} games in play"))
        }
    }

    /** The runner's sockets as the Live dashboard draws them (TCL122-4): [s] is the settings the engine runs under ([CloudEngine.forCloud]). */
    fun socketStates(s: ScanSettings): List<SocketState> {
        val sx = sxbet.stream
        val bolt = boltActive(s) && s.gates.boltSocket
        return listOf(
            SocketState("BoltOdds lines", bolt, boltFeed.lines.live, boltFeed.lines.lastFrameMs, if (!s.gates.useBoltOdds) "API off" else if (!has(CloudKeys.BOLT)) "no key on GitHub" else if (!bolt) "socket off: read by request" else ""),
            SocketState("BoltOdds props", bolt, boltFeed.props.live, boltFeed.props.lastFrameMs, ""),
            SocketState("Kalshi", kalshiSocketWanted, kalshiStream.live, kalshiStream.lastFrameMs, if (kalshiSigner == null) "no key on GitHub (public reads)" else "${kalshiStream.subscribedCount} of ${kalshiStream.wantedCount} markets"),
            SocketState("SX Bet", sxSocketWanted, sx?.live == true, sx?.lastMessageMs ?: 0L, if (!s.gates.useSxBet) "API off" else if (sx == null) "no read key on GitHub" else "${sx.fixtureStatus.values.count { it == 2 }} games in play"),
        )
    }

    /** One line for the engine's start-up log and status: whether Kalshi's key reached the engine and whether its socket is wanted. */
    fun kalshiNote(): String = if (kalshiSigner == null) "Kalshi: public reads (no key on GitHub)" else "Kalshi: signed key present, socket ${if (kalshiSocketWanted) "on" else "off"}"
    private val oddsApi = TheOddsApiClient(http, KeyPool(QuotaPolicy.ODDS_API, { keys(CloudKeys.ODDS_API) }, usage), json)
    private val bookProps = OddsApiPropsSource(oddsApi)
    private val parlayPool = KeyPool(QuotaPolicy.PARLAY, { keys(CloudKeys.PARLAY) }, usage)
    /** ParlayAPI's own account read per key (free): plan and credits into the meter, so an upgraded key is found and goes first ([KeyPool]). The engine asks hourly ([refreshParlayAccounts]). */
    private val parlayAccount = com.tjshea.vigilant.data.reference.ParlayAccount(http, { keys(CloudKeys.PARLAY) }, usage, json)
    /** Which ParlayAPI key (by position, never the key) is on which plan, as last read: for the engine's log. */
    fun parlayPlans(): String = keys(CloudKeys.PARLAY).mapIndexedNotNull { i, k -> parlayAccount.last[k]?.let { c -> "#${i + 1} ${c.tier ?: "?"} ${c.remaining ?: "?"}/${c.limit ?: "?"}" } }.joinToString(", ").ifBlank { "none read" }
    suspend fun refreshParlayAccounts(force: Boolean = false): Int = if (has(CloudKeys.PARLAY)) parlayAccount.refresh(force, minAgeMs = 50 * 60_000L) else 0
    private val parlayQuality = ParlaySourceQuality(http, json)
    private val parlayOdds = TheOddsApiClient(http, parlayPool, json, baseUrl = OddsFeed.PARLAY.base, feed = OddsFeed.PARLAY, quality = parlayQuality)
    private val parlayProps = ParlayPropsSource(parlayOdds, injuries)
    @Volatile private var parlayInjuriesOn = false
    private val parlayInjuries = ParlayInjuries(parlayOdds, injuries, json, active = { parlayInjuriesOn })
    private val parlayHalves = ParlayPeriodSource(parlayOdds, json)
    private val pinnacle = PinnapiClient(
        http, json,
        listOf(
            PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { keys(CloudKeys.PINNWIRE) }, usage)),
            PinnapiClient.pinnapi(KeyPool(QuotaPolicy.PINNAPI, { keys(CloudKeys.PINNAPI) }, usage)),
        ),
        // Pinnodds' REST board first when its key is on GitHub and its switch is on (as on the phone); PinnWire carries on if it fails.
        first = { if (pinnoddsOn && has(CloudKeys.PINNODDS)) pinnoddsHost else null },
    )
    @Volatile private var pinnoddsOn = false
    private val pinnoddsHost = PinnapiClient.pinnodds(KeyPool(QuotaPolicy.PINNODDS, { keys(CloudKeys.PINNODDS) }, usage))
    private val propLine = PropLineClient(http, KeyPool(QuotaPolicy.PROPLINE, { keys(CloudKeys.PROPLINE) }, usage), json, relayNovig = true, bookIds = false, meter = usage)
    private val propLineProps = PropLinePropsSource(propLine)
    /** PropLine's plan per key from its quota headers (Tj, 2026-10-16, TCL56): the paid key is found and used first ([KeyPool]). The engine asks hourly. */
    private val propLineAccount = com.tjshea.vigilant.data.reference.PropLineAccount(http, { keys(CloudKeys.PROPLINE) }, usage)
    fun propLinePlans(): String = keys(CloudKeys.PROPLINE).mapIndexedNotNull { i, k -> propLineAccount.last[k]?.let { c -> "#${i + 1} ${c.tier} used ${if (c.remaining != null && c.limit != null) c.limit!! - c.remaining!! else "?"}/${c.limit ?: "?"}" + (if (!c.valid) " (${c.reason})" else "") } }.joinToString(", ").ifBlank { "none read" }
    private val propLineEv = com.tjshea.vigilant.data.reference.PropLineEvSweep(propLine, json)
    /** PropLine's +EV-at-Novig sweep (TCL56): only with a paid key; a free key's answers are redacted. */
    suspend fun propLineEvSweep(s: ScanSettings, now: Long, scanned: List<com.tjshea.vigilant.data.scanner.Opportunity> = emptyList(), unpriced: List<com.tjshea.vigilant.data.scanner.Opportunity> = emptyList()) = propLineEv.sweep(s, now, scanned, unpriced)
    fun propLinePaid(): Boolean = propLine.plan() != null
    /** The paid key is within the day's share of its requests (the props read the same budget). */
    fun propLineHasRoom(now: Long): Boolean = propLine.roomForEv(now)
    suspend fun refreshPropLineAccounts(force: Boolean = false): Int = if (has(CloudKeys.PROPLINE)) propLineAccount.refresh(force, minAgeMs = 50 * 60_000L) else 0

    /** PinnWire's own account read per key (free, TCL119): a key is spent only when PinnWire says so. Asked only while PinnWire is on ([sources] sets the flag; off = never called). */
    private val pinnWireAccount = com.tjshea.vigilant.data.reference.PinnWireAccount(http, { keys(CloudKeys.PINNWIRE) }, usage, json)
    @Volatile private var pinnWireWanted = false
    suspend fun refreshPinnWireAccounts(force: Boolean = false): Int = if (pinnWireWanted && has(CloudKeys.PINNWIRE)) pinnWireAccount.refresh(force, minAgeMs = 10 * 60_000L) else 0
    fun pinnWirePlans(): String = keys(CloudKeys.PINNWIRE).mapIndexedNotNull { i, k -> pinnWireAccount.last[k]?.let { c -> "#${i + 1} ${if (c.valid == false) "refused" else "left ${c.remaining ?: "unknown"}/${c.limit ?: "?"}"}" } }.joinToString(", ").ifBlank { "none read" }

    private fun has(name: String) = keys(name).isNotEmpty()

    fun sgoActive(s: ScanSettings) = s.sgoPro && has(CloudKeys.SGO) && !sgoClient.down()
    fun opActive(s: ScanSettings) = s.oddsPapi && has(CloudKeys.ODDSPAPI) && !opClient.down()
    fun boltActive(s: ScanSettings) = s.gates.useBoltOdds && has(CloudKeys.BOLT)
    /** BoltOdds' soft-book average as Live light's second opinion on a moneyline (the phone's `confirm`); null while BoltOdds or its socket is off. */
    fun liveConfirm(s: ScanSettings): com.tjshea.vigilant.data.novig.lab.LiveConfirm? =
        if (boltActive(s) && s.gates.boltSocket) com.tjshea.vigilant.data.novig.lab.BoltLiveConfirm(boltFeed.lines.board) else null

    /** The BoltOdds soft-book average as a live price feed for [com.tjshea.vigilant.data.pinnodds.PinnLiveRunner] (the phone's `BoltLiveFeed`, same arguments); the engine has no Pinnodds. */
    fun boltLiveFeed(settings: () -> ScanSettings, sink: (String, Long) -> Unit, scope: kotlinx.coroutines.CoroutineScope, rules: () -> com.tjshea.vigilant.data.boltodds.BoltLiveConsensus.Rules): com.tjshea.vigilant.data.boltodds.BoltLiveFeed =
        com.tjshea.vigilant.data.boltodds.BoltLiveFeed(
            board = boltFeed.lines.board, socketLive = { boltFeed.lines.live }, ensureOpen = { boltFeed.arrange(settings()); boltFeed.lines.use() }, sink = sink, scope = scope, rules = rules,
        ).also { boltFeed.liveTennis = true }

    /** SX Bet's exchange mid as a live price feed (TCL118); null without the SX socket (no key / no scope). */
    fun sxLiveFeed(settings: () -> ScanSettings, sink: (String, Long) -> Unit, scope: kotlinx.coroutines.CoroutineScope): com.tjshea.vigilant.data.sxbet.SxBetLiveFeed? {
        val st = sxbet.stream ?: return null
        return com.tjshea.vigilant.data.sxbet.SxBetLiveFeed(st, sxbet, ensureOpen = { sxSocketWanted = true; st.open() }, sink = sink, scope = scope)
    }

    /** SX Bet's socket (null without a scope): the engine hangs its research recorder on it. */
    val sxStream: com.tjshea.vigilant.data.sxbet.SxBetStream? get() = sxbet.stream

    @Volatile private var sxSocketWanted = false

    fun unabatedActive(s: ScanSettings) = s.unabated && has(CloudKeys.UNABATED) && !unabatedClient.down()
    fun op4Active(s: ScanSettings) = s.oddsPapiFree && s.gates.op4Fair > 0 && has(CloudKeys.ODDSPAPI_FREE)

    private fun propsOf(s: ScanSettings, props: ReferenceSource): List<ReferenceSource> = if (MarketFamily.PLAYER_PROPS in s.families) listOf(props) else emptyList()

    /** The flat-rate feeds that are on, SGO first, Unabated last; then the rest. Same order as the phone's list. */
    /** The sockets the settings want, the others closed at once: nothing is held open or sent while a switch (or the GitHub / Phone live switch, [CloudEngine.forCloud]) says no. */
    fun applySockets(s: ScanSettings) {
        if (!boltActive(s) || !s.gates.boltSocket) boltFeed.close()
        sxSocketWanted = s.gates.useSxBet && s.gates.sxSocket
        if (!sxSocketWanted) sxbet.stream?.close()
        kalshiSocketWanted = s.useKalshi && s.gates.kalshiSocket && kalshiSigner != null
        if (!kalshiSocketWanted) kalshiStream.close()
    }

    fun sources(s: ScanSettings): List<ReferenceSource> {
        applySockets(s)
        pinnWireWanted = s.usePinnacle && s.gates.usePinnwire
        val front = buildList {
            if (sgoActive(s)) { add(sgoGames); addAll(propsOf(s, sgoProps)) }
            if (opActive(s)) { add(opGames); addAll(propsOf(s, opProps)) }
            // BoltOdds: read from the sockets' boards, which costs nothing, so it is asked for every league it carries beside every other API that is on (as on the phone).
            if (boltActive(s)) { add(boltGames); addAll(propsOf(s, boltProps)) }
            if (op4Active(s)) { add(op4Games); addAll(propsOf(s, op4Props)) }
        }
        val last = buildList { if (unabatedActive(s)) { add(unabatedGames); addAll(propsOf(s, unabatedProps)) } }
        return front + base(s) + last
    }

    /**
     * Why a side must not be bid on: its player is out ([PlayerOut]; the reports are the props answers' own, and ParlayAPI's injury list for the prop players no report covers). The phone's own
     * [com.tjshea.vigilant.data.novig.trading.maker.MakerLines] gets the same answer from the app's index; the candidate lines are made here, so the engine asks.
     */
    fun unavailable(o: com.tjshea.vigilant.data.scanner.Opportunity, now: Long): String? = PlayerOut.forOpportunity(injuries.book.value, o, now)

    /**
     * Asks ParlayAPI's injury list about the prop players of [result]'s priced lines that no report covers yet: one read a sport at most every [ParlayInjuries.REUSE_MS], only while ParlayAPI is on with a key
     * (as `MakerRunner.askInjuries` does on the phone). Awaited, so the candidate lines made right after already know the answer (a few requests at most).
     */
    suspend fun askInjuries(result: com.tjshea.vigilant.data.scanner.ScanResult, s: ScanSettings, now: Long) {
        parlayInjuriesOn = s.useParlay && has(CloudKeys.PARLAY)
        if (!parlayInjuriesOn) return
        val book = injuries.book.value
        val uncovered = HashMap<String, MutableSet<String>>()
        val seen = HashSet<String>()
        for (o in result.opportunities) {
            if (o.kind != com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP || o.isLive || !o.league.oddsApiListed || o.league.novigName !in s.leagues) continue
            val sport = o.league.oddsApiSportKey
            if (sport !in ParlayInjuries.SPORTS) continue
            val player = o.lineKey?.subject ?: continue
            if (!seen.add("$sport|$player") || book.covers(sport, player, now)) continue
            uncovered.getOrPut(sport) { HashSet() } += player
        }
        if (uncovered.isEmpty()) return
        kotlinx.coroutines.coroutineScope { uncovered.map { (sport, players) -> async { runCatching { parlayInjuries.fill(sport, players) } } }.awaitAll() }
    }

    /** What is in [sources], by name, for the status file and the logs (never a key). */
    fun names(s: ScanSettings): List<String> = sources(s).map { it.id }

    private fun base(s: ScanSettings): List<ReferenceSource> = buildList {
        pinnoddsOn = s.gates.usePinnodds
        val pinnacleOn = s.usePinnacle && ((s.gates.usePinnodds && has(CloudKeys.PINNODDS)) || (s.gates.usePinnwire && has(CloudKeys.PINNWIRE)) || (s.gates.usePinnapi && has(CloudKeys.PINNAPI)))
        if (pinnacleOn) add(pinnacle)
        // ParlayAPI's alternate lines are Pinnacle's: bought only when PinnWire/pinnapi aren't sending them (as on the phone).
        parlayOdds.alternates = !pinnacleOn
        parlayHalves.pinnacleFeedOn = pinnacleOn
        if (s.usePolymarket) add(polymarket)
        // SX Bet: markets are public but prices need the read-only key; with the switch on and a key it prices beside the other exchanges.
        if (s.gates.useSxBet && has(CloudKeys.SXBET) && !sxbet.down()) add(sxbet)
        if (s.useKalshi) add(kalshi)
        if (s.usePropLine && has(CloudKeys.PROPLINE)) {
            add(propLine)
            if (s.useBookProps) add(propLineProps)
        }
        if (s.useOddsApi && has(CloudKeys.ODDS_API)) {
            add(oddsApi)
            if (s.useBookProps) add(bookProps)
        }
        if (s.useParlay && has(CloudKeys.PARLAY)) {
            add(parlayOdds)
            if (s.useBookProps) add(parlayProps)
            add(parlayHalves)
        }
    }
}

/** The repository secrets the engine reads (each may hold several keys, separated by commas, spaces or newlines). */
object CloudKeys {
    const val SGO = "SGO_API_KEY"
    const val ODDSPAPI = "ODDSPAPI_API_KEY"
    const val ODDSPAPI_FREE = "ODDSPAPI_FREE_API_KEY"
    const val UNABATED = "UNABATED_API_KEY"
    const val SXBET = "SXBET_API_KEY"
    const val PINNWIRE = "PINNWIRE_API_KEY"
    const val PINNAPI = "PINNAPI_API_KEY"

    /** Pinnodds' REST board key (Tj, 2026-10-18: added the secret himself; "use pinnodds API, but without socket"). REST only: the engine never opens its socket. */
    const val PINNODDS = "PINNODDS_API_KEY"
    const val PROPLINE = "PROPLINE_API_KEY"
    const val ODDS_API = "ODDS_API_KEY"
    const val PARLAY = "PARLAY_API_KEY"

    /** BoltOdds Pro (Tj, 2026-10-15: "allow me to send my novig key and boltodds key to GitHub for rapid scanning"): the engine opens the odds sockets itself (the plan allows 3; the engine uses its two). */
    const val BOLT = "BOLTODDS_API_KEY"

    /**
     * The engine's own Novig key (Tj, 2026-10-15): a **`trading::read`** key, minted for the engine alone (so its rate bucket and stream are separate from the phone's) from the management key the phone holds, and sent
     * as these two secrets: the key's id and its PKCS#8 private key (a PEM, several lines: read with [rawEnv], never split). It cannot place an order or move money, and the engine only ever reads with it. Never the
     * trading key and never the management key ([CloudKeysTest] and `CloudNovigKey` hold that line).
     */
    const val NOVIG_KEY_ID = "NOVIG_READ_KEY_ID"
    const val NOVIG_KEY_PEM = "NOVIG_READ_KEY_PEM"
    val NOVIG = listOf(NOVIG_KEY_ID, NOVIG_KEY_PEM)

    /**
     * Kalshi's API key (Tj, 2026-10-16: "I want to send my kalshi key to GitHub so GitHub can use the socket for faster odds"): the Key ID and the private key PEM (several lines: read with [rawEnv]). The engine uses it
     * to read and to hold Kalshi's price socket and never to trade. An API key CAN trade, so this is the one trading-capable key besides the Novig subaccount's that Tj chose to put on GitHub.
     */
    const val KALSHI_KEY_ID = "KALSHI_KEY_ID"
    const val KALSHI_KEY_PEM = "KALSHI_KEY_PEM"
    val KALSHI = listOf(KALSHI_KEY_ID, KALSHI_KEY_PEM)

    /**
     * The Vigilant subaccount's `trading` key for the engine's orders (Tj, 2026-10-15, TCL20c): a software key made by [CloudTradeKey.swap] and sent by the phone only after Tj confirms. It can order, never move
     * money. The management key is NOT here and never goes to GitHub ([CloudKeysTest]).
     */
    const val TRADE_KEY_ID = "NOVIG_TRADE_KEY_ID"
    const val TRADE_KEY_PEM = "NOVIG_TRADE_KEY_PEM"
    val TRADE = listOf(TRADE_KEY_ID, TRADE_KEY_PEM)

    /** The secret holding each meter's keys (the dashboard needs the keys' order to say which one is active). A policy with no secret here has no key on GitHub. */
    fun envFor(policyId: String): String? = when (policyId) {
        "sgo" -> SGO; "oddspapi" -> ODDSPAPI; "oddspapi_free" -> ODDSPAPI_FREE; "unabated" -> UNABATED; "pinnwire" -> PINNWIRE; "pinnacle" -> PINNAPI; "pinnodds" -> PINNODDS
        "propline" -> PROPLINE; "oddsapi" -> ODDS_API; "parlay" -> PARLAY; "boltodds" -> BOLT; "sxbet" -> SXBET
        else -> null
    }

    val ALL = listOf(SGO, ODDSPAPI, ODDSPAPI_FREE, UNABATED, SXBET, PINNWIRE, PINNAPI, PINNODDS, PROPLINE, ODDS_API, PARLAY, BOLT)

    /**
     * Which of the phone's keys go to which repository secret (the app sends them: Settings › Cloud scan › Send my API keys to GitHub; Tj, 2026-10-14). **Pinnodds is never in it** (its socket allows one
     * connection and a second one evicts the app's), and neither is Kalshi's private key (it can trade; it is not an [com.tjshea.vigilant.data.keys.ApiProvider]). A new provider must be added here or left out on purpose
     * (`CloudKeysTest` fails until it is one or the other).
     */
    val BY_PROVIDER: Map<com.tjshea.vigilant.data.keys.ApiProvider, String> = mapOf(
        com.tjshea.vigilant.data.keys.ApiProvider.SPORTSGAMEODDS to SGO,
        com.tjshea.vigilant.data.keys.ApiProvider.ODDSPAPI to ODDSPAPI,
        com.tjshea.vigilant.data.keys.ApiProvider.ODDSPAPI_FREE to ODDSPAPI_FREE,
        com.tjshea.vigilant.data.keys.ApiProvider.UNABATED to UNABATED,
        com.tjshea.vigilant.data.keys.ApiProvider.PINNWIRE to PINNWIRE,
        com.tjshea.vigilant.data.keys.ApiProvider.PINNAPI to PINNAPI,
        com.tjshea.vigilant.data.keys.ApiProvider.PROPLINE to PROPLINE,
        com.tjshea.vigilant.data.keys.ApiProvider.THE_ODDS_API to ODDS_API,
        com.tjshea.vigilant.data.keys.ApiProvider.PARLAY to PARLAY,
        com.tjshea.vigilant.data.keys.ApiProvider.BOLTODDS to BOLT,
        com.tjshea.vigilant.data.keys.ApiProvider.SXBET to SXBET,
    )

    /** Secrets Tj added in GitHub himself, which the phone does not send (Pinnodds' REST key, 2026-10-18: "pinnodds api key is now added as a secret"). The engine reads them like the others. */
    val SET_BY_HAND = setOf(PINNODDS)

    /** Providers whose keys stay on the phone, on purpose: Pinnodds (one WebSocket per account: a second connection evicts the app's). */
    val PHONE_ONLY = setOf(com.tjshea.vigilant.data.keys.ApiProvider.PINNODDS)

    fun fromEnv(name: String): List<String> = System.getenv(name).orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    /** A secret's whole text (a PEM has line breaks and spaces that [fromEnv] would cut apart). */
    fun rawEnv(name: String): String = System.getenv(name).orEmpty().trim()
}
