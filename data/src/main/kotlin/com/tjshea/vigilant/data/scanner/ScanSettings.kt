package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.novig.NovigLinks
import com.tjshea.vigilant.data.novig.SlipStake
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** How many prop types to request from the sportsbooks per game (each costs a credit). */
enum class BookPropSet(val displayName: String) { CORE("Core 4"), ALL("All") }

/**
 * Which scanner runs (Tj, 2026-09-26). CNO only: CrazyNinjaOdds' list alone, with Vigilant's own
 * scan and every API behind it asleep (no Scan, no Novig or odds-API calls, their tabs hidden).
 * Vigilant only: CNO is never read. Both: both, and the mini window lists both by EV.
 */
enum class ScannerMode(val displayName: String) { BOTH("Both"), VIGILANT("Vigilant only"), CNO("CNO only") }

/**
 * What the sharp books do to a bet (Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement. Only skip a bet if the sharpest book for that market says it is
 * not +ev"): nothing, veto it when the sharpest book for its kind says it isn't +EV ([SharpVeto], the default), or require a fresh sharp quote that confirms
 * it ([SharpConfirm]: stricter, few bets: RESEARCH.md §64.3).
 */
enum class SharpMode(val displayName: String) { OFF("Off"), VETO("Veto"), CONFIRM("Require a confirmation") }

/**
 * Which sharp books can confirm a bet's +EV in [SharpMode.CONFIRM] ([ScanSettings.sharpAutoBet], [ScanSettings.sharpAlerts]; Tj, 2026-10-02: "at least one sharp
 * sports book (usually pinnacle)"). [codes] are CrazyNinjaOdds' column codes ([com.tjshea.vigilant.data.cno.CnoBooks]): PN = Pinnacle, CS = Circa.
 */
enum class SharpBookChoice(val displayName: String, val codes: Set<String>) {
    PINNACLE("Pinnacle", setOf("PN")),
    PINNACLE_CIRCA("Pinnacle or Circa", setOf("PN", "CS")),
}

/**
 * What runs in the background on a timer, with Vigilant closed or not (Tj, 2026-09-28: "an option to
 * auto scan either cno or both cno and vigilant every 5 10 20 30 or 40 minutes in the background,
 * even if the app is not open on the screen"). CNO: CrazyNinjaOdds' list and its best bets' books.
 * Both: that, then Vigilant's own scan (its API credits included).
 */
enum class AutoScanMode(val displayName: String) {
    OFF("Off"), CNO("CNO"), BOTH("CNO + Vigilant");

    val cno: Boolean get() = this != OFF
    val vigilant: Boolean get() = this == BOTH
}

/**
 * What the auto-bet stakes on each bet (Tj, 2026-10-01: "⅛ Kelly stake ¼ Kelly stake, ½ Kelly stake, $1 stake, or a manual amount i type in"). The
 * Kelly ones size the bet from his bankroll and the bet's own odds and edge ([com.tjshea.vigilant.data.novig.trading.AutoBet.stake]).
 */
enum class AutoBetStake(val label: String, val kelly: Double?) {
    SIXTEENTH_KELLY("1/16 Kelly", 0.0625), EIGHTH_KELLY("⅛ Kelly", 0.125), QUARTER_KELLY("¼ Kelly", 0.25), HALF_KELLY("½ Kelly", 0.5), ONE_DOLLAR("$1", null), CUSTOM("My amount", null)
}

/**
 * Which bids go up (Tj, 2026-10-05). [ALL]: every bid the Bids rules allow. [QUICK_LIKELY]: only bids with a real chance to win (no longshots) that sit where
 * takers actually fill them, with a sharp book behind the price, best chance first (RESEARCH.md §88.4; the numbers are [com.tjshea.vigilant.data.novig.trading.maker.QuickLikely]).
 */
enum class BidFocus(val displayName: String) {
    ALL("All bids"), QUICK_LIKELY("Quick & likely to win"),

    /**
     * Props only, in the next 6 hours, priced from 2-3 sharp prop books read at a slow pace (Tj, 2026-10-05; [LowUsageBids], RESEARCH.md §92): the fewest requests that
     * keep bids priced, at least 2.5% under the fair, no longer than +130, the likeliest to fill first.
     */
    LOW_USAGE("Low API usage"),
}

/**
 * Where bids get their fair price (Tj, 2026-10-07: "make auto bids using only the cno scanner with vigilant scanner turned off", DM3; RESEARCH.md §113-§114).
 * [VIGILANT]: Vigilant's own scan prices every bid (today's way). [CNO]: CrazyNinjaOdds' list and game pages price them, Novig's books are the offer and the
 * queue, and Vigilant's scan is not used for bids at all (it may be off). Never chosen by a preset or a restart.
 */
enum class BidSource(val displayName: String) { VIGILANT("Vigilant's scan"), CNO("CrazyNinjaOdds") }

/** How the +EV feed is ordered (OddsJam offers the same two). */
enum class FeedSort(val displayName: String) { EV("Best EV"), START("Soonest"), STARS("Stars") }

/** Which Novig market families to price. */
enum class MarketFamily(val displayName: String, val novigTypes: List<String>) {
    MONEYLINE("Moneyline", listOf("MONEY")),
    /** In tennis, the games spread and the sets spread (SET_SPREAD, ±1.5 sets). */
    SPREAD("Spread", listOf("SPREAD", "SET_SPREAD")),
    /** In tennis, total games and total sets (TOTAL_SETS, 2.5 sets). */
    TOTAL("Total", listOf("TOTAL", "TOTAL_SETS")),

    /**
     * 1st-half spreads and totals (the first 5 innings in baseball), baseball's 1st-inning total
     * (NRFI/YRFI), and a tennis match's 1st-set winner. 1st-half moneylines aren't priced: the fair
     * sources quote them 3-way with a tie, Novig 2-way (RESEARCH.md §13); a set can't tie.
     */
    FIRST_HALF("1st half / F5 / NRFI / 1st set", listOf("SPREAD_1H", "TOTAL_1H", "FIRST_INNING_TOTAL", "FIRST_SET_MONEYLINE")),
    /** Each team's own total; in tennis, each player's games won. */
    TEAM_TOTAL("Team totals", listOf("TEAM_TOTAL", "PLAYER_GAMES_WON")),
    PLAYER_PROPS("Player props", PropStats.NOVIG_TYPES),
}

/**
 * Everything the user can tune, persisted as JSON. Nothing here triggers network on its own: the
 * app only fetches when Tj taps Scan or pulls to refresh (his rule, 2026-09-25). The one
 * exception is CrazyNinjaOdds' list ([scanner]), kept current while on screen (Tj, 2026-09-26).
 */
