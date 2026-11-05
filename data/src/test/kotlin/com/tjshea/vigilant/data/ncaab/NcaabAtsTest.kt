package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The college basketball against-the-spread system (NC1): the signals, the history lookup, the team matching, the Novig board and the game view. */
class NcaabAtsTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/ncaab/$name")) { "missing $name" }.readBytes().toString(Charsets.UTF_8)

    private fun team(ppg: Double, papg: Double, ats: Double) = NcaabTeam(n = "T", g = 8, all = 8, ppg = ppg, papg = papg, ats = ats)

    // ---- the signals ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `every signal equals the Python tool's on its golden cases`() {
        val cases = Json.parseToJsonElement(resource("signal-golden.json")).jsonArray
        assertTrue(cases.size >= 50)
        fun t(o: JsonObject) = team(o["ppg"]!!.jsonPrimitive.double, o["papg"]!!.jsonPrimitive.double, o["ats"]!!.jsonPrimitive.double)
        var checked = 0
        for (c in cases) {
            val o = c.jsonObject
            val h = t(o["h"]!!.jsonObject)
            val a = t(o["a"]!!.jsonObject)
            for ((id, want) in o["expect"]!!.jsonObject) {
                val got = NcaabSignals.value(id, h, a, o["sp"]!!.jsonPrimitive.double, o["tt"]!!.jsonPrimitive.double, o["neu"]!!.jsonPrimitive.long == 1L)
                if (want.toString() == "null") assertNull("$id for $o", got) else assertEquals("$id for $o", want.jsonPrimitive.double, got!!, 1e-6)
                checked++
            }
        }
        assertEquals(cases.size * 4, checked)
    }

    private fun bucket(lo: Double, hi: Double?, n: Int, r: Double, r1: Double? = r, r2: Double? = r, rc: Double? = null, nc: Int = 0) = NcaabBucket(lo, hi, n, r, r1, n / 2, r2, n / 2, rc, nc)

    private fun model(ats: Int = -1) = NcaabModel(
        source = "backtest", sigma = 11.0, signals = mapOf(
            NcaabSignals.AVGS_LINE to NcaabSignalTable("Averages vs the spread", "spread", "points", "", 1, listOf(bucket(0.0, 3.0, 2000, 0.505), bucket(3.0, 6.0, 1400, 0.54), bucket(6.0, null, 600, 0.47))),
            NcaabSignals.ATS_GAP to NcaabSignalTable("Streak", "spread", "tiers", "", ats, listOf(bucket(0.0, 0.8, 2000, 0.47), bucket(0.8, null, 700, 0.46))),
            NcaabSignals.TOTAL_GAP to NcaabSignalTable("Total", "total", "points", "", 1, listOf(bucket(0.0, 6.0, 3000, 0.49), bucket(6.0, null, 80, 0.60))),
        ),
    )

    @Test
    fun `a signal names the side its history backs, fades when the history says so, and is backed only with enough games above break-even in both halves`() {
        val home = team(78.0, 66.0, 1.2)   // scores more, covers more
        val away = team(66.0, 70.0, -0.4)
        val reads = NcaabSignals.read(model(), home, away, homeSpread = -2.0, total = 140.0, neutral = false).associateBy { it.id }
        // averages: ((78-66)-(66-70))/2 = 8 + 3 home court - 2 = 9 -> the 6+ bucket: 47%, so not backed
        assertEquals(9.0, reads.getValue(NcaabSignals.AVGS_LINE).value, 1e-9)
        assertFalse(reads.getValue(NcaabSignals.AVGS_LINE).backed)
        // the streak: home covers more by 1.6 tiers; the history FADES that side, so the pick is the away team and its rate is 1 - 0.46
        val ats = reads.getValue(NcaabSignals.ATS_GAP)
        assertEquals(NcaabSignals.Pick.AWAY, ats.pick)
        assertTrue(ats.fade)
        assertEquals(0.54, ats.rate, 1e-9)
        assertTrue("0.54 over 700 games, both halves above 50%", ats.backed)
        // a total bucket with 80 games is never evidence, whatever its rate
        val tot = reads.getValue(NcaabSignals.TOTAL_GAP)
        assertEquals(NcaabSignals.Pick.OVER, tot.pick)   // (78+70)/2 + (66+66)/2 - 140 = 74 + 66 - 140 = 0 ... see next line
        assertEquals(0.0, tot.value, 1e-9)
    }

    @Test
    fun `a total far above the line is an Over, below an Under, and a small bucket is not backed`() {
        val h = team(80.0, 72.0, 0.0)
        val a = team(78.0, 74.0, 0.0)
        val over = NcaabSignals.read(model(), h, a, -1.0, total = 130.0, neutral = true).first { it.id == NcaabSignals.TOTAL_GAP }
        assertEquals(NcaabSignals.Pick.OVER, over.pick)
        assertEquals(0.60, over.rate, 1e-9)
        assertFalse("80 games is under the minimum", over.backed)
        val under = NcaabSignals.read(model(), h, a, -1.0, total = 160.0, neutral = true).first { it.id == NcaabSignals.TOTAL_GAP }
        assertEquals(NcaabSignals.Pick.UNDER, under.pick)
    }

    @Test
    fun `a half of the sample below 50 percent vetoes a bucket and a model with no measurement reads nothing`() {
        val m = NcaabModel(source = "backtest", signals = mapOf(NcaabSignals.TOTAL_GAP to NcaabSignalTable("Total", "total", "points", "", 1, listOf(bucket(0.0, null, 1000, 0.56, r1 = 0.62, r2 = 0.45)))))
        val h = team(80.0, 72.0, 0.0)
        val a = team(78.0, 74.0, 0.0)
        assertFalse(NcaabSignals.read(m, h, a, -1.0, 120.0, true).single().backed)
        assertTrue(NcaabSignals.read(NcaabModel(), h, a, -1.0, 120.0, true).isEmpty())
    }

    @Test
    fun `the normal curve helpers are right and a shifted chance moves the right way`() {
        assertEquals(0.5, NcaabSignals.cdf(0.0), 1e-6)
        assertEquals(0.8413, NcaabSignals.cdf(1.0), 1e-3)
        assertEquals(0.0, NcaabSignals.inverseCdf(0.5), 1e-9)
        assertEquals(1.0, NcaabSignals.inverseCdf(0.8413447), 1e-3)
        assertEquals(0.5, NcaabSignals.shifted(0.5, 0.0, 11.0), 1e-9)
        assertEquals(0.8413, NcaabSignals.shifted(0.5, 11.0, 11.0), 1e-3)
        assertTrue(NcaabSignals.shifted(0.54, -3.0, 11.0) < 0.54)
        assertEquals(1.12, NcaabSignals.evAt(0.56, 0.50)!!, 1e-9 + 0.0)
    }

    // ---- the files and the matching ------------------------------------------------------------------------------------------------------------

    @Test
    fun `ratings and the schedule decode, ignoring what a newer tool adds`() {
        val r = NcaabRatings.decode("""{"asOf":5,"season":2027,"games":3,"teams":{"41":{"n":"UConn","ab":"CONN","g":6,"all":7,"ex":1,"ppg":80.5,"papg":66.0,"ats":0.5,"cm":2.1,"sym":"+","last":[1,2,0,-1],"extra":1}},"model":{"source":"none"},"cfg":{"tie":3}}""")!!
        assertEquals(80.5, r.teams.getValue("41").ppg, 0.0)
        assertEquals(listOf(1, 2, 0, -1), r.teams.getValue("41").last)
        assertFalse(r.model.measured)
        val s = NcaabSlate.decode("""{"asOf":1,"games":[{"id":"9","t":100,"d":"2026-11-05","state":"post","neu":1,"detail":"Final","h":{"id":"41","n":"UConn","dn":"UConn Huskies","ab":"CONN","rk":4},"a":{"id":"252","n":"BYU"},"hs":"86","as":"84","sp":-6.5,"tt":156.5}]}""")!!
        val g = s.games.single()
        assertEquals("84", g.awayScore)
        assertEquals(4, g.h.rk)
        assertNull(NcaabRatings.decode("not json"))
    }

    @Test
    fun `ESPN's games find Novig's events by team names and start time on a real day`() {
        val j = Json.parseToJsonElement(resource("match-fixture.json")).jsonObject
        val events = j["novig"]!!.jsonArray.map { e -> e.jsonObject.let { NovigEvent(it["eventId"]!!.jsonPrimitive.content, "BASKETBALL", "NCAAB", "OPEN_PREGAME", it["description"]!!.jsonPrimitive.content, it["startsTs"]!!.jsonPrimitive.long) } }
        val games = j["slate"]!!.jsonArray.map { g ->
            val o = g.jsonObject
            fun t(x: JsonObject) = SlateTeam(x["id"]!!.jsonPrimitive.content, x["n"]!!.jsonPrimitive.content, x["dn"]!!.jsonPrimitive.content, x["ab"]!!.jsonPrimitive.content)
            SlateGame(o["id"]!!.jsonPrimitive.content, o["t"]!!.jsonPrimitive.long, h = t(o["h"]!!.jsonObject), a = t(o["a"]!!.jsonObject))
        }
        val matched = NcaabMatch.match(games, events)
        val unmatched = events.filter { e -> matched.values.none { it.event.eventId == e.eventId } }
        // Every Novig event with an ESPN game at its start should find it; allow a couple Novig lists that ESPN's Division I schedule does not (exhibitions, other divisions).
        val withStart = events.filter { e -> games.any { kotlin.math.abs(it.t - e.startsTs) <= NcaabMatch.START_SLACK_MS } }
        assertTrue("matched ${matched.size} of ${withStart.size}; unmatched: ${unmatched.take(8).map { it.description }}", matched.size >= withStart.size * 0.9)
        // no event is taken twice and no game twice
        assertEquals(matched.size, matched.values.map { it.event.eventId }.toSet().size)
        // a match is the right pairing: both teams' names share words with the event's
        for ((id, f) in matched) {
            val g = games.first { it.id == id }
            val m = f.event.matchup!!
            val a = com.tjshea.vigilant.data.match.TeamMatcher.similarity(g.h.n, if (f.swapped) m.away else m.home)
            assertTrue("${g.h.n} vs ${f.event.description}", a >= 0.5)
        }
    }

    // ---- the board and the game view -----------------------------------------------------------------------------------------------------------

    private val fee = MarketFee(0.0, 0.0, FeeCharge.WHEN_LIVE)
    private val start = 1_800_000_000_000L
    private val now = start - 3 * 3_600_000L
    private val event = NovigEvent("ev1", "BASKETBALL", "NCAAB", "OPEN_PREGAME", "Rider @ Davidson", start)

    private fun spread(strike: Double, id: String) = NovigMarket(
        "m-$id", "ev1", "SPREAD", "OPEN", "x", start, fee,
        listOf(NovigOutcome("$id-h", "DAV ${fmtSigned(strike)}", "TBD"), NovigOutcome("$id-a", "RID ${fmtSigned(-strike)}", "TBD")), strike,
    )

    private fun total(line: Double, id: String) = NovigMarket(
        "m-$id", "ev1", "TOTAL", "OPEN", "x", start, fee,
        listOf(NovigOutcome("$id-o", "Over $line", "TBD"), NovigOutcome("$id-u", "Under $line", "TBD")), line,
    )

    private fun fmtSigned(v: Double) = (if (v >= 0) "+" else "") + v

    /** A best-bid-only book: bids on [outcomeA] at [bidA] and on [outcomeB] at [bidB] (a side's take price is one minus the other side's bid). */
    private fun book(m: NovigMarket, bidHome: Double, bidAway: Double) = NovigBook(
        m.marketId, 1, mapOf(m.outcomes[0].outcomeId to listOf(BidLevel((bidHome * 1000).toInt(), 500)), m.outcomes[1].outcomeId to listOf(BidLevel((bidAway * 1000).toInt(), 500))),
        now, partial = true,
    )

    @Test
    fun `the board keeps every number near the main one, drops the far ones, and finds the main line by price`() {
        val markets = listOf(spread(-4.5, "s1"), spread(-2.5, "s2"), spread(-6.5, "s3"), spread(-30.5, "s4"), total(141.5, "t1"), total(143.5, "t2"), total(190.5, "t3"))
        val books = mapOf(
            "m-s1" to book(markets[0], 0.49, 0.49), "m-s2" to book(markets[1], 0.44, 0.54), "m-s3" to book(markets[2], 0.54, 0.44),
            "m-t1" to book(markets[4], 0.49, 0.49), "m-t2" to book(markets[5], 0.44, 0.54),
        )
        val rows = NcaabBoard.build(listOf(event), markets, books, emptyMap(), now)
        assertTrue("the 30-point spread and the 190 total are out of reach", rows.none { it.market.marketId in setOf("m-s4", "m-t3") })
        assertEquals(10, rows.size)
        val slate = NcaabSlate(1, listOf(SlateGame("g1", start, h = SlateTeam("1", "Davidson", "Davidson Wildcats"), a = SlateTeam("2", "Rider", "Rider Broncs"))))
        val ratings = NcaabRatings(1, 2027, mapOf("1" to team(74.0, 66.0, 1.0), "2" to team(62.0, 70.0, -1.0)), NcaabModel(), 10)
        val v = NcaabView.build(slate, ratings, NcaabBoardFeed(now, rows, 1), now).single()
        assertNotNull(v.event)
        assertEquals("the even-priced number is the main one", -4.5, v.mainSpread!!.handicap, 0.0)
        assertEquals(141.5, v.mainTotal!!.line, 0.0)
        assertEquals(listOf(-6.5, -4.5, -2.5), v.spreads.map { it.handicap })
        assertEquals("Novig", v.lineSource)
        assertEquals(-4.5, v.homeSpread!!, 0.0)
        // each side's price: one minus the opposing bid
        assertEquals(0.51, v.mainSpread!!.homeCost!!, 1e-9)
        assertTrue(v.rowsAreNovigOnly())
        assertTrue(v.bettable)
    }

    private fun NcaabGameView.rowsAreNovigOnly() = spreads.all { it.home.fair == null && it.home.quote == null }

    @Test
    fun `a Novig game ESPN has not listed still gets a card, and a game with no Novig market uses ESPN's line`() {
        val markets = listOf(spread(-4.5, "s1"), total(141.5, "t1"))
        val rows = NcaabBoard.build(listOf(event), markets, mapOf("m-s1" to book(markets[0], 0.49, 0.49), "m-t1" to book(markets[1], 0.49, 0.49)), emptyMap(), now)
        val alone = NcaabView.build(null, null, NcaabBoardFeed(now, rows, 1), now).single()
        assertEquals("Davidson", alone.game.h.n)
        assertNull(alone.home)
        val slate = NcaabSlate(1, listOf(SlateGame("g9", start + 3_600_000, h = SlateTeam("7", "Kent State"), a = SlateTeam("8", "Ohio"), sp = -3.5, tt = 151.5)))
        val v = NcaabView.build(slate, null, null, now).single()
        assertEquals("ESPN", v.lineSource)
        assertEquals(-3.5, v.homeSpread!!, 0.0)
        assertFalse("no Novig market, nothing to bet", v.bettable)
    }

    @Test
    fun `a picked side is the Novig outcome that backs it, with its cost and the history's expected return`() {
        val markets = listOf(spread(-4.5, "s1"), total(141.5, "t1"))
        val rows = NcaabBoard.build(listOf(event), markets, mapOf("m-s1" to book(markets[0], 0.49, 0.49), "m-t1" to book(markets[1], 0.49, 0.49)), emptyMap(), now)
        val slate = NcaabSlate(1, listOf(SlateGame("g1", start, h = SlateTeam("1", "Davidson", "Davidson Wildcats"), a = SlateTeam("2", "Rider", "Rider Broncs"))))
        val ratings = NcaabRatings(1, 2027, mapOf("1" to team(78.0, 66.0, 1.2), "2" to team(66.0, 70.0, -0.4)), model(), 10)
        val v = NcaabView.build(slate, ratings, NcaabBoardFeed(now, rows, 1), now).single()
        val streak = v.picks.first { it.reading.id == NcaabSignals.ATS_GAP }
        assertEquals("the history fades the team that covers more", NcaabSignals.Pick.AWAY, streak.reading.pick)
        assertEquals("RID +4.5", streak.opportunity!!.outcome.name)
        assertEquals(0.51, streak.cost!!, 1e-9)
        assertEquals(0.54 / 0.51 - 1, streak.systemEv!!, 1e-9)
        assertEquals(streak, v.bestPick)
    }

    @Test
    fun `the published board file round-trips and rows a scan priced win over Novig-only ones`() = runBlocking {
        val markets = listOf(spread(-4.5, "s1"), total(141.5, "t1"))
        val books = mapOf("m-s1" to book(markets[0], 0.49, 0.49), "m-t1" to book(markets[1], 0.49, 0.49))
        val first = NcaabBoard.build(listOf(event), markets, books, emptyMap(), now)
        val feed = NcaabBoardFeed(now, first, 1)
        val back = NcaabBoardFeed.decode(NcaabBoardFeed.encode(feed))!!
        assertEquals(first.size, back.rows.size)
        assertEquals(first.map { it.key }, back.rows.map { it.key })
        assertEquals(0, back.unreadable)
        assertNull(NcaabBoardFeed.decode(byteArrayOf(1, 2, 3)))
        // a priced row from the scan stands in for the Novig-only one with the same key
        val priced = first.first().copy(fairProbability = 0.55)
        val again = NcaabBoard.build(listOf(event), markets, books, mapOf(priced.key to priced), now)
        assertEquals(0.55, again.first { it.key == priced.key }.fairProbability!!, 0.0)
        // the engine's read: events, markets and tops from the source, only NCAAB, only pregame, only the types
        val src = object : NovigSource {
            override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event, event.copy(eventId = "nba", league = "NBA"))
            override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, 0, 0)
            override suspend fun market(marketId: String) = null
            override fun pushedTop(marketIds: Collection<String>) = books.filterKeys { it in marketIds }
        }
        val read = NcaabBoard.read(src, emptyList(), now)
        assertEquals(1, read.games)
        assertEquals(4, read.rows.size)
        assertTrue(read.rows.all { it.kind == LineKind.SPREAD || it.kind == LineKind.TOTAL })
    }
}
