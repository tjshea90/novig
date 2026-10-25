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
        l.onRow("basketball_nba", (json.parseToJsonElement(rows[1].toString().replace("\"Indiana Pacers\", \"line\"", "x")) as JsonObject).let { r ->
            JsonObject(r.toMutableMap().apply { put("team", kotlinx.serialization.json.JsonPrimitive("Brooklyn Nets")); put("line", kotlinx.serialization.json.JsonPrimitive(4.5)); put("price", kotlinx.serialization.json.JsonPrimitive(140)) })
        }, now)
        val moved = l.overlayEvent(game(spread), now).markets[0]
        assertEquals(2.4, moved.quotes[0].decimalOdds, 1e-9)
        assertEquals(1.0 + 100.0 / 167, moved.quotes[1].decimalOdds, 1e-9)
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
}
