package com.tjshea.vigilant.data.parlay

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.ParlayMarkets
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ParlayAPI's Business odds stream laid over its REST boards (PB11, Tj 2026-10-25). The rows are real ones from Tj's key (`tools/parlay.py streamprobe`, 2026-10-25 08:2x UTC, NBA), read by
 * [ParlayLive] exactly as [ParlayStream] hands them over.
 */
class ParlayLiveTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val rows = (json.parseToJsonElement(javaClass.getResource("/parlay-sse-rows-nba.json")!!.readText()) as JsonArray).map { it as JsonObject }
    private val now = 1_792_916_300_000L
    private val old = 1_792_900_000_000L

    private fun live() = ParlayLive { now }.also { l -> rows.forEach { l.onRow("basketball_nba", it, now) } }

    private fun market(kind: LineKind, quotes: List<RefQuote>, source: String = "parlay", at: Long = old, subject: String? = null, stat: String? = null, book: String = "pinnacle") =
        RefBookMarket(bookKey = book, bookTitle = book, kind = kind, quotes = quotes, lastUpdateMs = at, subject = subject, stat = stat, source = source)

    private fun game(vararg m: RefBookMarket, home: String = "Brooklyn Nets", away: String = "Indiana Pacers") = RefEvent("e716595000a150bcd90bb50777b13ff5", "basketball_nba", now + 3_600_000, home, away, m.toList())

    @Test
    fun `a moneyline and a total move to the stream's price, dated when ParlayAPI last confirmed them`() {
        val e = game(
            market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null))),
            market(LineKind.TOTAL, listOf(RefQuote(Side.OVER, 1.9, 228.5), RefQuote(Side.UNDER, 1.9, 228.5))),
        )
        val out = live().overlayEvent(e, now)
        val ml = out.markets[0]
        assertEquals(3.24, ml.quotes[0].decimalOdds, 1e-9)
        assertEquals(1.0 + 100.0 / 265, ml.quotes[1].decimalOdds, 1e-9)
        // Pinnacle's price last MOVED at 07:27, but ParlayAPI saw it unchanged at 08:23: current as of then.
        assertEquals(1_792_916_218_124L, ml.lastUpdateMs)
        val total = out.markets[1]
        assertEquals(1.0 + 100.0 / 139, total.quotes[0].decimalOdds, 1e-9)
        assertEquals(2.19, total.quotes[1].decimalOdds, 1e-9)
    }

    @Test
    fun `other feeds' quotes, newer quotes and half-heard markets are left alone`() {
        val propLine = market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null)), source = "propline")
        val newer = market(LineKind.TOTAL, listOf(RefQuote(Side.OVER, 1.9, 228.5), RefQuote(Side.UNDER, 1.9, 228.5)), at = now)
        // Only Indiana's side of the -4.5 spread was in the stream: a two-way price is never half new.
        val spread = market(LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1.9, 4.5), RefQuote(Side.AWAY, 1.9, -4.5)))
        val e = game(propLine, newer, spread)
        val l = live()
        assertSame(e, l.overlayEvent(e, now))
        // Once the other side is heard, the spread moves.
        val other = JsonObject(rows[1].toMutableMap().apply { put("team", JsonPrimitive("Brooklyn Nets")); put("line", JsonPrimitive(4.5)); put("price", JsonPrimitive(140)) })
        l.onRow("basketball_nba", other, now)
        val moved = l.overlayEvent(game(spread), now).markets[0]
        assertEquals(2.4, moved.quotes[0].decimalOdds, 1e-9)
        assertEquals(1.0 + 100.0 / 167, moved.quotes[1].decimalOdds, 1e-9)
    }

    /** PB23 (2026-10-25): a whole run moved nothing, because the merged game is named and oriented by the first feed that matched it. The quotes' tags, made from ParlayAPI's own board, still find the stream. */
    @Test
    fun `a game renamed and flipped by the merge still takes the stream's prices`() {
        val board = ParlayLive.tag(game(market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null)))))
        val merged = board.flipped().copy(home = "Indiana", away = "Brooklyn")
        val ml = live().overlayEvent(merged, now).markets[0]
        // Flipped: HOME is now Indiana, whose stream price is -265.
        assertEquals(1.0 + 100.0 / 265, ml.quotes.first { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(3.24, ml.quotes.first { it.side == Side.AWAY }.decimalOdds, 1e-9)
        // Untagged, the same merged game finds nothing.
        val untagged = game(market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null)))).flipped().copy(home = "Indiana", away = "Brooklyn")
        assertEquals(1.4, live().overlayEvent(untagged, now).markets[0].quotes.first { it.side == Side.HOME }.decimalOdds, 1e-9)
    }

    @Test
    fun `team totals and player props match by team side and player name`() {
        val tt = market(LineKind.TEAM_TOTAL, listOf(RefQuote(Side.OVER, 1.9, 117.5), RefQuote(Side.UNDER, 1.9, 117.5)), subject = RefBookMarket.AWAY)
        assertEquals(2.17, live().overlayEvent(game(tt), now).markets[0].quotes[1].decimalOdds, 1e-9)
        val stat = ParlayMarkets.statOf("basketball_nba", "player_points", "Player Props")!!
        val prop = market(LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, 1.9, 27.5), RefQuote(Side.UNDER, 1.9, 27.5)), subject = "Anthony Edwards", stat = stat)
        val out = live().overlayEvent(game(prop, home = "Minnesota Timberwolves", away = "Toronto Raptors"), now).markets[0]
        assertEquals(1.0 + 100.0 / 128, out.quotes[0].decimalOdds, 1e-9)
        // A head-to-head "most threes" prop has no shape Vigilant prices: counted, never guessed.
        assertTrue(live().unread >= 1)
    }

    @Test
    fun `an unchanged price is current as of ParlayAPI's last poll while the stream has been connected since its stamp`() {
        val l = live()
        val m = market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null)), book = "fanduel")
        val e = game(m)
        // Not connected yet: nothing can be said.
        assertSame(e, l.overlayEvent(e, now))
        l.connected("basketball_nba", old - 1)
        l.onHealth(Json.parseToJsonElement("""{"generated_at_ms": $now, "sources": [{"source": "fanduel", "poll_age_s": 4.5, "prices_stale": false}, {"source": "bovada", "poll_age_s": 3, "prices_stale": true}]}""") as JsonObject, now)
        assertEquals(now - 4_500, l.overlayEvent(e, now).markets[0].lastUpdateMs)
        // A book ParlayAPI calls stale is never confirmed.
        val bov = game(market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null)), book = "bovada"))
        assertSame(bov, l.overlayEvent(bov, now))
        // A break in the stream since the quote's stamp: no confirmation.
        l.disconnected("basketball_nba"); l.connected("basketball_nba", old + 1)
        assertSame(e, l.overlayEvent(e, now))
    }

    /** PX10g: the stream re-sends a row only when its price moves; while it has been connected since that move and ParlayAPI polled the book after it, the stream's copy is current as of the poll. */
    @Test
    fun `a stream copy unchanged since its last move is dated by ParlayAPI's later poll`() {
        val e = game(market(LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 3.0, null), RefQuote(Side.AWAY, 1.4, null))))
        val plain = live().overlayEvent(e, now).markets[0].lastUpdateMs!!
        val l = live()
        l.connected("basketball_nba", old - 1)
        l.onHealth(Json.parseToJsonElement("""{"generated_at_ms": $now, "sources": [{"source": "pinnacle", "poll_age_s": 6, "prices_stale": false}]}""") as JsonObject, now)
        assertEquals("the stream copy (dated $plain) is current as of the poll", now - 6_000, l.overlayEvent(e, now).markets[0].lastUpdateMs)
        // connected only after the stream copy's stamp: no such claim
        val late = live()
        late.connected("basketball_nba", now - 1_000)
        late.onHealth(Json.parseToJsonElement("""{"generated_at_ms": $now, "sources": [{"source": "pinnacle", "poll_age_s": 6, "prices_stale": false}]}""") as JsonObject, now)
        assertEquals(plain, late.overlayEvent(e, now).markets[0].lastUpdateMs)
    }

    private fun row(extra: String) = json.parseToJsonElement(
        """{"bookmaker":"bovada","home_team":"Utah Jazz","away_team":"Los Angeles Lakers","sport_key":"basketball_nba","last_update":1792916303232,"kind":"game","market_key":"totals","line":238.0,"over_price":-110,"under_price":-110$extra}""",
    ) as JsonObject

    /** PX8: a row of a part of the game is never held under the full game's key (its 1st-half total at 119.5 would overwrite the game's 238), and a game total some books send as a "prop" is read. */
    @Test
    fun `rows of a part of the game are left out, and a game total sent as a prop with the matchup as its player is read`() {
        val l = ParlayLive { now }
        l.onRow("basketball_nba", row(""", "period_key": "1H" """), now)
        l.onRow("basketball_nba", row(""", "period_key": 1 """), now)
        l.onRow("basketball_nba", row(""", "pinnacle": {"market": {"period": 3}} """), now)
        l.onRow("basketball_nba", row(""", "period_key": "Q2" """), now)
        assertEquals(0, l.size())
        assertEquals(4L, l.unread)
        l.onRow("basketball_nba", row(""", "period_key": 0 """), now)
        assertEquals(2, l.size())
        // Bovada's and Fliff's game totals: kind "prop", the matchup as the "player".
        val b = ParlayLive { now }
        b.onRow("basketball_nba", row(""", "kind": "prop", "player": "Los Angeles Lakers @ Utah Jazz" """), now)
        assertEquals(2, b.size())
        // A real player's total (kind prop, a person) is a prop and is not mistaken for the game.
        val c = ParlayLive { now }
        c.onRow("basketball_nba", row(""", "kind": "prop", "player": "LeBron James", "market_key": "player_points" """), now)
        assertEquals(2, c.size())
        assertTrue(c.describe(now).contains("sides held"))
    }
}
