package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.Serializable

/**
 * Which venues the app runs (KV, Tj 2026-10-20: "I should be able to completely toggle on and off and switch between the novig portion of the app and the kalshi portion. I should also be able
 * to use both simultaneously"). A venue that is off has no tab, no scan, no orders, no calls. Novig is on by default (the app as it was); Kalshi is off until switched on.
 */
@Serializable
data class VenueMode(
    val novig: Boolean = true,
    val kalshi: Boolean = false,
    /** Set when turning Novig off paused its scanning (so turning it back on resumes only what the switch itself paused). */
    val novigPausedByVenue: Boolean = false,
) {
    val both: Boolean get() = novig && kalshi
}

/** The Kalshi auto-bet's mode: nothing, record what would be ordered, or order. */
@Serializable
enum class KalshiAutoMode { OFF, PAPER, LIVE }

/**
 * Everything the Kalshi side keeps in settings, separate from the Novig app's (its own file, its own [ScanSettings]): the scan's filters and money limits use the SAME settings type as Novig's
 * so every rule that already reads one (the feed, the auto-bet, the sliders) works unchanged, but none of Novig's values is read or written here.
 */
@Serializable
data class KalshiConfig(
    val scan: ScanSettings = defaultScan(),
    /** Orders by hand (the Bet button) need this: off, the key is only read with. */
    val tradingEnabled: Boolean = false,
    val autoMode: KalshiAutoMode = KalshiAutoMode.OFF,
    /** Scan again by itself while the app is open. */
    val autoScan: Boolean = true,
    val autoScanSeconds: Int = 90,
    val arbMinProfit: Double = 0.005,
    val arbIncludeRisky: Boolean = false,
    /** Place both legs of an arbitrage by itself (needs trading on; paper first). Off by default. */
    val arbAuto: KalshiAutoMode = KalshiAutoMode.OFF,
    /** The most payout dollars one arbitrage may take (each leg's cost is under it). */
    val arbMaxPayout: Double = 25.0,
) {
    companion object {
        /** The leagues Kalshi lists game series for. */
        val LEAGUES: Set<String> = Leagues.ALL.filter { it.kalshiSeries.isNotEmpty() }.map { it.novigName }.toSet()

        /** A fresh Kalshi scan's settings: the app's defaults for the leagues Kalshi lists, Kalshi itself not among its own fair lines, nothing placed by itself. */
        fun defaultScan(): ScanSettings = ScanSettings(
            leagues = setOf("NFL", "NBA", "NHL", "MLB").filter { it in LEAGUES }.toSet(),
            useKalshi = false,
            autoBet = false,
            apiMaxStake = 10.0,
            apiMaxPerDay = 50.0,
            apiMaxPerGame = 25.0,
            bankroll = 100.0,
        )
    }

    /** The scan settings with the auto switch following [autoMode] (the auto-bet pass reads `scan.autoBet`). */
    fun forScan(): ScanSettings = scan.copy(useKalshi = false, autoBet = autoMode != KalshiAutoMode.OFF)

    val arbSettings: ArbSettings get() = ArbSettings(minProfit = arbMinProfit, includeRisky = arbIncludeRisky)
}
