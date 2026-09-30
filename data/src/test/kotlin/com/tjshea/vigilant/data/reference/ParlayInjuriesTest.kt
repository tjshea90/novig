package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Injury tags on prop bets (Tj, 2026-09-30, PARLAY_API.md §6.1): every `/props` row's `injury` report kept for free, ESPN's `/injuries`
 * list (1 credit a league) only for players no props answer covered, read at most every 10 minutes, and a tag only when a player isn't
 * active. Real answers: `parlay-props-with-injury.json`, `parlay-injuries-nfl.json`.
 */
class ParlayInjuriesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private var now = 1_790_000_000_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun client() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter),
        json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
    )

    @Test
    fun `ESPN's statuses become a tag only when a player may not play`() {
        assertNull(Injury.tagOf("Active"))
        assertEquals(InjuryLevel.NONE, Injury.levelOf("Active"))
        assertEquals("OUT", Injury.tagOf("Out"))
        assertEquals(InjuryLevel.RED, Injury.levelOf("Out"))
        assertEquals("IR", Injury.tagOf("Injured Reserve"))
        assertEquals(InjuryLevel.RED, Injury.levelOf("Injured Reserve"))
        assertEquals("IL", Injury.tagOf("10-Day IL"))
        assertEquals(InjuryLevel.RED, Injury.levelOf("60-Day-IL"))
        assertEquals("DOUBTFUL", Injury.tagOf("Doubtful"))
        assertEquals(InjuryLevel.AMBER, Injury.levelOf("Doubtful"))
        assertEquals("QUESTIONABLE", Injury.tagOf("Questionable"))
        assertEquals(InjuryLevel.AMBER, Injury.levelOf("Questionable"))
        assertEquals("DAY-TO-DAY", Injury.tagOf("Day-To-Day"))
        assertEquals(InjuryLevel.RED, Injury.levelOf("Suspension"))
        // "Outfielder" or "Without" isn't "Out".
        assertEquals(InjuryLevel.AMBER, Injury.levelOf("Probable"))
        assertFalse(Injury.levelOf("Throughout") == InjuryLevel.RED)
    }

    @Test
    fun `the real injuries answer reads every row, with the details a tap shows`() {
        val list = ParlayInjuries.parse(res("parlay-injuries-nfl.json"), json, "americanfootball_nfl")
        assertEquals(10, list.size)
        val caleb = list.first { it.player == "Caleb Williams" }
        assertEquals("Doubtful", caleb.status)
        assertEquals("CHI", caleb.teamAbbr)
        assertEquals("DOUBTFUL", caleb.tag)
        // "doubtful" as the whole note repeats the status: left out; the body part, the kind and the return date are what's new.
        assertEquals("Doubtful · Right Hamstring (Strain) · expected back 2026-10-04", caleb.details)
        assertEquals(2, list.count { it.level == InjuryLevel.RED && it.status == "Out" })
        assertEquals(2, list.count { it.status == "Injured Reserve" })
        assertTrue(list.first { it.player == "Caleb Williams" }.reportedAtMs!! > 0)
        // A row of another sport isn't this one's.
        assertTrue(ParlayInjuries.parse(res("parlay-injuries-nfl.json"), json, "baseball_mlb").isEmpty())
    }

    @Test
    fun `every props row carries its player's report, read for free with the props`() {
        val page = ParlayProps.parse(res("parlay-props-with-injury.json"), json, "americanfootball_nfl", now)
        assertEquals(3, page.injuries.size)
        val rodgers = page.injuries.first { it.player == "Aaron Rodgers" }
        assertEquals("Active", rodgers.status)
        assertEquals("PIT", rodgers.teamAbbr)
        assertNull(rodgers.tag)
        // A row whose report is null leaves its player listed as covered without one.
        val body = res("parlay-props-with-injury.json").replaceFirst(Regex("\"injury\": \\{[^}]*\\}"), "\"injury\": null")
        val p2 = ParlayProps.parse(body, json, "americanfootball_nfl", now)
        assertEquals(listOf("Aaron Rodgers"), p2.unreported)
    }

    @Test
    fun `a player is found by any spelling of his name, on his own team, and only while his report is fresh`() {
        val index = InjuryIndex { now }
        index.record(
            "americanfootball_nfl",
            listOf(
                Injury("C.J. Stroud", "Questionable", team = "Houston Texans", teamAbbr = "HOU", comment = "ankle"),
                Injury("Josh Allen", "Out", team = "Jacksonville Jaguars", teamAbbr = "JAX"),
                Injury("Josh Allen", "Active", team = "Buffalo Bills", teamAbbr = "BUF"),
            ),
        )
        val book = index.book.value
        assertEquals("Questionable", book.find("americanfootball_nfl", "CJ Stroud", now = now)!!.status)
        assertEquals("Questionable", book.find("americanfootball_nfl", "C.J. Stroud", listOf("Houston Texans", "Indianapolis Colts"), now)!!.status)
        // The same name on two teams: the game's teams say which.
        assertEquals("Out", book.find("americanfootball_nfl", "Josh Allen", listOf("Jacksonville Jaguars", "Tennessee Titans"), now)!!.status)
        assertEquals("Active", book.find("americanfootball_nfl", "Josh Allen", listOf("Miami Dolphins", "Buffalo Bills"), now)!!.status)
        // A report naming neither team is someone else's.
        assertNull(book.find("americanfootball_nfl", "CJ Stroud", listOf("Dallas Cowboys", "Miami Dolphins"), now))
        // Another sport's, or another player's: nothing.
        assertNull(book.find("baseball_mlb", "CJ Stroud", now = now))
        assertNull(book.find("americanfootball_nfl", "Nico Collins", now = now))
        // Six hours on, the report is too old to show.
        assertNull(book.find("americanfootball_nfl", "CJ Stroud", now = now + InjuryIndex.KEEP_MS + 1))
        // A newer answer that lists him with no report: the old one no longer holds.
        index.record("americanfootball_nfl", emptyList(), asked = listOf("CJ Stroud"))
        assertNull(index.book.value.find("americanfootball_nfl", "C.J. Stroud", now = now))
        assertTrue(index.book.value.covers("americanfootball_nfl", "C.J. Stroud", now))
    }

    @Test
    fun `a props scan fills the index, and a player it listed without a report needs no injuries read`() = runTest {
        server.enqueue(MockResponse().setBody(res("parlay-props-with-injury.json")))
        val index = InjuryIndex { now }
        val snap = ParlayPropsSource(client(), index).odds(Leagues.byNovigName("NFL")!!, ScanSettings())
        assertTrue(snap.events.isNotEmpty())
        val book = index.book.value
        assertTrue(book.covers("americanfootball_nfl", "Aaron Rodgers", now))
        assertTrue(book.covers("americanfootball_nfl", "Denzel Boston", now))
        assertFalse(book.covers("americanfootball_nfl", "Caleb Williams", now))
    }

    @Test
    fun `the injuries list is bought for uncovered players at most every ten minutes a sport, 1 credit, and marks who it didn't list`() = runTest {
        val index = InjuryIndex { now }
        val injuries = ParlayInjuries(client(), index, json, active = { true }, clock = { now })
        server.enqueue(MockResponse().setBody(res("parlay-injuries-nfl.json")).setHeader("x-requests-last", "1").setHeader("x-requests-remaining", "19000").setHeader("x-requests-used", "1000"))
        assertTrue(injuries.fill("americanfootball_nfl", listOf("Caleb Williams", "Nobody Listed")))
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/sports/americanfootball_nfl/injuries", url.encodedPath)
        val book = index.book.value
        assertEquals("DOUBTFUL", book.find("americanfootball_nfl", "Caleb Williams", listOf("Chicago Bears", "Detroit Lions"), now)!!.tag)
        // Asked about and not listed: nothing to report, and not asked about again for a while.
        assertTrue(book.covers("americanfootball_nfl", "Nobody Listed", now))
        assertEquals(19000, meter.flow.value.providers.getValue("parlay").keys.getValue("pk").remaining)
        // Within ten minutes: no second read, whoever is asked about.
        now += ParlayInjuries.REUSE_MS - 1
        assertFalse(injuries.fill("americanfootball_nfl", listOf("Someone Else")))
        assertEquals(1, server.requestCount)
        // A sport ParlayAPI doesn't list injuries for (it answers 400): never asked.
        assertFalse(injuries.fill("americanfootball_ncaaf", listOf("Someone")))
        // ParlayAPI off or no key: never asked.
        val off = ParlayInjuries(client(), index, json, active = { false }, clock = { now })
        assertFalse(off.fill("baseball_mlb", listOf("Someone")))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed injuries read waits its ten minutes too`() = runTest {
        val index = InjuryIndex { now }
        val injuries = ParlayInjuries(client(), index, json, active = { true }, clock = { now })
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertFalse(injuries.fill("baseball_mlb", listOf("Someone")))
        assertFalse(injuries.fill("baseball_mlb", listOf("Someone")))
        assertEquals(1, server.requestCount)
    }
}
