package com.tjshea.vigilant.data.livebid

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull


class LiveBidSliderTest {
    private fun assertTrue(c: Boolean, m: String = "") = org.junit.Assert.assertTrue(m, c)

    private val all = (LiveBidSlider.MIN..LiveBidSlider.MAX).map { LiveBidSlider.apply(LiveBidQuality(), it) }

    @Test
    fun `the margin only ever falls as the slider moves toward volume`() {
        for (i in 1 until all.size) assertTrue(all[i].margin <= all[i - 1].margin + 1e-12, "margin rose at $i")
        assertEquals(0.09, all.first().margin, 1e-9)
        assertEquals(0.012, all.last().margin, 1e-9)
    }

    @Test
    fun `every position is internally consistent so a bid is never posted already below its own pull line`() {
        all.forEachIndexed { l, q ->
            assertTrue(q.pullBelowEv < q.margin, "pull line must sit under the posted edge at $l")
            assertTrue(q.pullBelowEv > 0.0, "pull line positive at $l")
            assertTrue(q.minFair < q.maxFair, "fair range at $l")
            assertTrue(q.minPrice < q.maxPrice, "price range at $l")
            assertTrue(q.minKeptEv in 0.0..0.02, "kept ev at $l")
            assertTrue(q.refreshBeforeSec <= q.ttlSec / 2, "refresh before ttl/2 at $l")
            assertTrue(q.pickOffLimit in 1..q.pickOffWindow, "pick-off limit within window at $l")
            assertEquals(l, q.level)
        }
    }

    @Test
    fun `the safe end is stricter than the volume end on every trust rule`() {
        val safe = all.first(); val vol = all.last()
        assertTrue(safe.minFair > vol.minFair)
        assertTrue(safe.maxOverround < vol.maxOverround)
        assertTrue(safe.minPinnLimit > vol.minPinnLimit)
        assertTrue(safe.settleSec > vol.settleSec)
        assertTrue(safe.scoreHoldSec > vol.scoreHoldSec)
        assertTrue(safe.coolOffSec > vol.coolOffSec)
        assertTrue(safe.minKeptEv > vol.minKeptEv)
        assertTrue(safe.neverLead && !vol.neverLead)
        assertTrue(!safe.bothSides && vol.bothSides)
    }

    @Test
    fun `apply keeps what the slider does not own`() {
        val base = LiveBidQuality(moneyline = false, spread = true, total = false, tennis = true, onlyLeagues = setOf("NBA"))
        val q = LiveBidSlider.apply(base, 70)
        assertEquals(false, q.moneyline); assertEquals(true, q.spread); assertEquals(false, q.total); assertEquals(true, q.tennis)
        assertEquals(setOf("NBA"), q.onlyLeagues); assertEquals(base.devig, q.devig)
    }

    @Test
    fun `out of range positions are clamped and nearest finds the position of a margin`() {
        assertEquals(0, LiveBidSlider.apply(LiveBidQuality(), -50).level)
        assertEquals(100, LiveBidSlider.apply(LiveBidQuality(), 500).level)
        assertEquals(40, LiveBidSlider.nearest(LiveBidQuality(margin = 0.05)))
        assertTrue(LiveBidSlider.STOPS.all { (l, _) -> LiveBidSlider.name(l) == LiveBidSlider.STOPS.first { it.first == l }.second })
    }

    @Test
    fun `describe names a custom setup and a slider one`() {
        assertTrue(LiveBidSlider.describe(LiveBidSlider.apply(LiveBidQuality(), 40)).first().startsWith("Steady"))
        assertTrue(LiveBidSlider.describe(LiveBidQuality(level = -1)).first().startsWith("Custom"))
    }

    @Test
    fun `kept edge halt waits for a full window and trips only on a low average`() {
        val q = LiveBidSlider.apply(LiveBidQuality(), 10)
        assertNull(LiveBidSlider.keptHalt(List(q.keptWindow - 1) { -0.05 }, q))
        assertNotNull(LiveBidSlider.keptHalt(List(q.keptWindow) { 0.0 }, q))
        assertNull(LiveBidSlider.keptHalt(List(q.keptWindow) { 0.05 }, q))
        assertNull(LiveBidSlider.keptHalt(List(q.keptWindow) { -0.2 }, LiveBidSlider.apply(LiveBidQuality(), 100).copy(minKeptEv = -1.0)))
    }
}
