package com.tjshea.vigilant.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each free feed's real reply shape (trimmed samples read 2026-10-07 from Sofascore, Polymarket, ESPN and the NHL) read as game scores and odds. */
class FeedParsersTest {

    @Test
    fun `Sofascore tennis reads games in the current set with the set number folded in, other sports the score`() {
        val tennis = """{"events":[{"id":17269437,"homeTeam":{"name":"Mattia Bellucci"},"awayTeam":{"name":"Yi Zhou"},"homeScore":{"current":0,"display":0,"period1":4,"period2":0,"point":"0"},"awayScore":{"current":1,"display":1,"period1":6,"period2":0,"point":"0"},"status":{"code":9,"description":"2nd set","type":"inprogress"},"tournament":{"name":"Shanghai, China"}}]}"""
        val r = FeedParsers.sofascore(tennis, "tennis").single()
        assertEquals("17269437", r.gameId); assertEquals("Mattia Bellucci", r.home)
        // Second set, 0-0: set 2's games are offset by 100, so it is a new score, not set 1's.
        assertEquals(100 to 100, r.h to r.a)
        assertEquals("Shanghai, China", r.league)
        // The same payload read as a ball game gives the sets won.
        val football = FeedParsers.sofascore(tennis, "football").single()
        assertEquals(0 to 1, football.h to football.a)
        assertTrue(FeedParsers.sofascore("""{"events":[{"id":1}]}""", "tennis").isEmpty())
        assertTrue(FeedParsers.sofascore("not json", "tennis").isEmpty())
    }

    @Test
    fun `Polymarket's sports socket frames carry the score, the set for tennis, and a ping is nothing`() {
        val frame = """{"gameId": 6383932, "leagueAbbreviation": "atp", "homeTeam": "Zhizhen Zhang", "awayTeam": "Tomas Machac", "status": "inprogress", "score": "4-1", "period": "S1", "live": true, "ended": false}"""
        val r = FeedParsers.polymarketScore(frame)!!
        assertEquals("6383932", r.gameId); assertEquals(4 to 1, r.h to r.a); assertTrue(r.live)
        val set2 = FeedParsers.polymarketScore(frame.replace("\"4-1\"", "\"2-0\"").replace("S1", "S2"))!!
        assertEquals(102 to 100, set2.h to set2.a)
        // A team sport's score is as given, and a non-tennis league never gets the set offset.
        val nhl = FeedParsers.polymarketScore("""{"gameId": 9, "leagueAbbreviation": "nhl", "homeTeam": "Bruins", "awayTeam": "Devils", "score": "2-3", "period": "S2", "live": false}""")!!
        assertEquals(2 to 3, nhl.h to nhl.a); assertFalse(nhl.live)
        assertNull(FeedParsers.polymarketScore("ping"))
        assertNull(FeedParsers.polymarketScore("""{"gameId": 1, "homeTeam": "A", "awayTeam": "B"}"""))
    }

    @Test
    fun `ESPN's scoreboard and the NHL's score route and MLB's schedule are read`() {
        val espn = """{"events":[{"id":"401891815","status":{"type":{"state":"in"}},"competitions":[{"competitors":[{"homeAway":"home","score":"4","team":{"displayName":"Montreal Canadiens"}},{"homeAway":"away","score":"6","team":{"displayName":"Carolina Hurricanes"}}]}]}]}"""
        val e = FeedParsers.espn(espn).single()
        assertEquals("Montreal Canadiens", e.home); assertEquals(4 to 6, e.h to e.a); assertTrue(e.live)
        val nhl = """{"games":[{"id":2026020053,"gameState":"LIVE","homeTeam":{"name":{"default":"Capitals"},"placeName":{"default":"Washington"},"score":2},"awayTeam":{"name":{"default":"Penguins"},"score":1}}]}"""
        val n = FeedParsers.nhl(nhl).single()
        assertEquals("Washington Capitals", n.home); assertEquals("Penguins", n.away); assertEquals(2 to 1, n.h to n.a); assertTrue(n.live)
        assertFalse(FeedParsers.nhl(nhl.replace("LIVE", "FUT")).single().live)
        val mlb = """{"dates":[{"games":[{"gamePk":849819,"status":{"abstractGameState":"Live"},"teams":{"home":{"score":3,"team":{"name":"Atlanta Braves"}},"away":{"score":2,"team":{"name":"Los Angeles Dodgers"}}}}]}]}"""
        val m = FeedParsers.mlb(mlb).single()
        assertEquals("849819", m.gameId); assertEquals(3 to 2, m.h to m.a); assertTrue(m.live)
        assertTrue(FeedParsers.espn("{}").isEmpty() && FeedParsers.nhl("[]").isEmpty() && FeedParsers.mlb("").isEmpty())
    }

    @Test
    fun `Polymarket's odds socket gives best bid and ask from price changes and from a full book`() {
        val change = """{"market":"0x02","price_changes":[{"asset_id":"tok1","price":"0.05","size":"4400","side":"BUY","best_bid":"0.58","best_ask":"0.60"},{"asset_id":"tok2","price":"0.40","best_bid":"0.40","best_ask":"0.42"}],"timestamp":"1791357074213"}"""
        val q = FeedParsers.polymarketQuotes(change)
        assertEquals(listOf("tok1", "tok2"), q.map { it.assetId })
        assertEquals(0.58, q[0].bid, 1e-9); assertEquals(0.60, q[0].ask, 1e-9); assertEquals(1791357074213L, q[0].serverMs)
        val book = """[{"market":"0x02","asset_id":"tok1","timestamp":"1791357074000","bids":[{"price":"0.01","size":"5"},{"price":"0.57","size":"9"}],"asks":[{"price":"0.99","size":"5"},{"price":"0.61","size":"9"}]}]"""
        val b = FeedParsers.polymarketQuotes(book).single()
        assertEquals(0.57, b.bid, 1e-9); assertEquals(0.61, b.ask, 1e-9)
        assertTrue(FeedParsers.polymarketQuotes("""{"event_type":"last_trade_price","asset_id":"tok1","price":"0.58"}""").isEmpty())
        assertTrue(FeedParsers.polymarketQuotes("nonsense").isEmpty())
    }

    @Test
    fun `Polymarket's market for a game is found by the tokens and outcome names of its first market`() {
        val body = """[{"title":"Shanghai Rolex Masters: Zhizhen Zhang vs Tomas Machac","markets":[{"clobTokenIds":"[\"tokA\", \"tokB\"]","outcomes":"[\"Zhizhen Zhang\", \"Tomas Machac\"]"},{"clobTokenIds":"[\"x\",\"y\"]","outcomes":"[\"Yes\",\"No\"]"}]}]"""
        val m = FeedParsers.polymarketMarket(body)!!
        assertEquals("Shanghai Rolex Masters: Zhizhen Zhang vs Tomas Machac", m.title)
        assertEquals(listOf("tokA" to "Zhizhen Zhang", "tokB" to "Tomas Machac"), m.tokens)
        assertNull(FeedParsers.polymarketMarket("[]"))
        assertNull(FeedParsers.polymarketMarket("""[{"title":"t","markets":[{"clobTokenIds":"[\"only\"]","outcomes":"[\"one\"]"}]}]"""))
    }
}
