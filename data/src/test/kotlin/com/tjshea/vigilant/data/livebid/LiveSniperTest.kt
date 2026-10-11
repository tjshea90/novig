package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSniperTest {
    @Test fun `paper turns every engine on and none to real`() {
        val s = LiveSniper.apply(ScanSettings(), real = false)
        assertTrue(s.liveBid && s.pinnLive && s.tailLive.on && s.burstRecorder)
        assertFalse(s.liveBidReal || s.pinnLiveBet || s.tailLive.bet || s.burstTrade)
        assertTrue(LiveSniper.inForce(s)); assertFalse(LiveSniper.real(s))
    }

    @Test fun `real turns the taker, tail, bids and cover on for real, and off turns them all off`() {
        val s = LiveSniper.apply(ScanSettings(), real = true)
        assertTrue(s.liveBidReal && s.pinnLiveBet && s.tailLive.bet && s.burstTrade && LiveSniper.real(s))
        val off = LiveSniper.off(s)
        assertFalse(LiveSniper.real(off) || off.liveBid || off.pinnLive || off.tailLive.on || off.burstTrade)
    }

    @Test fun `the bids run at the sliders position, and 75 when it has none`() {
        assertEquals(LiveSniper.DEFAULT_LEVEL, LiveSniper.apply(ScanSettings(), false).liveBidQuality.level)
        val kept = ScanSettings(liveBidQuality = LiveBidSlider.apply(LiveBidQuality(), 30))
        assertEquals(30, LiveSniper.apply(kept, true).liveBidQuality.level)
    }

    @Test fun `his money limits never change`() {
        val lim = LiveBidLimits(maxStake = 7.0, haltLoss = 9.0)
        val s = ScanSettings(liveBidLimits = lim, burstTradeStake = 2.0, burstTradeMaxDay = 6.0)
        val on = LiveSniper.apply(s, true)
        assertEquals(7.0, on.liveBidLimits.maxStake, 0.0); assertEquals(9.0, on.liveBidLimits.haltLoss, 0.0)
        assertEquals(2.0, on.burstTradeStake, 0.0); assertEquals(6.0, on.burstTradeMaxDay, 0.0)
    }
}
