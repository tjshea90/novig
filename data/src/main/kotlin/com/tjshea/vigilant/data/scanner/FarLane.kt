package com.tjshea.vigilant.data.scanner

/**
 * The quick scan between the full-reach ones (Tj, 2026-10-14: "The scan takes way too long"). With bids on, the scan reads ten days of games so the bids can fill the wallet from the whole week: about 7,700 Novig prices and
 * 8 minutes, by when the fair odds it read at its start are too old to bid on ("The fair price is too old": 5,370 lines in his v0.99.12 file) and the bids built during it fall away. So the BACKGROUND scan alternates: a
 * full-reach scan at most every [FAR_EVERY_MS], and in between a [NEAR_HOURS]-hour scan that takes a fraction of the time and keeps the nearer games' fair fresh. Bids on a game past a near scan's reach are left up
 * for their own expiry ([MakerPlan.plan]'s `reachUntilMs`), and the next full scan judges them again. Tj's own Scan tap, Check odds now and a scan with bids off are always full. Pure.
 */
object FarLane {
    /** The hours a quick scan reads. */
    const val NEAR_HOURS = 48

    /** The longest between two full-reach scans while the background scan runs: a far bid rests up to 30 minutes ([com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.DEPLOY_FAR_FAIR_MS]). */
    const val FAR_EVERY_MS = 15 * 60_000L

    /** Whether this scan is the quick one: background, bids on with the far reach in use, and a full scan was read less than [FAR_EVERY_MS] ago. */
    fun nearOnly(requested: ScanSettings, background: Boolean, now: Long, lastFullAtMs: Long): Boolean =
        background && requested.maker && requested.vigGuards.bidDeploy && requested.vigGuards.bidFarLane &&
            requested.daysAhead.coerceAtLeast(1) * 24 > NEAR_HOURS && lastFullAtMs > 0L && now - lastFullAtMs < FAR_EVERY_MS
}