@Serializable
data class ScanSettings(
    val leagues: Set<String> = setOf("NFL"),
    val families: Set<MarketFamily> = MarketFamily.entries.toSet(),
    val fairSource: FairSource = FairSource.BLEND,
    /** Worst case by default and by migration (Tj, 2026-10-11: "I want the app to use conservative devig methods to be safest"): each side's lowest fair chance of multiplicative, additive, power and Shin. */
    val devigMethod: DevigMethod = DevigMethod.WORST_CASE,
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
    /**
     * How far ahead a scan reads. 7 since v0.19.1 (was 3): at 3, a Monday scan missed every college game
     * and the next NFL Sunday (Tj, 2026-09-28: "There are way more than 7 total games for it to scan").
     * Fair-odds requests don't grow with it (one per league or sport); only Novig reads do, within the
     * per-scan budget, soonest games first.
     */
    val daysAhead: Int = 7,
    val bankroll: Double = 1000.0,
    val kellyMultiplier: Double = 0.25,
    /**
     * What Novig's bet slip opens with when a bet is tapped ([NovigLinks]; Tj, 2026-09-28): nothing (Novig's own
     * default), $1, the bet's Kelly stake, or [slipCustomStake].
     */
    val slipStake: SlipStake = SlipStake.OFF,
    /**
     * The dollars "My amount" ([SlipStake.CUSTOM]) fills in, and (Tj, 2026-10-07: "redundant … settings") the one amount the Bet sheet starts from when the choice has none for a bet
     * (Off, or Kelly with no edge at the price): it replaced the Bet sheet's own amount [apiBetStake], which said the same thing in a second place.
     */
    val slipCustomStake: Double = 5.0,
    /**
     * Betting through Novig's API (Tj, 2026-09-29): the amount the Bet sheet starts with, the most one bet may be, the most a day's API bets
     * may add up to (the device's day). A bet placed by hand has no minimum edge (Tj, 2026-10-02); `apiMinEv`, which once set one, was removed in v0.46.0
     * (an old settings file that still has it reads fine: unknown keys are ignored).
     */
    /** Retired in v0.71.0: [slipCustomStake] is the one amount ([migrate] carries a saved value over); nothing reads or shows this any more. */
    val apiBetStake: Double = 5.0,
    val apiMaxStake: Double = 10.0,
    val apiMaxPerDay: Double = 50.0,
    /**
     * The most that may be at risk on one game, every market of it together (open bets plus resting bids: [com.tjshea.vigilant.data.tracker.GameExposure];
     * Tj, 2026-10-04: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10 … if that one team loses badly, I lose
     * many bets due to one event"). The auto-bet and auto-make never take a game past it; a bet by hand is only warned. 0 = no limit. On by default:
     * $25 is 2.5× the default per-bet maximum, so two or three full bets fit on a game and the fourth line of the same team does not.
     */
    val apiMaxPerGame: Double = 25.0,
    /**
     * Auto-bet (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all, including … in the background as the cno scanner
     * is on in the background"): off by default. A background CNO auto-scan cycle places each CrazyNinjaOdds bet that passes these through Novig's
     * API from the Vigilant wallet, pregame only ([com.tjshea.vigilant.data.novig.trading.AutoBet], `app/AutoBettor`).
     */
    val autoBet: Boolean = false,
    /**
     * Tennis in the auto-bet (Tj, 2026-10-10: "I want the auto bid feature and auto bet features both to include all tennis ... Let me toggle tennis on and off"). CNO carries no tennis
     * (RESEARCH.md §85), so the auto-bet takes tennis from Vigilant's own scan ([com.tjshea.vigilant.app.AutoBettor.runTennis]); needs ATP/WTA among [leagues]. [withTennis] flips both.
     */
    val autoBetTennis: Boolean = true,
    /** The fewest books that must each say +EV on their own ([com.tjshea.vigilant.data.cno.CnoBooks.Check.agreeing]): 2, 3, 4 or 5. */
    val autoBetBooks: Int = 3,
    /** The smallest edge at Novig's price now: [AUTO_BET_MIN_EV_CHOICES], or what Tj typed (0.0325 = +3.25%). */
    val autoBetMinEv: Double = 0.03,
    /**
     * Every book scanned must agree (Tj, 2026-10-01: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books
     * agree positive EV)"): on top of [autoBetBooks], every book that prices both sides ([com.tjshea.vigilant.data.cno.CnoBooks.Check.twoSided]) must say
     * +EV on its own ([com.tjshea.vigilant.data.cno.CnoBooks.Check.agreeing] equal to it). Off by default.
     */
    val autoBetAllAgree: Boolean = false,
    /**
     * Sharp-book confirmation (Tj, 2026-10-02: "require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … fresh (within
     * the last few minutes) odds from the sharp book(s) devigged and compared to the current novig odds for the same exact bet"): on top of every other
     * criterion, the bet must be +EV against Novig's price now on a sharp book's own devigged two-sided price for the exact same line and side, no older
     * than [sharpConfirmMaxAgeSeconds]. That is [SharpMode.CONFIRM]; since 2026-10-02 17:01Z (Tj: "sharp veto instead of requirement") both default to
     * [SharpMode.VETO]: skip only when the sharpest book for the bet's kind says it isn't +EV ([SharpVeto], RESEARCH.md §66). One for the auto-bet, one for
     * CNO's push alerts. See [SharpConfirm] and `data/reference/SharpBooks` (RESEARCH.md §60).
     */
    val sharpAutoBet: SharpMode = SharpMode.VETO,
    val sharpAlerts: SharpMode = SharpMode.VETO,
    val sharpConfirmBooks: SharpBookChoice = SharpBookChoice.PINNACLE,
    /** How old a sharp book's quote may be ([SHARP_MAX_AGE_CHOICES]; never over the app's 5-minute limit, [Freshness.MAX_QUOTE_AGE_MS]). */
    val sharpConfirmMaxAgeSeconds: Int = 180,
    /** The edge the sharp book's own devigged price must show at Novig's price now: 0 = any +EV, or [SHARP_MIN_EV_CHOICES]. */
    val sharpConfirmMinEv: Double = 0.0,
    /**
     * Also take Pinnacle's price from CrazyNinjaOdds' game page (free, already read for each candidate; the page has a "Last Updated" for the whole page
     * but no time for a book's own quote). Off: only a Pinnacle feed (PinnWire, pinnapi, PropLine, ParlayAPI) with the quote's own time confirms.
     */
    val sharpConfirmViaCno: Boolean = false,
    /**
     * The sharp veto's bar (Tj, 2026-10-03: "avoiding bets that look like positive EV but are actually sharp bets on the other side and the rest of
     * the markets lag"; RESEARCH.md §72): with [SharpMode.VETO], the sharpest book for the bet's kind, devigged on its own, must give Novig's price at
     * least this edge or the bet is vetoed ([SharpVeto.judge]); a bid's own price must clear it too when the bids' sharp veto is on
     * ([com.tjshea.vigilant.data.novig.trading.maker.MakerRules.sharpMinEv]). 1% by default: across 48,394 soccer matches with Pinnacle's early and
     * closing prices, bets the consensus called +EV kept about the SHARP book's own edge at the close, not the consensus's (sharp 0-1%: +0.8% CLV,
     * not distinguishable from zero; 1-2%: +1.6%; 2-4%: +2.8%; 4%+: +5.7%). 0 = any +EV (the bar before v0.56.0). [SHARP_VETO_MIN_EV_CHOICES].
     */
    val sharpVetoMinEv: Double = SharpVeto.DEFAULT_MIN_EV,
    /** The fewest books that must price both sides of the bet ([com.tjshea.vigilant.data.cno.CnoBooks.Check.twoSided]): 1, 2 or 3. */
    val autoBetTwoSided: Int = 2,
    val autoBetStake: AutoBetStake = AutoBetStake.ONE_DOLLAR,
    /** The amount [AutoBetStake.CUSTOM] stakes. */
    val autoBetCustomStake: Double = 5.0,
    /** The most one auto-bet may stake, whatever the stake rule says. */
    val autoBetMaxStake: Double = 10.0,
    /**
     * The longest odds an auto-bet may take, American (Tj, 2026-10-01: "I don't want it to bet anything that is more of a longshot than +130"):
     * 130 = nothing over +130, favorites always pass; 0 = no limit (what it was before this existed). [AUTO_BET_MAX_ODDS_CHOICES], or typed.
     */
    val autoBetMaxOdds: Int = 0,
    /** The shortest odds an auto-bet may take, American (−200 = nothing shorter than −200; 0 = no limit): [AUTO_BET_MIN_ODDS_CHOICES]. RESEARCH.md §66. */
    val autoBetMinOdds: Int = 0,
    /**
     * How much MORE edge a favorite (odds shorter than even money, −101 or shorter) needs than [autoBetMinEv] (Tj, 2026-10-07, proposal 4 of the v0.70.1 analysis: "a higher EV bar for
     * favourites"; 0.01 = one point, from the study's price slope: closing edge fell as the price got shorter). 0 = the same bar for every price. [AUTO_BET_FAVOURITE_EV_CHOICES], or typed.
     */
    val autoBetFavouriteExtraEv: Double = 0.01,
    /**
     * The small-prop guard ([com.tjshea.vigilant.data.novig.trading.PropGuard]; Tj, 2026-10-07): the most one kind of player prop (league + stat: NHL shots on goal) may take of the last 24 hours'
     * auto-bets, once there are [propGuardMinSample] of them (0 = no share cap), and the most auto-bets on one kind of prop in one game ([propGuardPerGame]; 0 = no limit).
     */
    val propGuardShare: Double = 0.25,
    val propGuardMinSample: Int = 8,
    val propGuardPerGame: Int = 3,
    /** The kinds of bet the auto-bet places ([BetKind]); every kind by default. A preset narrows it (Tj's game totals lose to the close: RESEARCH.md §65). */
    val autoBetKinds: Set<BetKind> = BetKind.entries.toSet(),
    /**
     * Auto-lock (Tj, 2026-10-02 ~18:50Z: "include an option to auto bet these bets in addition to whatever the auto bet system already does"): with the
     * background scan, lock in a bet's profit by buying the other side of its Novig market once the lock pays at least [autoLockMinPercent] of what's
     * staked in that market whichever side wins ([com.tjshea.vigilant.data.novig.trading.LockIn], RESEARCH.md §67). Off by default. API bets only.
     * [autoLockLive]: also once the game is under way (the in-game fee is in the worst case; a line that can push isn't locked then).
     */
    val autoLock: Boolean = false,
    /** The Tracker's "Novig only" filter is on ([com.tjshea.vigilant.data.tracker.NovigNow]): kept, so the Tracker opens the way Tj left it. */
    val trackerNovigOnly: Boolean = false,
    /**
     * The Tracker leaves markets locked in out of its lists and stats ([com.tjshea.vigilant.data.tracker.LockedBets]; Tj, 2026-10-02 20:06Z: "It makes no
     * sense for me to track a bet that is already cashed out"). On unless he turns it off; the lock numbers still show what they made.
     */
    val trackerHideLocked: Boolean = true,
    /**
     * The scan study (Tj, 2026-10-03: "on every cno scan, the vigilant app saves logs on all kinds of information … the odds at the time of scan, type of bet,
     * percent EV, books that agree, time before the game begins … when those bets are final, it logs whether they won or lost and their closing line odds"):
     * every bet a CNO or Vigilant scan lists is logged ([com.tjshea.vigilant.data.study.ScanStudy]) and graded later, for Settings › Tools › Share scan study
     * with Claude. On unless he turns it off; it reads nothing of its own from Novig.
     */
    val scanStudy: Boolean = true,
    /**
     * The study's wide read (Tj, 2026-10-03: "log all cno finds on every scan … even if these bets don't meet my criteria for showing up in the list in the app. They should
     * still be hidden in the app but logged"): CNO's list read a second time with its filters opened up, every row logged and flagged shown or hidden, never shown in the app.
     * The one request the study makes of its own, at most every 30 s while the CNO list is being read. On unless he turns it off.
     */
    val scanStudyHidden: Boolean = true,
    val autoLockMinPercent: Double = 0.02,
    val autoLockLive: Boolean = true,
    /**
     * Make orders (Tj, 2026-10-03: "do deep research on how to do make orders on novig … build the system in the app"; RESEARCH.md §70): post bids
     * under Vigilant's fair price on Novig and let takers fill them ([com.tjshea.vigilant.data.novig.trading.maker.MakerDesk]). Off by default. Each bid
     * is post-only (it never takes) with an expiry ([makerTtlMinutes]), re-posted at the current fair after each scan while it's still worth it, and
     * cancelled when the fair moves against it, the game is about to start, or scanning is paused. Pregame only (Novig voids resting orders at the start).
     */
    val maker: Boolean = false,
    /** The EV at the fair each bid is posted at: fair / (1 + this), on Novig's grid ([MAKER_MARGIN_CHOICES]). 4% is §70's balance point. */
    val makerMargin: Double = 0.04,
    /**
     * How a bid is sized, like the auto-bet's stake rule ([autoBetStake]): ¼ Kelly by default (Benter's and the pros' fractional Kelly on the edge at
     * the fair, RESEARCH.md §69.1), on [bankroll], never over [makerMaxStake] or [apiMaxStake].
     */
    val makerStakeMode: AutoBetStake = AutoBetStake.QUARTER_KELLY,
    /** The dollars "My amount" ([AutoBetStake.CUSTOM]) bids. */
    val makerStake: Double = 5.0,
    /** The most one bid may cost, whatever the stake rule says (and never over [apiMaxStake]). */
    val makerMaxStake: Double = 10.0,
    /** Skip a bid that a sharp book in the fair, devigged on its own, says isn't +EV (the auto-bet's sharp veto). */
    val makerSharpVeto: Boolean = true,
    /**
     * Tj, 2026-10-04: "bids … with more attractive bets that involve bets that are more popular than obscure players props". When the most bids, the most
     * dollars or the wallet can't take every bid wanted, the ones on the kinds of market takers trade most (touchdowns, rushing attempts, pitcher outs, shots on goal:
     * [com.tjshea.vigilant.data.novig.trading.maker.MarketPopularity], Novig's own volume) go up before the obscure ones (longest reception, hits, assists), after the
     * ones that lead their side (RESEARCH.md §81.4). On by default: it only orders what is posted.
     */
    val makerPopularFirst: Boolean = true,
    /**
     * Tj, 2026-10-08: "leave the app on and background auto bid for hours … worried about api usage running out too fast … without sacrificing accuracy or quality of bids".
     * The long-run saver ([LongRunBids], RESEARCH.md §119): the background scan reads only what Quick & likely bids go on and waits 8 minutes, not 4, while every game is
     * far off. No bid rule changes. On by default.
     */
    val makerLongRun: Boolean = true,
    /**
     * Tj, 2026-10-04: "Maybe a sharp book should be required to agree on the positive EV". A bid is posted only when a sharp book (Pinnacle, Circa, the exchanges)
     * prices the line both ways and agrees: the veto ([makerSharpVeto]) stays a veto when one says no, and with this on, none pricing it is a no too. Off by
     * default: Tj's own bets with no sharp book on the page had a CLV of -1.9% (12 closes) against +3.5% (62) with one, but the study's props went the
     * other way on 6 closes; too thin to switch a rule (RESEARCH.md §81.4).
     */
    val makerRequireSharp: Boolean = false,
    /**
     * Tj, 2026-10-05: "make sure the math for the auto bid feature is sound and is getting me true positive EV bids placed because my bids right now are being taken
     * fast". A bid is priced the margin under the LOWER of Vigilant's blended fair and the sharpest book's own fair (each sharp book devigged the worst way),
     * not under the blend alone: the blend is 30% soft books that follow the sharp ones, and a fill is more likely exactly when a sharp book disagrees with it.
     * So the margin is a real edge against the book that moves first (RESEARCH.md §88.3). On by default; with no sharp book in the fair, nothing changes.
     */
    val makerAnchorSharp: Boolean = true,
    /**
     * The picked-off guard (RESEARCH.md §88.3): a bid that fills while the fair has already moved under its price is a bet the market was walking away from.
     * Each fill is judged against the fair on the next scan after it ([com.tjshea.vigilant.data.novig.trading.maker.MakerBid.fairAtFill]); when half or more of
     * the last [MAKER_GUARD_FILLS] fills were that, bids stop themselves ([makerHalted]) until Tj resumes them. On by default.
     */
    val makerGuard: Boolean = true,
    /** Why the picked-off guard stopped the bids (null = running). Set by the app, cleared by Tj's Resume on the Bids tab; not a setting he picks. */
    val makerHalted: String? = null,
    /** Fills before this time (epoch ms) aren't looked at by the guard: set to the moment Tj resumed, so the same fills can't stop the bids twice. */
    val makerGuardFromMs: Long = 0L,
    /**
     * Which bids go up (Tj, 2026-10-05: "only the bets which have the maximum chance of being filled quickly and also are decent chance for me to win the bet …
     * favorites and small underdogs … positive EV and the best chance at beating clv"): [BidFocus.ALL] (every bid the rules allow) or [BidFocus.QUICK_LIKELY].
     */
    val makerFocus: BidFocus = BidFocus.ALL,
    /** Where bids are priced from ([BidSource]; default Vigilant's scan). Only [BidSource.CNO] reads CrazyNinjaOdds for bids (see [bidsFromCno]). */
    val makerSource: BidSource = BidSource.VIGILANT,
    /**
     * CNO-priced bids only ([BidSource.CNO]): the oldest the data behind a bid's fair may be, in seconds, measured from the OLDER of the list's and the game page's own
     * "Last Updated" ([MAKER_CNO_MAX_AGE_CHOICES]). A bid is posted only inside it and comes down when its data goes past it (RESEARCH.md §113: CNO publishes every 13-33 s and its
     * "Last Updated" is a lower bound on a price's age, so 5 minutes, the app's rule for Vigilant's scan, is far too slow for a resting bid).
     */
    val makerCnoMaxAgeSeconds: Int = MAKER_CNO_MAX_AGE_DEFAULT,
    /**
     * Low API usage ([BidFocus.LOW_USAGE]): the 2-3 sharp prop books the fair is built from ([LowUsageBids.books] makes sure of two or three), how often Vigilant's own
     * scan runs while it is on ([lowUsagePace]: [LowUsageBids.AUTO] or minutes, [LowUsageBids.PACE_CHOICES]), and the margin each bid is posted under the fair (never under
     * [LowUsageBids.MIN_MARGIN]). [lowUsagePace] replaced `lowUsageMinutes` (v0.68.1): a saved 10 (the old default, which left the bids down half the time) is not carried over.
     */
    val lowUsageBooks: Set<String> = LowUsageBids.DEFAULT_BOOKS,
    val lowUsagePace: Int = LowUsageBids.AUTO,
    val lowUsageMargin: Double = LowUsageBids.DEFAULT_MARGIN,
    /**
     * The live burst recorder (Tj, 2026-10-06; RESEARCH.md §95): while a live game of [burstLeagues] is on Novig, the read key's websocket records the cross-line mispricings after plays and
     * paper-trades them at Tj's own measured delays. It never places or cancels anything. Off by default; STOP ALL stops it.
     */
    val burstRecorder: Boolean = false,
    /**
     * The live feed test (Tj, 2026-10-07: "test all available sources that can be used as a rapid source of odds or scores"; RESEARCH.md §106): while a game is live on Novig it holds the free
     * score and odds feeds of it open, stamps each reading, and says which was first and whether any beat Novig's price ([com.tjshea.vigilant.data.live.FeedRaceRunner]). Public reads only: it
     * places nothing and has no way to. Off by default; STOP ALL stops it.
     */
    val feedRace: Boolean = false,
    /**
     * The paper lab (Tj, 2026-10-09: "Build all three. This app is in 100% research and development"; RESEARCH.md §120.6): while a game is live on Novig it reads the totals, spread and moneyline books and writes down
     * would-be bets (ladder covers, late-game tail strikes, alternate lines against Pinnacle's) and grades them from the settled markets ([com.tjshea.vigilant.data.novig.lab.LabRecorder]). It places nothing and
     * is given no way to. Off by default; STOP ALL stops it.
     */
    val altLab: Boolean = false,
    /**
     * SportsGameOdds Pro (Tj, 2026-10-09: "take full advantage of the sportsgamesodds pro API ... redundant apis that do the same thing should be turned off"; SPORTSGAMEODDS_API.md): the one feed that
     * carries Pinnacle, Circa, bet365 and the US books, game lines, alternates, 1st-half lines and props, each with its update time. While it is on (and a key is saved) the scan, the bid desk, the
     * open-bet pricing and the closing lines read it, and the paid feeds that sell the same thing (PinnWire/pinnapi, PropLine, The Odds API, ParlayAPI's odds/props/halves) are not called for the
     * leagues it carries. Off by default.
     */
    val sgoPro: Boolean = false,
    /** SGO Pro: also price the fair line with the books Vigilant's list lacks (Circa, SuperBook, bet365: [SgoBooks.EXTRA]). */
    val sgoExtraBooks: Boolean = true,
    /** SGO Pro: ask for every book's alternate lines with the game lines (heavier replies; dropped for a league whose query times out). */
    val sgoAltLines: Boolean = true,
    /** Only notifications about money: a bet placed, a bid filled (Tj, 2026-10-09: "turn all notifications off except notifications of actual money bet or bids filled"). The running-service and open-bids notice stays: Android requires it. */
    val quietNotifications: Boolean = false,
    /** SGO Pro: the odds-age guard in minutes (the app drops a quote older than this): 10 as Tj set it ([SGO_MAX_AGE_CHOICES]); SGO's props refresh about every 10 minutes, so a longer guard leaves fewer gaps between bids. */
    val sgoMaxAgeMinutes: Int = 10,
    /**
     * OddsPapi v5 (Tj, 2026-10-09: "the next best api ... a toggle ... with it on use it to full power everywhere"; ODDSPAPI_API.md): sharp and US books with each price's own change time. While it is on (and
     * a key is saved and answering) the scan, the bid desk, open-bet pricing, closing lines (CLV for any bet) and grading read it, and the feeds that sell the same thing (PinnWire/pinnapi, PropLine, The
     * Odds API, ParlayAPI, and SGO's own sources when SGO Pro is off) rest for the leagues it carries; the free feeds and Pinnodds stay. Off by default; off, the app is exactly what it was.
     */
    val oddsPapi: Boolean = false,
    /**
     * Leave a quote out of every fair line when its two sides add up to too much (a book above 12% hold, an exchange above 10%: [com.tjshea.vigilant.engine.WideQuotes]); Tj, 2026-10-09: ProphetX at
     * -140 / -113 on a total (11.4%) was counted as a view of the price. On by default.
     */
    val ignoreWideQuotes: Boolean = true,
    /** Tj's 2026-10-11 scanner guards and the auto-bet's Vigilant lane, in one field (ScanSettings is near the JVM's 255-argument limit: [ScanSettingsSizeTest]). */
    val vigGuards: ScanGuards = ScanGuards(),
    /** OddsPapi: also price the fair line with the books Vigilant's list lacks (Circa, SuperBook, bet365: [com.tjshea.vigilant.data.reference.OpBooks.EXTRA]). */
    val opExtraBooks: Boolean = true,
    /** OddsPapi: read every game in depth for its alternate lines (one request a game); off, the main lines of a whole tournament come in one request. */
    val opAltLines: Boolean = true,
    /**
     * Unabated Free API (Tj, 2026-10-11; UNABATED_API.md): DraftKings, FanDuel, MGM, Caesars, theScore and Fanatics' lines and props, 15 s delayed, one request per 5 s, no credits. On (and a key saved and
     * answering) it joins the scan LAST in the source order, so it prices only the lines no better feed sent (a fallback by line), and nothing else rests for it. Off by default; off, the app is what it was.
     */
    val unabated: Boolean = false,
    /**
     * OddsPapi Free (v4, Tj's free key; ODDSPAPI_API.md addendum 2026-10-11): the Pinnacle closing line for CLV from its free historical odds, last in the close-source order. A different product from [oddsPapi]
     * (v5, B2B trial). Off by default.
     */
    val oddsPapiFree: Boolean = false,
    /** The newer per-API switches and OddsPapi's every-book mode, in one group (a settings class this wide runs out of JVM argument slots: [ApiGates]). */
    val gates: ApiGates = ApiGates(),
    /** Safety nets that take orders down by themselves ([Guards]). */
    val guards: Guards = Guards(),
    /**
     * Research mode (Tj, 2026-10-09: "just make it simple for me to run"): ONE switch that turns on every recorder that places nothing: the live feed test, the burst recorder, the paper lab, the paper bid lab
     * and (with a Pinnodds key saved) Pinnodds live in PAPER mode. It never turns on a real-money feature. Off by default; STOP ALL stops it.
     */
    val researchMode: Boolean = false,
    val burstLeagues: Set<String> = BURST_LEAGUES.toSet(),
    /**
     * The real-money burst trader (Tj, 2026-10-06: "make it good enough so that if it is proven I can just turn it on for actual money betting"; RESEARCH.md §95): OFF by default, and
     * it places nothing until the recorder's own verdict says its windows are worth a test on this phone ([com.tjshea.vigilant.data.novig.burst.BurstStudy.proof]). It buys both legs of a
     * cover at once as two immediate-or-cancel orders, at most [burstTradeStake] dollars a leg, [burstTradeMaxGame] a game and [burstTradeMaxDay] a day (fees in). Legs held alone
     * ([burstTradeHaltLoss] dollars of them in a day, or two covers in a row) halt it: [burstTradeHalted] says why until Tj resumes it. STOP ALL stops it like everything else.
     */
    val burstTrade: Boolean = false,
    val burstTradeStake: Double = 1.0,
    val burstTradeMaxGame: Double = 5.0,
    val burstTradeMaxDay: Double = 10.0,
    val burstTradeHaltLoss: Double = 3.0,
    val burstTradeHalted: String? = null,
    /**
     * Pinnodds live (Tj, 2026-10-08; RESEARCH.md §116): Pinnacle's live price on the Pinnodds WebSocket, devigged, against Novig's live books. [pinnLive] switches the feed on: it reads, judges and
     * journals what it WOULD bet (paper) and sends nothing. [pinnLiveBet] makes it bet for real: one immediate-or-cancel order at Novig's ask when the price lags Pinnacle's fair by at least
     * [pinnLiveMinEv] after Novig's fee AND Pinnacle's fair for that side just rose by at least [pinnLiveMinMove] (a lag, not a standing disagreement). At most [pinnLiveStake] dollars a bet,
     * [pinnLiveMaxGame] a game, [pinnLiveMaxDay] a day; a day's settled loss of [pinnLiveHaltLoss] halts it ([pinnLiveHalted] says why until Tj resumes it). STOP ALL stops it.
     */
    val pinnLive: Boolean = false,
    val pinnLiveBet: Boolean = false,
    val pinnLiveStake: Double = 2.0,
    val pinnLiveMaxGame: Double = 5.0,
    val pinnLiveMaxDay: Double = 25.0,
    val pinnLiveHaltLoss: Double = 10.0,
    val pinnLiveHalted: String? = null,
    /** The defaults come from replaying the rule over 52 minutes of tape (RESEARCH.md §116): score-driven moves of 3+ points with 5%+ edge held up two minutes later (+6.2%, n=20); price-only moves did not (-2.7%, n=24). */
    val pinnLiveMinEv: Double = 0.05,
    val pinnLiveMinMove: Double = 0.03,
    val pinnLiveDevig: DevigMethod = DevigMethod.WORST_CASE,
    /** What counts as a lag ([com.tjshea.vigilant.data.pinnodds.LiveTrigger]): a score-driven Pinnacle move (default), any move, or any steady edge. */
    val pinnLiveTrigger: com.tjshea.vigilant.data.pinnodds.LiveTrigger = com.tjshea.vigilant.data.pinnodds.LiveTrigger.SCORE,
    /** Also act on prematch lines (Pinnacle's push moves against Novig's pregame quotes; Novig charges no taker fee before the game starts). Off by default. */
    val pinnLivePregame: Boolean = false,
    /** No live bet for this many seconds after the game's score changes (0 = off): Novig pauses live betting after a score. [PINN_HOLDOFF_CHOICES]. */
    val pinnLiveHoldoffSeconds: Int = 0,
    /**
     * Live bids (Tj, 2026-10-10: "Build a live bid feature for live betting on novig … strong safeguards … presets I can save and manual fields"; RESEARCH.md §123-§124): post-only bids on Novig's live
     * lines, priced under Pinnacle's live fair on the Pinnodds socket, resting seconds at a time and pulled the moment anything that justified them changes. [liveBid] switches the engine on: it judges,
     * journals and follows up what it WOULD bid (paper) and sends nothing. [liveBidReal] makes it bid with real money, sized by [liveBidLimits] (⅛ Kelly by default) and held to them. The rules are
     * [liveBidQuality] (a preset sets them, [liveBidPresetName] says which); [liveBidHalted] says why it stopped until Tj resumes it. STOP ALL and a crash switch both off.
     */
    val liveBid: Boolean = false,
    val liveBidReal: Boolean = false,
    val liveBidHalted: String? = null,
    val liveBidQuality: com.tjshea.vigilant.data.livebid.LiveBidQuality = com.tjshea.vigilant.data.livebid.LiveBidQuality(),
    val liveBidLimits: com.tjshea.vigilant.data.livebid.LiveBidLimits = com.tjshea.vigilant.data.livebid.LiveBidLimits(),
    val liveBidPresetName: String? = "Balanced",
    val liveBidPresets: List<com.tjshea.vigilant.data.livebid.SavedLiveBidPreset> = emptyList(),
    /** Live tail bets ([TailLiveSettings]; RESEARCH.md §125). */
    val tailLive: TailLiveSettings = TailLiveSettings(),
    /** The Pinnacle website feed switch and its pace ([PinnWebsiteSettings]; RESEARCH.md §127). */
    val pinnWebsite: PinnWebsiteSettings = PinnWebsiteSettings(),
    /**
     * Set only by [effective]: these settings are the low-usage scan's ([LowUsageBids.profile]). The scanner and the feeds read it (quotes past the freshness limit are
     * dropped before the devig, a league with no game in the window isn't asked); never saved, so a saved file can't switch it on.
     */
    @Transient val lowUsageScan: Boolean = false,
    /** Set only by the background scan ([LongRunBids.leanApplies]) and read by [effective]: this scan is the lean one. Never saved. */
    @Transient val leanScan: Boolean = false,
    /** Set only by [effective]: the fewest sharp books a sharp fair is built from ([com.tjshea.vigilant.engine.FairSettings.minSharp]). Never saved. */
    @Transient val minSharpBooks: Int = 1,
    /**
     * With auto-make off, recommend bids to approve or deny (Tj, 2026-10-03: "recommend bets to make and I manually approve or deny them"): the Bids
     * tab's list, and a notification for each new one (at most a few a cycle) with Approve and Deny.
     */
    val makerRecommend: Boolean = true,
    /** The most bids resting at once ([MAKER_MAX_BIDS_CHOICES]). */
    val makerMaxBids: Int = 20,
    /** The most dollars resting at once, all bids together ([MAKER_MAX_DOLLARS_CHOICES]): Novig needs the wallet to cover each bid. */
    val makerMaxDollars: Double = 100.0,
    /** The kinds of bet it posts on: props, 1st-half/inning lines and team totals by default; game lines only with a fair that leads Novig (§70.2). */
    val makerKinds: Set<BetKind> = MAKER_DEFAULT_KINDS,
    /** How long each bid may rest before Novig cancels it by itself (`ttl`, [MAKER_TTL_CHOICES]): the safety net if the phone stops checking. */
    val makerTtlMinutes: Int = 30,
    /** No bid rests closer to the start than this ([MAKER_STOP_CHOICES]). */
    val makerStopMinutes: Int = 15,
    /** The bid's price window (probability; 0.10 = +900, 0.65 = −186): favorites over 0.65 almost never fill (§70.2). */
    val makerMinPrice: Double = 0.10,
    val makerMaxPrice: Double = 0.65,
    /**
     * The longest odds a bid may be posted at, American (Tj, 2026-10-05: "do not post bids longer than +140 odds"): 140 = no bid priced under 41.7¢ (a bid at +141 or longer
     * is skipped, and one already up at such a price comes down at the next pass); favorites always pass; 0 = no limit (the price window above is all there is). The
     * auto-bet's [autoBetMaxOdds] is the same idea for bets taken. [MAKER_MAX_ODDS_CHOICES], or typed.
     */
    val makerMaxOdds: Int = 0,
    /**
     * The shortest odds a bid may be posted at, American (Tj, 2026-10-07: "make a shortest odds setting as well"): −200 = no bid priced over 66.7¢ (nothing shorter than −200),
     * +110 = underdogs at least that long only; 0 = no limit. [makerMaxOdds] is the longest. [MAKER_MIN_ODDS_CHOICES], or typed.
     */
    val makerMinOdds: Int = 0,
    /**
     * Quick & likely to win bids go only where takers are (Tj, 2026-10-07: "no strange props or small markets"): a line priced by fewer books than this is a small market (a backup
     * player's prop, an alternate line) and gets no bid. [MAKER_QUICK_MIN_BOOKS_CHOICES], or typed; 5 by default (a quarter of the study's props were priced by 5 books or fewer).
     */
    val makerQuickMinBooks: Int = 5,
    /**
     * Fill the money the popular bids leave idle with small markets too, under strict safeguards (Tj, 2026-10-07: "If auto bid feature can't find enough bids that are popular, include
     * obscure bids as well, up to the max amount of money that I selected or that is in the wallet. But prioritize the bids, popular large markets most likely to get a taker first, then
     * if there is room, obscure bids. But there must be strict safeguards on obscure bids, such as sharp markets must agree and/or the positive EV must be a good margin"). Only with
     * "Quick & likely" ([QuickLikely]); the popular bids always go up first and a small-market bid comes down to make room for one ([MakerPlan.MADE_ROOM]).
     */
    val makerObscureFill: Boolean = true,
    /** A small-market bid is posted at least this far under the fair (never under the normal [makerMargin]): a wider margin pays for the thinner market. [MAKER_OBSCURE_MARGIN_CHOICES], or typed. */
    val makerObscureMargin: Double = 0.06,
    /** Every sharp book's own fair must give a small-market bid at least this edge on its own (the normal sharp veto is [sharpVetoMinEv]). [MAKER_OBSCURE_SHARP_MIN_EV_CHOICES], or typed. */
    val makerObscureSharpMinEv: Double = 0.03,
    /** The sharp books' fairs and Vigilant's blend must sit within this many probability points of each other for a small-market bid ("sharp markets must agree"). */
    val makerObscureAgreePoints: Double = 0.02,
    /** A small-market line needs at least this many books pricing it (a line one or two books price never gets a bid). */
    val makerObscureMinBooks: Int = 3,
    /** A small-market bid stakes this share of what a popular bid would (0.5 = half). */
    val makerObscureStake: Double = 0.5,
    /** Bid both sides of a market (both filling locks in the two margins) or only the side with the better EV per bid. */
    val makerBothSides: Boolean = true,
    /** The fewest books behind the fair price for a bid to be posted. */
    val makerMinBooks: Int = 2,
    /**
     * The trap guard's first rule (Tj, 2026-10-03: "find these trap bets and avoid them"; RESEARCH.md §71, [TrapGuard.early]): the auto-bet, the +EV
     * alerts and the bids leave alone any game starting more than this many hours from now ([TrapGuard.EARLY_CHOICES]; 0 = off). 6 h by default: Tj's
     * own bets placed earlier than that lost to the close. The lists still show them (each card says so).
     */
    val trapEarlyHours: Int = TrapGuard.DEFAULT_EARLY_HOURS,
    /**
     * The same rule for the BIDS on their own (Tj, 2026-10-10: "separate the auto bid and auto bet trap guards"): [trapEarlyHours] is the auto-bet's and the alerts' from now on, this is
     * the bids': they go up only on games starting within this many hours ([TrapGuard.EARLY_CHOICES]; 0 = off), and in Low API usage it is also how far the scan reads. Saved settings start
     * with the value the one shared number had (schema 14), so nothing changes until Tj changes it.
     */
    val trapBidHours: Int = TrapGuard.DEFAULT_EARLY_HOURS,
    /**
     * The trap guard's second rule ([TrapGuard.move]): before the auto-bet places a moneyline, spread or game total, Novig's own trades in the market
     * are read (one public request); a price that just moved 2¢+ under where it traded this hour, with $100+ bought on the other side in the last
     * 15 min, isn't bet.
     */
    val trapNovigMove: Boolean = true,
    /**
     * The trap guard's third rule ([TrapGuard.listedEarly], Tj, 2026-10-07: "remember a bet's first-listed time"): the auto-bet and the alerts also leave alone a bet that was
     * already listed more than [trapEarlyHours] before the start, even once its game is inside the window ([FirstListed] remembers when each bet was first seen). Shares the hours
     * above; off when they are off.
     */
    val trapFirstListed: Boolean = true,
    /** Tj's own presets (Tj, 2026-10-02: "make it so I can make my own settings presets"), beside the built-in ones ([Presets]). */
    val presets: List<SavedPreset> = emptyList(),
    /** The preset applied last (a built-in's or one of [presets]' names), null = none; recorded on each bet ([com.tjshea.vigilant.data.tracker.AtBet]). */
    val presetName: String? = null,
    /**
     * Why auto-bet stopped itself and stays stopped until Tj resumes it (an order whose answer was lost, so nothing says whether it filled);
     * null = running. Not a setting he picks: [com.tjshea.vigilant.data.novig.trading.AutoBet] and the app set and clear it.
     */
    val autoBetHalted: String? = null,
    /**
     * How long a feed's last answer is kept after a failed call, only to order reads: it never prices
     * past [Freshness.MAX_QUOTE_AGE_MS] (RESEARCH.md §24).
     */
    val staleReferenceMinutes: Int = 30,
    // ---- Fair-odds sources (RESEARCH.md §11). Every fetch happens only on a manual scan. ----
    /** Pinnacle via pinnapi's free key (100 requests/day). Needs a key in Settings. */
    val usePinnacle: Boolean = true,
    /** Polymarket's public game markets. Free, no key. */
    val usePolymarket: Boolean = true,
    /** Kalshi's public game markets. Free, no key. */
    val useKalshi: Boolean = true,
    /**
     * Test Kalshi at 3 requests a second instead of 2 (Tj, 2026-10-07, proposal 9: a scan of its 57 series from about 32 s to about 23 s if it holds): about 300 requests, and at
     * the first refusal (429) it goes back to 2 for the rest of the session. Settings › Diagnostics says how it went ([KalshiClient.paceNote]).
     */
    val kalshiFastPace: Boolean = true,
    /** The Odds API (500 credits/month free). */
    val useOddsApi: Boolean = true,
    /** ParlayAPI (The Odds API's format with Pinnacle and 14 more books, props included; RESEARCH.md §43). Needs a key in Settings. */
    val useParlay: Boolean = true,
    /**
     * PropLine (1,000 requests a day free, RESEARCH.md §22): every reference sportsbook's game lines
     * each scan, and their player props per game when [useBookProps] is on. Needs a key in Settings.
     */
    val usePropLine: Boolean = true,
    /** Re-use The Odds API's last odds for this long instead of paying credits on every scan. */
    val oddsApiReuseMinutes: Int = 2,
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
     * likeliest +EV lines read first and shown as they land. Up to 1,200 since v0.18.0 (Tj,
     * 2026-09-28): about four minutes on public routes. [NO_LIMIT] since v0.19.6 (Tj, 2026-09-28: "unlimited novig
     * prices per scan … it should stop the scan when all the markets are finished scanning for the selected time
     * period"): every line a fair source prices in [scanWindowHours], each read once; a line whose other books' odds
     * would be too old once read is left for the next scan (which reads those first), so a scan never outlasts them.
     */
    val maxBooksPerScan: Int = 300,
    /**
     * What the per-game picks leave of [maxBooksPerScan] goes to every other line a fair source quotes
     * (alternate spreads and totals, more props), best-covered first (Tj, 2026-09-28: "make sure the app is
     * finding as many positive EV bets on novig as possible"). Off: a scan reads the per-game picks only.
     */
    val fillBudget: Boolean = true,
    /**
     * Player props from the major sportsbooks (DraftKings, FanDuel, BetMGM, …) via The Odds API,
     * devigged book by book and averaged. Costs 1 credit per prop type per game.
     */
    val useBookProps: Boolean = true,
    /** Which prop types to buy from the books: the core four per sport, or every one Novig lists. */
    val bookPropSet: BookPropSet = BookPropSet.CORE,
    /** Most Odds API credits one scan may spend on sportsbook props. */
    val bookPropCreditsPerScan: Int = 24,
    /**
     * Only games starting within this many hours get sportsbook props (soonest first); [NO_LIMIT] = every game
     * the scan reads ([bookPropWindowHours]).
     */
    val bookPropHours: Int = 24,
    /**
     * PropLine's player props: at most this many games a scan, one request each against its free 1,000 a day
     * (12 was a fixed cap until v0.19.6; Tj, 2026-09-28: "Add unlimited options … for all types of scans that
     * can benefit"). [NO_LIMIT]: every game in [bookPropWindowHours].
     */
    val propLineGamesPerScan: Int = 12,
    /** Re-use sportsbook props for this long between scans. */
    val bookPropReuseMinutes: Int = 2,
    /** Exchange quotes wider than this (ask − bid) are too thin to trust as a fair price. */
    val exchangeMaxSpread: Double = 0.03,
    val feedSort: FeedSort = FeedSort.EV,
    /**
     * With 3+ books behind a fair price, use the lower of their mean and median per side, so one
     * stale book can't manufacture an edge (CrazyNinjaOdds' default, RESEARCH.md §16).
     */
    val outlierGuard: Boolean = true,
    /**
     * The feed hides prices longer than these American odds. Devigging is least reliable on
     * longshots, which is where the biggest fake edges show up (RESEARCH.md §8.1, §16). +300 at most
     * since v0.18.0 (Tj, 2026-09-28: "Let me choose +200 +150 and +120 and get rid of any option over
     * +300"); 0 (no limit) is still read as such, but no longer offered ([migrate] moves it to +300).
     */
    val maxOdds: Int = 300,
    /**
     * The feed hides prices shorter than these American odds (Tj, 2026-10-07: "anywhere there is a longest odds setting … make a shortest odds setting as well"):
     * negative = no favorite shorter than it (−200), positive = underdogs at least that long only (+110); 0 = no limit.
     */
    val minOdds: Int = 0,
    /**
     * Opt-in: leaving Vigilant with a scan running or bets on the feed brings up the widget (or the
     * picture-in-picture window) by itself, and opening Novig's app from a bet floats it over Novig
     * (Tj, 2026-09-26). Off by default since v0.22.0 (Tj, 2026-09-29: "often when I switch from
     * vigilant to another app, the widget opens automatically. Only open the widget if I press the
     * icon to open it in the app"): the widget's own button is then the only thing that opens it.
     */
    val miniWindow: Boolean = false,
    /**
     * v0.13.0's on/off switch for CrazyNinjaOdds' list; read only by [migrate] now ([scanner]
     * decides). The list is the one thing Vigilant reads without a tap: only while its tab or a
     * widget is on screen, and never Novig or a keyed provider (RESEARCH.md §18–20).
     */
    val cnoEnabled: Boolean = true,
    /** Tj's CNO Shared View link (his filters), as normalized by CnoView; blank = Novig, CNO's defaults. */
    val cnoViewUrl: String = "",
    /**
     * Seconds between automatic CNO reads while on screen; [com.tjshea.vigilant.data.cno.CnoFeed.REALTIME]
     * = as soon as CNO publishes; 0 = only when tapped.
     */
    val cnoRefreshSeconds: Int = 15,
    /** Which scanner runs. Saved as "miniSource" by v0.13.0, whose values it keeps. */
    @SerialName("miniSource") val scanner: ScannerMode = ScannerMode.BOTH,
    /** The CNO scanner's filters: worst-case devig, odds cap, fewest books, … (RESEARCH.md §19). */
    val cnoFilters: CnoFilters = CnoFilters(),
    /**
     * With CNO on, the widget is a floating window drawn over other apps (needs Android's "Display
     * over other apps"): scroll buttons always showing, tap a bet to open it in Novig, mark bets
     * placed (Tj, 2026-09-26). Off, or without that permission: the picture-in-picture window.
     */
    val floatingWidget: Boolean = true,
    /**
     * The green check: the best CNO bets' books are read in the background (one game page every
     * few seconds, each re-read after 5 minutes) to see whether several books agree (RESEARCH.md §20).
     */
    val cnoCheckBooks: Boolean = true,
    /** Player bets show the player's team ("D. Schultz (HOU)"), from ESPN's rosters. */
    val cnoPlayerTeams: Boolean = true,
    /**
     * CNO's list shows only green-check bets (Tj, 2026-09-27: "only include bets where multiple
     * books agree … and where both sides of the bet have odds at different sports books"): 3+
     * other books price both sides and 3+ of them alone say it's +EV ([com.tjshea.vigilant.data.cno.CnoBooks]).
     * Bets whose books aren't read yet are held back until they are. Implies reading the books.
     */
    val cnoOnlyAgreed: Boolean = false,
    /**
     * With both scanners, Vigilant's own scan runs again this many minutes after the last one
     * while CNO's list is on screen (its tab or a widget), so the widget's two lists stay fresh
     * together (Tj, 2026-09-27: "an option to also use the regular scan in addition to cno").
     * 0 = only when Scan is tapped (the default: each scan spends API credits).
     */
    val widgetRescanMinutes: Int = 0,
    /**
     * CNO's Novig bets show Novig's price now, read from Novig's own order book every few seconds
     * while the list is on screen, with the EV at it against CNO's fair odds: current even when CNO
     * is slow or out of reach (Tj, 2026-09-27; [com.tjshea.vigilant.data.cno.NovigLive]).
     */
    val cnoLivePrices: Boolean = true,
    /**
     * Vigilant MGM only: the state Tj bets BetMGM in (two letters, "nj"). BetMGM's sites are per state,
     * so its bet-slip links need it ([com.tjshea.vigilant.data.book.BetMgmLinks]). Unused by Vigilant.
     */
    val bookState: String = "",
    /**
     * Every list (the +EV feed, CNO's list, the Games board, the widgets) shows only games starting
     * within this many hours; 0 = any time (Tj, 2026-09-27: "only show games that start within the next
     * 24 hours or 12 hours or 48 hours"). Since v0.19.6 Vigilant's own scan reads only this window too when
     * it's shorter than [daysAhead] ([scanWindowHours]; Tj, 2026-09-28: "stop the scan when all the markets
     * are finished scanning for the selected time period"), so widening it needs a new scan.
     */
    val startsWithinHours: Int = 0,
    /**
     * Scans on a timer in the background, every [autoScanSeconds], with Vigilant closed or not
     * (Tj, 2026-09-28). A foreground service with its own notification keeps it going; each scan
     * is woken by an alarm and holds the CPU only while it runs, unless [autoScanKeepAwake] holds it between scans. Off by default.
     */
    val autoScan: AutoScanMode = AutoScanMode.OFF,
    /**
     * The gap between background scans, in seconds ([AUTO_SCAN_SECONDS_CHOICES]: 5 s up to 40 min; Tj, 2026-10-01: "every 3 minutes, 1 minute,
     * 30 seconds, and 15 seconds"). Before v0.38.0 this was whole minutes ([autoScanMinutes]); [migrate] moves a saved one over.
     */
    val autoScanSeconds: Int = 600,
    /** The v0.19-v0.37 interval in whole minutes: read only by [migrate] (schema 12), which turns it into [autoScanSeconds]. */
    val autoScanMinutes: Int = 10,
    /**
     * Keep the phone's CPU awake (screen off) while background auto-scan runs more often than every 9 minutes (Tj, 2026-10-02: "keep it
     * alive robustly … even if the phone is idle and the screen is turned off and locked"): without it Android's Doze holds an alarm-driven
     * scan to its allowance for alarms (documented: about one every 9 minutes) while the phone sits still. See [KeepAwake]. On by default: costs battery, best plugged in.
     */
    val autoScanKeepAwake: Boolean = true,
    /**
     * A push notification for each new bet at or over this EV (0.03 = 3%) that several books agree
     * on, found while Vigilant isn't on screen; tapping it opens the bet in Novig (Tj, 2026-09-28).
     * 0 = no alerts.
     */
    val alertMinEv: Double = 0.03,
    /**
     * Every scan stops until Tj resumes (Tj, 2026-09-28: "Make an option in the app to pause all scanning"): a scan
     * running is stopped, CrazyNinjaOdds' list and its lanes (books, links, rosters, Novig's price now) aren't read even
     * on screen, background auto-scan and the widget's rescans wait, and Scan, Recheck and Refresh say it's paused. The
     * scanner and auto-scan choices are kept for when it resumes. Opening a bet and settling tracked bets still work.
     */
    @SerialName("paused") val pausedByHand: Boolean = false,
    /**
     * The kill switch (Tj, 2026-10-05: "a stop button kill switch in the app visible everywhere that immediately stops all scanning, all auto betting, all auto
     * bidding, and all background scan. If I press this, everything remains off, even if I close the app and open it again, until I press resume"). Saved with
     * the settings, so it outlives the app being closed, the phone restarting and an update; only Tj's Resume on the red bar clears it (no scan, refresh, Check
     * odds now, wallet-empty note or settings fix does). It is [paused] too, so every scan, the CNO reads, auto-bet, auto-lock, bids and the background
     * services that already stop for a pause stop for it; the switches Tj set (auto-bet, auto-make, auto-scan, the interval) are kept for when he resumes.
     */
    val killed: Boolean = false,
    /** When [killed] was pressed (epoch ms), for the bar and Diagnostics; null when not killed. */
    val killedAtMs: Long? = null,
    /** Settings format version, for one-time upgrades of a saved file ([migrate]). */
    val schema: Int = 0,
) {
    /** Every scan, bet and bid is held: the Pause button ([pausedByHand]) or the kill switch ([killed]). Everything that waits for a pause reads this. */
    val paused: Boolean get() = pausedByHand || killed

    /**
     * Brings settings saved by an older version up to date. v0.6.0 (schema 2) made Polymarket and
     * Kalshi sharp by default; a saved v0.5 file still says Pinnacle only, so they're added once.
     */
    /**
     * The settings an app start begins with: unchanged after a normal start, and after a crash ([crashed]) every research recorder, the bids, auto-bet and live feed off. It used to switch the research
     * off at EVERY start, but Android ends a backgrounded app's process after a few minutes and the next open is a new start: Tj (2026-10-10): "When I switch apps then come back to vigilant after a couple
     * minutes, the research options are turned off".
     */
    fun safeStart(crashed: Boolean): ScanSettings =
        if (!crashed) this else copy(researchMode = false, altLab = false, feedRace = false, burstRecorder = false, autoBet = false, maker = false, pinnLive = false, liveBid = false, liveBidReal = false, tailLive = tailLive.copy(on = false, bet = false))

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
        // v0.14.0: the CNO switch became the scanner choice, and 15 s the default refresh.
        if (s.schema < 5) {
            s = s.copy(
                scanner = if (!s.cnoEnabled) ScannerMode.VIGILANT else s.scanner,
                cnoEnabled = true,
                cnoRefreshSeconds = if (s.cnoRefreshSeconds == 60) 15 else s.cnoRefreshSeconds,
                schema = 5,
            )
        }
        // v0.18.0: the longest odds shown is +300 at most (Tj, 2026-09-28); a longer cap or none becomes +300.
        if (s.schema < 6) {
            s = s.copy(maxOdds = if (s.maxOdds <= 0 || s.maxOdds > MAX_ODDS_LIMIT) MAX_ODDS_LIMIT else s.maxOdds, schema = 6)
        }
        // v0.19.0: tennis is new (Tj, 2026-09-28: "It may be missing many games and bets"). Turned on once,
        // beside whatever leagues are picked; a tap on its chip turns it off for good.
        if (s.schema < 7) {
            s = s.copy(leagues = s.leagues + TENNIS, schema = 7)
        }
        // v0.19.1: a week ahead by default; a file still at the old default of 3 days moves once.
        if (s.schema < 8) {
            s = s.copy(daysAhead = if (s.daysAhead == 3) 7 else s.daysAhead, schema = 8)
        }
        // v0.19.3: CNO's fewest books is 1-4 (Tj, 2026-09-28: "Remove any option over 4 books"); 5 or more becomes 4.
        if (s.schema < 9) {
            s = s.copy(cnoFilters = s.cnoFilters.copy(minBooks = s.cnoFilters.minBooks.coerceIn(1, CNO_MIN_BOOKS_CHOICES.max())), schema = 9)
        }
        // v0.22.0: the widget opens only from its button (Tj, 2026-09-29). The old default was on, so a saved
        // file can't tell a chosen "on" from the default: it moves to off once, and the switch can turn it back on.
        if (s.schema < 10) {
            s = s.copy(miniWindow = false, schema = 10)
        }
        // v0.35.0 (Tj, 2026-09-30: "add sports books to each scanner"): Hard Rock, Bovada and Fliff join the books read (free on PropLine);
        // LowVig, BetOnline's twin line, leaves a list that has BetOnline, so one line isn't counted twice in the consensus.
        if (s.schema < 11) {
            val books = s.referenceBooks.filterNot { it == "lowvig" && "betonlineag" in s.referenceBooks } +
                com.tjshea.vigilant.data.reference.TheOddsApiClient.ADDED_V35.filterNot { it in s.referenceBooks }
            s = s.copy(referenceBooks = books, schema = 11)
        }
        // v0.38.0 (Tj, 2026-10-01): the auto-scan interval is in seconds so 15 s, 30 s and 1 min fit; a saved file's minutes carry over.
        if (s.schema < 12) {
            s = s.copy(autoScanSeconds = s.autoScanMinutes.coerceAtLeast(1) * 60, schema = 12)
        }
        // v0.71.0 (Tj, 2026-10-07: "redundant … settings"): the Bet sheet's own starting amount and "My amount" were one number in two places; with the choice Off or Kelly the
        // sheet's amount was the one in use, so it moves to "My amount" (with $1 or My amount chosen, the saved "My amount" already was the one).
        if (s.schema < 13) {
            val sheetOwn = s.slipStake == com.tjshea.vigilant.data.novig.SlipStake.OFF || s.slipStake == com.tjshea.vigilant.data.novig.SlipStake.KELLY
            s = s.copy(slipCustomStake = if (sheetOwn) s.apiBetStake else s.slipCustomStake, schema = 13)
        }
        // v0.86.1 (Tj, 2026-10-10): the trap guard's hours split in two, the auto-bet's and the bids'. Both start at the number the one setting held.
        if (s.schema < 14) {
            s = s.copy(trapBidHours = s.trapEarlyHours, schema = 14)
        }
        // v0.89.0 (Tj, 2026-10-10): tennis in the auto-bet and the bids, on once beside whatever leagues are picked; the Tennis switch turns it off.
        if (s.schema < 15) {
            s = s.copy(leagues = s.leagues + TENNIS, autoBetTennis = true, schema = 15)
        }
        // v0.89.7 (Tj, 2026-10-11: "oddspapi currently does not scan"): switching OddsPapi Free on meant closing lines only until a daily figure was picked, which read as a switch that did nothing.
        // It now prices scans from 10 requests a day unless a figure is chosen; a file that still says 0 (the old default) moves once, and Off stays a choice.
        if (s.schema < 16) {
            s = s.copy(gates = s.gates.copy(op4Fair = if (s.gates.op4Fair == 0) 10 else s.gates.op4Fair), schema = 16)
        }
        // v0.89.19 (Tj, 2026-10-11: "I want the app to use conservative devig methods to be safest"): the fair line moves once to the worst case of the four devig methods; a later choice in Settings stays.
        if (s.schema < 17) {
            s = s.copy(devigMethod = DevigMethod.WORST_CASE, schema = 17)
        }
        // v0.99.4 (Tj, 2026-10-14: "Do what you think is most profitable because I'm losing money"): Profit-proven only turns on once, from the history files' profile; a later choice in Settings stays.
        if (s.schema < 18) {
            // Tennis auto-bets: -28% on 97 bets (non-prop tennis -40% on 38, t -4.1): off until a history file says otherwise.
            s = s.copy(gates = s.gates.copy(profitProven = true), vigGuards = s.vigGuards.copy(propHaircut = true), autoBetTennis = false, schema = 18)
        }
        // v0.99.8 (Tj, 2026-10-14: the weekend's slate was never scanned: Days ahead 2): bids need a week of games and their props.
        if (s.schema < 19) {
            s = if (s.maker || s.makerRecommend) s.copy(daysAhead = maxOf(s.daysAhead, BID_REACH_DAYS), bookPropHours = maxOf(s.bookPropHours, BID_REACH_DAYS * 24), schema = 19) else s.copy(schema = 19)
        }
        // v0.99.11 (Tj, 2026-10-14: "All times are allowed"): bids read the longest reach the scan offers, ten days.
        if (s.schema < 20) {
            s = if (s.maker || s.makerRecommend) s.copy(daysAhead = maxOf(s.daysAhead, BID_REACH_DAYS), bookPropHours = maxOf(s.bookPropHours, BID_REACH_DAYS * 24), schema = 20) else s.copy(schema = 20)
        }
        // v0.99.14 (Tj, 2026-10-14: "The key is volume"): Volume mode on for every install; switching it off on the Bids tab stays.
        if (s.schema < 21) {
            s = s.copy(vigGuards = s.vigGuards.copy(bidVolume = true), schema = 21)
        }
        // v0.116 (Tj, 2026-10-18: "it should be used as one of the sharp odds sources"): SX Bet joins the sharp books once; its own switch (off by default) and a saved key decide whether it prices anything.
        if (s.schema < 22) {
            s = s.copy(sharpBooks = s.sharpBooks + "sxbet", schema = 22)
        }
        return s
    }

    /** Whether ATP and WTA are both scanned (the Tennis switch's state). */
    val tennisOn: Boolean get() = leagues.containsAll(TENNIS)

    /** The Tennis switch: ATP and WTA in or out of the scanned [leagues] (so the pregame bids follow) and the tennis auto-bet on or off. */
    fun withTennis(on: Boolean): ScanSettings = copy(leagues = if (on) leagues + TENNIS else leagues - TENNIS, autoBetTennis = on)

    /** The dollars a bet's Novig slip opens with, for a bet whose Kelly stake is [kelly]; null = none. */
    fun slipStakeFor(kelly: Double?): Double? = NovigLinks.stake(slipStake, slipCustomStake, kelly)

    /** CrazyNinjaOdds' list is read (both scanners, or CNO only). */
    val cnoOn: Boolean get() = scanner != ScannerMode.VIGILANT

    /** Vigilant's own scan, and the APIs behind it, can run (both scanners, or Vigilant only). */
    val vigilantOn: Boolean get() = scanner != ScannerMode.CNO

    /** Live games only mode is on: no scan reads the REST board and the list holds the real-time lane's bets alone. */
    val liveOnly: Boolean get() = gates.liveOnly.on

    /**
     * Cloud scan (Tj, 2026-10-14: "use the phone only to set the settings and retrieve the scanned results. GitHub will provide all the power"): the scan runs on GitHub's computers
     * ([com.tjshea.vigilant.data.cloud.CloudEngine]) under these settings, and the phone's "scan" adopts the list it publishes ([com.tjshea.vigilant.data.cloud.CloudScanner]); no API is called from the phone.
     * Off (the default): the app is what it was. Kept in [gates] (ScanSettings is near the JVM's 255-argument limit, ScanSettingsSizeTest).
     */
    val cloudScan: Boolean get() = gates.cloudScan

    /** Save every bet, bid and the scan study's days to GitHub's `phone-data` branch automatically (Tj, 2026-10-14), whenever a GitHub token is saved in Settings › Cloud scan. */
    val cloudSave: Boolean get() = gates.cloudSave

    /** The GitHub engine keeps to 08:00-01:00 Eastern ([com.tjshea.vigilant.data.cloud.EngineHours]). */
    val cloudHours: Boolean get() = gates.cloudHours

    /** The bid desk is on (automatic or Recommend) and priced from Vigilant's scan: it needs a line for every side worth a bid, not only the +EV ones the Feed lists. CNO-priced bids never read Vigilant's scan. */
    val bidsNeedLines: Boolean get() = (maker || makerRecommend) && makerSource == BidSource.VIGILANT

    /**
     * The scan is the GitHub engine's (the phone adopts its list, the engine mirrors these settings): the switch is on and scanning is not paused (by hand or by the kill switch). The engine publishes the Feed's
     * list; with Vigilant-priced bids on ([bidsFromCloud]) it also publishes the bid desk's candidate lines. When false with [cloudScan] on, the engine is told to stand down.
     */
    val cloudDrives: Boolean get() = cloudScan && !paused

    /** The bid desk's lines are GitHub's candidates, given Novig's books by this phone ([com.tjshea.vigilant.data.cloud.CloudBidLane]); the engine publishes them only then. */
    val bidsFromCloud: Boolean get() = cloudDrives && bidsNeedLines

    /**
     * The GitHub engine posts the bids (Tj, 2026-10-16, TCL31): the cloud scan drives, the engine trades LIVE with the trading key, bids are priced from Vigilant's scan and [ApiGates.cloudBids] is on. Whether the
     * phone's own bid desk then stands down is decided by the engine's heartbeat, not by this alone ([com.tjshea.vigilant.data.cloud.CloudStatus.deskLive]), so a dead engine never leaves the wallet unbid.
     */
    /** The live tail lane (Live light, the tail taker, its bids) runs on the GitHub engine (TCL109): the engine drives and trades (paper or live) and Tj switched the lane on. The phone's own copy stands down while the engine says it is live ([com.tjshea.vigilant.data.cloud.CloudStatus.liveLive]). */
    val engineLive: Boolean get() = cloudDrives && !killed && gates.cloudTrading != CloudTradeMode.OFF && tailLive.engine && (tailLive.on || pinnLive || liveBid)

    val engineBids: Boolean get() = cloudDrives && !killed && gates.cloudTrading == CloudTradeMode.LIVE && gates.cloudBids && makerSource == BidSource.VIGILANT

    /** The scanner choice as it runs. */
    val scannerNow: ScannerMode get() = scanner

    /**
     * These settings as the scan reads them: unchanged, except under Low API usage bids (RESEARCH.md §92).
     */
    fun effective(forBets: Boolean = false): ScanSettings = when {
        // Low API usage (RESEARCH.md §92): Vigilant's own scan reads the picked sharp prop books' props for the next 6 hours and nothing else. A bets-only pass ([forBets]:
        // Check odds now, pricing Tj's open bets of every kind) is not narrowed: it asks for the games he holds.
        lowUsageNow && !forBets -> LowUsageBids.profile(this)
        // The long-run saver's lean background scan (RESEARCH.md §119): the families a bid can go on, nothing else. Tj's own scans never carry the flag.
        leanScan && !forBets -> LongRunBids.profile(this)
        else -> this
    }

    /**
     * Bids are on ([maker] or [makerRecommend]) and set to [BidFocus.LOW_USAGE]: Vigilant's
     * own scan is the low-usage scan ([effective]) and runs at the pace [lowUsagePace] says ([LowUsageBids.gapSeconds]).
     */
    val lowUsageNow: Boolean get() = makerFocus == BidFocus.LOW_USAGE && (maker || makerRecommend) && makerSource == BidSource.VIGILANT

    /**
     * Bids are priced from CrazyNinjaOdds ([makerSource]) and are on ([maker] or [makerRecommend]): the background cycle reads
     * CNO's list, the wide list and the games' pages for them ([com.tjshea.vigilant.data.novig.trading.maker.CnoMakerLines]), whatever alerts and auto-bet are set to.
     */
    val bidsFromCno: Boolean get() = makerSource == BidSource.CNO && (maker || makerRecommend)

    /**
     * The shortest gap between two background runs of Vigilant's own scan: the usual [AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS], or the low-usage pace ([LowUsageBids.shortestGapSeconds];
     * the Auto pace's gap is longer while every game is far off, [LowUsageBids.gapSeconds], which the background scan asks).
     */
    val vigilantGapSeconds: Int get() = when {
        lowUsageNow -> LowUsageBids.shortestGapSeconds(this)
        // SGO Pro (Tj, 2026-10-09: bids "keep posting new bids all day ... automatic scans every few minutes and fresh odds"): its reads cost no credits, and its prices refresh about every 5 minutes, so a bid
        // priced from a 6-minute-old quote lives only 4 more minutes under the 10-minute guard: a scan every 4+ minutes left holes between a bid coming down and the next one going up.
        sgoPro -> SGO_SCAN_GAP_SECONDS
        else -> AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS
    }

    /** What background auto-scan does now: [autoScan], or nothing while [paused]. */
    val activeAutoScan: AutoScanMode get() = if (autoScansCno || autoScansVigilant) autoScan else AutoScanMode.OFF

    /**
     * A background cycle reads CrazyNinjaOdds' list: auto-scan is on CNO or Both, scanning isn't paused and the CNO scanner is on. The scanner
     * choice is the master switch (Tj, 2026-09-29: "if I have cno only turned on in the settings … it doesn't scan vigilant in the background and
     * waste api usage"): on Vigilant only, CNO is asleep in the background as it is in the app.
     */
    val autoScansCno: Boolean get() = !paused && autoScan.cno && cnoOn

    /**
     * A background cycle places bets: auto-bet is on, not halted, and the CNO scanner runs in the background ([autoScansCno]: CNO on, auto-scan on
     * CNO or Both, not paused). Whether betting through the API is set up is the app's to know ([com.tjshea.vigilant.app.AutoBettor]).
     */
    val autoBetsNow: Boolean get() = autoBet && autoBetHalted == null && autoScansCno

    /** A background cycle locks profits ([autoLock]): on, with the background scan running (any scanner) and not paused. */
    val autoLocksNow: Boolean get() = autoLock && !paused && autoScan != AutoScanMode.OFF

    /** Make orders post and re-price by themselves: on, not paused (the Pause button, the kill switch and an empty wallet cancel every bid) and not stopped by the picked-off guard. */
    val makerNow: Boolean get() = maker && !paused && makerHalted == null

    /** A background cycle runs Vigilant's own scan (spending its APIs' credits): auto-scan on Both, not paused, and the Vigilant scanner on (never on CNO only). */
    val autoScansVigilant: Boolean get() = !paused && autoScan.vigilant && vigilantOn

    /**
     * How far ahead Vigilant's scan reads, in hours: [daysAhead], or [startsWithinHours] when that's shorter (Tj,
     * 2026-09-28: "stop the scan when all the markets are finished scanning for the selected time period").
     */
    val scanWindowHours: Int
        get() {
            val days = daysAhead.coerceAtLeast(1) * 24
            if (vigGuards.nearScan) return minOf(days, FarLane.NEAR_HOURS)
            // The auto bids fill the wallet from games later in the week (Tj, 2026-10-14: "There are games this weekend with a full NFL and college football slate ..."): the Starts within chip is a VIEW of the next
            // hours, and it must not stop the scan from reading the whole Days ahead window the bids are priced from. Only the bids' own switch widens it; a feed that shows 48 h still shows 48 h.
            if (maker && vigGuards.bidDeploy && !lowUsageScan && !leanScan && makerFocus != BidFocus.LOW_USAGE) return days
            return if (startsWithinHours in 1 until days) startsWithinHours else days
        }

    /** Games starting within this many hours get sportsbook props: [bookPropHours], never past [scanWindowHours]. */
    val bookPropWindowHours: Int get() = minOf(bookPropHours, scanWindowHours).coerceAtLeast(1)

    /**
     * One book a parent company in every fair line and every list (Tj, 2026-10-11: "consolidate same company books and do not count them for their odds more than once"): Hard Rock's
     * sites, FanDuel and its YourWay, Caesars' two names are one vote ([com.tjshea.vigilant.engine.BookCompany]). On by default.
     */
    val oneVotePerCompany: Boolean get() = vigGuards.oneVotePerCompany

    /** Leave a book out of every fair line when its odds look wrong next to the market: an extreme outlier, or the favourite on the other side ([com.tjshea.vigilant.engine.BadOdds]). On by default. */
    val ignoreBadOdds: Boolean get() = vigGuards.ignoreBadOdds
    val thinEarlyHaircut: Boolean get() = vigGuards.thinEarlyHaircut
    val propHaircut: Boolean get() = vigGuards.propHaircut

    /**
     * The auto-bet also takes bets from Vigilant's own scan (Tj, 2026-10-11: "make the auto bet feature also bet on vigilant scanned bets, but only if they meet the criteria I set for auto bet"):
     * the same edge, odds, kind, stake and limit rules, plus the books-agree rules ([com.tjshea.vigilant.data.novig.trading.AutoBet.judgeVigilant]). Needs [autoBet]. On by default.
     */
    val autoBetVigilant: Boolean get() = vigGuards.autoBetVigilant

    /** The fewest stars ([BetRating]) a bet needs for the auto-bet; 0 = any. */
    val autoBetMinStars: Double get() = vigGuards.autoBetMinStars
    val autoBetOverPropExtraEv: Double get() = vigGuards.autoBetOverPropExtraEv

    fun fairSettings(): FairSettings = FairSettings(
        source = fairSource,
        method = devigMethod,
        sharpBooks = sharpBooks,
        sharpWeight = sharpWeight.coerceIn(0.0, 1.0),
        fallbackToAverage = fallbackToAverage,
        minBooks = minBooks.coerceAtLeast(1),
        outlierGuard = outlierGuard,
        minSharp = minSharpBooks.coerceAtLeast(1),
        wideGuard = ignoreWideQuotes,
        oneBookPerCompany = oneVotePerCompany,
        dropBadOdds = ignoreBadOdds,
    )

    /** Whether a price (cost per $1 payout) is within [maxOdds]. */
    fun withinMaxOdds(cost: Double): Boolean = maxOdds <= 0 || cost >= 100.0 / (100.0 + maxOdds) - 1e-9

    /** Whether a price (cost per $1 payout) is within [minOdds]: no shorter than it (a shorter price costs more). */
    fun withinMinOdds(cost: Double): Boolean = minOdds == 0 || cost <= 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(minOdds) + 1e-9

    /** Whether a price is inside both odds limits ([minOdds], [maxOdds]). */
    fun withinOdds(cost: Double): Boolean = withinMaxOdds(cost) && withinMinOdds(cost)

    /**
     * Whether a game starting at [startsMs] passes [startsWithinHours] at [now]. Games already under
     * way pass (whether live bets show is [includeLive]'s call), and so does an unknown start.
     */
    fun startsInWindow(startsMs: Long?, now: Long): Boolean =
        startsWithinHours <= 0 || startsMs == null || startsMs <= 0 || startsMs <= now + startsWithinHours * 3_600_000L

    val selectedLeagues: List<League> get() = Leagues.ALL.filter { it.novigName in leagues }

    /** How long The Odds API's game lines are re-used: the setting, never past [Freshness.MAX_REUSE_MS]. */
    val oddsApiReuseMs: Long get() = minOf(oddsApiReuseMinutes.coerceAtLeast(0) * 60_000L, Freshness.MAX_REUSE_MS)

    /** How long a game's sportsbook props are re-used: the setting, never past [Freshness.MAX_REUSE_MS]. */
    val bookPropReuseMs: Long get() = minOf(bookPropReuseMinutes.coerceAtLeast(0) * 60_000L, Freshness.MAX_REUSE_MS)

    /** [com.tjshea.vigilant.data.reference.ReferenceSource.id]s the user has switched on. */
    val enabledSources: Set<String>
        get() = buildSet {
            if (usePinnacle) add("pinnacle")
            if (usePolymarket) add("polymarket")
            if (useKalshi) add("kalshi")
            if (useOddsApi) add("oddsapi")
            if (useOddsApi && useBookProps) add("oddsapi_props")
            if (useParlay) add("parlay")
            if (useParlay) add("parlay_1h")
            if (useParlay && useBookProps) add("parlay_props")
            if (usePropLine) add("propline")
            if (usePropLine && useBookProps) add("propline_props")
            // SGO Pro prices through its own two sources (SPORTSGAMEODDS_API.md §5); off, this set is exactly what it was.
            if (sgoPro) { add("sgo"); add("sgo-props") }
            // OddsPapi prices through its own two sources (ODDSPAPI_API.md §6); off, this set is exactly what it was.
            if (oddsPapi) { add("oddspapi"); add("oddspapi-props") }
            // BoltOdds Pro: its own two sources (BOLTODDS_API.md §7); off, this set is exactly what it was.
            if (gates.useBoltOdds) { add("bolt"); add("bolt-props") }
            if (gates.useSxBet) add("sxbet")
            // Unabated Free: its own two sources, last in the order (UNABATED_API.md §3); off, this set is exactly what it was.
            if (unabated) { add("unabated"); add("unabated-props") }
            // OddsPapi Free (v4) reads whole games for the fair average only when Tj gives it a daily request figure; with 0 it is closing lines only.
            if (oddsPapiFree && gates.op4Fair > 0) { add("oddspapi_free"); add("oddspapi_free-props") }
        }

    val novigMarketTypes: List<String> get() = families.flatMap { it.novigTypes }

    companion object {
        /** The auto-bet's minimum-rating choices: 0 = any, then 2 to 5 stars in halves. */
        val AUTO_BET_STARS_CHOICES: List<Double> = listOf(0.0, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

        /** At most [Freshness.MAX_REUSE_MS]: older sportsbook odds are never compared (RESEARCH.md §24). */
        val ODDS_API_REUSE_CHOICES = listOf(0, 1, 2)
        /**
         * "No limit" / "All" on every per-scan cap that can use it (Tj, 2026-09-28: "Add unlimited options in the vigilant
         * app for all types of scans that can benefit from unlimited"). A scan is still finite: each line in the time
         * window at most once ([maxBooksPerScan]), each game's props at most once.
         */
        const val NO_LIMIT = Int.MAX_VALUE

        /** [NO_LIMIT] ("All") since v0.19.6: with the budget filled, these only order the reads. */
        val LINES_PER_GAME_CHOICES = listOf(1, 2, 3, 5, 8, 10, NO_LIMIT)
        /** 16 and 24 since v0.18.0: room to fill the bigger per-scan budgets with props, where exchange prices lag most. */
        val PROPS_PER_GAME_CHOICES = listOf(0, 2, 4, 8, 12, 16, 24, 32, 48, NO_LIMIT)

        /** Up to 1,200 since v0.18.0 (Tj, 2026-09-28: "so I can select 500 600 700 800 up to 1200"). */
        /**
         * Up to 2,000 (Tj, 2026-09-28: "consider if the 1200 Max prices per novig scan is enough"): what one connection of
         * the key's live feed can watch (2,048), so a keyed scan gets them all pushed. No "No limit": past it every price
         * is its own request (~14 a second), a scan runs many minutes, and background scans repeat that (RESEARCH.md §31).
         */
        val MAX_BOOKS_CHOICES = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1500, 2000, NO_LIMIT)
        val BOOK_PROP_CREDIT_CHOICES = listOf(0, 12, 24, 48, 96, 192, NO_LIMIT)
        /** [NO_LIMIT] ("All"): every game the scan reads. */
        val BOOK_PROP_HOURS_CHOICES = listOf(6, 12, 24, 48, NO_LIMIT)
        /** [propLineGamesPerScan]'s choices. */
        val PROPLINE_GAMES_CHOICES = listOf(12, 24, 48, NO_LIMIT)
        val BOOK_PROP_REUSE_CHOICES = listOf(1, 2)
        val KELLY_CHOICES = listOf(0.125, 0.25, 0.5, 1.0)
        /** Nothing over +300 since v0.18.0 (Tj, 2026-09-28). */
        val MAX_ODDS_CHOICES = listOf(120, 150, 200, 300)

        /** The longest [maxOdds] offered. */
        const val MAX_ODDS_LIMIT = 300

        /** Tennis's leagues, added to a saved file's once by [migrate] (v0.19.0). */
        val TENNIS = setOf("ATP", "WTA")
        val CNO_MAX_ODDS_CHOICES = listOf(100, 150, 200, 300, 0)
        /** "Fewest books behind the fair price" (Tj, 2026-09-28: "add options for 1 and 2 books. Remove any option over 4 books"). */
        val CNO_MIN_BOOKS_CHOICES = listOf(1, 2, 3, 4)
        val CNO_MIN_EV_CHOICES = listOf(0.0, 0.01, 0.02, 0.03)
        val CNO_ROWS_CHOICES = listOf(25, 50, 100)

        /** [widgetRescanMinutes]' choices (0 = off). */
        val WIDGET_RESCAN_CHOICES = listOf(0, 5, 10, 15, 30)

        /**
         * [startsWithinHours]' choices (0 = any time). 3 h and 6 h were added in v0.61.0 (RESEARCH.md §82): Tj's bets placed within 6 h of the start beat the
         * close by +3.0% (96 closes, 81%), those placed earlier by −0.5% (322), and the trap guard's own window ([TrapGuard.DEFAULT_EARLY_HOURS]) is 6 h, so
         * a list that can be set to 6 h shows what the guard would bet; before, the shortest choice was 12 h.
         */
        val STARTS_WITHIN_CHOICES = listOf(0, 3, 6, 12, 24, 48)

        /** How many days ahead the scan must read for the auto bids to find a full slate of games and props (the week). */
        const val BID_REACH_DAYS = 10

        /** [daysAhead]'s choices. */
        val DAYS_AHEAD_CHOICES = listOf(1, 2, 3, 5, 7, 10)

        /**
         * [autoScanSeconds]' choices (Tj, 2026-09-28: "every 5 10 20 30 or 40 minutes", then 2026-10-01: "every 3 minutes, 1 minute, 30 seconds, and
         * 15 seconds", then "an option to scan cno every 5 seconds for the auto bet function"), fastest first.
         */
        val AUTO_SCAN_SECONDS_CHOICES = listOf(5, 15, 30, 60, 180, 300, 600, 1200, 1800, 2400)

        /** Vigilant's own scan (API credits) starts at most this often inside a background cycle, however fast the cycles are ([AutoScanner]). */
        const val AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS = 240

        /** [sgoMaxAgeMinutes]'s choices. */
        val SGO_MAX_AGE_CHOICES = listOf(10, 12, 15, 20)

        /** The same gap while SGO Pro is on: every 2 minutes (see [vigilantGapSeconds]). */
        const val SGO_SCAN_GAP_SECONDS = 120

        /** How often a background cycle really runs Vigilant's own scan at [autoScanSeconds] (every cycle, or every few of them when cycles are faster than the gap: [gapSeconds], the one above or [ScanSettings.vigilantGapSeconds]'s low-usage pace). */
        fun vigilantEverySeconds(autoScanSeconds: Int, gapSeconds: Int = AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS): Int {
            val every = autoScanSeconds.coerceAtLeast(1)
            return if (every >= gapSeconds) every else Math.ceil(gapSeconds.toDouble() / every).toInt() * every
        }

        /** [seconds] as the settings, the notification and Diagnostics say it: "15 sec", "30 sec", "1 min", "3 min", "90 sec". */
        fun intervalLabel(seconds: Int): String = when {
            seconds < 60 || seconds % 60 != 0 -> "$seconds sec"
            else -> "${seconds / 60} min"
        }

        /** [autoBetMinEv]'s choices (Tj, 2026-10-01: "+2%, +2.5, +3, +3.25, +3.5, +3.75, +4, plus an option to manually type in an amount"). */
        val AUTO_BET_MIN_EV_CHOICES = listOf(0.02, 0.025, 0.03, 0.0325, 0.035, 0.0375, 0.04)

        /** [autoBetFavouriteExtraEv]'s choices (probability points above the minimum edge; 0 = none). */
        val AUTO_BET_FAVOURITE_EV_CHOICES = listOf(0.0, 0.005, 0.01, 0.015, 0.02)

        /** The extra edge a player-prop Over can be asked for ([autoBetOverPropExtraEv]). */
        val AUTO_BET_OVER_PROP_EV_CHOICES = listOf(0.0, 0.01, 0.02, 0.03, 0.05)

        /** The leagues the burst recorder can watch (Novig's league names): the ones with a game clock whose lines Novig lists as a moneyline, spreads and totals. */
        val BURST_LEAGUES = listOf("NFL", "NCAAF", "NBA", "WNBA", "NHL", "MLB")

        /** Pinnodds live's choices: a bet's stake and the game, day and loss caps (dollars), the least edge after the fee (probability) and the least Pinnacle move (probability points). */
        val PINN_LIVE_STAKE_CHOICES = listOf(1.0, 2.0, 5.0, 10.0)
        val PINN_LIVE_MAX_GAME_CHOICES = listOf(5.0, 10.0, 25.0)
        val PINN_LIVE_MAX_DAY_CHOICES = listOf(10.0, 25.0, 50.0, 100.0)
        val PINN_LIVE_HALT_LOSS_CHOICES = listOf(5.0, 10.0, 25.0, 50.0)
        val PINN_LIVE_MIN_EV_CHOICES = listOf(0.02, 0.03, 0.04, 0.05)
        val PINN_LIVE_MIN_MOVE_CHOICES = listOf(0.01, 0.015, 0.02, 0.03)

        /** The real-money burst trader's choices (dollars): a leg's stake, a game's and a day's total (both legs of every attempt, fees in), and legs held alone that halt it. No "no limit" here. */
        val BURST_TRADE_STAKE_CHOICES = listOf(1.0, 2.0, 5.0, 10.0)
        val BURST_TRADE_MAX_GAME_CHOICES = listOf(5.0, 10.0, 25.0)
        val BURST_TRADE_MAX_DAY_CHOICES = listOf(10.0, 25.0, 50.0, 100.0)
        val BURST_TRADE_HALT_LOSS_CHOICES = listOf(1.0, 3.0, 5.0, 10.0)

        /** [apiMaxPerGame]'s chips, dollars (0 = no limit); any other amount is typed (Tj, 2026-10-04: "add $5 and a manual entry"). */
        val API_MAX_PER_GAME_CHOICES = listOf(5.0, 10.0, 25.0, 50.0, 100.0, 0.0)

        /**
         * [makerMargin]'s choices: 2% and 2.5% fill the most, 4% earns the most per fill (RESEARCH.md §70.2). Tj, 2026-10-07: "remove the 6% and 8% under the fair options and add 2%
         * and 2.5%" (a 6% or 8% margin already saved stays, and any other margin can be typed). 3.25% and 3.5% (Tj, 2026-10-04) usually land on the same price as 3% or 4% on Novig's
         * 0.5¢ grid (§81.4).
         */
        val MAKER_MARGIN_CHOICES = listOf(0.02, 0.025, 0.03, 0.0325, 0.035, 0.04)

        /** The most a typed bid margin may be (a bid's price must stay well above nothing) and the least (a margin under 0.5% isn't an edge). */
        const val MAKER_MARGIN_MIN = 0.005
        const val MAKER_MARGIN_MAX = 0.5

        /** A kind of market the popularity study didn't measure is popular when this many books or more price the line ([makerPopularFirst]). */
        const val MAKER_POPULAR_BOOKS = 6

        /** [makerCnoMaxAgeSeconds]'s choices: 1 to 5 minutes (the app's own limit for any fair is 5, [Freshness.MAX_QUOTE_AGE_MS]); a page is re-read about every minute for a game with a bid up. */
        val MAKER_CNO_MAX_AGE_CHOICES = listOf(60, 90, 120, 180, 300)
        const val MAKER_CNO_MAX_AGE_DEFAULT = 120

        /** [makerStake]'s and [makerMaxStake]'s choices, dollars. */
        val MAKER_STAKE_CHOICES = listOf(1.0, 2.0, 5.0, 10.0, 25.0)

        /** [makerMaxBids]' choices; [NO_LIMIT] = unlimited (Tj, 2026-10-05: "an option for unlimited bids up at once"): the wallet, the dollars and the per-game limit still bind. */
        /** [ScanGuards.bidPerGameDollars]' and [ScanGuards.bidPerGameBids]' chips (0 = no limit). */
        val BID_PER_GAME_DOLLARS_CHOICES = listOf(3.0, 5.0, 10.0, 20.0, 0.0)
        val BID_PER_GAME_BIDS_CHOICES = listOf(3, 5, 10, 20, 0)

        val MAKER_MAX_BIDS_CHOICES = listOf(5, 10, 20, 40, BidSlider.MANY_BIDS, NO_LIMIT)

        /** [makerMaxDollars]' choices; [MAKER_NO_DOLLAR_LIMIT] = no limit of its own (the wallet and the day's limit still hold every bid). */
        val MAKER_MAX_DOLLARS_CHOICES = listOf(25.0, 50.0, 100.0, 250.0, 1_000.0, MAKER_NO_DOLLAR_LIMIT)

        /** "No limit" for the dollars up at once. */
        const val MAKER_NO_DOLLAR_LIMIT = 1_000_000.0

        /** The picked-off guard looks at this many of the newest fills ([makerGuard]). */
        const val MAKER_GUARD_FILLS = 8

        /** [makerTtlMinutes]' choices. */
        val MAKER_TTL_CHOICES = listOf(10, 30, 60, 120)

        /** [makerStopMinutes]' choices. */
        val MAKER_STOP_CHOICES = listOf(5, 15, 30, 60)

        /** [makerKinds]' default: where a bid earns even with no edge on Novig's own price (RESEARCH.md §70.2). */
        val MAKER_DEFAULT_KINDS = setOf(BetKind.PROP, BetKind.PERIOD, BetKind.TEAM_TOTAL)

        /** [pinnLiveHoldoffSeconds]' choices. */
        val PINN_HOLDOFF_CHOICES = listOf(0, 3, 5, 8, 12, 20)

        /** [sharpConfirmMaxAgeSeconds]' choices. */
        val SHARP_MAX_AGE_CHOICES = listOf(60, 120, 180, 300)

        /** [sharpConfirmMinEv]'s choices (0 = any +EV). */
        val SHARP_MIN_EV_CHOICES = listOf(0.0, 0.01, 0.02, 0.03)

        /** [sharpVetoMinEv]'s choices (0 = any +EV). */
        val SHARP_VETO_MIN_EV_CHOICES = listOf(0.0, 0.005, 0.01, 0.015, 0.02)

        /** [autoBetBooks]' choices (the last is "5+"). */
        val AUTO_BET_BOOKS_CHOICES = listOf(2, 3, 4, 5)

        /** [autoLockMinPercent]'s choices: the least a lock must pay, as a share of what's staked in the market. */
        val AUTO_LOCK_MIN_CHOICES = listOf(0.005, 0.01, 0.02, 0.03, 0.05, 0.10)

        /** [autoBetMinOdds]' choices (0 = no limit). */
        val AUTO_BET_MIN_ODDS_CHOICES = listOf(0, -150, -200, -250, -300, 100)

        /** [makerMaxOdds]' choices (0 = no limit); a typed amount of +100 or more is also allowed. */
        val MAKER_MAX_ODDS_CHOICES = listOf(100, 110, 120, 130, 140, 150, 175, 200, 250, 300, 0)

        /** The shortest-odds chips every odds setting shares (0 = no limit; negative = nothing shorter than it; +100 = underdogs and even money only); any other value is typed. */
        val SHORTEST_ODDS_CHOICES = listOf(0, -300, -250, -200, -150, 100)

        /** [makerObscureMargin]'s chips. */
        val MAKER_OBSCURE_MARGIN_CHOICES = listOf(0.04, 0.05, 0.06, 0.08, 0.10)

        /** [makerObscureSharpMinEv]'s chips. */
        val MAKER_OBSCURE_SHARP_MIN_EV_CHOICES = listOf(0.02, 0.03, 0.04, 0.05)

        /** [makerObscureAgreePoints]' chips (probability, 0.02 = 2 points). */
        val MAKER_OBSCURE_AGREE_CHOICES = listOf(0.01, 0.02, 0.03, 0.05)

        /** [makerObscureMinBooks]' chips. */
        val MAKER_OBSCURE_MIN_BOOKS_CHOICES = listOf(2, 3, 4, 5)

        /** [makerObscureStake]'s chips (share of a popular bid's stake). */
        val MAKER_OBSCURE_STAKE_CHOICES = listOf(0.25, 0.5, 0.75, 1.0)

        /** [makerQuickMinBooks]' chips. */
        val MAKER_QUICK_MIN_BOOKS_CHOICES = listOf(3, 4, 5, 6, 8)

        /** [makerMinOdds]' chips. */
        val MAKER_MIN_ODDS_CHOICES = SHORTEST_ODDS_CHOICES

        /** [autoBetMaxOdds]' choices (0 = no limit); a typed amount of +100 or more is also allowed. */
        val AUTO_BET_MAX_ODDS_CHOICES = listOf(100, 110, 120, 130, 150, 200, 300, 0)

        /** [autoBetTwoSided]'s choices. */
        val AUTO_BET_TWO_SIDED_CHOICES = listOf(1, 2, 3)

        /** [alertMinEv]'s choices (0 = off; Tj, 2026-09-28: "a minimum of 2%, 3%, or 4%"). */
        val ALERT_MIN_EV_CHOICES = listOf(0.0, 0.02, 0.03, 0.04)
    }
}

