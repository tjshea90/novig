package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import kotlinx.serialization.Serializable

/** How the +EV feed is ordered (OddsJam offers the same two). */
enum class FeedSort(val displayName: String) { EV("Best EV"), START("Soonest") }

/** Which Novig market families to price. */
enum class MarketFamily(val displayName: String, val novigTypes: List<String>) {
    MONEYLINE("Moneyline", listOf("MONEY")),
    SPREAD("Spread", listOf("SPREAD")),
    TOTAL("Total", listOf("TOTAL")),

    /**
     * 1st-half spreads and totals (the first 5 innings in baseball). 1st-half moneylines aren't
     * priced: the fair sources quote them 3-way with a tie, Novig 2-way (RESEARCH.md §13).
     */
    FIRST_HALF("1st half / F5", listOf("SPREAD_1H", "TOTAL_1H")),
    TEAM_TOTAL("Team totals", listOf("TEAM_TOTAL")),
    PLAYER_PROPS("Player props", PropStats.NOVIG_TYPES),
}

/**
 * Everything the user can tune, persisted as JSON. Nothing here triggers network on its own: the
 * app only fetches when Tj taps Scan or pulls to refresh (his rule, 2026-09-25).
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
    /** A fair line older than this is flagged stale in the UI. */
    val staleReferenceMinutes: Int = 30,
    // ---- Fair-odds sources (RESEARCH.md §11). Every fetch happens only on a manual scan. ----
    /** Pinnacle via pinnapi's free key (100 requests/day). Needs a key in Settings. */
    val usePinnacle: Boolean = true,
    /** Polymarket's public game markets. Free, no key. */
    val usePolymarket: Boolean = true,
    /** Kalshi's public game markets. Free, no key. */
    val useKalshi: Boolean = true,
    /** The Odds API (500 credits/month free). */
    val useOddsApi: Boolean = true,
    /** Re-use The Odds API's last odds for this long instead of paying credits on every scan. */
    val oddsApiReuseMinutes: Int = 15,
    /**
     * Spread and total lines priced per game (each). Every line is one Novig request per scan,
     * so this is the main lever on scan time and Novig's rate limit.
     */
    val linesPerGame: Int = 2,
    /** Player props priced per game, best-covered first (each one is a Novig request per scan). */
    val propsPerGame: Int = 4,
    /**
     * The most Novig prices one scan reads. Past it, main lines and the soonest games win; props
     * and later games wait. Keeps a big slate (college Saturday with props) to about a minute.
     */
    val maxBooksPerScan: Int = 200,
    /** Exchange quotes wider than this (ask − bid) are too thin to trust as a fair price. */
    val exchangeMaxSpread: Double = 0.03,
    val feedSort: FeedSort = FeedSort.EV,
    /** Settings format version, for one-time upgrades of a saved file ([migrate]). */
    val schema: Int = 0,
) {
    /**
     * Brings settings saved by an older version up to date. v0.6.0 (schema 2) made Polymarket and
     * Kalshi sharp by default; a saved v0.5 file still says Pinnacle only, so they're added once.
     */
    fun migrate(): ScanSettings {
        var s = this
        if (s.schema < 2) s = s.copy(sharpBooks = s.sharpBooks + setOf("polymarket", "kalshi"), schema = 2)
        // v0.8.0: alternative markets on by default; soccer, CFL, KBO and NPB are gone from the app.
        if (s.schema < 3) {
            val kept = s.leagues.filterTo(HashSet()) { Leagues.byNovigName(it) != null }
            s = s.copy(
                families = s.families + setOf(MarketFamily.FIRST_HALF, MarketFamily.TEAM_TOTAL, MarketFamily.PLAYER_PROPS),
                leagues = kept.ifEmpty { setOf("NFL") },
                schema = 3,
            )
        }
        return s
    }

    fun fairSettings(): FairSettings = FairSettings(
        source = fairSource,
        method = devigMethod,
        sharpBooks = sharpBooks,
        sharpWeight = sharpWeight.coerceIn(0.0, 1.0),
        fallbackToAverage = fallbackToAverage,
        minBooks = minBooks.coerceAtLeast(1),
    )

    val selectedLeagues: List<League> get() = Leagues.ALL.filter { it.novigName in leagues }

    /** [com.tjshea.vigilant.data.reference.ReferenceSource.id]s the user has switched on. */
    val enabledSources: Set<String>
        get() = buildSet {
            if (usePinnacle) add("pinnacle")
            if (usePolymarket) add("polymarket")
            if (useKalshi) add("kalshi")
            if (useOddsApi) add("oddsapi")
        }

    val novigMarketTypes: List<String> get() = families.flatMap { it.novigTypes }

    companion object {
        val ODDS_API_REUSE_CHOICES = listOf(0, 5, 15, 30, 60)
        val LINES_PER_GAME_CHOICES = listOf(1, 2, 3, 5)
        val PROPS_PER_GAME_CHOICES = listOf(0, 2, 4, 8, 12)
        val MAX_BOOKS_CHOICES = listOf(100, 200, 400)
        val KELLY_CHOICES = listOf(0.125, 0.25, 0.5, 1.0)
    }
}
