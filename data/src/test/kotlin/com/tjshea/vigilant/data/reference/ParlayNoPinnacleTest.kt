package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.ParlayCloses
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PA1 (Tj, 2026-11-14, from ParlayAPI's e-mail): ParlayAPI stopped serving Pinnacle on 2026-11-09, and a request that names ONLY `pinnacle` is refused (HTTP 400 `UNKNOWN_BOOKMAKER`; it was 117 closing-lines calls
 * a run that failed). No request to ParlayAPI may name it: every list that reaches the API, every client call that names books, every close it is asked for. [ParlayWithdrawn] is the one list of what is gone.
 */
class ParlayNoPinnacleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun meter() = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun client(base: String, feed: OddsFeed = OddsFeed.PARLAY) =
        TheOddsApiClient(OkHttpClient(), KeyPool(if (feed == OddsFeed.PARLAY) QuotaPolicy.PARLAY else QuotaPolicy.ODDS_API, { listOf("pk") }, meter()), json, baseUrl = base, clock = { 42L }, minIntervalMs = 0, feed = feed)

    private fun serve(seen: MutableList<RecordedRequest>): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return if (request.path!!.contains("/events/")) MockResponse().setResponseCode(404) else MockResponse().setBody("[]")
            }
        }
        start()
    }

    private fun named(r: RecordedRequest): List<String> =
        listOf("bookmakers", "books", "source", "sharpBook").flatMap { k -> r.requestUrl!!.queryParameter(k)?.split(',').orEmpty() }.map { it.trim().lowercase() }

    @Test
    fun `no list that reaches ParlayAPI names a withdrawn book`() {
        assertTrue(ParlayWithdrawn.isWithdrawn("pinnacle") && ParlayWithdrawn.isWithdrawn(" Pinnacle "))
        assertFalse(ParlayWithdrawn.isWithdrawn("pinnacle_delayed"))
        assertFalse(ParlayWithdrawn.isWithdrawn("draftkings"))
        assertEquals(listOf("draftkings", "fanduel"), ParlayWithdrawn.without(listOf("pinnacle", "draftkings", "PINNACLE", "fanduel")))
        assertTrue("the scan's game-line books", OddsFeed.PARLAY.books!!.none { ParlayWithdrawn.isWithdrawn(it) })
        assertTrue("the props books", ParlayProps.BOOKS.none { ParlayWithdrawn.isWithdrawn(it) })
        assertTrue("the closes' books", ParlayCloses.BOOKS.none { ParlayWithdrawn.isWithdrawn(it) })
        assertEquals(listOf("draftkings", "fanduel"), ParlayCloses.BOOKS)
        // A book ParlayAPI's own directory says serves no data is not asked either (bookmaker_eu, 2026-11-14).
        assertFalse("bookmaker_eu" in OddsFeed.PARLAY.books!!)
        // Low-usage bids: Pinnacle is carried by PinnWire / pinnapi and PropLine, no longer by ParlayAPI, and no pick makes ParlayAPI's filter name it.
        val pinnacle = LowUsageBids.BOOKS.first { it.key == "pinnacle" }
        assertFalse(LowUsageBids.FEED_PARLAY in pinnacle.feeds)
        assertEquals(null, pinnacle.parlayKey)
        assertTrue(LowUsageBids.BOOKS.none { it.parlayKey?.let(ParlayWithdrawn::isWithdrawn) == true })
        assertTrue(LowUsageBids.parlayBooks(ScanSettings(lowUsageBooks = setOf("pinnacle", "fanduel", "prophetx"))).none { ParlayWithdrawn.isWithdrawn(it) })
    }

    @Test
    fun `the client drops a withdrawn book from every call that names books, and asks for nothing when nothing is left`() = runBlocking {
        val seen = ArrayList<RecordedRequest>()
        val server = serve(seen)
        try {
            val c = client(server.url("/v1").toString().trimEnd('/'))
            c.fetch("americanfootball_nfl", listOf("pinnacle", "draftkings", "fanduel"))
            c.eventOdds("americanfootball_nfl", "e1", listOf("pinnacle", "draftkings"), listOf("player_pass_yds"))
            c.eventOddsRaw("americanfootball_nfl", "e1", listOf("Pinnacle", "fanduel"), listOf("player_pass_yds"))
            c.bulkProps("americanfootball_nfl", listOf("player_pass_yds"), listOf("pinnacle", "draftkings"))
            assertEquals(4, seen.size)
            assertEquals(listOf("draftkings,fanduel", "draftkings", "fanduel", "draftkings"), seen.map { it.requestUrl!!.queryParameter("bookmakers") })
            assertTrue(seen.none { "pinnacle" in named(it) })
            // Only a withdrawn book named: no call (the old refusal was a whole failed call).
            val none = runCatching { c.fetch("americanfootball_nfl", listOf("pinnacle")) }.exceptionOrNull()
            assertTrue(none.toString(), none is IllegalArgumentException)
            assertEquals(4, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the scan's own game lines and props never name Pinnacle, and The Odds API still may`() = runBlocking {
        val seen = ArrayList<RecordedRequest>()
        val server = serve(seen)
        try {
            val base = server.url("/v1").toString().trimEnd('/')
            val nfl = Leagues.byNovigName("NFL")!!
            val parlay = client(base)
            parlay.odds(nfl, ScanSettings())
            ParlayPropsSource(parlay).odds(nfl, ScanSettings())
            // Low-usage bids ask the picked books' props alone: Pinnacle among them is dropped, not named.
            ParlayPropsSource(parlay).odds(nfl, ScanSettings(lowUsageScan = true, lowUsageBooks = setOf("pinnacle", "fanduel", "prophetx")))
            assertTrue(seen.size >= 3)
            assertTrue(seen.joinToString { it.path!! }, seen.none { "pinnacle" in named(it) })
            // No alternates are bought any more (PA1: they were Pinnacle's, and a probe found no other book sending them): 3 credits a league, not 5.
            val odds = seen.first { it.path!!.contains("/odds") }
            assertEquals("h2h,spreads,totals", odds.requestUrl!!.queryParameter("markets"))
            // The Odds API itself serves Pinnacle, and keeps being asked for it.
            seen.clear()
            client(base, OddsFeed.ODDS_API).fetch("americanfootball_nfl", listOf("pinnacle", "draftkings"))
            assertEquals("pinnacle,draftkings", seen.single().requestUrl!!.queryParameter("bookmakers"))
        } finally {
            server.shutdown()
        }
    }
}