/**
 * Per-API switches (Tj, 2026-10-11: "toggle on and off any apis ... not using any API at all which is toggled off") and OddsPapi's every-book mode. [usePinnacle] stays the switch for the Pinnacle boards as a whole;
 * these turn the three hosts behind it off one by one: PinnWire, pinnapi and the Pinnodds account (its REST board and its live engine). [opAllBooks] (Tj, 2026-10-11: "use as many as possible to get fair odds averages"):
 * OddsPapi v5 prices the fair line with EVERY book the key carries, not only the reference books picked in Settings and the extras; each is devigged on its own and averaged, regional clones of one operator count
 * once, and wide-margin or stale books are still left out. On by default except [opAllBooks], as the app always was.
 */
@kotlinx.serialization.Serializable
data class ApiGates(
    val usePinnwire: Boolean = true,
    val usePinnapi: Boolean = true,
    val usePinnodds: Boolean = true,
    val opAllBooks: Boolean = false,
    /** OddsPapi Free (v4): billable requests a day the fair-odds feed may spend reading whole games (every book in one request); 0 = closing lines only ([com.tjshea.vigilant.data.reference.Op4Feed]). */
    val op4Fair: Int = 10,
    /** SportsGameOdds: price the fair line with every book it carries (82), not only the picked ones; falls back to the picked books for a league whose unfiltered query times out. */
    val sgoAllBooks: Boolean = false,
    /** PropLine: every sportsbook it lists (about 30, one request either way), not only the picked ones. */
    val propLineAllBooks: Boolean = false,
    /** Live games only mode (Tj, 2026-10-11): see [LiveOnlyMode]. */
    val liveOnly: LiveOnlyMode = LiveOnlyMode(),
    /** Cloud scan on / history saving to GitHub on (see [ScanSettings.cloudScan]). */
    val cloudScan: Boolean = false,
    val cloudSave: Boolean = true,
    /** The GitHub engine works only 08:00-01:00 America/New_York ([com.tjshea.vigilant.data.cloud.EngineHours]), Tj 2026-10-15; off = round the clock. */
    val cloudHours: Boolean = true,
    /**
     * What the GitHub engine does with the bets it lists (Tj, 2026-10-15, TCL20d): OFF = nothing, PAPER = decides and sizes them with the auto-bet's rules and writes the orders it would have sent to a ledger,
     * LIVE = sends them with the Vigilant subaccount's trading key. Set only on the phone ([com.tjshea.vigilant.data.cloud.CloudTrader]); the sizing numbers below are [com.tjshea.vigilant.data.cloud.CloudKelly]'s.
     */
    val cloudTrading: CloudTradeMode = CloudTradeMode.OFF,
    /** The share of full Kelly the engine stakes before its confidence shrink (0.25 = quarter Kelly). */
    val cloudKelly: Double = 0.25,
    /** The most on one bet, as a share of the bankroll (wallet plus what is at risk). */
    val cloudBetCap: Double = 0.05,
    /** The most on one game across all its markets, as a share of the bankroll. */
    val cloudGameCap: Double = 0.10,
    /** The most at risk at once, as a share of the bankroll (Tj allows up to 1.0). */
    val cloudWalletUse: Double = 0.9,
    /** Trading stops for the day when the bankroll is down this share since the day began (half stakes from half of it). */
    val cloudDayStop: Double = 0.40,
    /**
     * The GitHub engine also posts BIDS (Tj, 2026-10-16, TCL31): the phone's bid desk, unchanged, runs on the runner with the engine's trading key whenever [cloudTrading] is LIVE. Default on: the 6 h trap guard
     * holds back almost every taker bet on a game further out, and resting bids are how those games earn. The bid rules are the phone's own (bid slider, many, kinds, windows); the switch only decides WHERE they run.
     */
    val cloudBids: Boolean = true,
    /** The one main switch for every live feature and socket (Tj, 2026-10-14): see [LiveMasterMode]. */
    val liveMaster: LiveMasterMode = LiveMasterMode(),
    /**
     * BoltOdds Pro (Tj, 2026-10-12; BOLTODDS_API.md): the odds websocket as a fair-line source. On (and a key saved and answering) the scan reads the lines and props the socket has already delivered,
     * free of any per-call cost, beside every other API that is on. Off by default; off, the app is what it was and nothing is sent to BoltOdds.
     */
    val useBoltOdds: Boolean = false,
    /** SX Bet (SXBET_API.md, Tj 2026-10-18): an exchange read as a sharp fair-line source (markets public, books need a read-only key). Off by default; off, nothing is sent to SX Bet. */
    val useSxBet: Boolean = false,
    /** BoltOdds: price the fair line with every sportsbook it lists (not only the picked ones). */
    val boltAllBooks: Boolean = false,
    /**
     * Novig's live feed on its best-bid channel (`bbo`; Tj, 2026-10-12, NOVIG_API.md §26): every market a scan screens is held as its best bid and size (half the tokens, a fraction of the messages), and the
     * markets worth a bet, the live lane's and those with a bid of ours resting are held as the full book. Off = every market on the full `book` channel, exactly as before. The feed also puts itself back on
     * `book` when Novig refuses `bbo`, never sends it, or the check against the book disagrees (Diagnostics says so); turning this off and on again tries `bbo` afresh.
     */
    val novigBbo: Boolean = true,
    /** Keep the Novig live feed's connection open between scans and reconnect it by itself after a drop (off: it closes two minutes after a scan, as before). */
    val novigKeepOpen: Boolean = true,
    /**
     * The socket of each API that has one (Tj, 2026-10-13, Settings › APIs): ON = the app keeps that API's WebSocket open for real-time prices; OFF with the API itself still on = the regular
     * requests, no socket; the API off = never called. [novigSocket] is Novig's market feed (the Vigilant read key's `book`/`bbo` stream; off = the scan reads books by request); the order socket and the
     * orders push have their own switches below. [boltSocket]: BoltOdds' two odds sockets (off = its REST snapshot, read at most every 12 s). [pinnSocket]: the Pinnodds live socket (off = the live lane,
     * which needs it, waits; Pinnacle's REST boards are unaffected). [kalshiSocket]: Kalshi's `ticker` socket (needs the key; off = the signed or public REST read every scan).
     */
    val novigSocket: Boolean = true,
    val boltSocket: Boolean = true,
    val pinnSocket: Boolean = true,
    val kalshiSocket: Boolean = true,
    /**
     * Novig's private `orders` stream (NOVIG_API.md §26): an order's end (filled, cancelled, rejected) arrives the moment the matching engine has it, so a bet's result is not asked for every 0.4 s. Off = the
     * result is read by request as before; the REST fills stay the record either way.
     */
    val novigOrderPush: Boolean = true,
    /**
     * Taker orders (the Bet sheet, the auto-bet, a lock) placed over Novig's websocket instead of `POST /v3/orders` (Tj, 2026-10-12: "I want orders placed over the socket"; NOVIG_API.md §27): a
     * connection of its own, signed by the trading key, nothing to sign per order. Off = every order by REST. It also goes back to REST by itself, for a while, after any answer it cannot read.
     */
    val novigSocketOrders: Boolean = true,
    /**
     * Novig's live feed as raw-DEFLATE binary frames (`X-Novig-WS-Compress: deflate`, Tj, 2026-10-12: "If socket compression helps speed ... build that in"; NOVIG_API.md §27). A phone downloads about
     * 640 KB a second from Novig (v0.91.0's diagnostics), so a first snapshot of a thousand markets is a second or more; compressed it is a fraction. Off = plain text frames, as before. It turns
     * itself off for good (until switched off and on) if frames will not inflate.
     */
    val novigCompress: Boolean = true,
    /**
     * Profit-proven only (Tj, 2026-10-12): the auto-bet and the bid desk skip the types of bet and bid that have lost money in Tj's own bet history ([ProfitProfile]), the bids go up best type first, and a
     * bet by hand is warned. Default OFF: off changes nothing at all.
     */
    val profitProven: Boolean = false,
    /**
     * The repeat and hedge guard ([com.tjshea.vigilant.data.tracker.RepeatGuard], Tj, 2026-10-12): the auto-bet, the bid desk and the live engine place at most [repeatMaxBets] bets or bids and [repeatMaxDollars]
     * dollars on one side of one line (a new price on it is the same selection), and bet the other side of a market only while both sides together are a sure profit ([repeatHedge]). Default ON (a safety).
     * A limit of 0 is no limit on that; a bet by hand is warned, never blocked.
     */
    val repeatGuard: Boolean = true,
    /**
     * Freshest quote wins (Tj, 2026-10-12): when two feeds that are on both send the same book's price for one line, the one whose book-stamped time is newer by more than
     * [com.tjshea.vigilant.data.scanner.Pricing.FRESHER_BY_MS] is used; otherwise (or with no time) the feed order decides, as before. Off = the feed order alone.
     */
    /**
     * The money Tj has actually put in, all time (Tj, 2026-10-13): the base of the Tracker's "Profit % of money put in". 0 = not given: the Tracker replays his bets and uses the least deposit that
     * would have placed them all with winnings re-bet ([com.tjshea.vigilant.data.tracker.MoneyIn]).
     */
    val moneyDeposited: Double = 0.0,
    val freshestWins: Boolean = true,
    /**
     * API study mode (Tj, 2026-10-12: "run tests to see which apis are the best for speed and accuracy"): every API that is switched on is asked for every league, ignoring the standby chain
     * ([com.tjshea.vigilant.data.scanner.ApiChain]), so Diagnostics' Source race can compare them all on the same scan. It spends the credits of every API that is on: leave it off except for a study.
     */
    val askAllSources: Boolean = false,
    /**
     * Rank the APIs by what they were measured to deliver ([SourceScore]): the freshest, most accurate answers first, the shipped order as the prior and as the fallback. Off = the shipped order only.
     */
    val rankByMeasure: Boolean = true,
    val repeatMaxBets: Int = com.tjshea.vigilant.data.tracker.RepeatGuard.DEFAULT_MAX_BETS,
    val repeatMaxDollars: Double = com.tjshea.vigilant.data.tracker.RepeatGuard.DEFAULT_MAX_DOLLARS,
    val repeatHedge: Boolean = true,
    /** Other lines of the same team or player in one game the auto-bet may hold ([com.tjshea.vigilant.data.tracker.RepeatGuard.family]; 0 = no limit; Tj, 2026-10-14). */
    val repeatMaxLines: Int = com.tjshea.vigilant.data.tracker.RepeatGuard.DEFAULT_MAX_LINES,
    /**
     * The streaming engine (Tj, 2026-10-12): a push on the Novig or BoltOdds socket starts a scan pass at once (free sources only: the BoltOdds board in memory and Novig's pushed books, no API credits), and the
     * auto-bet and the bid desk judge its result, instead of waiting for the next interval. Default OFF: off changes nothing. [streamGapSeconds] is the least time between two passes.
     */
    val streamScan: Boolean = false,
    val streamGapSeconds: Int = 3,
    /** Live games in the streaming engine: the real-time Pinnodds + Novig lane (live fee, 5 s freshness, the order's own delay in its worst price) with live bids. Real orders still need their own switches in Pinnodds live. */
    val streamLive: Boolean = false,
    /**
     * The live engine on BoltOdds' soft-book average (Tj, 2026-10-12; [PinnFeedChoice.BOLT]): the fewest companies that must agree, how recently each must have pushed (seconds), the widest they may disagree
     * (percentage points of probability), and whether REAL orders are allowed on it at all (off = paper only, whatever the live switches say).
     */
    val boltLiveMinCompanies: Int = 4,
    val boltLiveMaxAgeSeconds: Int = 10,
    val boltLiveMaxSpreadPoints: Int = 5,
    val boltLiveReal: Boolean = false,
) {
    /** The guard's rules for the placers, or null with the guard switched off. */
    fun repeatRules(): com.tjshea.vigilant.data.tracker.RepeatGuard.Rules? =
        if (repeatGuard) com.tjshea.vigilant.data.tracker.RepeatGuard.Rules(repeatMaxBets.coerceAtLeast(0), repeatMaxDollars.coerceAtLeast(0.0), repeatHedge, repeatMaxLines.coerceAtLeast(0)) else null
}

