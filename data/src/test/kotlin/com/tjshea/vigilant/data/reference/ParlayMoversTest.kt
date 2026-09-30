package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.runBlocking
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
import java.time.Instant

/**
 * Pinnacle's biggest moneyline moves (Tj, 2026-09-30, PARLAY_API.md §6.3): ParlayAPI's public `/v1/meta/movers` read (free, 90 s apart at
 * most), and a team bet whose game moved told whether Pinnacle moved toward its side or against it. Real answer: `parlay-movers-nfl.json`.
 */
class ParlayMoversTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var now = Instant.parse("2026-09-30T05:10:40Z").toEpochMilli()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private fun board() = ParlayMovers.parse(res("parlay-movers-nfl.json"), json, "americanfootball_nfl", now)!!

    private val broncosStart = Instant.parse("2026-10-04T20:25:00Z").toEpochMilli()

    @Test
    fun `the real answer reads each game's move, biggest first`() {
        val b = board()
        assertEquals(360, b.windowMinutes)
        assertEquals(2, b.movers.size)
        val sf = b.movers.first()
        assertEquals("San Francisco 49ers", sf.home)
        assertEquals("Denver Broncos", sf.away)
        assertEquals(-150, sf.homeFirst)
        assertEquals(-142, sf.homeLast)
        assertEquals(1.36, sf.awayPp, 1e-9)
        assertFalse(sf.steamHome) // the money went to Denver
        assertEquals(broncosStart, sf.commenceMs)
        assertNull(ParlayMovers.parse("""{"error":"x"}""", json, "americanfootball_nfl", now))
    }

    @Test
    fun `a team bet's side is told apart, by full name or abbreviation, and a game that isn't on the board gets nothing`() {
        val b = board()
        val game = "Denver Broncos @ San Francisco 49ers"
        val den = LineMoves.moveFor(b, game, broncosStart, "Denver Broncos")!!
        assertTrue(den.toward)
        assertEquals(130, den.first)
        assertEquals(123, den.last)
        val sf = LineMoves.moveFor(b, game, broncosStart, "SF -2.5")!!
        assertFalse(sf.toward)
        assertEquals(-1.32, sf.pp, 1e-9)
        // Another week's meeting of the same teams: not this move.
        assertNull(LineMoves.moveFor(b, game, broncosStart + 7 * 86_400_000L, "Denver Broncos"))
        // A game not on the board, or one sharing a city only.
        assertNull(LineMoves.moveFor(b, "Denver Broncos @ Los Angeles Rams", broncosStart, "Denver Broncos"))
    }

    @Test
    fun `only team bets get a note, CNO's moneylines and spreads and open Tracker bets, never totals or players`() {
        val b = mapOf("americanfootball_nfl" to board())
        val start = Instant.parse("2026-10-02T00:15:00Z").toEpochMilli()
        val game = "Pittsburgh Steelers @ Cleveland Browns"
        fun row(market: String, bet: String) = CnoRow(0.03, start, "Football", "NFL", game, market, bet, 120, null, "Novig", gameUrl = "https://x/?side_id=$bet")
        val rows = listOf(row("Moneyline", "Cleveland Browns"), row("Point Spread", "Pittsburgh Steelers -2.5"), row("Total Points", "Over 41.5"), row("Player Receptions", "Jerry Jeudy Over 4.5"))
        fun bet(id: String, market: String, sel: String, status: BetStatus = BetStatus.PENDING) = TrackedBet(
            id, now, "NFL", game, start, market, sel, "m", "o", 0.45, 0.45, 0.47, 0.04, 10.0, status = status,
        )
        val bets = listOf(bet("a", "Moneyline", "Pittsburgh Steelers"), bet("b", "Total", "Over 41.5"), bet("c", "Moneyline", "Cleveland Browns", BetStatus.WON))
        val notes = LineMoves.notes(b, emptyList(), rows, bets, now)
        assertEquals(false, notes[InjuryTags.cnoKey(rows[0])]?.toward) // Browns' chance fell 1.15 pts
        assertEquals(true, notes[InjuryTags.cnoKey(rows[1])]?.toward)
        assertNull(notes[InjuryTags.cnoKey(rows[2])])
        assertNull(notes[InjuryTags.cnoKey(rows[3])])
        assertEquals(1.17, notes["bet:a"]!!.pp, 1e-9)
        assertNull(notes["bet:b"])
        assertNull(notes["bet:c"])
        // No boards: nothing.
        assertTrue(LineMoves.notes(emptyMap(), emptyList(), rows, bets, now).isEmpty())
    }

    @Test
    fun `boards are read without a key for the leagues asked, at most every 90 seconds, and dropped when a league is unpicked`() = runBlocking<Unit> {
        val movers = ParlayMovers(OkHttpClient(), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
        server.enqueue(MockResponse().setBody(res("parlay-movers-nfl.json")))
        movers.refresh(listOf("americanfootball_nfl"))
        val req = server.takeRequest()
        assertEquals("/v1/meta/movers", req.requestUrl!!.encodedPath)
        assertEquals("americanfootball_nfl", req.requestUrl!!.queryParameter("sport_key"))
        assertEquals("360", req.requestUrl!!.queryParameter("window_minutes"))
        assertEquals("true", req.requestUrl!!.queryParameter("pre_game_only"))
        assertNull(req.getHeader("X-API-Key"))
        assertEquals(2, movers.boards.value.getValue("americanfootball_nfl").movers.size)
        now += 60_000
        movers.refresh(listOf("americanfootball_nfl"))
        assertEquals(1, server.requestCount)
        movers.refresh(emptyList())
        assertTrue(movers.boards.value.isEmpty())
    }
}
