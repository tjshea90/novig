package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.InjuryIndex
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
import com.tjshea.vigilant.data.reference.ParlayPeriodSource
import com.tjshea.vigilant.data.reference.ParlayPropsSource
import com.tjshea.vigilant.data.reference.ParlaySourceQuality
import com.tjshea.vigilant.data.reference.PinnapiClient
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * The fair-odds feeds, built for the GitHub engine from repository secrets (Tj, 2026-10-14: the scans run on GitHub's runners). It mirrors the phone's wiring (`AppContainer.allReferenceSources`) so
 * the cloud prices a bet from the same sources, in the same order, under the same on/off switches: **a feed that is switched off in the settings, or has no secret, is not in the list and is never
 * called** (Tj, 2026-10-11).
 *
 * Left out on purpose, and why:
 *  - **Pinnodds** (one WebSocket per account, and a second connection evicts the app's; Tj, 2026-10-09) and **BoltOdds** (three sockets, the app's own two plus the Test's probe are the budget): both stay on the phone.
 *  - **Kalshi's signed key** (an API key can trade; it is never placed in a repository secret): the cloud reads Kalshi's public markets unsigned.
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

    private val polymarket = PolymarketClient(http, json, usage = usage)
    private val kalshi = KalshiClient(http, json, altBaseUrl = KalshiClient.ALT_URL, usage = usage)
    private val oddsApi = TheOddsApiClient(http, KeyPool(QuotaPolicy.ODDS_API, { keys(CloudKeys.ODDS_API) }, usage), json)
    private val bookProps = OddsApiPropsSource(oddsApi)
    private val parlayPool = KeyPool(QuotaPolicy.PARLAY, { keys(CloudKeys.PARLAY) }, usage)
    private val parlayQuality = ParlaySourceQuality(http, json)
    private val parlayOdds = TheOddsApiClient(http, parlayPool, json, baseUrl = OddsFeed.PARLAY.base, feed = OddsFeed.PARLAY, quality = parlayQuality)
    private val parlayProps = ParlayPropsSource(parlayOdds, injuries)
    private val parlayHalves = ParlayPeriodSource(parlayOdds, json)
    private val pinnacle = PinnapiClient(
        http, json,
        listOf(
            PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { keys(CloudKeys.PINNWIRE) }, usage)),
            PinnapiClient.pinnapi(KeyPool(QuotaPolicy.PINNAPI, { keys(CloudKeys.PINNAPI) }, usage)),
        ),
    )
    private val propLine = PropLineClient(http, KeyPool(QuotaPolicy.PROPLINE, { keys(CloudKeys.PROPLINE) }, usage), json, relayNovig = true, bookIds = false)
    private val propLineProps = PropLinePropsSource(propLine)

    private fun has(name: String) = keys(name).isNotEmpty()

    fun sgoActive(s: ScanSettings) = s.sgoPro && has(CloudKeys.SGO) && !sgoClient.down()
    fun opActive(s: ScanSettings) = s.oddsPapi && has(CloudKeys.ODDSPAPI) && !opClient.down()
    fun unabatedActive(s: ScanSettings) = s.unabated && has(CloudKeys.UNABATED) && !unabatedClient.down()
    fun op4Active(s: ScanSettings) = s.oddsPapiFree && s.gates.op4Fair > 0 && has(CloudKeys.ODDSPAPI_FREE)

    private fun propsOf(s: ScanSettings, props: ReferenceSource): List<ReferenceSource> = if (MarketFamily.PLAYER_PROPS in s.families) listOf(props) else emptyList()

    /** The flat-rate feeds that are on, SGO first, Unabated last; then the rest. Same order as the phone's list. */
    fun sources(s: ScanSettings): List<ReferenceSource> {
        val front = buildList {
            if (sgoActive(s)) { add(sgoGames); addAll(propsOf(s, sgoProps)) }
            if (opActive(s)) { add(opGames); addAll(propsOf(s, opProps)) }
            if (op4Active(s)) { add(op4Games); addAll(propsOf(s, op4Props)) }
        }
        val last = buildList { if (unabatedActive(s)) { add(unabatedGames); addAll(propsOf(s, unabatedProps)) } }
        return front + base(s) + last
    }

    /** What is in [sources], by name, for the status file and the logs (never a key). */
    fun names(s: ScanSettings): List<String> = sources(s).map { it.id }

    private fun base(s: ScanSettings): List<ReferenceSource> = buildList {
        val pinnacleOn = s.usePinnacle && ((s.gates.usePinnwire && has(CloudKeys.PINNWIRE)) || (s.gates.usePinnapi && has(CloudKeys.PINNAPI)))
        if (pinnacleOn) add(pinnacle)
        // ParlayAPI's alternate lines are Pinnacle's: bought only when PinnWire/pinnapi aren't sending them (as on the phone).
        parlayOdds.alternates = !pinnacleOn
        parlayHalves.pinnacleFeedOn = pinnacleOn
        if (s.usePolymarket) add(polymarket)
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
    const val PINNWIRE = "PINNWIRE_API_KEY"
    const val PINNAPI = "PINNAPI_API_KEY"
    const val PROPLINE = "PROPLINE_API_KEY"
    const val ODDS_API = "ODDS_API_KEY"
    const val PARLAY = "PARLAY_API_KEY"

    val ALL = listOf(SGO, ODDSPAPI, ODDSPAPI_FREE, UNABATED, PINNWIRE, PINNAPI, PROPLINE, ODDS_API, PARLAY)

    fun fromEnv(name: String): List<String> = System.getenv(name).orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
}
