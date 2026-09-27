package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Odds API's game lines behind PropLine (RESEARCH.md §23; Tj, 2026-09-27: "If apis overlap odds
 * from the same sports books, use the best/fastest API first and the others as automatic
 * fallbacks"): its 3 credits a league are spent only for what PropLine couldn't give.
 */
class OddsApiFallbackTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var listed = "[]"
    private val hour = 3_600_000L
    private var now = 1_790_500_000_000L

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return if (request.requestUrl!!.encodedPath.endsWith("/events")) MockResponse().setBody(listed) else MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun client() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.ODDS_API, { listOf("test-key") }, meter), json,
        server.url("/v4").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0,
    )

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    private val nfl = Leagues.byNovigName("NFL")!!
    private val ravens = NovigEvent("nA", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", now + 3 * hour)
    private val bills = NovigEvent("nB", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Buffalo Bills @ New York Jets", now + 30 * hour)
    private val settings = ScanSettings(leagues = setOf("NFL"))

    private fun after(covered: Map<String, Set<String>>, answered: Set<String> = setOf("NFL")) =
        ScanContext(listOf(ravens, bills), emptyList(), now, covered, answered)

    private val bothCovered = mapOf("nA" to setOf("MONEYLINE:0", "SPREAD:0"), "nB" to setOf("TOTAL:0"))

    @Test
    fun `it backs up PropLine, and is asked for a league PropLine didn't answer`() = runTest {
        val c = client()
        assertEquals(PropLineClient.ID, c.fallbackFor)
        assertTrue(c.needed(nfl, settings, after(emptyMap(), answered = emptySet())))
        assertTrue(requests.isEmpty()) // decided without a call
    }

    @Test
    fun `it stands by when PropLine gave every Novig game's lines`() = runTest {
        assertFalse(client().needed(nfl, settings, after(bothCovered)))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a game PropLine lacks is checked on the free game list, and bought only if listed there`() = runTest {
        val c = client()
        val onlyRavens = mapOf("nA" to setOf("MONEYLINE:0"), "nB" to setOf("PROP:RECEPTIONS")) // Bills-Jets: props only
        listed = """[{"id":"x","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 50 * hour)}","home_team":"Detroit Lions","away_team":"Chicago Bears"}]"""
        assertFalse(c.needed(nfl, settings, after(onlyRavens)))
        assertEquals(1, requests.size)
        assertEquals("/v4/sports/americanfootball_nfl/events", requests.single().requestUrl!!.encodedPath) // free: no odds bought
        // The list is re-used for a few minutes; once it lists Bills-Jets, the league is worth its credits.
        listed = """[{"id":"b","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 30 * hour)}","home_team":"New York Jets","away_team":"Buffalo Bills"}]"""
        assertFalse(c.needed(nfl, settings, after(onlyRavens)))
        assertEquals(1, requests.size)
        now += 6 * 60_000L
        assertTrue(c.needed(nfl, settings.copy(), after(onlyRavens).copy(now = now)))
        assertEquals(2, requests.size)
    }

    @Test
    fun `a book picked as sharp that only The Odds API carries keeps it asked`() = runTest {
        val s = settings.copy(referenceBooks = settings.referenceBooks + "betfair_ex_eu", sharpBooks = settings.sharpBooks + "betfair_ex_eu")
        assertTrue(client().needed(nfl, s, after(bothCovered)))
        // Caesars (a soft book PropLine lacks) doesn't: it's in the defaults and would keep it asked every scan.
        assertTrue("williamhill_us" in settings.referenceBooks)
        assertFalse(client().needed(nfl, settings, after(bothCovered)))
    }

    @Test
    fun `PropLine's carried books are the ones its board can return`() {
        assertTrue(PropLineClient.carries("pinnacle") && PropLineClient.carries("draftkings") && PropLineClient.carries("hardrockbet"))
        assertFalse(PropLineClient.carries("williamhill_us") || PropLineClient.carries("espnbet") || PropLineClient.carries("betfair_ex_eu"))
        assertFalse(PropLineClient.carries("novig") || PropLineClient.carries("kalshi"))
    }

    @Test
    fun `if the free game list can't be read, it stands by instead of raising an error`() = runTest {
        server.shutdown() // nothing answers
        val onlyRavens = mapOf("nA" to setOf("MONEYLINE:0"))
        assertFalse(client().needed(nfl, settings, after(onlyRavens)))
    }
}
