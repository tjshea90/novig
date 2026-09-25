package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import kotlinx.serialization.Serializable

/** Which Novig market families to price. */
enum class MarketFamily(val displayName: String, val novigTypes: List<String>) {
    MONEYLINE("Moneyline", listOf("MONEY", "MONEYLINE_3_WAY_WIN", "MONEYLINE_3_WAY_DRAW")),
    SPREAD("Spread", listOf("SPREAD")),
    TOTAL("Total", listOf("TOTAL")),
}

/**
 * Everything the user can tune, persisted as JSON. Defaults are chosen for a free Odds API key
 * on a Moto G: one league, Novig books every 15s while the app is open, reference odds only on
 * pull-to-refresh (each sport refresh costs 3 credits of the free 500 a month).
 */
@Serializable
data class ScanSettings(
    val leagues: Set<String> = setOf("NFL"),
    val families: Set<MarketFamily> = MarketFamily.entries.toSet(),
    val fairSource: FairSource = FairSource.BLEND,
    val devigMethod: DevigMethod = DevigMethod.POWER,
    val sharpBooks: Set<String> = FairSettings.DEFAULT_SHARP_BOOKS,
    val sharpWeight: Double = 0.7,
    val fallbackToAverage: Boolean = true,
    val minBooks: Int = 2,
    val referenceBooks: List<String> = TheOddsApiClient.DEFAULT_BOOKMAKERS,
    /** The feed hides anything below this EV, as a fraction (0.01 = 1%). */
    val minEvPercent: Double = 0.01,
    /** Hide edges that look too good to be true (usually a stale or mismatched line). */
    val maxEvPercent: Double = 0.25,
    val includeLive: Boolean = false,
    val daysAhead: Int = 3,
    val bankroll: Double = 1000.0,
    val kellyMultiplier: Double = 0.25,
    /** How often Novig books refresh while the app is on screen. */
    val novigRefreshSeconds: Int = 15,
    /** 0 = only on pull-to-refresh. Otherwise re-pull reference odds this often while open. */
    val referenceRefreshMinutes: Int = 0,
    /** A fair line older than this is flagged stale in the UI. */
    val staleReferenceMinutes: Int = 30,
) {
    fun fairSettings(): FairSettings = FairSettings(
        source = fairSource,
        method = devigMethod,
        sharpBooks = sharpBooks,
        sharpWeight = sharpWeight.coerceIn(0.0, 1.0),
        fallbackToAverage = fallbackToAverage,
        minBooks = minBooks.coerceAtLeast(1),
    )

    val selectedLeagues: List<League> get() = Leagues.ALL.filter { it.novigName in leagues }

    val novigMarketTypes: List<String> get() = families.flatMap { it.novigTypes }

    companion object {
        val NOVIG_REFRESH_CHOICES = listOf(5, 10, 15, 30, 60)
        val REFERENCE_REFRESH_CHOICES = listOf(0, 5, 10, 15, 30, 60)
        val KELLY_CHOICES = listOf(0.125, 0.25, 0.5, 1.0)
    }
}
