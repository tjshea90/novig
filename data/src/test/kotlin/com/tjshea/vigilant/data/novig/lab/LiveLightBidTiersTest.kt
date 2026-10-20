package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.livebid.LiveBidVerdict
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.novig.burst.LadderLine
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLightBidTiersTest {
    private val line = LadderLine("m", "e", LadderKind.TOTAL, "", 210.5, "m-o", "m-u", "Total 210.5", MarketFee.GAME)
    private fun side(fair: Double, ask: Double = 0.95) = LiveLightScan.SideFair(line, true, fair, ask, 100L, null)
    private val rules = LiveLightBidRules()

    @Test
    fun `a decided side is bid at the usual margin and a middle one a wider cushion under its fair`() {
        val top = LiveLightBids.judge(side(0.90), rules) as LiveBidVerdict.Post
        val mid = LiveLightBids.judge(side(0.72), rules) as LiveBidVerdict.Post
        assertTrue(top.price <= 0.90 / 1.07 + 1e-9)
        assertTrue(mid.price <= 0.72 / 1.15 + 1e-9)
        assertTrue(mid.ev >= 0.15 - 1e-9)
    }

    @Test
    fun `a side under the middle tier gets no bid`() {
        assertEquals(LiveBidVerdict.Skip("not decided enough to bid"), LiveLightBids.judge(side(0.60), rules))
    }
}
