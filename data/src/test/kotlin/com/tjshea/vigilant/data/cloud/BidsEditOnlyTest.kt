package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Bids tab's edits wait for 15 s of quiet before they go to GitHub together (Tj, 2026-10-23, CH74d); a mode switch or any other setting goes at once. */
class BidsEditOnlyTest {
    private val base = ScanSettings()

    @Test fun `a bid rule alone is an edit that waits`() = assertTrue(CloudScanKey.bidsEditOnly(base, base.copy(makerMargin = 0.03)))

    @Test fun `the slider changing bid rules only is an edit that waits`() = assertTrue(CloudScanKey.bidsEditOnly(base, base.copy(vigGuards = base.vigGuards.copy(bidLevel = 40))))

    @Test fun `the mode switch is sent at once`() = assertFalse(CloudScanKey.bidsEditOnly(base, base.copy(maker = true)))

    @Test fun `a bid rule with any other setting is sent at once`() = assertFalse(CloudScanKey.bidsEditOnly(base, base.copy(makerMargin = 0.03, minEvPercent = base.minEvPercent + 1)))

    @Test fun `no change is nothing to wait for`() = assertFalse(CloudScanKey.bidsEditOnly(base, base))
}
