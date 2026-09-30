package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Closing lines found after the start (Tj, 2026-09-30: "My phone will not always be on. The app has to be able to find clv from closing lines
 * after the games started or even days later"). ESPN's closing game lines and Novig's trade history, on payloads recorded from the real feeds
 * on 2026-09-30 (trimmed): the Chargers @ Bills and Eagles @ Bears games, and a slice of Novig's 2026-09-28 trades around Monday night's kickoff.
 */
class HistoricalClosesTest {

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    private val billsStart = Instant.parse("2026-09-27T17:00:00Z").toEpochMilli()
    private val bearsStart = Instant.parse("2026-09-29T00:15:00Z").toEpochMilli()
    private val bills = GameScore("401872953", "NFL", "Buffalo Bills", "Los Angeles Chargers", billsStart, final = true, called = false, homeScore = 27, awayScore = 20)

    private fun bet(
        id: String, market: String, selection: String, event: String = "Los Angeles Chargers @ Buffalo Bills", starts: Long = billsStart,
        outcomeId: String = "", betUrl: String? = null, league: String = "NFL",
    ) = TrackedBet(
        id, starts - 86_400_000L, league, event, starts, market, selection, "m", outcomeId, 0.5, 0.5, 0.52, 0.04, 10.0, betUrl = betUrl,
    )

    private fun pick(market: String, selection: String) = BetGrader.pickOf(market, selection)!!

    // ---- ESPN's closing lines ----------------------------------------------------------------------------------------

    @Test
    fun `ESPN's closing moneyline, devigged, for either side`() {
        val root = json.parseToJsonElement(res("espn-odds-chargers-bills.json"))
        // DraftKings closed BUF −345 / LAC +275.
        val h = 345.0 / 445.0
        val a = 100.0 / 375.0
        val buf = EspnCloses.parseClose(root, pick("Moneyline", "Buffalo Bills"), bills) as CloseLookup.Found
        assertEquals(h / (h + a), buf.fair, 1e-9)
        assertEquals("ESPN · DraftKings close", buf.via)
        val lac = EspnCloses.parseClose(root, pick("Moneyline", "Los Angeles Chargers"), bills) as CloseLookup.Found
        assertEquals(1.0, buf.fair + lac.fair, 1e-9)
    }

    @Test
    fun `a spread or total only at the line the book closed at`() {
        val root = json.parseToJsonElement(res("espn-odds-chargers-bills.json"))
        // BUF −7 at −115, LAC +7 at −105.
        val p = 115.0 / 215.0
        val q = 105.0 / 205.0
        val spread = EspnCloses.parseClose(root, pick("Spread", "Buffalo Bills -7"), bills) as CloseLookup.Found
        assertEquals(p / (p + q), spread.fair, 1e-9)
        val dog = EspnCloses.parseClose(root, pick("Point Spread", "Los Angeles Chargers +7"), bills) as CloseLookup.Found
        assertEquals(1.0, spread.fair + dog.fair, 1e-9)
        val moved = EspnCloses.parseClose(root, pick("Spread", "Buffalo Bills -6.5"), bills) as CloseLookup.None
        assertEquals("DraftKings closed at -7, not your -6.5", moved.reason)
        // Total 50.5 at −110 both ways: a coin flip.
        assertEquals(0.5, (EspnCloses.parseClose(root, pick("Total", "Over 50.5"), bills) as CloseLookup.Found).fair, 1e-9)
        assertTrue(EspnCloses.parseClose(root, pick("Total", "Under 49.5"), bills) is CloseLookup.None)
    }

    @Test
    fun `a live-odds provider is skipped, and props and halves aren't ESPN's`() {
        val root = json.parseToJsonElement(res("espn-odds-2025-two-providers.json"))
        val jets = GameScore("401772634", "NFL", "New York Jets", "Denver Broncos", 0, final = true, called = false, homeScore = 0, awayScore = 0)
        val found = EspnCloses.parseClose(root, pick("Moneyline", "Denver Broncos"), jets) as CloseLookup.Found
        assertEquals("ESPN · ESPN BET close", found.via)
        assertFalse(EspnCloses.gameLine(BetGrader.pickOf("Player Receptions", "Brock Bowers Over 4.5")))
        assertFalse(EspnCloses.gameLine(BetGrader.pickOf("1st Half Spread", "Buffalo Bills -3.5")))
        assertTrue(EspnCloses.gameLine(BetGrader.pickOf("Total", "Over 8.5")))
    }

    @Test
    fun `from the bet to ESPN's game and its close, one scoreboard and one odds read per game`() = runBlocking {
        val server = MockWebServer()
        val scoreboard = """{"events":[{"id":"401872953","date":"2026-09-27T17:00Z","competitions":[{"status":{"type":{"name":"STATUS_FINAL","completed":true,"state":"post"}},
            "competitors":[{"homeAway":"home","team":{"displayDisplayName":"x","displayName":"Buffalo Bills"},"score":"27"},{"homeAway":"away","team":{"displayName":"Los Angeles Chargers"},"score":"20"}]}]}]}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                return when {
                    path.startsWith("/site/football/nfl/scoreboard?dates=20260927") -> MockResponse().setBody(scoreboard)
                    path.startsWith("/site/") -> MockResponse().setBody("""{"events":[]}""")
                    path.startsWith("/core/football/leagues/nfl/events/401872953/competitions/401872953/odds") -> MockResponse().setBody(res("espn-odds-chargers-bills.json"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val espn = EspnCloses(http, json, server.url("/site").toString().trimEnd('/'), server.url("/core").toString().trimEnd('/'), gapMs = 0)
            val out = espn.closes(listOf(bet("ml", "Moneyline", "Buffalo Bills"), bet("tot", "Total", "Over 50.5"), bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5")))
            assertTrue(out["ml"] is CloseLookup.Found)
            assertEquals(0.5, (out["tot"] as CloseLookup.Found).fair, 1e-9)
            assertTrue(out["prop"] is CloseLookup.None)
            assertEquals(2, espn.requests)
        } finally {
            server.shutdown()
        }
    }

    // ---- Novig's trade history ---------------------------------------------------------------------------------------

    private val phi = "01a0aa74-3a32-75b2-bd49-def757268c4f"
    private val chi = "01a0aa74-3a32-75b2-bd49-dee69a477443"

    /** Serves index.json and the trades file with byte ranges, like data.novig.com. */
    private fun novigServer(file: ByteArray, dates: List<String> = listOf("2026-09-27", "2026-09-28")): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                return when {
                    path == "/index.json" -> MockResponse().setBody("""{"dates":${dates.joinToString(",", "[", "]") { "\"$it\"" }},"marketDates":[]}""")
                    path == "/2026-09-28/trades.csv" -> {
                        val (a, b) = request.getHeader("Range")!!.removePrefix("bytes=").split('-').map { it.toLong() }
                        val end = minOf(b, file.size - 1L)
                        MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $a-$end/${file.size}")
                            .setBody(Buffer().write(file.copyOfRange(a.toInt(), end.toInt() + 1)))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        return server
    }

    private fun novig(server: MockWebServer) = NovigTradeCloses(http, json, server.url("/").toString().trimEnd('/'))

    @Test
    fun `Novig's last half hour of trades before the start is the close, for the outcome on the bet or in its link`() = runBlocking {
        val server = novigServer(res("novig-trades-mnf.csv").toByteArray())
        try {
            val out = novig(server).closes(
                listOf(
                    bet("phi", "Moneyline", "Philadelphia Eagles", "Philadelphia Eagles @ Chicago Bears", bearsStart, outcomeId = phi),
                    bet("chi", "Moneyline", "Chicago Bears", "Philadelphia Eagles @ Chicago Bears", bearsStart, betUrl = "novigapp://events/$chi/cno"),
                    bet("none", "Moneyline", "X", "A @ B", bearsStart),
                ),
            )
            // Volume-weighted over the slice's trades from 23:45 to 00:15 (recomputed from the fixture when it was cut).
            val p = out["phi"] as CloseLookup.Found
            assertEquals(0.6313616457352901, p.fair, 1e-9)
            assertTrue(p.via.startsWith("Novig's last trades"))
            assertEquals(0.3674162851268143, (out["chi"] as CloseLookup.Found).fair, 1e-9)
            assertEquals("No Novig outcome on record for this bet", (out["none"] as CloseLookup.None).reason)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a day not published yet is asked again later, one before the history starts never, a quiet outcome has no close`() = runBlocking {
        val server = novigServer(res("novig-trades-mnf.csv").toByteArray())
        try {
            val out = novig(server).closes(
                listOf(
                    bet("today", "Moneyline", "X", "A @ B", Instant.parse("2026-09-30T17:00:00Z").toEpochMilli(), outcomeId = phi),
                    bet("old", "Moneyline", "X", "A @ B", Instant.parse("2026-07-01T17:00:00Z").toEpochMilli(), outcomeId = phi),
                    bet("quiet", "Moneyline", "X", "A @ B", Instant.parse("2026-09-28T16:00:00Z").toEpochMilli(), outcomeId = phi),
                ),
            )
            assertEquals("Novig publishes this day's trades the next morning", (out["today"] as CloseLookup.Later).reason)
            assertTrue(out["old"] is CloseLookup.None)
            assertEquals("No trades on Novig in the 30 minutes before the start", (out["quiet"] as CloseLookup.None).reason)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `only the window is downloaded from a day's big file`() = runBlocking {
        // 20,000 earlier trades ahead of the real slice: the half hour before the start is found by halving byte ranges.
        val real = res("novig-trades-mnf.csv")
        val header = real.substringBefore('\n') + "\n"
        val early = StringBuilder()
        val t0 = Instant.parse("2026-09-28T04:00:00Z").toEpochMilli()
        repeat(20_000) { i ->
            early.append(Instant.ofEpochMilli(t0 + i * 3_000L)).append(",01a00000-0000-0000-0000-000000000001,01a00000-0000-0000-0000-000000000002,Football Prop,NFL,RECEPTIONS,STRAIGHT,1,5.5,10,TAKER\n")
        }
        val file = (header + early + real.substringAfter('\n')).toByteArray()
        val server = novigServer(file)
        try {
            val closes = novig(server)
            val out = closes.closes(listOf(bet("phi", "Moneyline", "Philadelphia Eagles", "Philadelphia Eagles @ Chicago Bears", bearsStart, outcomeId = phi)))
            assertEquals(0.6313616457352901, (out["phi"] as CloseLookup.Found).fair, 1e-9)
            assertTrue("read ${closes.bytesRead} of ${file.size} bytes", closes.bytesRead < file.size / 3)
        } finally {
            server.shutdown()
        }
    }

    // ---- the back-fill -----------------------------------------------------------------------------------------------

    private class Fake(val answers: (TrackedBet) -> CloseLookup) : CloseSource {
        var asked = 0
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.size; return bets.associate { it.id to answers(it) } }
    }

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        val f = File.createTempFile("bets", ".json").also { it.delete() }
        f.writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(f)
    }

    @Test
    fun `ESPN first, then Novig, and the close counts for CLV even days later`() = runBlocking {
        val now = billsStart + 3 * 86_400_000L
        val t = tracker(bet("ml", "Moneyline", "Buffalo Bills"), bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5", outcomeId = phi))
        val espn = Fake { b -> if (b.id == "ml") CloseLookup.Found(0.74, "ESPN · DraftKings close") else CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only") }
        val novigSrc = Fake { CloseLookup.Found(0.55, "Novig's last trades (4)") }
        val r = CloseBackfill(t, listOf(espn, novigSrc), clock = { now }).run()
        assertEquals(2, r.found)
        assertEquals(1, novigSrc.asked) // only what ESPN didn't have
        val ml = t.all().first { it.id == "ml" }
        assertEquals(0.74, ml.closeFair!!, 0.0)
        assertTrue(ml.closeFinal)
        assertEquals(0.74 / 0.5 - 1, ClosingLine.clv(ml, now)!!, 1e-12)
        assertEquals("ESPN · DraftKings close", ClosingLine.closeOf(ml, now)!!.second)
        assertEquals(0.55, t.all().first { it.id == "prop" }.closeFair!!, 0.0)
        assertEquals(mapOf("ESPN" to 1, "Novig's last trades" to 1), r.bySource)
        // Found: never looked for again.
        assertEquals(0, CloseBackfill(t, listOf(espn, novigSrc), clock = { now + 86_400_000L }).run().looked)
    }

    @Test
    fun `not yet means again in 3 hours, never from every source means stop`() = runBlocking {
        val start = billsStart
        val t = tracker(bet("later", "Moneyline", "Buffalo Bills"), bet("never", "Player Receptions", "Dalton Kincaid Over 3.5"))
        val espn = Fake { b -> if (b.id == "later") CloseLookup.Later("ESPN didn't answer") else CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only") }
        val novigSrc = Fake { b -> if (b.id == "later") CloseLookup.Later("Novig publishes this day's trades the next morning") else CloseLookup.None("No Novig outcome on record for this bet") }
        val now = start + 60 * 60_000L
        CloseBackfill(t, listOf(espn, novigSrc), clock = { now }).run()
        val later = t.all().first { it.id == "later" }
        assertEquals("ESPN didn't answer; Novig publishes this day's trades the next morning", later.closeNote)
        assertFalse(later.closeFinal)
        assertTrue(t.all().first { it.id == "never" }.closeFinal)
        assertFalse(CloseBackfill.due(later, now + 60 * 60_000L))
        assertTrue(CloseBackfill.due(later, now + CloseBackfill.RETRY_MS))
        assertFalse(CloseBackfill.due(t.all().first { it.id == "never" }, now + CloseBackfill.RETRY_MS))
    }

    @Test
    fun `a close read just before the start wins, and nothing is looked for before the start or after 60 days`() {
        val captured = bet("c", "Moneyline", "Buffalo Bills").copy(closingFair = 0.6, closingSeenAtMs = billsStart - 5 * 60_000L, closeFair = 0.7, closeVia = "ESPN · DraftKings close")
        assertEquals(0.6 to ClosingLine.SOURCE_CAPTURED, ClosingLine.closeOf(captured, billsStart + 1))
        assertFalse(CloseBackfill.due(captured.copy(closeFair = null), billsStart + 3_600_000L))
        val plain = bet("p", "Moneyline", "Buffalo Bills")
        assertFalse(CloseBackfill.due(plain, billsStart + 60_000L)) // ESPN posts its close at the start: a few minutes after
        assertTrue(CloseBackfill.due(plain, billsStart + CloseBackfill.AFTER_START_MS))
        assertFalse(CloseBackfill.due(plain, billsStart + CloseBackfill.GIVE_UP_MS + 1))
        assertFalse(CloseBackfill.due(plain.copy(status = BetStatus.VOID), billsStart + 3_600_000L))
        // A history close stands in when nothing was read before the start.
        assertEquals(0.7, ClosingLine.closeFair(plain.copy(closeFair = 0.7, closeVia = "Novig's last trades (3)"), billsStart + 1)!!, 0.0)
        assertNull(ClosingLine.closeFair(plain.copy(closeFair = 0.7), billsStart - 1))
    }

    @Test
    fun `when a caller holds back the heavy source it waits, the light one still runs`() = runBlocking {
        val now = billsStart + 3_600_000L
        val t = tracker(bet("ml", "Moneyline", "Buffalo Bills"), bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5", outcomeId = phi))
        val espn = Fake { b -> if (b.id == "ml") CloseLookup.Found(0.74, "ESPN · DraftKings close") else CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only") }
        val heavy = object : CloseSource {
            var asked = 0
            override val heavy = true
            override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked++; return bets.associate { it.id to CloseLookup.Found(0.5, "Novig's last trades (1)") } }
        }
        val r = CloseBackfill(t, listOf(espn, heavy), clock = { now }).run(heavyOk = false)
        assertEquals(1, r.found)
        assertEquals(0, heavy.asked)
        val prop = t.all().first { it.id == "prop" }
        assertFalse(prop.closeFinal)
        assertTrue(prop.closeNote!!.endsWith("Novig's trade history wasn't read this time"))
        // On Wi-Fi, 3 hours later, it's found.
        CloseBackfill(t, listOf(espn, heavy), clock = { now + CloseBackfill.RETRY_MS }).run(heavyOk = true)
        assertEquals(0.5, t.all().first { it.id == "prop" }.closeFair!!, 0.0)
    }
}
