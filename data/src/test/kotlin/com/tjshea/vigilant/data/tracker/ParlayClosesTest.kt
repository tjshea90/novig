package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.CreditsHeldBackException
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * ParlayAPI's closing moneylines (Tj, 2026-09-30, RESEARCH.md §43; PA1, 2026-11-14): Pinnacle's until ParlayAPI stopped serving Pinnacle on 2026-11-09 (its e-mail: "closing-lines requests that name pinnacle are refused with
 * 400 UNKNOWN_BOOKMAKER ... closing lines are served for other books"), DraftKings' and FanDuel's since. The rows below are ParlayAPI's real answers of 2026-11-14 (GitHub probe, parlay.yml `closeprobe`):
 * `parlay-closing-lines-2026-11-14.json`, flat rows, one per game and book, told apart by `bookmaker`.
 */
class ParlayClosesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun meter() = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun pool(vararg keys: String, meter: UsageMeter = meter()) = KeyPool(QuotaPolicy.PARLAY, { keys.toList() }, meter)

    private fun p(american: Int) = if (american < 0) -american / (-american + 100.0) else 100.0 / (american + 100.0)

    private fun pick(market: String, selection: String) = BetGrader.pickOf(market, selection)!!

    private fun real(): String = javaClass.classLoader!!.getResource("parlay-closing-lines-2026-11-14.json")!!.readText()

    // The real games: Washington Commanders @ New York Giants (NFL, 2026-11-13 01:16Z; DraftKings -122/+102, FanDuel -128/+100) and
    // Portland Trail Blazers @ Sacramento Kings (NBA, 2026-11-14 03:14Z; DraftKings +361/-468, FanDuel +285/-400).
    private val giantsStart = Instant.parse("2026-11-13T01:16:00Z").toEpochMilli()
    private val kingsStart = Instant.parse("2026-11-14T03:14:00Z").toEpochMilli()

    private fun giants(id: String, selection: String = "New York Giants", market: String = "Moneyline") = TrackedBet(
        id, giantsStart - 86_400_000L, "NFL", "Washington Commanders @ New York Giants", giantsStart, market, selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
    )

    private fun kings(id: String, selection: String = "Sacramento Kings") = TrackedBet(
        id, kingsStart - 86_400_000L, "NBA", "Portland Trail Blazers @ Sacramento Kings", kingsStart, "Moneyline", selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
    )

    // ---- parsing: each book's own two sides, devigged, labelled -------------------------------------------------------

    @Test
    fun `a game's close is the asked book's two sides devigged, labelled by the book, and the other book's row is never mixed in`() {
        val root = json.parseToJsonElement(real())
        val dk = ParlayCloses.parseGameLine(root, kings("k"), pick("Moneyline", "Sacramento Kings"), "draftkings") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(361), p(-468)), dk.fair, 1e-9)
        assertEquals("ParlayAPI · DraftKings close", dk.via)
        val fd = ParlayCloses.parseGameLine(root, kings("k"), pick("Moneyline", "Sacramento Kings"), "fanduel") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(285), p(-400)), fd.fair, 1e-9)
        assertEquals("ParlayAPI · FanDuel close", fd.via)
        // The other side of the same game, and the two sides sum to about one (the conservative devig).
        val dog = ParlayCloses.parseGameLine(root, kings("d", "Portland Trail Blazers"), pick("Moneyline", "Portland Trail Blazers"), "draftkings") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(-468), p(361)), dog.fair, 1e-9)
        assertTrue("conservative: the two sides sum to just under 1", dk.fair + dog.fair in 0.97..1.0000001)
        // A game one book has not closed: that book says so by name.
        val spurs = TrackedBet("s", kingsStart, "NBA", "Golden State Warriors @ San Antonio Spurs", Instant.parse("2026-11-14T02:46:00Z").toEpochMilli(), "Moneyline", "San Antonio Spurs", "m", "", 0.5, 0.5, 0.52, 0.04, 10.0)
        assertTrue(ParlayCloses.parseGameLine(root, spurs, pick("Moneyline", "San Antonio Spurs"), "draftkings") is CloseLookup.Found)
        val noFd = ParlayCloses.parseGameLine(root, spurs, pick("Moneyline", "San Antonio Spurs"), "fanduel") as CloseLookup.None
        assertEquals("FanDuel has no close for this game in ParlayAPI's closing lines", noFd.reason)
    }

    @Test
    fun `two books that closed are averaged and named, one book is that book alone, none is a never naming why`() {
        val root = json.parseToJsonElement(real())
        val both = ParlayCloses.combine(ParlayCloses.BOOKS.map { it to ParlayCloses.parseGameLine(root, giants("g"), pick("Moneyline", "New York Giants"), it) }) as CloseLookup.Found
        val dk = CloseDevig.fair(p(-122), p(102))
        val fd = CloseDevig.fair(p(-128), p(100))
        assertEquals((dk + fd) / 2, both.fair, 1e-9)
        assertEquals("ParlayAPI · DraftKings + FanDuel close", both.via)
        val one = ParlayCloses.combine(listOf("draftkings" to CloseLookup.Found(0.55, "x"), "fanduel" to CloseLookup.None("FanDuel has no close"))) as CloseLookup.Found
        assertEquals(0.55, one.fair, 1e-12)
        assertEquals("ParlayAPI · DraftKings close", one.via)
        val never = ParlayCloses.combine(listOf("draftkings" to CloseLookup.None("same reason"), "fanduel" to CloseLookup.None("same reason"))) as CloseLookup.None
        assertEquals("same reason", never.reason)
        // A look that may still come (nothing answered) is not a never.
        assertTrue(ParlayCloses.combine(listOf("draftkings" to CloseLookup.Later("later"), "fanduel" to CloseLookup.None("no"))) is CloseLookup.Later)
        assertEquals("ParlayAPI close", ParlayCloses.via(emptyList()))
    }

    @Test
    fun `a three-way close with a draw, a spread, a total and a prop are never Novig's two-way moneyline`() {
        val soccer = """[{"sport_key":"soccer_epl","home_team":"Arsenal","away_team":"Chelsea","bookmaker":"draftkings","home_odds":120,"away_odds":230,"draw_odds":250,"market_key":"h2h","commence_time":"2026-11-13T19:00:00Z"}]"""
        val bet = TrackedBet("x", giantsStart, "EPL", "Chelsea @ Arsenal", Instant.parse("2026-11-13T19:00:00Z").toEpochMilli(), "Moneyline", "Arsenal", "m", "", 0.5, 0.5, 0.52, 0.04, 10.0)
        val three = ParlayCloses.parseGameLine(json.parseToJsonElement(soccer), bet, pick("Moneyline", "Arsenal"), "draftkings") as CloseLookup.None
        assertTrue(three.reason, three.reason.contains("three-way"))
        val root = json.parseToJsonElement(real())
        val spread = ParlayCloses.parseGameLine(root, giants("s"), pick("Spread", "New York Giants -2.5"), "draftkings") as CloseLookup.None
        assertEquals(ParlayCloses.ONLY_MONEYLINES, spread.reason)
        assertEquals(ParlayCloses.ONLY_MONEYLINES, (ParlayCloses.parseGameLine(root, giants("t"), pick("Total", "Under 44.5"), "draftkings") as CloseLookup.None).reason)
    }

    @Test
    fun `a row that names no book is taken as the asked book's, a row of another book is not`() {
        val named = """{"sport_key":"basketball_nba","home_team":"Sacramento Kings","away_team":"Portland Trail Blazers","bookmaker":"fanduel","home_odds":285,"away_odds":-400,"draw_odds":null,"market_key":"h2h","commence_time":"2026-11-14T03:14:00Z"}"""
        val bare = named.replace(""""bookmaker":"fanduel",""", "")
        val bet = kings("k")
        assertTrue(ParlayCloses.parseGameLine(json.parseToJsonElement("[$bare]"), bet, pick("Moneyline", "Sacramento Kings"), "draftkings") is CloseLookup.Found)
        assertTrue(ParlayCloses.parseGameLine(json.parseToJsonElement("[$named]"), bet, pick("Moneyline", "Sacramento Kings"), "draftkings") is CloseLookup.None)
        assertTrue(ParlayCloses.parseGameLine(json.parseToJsonElement("[$named]"), bet, pick("Moneyline", "Sacramento Kings")) is CloseLookup.Found)
    }

    // ---- one game, not another that shares a word (Tj's scan-study file, 2026-10-03: a Washington State moneyline of -117 "closed" at +272) ----------

    /**
     * Tj's v0.58.3 scan-study file: "Fresno State @ Washington State", Washington State -117 (-115 at its last look, fair 54%), got a close of +272 (27%, CLV -50%). Names share the school word "State" (the matcher's
     * token for it is `st`), and a name pair that shares only that word scores 0.5, the bar the closes code asked: any other "X State @ Y State" game starting within three hours passed, and the LATEST such row won,
     * not the best fit.
     */
    private val sat = Instant.parse("2026-10-03T23:30:00Z").toEpochMilli()

    private fun wsu(id: String, selection: String = "Washington State", market: String = "Moneyline") = TrackedBet(
        id, sat - 150 * 60_000L, "NCAAF", "Fresno State @ Washington State", sat, market, selection, "m", "", 0.54, 0.54, 0.54, 0.0007, 1.0,
    )

    private fun flat(home: String, away: String, homeOdds: Int, awayOdds: Int, start: String = "2026-10-03T23:30:00Z", update: String = start, book: String = "draftkings") =
        """{"sport_key":"americanfootball_ncaaf","home_team":"$home","away_team":"$away","bookmaker":"$book","home_odds":$homeOdds,"away_odds":$awayOdds,"draw_odds":null,"market_key":"h2h","commence_time":"$start","last_update":"$update"}"""

    @Test
    fun `a moneyline close comes from the game with the best fit, never from another game that shares only the word State`() {
        val truth = flat("Washington State Cougars", "Fresno State Bulldogs", -115, -105)
        // Another game 15 minutes later between two other State teams: it shares "State" with both of ours, and its row is the later one.
        val other = flat("Oregon State Beavers", "Idaho State Bengals", 272, -340, start = "2026-10-03T23:45:00Z")
        val both = json.parseToJsonElement("[$other,$truth]")
        val found = ParlayCloses.parseGameLine(both, wsu("ml"), pick("Moneyline", "Washington State"), "draftkings") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(-115), p(-105)), found.fair, 1e-9)
        val away = ParlayCloses.parseGameLine(both, wsu("a", "Fresno State"), pick("Moneyline", "Fresno State"), "draftkings") as CloseLookup.Found
        assertTrue("conservative: the two sides sum to just under 1", found.fair + away.fair in 0.97..1.0000001)
        // The real game missing from the file: no close at all beats the other game's.
        val none = ParlayCloses.parseGameLine(json.parseToJsonElement("[$other]"), wsu("ml"), pick("Moneyline", "Washington State"), "draftkings")
        assertTrue(none.toString(), none is CloseLookup.None)
    }

    @Test
    fun `two games that fit equally well are not guessed between, and a doubleheader's own game is the nearest start`() {
        // Same two teams twice (a doubleheader, 3 hours apart): the bet's own game by its start.
        val first = flat("Washington State Cougars", "Fresno State Bulldogs", -115, -105, start = "2026-10-03T20:30:00Z")
        val second = flat("Washington State Cougars", "Fresno State Bulldogs", 150, -170, start = "2026-10-03T23:30:00Z")
        val dh = ParlayCloses.parseGameLine(json.parseToJsonElement("[$first,$second]"), wsu("ml"), pick("Moneyline", "Washington State"), "draftkings") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(150), p(-170)), dh.fair, 1e-9)
        // And the first game's own bet, whichever row came first or was updated last: its own start's game, not the later one.
        val early = wsu("early").copy(startsTs = Instant.parse("2026-10-03T20:30:00Z").toEpochMilli())
        val dhEarly = ParlayCloses.parseGameLine(json.parseToJsonElement("[$second,$first]"), early, pick("Moneyline", "Washington State"), "draftkings") as CloseLookup.Found
        assertEquals(CloseDevig.fair(p(-115), p(-105)), dhEarly.fair, 1e-9)
        // Two DIFFERENT pairings each as good a fit as the other (each shares one team exactly and a State word with the other): refused, not the later one.
        val a = flat("Oregon State Beavers", "Fresno State Bulldogs", 200, -240, start = "2026-10-03T23:40:00Z")
        val b = flat("Washington State Cougars", "Boise State Broncos", -115, -105)
        val clash = ParlayCloses.parseGameLine(json.parseToJsonElement("[$a,$b]"), wsu("ml"), pick("Moneyline", "Washington State"), "draftkings")
        assertTrue(clash.toString(), clash is CloseLookup.None)
    }

    @Test
    fun `the Odds API sport key of a bet's league`() {
        assertEquals("americanfootball_nfl", ParlayCloses.sportKeyOf(giants("a")))
        assertEquals(null, ParlayCloses.sportKeyOf(giants("b").copy(league = "Nowhere League")))
    }

    // ---- over HTTP ----------------------------------------------------------------------------------------------------

    private fun serve(handler: (RecordedRequest) -> MockResponse): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = handler(request) }
        start()
    }

    @Test
    fun `asks DraftKings and FanDuel in one call, never Pinnacle, with the key in a header, and keeps the answer for hours`() = runBlocking {
        val seen = ArrayList<RecordedRequest>()
        val server = serve { r ->
            seen += r
            if (r.path!!.startsWith("/v1/sports/americanfootball_nfl/closing-lines")) MockResponse().setBody(real()).setHeader("x-requests-last", "5") else MockResponse().setResponseCode(404)
        }
        try {
            var now = giantsStart + 86_400_000L
            val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            val ml = giants("ml")
            val out = closes.closes(listOf(ml, giants("dog", "Washington Commanders")))
            val found = out.getValue("ml") as CloseLookup.Found
            assertEquals((CloseDevig.fair(p(-122), p(102)) + CloseDevig.fair(p(-128), p(100))) / 2, found.fair, 1e-9)
            assertEquals("ParlayAPI · DraftKings + FanDuel close", found.via)
            assertTrue(out.getValue("dog") is CloseLookup.Found)
            // One call for the league, naming the two books and nobody else.
            val url = seen.single().requestUrl!!
            assertEquals("/v1/sports/americanfootball_nfl/closing-lines", url.encodedPath)
            assertEquals("draftkings,fanduel", url.queryParameter("bookmakers"))
            assertEquals("2", url.queryParameter("daysFrom"))
            assertEquals("american", url.queryParameter("oddsFormat"))
            assertEquals("pk-1", seen.single().getHeader("X-API-Key"))
            assertEquals(null, url.queryParameter("apiKey")) // ParlayAPI's best practices: the key in a header, never the URL
            assertTrue("no request names pinnacle", seen.none { it.path!!.contains("pinnacle", ignoreCase = true) })
            // Within KEEP_MS the same league-day isn't bought again; after it, it is.
            val asked = closes.requests
            now += 60 * 60_000L
            closes.closes(listOf(ml))
            assertEquals(asked, closes.requests)
            now += ParlayCloses.KEEP_MS
            closes.closes(listOf(ml))
            assertTrue(closes.requests > asked)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a spread, a total or a prop is not asked of ParlayAPI at all, and its next source is told why`() = runBlocking {
        val server = serve { MockResponse().setBody(real()) }
        try {
            val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, server.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 86_400_000L })
            val out = closes.closes(listOf(giants("sp", "New York Giants -2.5", "Spread"), giants("tot", "Under 44.5", "Total"), giants("prop", "Malik Nabers Over 5.5", "Player Receptions")))
            assertEquals(listOf("sp", "tot", "prop").map { ParlayCloses.ONLY_MONEYLINES }, listOf("sp", "tot", "prop").map { (out.getValue(it) as CloseLookup.None).reason })
            assertEquals(0, closes.requests)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a book ParlayAPI no longer serves is a never, not a later, is dropped and the look goes on with the others, and is not named again`() = runBlocking {
        val asked = ArrayList<String>()
        val unknown = """{"detail":{"error":"UNKNOWN_BOOKMAKER","unknown":["fanduel"],"message":"Unknown bookmaker key(s): ['fanduel']. Call GET /v1/bookmakers for the valid list.","request_id":"x","docs_url":"/docs","status":400}}"""
        val server = serve { r ->
            val books = r.requestUrl!!.queryParameter("bookmakers").orEmpty()
            asked += books
            if ("fanduel" in books) MockResponse().setResponseCode(400).setBody(unknown) else MockResponse().setBody(real())
        }
        try {
            var now = giantsStart + 86_400_000L
            val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            val first = closes.closes(listOf(giants("ml"))).getValue("ml") as CloseLookup.Found
            // FanDuel refused, DraftKings' close kept: the same look went on without it.
            assertEquals(listOf("draftkings,fanduel", "draftkings"), asked)
            assertEquals("ParlayAPI · DraftKings close", first.via)
            assertEquals(CloseDevig.fair(p(-122), p(102)), first.fair, 1e-9)
            // Not named again.
            now += ParlayCloses.KEEP_MS
            closes.closes(listOf(giants("ml2")))
            assertEquals("draftkings", asked.last())
            assertEquals(3, asked.size)
        } finally {
            server.shutdown()
        }
        // Every book refused (what Pinnacle's refusal looked like): a "never" that says so, and no further request on the next look.
        val all = """{"detail":{"error":"UNKNOWN_BOOKMAKER","unknown":["draftkings","fanduel"],"message":"Unknown bookmaker key(s)","status":400}}"""
        val dead = serve { MockResponse().setResponseCode(400).setBody(all) }
        try {
            val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, dead.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 86_400_000L })
            val none = closes.closes(listOf(giants("ml"))).getValue("ml") as CloseLookup.None
            assertTrue(none.reason, none.reason.contains("no longer serves DraftKings or FanDuel"))
            val before = dead.requestCount
            assertEquals(ParlayCloses.NO_BOOKS, (closes.closes(listOf(giants("ml3"))).getValue("ml3") as CloseLookup.None).reason)
            assertEquals(before, dead.requestCount)
        } finally {
            dead.shutdown()
        }
    }

    @Test
    fun `a 400 that names no books refuses the ones asked, and a body that names none of ours does not loop`() = runBlocking {
        val bare = serve { MockResponse().setResponseCode(400).setBody("""{"detail":{"error":"UNKNOWN_BOOKMAKER"}}""") }
        val odd = serve { MockResponse().setResponseCode(400).setBody("""{"detail":{"error":"UNKNOWN_BOOKMAKER","unknown":["pinnacle"]}}""") }
        try {
            val a = ParlayCloses(OkHttpClient(), pool("k"), json, bare.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 86_400_000L })
            assertTrue(a.closes(listOf(giants("ml"))).getValue("ml") is CloseLookup.None)
            val b = ParlayCloses(OkHttpClient(), pool("k"), json, odd.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 86_400_000L })
            assertTrue(b.closes(listOf(giants("ml"))).getValue("ml") is CloseLookup.None)
            assertEquals("one request, not a loop", 1, odd.requestCount)
        } finally {
            bare.shutdown()
            odd.shutdown()
        }
    }

    @Test
    fun `past the plan's window is nothing to find, out of credits is asked again later`() = runBlocking {
        var body = """{"detail":{"error":"HISTORICAL_LIMIT"}}"""
        val server = serve { MockResponse().setResponseCode(403).setBody(body) }
        try {
            val now = giantsStart + 5 * 86_400_000L
            val ml = giants("ml")
            val limit = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            assertTrue(limit.closes(listOf(ml))["ml"] is CloseLookup.None)
            body = """{"error":"credit_limit_exceeded"}"""
            val broke = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            assertTrue(broke.closes(listOf(ml))["ml"] is CloseLookup.Later)
            // Older than the plan's history (Starter: 7 days, /v1/meta/limits): not asked at all.
            val old = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 8 * 86_400_000L })
            assertEquals("Older than your ParlayAPI plan's 7-day history", (old.closes(listOf(ml))["ml"] as CloseLookup.None).reason)
            assertEquals(0, old.requests)
            // On Pro (30 days) a 20-day-old game is asked, never further back than the plan reaches.
            body = """{"detail":{"error":"HISTORICAL_LIMIT"}}"""
            val before = server.requestCount
            val pro = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 20 * 86_400_000L }, historyDays = { 30 })
            pro.closes(listOf(ml))
            repeat(before) { server.takeRequest() }
            assertEquals("21", server.takeRequest().requestUrl!!.queryParameter("daysFrom"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a server error is asked again later, not a never`() = runBlocking {
        val server = serve { MockResponse().setResponseCode(500).setBody("""{"detail":"oops"}""") }
        try {
            val closes = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { giantsStart + 86_400_000L })
            assertTrue(closes.closes(listOf(giants("ml")))["ml"] is CloseLookup.Later)
        } finally {
            server.shutdown()
        }
    }

    // ---- in the back-fill ---------------------------------------------------------------------------------------------

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        val f = File.createTempFile("bets", ".json").also { it.delete() }
        f.writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(f)
    }

    private class Fake(val answer: CloseLookup, override val sharp: Boolean = false) : CloseSource {
        var asked = 0
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.size; return bets.associate { it.id to answer } }
    }

    @Test
    fun `ParlayAPI's DraftKings and FanDuel closes are a soft close, with a key it is asked after ESPN, without one it isn't asked or counted`() = runBlocking {
        val server = serve { MockResponse().setBody(real()) }
        try {
            val now = giantsStart + 86_400_000L
            val url = server.url("/v1").toString().trimEnd('/')
            val t = tracker(giants("ml").copy(fairAtBet = 0.55))
            val withKey = ParlayCloses(OkHttpClient(), pool("k"), json, url, clock = { now })
            assertFalse("a soft book's close is not the sharp one", withKey.sharp)
            // ESPN has nothing for this one: ParlayAPI's is next.
            val espn = Fake(CloseLookup.None("Not on ESPN's scoreboard"))
            CloseBackfill(t, listOf(espn, withKey), clock = { now }).run()
            assertEquals(1, espn.asked)
            assertEquals("ParlayAPI · DraftKings + FanDuel close", t.all().single().closeVia)
            assertEquals("DraftKings + FanDuel via ParlayAPI", ClosingLine.sourceLabel(t.all().single().closeVia!!))
            // ...and ESPN, when it has the close, is asked first and ParlayAPI is not asked (a call that costs credits).
            val t1 = tracker(giants("ml").copy(fairAtBet = 0.55))
            val parlay = ParlayCloses(OkHttpClient(), pool("k"), json, url, clock = { now })
            val espnFound = Fake(CloseLookup.Found(0.7, "ESPN · DraftKings close"))
            CloseBackfill(t1, listOf(espnFound, parlay), clock = { now }).run()
            assertEquals("ESPN · DraftKings close", t1.all().single().closeVia)
            assertEquals(0, parlay.requests)

            // No key: never asked, and ESPN + Novig saying "never" is enough to stop looking.
            val t2 = tracker(giants("prop", "Malik Nabers Over 5.5", "Player Receptions"))
            val keyless = ParlayCloses(OkHttpClient(), pool(), json, url, clock = { now })
            assertFalse(keyless.active)
            // Switched off in Settings, a key doesn't make it asked.
            assertFalse(ParlayCloses(OkHttpClient(), pool("k"), json, url).apply { enabled = false }.active)
            val none = Fake(CloseLookup.None("nope"))
            CloseBackfill(t2, listOf(keyless, none, Fake(CloseLookup.None("nor here"))), clock = { now }).run()
            assertEquals(0, keyless.requests)
            val b = t2.all().single()
            assertTrue(b.closeFinal)
            assertEquals("nope; nor here", b.closeNote)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `scans leave a key's last credits to the closing lines`() = runBlocking {
        val server = serve { r ->
            if (r.path!!.contains("/closing-lines")) MockResponse().setBody(real()).setHeader("x-requests-remaining", "285").setHeader("x-requests-used", "19715")
            else MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "290").setHeader("x-requests-used", "19710").setHeader("x-requests-last", "3")
        }
        try {
            val url = server.url("/v1").toString().trimEnd('/')
            val shared = pool("pk")
            val scans = TheOddsApiClient(OkHttpClient(), shared, json, baseUrl = url, minIntervalMs = 0, feed = OddsFeed.PARLAY)
            scans.fetch("americanfootball_nfl", listOf("draftkings")) // the server says 290 left: under the 300 kept back
            val refused = runCatching { scans.fetch("americanfootball_nfl", listOf("draftkings")) }.exceptionOrNull()
            assertTrue(refused is CreditsHeldBackException)
            assertEquals("The last 300 credits on your ParlayAPI key are kept for closing lines.", refused!!.message)
            assertEquals(1, server.requestCount)
            // The closing lines still get them.
            val now = giantsStart + 86_400_000L
            val closes = ParlayCloses(OkHttpClient(), shared, json, url, clock = { now })
            assertTrue(closes.closes(listOf(giants("ml")))["ml"] is CloseLookup.Found)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    /** Tj's diagnostics, 2026-09-30: "100 started with no close found yet", 69 of them marked final by ESPN and Novig before ParlayAPI existed. */
    @Test
    fun `a bet every old source gave up on is asked again once ParlayAPI is added, and not again after`() = runBlocking {
        val now = giantsStart + 2 * 86_400_000L
        val given = giants("ml").copy(
            closeFinal = true, closeLookedAtMs = giantsStart + 3_600_000L, closeNote = "ESPN keeps full-game moneylines, spreads and totals only; No Novig outcome on record for this bet",
        )
        val t = tracker(given)
        val parlay = object : CloseSource {
            var asked = 0
            override val id = ParlayCloses.ID
            override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.size; return bets.associate { it.id to CloseLookup.None("Not in ParlayAPI's closing lines") } }
        }
        val espn = Fake(CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only"))
        CloseBackfill(t, listOf(parlay, espn), clock = { now }).run()
        assertEquals(1, parlay.asked)
        val b = t.all().single()
        assertTrue(b.closeFinal)
        assertTrue(ParlayCloses.ID in b.closeAskedOf!!)
        // Asked of every source now: left alone.
        CloseBackfill(t, listOf(parlay, espn), clock = { now + CloseBackfill.RETRY_MS }).run()
        assertEquals(1, parlay.asked)
        // Found: the close counts.
        val found = tracker(given)
        CloseBackfill(found, listOf(Fake(CloseLookup.Found(0.58, "ParlayAPI · DraftKings close"))), clock = { now }).run()
        assertEquals(0.58, found.all().single().closeFair!!, 0.0)
    }

    /** Tj's diagnostics 2026-09-30: "missing ×53: ParlayAPI has no key for this league" read as a key problem; they were early ✓ imports. */
    @Test
    fun `a bet with no league on record says it's an early import, not that a key is missing`() = runBlocking {
        val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, "http://127.0.0.1:9/v1", clock = { giantsStart + 86_400_000L })
        val old = giants("imp", "Isaac Paredes Over 1.5", "Player Hits").copy(league = "")
        assertEquals("No league on record (a ✓ mark imported before the Tracker)", (closes.closes(listOf(old)).getValue("imp") as CloseLookup.None).reason)
        assertNull(closes.closes(emptyList()).values.firstOrNull())
    }
}
