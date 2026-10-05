package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.ReferenceSource

/**
 * A scan on Tj's tap, for whichever book the app prices: [Scanner] reads Novig's own books;
 * [com.tjshea.vigilant.data.book.SportsbookScanner] prices a sportsbook's posted odds (Vigilant MGM).
 * Both hand the screens the same [ScanResult].
 */
interface OddsScanner {
    suspend fun scan(
        settings: ScanSettings,
        sources: List<ReferenceSource>,
        /** Markets always priced (the ones Tj has open bets on). */
        pinned: Set<String> = emptySet(),
        onProgress: (ScanProgress) -> Unit = {},
        /** Everything priced so far, while the scan runs; the final result is the report's. */
        onPartial: (ScanResult) -> Unit = {},
    ): ScanReport

    /** Re-reads just [marketIds]' prices and re-prices. Null result before any scan. */
    suspend fun recheck(
        settings: ScanSettings,
        marketIds: Collection<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): RecheckReport

    /** Re-price what's already fetched under new settings. No network. Null before the first scan. */
    suspend fun reprice(settings: ScanSettings): ScanResult?

    /**
     * Re-reads the fair-odds boards of [leagues] (Novig names) from [offered] now, bypassing every re-use window the scanner has, then re-prices with them
     * and the books already read (Pinnacle only bets on a Pinnacle price this new: RESEARCH.md §88.5). Null when there is no scan to re-price or the scanner has no such
     * boards. Costs the requests of one fetch per league and source that answers.
     */
    suspend fun refreshFair(settings: ScanSettings, offered: List<ReferenceSource>, leagues: Set<String>): ScanResult? = null

    /** Leagues selected now that the last scan didn't load. */
    suspend fun unscannedLeagues(settings: ScanSettings): Set<String>

    /**
     * Vigilant left the screen: let go of what can never price again (fair-odds boards past the freshness limit) and what is cheap to make
     * again, so Android, which ends the biggest cached apps first, keeps this one (RESEARCH.md §63). Nothing when a scan holds the scanner.
     * Returns how many boards were dropped.
     */
    fun trimForBackground(): Int = 0
}
