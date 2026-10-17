package com.tjshea.vigilant.data.novig.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TCL97 (Tj, 2026-10-17: "there are many live games going on right now. it only lists 3"): the stream's 48 markets are shared by every live game, not taken whole by the first three. */
class LiveLightBudgetTest {
    @Test
    fun `few games keep every strike, many games share the budget evenly`() {
        assertEquals(LiveLightLines.MAX_PER_GAME, LiveLightLines.perGame(3, 48))
        // 20 live games on a 48-market budget: 1 moneyline + 1 strike each would be 40, so each keeps the minimum
        assertEquals(LiveLightLines.MIN_PER_GAME, LiveLightLines.perGame(20, 48))
        // 8 games: (48 / 8) - 1 = 5 strikes + the moneyline = 6 markets each = 48
        assertEquals(5, LiveLightLines.perGame(8, 48))
        assertTrue("never fewer than the minimum, never more than the full window", (1..60).all { n -> LiveLightLines.perGame(n, 48) in LiveLightLines.MIN_PER_GAME..LiveLightLines.MAX_PER_GAME })
        assertEquals("one game with nothing else on takes the full window", LiveLightLines.MAX_PER_GAME, LiveLightLines.perGame(1, 48))
    }
}
