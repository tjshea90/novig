package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.live
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.money
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.parse
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.spread
import com.tjshea.vigilant.engine.DevigMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnBookTest {
    private fun book() = PinnBook(DevigMethod.MULTIPLICATIVE)

    private fun PinnBook.feed(text: String, at: Long) = apply(parse(text), at)

    @Test
    fun `an American price is the probability it implies, and a price that cannot be one is refused`() {
        assertEquals(0.6, PinnBook.impliedProbability(-150.0)!!, 1e-9)
        assertEquals(0.5, PinnBook.impliedProbability(100.0)!!, 1e-9)
        assertEquals(0.4, PinnBook.impliedProbability(150.0)!!, 1e-9)
        assertNull(PinnBook.impliedProbability(50.0))
        assertNull(PinnBook.impliedProbability(0.0))
    }

    @Test
    fun `a moneyline is devigged and its names, league and state are read`() {
        val b = book()
        val changes = b.feed(live(markets = arrayOf(money(10, -150, 125)), score = 40 to 38), 1_000)
        assertEquals(listOf(PinnChange.Kind.PRICE), changes.map { it.kind })
        val e = b.events.getValue(1)
        assertEquals("Oklahoma City Thunder", e.home)
        assertEquals("Milwaukee Bucks", e.away)
        assertEquals("NBA Preseason", e.league)
        assertTrue(e.live)
        assertEquals(40 to 38, e.score)
        val l = e.lines.getValue("s;0;m")
        assertTrue(l.open)
        assertEquals(PinnLineType.MONEYLINE, l.type)
        val raw = listOf(0.6, 100.0 / 225.0)
        assertEquals(raw[0] / raw.sum(), l.fair.getValue(PinnSide.HOME), 1e-9)
        assertEquals(raw.sum() - 1.0, l.overround, 1e-9)
        assertEquals(2000.0, l.maxRisk!!, 0.0)
        assertEquals(1_000L, l.changedAtMs)
    }

    @Test
    fun `a market re-sent with the same or an older version changes nothing (dedupe on markets version)`() {
        val b = book()
        b.feed(live(markets = arrayOf(money(10, -150, 125))), 1_000)
        assertTrue("same version, same price: nothing", b.feed(live(markets = arrayOf(money(10, -150, 125))), 2_000).isEmpty())
        assertTrue("an older version is ignored even with a different price", b.feed(live(markets = arrayOf(money(9, -300, 250))), 3_000).isEmpty())
        assertEquals(1_000L, b.events.getValue(1).lines.getValue("s;0;m").changedAtMs)
        assertTrue("a newer version with the same price does not move the price time", b.feed(live(markets = arrayOf(money(11, -150, 125))), 4_000).isEmpty())
        assertEquals(1_000L, b.events.getValue(1).lines.getValue("s;0;m").changedAtMs)
        assertEquals(1, b.feed(live(markets = arrayOf(money(12, -160, 135))), 5_000).size)
    }

    @Test
    fun `a danger zone frame is a volatility signal and never a price`() {
        val b = book()
        b.feed(live(markets = arrayOf(money(10, -150, 125))), 1_000)
        val changes = b.feed(live(channel = "dz", markets = arrayOf(money(11, -400, 330))), 2_000)
        assertEquals(listOf(PinnChange.Kind.VOLATILE), changes.map { it.kind })
        val e = b.events.getValue(1)
        assertEquals(-150.0, e.lines.getValue("s;0;m").american.getValue(PinnSide.HOME), 0.0)
        assertEquals(2_000L + PinnBook.VOLATILE_MS, e.volatileUntilMs)
    }

    @Test
    fun `a market that is not open closes its line, and reopening is a price change again`() {
        val b = book()
        b.feed(live(markets = arrayOf(money(10, -150, 125))), 1_000)
        val closed = b.feed(live(markets = arrayOf(money(11, -150, 125, status = "suspended"))), 2_000)
        assertEquals(listOf(PinnChange.Kind.CLOSED), closed.map { it.kind })
        assertFalse(b.events.getValue(1).lines.getValue("s;0;m").open)
        val reopened = b.feed(live(markets = arrayOf(money(12, -150, 125))), 3_000)
        assertEquals("the same price after a close is still news", listOf(PinnChange.Kind.PRICE), reopened.map { it.kind })
        assertTrue(b.events.getValue(1).lines.getValue("s;0;m").open)
        assertEquals(3_000L, b.events.getValue(1).lines.getValue("s;0;m").changedAtMs)
    }

    @Test
    fun `a period that is no longer open closes all of its markets, and a deleted matchup is dropped`() {
        val b = book()
        b.feed(live(markets = arrayOf(money(10, -150, 125), spread(10, -3.5, -110, -110))), 1_000)
        val closed = b.feed(live(periods = """[{"period":0,"status":"closed"}]""", markets = emptyArray()), 2_000)
        assertEquals(2, closed.count { it.kind == PinnChange.Kind.CLOSED })
        assertTrue(b.events.getValue(1).lines.values.none { it.open })
        val gone = b.feed(live(op = "del", markets = emptyArray()), 3_000)
        assertEquals(listOf(PinnChange.Kind.GONE), gone.map { it.kind })
        assertTrue(b.events.isEmpty())
    }

    @Test
    fun `spreads keep the home handicap and totals the line, and a wrong shape is not kept`() {
        val b = book()
        b.feed(live(markets = arrayOf(spread(1, -3.5, -105, -115), PinnTestFrames.total(1, 220.5, -110, -110), PinnTestFrames.total(2, 221.5, -120, 100, alt = true))), 1_000)
        val lines = b.events.getValue(1).lines
        assertEquals(-3.5, lines.getValue("s;0;s;-3.5").points!!, 0.0)
        assertEquals(220.5, lines.getValue("s;0;ou;220.5").points!!, 0.0)
        assertTrue(lines.getValue("s;0;ou;221.5").alternate)
        assertFalse(lines.getValue("s;0;s;-3.5").alternate)
        // A spread with no points on its home side is not a line.
        val bad = """{"key":"s;0;s;x","period":0,"status":"open","type":"spread","version":1,"prices":[{"designation":"home","price":-110},{"designation":"away","price":-110}]}"""
        b.feed(live(markets = arrayOf(bad)), 2_000)
        assertFalse(lines.containsKey("s;0;s;x"))
    }

    @Test
    fun `the move over a window is the fair now less the fair then`() {
        val b = book()
        b.feed(live(markets = arrayOf(money(1, -110, -110))), 0)
        b.feed(live(markets = arrayOf(money(2, -130, 110))), 10_000)
        val l = b.events.getValue(1).lines.getValue("s;0;m")
        val now = l.fair.getValue(PinnSide.HOME)
        assertEquals(now - 0.5, l.moveOver(PinnSide.HOME, 20_000, 12_000)!!, 1e-9)
        // Over a window that starts after the change there is no move.
        assertEquals(0.0, l.moveOver(PinnSide.HOME, 1_000, 12_000)!!, 1e-9)
        assertEquals(-(now - 0.5), l.moveOver(PinnSide.AWAY, 20_000, 12_000)!!, 1e-9)
    }

    @Test
    fun `a prematch update is kept as not live, and the older participant-id shape is not read`() {
        val b = book()
        b.feed(live(isLive = false, channel = "pre", markets = arrayOf(money(1, -150, 125))), 1_000)
        assertFalse(b.events.getValue(1).live)
        val old = """{"matchupId":1,"key":"s;0;m","period":0,"type":"moneyline","prices":[{"participantId":1,"price":-150},{"participantId":2,"price":125}]}"""
        val b2 = book()
        b2.feed(live(markets = arrayOf(old)), 1_000)
        assertTrue(b2.events.getValue(1).lines.isEmpty())
    }

    @Test
    fun `a Pinnacle special book on a match is not the game`() {
        val b = book()
        b.feed(live(units = "Corners", markets = arrayOf(money(1, -150, 125))), 1_000)
        assertFalse(b.events.getValue(1).regular)
    }

    // ---- the real frames captured from the feed ----------------------------------------------------------------------------------

    @Test
    fun `real basketball frames - the Pacers moneyline moved from -386 to -414 and the fair rose`() {
        val f = PinnTestFrames.real()
        val b = book()
        b.apply(f.getValue("sport3-before"), 1_000)
        val before = b.events.getValue(1637874622).lines.getValue("s;0;m")
        assertEquals(-386.0, before.american.getValue(PinnSide.HOME), 0.0)
        val fairBefore = before.fair.getValue(PinnSide.HOME)
        val changes = b.apply(f.getValue("sport3-after"), 6_000)
        assertTrue(changes.any { it.kind == PinnChange.Kind.PRICE && it.key == "s;0;m" })
        val e = b.events.getValue(1637874622)
        val after = e.lines.getValue("s;0;m")
        assertEquals(-414.0, after.american.getValue(PinnSide.HOME), 0.0)
        assertTrue(after.fair.getValue(PinnSide.HOME) > fairBefore)
        assertEquals("Indiana Pacers", e.home)
        assertTrue(e.live)
        assertNotNull("the score comes from the parent matchup for basketball", e.score)
        assertTrue(after.moveOver(PinnSide.HOME, 20_000, 7_000)!! > 0.0)
        // Every line kept is one of the three types and carries a fair that is a probability.
        assertTrue(e.lines.values.all { l -> l.fair.values.all { it > 0.0 && it < 1.0 } })
    }

    @Test
    fun `real frames of every sport parse into open lines with devigged fairs`() {
        val f = PinnTestFrames.real()
        for (sport in listOf("sport2", "sport3", "sport4", "sport6")) {
            val b = book()
            b.apply(f.getValue("$sport-before"), 1_000)
            b.apply(f.getValue("$sport-after"), 2_000)
            val e = b.events.values.single()
            assertTrue("$sport has a moneyline", e.lines.values.any { it.type == PinnLineType.MONEYLINE && it.open })
            assertTrue("$sport names both sides", e.home.isNotBlank() && e.away.isNotBlank())
            for (l in e.lines.values) {
                val sum = l.fair.values.sum()
                assertEquals("$sport ${l.key}: a multiplicative fair sums to one", 1.0, sum, 1e-9)
            }
        }
    }

    @Test
    fun `a real danger zone frame never changes a price, a real prematch frame is not live, a real snapshot fills the book`() {
        val f = PinnTestFrames.real()
        val b = book()
        val dz = b.apply(f.getValue("soccer-dz"), 1_000)
        assertTrue(dz.any { it.kind == PinnChange.Kind.VOLATILE })
        assertTrue(b.events.values.single().lines.isEmpty())
        val b2 = book()
        b2.apply(f.getValue("pre"), 1_000)
        assertTrue(b2.events.values.none { it.live })
        val b3 = book()
        b3.apply(f.getValue("snapshot-hockey"), 1_000)
        assertTrue(b3.events.isNotEmpty())
        assertTrue(b3.events.values.all { it.sportId == 4 })
    }
}
