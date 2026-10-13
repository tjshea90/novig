package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAgreementTest {
    private val t = 10_000_000_000L
    private fun ml(book: String, h: Double, a: Double) = RefBookMarket(book, book, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, h, null), RefQuote(Side.AWAY, a, null)), t)
    private fun ev(home: String, away: String, vararg m: RefBookMarket) = RefEvent("e-$home", "basketball_nba", t, home, away, m.toList())
    private fun snap(id: String, vararg e: RefEvent) = RefSnapshot("basketball_nba", e.toList(), t, provider = id)

    @Test fun `a feed that matches pinnacle scores near zero and a stale one scores worse, even with home and away swapped`() {
        val pin = ev("Boston Celtics", "New York Knicks", ml("pinnacle", 1.80, 2.10))
        val good = snap("good", ev("Boston Celtics", "New York Knicks", ml("draftkings", 1.80, 2.10)))
        val stale = snap("stale", ev("Boston Celtics", "New York Knicks", ml("draftkings", 2.10, 1.80)))
        val flipped = snap("flipped", ev("New York Knicks", "Boston Celtics", ml("draftkings", 2.10, 1.80)))
        val r = SourceAgreement.compute(mapOf("good" to listOf(good), "stale" to listOf(stale), "flipped" to listOf(flipped), "pinnacle" to listOf(snap("pinnacle", pin))), listOf(pin), setOf("draftkings"))
        assertEquals(0.0, r.getValue("good").meanPoints, 1e-9)
        assertEquals("swapped home/away is the same game", 0.0, r.getValue("flipped").meanPoints, 1e-9)
        assertTrue(r.getValue("stale").meanPoints > 10.0)
        assertFalse("pinnacle is not compared with itself", "pinnacle" in r)
    }

    @Test fun `books not asked for and games pinnacle lacks are ignored`() {
        val pin = ev("Boston Celtics", "New York Knicks", ml("pinnacle", 1.80, 2.10))
        val other = snap("x", ev("Boston Celtics", "New York Knicks", ml("bovada", 2.10, 1.80)), ev("Denver Nuggets", "Phoenix Suns", ml("draftkings", 1.5, 2.6)))
        assertTrue(SourceAgreement.compute(mapOf("x" to listOf(other)), listOf(pin), setOf("draftkings")).isEmpty())
    }
}