/**
 * "Live games only" mode (Tj, 2026-10-11): only the real-time lane runs (Pinnodds + Novig sockets), only live bets are listed, and everything the lane does not need is switched OFF. [on] is the mode;
 * the rest is what was on before, so that switching it off puts it all back ([com.tjshea.vigilant.app.LiveOnly] does both). The saved fields are only meaningful while [on].
 */
/**
 * What the Live features master switch remembers while it is OFF (Tj, 2026-10-14: "I need a way to turn off all live features and sockets on one main toggle"): every live lane, live bid and socket
 * switch as it was, so turning the master back ON puts each back exactly. [off] true = every live feature and socket is switched off.
 */
@kotlinx.serialization.Serializable
data class LiveMasterMode(
    val off: Boolean = false,
    val tailOn: Boolean = false,
    val tailBids: Boolean = false,
    val liveBid: Boolean = false,
    val pinnLive: Boolean = false,
    val includeLive: Boolean = false,
    val streamLive: Boolean = false,
    val streamScan: Boolean = false,
    val novigSocket: Boolean = true,
    val boltSocket: Boolean = true,
    val pinnSocket: Boolean = true,
    val kalshiSocket: Boolean = true,
)

/** What the GitHub engine does with the bets it lists ([ApiGates.cloudTrading]). */
@kotlinx.serialization.Serializable
enum class CloudTradeMode { OFF, PAPER, LIVE }

