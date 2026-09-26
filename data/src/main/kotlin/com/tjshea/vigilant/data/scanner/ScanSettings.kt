package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import kotlinx.serialization.Serializable

/** How many prop types to request from the sportsbooks per game (each costs a credit). */
enum class BookPropSet(val displayName: String) { CORE("Core 4"), ALL("All") }

/** How the +EV feed is ordered (OddsJam offers the same two). */
enum class FeedSort(val displayName: String) { EV("Best EV"), START("Soonest") }

/** Which Novig market families to price. */
enum class MarketFamily(val displayName: String, val novigTypes: List<String>) {
    MONEYLINE("Moneyline", listOf("MONEY")),
    SPREAD("Spread", listOf("SPREAD")),
    TOTAL("Total", listOf("TOTAL")),

    /**
     * 1st-half spreads and totals (the first 5 innings in baseball), and baseball's 1st-inning
     * total (NRFI/YRFI). 1st-half moneylines aren't priced: the fair sources quote them 3-way with
     * a tie, Novig 2-way (RESEARCH.md §13).
     */
    FIRST_HALF("1st half / F5 / NRFI", listOf("SPREAD_1H", "TOTAL_1H", "FIRST_INNING_TOTAL")),
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
    /**
     * Player props priced per game, best-covered first (each one is a Novig request per scan).
     * 8 since v0.10.0 (was 4): props are where exchange prices lag most, and results now stream in.
     */
    val propsPerGame: Int = 8,
    /**
     * The most Novig prices one scan reads. Past it, main lines and the soonest games win; props
     * and later games wait. 300 since v0.10.0 (was 200): about a minute on public routes, with the
     * likeliest +EV lines read first and shown as they land.
     */
    val maxBooksPerScan: Int = 300,
    /**
     * Player props from the major sportsbooks (DraftKings, FanDuel, BetMGM, …) via The Odds API,
     * devigged book by book and averaged. Costs 1 credit per prop type per game.
     */
    val useBookProps: Boolean = true,
    /** Which prop types to buy from the books: the core four per sport, or every one Novig lists. */
    val bookPropSet: BookPropSet = BookPropSet.CORE,
    /** Most Odds API credits one scan may spend on sportsbook props. */
    val bookPropCreditsPerScan: Int = 24,
    /** Only games starting within this many hours get sportsbook props (soonest first). */
    val bookPropHours: Int = 24,
    /** Re-use sportsbook props for this long between scans. */
    val bookPropReuseMinutes: Int = 60,
    /** Exchange quotes wider than this (ask − bid) are too thin to trust as a fair price. */
    val exchangeMaxSpread: Double = 0.03,
    val feedSort: FeedSort = FeedSort.EV,
    /**
     * With 3+ books behind a fair price, use the lower of their mean and median per side, so one
     * stale book can't manufacture an edge (CrazyNinjaOdds' default, RESEARCH.md §16).
     */
    val outlierGuard: Boolean = true,
    /**
     * The feed hides prices longer than these American odds (0 = no limit). Devigging is least
     * reliable on longshots, which is where the biggest fake edges show up (RESEARCH.md §8.1, §16).
     */
    val maxOdds: Int = 1000,
    /**
     * Leaving Vigilant with a scan running or bets on the feed shrinks it to a floating
     * picture-in-picture window, so the results stay in view in Novig (Tj, 2026-09-26).
     */
    val miniWindow: Boolean = true,
    /**
     * CrazyNinjaOdds' +EV list in its own tab and the mini window (Tj, 2026-09-26; RESEARCH.md
     * §18). The one thing Vigilant reads without a tap: only while it or its mini window is on
     * screen, never more than once per 30 s, and never Novig or a keyed provider.
     */
    val cnoEnabled: Boolean = true,
    /** Tj's CNO Shared View link (his filters), as normalized by CnoView; blank = Novig, CNO's defaults. */
    val cnoViewUrl: String = "",
    /** Seconds between automatic CNO reads while on screen; 0 = only when tapped. */
    val cnoRefreshSeconds: Int = 60,
    /** What the mini window lists. */
    val miniSource: MiniSource = MiniSource.BOTH,
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
        // v0.10.0: wider coverage by default (results now stream in as they're priced). Only
        // values still at the old defaults move; anything Tj picked himself stays.
        if (s.schema < 4) {
            s = s.copy(
                propsPerGame = if (s.propsPerGame == 4) 8 else s.propsPerGame,
                maxBooksPerScan = if (s.maxBooksPerScan == 200) 300 else s.maxBooksPerScan,
                schema = 4,
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
        outlierGuard = outlierGuard,
    )

    /** Whether a price (cost per $1 payout) is within [maxOdds]. */
    fun withinMaxOdds(cost: Double): Boolean = maxOdds <= 0 || cost >= 100.0 / (100.0 + maxOdds) - 1e-9

    val selectedLeagues: List<League> get() = Leagues.ALL.filter { it.novigName in leagues }

    /** [com.tjshea.vigilant.data.reference.ReferenceSource.id]s the user has switched on. */
    val enabledSources: Set<String>
        get() = buildSet {
            if (usePinnacle) add("pinnacle")
            if (usePolymarket) add("polymarket")
            if (useKalshi) add("kalshi")
            if (useOddsApi) add("oddsapi")
            if (useOddsApi && useBookProps) add("oddsapi_props")
        }

    val novigMarketTypes: List<String> get() = families.flatMap { it.novigTypes }

    companion object {
        val ODDS_API_REUSE_CHOICES = listOf(0, 5, 15, 30, 60)
        val LINES_PER_GAME_CHOICES = listOf(1, 2, 3, 5)
        val PROPS_PER_GAME_CHOICES = listOf(0, 2, 4, 8, 12)
        val MAX_BOOKS_CHOICES = listOf(100, 200, 300, 400)
        val BOOK_PROP_CREDIT_CHOICES = listOf(0, 12, 24, 48, 96)
        val BOOK_PROP_HOURS_CHOICES = listOf(6, 12, 24, 48)
        val BOOK_PROP_REUSE_CHOICES = listOf(30, 60, 120, 240)
        val KELLY_CHOICES = listOf(0.125, 0.25, 0.5, 1.0)
        val MAX_ODDS_CHOICES = listOf(300, 500, 1000, 2000, 0)
    }
}
