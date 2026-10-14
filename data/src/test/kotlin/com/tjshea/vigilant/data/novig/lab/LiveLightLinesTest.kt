package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.novig.burst.LadderLine
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLightLinesTest {
    private fun total(id: String, t: Double) = LadderLine(id, "ev", LadderKind.TOTAL, "", t, "$id-o", "$id-u", "Total $t", MarketFee.GAME)
    private fun spread(id: String, ref: String, t: Double) = LadderLine(id, "ev", LadderKind.MARGIN, ref, t, "$id-y", "$id-n", "Spr $ref $t", MarketFee.GAME)
    private val e = EspnGame("Boston Celtics", "BOS", "Miami Heat", "MIA", 90, 80, true, 3, 432.0)
    private val state = GameState(TailSport.BASKETBALL, 90, 80, 0.40)

    @Test
    fun `without a score only the moneyline is watched`() {
        val ml = spread("ml", "Boston Celtics", 0.0)
        assertEquals(listOf(ml), LiveLightLines.window(listOf(ml, total("t1", 215.5), spread("s1", "Boston Celtics", -3.5)), "NBA", null, null))
    }

    @Test
    fun `lines near where the score says the game is going are watched nearest first, and lines far from it are not`() {
        // 170 points scored, 40% left: expected final total about 170 + 226 x .4 = 260, sd 19 x sqrt(.4) = 12.
        val lines = listOf(total("near", 258.5), total("mid", 240.5), total("tail", 225.5), total("far", 330.5), total("zero", 120.5), spread("ml", "Boston Celtics", 0.0), spread("s", "Boston Celtics", -10.5), spread("sfar", "Boston Celtics", -60.5))
        val w = LiveLightLines.window(lines, "NBA", state, e).map { it.marketId }
        assertEquals("ml", w.first())
        assertTrue("near" in w && "mid" in w && "tail" in w && "s" in w)
        assertTrue("far" !in w && "zero" !in w && "sfar" !in w)
        assertTrue(w.indexOf("near") < w.indexOf("mid"))
    }

    @Test
    fun `at most a fixed number of alternate lines are watched per game`() {
        val lines = (0 until 40).map { total("t$it", 250.5 + it) }
        assertEquals(LiveLightLines.MAX_PER_GAME, LiveLightLines.window(lines, "NBA", state, e).size)
    }
}
