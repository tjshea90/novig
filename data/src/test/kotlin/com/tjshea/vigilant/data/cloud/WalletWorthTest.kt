package com.tjshea.vigilant.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TR3 (Tj 2026-11-18): the wallet's worth is cash plus every open position, not the trader's own bankroll. */
class WalletWorthTest {
    @Test fun worthIsCashPlusPositions() {
        val st = CloudStatus(walletCash = 36.47, walletEquity = 109.36, walletPositionsValue = 145.63, walletPnlAtMs = 1L)
        assertEquals(182.10, st.walletWorth!!, 0.001)
    }
    @Test fun unknownWithoutPositionsRead() {
        assertNull(CloudStatus(walletCash = 10.0, walletEquity = 50.0).walletWorth)
        assertNull(CloudStatus(walletCash = -1.0, walletPositionsValue = 5.0, walletPnlAtMs = 1L).walletWorth)
    }
}