@kotlinx.serialization.Serializable
data class LiveOnlyMode(
    val on: Boolean = false,
    /** [com.tjshea.vigilant.app.ApiEntry] names that were on. */
    val apis: Set<String> = emptySet(),
    val scanner: String = "",
    val autoScan: String = "",
    val maker: Boolean = false,
    val autoBet: Boolean = false,
    val burstRecorder: Boolean = false,
    val feedRace: Boolean = false,
    val altLab: Boolean = false,
    val researchMode: Boolean = false,
    val burstTrade: Boolean = false,
    /** The Pinnacle boards' own switches, exactly as they were (an entry's `isOn` needs two of them). */
    val usePinnacle: Boolean = true,
    val pinnwire: Boolean = true,
    val pinnapi: Boolean = true,
)

/**
 * The bid guard (Tj, 2026-10-11: "I accidentally left a bad bid up indefinitely, and someone filled the bid and I lost almost immediately"): while [bidGuard] is on, any resting order with no expiry that has been up longer than
 * [bidGuardMinutes] is cancelled, whoever posted it ([com.tjshea.vigilant.data.novig.trading.BidGuard]). On by default.
 */
@kotlinx.serialization.Serializable
data class Guards(
    val bidGuard: Boolean = true,
    val bidGuardMinutes: Int = 30,
)

