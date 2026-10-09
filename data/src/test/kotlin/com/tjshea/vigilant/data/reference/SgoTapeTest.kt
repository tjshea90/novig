package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.lab.gh.SgoCloseRow
import com.tjshea.vigilant.data.novig.lab.gh.SgoTape
import com.tjshea.vigilant.data.novig.lab.gh.SgoTick
import com.tjshea.vigilant.data.pinnodds.DayJournal
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The GitHub lab's SGO tape: what changed since the last read, how fresh each book's prices were, and a close written once. */
class SgoTapeTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private var body = ""
    private val urls = ArrayList<String>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { urls += request.requestUrl.toString(); return MockResponse().setResponseCode(200).setBody(body) } }
        server.start()
    }
    @After fun tearDown() { server.shutdown() }

    private fun event(odds: String, ts: String, started: Boolean = false, close: Boolean = false) = """
    {"success":true,"data":[{"eventID":"E1","leagueID":"NFL","teams":{"home":{"names":{"long":"Kansas City Chiefs"}},"away":{"names":{"long":"Las Vegas Raiders"}}},
     "status":{"startsAt":"2026-10-11T17:00:00.000Z","started":$started,"ended":false,"live":$started},
     "odds":{"points-home-game-ml-home":{"oddID":"points-home-game-ml-home","statID":"points","statEntityID":"home","periodID":"game","betTypeID":"ml","sideID":"home",
       "byBookmaker":{"pinnacle":{"odds":"$odds","available":true,"lastUpdatedAt":"$ts"${if (close) ",\"openOdds\":\"-140\",\"closeOdds\":\"-160\"" else ""}}}}}}]}"""

    @Test fun onlyChangesAreWrittenAndTheBooksFreshnessIsKept() = runTest {
        val clock = SgoParser.ms("2026-10-09T18:00:30.000Z")!!
        val meter = UsageMeter(JsonFileStore(tmp.newFile("u.json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clock })
        val client = SportsGameOddsClient(OkHttpClient(), KeyPool(QuotaPolicy.SGO, { listOf("k") }, meter), Json { ignoreUnknownKeys = true }, server.url("/v2").toString().trimEnd('/'), clock = { clock }, minIntervalMs = 0)
        val ticks = DayJournal(tmp.newFolder(), "sgo-tick", SgoTick.serializer()) { it.atMs }
        val closes = DayJournal(tmp.newFolder(), "sgo-close", SgoCloseRow.serializer()) { it.atMs }
        val tape = SgoTape(client, ticks, closes)
        body = event("-150", "2026-10-09T18:00:00.000Z")
        assertEquals(1, tape.cycle(listOf("NFL"), clock))
        assertEquals("the same price at the same update time is not written again", 0, tape.cycle(listOf("NFL"), clock + 30_000))
        body = event("-150", "2026-10-09T18:00:45.000Z")   // refreshed, price unchanged
        assertEquals(1, tape.cycle(listOf("NFL"), clock + 60_000))
        body = event("-155", "2026-10-09T18:01:20.000Z")
        assertEquals(1, tape.cycle(listOf("NFL"), clock + 90_000))
        assertEquals(3, ticks.readAll().size)
        val st = tape.books.getValue("pinnacle")
        assertEquals(4, st.reads); assertEquals(2, st.changes)   // first sight and the -155
        assertEquals("refreshes every 45 s then 35 s: 40 s on average", 80L, st.intervalSum)
        assertTrue(tape.report().any { it.contains("pinnacle") && it.contains("refreshes every 40s") })
        assertTrue("main-line moneyline asked without alternates", urls.none { "includeAltLines" in it } && urls.all { "oddID=" in it && "x-api-key" !in it })
    }

    @Test fun aCloseIsWrittenOncePerEventBookAndMarket() = runTest {
        val clock = SgoParser.ms("2026-10-11T18:00:00.000Z")!!
        val meter = UsageMeter(JsonFileStore(tmp.newFile("u2.json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clock })
        val client = SportsGameOddsClient(OkHttpClient(), KeyPool(QuotaPolicy.SGO, { listOf("k") }, meter), Json { ignoreUnknownKeys = true }, server.url("/v2").toString().trimEnd('/'), clock = { clock }, minIntervalMs = 0)
        val closes = DayJournal(tmp.newFolder(), "sgo-close", SgoCloseRow.serializer()) { it.atMs }
        val tape = SgoTape(client, DayJournal(tmp.newFolder(), "sgo-tick", SgoTick.serializer()) { it.atMs }, closes)
        body = event("-160", "2026-10-11T16:59:00.000Z", started = true, close = true)
        assertEquals(1, tape.closes(listOf("NFL"), clock))
        assertEquals(0, tape.closes(listOf("NFL"), clock))
        val row = closes.readAll().single()
        assertEquals(-160.0, row.closeOdds!!, 0.0); assertEquals(-140.0, row.openOdds!!, 0.0); assertEquals("pinnacle", row.book)
        assertTrue(urls.last().contains("includeOpenCloseOdds=true"))
        // a restart resumes the set from the journal
        val again = SgoTape(client, DayJournal(tmp.newFolder(), "t", SgoTick.serializer()) { it.atMs }, closes).also { it.resume(closes.readAll()) }
        assertEquals(0, again.closes(listOf("NFL"), clock))
    }
}
