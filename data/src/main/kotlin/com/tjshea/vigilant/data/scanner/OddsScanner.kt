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

    /**
     * The streaming engine's update (Tj, 2026-10-12): reads [sources] again (in-memory boards: no request), takes the Novig books the live feed holds for [marketIds] (null: every market the plan prices),
     * deepens the ones whose best price is +EV, and prices. No catalog read, no REST read. Null before the first scan (the catalog is the standing match of Novig's games to the feeds').
     */
    /** Whether a catalog is held (a scan has run): [streamUpdate] has something to price from. */
    fun streamReady(): Boolean = false

    /** Where the last streaming update's time went (reading the boards, matching, pricing, deepening), for Diagnostics; null when none ran. */
    fun streamTimings(): String? = null

    /** The same last streaming pass's phases in milliseconds (boards, match, price, deepen), for Diagnostics' timing percentiles; null before the first pass. */
    fun streamPhaseMs(): Map<String, Long>? = null

    /** Diagnostics "Source race": for each feed the scanner holds, how old its book quotes were when last read (median / 90th percentile), and its freshest and stalest books. */
    fun sourceRace(now: Long): List<String> = emptyList()

    /** Diagnostics "API ranking": the best-to-worst order the scan now asks its APIs in, with what each was measured to deliver; null before the first measurement. */
    fun sourceRanking(): String? = null

    suspend fun streamUpdate(settings: ScanSettings, sources: List<ReferenceSource>, marketIds: Collection<String>?): ScanResult? = null

    /**
     * The listed [rows] priced again from Novig's live top of book (the push feed, else a read of just those books), WITHOUT the scan's lock: a long scan holds the scanner for minutes and the list must not
     * wait for it to drop a bet that is no longer +EV (Tj, 2026-10-14). Returns only the rows that have a newer book; empty when there is nothing newer.
     */
    suspend fun requoteListed(settings: ScanSettings, rows: List<Opportunity>): List<Opportunity> = emptyList()

    /**
     * The engine's real-time list (PB19, [com.tjshea.vigilant.data.cloud.RealtimeList]): [rows] priced again from memory only, without the scan's lock (a scan may be running): each game's boards through
     * [overlay] (the live streams' pushes) and each side's fair odds worked out again ([Pricing.refair]), then Novig's price from the socket's held book where it is newer ([Pricing.requote]). Nothing is
     * requested from any API or from Novig.
     */
    fun liveReprice(settings: ScanSettings, rows: List<Opportunity>, overlay: (com.tjshea.vigilant.data.reference.RefEvent, Long) -> com.tjshea.vigilant.data.reference.RefEvent): List<Opportunity> = rows

    /** Re-price what's already fetched under new settings. No network. Null before the first scan. */
    suspend fun reprice(settings: ScanSettings): ScanResult?


    /** Leagues selected now that the last scan didn't load. */
    suspend fun unscannedLeagues(settings: ScanSettings): Set<String>

    /**
     * Vigilant left the screen: let go of what can never price again (fair-odds boards past the freshness limit) and what is cheap to make
     * again, so Android, which ends the biggest cached apps first, keeps this one (RESEARCH.md §63). Nothing when a scan holds the scanner.
     * Returns how many boards were dropped.
     */
    fun trimForBackground(): Int = 0
}
