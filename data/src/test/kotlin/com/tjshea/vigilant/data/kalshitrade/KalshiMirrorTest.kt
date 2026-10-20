package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kalshi scan takes Novig's fair-odds switches and method, and NOTHING that moves money. */
class KalshiMirrorTest {
    @Test
    fun `the fair-odds side is Novig's and the money side is Kalshi's own`() {
        val kalshi = KalshiConfig().forScan().copy(autoBet = true, autoBetMinEv = 0.05, apiMaxStake = 3.0, maker = true, leagues = setOf("NHL"))
        val novig = ScanSettings(
            sgoPro = true, oddsPapi = true, usePropLine = false, referenceBooks = listOf("pinnacle", "circa"), sharpBooks = setOf("circa"), devigMethod = DevigMethod.POWER,
            autoBet = true, autoBetMinEv = 0.01, apiMaxStake = 99.0, maker = false, leagues = setOf("NFL"), autoBetStake = AutoBetStake.HALF_KELLY, useKalshi = true,
            gates = ScanSettings().gates.copy(cloudTrading = CloudTradeMode.LIVE, useBoltOdds = true, op4Fair = 77),
        )
        val m = KalshiMirror.fairOdds(kalshi, novig)
        // Fair-odds side: Novig's.
        assertTrue(m.sgoPro && m.oddsPapi && !m.usePropLine)
        assertEquals(listOf("pinnacle", "circa"), m.referenceBooks)
        assertEquals(setOf("circa"), m.sharpBooks)
        assertEquals(DevigMethod.POWER, m.devigMethod)
        assertTrue(m.gates.useBoltOdds)
        assertEquals(77, m.gates.op4Fair)
        // Money side: Kalshi's own, whatever Novig says.
        assertTrue(m.autoBet)
        assertEquals(0.05, m.autoBetMinEv, 0.0)
        assertEquals(3.0, m.apiMaxStake, 0.0)
        assertTrue(m.maker)
        assertEquals(setOf("NHL"), m.leagues)
        assertEquals(CloudTradeMode.OFF, m.gates.cloudTrading)
        // Kalshi is never one of its own fair feeds.
        assertFalse(m.useKalshi)
    }

    @Test
    fun `with no Novig settings loaded it still leaves Kalshi out of its own fair lines`() {
        assertFalse(KalshiMirror.fairOdds(ScanSettings(useKalshi = true), null).useKalshi)
    }
}
