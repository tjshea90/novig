package com.tjshea.vigilant.data.livebid
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Test
class TmpSweepTest {
    @Test fun sweep() {
        for (lvl in listOf(0, 25, 55, 75, 90, 100)) for ((bb, off) in listOf(0.45 to 0.52, 0.49 to 0.51, 0.495 to 0.505, null to null)) for (fair in listOf(0.5, 0.3, 0.1)) {
            val q = LiveBidSlider.apply(LiveBidQuality(), lvl)
            val v = LiveBidView(1_000_000L, null, true, true, true, fair, 0.06, 3000.0, 1_000L, 10_000L, null, null, bb, off, MarketFee.GAME)
            println("SWEEP $lvl bb=$bb off=$off fair=$fair -> ${LiveBidJudge.want(v, q)}")
        }
    }
}