/** See [ScanSettings.guards]. */
@kotlinx.serialization.Serializable
data class ScanGuards(
    val oneVotePerCompany: Boolean = true,
    val ignoreBadOdds: Boolean = true,
    /** [ThinEarlyHaircut] (Tj, 2026-10-13): NHL shots-on-goal fair prices built from few books on a game far off are cut a few points. On by default. */
    val thinEarlyHaircut: Boolean = true,
    /** [PropHaircut] (Tj, 2026-10-14): a player-prop Over's fair is cut 7 points and an Under's 3 before anything reads it (the history: Overs won 39.7% against a 49.4% fair, t -4.2). Off in a bare ScanSettings() (a test's); the schema-18 migration turns it on for every real install, so it is on by default in the app and a later choice stays. */
    val propHaircut: Boolean = false,
    val autoBetVigilant: Boolean = true,
    /** The auto-bet places only bets rated at least this many stars ([BetRating]; Tj, 2026-10-11); 0 = any. */
    val autoBetMinStars: Double = 0.0,
    /**
     * Player-prop OVERS need this much more edge than the auto-bet's minimum (Tj's 2026-10-11 export: 456 settled Over props won 39.7% against a 49.2% fair chance, z = -3.4 by game, in all four sports
     * with a prop, while Unders won 47.1% against 50.1%, z = -1.3: books shade the public's side, so a devigged Over fair runs high; 0.03 = 3 points, RESEARCH.md §135). 0 = the same bar as every bet.
     * Not applied while a minimum star rating decides.
     */
    val autoBetOverPropExtraEv: Double = 0.03,
    /** Copy each day's bets and bids to Downloads/Vigilant/bets (the app keeps its own copy either way; Tj, 2026-10-11). */
    val betArchive: Boolean = true,
    /** The 1-100 safety/volume sliders' positions ([BetSlider], [BidSlider]); 0 = never set. The rules they set are ordinary settings: edit one and the slider reads Custom. */
    val betLevel: Int = 0,
    val bidLevel: Int = 0,
    /** The Bids slider's "More, smaller bids" mode ([BidSlider.many], Tj 2026-10-13): hundreds of 1/16-Kelly bids, proven types first, riskier ones under stricter rules. */
    val bidMany: Boolean = false,
    /**
     * Keep the wallet working (Tj, 2026-10-14: "I want the auto bids to fill up my wallet at all times. As long as the bids are placed at positive EV, it is fine. If there are not great bids to put up, make it put
     * up riskier bids but with safeguards such as higher EV"): while the bids resting are worth less than [bidDeployPct] percent of the wallet, every new bid's stake is scaled up (never past [bidDeployKelly]
     * percent of full Kelly or the per-bid maximum), and when that is still not enough, riskier bids go up too (games further out, a sharp book required) at a wider margin and half the stake.
     * Off = bids are sized exactly as before.
     */
    val bidDeploy: Boolean = true,
    val bidDeployPct: Int = 90,
    val bidDeployKelly: Int = 25,
    /**
     * The most Vigilant's BIDS put on one game, dollars and bids (Tj, 2026-10-14: "I only want so many bids/money on each game. Make it diversify bids among many games and bid types"). 0 = no limit.
     * Apart from [ScanSettings.apiMaxPerGame], which counts every bet and bid on a game together.
     */
    val bidPerGameDollars: Double = 10.0,
    val bidPerGameBids: Int = 10,
    /**
     * Bid on a league's lines while the scan is still collecting its odds (Tj, 2026-10-14: "The scan is huge and takes a long time, can't it put up bids progressively as the scan is going?"): a line is judged
     * with the fair odds in so far, under every rule a bid has (the fewest books, the sharp book, the margin), and re-priced as the rest arrive (a bid whose fair falls comes down). Off: a league waits until
     * every source has answered it, as the feed does.
     */
    val bidWhileScanning: Boolean = true,
    /**
     * The background scan alternates a full-reach scan (at most every 15 minutes) with a quick 48-hour one ([FarLane]), so the fair odds stay fresh for the bids and a scan stops taking 8 minutes (Tj, 2026-10-14:
     * "The scan takes way too long"). Off: every background scan reads the whole reach, as before.
     */
    val bidFarLane: Boolean = true,
    /**
     * Volume mode for the bids (Tj, 2026-10-14: "The key is volume ... hundreds or thousands of bids up ... 1.5% positive EV on the safest bid types and 2.5% on riskier ones ... most likely to be filled put up first"):
     * every bid at the most its EV floor allows, ordered by how fast that kind of bid has filled, up to 3,000 at once, stakes not scaled up (count, not size). Replaces the margins and the wallet-filling passes while on. Off in a bare ScanSettings() (a test's); the schema-21 migration turns it on for every real install, so a later choice stays.
     */
    val bidVolume: Boolean = false,
    /**
     * Set by the background scan only ([FarLane]): this pass reads just the next [FarLane.NEAR_HOURS] hours, the quick one that runs between the full-reach scans (Tj, 2026-10-14: "The scan takes way too long":
     * ten days is ~7,700 Novig prices and 8 minutes, by when the fair odds read at its start are too old to bid on). Never saved; a scan Tj starts is always the full reach.
     */
    @Transient val nearScan: Boolean = false,
    /**
     * The GitHub engine only, never saved (Tj, 2026-10-15: "Is this speed too slow for pre game betting and bidding?"): a long scan reads its fair odds once, at its start, and then spends minutes on Novig's books, so the
     * odds behind the bets it lists were 13-19 minutes old by the end (measured on the engine, 16 Novig reads a second, 9,619 books in 537 s). With this on, while the scan reads Novig, the games starting within [hotHours]
     * get their fair odds read again from the feeds that cost nothing per read or are flat-rate, and the Novig books of the likeliest +EV lines among them are read again, every [hotEverySeconds]: what Tj can bet soonest is
     * priced from odds a minute or two old instead of ten. The far games keep the scan's own pace. See `Scanner.hotFair` and `BookPump.hotStep`.
     */
    @Transient val hotRefresh: Boolean = false,
    @Transient val hotHours: Int = 12,
    @Transient val hotEverySeconds: Int = 45,
)
