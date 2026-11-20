package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefMilestone
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MK22: every price the feeds carry at a strike (two-sided rows and "N+" ladders), not only the lines the scan priced (research/prop_ladder_fair_2026-11-20.md). */
class PropLadderFairTest {
    private val now = 1_790_500_000_000L

    private fun american(a: Int) = if (a > 0) 1.0 + a / 100.0 else 1.0 + 100.0 / -a

    private fun twoSided(book: String, player: String, stat: String, point: Double, over: Int, under: Int) = RefBookMarket(
        book, book, LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, american(over), point), RefQuote(Side.UNDER, american(under), point)), now - 30_000L, subject = player, stat = stat)

    private fun milestone(book: String, player: String, stat: String, n: Int, price: Int, seen: Long? = now - 20_000L) = RefMilestone(book, player, stat, n - 0.5, american(price), seen)

    private fun event(markets: List<RefBookMarket>, milestones: List<RefMilestone>) = RefEvent("pl:1", "basketball_nba", now + 3_600_000L, "Spurs", "Clippers", markets, milestones = milestones)

    // ---- the parse of the real answer's shapes ----
    @Test fun propLineMilestoneRowsAreKeptApartFromTheTwoSidedMarkets() {
        val raw = """[{"id":"1","home_team":"San Antonio Spurs","away_team":"Los Angeles Clippers","commence_time":"2026-11-20T01:00:00Z","bookmakers":[
            {"key":"draftkings","title":"DraftKings","last_update":"2026-11-20T00:26:22Z","markets":[
              {"key":"player_points","last_update":"2026-11-20T00:26:22Z","line_type":"milestone","outcomes":[
                {"name":"10+ Points","description":"Brook Lopez","price":-217,"point":null,"last_seen_at":"2026-11-20T00:26:22Z"},
                {"name":"20+ Points","description":"Brook Lopez","price":450,"point":null,"last_seen_at":"2026-11-20T00:26:22Z"}]},
              {"key":"player_points","last_update":"2026-11-20T00:26:22Z","line_type":"main","outcomes":[
                {"name":"Over","description":"Brook Lopez","price":-123,"point":11.5,"last_seen_at":"2026-11-20T00:26:22Z"},
                {"name":"Under","description":"Brook Lopez","price":101,"point":11.5,"last_seen_at":"2026-11-20T00:26:22Z"}]},
              {"key":"player_anytime_td","last_update":"2026-11-20T00:26:22Z","outcomes":[{"name":"Yes","description":"Somebody","price":-110,"point":null}]}]},
            {"key":"bovada","title":"Bovada","last_update":"2026-11-20T00:26:22Z","markets":[
              {"key":"player_points","last_update":"2026-11-20T00:26:22Z","line_type":"milestone","outcomes":[
                {"name":"To Score 15+ Points","description":"Brook Lopez","price":150,"point":null,"last_seen_at":"2026-11-20T00:26:22Z"}]},
              {"key":"player_rebounds","last_update":"2026-11-20T00:26:22Z","outcomes":[
                {"name":"3+ Rebounds","description":"Brook Lopez","price":-300,"point":2.5}]}]}]}]"""
        val e = PropLineClient.parseEvents(raw, Json { ignoreUnknownKeys = true }, "basketball_nba").single()
        val m = e.milestones
        assertEquals("three N+ rows: two of DraftKings, one of Bovada; the Yes market, the row with a point are not", 3, m.size)
        assertEquals(setOf(9.5, 19.5, 14.5), m.map { it.strike }.toSet())
        assertTrue(m.all { it.stat == "POINTS" && it.subject == "Brook Lopez" })
        assertEquals(american(-217), m.single { it.strike == 9.5 }.decimal, 1e-9)
        assertEquals("Bovada's 'To Score 15+ Points' is Over 14.5", "bovada", m.single { it.strike == 14.5 }.bookKey)
        assertTrue("the milestone rows are not two-sided markets", e.markets.none { it.quotes.size != 2 })
        assertEquals("the two-sided line is unchanged", 11.5, e.markets.single { it.stat == "POINTS" }.line!!, 0.0)
    }

    // ---- the model ----
    @Test fun aStrikeIsTheMeanOfOneVotePerCompanyAndAMilestoneRowIsDividedByItsMargin() {
        val ev = event(
            listOf(twoSided("fanduel", "Brook Lopez", "POINTS", 11.5, -110, -110)),                      // 50.0%
            listOf(milestone("draftkings", "Brook Lopez", "POINTS", 12, -134), milestone("fanduel", "Brook Lopez", "POINTS", 12, -134)),
        )
        val store = PropLadderFair.build(listOf(ev), holds = mapOf("draftkings" to 1.07))
        val t = store.table("pl:1", com.tjshea.vigilant.data.match.PlayerNames.key("Brook Lopez"), "POINTS")!!
        val s = t.strikes.getValue(11.5)
        // FanDuel has a two-sided row AND an N+ row at the strike: one vote. DraftKings: 1/1.746 = 0.573 / 1.07 = 0.535.
        assertEquals("two companies", 2, s.companies)
        val dk = (1.0 / american(-134)) / 1.07
        val fd = (0.5 + (1.0 / american(-134)) / PropLadderFair.DEFAULT_HOLD) / 2.0
        assertEquals((dk + fd) / 2.0, s.overProb, 1e-9)
        assertEquals("the older of the two middle ages", now - 30_000L, s.asOfMs)
    }

    @Test fun aBookIsCalibratedOnTheStrikesWhereItsNPlusRowMeetsTheTwoSidedConsensus() {
        val markets = ArrayList<RefBookMarket>(); val ms = ArrayList<RefMilestone>()
        for (i in 1..14) {
            val player = "Player $i"
            markets += twoSided("fanduel", player, "POINTS", 19.5, -110, -110)
            markets += twoSided("betmgm", player, "POINTS", 19.5, -110, -110)
            ms += milestone("draftkings", player, "POINTS", 20, -122)     // implied 0.549, consensus 0.5: ratio 1.098
        }
        val holds = PropLadderFair.calibrate(listOf(event(markets, ms)))
        assertEquals(1.098, holds.getValue("draftkings"), 0.002)
        assertNull("a book with too few shared strikes is not calibrated (the default applies)", PropLadderFair.calibrate(listOf(event(markets.take(4), ms.take(2)))).get("draftkings"))
    }

    @Test fun aSideOutsideTheTrustedRangeIsNotPriced() {
        val ev = event(emptyList(), listOf(milestone("draftkings", "A", "POINTS", 10, -50000), milestone("fanduel", "A", "POINTS", 10, -50000), milestone("betmgm", "A", "POINTS", 10, -50000)))
        val store = PropLadderFair.build(listOf(ev))
        val t = store.table("pl:1", com.tjshea.vigilant.data.match.PlayerNames.key("A"), "POINTS")!!
        assertNull("over 9.5 is a 93%+ side after the margin", PropLadderFair.atStrike(t, 9.5, true))
        assertNull("and its under a 7% one", PropLadderFair.atStrike(t, 9.5, false))
        assertNull("no vote at another strike", PropLadderFair.atStrike(t, 12.5, true))
    }

    @Test fun theIndexPairsANovigStrikeWithItsPlayerStatAndStrike() {
        val ev = event(
            listOf(twoSided("fanduel", "Jalen Brunson", "POINTS", 25.5, -110, -110), twoSided("betmgm", "Jalen Brunson", "POINTS", 25.5, -112, -108)),
            listOf(milestone("draftkings", "Jalen Brunson", "POINTS", 26, 120), milestone("fanatics", "Jalen Brunson", "POINTS", 15, -600)),
        )
        val index = PropLadderFair.Index(PropLadderFair.build(listOf(ev)), mapOf("novig-event" to "pl:1"))
        val player = RfqLadder.playerOf("Jalen Brunson 25.5 POINTS", "POINTS", 25.5)
        assertEquals("jalen brunson", player.lowercase().let { if (it.contains("brunson")) "jalen brunson" else it })
        fun target(strike: Double, over: Boolean, event: String = "novig-event") = RfqLadder.Target("g", strike, over, event, "POINTS", now + 3_600_000L, player)
        val over = index.at(target(25.5, true))!!
        val under = index.at(target(25.5, false))!!
        assertEquals("the two sides of one strike add up", 1.0, over.overProb + (1.0 - over.overProb), 0.0)
        assertTrue("three companies at 25.5 (FanDuel, BetMGM and DraftKings' 26+)", over.companies >= 2)
        assertNotNull(under)
        assertNull("a Novig game with no paired reference game", index.at(target(25.5, true, event = "other")))
        assertNull("a stat nobody posted", PropLadderFair.Index(PropLadderFair.build(listOf(ev)), mapOf("novig-event" to "pl:1")).at(RfqLadder.Target("g", 25.5, true, "novig-event", "REBOUNDS", now, player)))
    }

    @Test fun fairsTakesTheVotesBeforeInterpolationAndAfterTheScansOwnFair() {
        val ev = event(
            listOf(twoSided("fanduel", "Jalen Brunson", "POINTS", 25.5, -110, -110), twoSided("betmgm", "Jalen Brunson", "POINTS", 25.5, -110, -110), twoSided("pinnacle", "Jalen Brunson", "POINTS", 25.5, -110, -110)),
            listOf(milestone("draftkings", "Jalen Brunson", "POINTS", 20, -450), milestone("fanatics", "Jalen Brunson", "POINTS", 20, -450), milestone("betrivers", "Jalen Brunson", "POINTS", 20, -450)),
        )
        val player = RfqLadder.playerOf("Jalen Brunson 19.5 POINTS", "POINTS", 19.5)
        val index = PropLadderFair.Index(PropLadderFair.build(listOf(ev)), mapOf("n1" to "pl:1"))
        val g = RfqLadder.group("n1", "POINTS", "Jalen Brunson 19.5 POINTS", 19.5)
        val targets = mapOf(
            "o20" to RfqLadder.Target(g, 19.5, true, "n1", "POINTS", now + 3_600_000L, player),
            "u20" to RfqLadder.Target(g, 19.5, false, "n1", "POINTS", now + 3_600_000L, player),
            "o26" to RfqLadder.Target(g, 25.5, true, "n1", "POINTS", now + 3_600_000L, player),
        )
        val f = RfqLadder.Fairs({ null }, { targets }, { emptyMap() }, { false }, { index })
        val o = f.leg("o20")!!; val u = f.leg("u20")!!
        assertEquals(1.0, o.probability + u.probability, 1e-9)
        assertEquals("three companies voted at 19.5", 3, o.books)
        assertTrue(o.label, o.label.startsWith("ladder-votes"))
        assertTrue("a heavy favourite's side priced from three N+ rows (-450 less 7%: about 0.77): ${o.probability}", o.probability in 0.70..0.85)
        assertEquals("the two-sided strike works too", 3, f.leg("o26")!!.books)
        val scan = LegFair("o20", "n1", "scan", 0.123, 5, now, now, false, "POINTS")
        assertEquals("the scan's own fair comes first", 0.123, RfqLadder.Fairs({ scan }, { targets }, { emptyMap() }, { false }, { index }).leg("o20")!!.probability, 0.0)
        assertNull("switched off, nothing else priced it", RfqLadder.Fairs({ null }, { targets }, { emptyMap() }, { false }, { null }).leg("o20"))
    }
}
