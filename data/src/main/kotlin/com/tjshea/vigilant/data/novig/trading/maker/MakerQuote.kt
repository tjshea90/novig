package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.ProfitProfile
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds
import com.tjshea.vigilant.engine.PriceGrid
import java.util.Locale
import kotlin.math.floor

/**
 * How bids are posted (Tj, 2026-10-03: "figure out the optimal bets and math for make bets with the highest chance of beating clv and profiting, build
 * the system in the app"; then "it only will make bets which are positive EV, aiming for as much profit as possible … Make sure to implement the
 * strategies of proven professional bettors"; RESEARCH.md §69-§70), from [ScanSettings]: [margin] under the fair, sized by [stakeMode] (fractional
 * Kelly on [bankroll] like the auto-bet, a dollar, or [customStake]) and never over [maxStake], at most [maxBids] and [maxDollars] resting, on
 * [kinds], each resting [ttlMs] at most and never past the fair's own freshness, none within [stopMs] of the start, bid prices in
 * [minPrice]..[maxPrice], at least [minBooks] books each pricing the bid +EV on their own, and (with [sharpVeto]) no sharp book saying it isn't.
 */
data class MakerRules(
    val margin: Double,
    /** The amount [AutoBetStake.CUSTOM] bids. */
    val customStake: Double,
    val maxBids: Int,
    val maxDollars: Double,
    val kinds: Set<BetKind>,
    val ttlMs: Long,
    val stopMs: Long,
    val minPrice: Double,
    val maxPrice: Double,
    /**
     * The longest American odds a bid may be posted at ([ScanSettings.makerMaxOdds]; 0 = no limit): a bid priced under [priceAtOdds] of it is skipped, however good its EV
     * (Tj, 2026-10-05: "do not post bids longer than +140 odds"). Judged in [MakerQuote.outsideWindow], where the price window is, so every path that bids
     * (auto-make, a recommendation, a re-post, quick & likely) and the resting bids the plan keeps or takes down agree on it.
     */
    val maxOdds: Int = 0,
    /**
     * The shortest American odds a bid may be posted at ([ScanSettings.makerMinOdds]; 0 = no limit; negative: nothing shorter than −X, positive: underdogs at least +X): a bid
     * priced over [priceAtShortest] of it is skipped, judged in [MakerQuote.outsideWindow] beside [maxOdds].
     */
    val minOdds: Int = 0,
    val bothSides: Boolean,
    val minBooks: Int,
    val stakeMode: com.tjshea.vigilant.data.scanner.AutoBetStake = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM,
    /** The most one bid may cost (Tj's per-bet limit is the ceiling). */
    val maxStake: Double = customStake,
    /** What a Kelly stake is a fraction of (Settings' bankroll, as the auto-bet uses). */
    val bankroll: Double = 0.0,
    /** Skip a bid that a sharp book in the fair, devigged on its own, says isn't +EV (the auto-bet's sharp veto, RESEARCH.md §66). */
    val sharpVeto: Boolean = true,
    /**
     * The sharp veto's bar at the bid's own price ([ScanSettings.sharpVetoMinEv], as the auto-bet's veto): every sharp book in the fair must give the bid
     * at least this edge on its own (zero or less is always a veto). A filled bid keeps about the sharp book's edge, not the blend's (RESEARCH.md §72).
     */
    val sharpMinEv: Double = 0.0,
    /** A resting bid is moved up only when the bid wanted is at least this many grid steps higher (moving loses its place in the queue). */
    val requoteSteps: Int = 2,
    /** A bid this close to expiring is re-posted now (so a bid that's still good is always up). */
    val refreshBeforeMs: Long = REFRESH_BEFORE_MS,
    /** No bid is posted for less time than this (its fair about to go old, the start or the stop window too near). */
    val minLifeMs: Long = 60_000L,
    /**
     * How old the fair behind a bid on a FAR game ([MakerQuote.FAR_HOURS] or more out) may be before the bid must end, when longer than [Freshness]'s own limit (0 = that limit). Only the wallet-filling bids
     * set it (Tj, 2026-10-14: "They don't rest anywhere near long enough"): a game days away moves little in half an hour, and those bids sit 8+ points under the fair; the quotes behind the fair are still
     * the scan's fresh ones, only the bid's life after the post is longer.
     */
    val farFairMaxAgeMs: Long = 0L,
    /** Volume mode ([BidVolume]): the margin is an EV floor by type, the order is by fill speed, no wallet-filling passes or stake scale-up. */
    val volume: Boolean = false,
    /** Like [farFairMaxAgeMs] for a game 1 to [FAR_HOURS] hours out (0 = the ordinary limit). */
    val midFairMaxAgeMs: Long = 0L,
    /**
     * The most Vigilant's own BIDS put on one game (Tj, 2026-10-14: "I only want so many bids/money on each game. Make it diversify bids among many games and bid types"): dollars up on it, and bids
     * up on it (0 = no limit). Counted apart from Tj's open bets, which the per-game limit above covers.
     */
    val perGameBidDollars: Double = 0.0,
    val perGameBids: Int = 0,
    /**
     * The trap guard's first rule ([com.tjshea.vigilant.data.scanner.TrapGuard.early], RESEARCH.md §71): no bid on a game starting more than this
     * many hours from now (0 = off). The fair that far out is the least reliable (Tj's own bets placed then lost to the close), and 71% of prop
     * takers' dollars trade in the last 6 h anyway, so the wallet goes where the fills are.
     */
    val earlyHours: Int = 0,
    /**
     * The trap guard's second rule for game-line bids ([com.tjshea.vigilant.data.scanner.TrapGuard.move], [ScanSettings.trapNovigMove]): a line whose
     * Novig price just fell 2¢+ under where it traded this hour, with $100+ bought on the other side in 15 min, gets no bid (one resting there comes
     * down). RESEARCH.md §72: re-quoted game-line bids on a side whose price fell 2¢+ over the hour kept +1.2% at the close per fill against +6-7% on
     * steady lines (fair leading Novig by a quarter of the move); the same flow on props and 1st-half lines didn't hurt bids, so they aren't checked.
     */
    val novigMove: Boolean = false,
    /**
     * The most that may be at risk on one game across every market of it, open bets and resting bids ([ScanSettings.apiMaxPerGame], 0 = no limit;
     * [GameExposure]): auto-make posts no bid that would take a game past it (Tj, 2026-10-04: one event is one risk, however many lines it has).
     */
    val maxPerGame: Double = 0.0,
    /**
     * Bids on the kinds of market takers trade most go up first when not every wanted bid fits ([ScanSettings.makerPopularFirst], [MarketPopularity]); a kind never
     * measured counts as popular when [popularBooks] books or more price it.
     */
    val popularFirst: Boolean = true,
    val popularBooks: Int = ScanSettings.MAKER_POPULAR_BOOKS,
    /** A bid needs a sharp book that prices the line both ways and agrees, on any kind of bet, not game lines only ([ScanSettings.makerRequireSharp]). */
    val requireSharp: Boolean = false,
    /** The margin is under the lower of the blended fair and the sharpest book's own fair, not the blend alone ([ScanSettings.makerAnchorSharp]). */
    val anchorSharp: Boolean = true,
    /** Sharp-confirmed bids step their margin down toward [MakerQuote.SHARP_RELAX_FLOOR] as the game nears (not a Settings switch yet; tests turn it off). */
    val sharpRelax: Boolean = true,
    /** "Quick & likely to win" ([QuickLikely]): the rules are already narrowed, and the bids that go up first are the ones most likely to fill and win. */
    val quick: Boolean = false,
    /**
     * The most new bids one pass sends (Novig takes 8 orders a second; the rest wait for the next pass, a scan's own come every 20 s): with "unlimited" bids a busy
     * slate could want hundreds at once, and a pass that long would hold the lock the Pause, the kill switch and the fills' checks wait for.
     */
    val postsPerPass: Int = POSTS_PER_PASS,
    /**
     * "More, smaller bids" ([com.tjshea.vigilant.data.scanner.VigGuards.bidMany], Tj 2026-10-13): every bid is judged by its [BidTier] (proven types first, risky ones after under stricter
     * rules: [BidTier]) and the bids go up in that order. Off = nothing changes.
     */
    val tiered: Boolean = false,
    /**
     * Keep the wallet working ([com.tjshea.vigilant.data.scanner.VigGuards.bidDeploy]): the share of the wallet the resting bids should add up to (0 = off), the ceiling on a stake while it is scaled up (a fraction
     * of full Kelly), and what the desk works out each pass: [stakeScale], the multiple of the usual stake a new bid asks for (1 = usual; never past [maxStake] or [kellyCeiling]).
     */
    val deployPct: Double = 0.0,
    val deployMaxKelly: Double = 0.25,
    val stakeScale: Double = 1.0,
    val kellyCeiling: Double = 0.0,
    /**
     * Low API usage bids ([LowUsage], RESEARCH.md §92): the fair must be built from [lowUsageBooks] alone (their names as the fair line gives them: "Kalshi", "ProphetX"),
     * at least two of them, which is what the low-usage scan prices ([com.tjshea.vigilant.data.scanner.LowUsageBids.profile]); a line priced by an older full scan, with
     * a soft book or a sharp book that wasn't picked in it, waits for the next scan. Empty = the rule is off.
     */
    val lowUsageBooks: Set<String> = emptySet(),
    /** Skip the kinds of prop Novig's takers were measured to trade rarely ([MarketPopularity.measuredObscure]): the least likely to be filled. */
    val skipObscure: Boolean = false,
    /**
     * Quick & likely's filter on market size (Tj, 2026-10-07: "no strange props or small markets"): only kinds of market in the popular tiers ([MarketPopularity.tier] 0 or 1: measured at
     * [MarketPopularity.POPULAR] dollars a listed market a day or more, or never measured and priced by [popularBooks] books or more), never an obscure one. Orders nothing: it decides
     * which lines qualify. Off for low-usage bids, whose fair comes from two or three books by design.
     */
    val popularOnly: Boolean = false,
    /** A line priced by fewer books than this is a small market and gets no bid ([ScanSettings.makerQuickMinBooks]; 0 = off). */
    val minLineBooks: Int = 0,
    /**
     * Small-market fill ([ScanSettings.makerObscureFill]): a line that fails ONLY the popularity filters above ([smallMarketReason]) is not skipped but becomes a "small-market bid" with
     * its own, stricter checks (a margin of at least [obscureMargin], every sharp fair giving at least [obscureSharpMinEv], the sharp fairs and the blend within [obscureAgreePoints],
     * at least [obscureMinBooks] books, a stake of [obscureStake] of the normal one). Every other rule still binds it. Off by default; [MakerRules.of] turns it on with Quick & likely.
     */
    val obscureFill: Boolean = false,
    val obscureMargin: Double = 0.06,
    val obscureSharpMinEv: Double = 0.03,
    val obscureAgreePoints: Double = 0.02,
    val obscureMinBooks: Int = 3,
    val obscureStake: Double = 0.5,
    /** Which "Which bids go up" choice these rules are ([com.tjshea.vigilant.data.scanner.BidFocus] name): written on every bid, so Diagnostics can split the bids by it. */
    val focus: String = com.tjshea.vigilant.data.scanner.BidFocus.ALL.name,
    /**
     * Tennis (Tj, 2026-10-11: "for the bids it should still put up bids that are positive EV for tennis"): the rules a tennis line (ATP, WTA) is judged by when the Tennis switch is on. Tennis has one sharp
     * price (Pinnacle's; Kalshi, Polymarket and ParlayAPI match almost none of it) and no player props, so Quick & likely's kinds (props and team totals), its small-market filters and the two-book rule
     * would leave it without a single bid: tennis keeps Tj's own margin, price window, odds limits, stake and sharp rules and is judged by them alone. Null = tennis is judged like everything else.
     */
    val tennis: MakerRules? = null,
    /**
     * A bid sitting more than this far under the best bid on its side is not posted ([MAX_BEHIND]). Novig fills the best bid first; Tj's export of 2026-10-11 (22,234 bids posted) had 3,370 bids 3+ ticks
     * (1.5¢) behind the best bid and 4 of them filled (0.1%), against 1.5% of the 15,000 that led or sat level: they only added posts, cancels and Novig calls. 0 or less = no limit.
     */
    val maxBehind: Double = MAX_BEHIND,
    /** Settings › Profit-proven only: the profile of Tj's bet history (null = off, nothing changes). A bid on a type of bid that lost money is skipped, and the best types go up first. */
    val profit: ProfitProfile? = null,
    /** The repeat and hedge guard ([com.tjshea.vigilant.data.tracker.RepeatGuard]); null = off. A bid on a side already bet or bid to the limit, or on the other side of a market without a sure profit, is held back. */
    val repeat: com.tjshea.vigilant.data.tracker.RepeatGuard.Rules? = null,
) {
    /** The rules [line] is judged by: the tennis rules for an ATP or WTA line, else these. */
    fun forLine(line: MakerLine): MakerRules = if (tennis != null && line.league in TENNIS_LEAGUES) tennis else this

    companion object {
        val TENNIS_LEAGUES = setOf("ATP", "WTA")

        /** [maxBehind]'s default: two ticks (1¢) of Novig's half-cent grid. */
        const val MAX_BEHIND = 0.01

        /**
         * [refreshBeforeMs]'s default: a bid is re-posted from a fresher fair once it is this close to its end, so a scan that STARTS this long before the oldest bid's
         * fair goes old keeps it up ([com.tjshea.vigilant.data.scanner.LowUsageBids.autoGapSeconds] is the freshness limit minus this).
         */
        const val REFRESH_BEFORE_MS = 2 * 60_000L

        /** [postsPerPass]'s default. */
        const val POSTS_PER_PASS = 60

        /** [postsPerPass] with "More, smaller bids" on: hundreds of small bids go up in a few passes (Novig takes 8 orders a second; 120 is 15 s). */
        const val MANY_POSTS_PER_PASS = 120

        /** The shortest a longest-odds limit can be: +100 is even money; under it would mean "favorites only", which isn't what the option is for (as [com.tjshea.vigilant.data.novig.trading.AutoBet.MIN_MAX_ODDS]). */
        const val MIN_MAX_ODDS = com.tjshea.vigilant.data.novig.trading.AutoBet.MIN_MAX_ODDS

        /** The price (cost of a $1 payout) at which a bid pays American odds of [odds]: +140 pays $1.40 on $1, so $1 / $2.40 = 0.4167. 0 (no limit) = no floor. */
        fun priceAtOdds(odds: Int): Double = if (odds <= 0) 0.0 else 100.0 / (100.0 + odds)

        /** The price at which a bid pays [odds], either sign (−200 pays $0.50 on $1.00 staked: $1 / $1.50 = 0.6667). The ceiling a shortest-odds limit puts on a bid's price. */
        fun priceAtShortest(odds: Int): Double = if (odds == 0) 1.0 else 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(odds)

        /** The plain bid rules for tennis: Tj's own, game lines always on, one sharp book (Pinnacle) pricing both ways is enough, no popularity filters, whatever the focus. */
        private fun tennisRules(s: ScanSettings): MakerRules = base(s).copy(
            kinds = s.makerKinds + GAME_LINES + BetKind.TEAM_TOTAL, minBooks = 1, requireSharp = true, quick = false, skipObscure = false, popularOnly = false, minLineBooks = 0,
            obscureFill = false, lowUsageBooks = emptySet(), focus = s.makerFocus.name,
        )

        fun of(s: ScanSettings): MakerRules = withTennis(s, ofFocus(s)).let { if (s.vigGuards.bidVolume && s.makerSource != com.tjshea.vigilant.data.scanner.BidSource.CNO) volumeOf(it) else it }

        /** Volume mode's rules over the usual ones ([BidVolume]): thousands of bids, no start-time limit, the EV floors, the fair allowed to age a little longer while a bid rests. */
        private fun volumeOf(r: MakerRules): MakerRules = r.copy(
            volume = true, tiered = true, margin = BidVolume.SAFE_EV, maxBids = BidVolume.MAX_BIDS, maxDollars = Double.MAX_VALUE / 4, postsPerPass = BidVolume.POSTS_PER_PASS,
            earlyHours = 0, requireSharp = false, farFairMaxAgeMs = maxOf(r.farFairMaxAgeMs, 30 * 60_000L), midFairMaxAgeMs = BidVolume.MID_FAIR_MAX_AGE_MS,
        )

        private fun withTennis(s: ScanSettings, r: MakerRules): MakerRules =
            if (s.tennisOn && (s.maker || s.makerRecommend) && s.makerSource != com.tjshea.vigilant.data.scanner.BidSource.CNO) r.copy(tennis = tennisRules(s)) else r

        private fun ofFocus(s: ScanSettings): MakerRules = base(s).let { r ->
            when {
                // Bids priced from CrazyNinjaOdds ([CnoMakerLines]): Quick & likely narrows them too, Low API usage (Vigilant's scan on a few books) means nothing without that scan.
                s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.CNO ->
                    CnoMakerLines.narrow(if (QuickLikely.on(s)) QuickLikely.withObscure(QuickLikely.narrow(r, s.makerQuickMinBooks), s) else r).copy(focus = s.makerFocus.name)
                LowUsage.on(s) -> LowUsage.narrow(r, s).copy(focus = s.makerFocus.name)
                QuickLikely.on(s) -> QuickLikely.withObscure(QuickLikely.narrow(r, s.makerQuickMinBooks), s).copy(focus = s.makerFocus.name)
                else -> r.copy(focus = s.makerFocus.name)
            }
        }

        private fun base(s: ScanSettings): MakerRules {
            val maxOdds = s.makerMaxOdds.let { if (it <= 0) 0 else it.coerceAtLeast(MIN_MAX_ODDS) }
            val minOdds = com.tjshea.vigilant.data.novig.trading.AutoBet.normalizeMinOdds(s.makerMinOdds)
            return baseRules(s, maxOdds, minOdds)
        }

        private fun baseRules(s: ScanSettings, maxOdds: Int, minOdds: Int) = MakerRules(
            profit = if (s.gates.profitProven) ProfitProfile.shipped else null,
            repeat = s.gates.repeatRules(),
            margin = s.makerMargin.coerceIn(0.005, 0.5),
            customStake = s.makerStake.coerceAtLeast(0.01),
            maxBids = s.makerMaxBids.coerceAtLeast(0),
            maxDollars = s.makerMaxDollars.coerceAtLeast(0.0),
            kinds = s.makerKinds,
            ttlMs = s.makerTtlMinutes.coerceIn(1, 24 * 60) * 60_000L,
            stopMs = s.makerStopMinutes.coerceAtLeast(0) * 60_000L,
            // The price window (10-65%) has no control of its own: a longest or shortest odds Tj picked beyond it widens it, so the chip he taps is never a dead one.
            minPrice = s.makerMinPrice.coerceIn(0.001, 0.999).let { if (maxOdds > 0) minOf(it, priceAtOdds(maxOdds)) else it },
            maxPrice = s.makerMaxPrice.coerceIn(0.001, 0.999).let { if (minOdds != 0) maxOf(it, priceAtShortest(minOdds).coerceAtMost(0.999)) else it },
            maxOdds = maxOdds,
            minOdds = minOdds,
            bothSides = s.makerBothSides,
            minBooks = s.makerMinBooks.coerceAtLeast(1),
            stakeMode = s.makerStakeMode,
            // A filled bid is a bet: never more than the per-bet limit.
            maxStake = minOf(s.makerMaxStake, s.apiMaxStake).coerceAtLeast(0.01),
            bankroll = s.bankroll,
            sharpVeto = s.makerSharpVeto,
            sharpMinEv = s.sharpVetoMinEv.coerceIn(0.0, MAX_SHARP_MIN_EV),
            earlyHours = s.trapBidHours.coerceAtLeast(0),
            novigMove = s.trapNovigMove,
            maxPerGame = s.apiMaxPerGame.coerceAtLeast(0.0),
            perGameBidDollars = s.vigGuards.bidPerGameDollars.coerceAtLeast(0.0),
            perGameBids = s.vigGuards.bidPerGameBids.coerceAtLeast(0),
            popularFirst = s.makerPopularFirst,
            tiered = s.vigGuards.bidMany,
            // The target is always the most the bids may be worth: the desk's wallet is already cash x "most the bids up may be worth" (MakerRunner.forBids), so 100% of it (Tj, 2026-10-21: "always target the maximum").
            deployPct = if (s.vigGuards.bidDeploy) 1.0 else 0.0,
            deployMaxKelly = s.vigGuards.bidDeployKelly.coerceIn(5, 100) / 100.0,
            postsPerPass = if (s.vigGuards.bidMany) MANY_POSTS_PER_PASS else POSTS_PER_PASS,
            requireSharp = s.makerRequireSharp,
            anchorSharp = s.makerAnchorSharp,
        )

        /**
         * The fair a bid's margin is taken from (RESEARCH.md §88.3): the lower of [blend] and the sharpest book's own fair in [sharpFairs] (each devigged the worst
         * way) when [anchorSharp] is on; [blend] when it's off or no sharp book is in the fair. Never above [blend].
         */
        fun anchorOf(blend: Double, sharpFairs: List<Double>, anchorSharp: Boolean): Double =
            if (anchorSharp && sharpFairs.isNotEmpty()) minOf(blend, sharpFairs.min()) else blend

        /** Game lines (moneylines, spreads, game totals): bid on only with a sharp book in the fair (RESEARCH.md §70.2). */
        val GAME_LINES = setOf(BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL)

        /** The highest bar a saved setting can set for the bids' sharp veto (a bid's whole margin is 4% by default). */
        const val MAX_SHARP_MIN_EV = 0.10
    }
}

/** One side of one Novig market Vigilant has priced: what a bid on it is judged from. */
data class MakerLine(
    val market: NovigMarket,
    val outcomeId: String,
    /** The game's start: the earlier of the event's and the market's. */
    val startsTs: Long,
    val league: String,
    val eventName: String,
    val marketLabel: String,
    val selection: String,
    val kind: BetKind,
    /** Vigilant's fair probability for this side; null when it has none. */
    val fair: Double?,
    /** When the oldest book price behind [fair] was seen. */
    val fairAsOfMs: Long?,
    /** When the newest one was: a fair with a price seen after a bid filled has at least some news of the fill ([MakerDesk]'s fill check). */
    val fairNewestMs: Long? = null,
    /** The fair is too old to bet on now (the app's freshness rule for this game). */
    val fairOld: Boolean,
    /** Books behind [fair]. */
    val books: Int,
    /** Their names as the fair line gives them ("Kalshi", "FanDuel"): what low-usage bids check against the books Tj picked ([MakerRules.lowUsageBooks]). Empty = not known. */
    val fairBooks: List<String> = emptyList(),
    /** Why the fair rests on too few small books with the same odds to bid on ([com.tjshea.vigilant.engine.ThinFair]); null = fine or not known. */
    val thinFair: String? = null,
    /** Each book's own fair for this side, devigged worst case (one per book): what "books agree" counts. Empty = not known. */
    val bookFairs: List<Double> = emptyList(),
    /** The same for the sharp books in the fair (Pinnacle, Circa, the exchanges Settings calls sharp). */
    val sharpFairs: List<Double> = emptyList(),
    /** Novig's price to take this side now (1 − the best bid on the other side); null when nothing is offered. */
    val offer: Double?,
    /** The best resting bid on this side now (what a new bid has to beat to lead). */
    val bestBid: Double?,
    val live: Boolean,
    val source: String,
    val basis: FairBasis? = null,
    val gameUrl: String? = null,
    /** When Novig's book behind [offer] and [bestBid] was read (it can be the last scan's: [MakerLines.MAX_BOOK_AGE_MS]). */
    val bookAtMs: Long? = null,
    /** Every resting bid on this side in that book, best first ([bestBid] is the first): what [MakerLines.withoutOwn] takes Vigilant's own out of. */
    val bidLevels: List<com.tjshea.vigilant.data.novig.BidLevel> = emptyList(),
    /** What Novig's newest trades say about this side's price now ([MakerRules.novigMove]; game lines only); null = not read. */
    val novigMove: com.tjshea.vigilant.data.scanner.TrapGuard.Move? = null,
    /** The read of Novig's trades for this side's market was tried and FAILED (429, 451, no route): [MakerQuote.decide] skips it with the move rule on (Tj, 2026-10-07, proposal 10). */
    val moveUnread: Boolean = false,
    /**
     * Why nobody should bid on this line, or null: the player is out ([com.tjshea.vigilant.data.reference.PlayerOut], Tj 2026-10-07: "she isn't playing ... yet the
     * auto bid feature offered bids on her"). [MakerQuote.precheck] skips it, so no bid goes up and the plan takes down any that already did.
     */
    val unavailable: String? = null,
    /**
     * The longest the oldest data behind [fair] may be, in ms, for a CNO-priced line ([CnoMakerLines], [ScanSettings.makerCnoMaxAgeSeconds]); null = the app's freshness rule for
     * Vigilant's scan ([Freshness.maxAgeMs]). [MakerQuote.precheck] caps a bid's life at [fairAsOfMs] + this.
     */
    val fairMaxAgeMs: Long? = null,
    /**
     * CNO-priced lines: when CNO's list, and the game page whose books made the fair, were last updated on CNO's own side (their "Last Updated", [com.tjshea.vigilant.data.cno.CnoSnapshot.dataAtMs]);
     * null = not from that read. [fairAsOfMs] is the older of the two. A bid keeps the ages it was posted with ([MakerBid.listAgeSec]).
     */
    val listAtMs: Long? = null,
    val pageAtMs: Long? = null,
) {
    val marketId: String get() = market.marketId
}

/** The lines a bid can be judged on: every side Vigilant's scan priced ([com.tjshea.vigilant.data.scanner.Opportunity] keeps them all, +EV or not). */
object MakerLines {

    /** The kind of bet an outcome is, as [MakerRules.kinds] and the auto-bet name them. */
    fun kindOf(o: com.tjshea.vigilant.data.scanner.Opportunity): BetKind = when {
        o.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP -> BetKind.PROP
        o.kind == com.tjshea.vigilant.data.reference.LineKind.TEAM_TOTAL -> BetKind.TEAM_TOTAL
        o.market.marketType in com.tjshea.vigilant.data.scanner.MarketFamily.FIRST_HALF.novigTypes -> BetKind.PERIOD
        o.kind == com.tjshea.vigilant.data.reference.LineKind.MONEYLINE -> BetKind.MONEYLINE
        o.kind == com.tjshea.vigilant.data.reference.LineKind.SPREAD -> BetKind.SPREAD
        o.kind == com.tjshea.vigilant.data.reference.LineKind.TOTAL -> BetKind.TOTAL
        else -> BetKind.OTHER
    }

    /**
     * The oldest Novig book a line may be judged with. A bid's price comes from the fair alone; Novig's book only says whether the bid would cross
     * its offer (a post-only bid that would is refused whole, nothing fills) and what's already bid. So a scan still reading Novig's books can bid on
     * every line whose fair odds are in, with the book its last scan read, instead of waiting minutes for each book to be read again (Tj, 2026-10-03:
     * "I had auto make bids turned on, but it didn't actually make any bids by itself": a scan took 8 minutes and bids waited for its end).
     */
    const val MAX_BOOK_AGE_MS = 20 * 60_000L

    /**
     * Volume mode ([BidVolume]), a game 6 hours or more out: the Novig book may be this old. The bid is priced from the fair; the book only says whether the bid would cross Novig's offer (a post-only bid that
     * would is refused whole, nothing is lost) and what is already bid. Far games' books are read late in a full scan and not at all in a quick one (Tj, 2026-10-14: 93 bids up of thousands possible).
     */
    const val VOLUME_FAR_BOOK_AGE_MS = 90 * 60_000L

    /**
     * [result]'s priced sides in [settings]' leagues at [now], pregame, as [MakerLine]s: on a scan still running, the leagues whose fair-odds sources
     * have all answered (as the feed holds them back), each with the newest Novig book read in the last [MAX_BOOK_AGE_MS]. Each book's own fair
     * ([bookFairs], a devig per book) is worked out only for the lines that pass [MakerQuote.precheck] (a busy slate prices 13,000 sides, and a pass
     * runs every 20 s while a scan does). [always]: sides whose books' fairs are worked out whatever precheck says (the ones Vigilant has a bid or a fill on, so a
     * fill is judged against the sharp books too).
     */
    fun from(
        result: com.tjshea.vigilant.data.scanner.ScanResult?,
        settings: ScanSettings,
        now: Long,
        always: Set<String> = emptySet(),
        /** Why a side must not be bid on (a player who is out: [com.tjshea.vigilant.data.reference.PlayerOut.forOpportunity]), or null. */
        unavailable: (com.tjshea.vigilant.data.scanner.Opportunity) -> String? = { null },
    ): List<MakerLine> {
        result ?: return emptyList()
        val rules = MakerRules.of(settings)
        return result.opportunities.filter { o ->
            inScope(o, result, settings) && o.bookFetchedAtMs != null && now - o.bookFetchedAtMs <= maxBookAgeMs(o, rules, now)
        }.map { o ->
            val line = lineOf(o, now, unavailable(o))
            // Each book's own fair only where a bid could follow: every other line is skipped before [MakerQuote.decide] asks for them.
            if (line.outcomeId !in always && MakerQuote.precheck(line, rules, now) is MakerQuote.Pre.No) line else withFairs(line, o)
        }
    }

    /** Whether [o] is a side a bid could be judged on at all: in the picked leagues, pregame, with a fair, and (on a scan still running) in a league whose fair-odds sources have all answered. */
    fun inScope(o: com.tjshea.vigilant.data.scanner.Opportunity, result: com.tjshea.vigilant.data.scanner.ScanResult, settings: ScanSettings): Boolean =
        o.league.novigName in settings.leagues && !o.isLive && o.fairProbability != null &&
            (result.waitingFor.isEmpty() || settings.vigGuards.bidWhileScanning ||
                com.tjshea.vigilant.data.scanner.ScanResult.waitKey(o.league.novigName, o.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP) !in result.waitingFor)

    /** The oldest Novig book [o] may be judged with at [now]: [MAX_BOOK_AGE_MS], or [VOLUME_FAR_BOOK_AGE_MS] for a far game in Volume mode. */
    fun maxBookAgeMs(o: com.tjshea.vigilant.data.scanner.Opportunity, rules: MakerRules, now: Long): Long =
        if (rules.volume && minOf(o.event.startsTs, o.market.startsTs) - now >= 6 * 3_600_000L) VOLUME_FAR_BOOK_AGE_MS else MAX_BOOK_AGE_MS

    /** [o] as a bid line, without each book's own fair ([withFairs] adds them where a bid could follow). */
    fun lineOf(o: com.tjshea.vigilant.data.scanner.Opportunity, now: Long, unavailable: String?): MakerLine = MakerLine(
        market = o.market, outcomeId = o.outcome.outcomeId, startsTs = minOf(o.event.startsTs, o.market.startsTs), league = o.league.displayName,
        eventName = o.event.description, marketLabel = o.marketLabel, selection = o.selection, kind = kindOf(o), fair = o.fairProbability,
        fairAsOfMs = o.fairAsOfMs, fairNewestMs = o.fair?.usedUpdates?.first, fairOld = o.fairIsOld(now), books = o.fair?.booksUsed?.size ?: 0,
        fairBooks = o.fair?.booksUsed.orEmpty(), thinFair = com.tjshea.vigilant.engine.ThinFair.reason(o.fair), offer = o.quote?.price,
        bestBid = o.bestBid, live = o.isLive, source = com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT,
        basis = FairBasis.of(o), bookAtMs = o.bookFetchedAtMs, bidLevels = o.bidLevels, unavailable = unavailable,
    )

    /** [line] with each book's own fair and the sharp books' ([bookFairs]), worked out from [o]'s fair line. */
    fun withFairs(line: MakerLine, o: com.tjshea.vigilant.data.scanner.Opportunity): MakerLine = line.copy(bookFairs = bookFairs(o, sharpOnly = false), sharpFairs = bookFairs(o, sharpOnly = true))

    /**
     * [line] with Novig's side taken from a newer [book]: the price to take, the best bid, the bids behind it and when it was read. The same four fields [com.tjshea.vigilant.data.scanner.Pricing.requote]
     * gives an [com.tjshea.vigilant.data.scanner.Opportunity] (`MakerLinesTest` proves they are equal), so a line made on one machine from the fair odds can be given Novig's book on another. A market
     * Novig has closed or put in play has nothing to take, as in [com.tjshea.vigilant.data.scanner.Pricing.requote].
     */
    fun withBook(line: MakerLine, book: com.tjshea.vigilant.data.novig.NovigBook): MakerLine {
        val closed = book.lifecycle?.let { it.closed || it.live == true } == true
        val ladder = if (closed) emptyList() else book.takeLadder(line.market, line.outcomeId)
        return line.copy(
            offer = ladder.firstOrNull()?.price, bestBid = book.bestBid(line.outcomeId)?.price,
            bidLevels = book.bidsByOutcome[line.outcomeId].orEmpty(), bookAtMs = book.fetchedAtMs,
        )
    }

    /**
     * [lines] with each one's [MakerLine.bestBid] leaving out Vigilant's own [bids] that rested on that side when its book was read: a bid of ours is
     * not one a new bid has to beat to lead (Tj's v0.53.0 file: "led their side 41%", counted against the very bids being re-posted). Our contracts
     * at a price are taken off that level of the book; the best level with any left is the best bid. A bid posted within [OWN_SETTLE_MS] of the read
     * may not be in it yet and isn't taken off (it then counts as someone else's: the safe side).
     */
    fun withoutOwn(lines: List<MakerLine>, bids: List<MakerBid>): List<MakerLine> {
        val mine = bids.filter { it.orderId != null }.groupBy { it.outcomeId }
        if (mine.isEmpty()) return lines
        return lines.map { l ->
            val at = l.bookAtMs ?: return@map l
            val own = HashMap<Int, Long>()
            for (b in mine[l.outcomeId].orEmpty()) {
                if (b.postedAtMs + OWN_SETTLE_MS <= at && (b.endedAtMs ?: Long.MAX_VALUE) > at) own.merge(Math.round(b.price * 1000).toInt(), b.contracts - b.filled, Long::plus)
            }
            if (own.isEmpty() || l.bidLevels.isEmpty()) return@map l
            val others = l.bidLevels.firstOrNull { it.contracts - (own[it.priceMilli] ?: 0L) > 0 }?.price
            if (others == l.bestBid) l else l.copy(bestBid = others)
        }
    }

    /** How long after posting a bid is taken to be in the books Novig answers with ([withoutOwn]). */
    const val OWN_SETTLE_MS = 2_000L

    /**
     * Each book behind [o]'s fair, devigged on its own the worst way for this side (the lowest of multiplicative, additive, power and Shin, as the
     * app's agreement check does: [com.tjshea.vigilant.data.scanner.Agreement]), one per book; only the sharp ones with [sharpOnly].
     */
    /**
     * The sides [MakerRules.novigMove] reads Novig's trades for (RESEARCH.md §72): moneylines, spreads and game totals that would otherwise be bid on (their
     * kind switched on for bids, a sharp book in the fair, a price Novig offers, and every check [MakerQuote.precheck] makes). Empty with the rule off.
     */
    fun moveWanted(lines: List<MakerLine>, rules: MakerRules, now: Long): List<MakerLine> = if (!rules.novigMove) emptyList() else lines.filter {
        it.kind in com.tjshea.vigilant.data.scanner.TrapGuard.MOVE_KINDS && it.kind in rules.kinds && it.offer != null && it.sharpFairs.isNotEmpty() &&
            MakerQuote.precheck(it, rules, now) is MakerQuote.Pre.Price
    }

    /**
     * [lines] with what [trades] (Novig's newest, by market) say about each side in [wanted] at Novig's price to take it now ([MakerLine.offer]): the move
     * rule judges the price the market shows, not the bid under it. A market with no trades read is left as it was (judged without the rule).
     */
    fun withMoves(
        lines: List<MakerLine>, wanted: List<MakerLine>, trades: Map<String, List<com.tjshea.vigilant.data.scanner.TrapGuard.Trade>>, now: Long, unread: Set<String> = emptySet(),
    ): List<MakerLine> {
        if (wanted.isEmpty()) return lines
        val ids = wanted.mapTo(HashSet()) { it.outcomeId }
        return lines.map { l ->
            val got = trades[l.marketId]
            val offer = l.offer
            when {
                l.outcomeId !in ids -> l
                got == null -> if (l.marketId in unread) l.copy(moveUnread = true) else l
                offer == null -> l
                else -> l.copy(novigMove = com.tjshea.vigilant.data.scanner.TrapGuard.move(got, l.outcomeId, offer, now))
            }
        }
    }

    fun bookFairs(o: com.tjshea.vigilant.data.scanner.Opportunity, sharpOnly: Boolean): List<Double> {
        val fair = o.fair ?: return emptyList()
        val side = o.referenceIndex ?: return emptyList()
        val used = fair.booksUsed.toHashSet()
        return fair.perBook.asSequence()
            .filter { it.book.bookTitle in used && (!sharpOnly || it.isSharp) }
            // One book a company: the bid desk's "books agree" count and its sharp books are votes, and one trading desk is one vote (Tj, 2026-10-12).
            .distinctBy { com.tjshea.vigilant.engine.BookCompany.of(it.book.bookKey) }
            .mapNotNull { b ->
                val odds = b.book.decimalOdds
                if (odds.size < 2 || !odds.all { it > 1.0 && it.isFinite() }) return@mapNotNull null
                val raw = odds.map { 1.0 / it }
                val sum = raw.sum()
                runCatching { if (sum <= 1.0) raw.map { it / sum } else com.tjshea.vigilant.engine.Devig.worstCase(raw) }.getOrNull()?.getOrNull(side)
            }
            .toList()
    }
}

/** What [MakerQuote.decide] made of a line: a bid to post, or why not. */
sealed interface MakerDecision {
    val line: MakerLine

    /**
     * Post [contracts] at [price] (what they cost: [cost] dollars); [evAtFair] = fair / price − 1; [restUntilMs]: when it must be down (the expiry, the
     * fair's freshness and the stop window before the start, whichever comes first).
     */
    data class Post(
        override val line: MakerLine,
        val price: Double,
        val contracts: Long,
        /** The EV at the fair the margin was taken from: the anchor ([anchorFair]) when a sharp book lowered it, else the blend. */
        val evAtFair: Double,
        val restUntilMs: Long = Long.MAX_VALUE,
        /** The fair the margin is under ([MakerRules.anchorOf]); null = the line's own fair. */
        val anchorFair: Double? = null,
        /** A small-market bid ([MakerRules.obscureFill]): goes up only after every popular bid, and comes down to make room for one. */
        val obscure: Boolean = false,
        /** A riskier bid put up only to keep the wallet working ([MakerDesk.deployFor]): a wider margin, a sharp book required, half the stake; it is also [obscure] for the plan (last in, first out). */
        val overflow: Boolean = false,
    ) : MakerDecision {
        val cost: Double get() = contracts * price * EvMath.CONTRACT_PAYOUT_DOLLARS

        /**
         * Over every other bid on its side (or the only one): the first a taker on the other side reaches. Novig fills the best bid first, so a bid
         * behind another fills only once that one is used up.
         */
        val leads: Boolean get() = line.bestBid.let { it == null || it < price - 1e-9 }

        /** What this bid would put at risk, on its game ([GameExposure]). */
        val gameItem: GameExposure.Item
            get() = GameExposure.Item(GameRef(line.market.eventId, line.eventName, line.startsTs, line.league), line.market.marketId, line.outcomeId, cost)
    }

    data class Skip(override val line: MakerLine, val why: String) : MakerDecision
}

object MakerQuote {

    /** The words of a game line left without a bid because Novig's recent trades couldn't be read (the trap guard's move rule has nothing to judge it on). */
    const val MOVE_UNREAD = "Novig's recent trades couldn't be read, so this game line gets no bid (trap guard)"

    /**
     * The bid for [line] under [rules] at [now], or why there's none. [held]: outcomes already held or bet and still open (a filled bid is a bet;
     * the same side is never bought twice, like the auto-bet). The bid is fair / (1 + margin) floored to Novig's grid; it must stay under Novig's offer
     * (a post-only bid at or over it would be refused: that side is a bet to take now, the +EV feed's).
     */
    fun decide(line: MakerLine, allRules: MakerRules, now: Long, held: Set<String> = emptySet()): MakerDecision {
        val rules = allRules.forLine(line)
        var obscure = false
        val (blendPrice, until) = when (val pre = precheck(line, rules, now, held)) {
            is Pre.No -> return pre.skip
            is Pre.Price -> { obscure = pre.obscure; pre.price to pre.until }
        }
        // A small-market bid is posted under a wider margin: never under the normal one.
        val relaxed = sharpRelaxedMargin(line, rules, now, marginOf(line, rules, obscure), obscure)
        val margin = relaxed ?: marginOf(line, rules, obscure)
        fun skip(why: String) = MakerDecision.Skip(line, why)
        val blend = line.fair!!
        // The margin is under the lower of the blend and the sharpest book's own fair (only lines with their books' fairs worked out get here: [MakerLines.from]).
        val fair = MakerRules.anchorOf(blend, line.sharpFairs, rules.anchorSharp)
        val price = if (fair < blend - 1e-12 || relaxed != null) PriceGrid.floor(fair / (1.0 + margin)) ?: return skip("The sharp book's price is too small to bid under") else blendPrice
        outsideWindow(price, rules)?.let { return skip(it) }
        rules.profit?.judge(ProfitProfile.Instrument.BID, line.league, line.kind.label, line.selection, blend / price - 1.0, (line.startsTs - now) / 60_000L)?.blocked?.let { return skip("Not bid: $it") }
        if (line.kind in MakerRules.GAME_LINES && line.sharpFairs.isEmpty()) return skip("Game lines need a sharp book (Pinnacle, Circa …) in the fair")
        if (rules.requireSharp && line.sharpFairs.isEmpty()) return skip("No sharp book (Pinnacle, Circa, an exchange) prices this both ways, and a sharp book must agree (your setting)")
        // Books agree: each one's own fair (worst case) must put this bid at +EV, at least [minBooks] of them (the auto-bet's "books agree").
        val agreeing = if (line.bookFairs.isEmpty()) line.books else line.bookFairs.count { it > price + 1e-9 }
        if (agreeing < rules.minBooks) return skip("Only $agreeing book${if (agreeing == 1) "" else "s"} price this bid +EV on their own (fewest: ${rules.minBooks})")
        tierReason(line, rules, price, agreeing)?.let { return skip(it) }
        if (rules.novigMove && line.moveUnread) return skip(MOVE_UNREAD)
        if (rules.novigMove) line.novigMove?.let { m -> com.tjshea.vigilant.data.scanner.TrapGuard.moveReason(line.kind, m)?.let { return skip(it) } }
        if (rules.sharpVeto) {
            if (line.sharpFairs.any { it <= price + 1e-9 }) return skip("A sharp book's own price says this bid isn't +EV")
            if (line.sharpFairs.any { !com.tjshea.vigilant.data.scanner.SharpVeto.passes(it / price - 1.0, if (relaxed != null) minOf(rules.sharpMinEv, SHARP_RELAX_FLOOR) else rules.sharpMinEv) }) {
                return skip("A sharp book's own price gives this bid under the sharp veto's ${percent(rules.sharpMinEv)} edge")
            }
        }
        // The small-market safeguards (Tj, 2026-10-07: "sharp markets must agree and/or the positive EV must be a good margin"): a sharp book must price the line, every sharp book must give
        // the bid a real edge on its own, and the sharp books and the blend must sit close together. Each says which one stopped the line.
        if (obscure) {
            if (line.sharpFairs.isEmpty()) return skip("A small market gets a bid only where a sharp book (Pinnacle, Circa, an exchange) prices the line both ways")
            if (line.sharpFairs.any { it / price - 1.0 < rules.obscureSharpMinEv - 1e-9 }) {
                return skip("A sharp book's own price gives this small-market bid under ${percent(rules.obscureSharpMinEv)} edge (small markets need more)")
            }
            val all = line.sharpFairs + blend
            val spread = all.max() - all.min()
            if (spread > rules.obscureAgreePoints + 1e-9) {
                return skip("The sharp books and the blend are ${points(spread)} apart on this small-market line (they must be within ${points(rules.obscureAgreePoints)})")
            }
        }
        // Too far behind the best bid to fill (see [MakerRules.maxBehind]): the bid would rest for its whole life and never reach a taker.
        line.bestBid?.let { best ->
            if (rules.maxBehind > 0.0 && best - price > rules.maxBehind + 1e-9) {
                return skip("Priced ${points(best - price)} under the best bid, too far behind it to fill (more than ${points(rules.maxBehind)}): a smaller margin would climb")
            }
        }
        // A small-market bid stakes [MakerRules.obscureStake] of what a POPULAR bid on the line would (sized at the popular bid's own price: Kelly sized at the wider margin's lower
        // price would see a bigger edge and stake more, 0.74x of a popular bid at the default margin and 1.18x at the widest chip, not half).
        val popularPrice = if (obscure) maxOf(price, PriceGrid.floor(fair / (1.0 + rules.margin)) ?: price) else price
        val fullStake = stake(fair, popularPrice, rules, (line.startsTs - now) / 3_600_000.0) ?: return skip("No stake: ${rules.stakeMode.label} has nothing to bid here (no bankroll set?)")
        val stake = (if (obscure) fullStake * rules.obscureStake else fullStake) * (if (rules.tiered) BidTier.stakeShare(BidTier.of(line)) else 1.0)
        val contracts = floor(stake / (price * EvMath.CONTRACT_PAYOUT_DOLLARS) + 1e-9).toLong()
        if (contracts < 1) return skip("The stake is too small for one contract")
        return MakerDecision.Post(line, price, contracts, fair / price - 1.0, until, anchorFair = fair, obscure = obscure)
    }

    /** Lowest margin (under the sharp book's own fair) a sharp-confirmed bid steps down to (Tj, 2026-10-21, chat: "lower the EV gradually ... to a limit of 1.5% under the sharp book"). */
    const val SHARP_RELAX_FLOOR = 0.015
    /** The step-down runs from this many hours before the start (the usual margin) to [SHARP_RELAX_END_H] (the floor). */
    const val SHARP_RELAX_START_H = 12.0
    const val SHARP_RELAX_END_H = 2.0
    /** A sharp-confirmed read must be this fresh (ms) for the step-down. */
    const val SHARP_RELAX_FRESH_MS = 90_000L

    /**
     * The margin of a bid confirmed by a fresh read with a sharp book in the fair, stepped down toward [SHARP_RELAX_FLOOR] as the game nears; null = not applicable
     * (switch off, small market, volume or quick mode (their margins are their own), no sharp book, read not fresh, or [usual] already at or under the floor).
     */
    fun sharpRelaxedMargin(line: MakerLine, rules: MakerRules, now: Long, usual: Double, obscure: Boolean): Double? {
        if (!rules.sharpRelax || obscure || rules.volume || rules.quick || line.sharpFairs.isEmpty() || usual <= SHARP_RELAX_FLOOR) return null
        val newest = line.fairNewestMs ?: return null
        if (now - newest > SHARP_RELAX_FRESH_MS) return null
        val hours = (line.startsTs - now) / 3_600_000.0
        val t = ((SHARP_RELAX_START_H - hours) / (SHARP_RELAX_START_H - SHARP_RELAX_END_H)).coerceIn(0.0, 1.0)
        return usual + (SHARP_RELAX_FLOOR - usual) * t
    }

    /** The margin a bid on [line] is posted under: the rules' own, a small market's wider one, and (tiered bids) the tier's extra points. */
    fun marginOf(line: MakerLine, rules: MakerRules, smallMarket: Boolean): Double =
        if (rules.volume) BidVolume.margin(line, smallMarket) else (if (smallMarket) maxOf(rules.margin, rules.obscureMargin) else rules.margin) + (if (rules.tiered) BidTier.extraMargin(BidTier.of(line)) else 0.0)

    /** Why a bid in the steady or risky tier is stricter-checked out, or null: a sharp book must price it, and a risky bid needs every book to agree (see [BidTier]). */
    private fun tierReason(line: MakerLine, rules: MakerRules, price: Double, agreeing: Int): String? {
        if (!rules.tiered) return null
        if (rules.volume) return BidVolume.tierReason(line, agreeing, rules.minBooks)
        val tier = BidTier.of(line)
        if (tier == BidTier.PROVEN) return null
        if (line.sharpFairs.isEmpty()) return "A ${BidTier.label(tier)} needs a sharp book (Pinnacle, Circa, an exchange) pricing both sides to agree"
        if (tier == BidTier.STEADY) {
            return if (agreeing < rules.minBooks + 1) "A ${BidTier.label(tier)} needs ${rules.minBooks + 1} books on its side (only $agreeing)" else null
        }
        val priced = if (line.bookFairs.isEmpty()) line.books else line.bookFairs.size
        if (priced < BidTier.RISKY_MIN_BOOKS) return "A ${BidTier.label(tier)} needs at least ${BidTier.RISKY_MIN_BOOKS} books pricing it (only $priced)"
        if (agreeing < priced) return "A ${BidTier.label(tier)} needs every book that prices it to agree ($agreeing of $priced do)"
        if (line.sharpFairs.any { it / price - 1.0 < BidTier.RISKY_SHARP_MIN_EV - 1e-9 }) return "A sharp book's own price gives this ${BidTier.label(tier)} under ${percent(BidTier.RISKY_SHARP_MIN_EV)} edge"
        return null
    }

    /**
     * Why a bid at [price] is outside what Tj allows, or null: longer odds than his limit ([MakerRules.maxOdds]; said without the price, so the reports count it as one
     * reason), or outside the price window.
     */
    fun outsideWindow(price: Double, rules: MakerRules): String? = when {
        rules.maxOdds > 0 && price < MakerRules.priceAtOdds(rules.maxOdds) - 1e-9 ->
            "A bid at that price would be at longer odds than your ${Odds.formatAmerican(rules.maxOdds)} limit for bids"
        rules.minOdds != 0 && price > MakerRules.priceAtShortest(rules.minOdds) + 1e-9 ->
            "A bid at that price would be at shorter odds than your ${Odds.formatAmerican(rules.minOdds)} limit for bids"
        price < rules.minPrice - 1e-9 || price > rules.maxPrice + 1e-9 ->
            "A bid at ${percent(price)} is outside the price window (${percent(rules.minPrice)}-${percent(rules.maxPrice)})"
        else -> null
    }

    /** What [precheck] made of a line before the books are asked: no bid (why), or the bid's price and how long it may rest. */
    sealed interface Pre {
        data class No(val skip: MakerDecision.Skip) : Pre
        data class Price(val price: Double, val until: Long, val obscure: Boolean = false) : Pre
    }

    /**
     * [decide]'s checks that don't need each book's own fair: the start, the market, the kind, the fair and its age, the price window, Novig's offer.
     * [MakerLines.from] works out the books' fairs (a devig per book) only for lines that pass, on every pass with that pass's clock (a line too far
     * off now is judged again once its game is inside the trap guard's window).
     */
    fun precheck(line: MakerLine, allRules: MakerRules, now: Long, held: Set<String> = emptySet()): Pre {
        val rules = allRules.forLine(line)
        fun skip(why: String) = Pre.No(MakerDecision.Skip(line, why))
        if (line.live || now >= line.startsTs) return skip("The game has started (Novig cancels resting bids at the start)")
        if (now >= line.startsTs - rules.stopMs) return skip("Starts within ${rules.stopMs / 60_000} min: no bids this close")
        if (com.tjshea.vigilant.data.scanner.TrapGuard.isEarly(line.startsTs, now, rules.earlyHours)) {
            return skip("Starts in more than ${rules.earlyHours} h: no bids this early (trap guard)")
        }
        line.unavailable?.let { return skip(it) }
        if (line.market.status != "OPEN") return skip("Novig isn't taking orders on this market")
        if (line.kind !in rules.kinds) return skip("${line.kind.label} are off for bids")
        // The popularity filters: a line that fails one is skipped, or (small-market fill on) becomes a small-market bid under the stricter checks in [decide].
        val smallMarket = smallMarketReason(line, rules)
        if (smallMarket != null) {
            if (!rules.obscureFill) return skip(smallMarket)
            if (line.books < rules.obscureMinBooks) {
                return skip("Only ${line.books} book${if (line.books == 1) "" else "s"} price this small-market line (a small-market bid needs ${rules.obscureMinBooks}): too thin to bid on")
            }
        }
        if (rules.lowUsageBooks.isNotEmpty() && (line.fairBooks.size < LowUsage.MIN_BOOKS || line.fairBooks.any { it !in rules.lowUsageBooks })) {
            return skip(LowUsage.NOT_PRICED)
        }
        line.thinFair?.let { return skip(it.replaceFirstChar { c -> c.uppercase() }) }
        val fair = line.fair ?: return skip("No fair price")
        if (fair <= 0.0 || fair >= 1.0) return skip("No fair price")
        if (line.fairOld) return skip("The fair price is too old to bid on")
        val seen = line.fairAsOfMs ?: return skip("The fair price's age isn't known: no bid on it")
        // A bid never outlives what it was priced from: the fair's own freshness, the stop window before the start, and the expiry setting.
        val fairLife = line.fairMaxAgeMs ?: Freshness.maxAgeMs(line.startsTs, now).let { own ->
            when {
                rules.farFairMaxAgeMs > own && MakerPlan.farOf(line.startsTs, now) == 1 -> rules.farFairMaxAgeMs
                rules.midFairMaxAgeMs > own && line.startsTs - now >= 3_600_000L -> rules.midFairMaxAgeMs
                else -> own
            }
        }
        val until = minOf(now + rules.ttlMs, line.startsTs - rules.stopMs, seen + fairLife)
        if (until - now < rules.minLifeMs) return skip("The fair price goes old within a minute: re-priced at the next scan")
        if (line.outcomeId in held) return skip("Already bet or bid on this side")
        val price = PriceGrid.floor(fair / (1.0 + marginOf(line, rules, smallMarket != null))) ?: return skip("The fair price is too small to bid under")
        outsideWindow(price, rules)?.let { return skip(it) }
        val offer = line.offer
        if (offer != null && price >= offer - 1e-9) return skip("Novig already offers it at ${percent(offer)}, at or under this bid: take it instead")
        return Pre.Price(price, until, obscure = smallMarket != null)
    }

    /**
     * Why [line] is a small market under the popularity filters ([MakerRules.skipObscure], [MakerRules.popularOnly], [MakerRules.minLineBooks]), or null when it passes all of them. Without
     * [MakerRules.obscureFill] this is the reason a line gets no bid; with it, the line is a small-market bid under the stricter checks.
     */
    fun smallMarketReason(line: MakerLine, rules: MakerRules): String? = when {
        rules.skipObscure && MarketPopularity.measuredObscure(line.league, line.market.marketType) ->
            "Takers rarely trade this kind of prop on Novig: a bid on it is unlikely to be filled"
        rules.popularOnly && MarketPopularity.tier(line.league, line.market.marketType, line.books, rules.popularBooks) >= 2 ->
            "A small or unusual market: takers rarely trade this kind of line, so a bid on it is unlikely to be filled quickly"
        rules.minLineBooks > 0 && line.books < rules.minLineBooks ->
            "Only ${line.books} book${if (line.books == 1) "" else "s"} price this line (you need ${rules.minLineBooks}): a small market, unlikely to be filled quickly"
        else -> null
    }

    /**
     * What one bid stakes (dollars it costs if it fills), held to [MakerRules.maxStake]: a fraction of full Kelly on the bankroll for this bid's own
     * price and fair (`(fair − price) / (1 − price)`: makers pay no fee), a dollar, or the set amount. Null when Kelly has nothing to stake.
     */
    fun stake(fair: Double, price: Double, rules: MakerRules, hoursToStart: Double? = null): Double? {
        val wanted = when (rules.stakeMode) {
            com.tjshea.vigilant.data.scanner.AutoBetStake.ONE_DOLLAR -> 1.0
            com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM -> rules.customStake
            else -> {
                val f = rules.stakeMode.kelly ?: return null
                if (!(rules.bankroll > 0.0) || fair <= price || price >= 1.0) return null
                f * (fair - price) / (1.0 - price) * rules.bankroll * EvMath.leadScale(hoursToStart)
            }
        }
        // Scaled up to keep the wallet working (or down for a riskier bid): never past the per-bid maximum, and a scaled-UP stake never past the Kelly ceiling for this bid's own edge.
        var sized = wanted * rules.stakeScale
        if (rules.stakeScale > 1.0 && rules.kellyCeiling > 0.0 && rules.bankroll > 0.0 && fair > price && price < 1.0) {
            sized = maxOf(wanted, minOf(sized, rules.kellyCeiling * (fair - price) / (1.0 - price) * rules.bankroll * EvMath.leadScale(hoursToStart)))
        }
        return minOf(sized, rules.maxStake).takeIf { it > 0.0 }
    }

    /**
     * Every line's decision, with one side per market when [MakerRules.bothSides] is off: the cheaper side (the underdog's bid earns the most per bid,
     * RESEARCH.md §70.2).
     */
    fun decideAll(lines: List<MakerLine>, rules: MakerRules, now: Long, held: Set<String>): List<MakerDecision> {
        val all = lines.distinctBy { it.outcomeId }.map { decide(it, rules, now, held) }
        if (rules.bothSides) return all
        val keep = all.filterIsInstance<MakerDecision.Post>().groupBy { it.line.marketId }.values.mapTo(HashSet()) { posts -> posts.minBy { it.price }.line.outcomeId }
        return all.map { d ->
            if (d is MakerDecision.Post && d.line.outcomeId !in keep) MakerDecision.Skip(d.line, "One side per market (both sides is off): the other side's bid is cheaper") else d
        }
    }

    /** One grid step at [price] (NOVIG_API.md §7). */
    fun step(price: Double): Double = if (price <= 0.050 + 1e-9 || price >= 0.950 - 1e-9) 0.001 else 0.005

    private fun percent(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)

    private fun points(p: Double) = String.format(Locale.US, "%.1f point%s", p * 100, if (Math.round(p * 1000) == 10L) "" else "s")
}

/** One of Vigilant's bids resting on Novig now (from [MakerStore] and Novig's open orders). */
data class RestingBid(
    val orderId: String,
    val marketId: String,
    val outcomeId: String,
    val price: Double,
    /** Contracts still resting. */
    val remaining: Long,
    /** Contracts already filled (a partly filled bid is part bet). */
    val filled: Long,
    val expiresAtMs: Long?,
    /** Posted by auto-make (false: Tj approved it by hand: the last to come down when the wallet can't hold every bid, [MakerPlan.plan]'s trim). */
    val auto: Boolean = true,
    /** The EV at the fair when it was posted, and whether it led its side then: what ranks it when the wallet can't hold every bid and no fresh line judges it. */
    val evAtFair: Double = 0.0,
    val leads: Boolean = true,
    /** A small-market bid ([MakerRules.obscureFill]): trimmed before any popular bid, and taken down to make room for one ([MakerPlan.MADE_ROOM]). */
    val obscure: Boolean = false,
    /** The game it is on, for the per-game limit ([MakerRules.maxPerGame]); null = not known (it counts toward no game). */
    val game: GameRef? = null,
) {
    val restingDollars: Double get() = remaining * price * EvMath.CONTRACT_PAYOUT_DOLLARS

    /** What it would put at risk on its game if it filled; null when its game isn't known. */
    val gameItem: GameExposure.Item? get() = game?.let { GameExposure.Item(it, marketId, outcomeId, restingDollars) }
}

/**
 * What one cycle does: cancel these (with why), then place these. [waiting]: bids wanted that weren't posted this pass, by why (the most bids or
 * dollars up at once, the wallet or the day's limit), so the tab can say why auto-make isn't posting them.
 */
data class MakerActions(
    val cancels: List<Pair<RestingBid, String>>,
    val places: List<MakerDecision.Post>,
    val kept: List<RestingBid>,
    val waiting: Map<String, Int> = emptyMap(),
    /** How many of [cancels] are bids the wallet (or the day's limit) no longer covers ([MakerPlan.TRIMMED]). */
    val trimmed: Int = 0,
)

object MakerPlan {

    /**
     * Resting bids against the bids wanted now. [stopAll]: every bid comes down (paused, wallet empty, daily limit, bids switched off). A resting bid is
     * cancelled when its line is no longer wanted (with that line's reason from [skips]), moved when the fair fell under it (it would now be over the
     * fair minus the margin: the bid that gets picked off, §70.3) or rose by [MakerRules.requoteSteps] steps or more, and re-posted when it's about to
     * expire with a fresher fair behind it; a partly filled bid isn't re-posted (that side is now a bet). New bids go in by [priority] (those that lead
     * their side, then the popular lines, then the cheapest), within [MakerRules.maxBids], [MakerRules.maxDollars] and [budget] (the wallet and the day's limit, less what's up).
     *
     * [budget] may be negative: bids up are worth more than the wallet (or the day's limit) covers (Tj, 2026-10-04: "Vigilant wallet $8.98 · 7 bids up
     * ($16.14)": a bet by hand, an auto-bet or a fill took money out after the bids went up, and Novig holds nothing for a resting bid). The bids that
     * don't fit come down, the least valuable first ([TRIMMED]); a trimmed side isn't posted again this pass.
     */
    fun plan(
        wanted: List<MakerDecision.Post>,
        resting: List<RestingBid>,
        rules: MakerRules,
        now: Long,
        skips: Map<String, String> = emptyMap(),
        stopAll: String? = null,
        budget: Double = Double.MAX_VALUE,
        /**
         * False with auto-make off: bids Tj approved by hand are only taken down when they stop being worth it (the fair fell under the bid, its line
         * isn't wanted any more); nothing new is posted, nothing is moved up or re-posted (each bid is the one he approved).
         */
        repost: Boolean = true,
        /**
         * The lines come from a scan still running (its leagues arrive one at a time): a resting bid whose line this pass didn't judge (neither
         * [wanted] nor in [skips]) is left up; its `ttl` already ends it when its fair goes old, and a later pass judges it. On a finished scan a bid
         * whose line is gone comes down.
         */
        partial: Boolean = false,
        /**
         * The last start the scan that judged these lines reached (a quick scan, [com.tjshea.vigilant.data.scanner.FarLane]): a resting bid on a game that starts after it was not judged at all, so it stays
         * up until its own expiry and the next full-reach scan judges it. Null: every game is in reach.
         */
        reachUntilMs: Long? = null,
        /** What Tj's open bets have at risk, for the per-game limit ([MakerRules.maxPerGame]): bids add to it, never replace it ([GameExposure.items]). */
        heldItems: List<GameExposure.Item> = emptyList(),
        /** Bids whose cancel is in flight ([MakerDesk]): they can still be on Novig's book, so the wash guard counts them with [resting]. */
        onTheWay: List<RestingBid> = emptyList(),
        /** What Tj's open bets hold, for the repeat and hedge guard ([com.tjshea.vigilant.data.tracker.RepeatGuard.fromBets]); the bids that stay up are added here. */
        held: List<com.tjshea.vigilant.data.tracker.RepeatGuard.Pos> = emptyList(),
    ): MakerActions {
        if (stopAll != null) return MakerActions(resting.map { it to stopAll }, emptyList(), emptyList())
        val byOutcome = wanted.associateBy { it.line.outcomeId }
        val cancels = ArrayList<Pair<RestingBid, String>>()
        val kept = ArrayList<RestingBid>()
        val noRepost = HashSet<String>()
        for (r in resting) {
            val w = byOutcome[r.outcomeId]
            if (w == null && partial && r.outcomeId !in skips) {
                kept += r
                continue
            }
            // A game past this scan's reach was not judged: the bid stays until its own expiry (a quick scan reads only the next 48 hours).
            if (w == null && r.outcomeId !in skips && reachUntilMs != null && (r.game?.startsTs ?: 0L) > reachUntilMs && (r.expiresAtMs == null || r.expiresAtMs > now)) {
                kept += r
                continue
            }
            // A fair that is merely too old THIS pass (a scan in flight has not refreshed it yet) does not take a bid down: the bid's own expiry, set from the fair it was priced from, ends it
            // (Tj, 2026-10-14: "every minute or less it takes hundreds of bids back down"). Only a bid still inside its life stays.
            if (w == null && transientSkip(skips[r.outcomeId]) && (r.expiresAtMs == null || r.expiresAtMs > now)) {
                kept += r
                continue
            }
            val why = when {
                w == null -> skips[r.outcomeId] ?: "No longer a bid to post"
                w.price < r.price - 1e-9 -> if (repost) "The fair price fell: re-posted lower" else "The fair price fell under the bid: taken down"
                // A wallet-filling bid (small-market flag) stays put through small rises of the fair: it is posted to rest, and moving it loses its place for a gain of a tick or two.
                repost && w.price >= r.price + (if (r.obscure) rules.requoteSteps * WALLET_FILL_REQUOTE_FACTOR else rules.requoteSteps) * MakerQuote.step(r.price) - 1e-9 -> "The fair price rose: re-posted higher"
                // Only when the new bid would rest at least [MakerRules.minLifeMs] longer (a fresher fair): re-posted from the same fair it would end at
                // the same moment and only lose its place in the queue (Tj's v0.53.0 file: 163 of 289 bids re-posted every pass, a minute's rest each).
                repost && r.expiresAtMs != null && r.expiresAtMs - now <= rules.refreshBeforeMs && w.restUntilMs - r.expiresAtMs >= rules.minLifeMs -> "About to expire: re-posted"
                // Belt and braces (Tj, 2026-10-12: every resting bid is re-checked against each scan and comes down the moment it is not +EV): a bid priced at or over the fair this pass worked out
                // (the anchor when a sharp book lowered it) has no edge left, whatever else says it can stay.
                r.price >= (w.anchorFair ?: w.line.fair ?: 1.0) - 1e-9 -> "No longer +EV: its price is at or over the fair"
                else -> null
            }
            if (why == null) {
                kept += r
                continue
            }
            cancels += r to why
            if (r.filled > 0) noRepost += r.outcomeId
        }
        // What the bids still up may add up to: the budget is what's left beside every bid not yet ended (the ones coming down included, which can
        // still fill), so the resting ones may hold their own dollars plus whatever the budget has left, which is under zero when the money fell short.
        // Hand-approved first, then the ones that lead their side, the cheaper, the more EV: a bid that outranks another but doesn't fit comes down,
        // and a lower one that does fit stays.
        var trimmed = 0
        val room = budget + resting.sumOf { it.restingDollars }
        if (kept.sumOf { it.restingDollars } > room + 1e-9) {
            val worth = compareBy<RestingBid> { it.auto }
                // Volume mode: the bids that fill fastest are the last to come down when the money falls short.
                .thenByDescending { r -> if (rules.volume) byOutcome[r.outcomeId]?.let { BidVolume.score(it.line, it.leads, now, it.price) } ?: 0.0 else 0.0 }
                .thenBy { it.obscure }
                // Many-bids mode: the riskiest tier's bids come down first when the money falls short.
                .thenBy { if (rules.tiered) byOutcome[it.outcomeId]?.line?.let { l -> BidTier.of(l) } ?: BidTier.PROVEN else 0 }
                .thenByDescending { byOutcome[it.outcomeId]?.leads ?: it.leads }
                // Far games' bids come down first when the money falls short: a bid 12 h or more out fills about a third as often per quote (RESEARCH.md §119.3).
                .thenBy { farOf(byOutcome[it.outcomeId]?.line?.startsTs ?: it.game?.startsTs, now) }
                .thenBy { it.price }
                .thenByDescending { byOutcome[it.outcomeId]?.evAtFair ?: it.evAtFair }
            var left = room
            val fits = HashSet<String>()
            for (r in kept.sortedWith(worth)) {
                if (r.restingDollars <= left + 1e-9) {
                    fits += r.orderId
                    left -= r.restingDollars
                }
            }
            val drop = kept.filter { it.orderId !in fits }
            kept.removeAll(drop.toSet())
            for (r in drop) {
                cancels += r to TRIMMED
                noRepost += r.outcomeId
            }
            trimmed = drop.size
        }
        if (!repost) return MakerActions(cancels, emptyList(), kept, trimmed = trimmed)
        val covered = kept.mapTo(HashSet()) { it.outcomeId } + noRepost
        // [budget] still counts every bid cancelled this pass as up. A side's replacement may use the dollars its own cancelled bid frees (the desk
        // places a replacement only once that cancel is confirmed gone); no other side may, since a cancel can still be filled before it lands.
        val freed = cancels.associate { (r, _) -> r.outcomeId to r.restingDollars }
        val bids = kept.size
        val dollars = kept.sumOf { it.restingDollars }
        val spend = budget
        // One game is one event: what is at risk on each game now (open bets, the bids that stay up) and what this pass adds, market by market.
        val onGames = if (rules.maxPerGame > 0.0) heldItems + kept.mapNotNull { it.gameItem } else emptyList()
        // Every bid of ours that may be on the book while this pass places: the ones up (the ones this pass cancels too: a cancel can fail or lag), the ones coming
        // down, and each bid this pass places. A new bid is held back when it and one of these on the OTHER side of its market add up to $1 or more ([WASH]).
        val onBook = HashMap<String, MutableList<Pair<String, Double>>>()
        for (r in resting + onTheWay) onBook.getOrPut(r.marketId) { ArrayList() } += r.outcomeId to r.price
        val places = ArrayList<MakerDecision.Post>()
        val waiting = HashMap<String, Int>()
        fun wait(why: String) = waiting.merge(why, 1, Int::plus)
        // What a pass has left to spend on bids: the bids up, the dollars they hold, the wallet, and what each game already has at risk.
        class Room(var bids: Int, var dollars: Double, var spend: Double, var games: List<GameExposure.Item>, var pos: List<com.tjshea.vigilant.data.tracker.RepeatGuard.Pos>,
            var gameBids: Map<String, Int> = emptyMap(), var gameDollars: Map<String, Double> = emptyMap())
        // The repeat guard's picture: Tj's open bets, and every bid that stays up as if it filled (it adds to the other-side test but not to the count of bets).
        val keptPos = if (rules.repeat != null) held + kept.map { com.tjshea.vigilant.data.tracker.RepeatGuard.Pos(it.marketId, it.outcomeId, it.restingDollars, it.remaining * EvMath.CONTRACT_PAYOUT_DOLLARS, count = 0) } else emptyList()
        val state = Room(bids, dollars, spend, onGames, keptPos, BidSpread.bidsPerGame(kept), BidSpread.dollarsPerGame(kept))
        /** Why [w] can't go up in [r] (the most bids, the pass's own limit when [passCapped], the most dollars, the per-game limit, the wallet), or null. */
        fun capacity(w: MakerDecision.Post, r: Room, passCapped: Boolean): String? = when {
            r.bids >= rules.maxBids -> MAX_BIDS_REACHED.format(rules.maxBids)
            passCapped && places.size >= rules.postsPerPass -> PASS_FULL.format(rules.postsPerPass)
            r.dollars + w.cost > rules.maxDollars + 1e-9 -> MAX_DOLLARS_REACHED.format(money(rules.maxDollars))
            rules.maxPerGame > 0.0 && GameExposure.check(w.gameItem.game, r.games, w.gameItem.marketId, w.gameItem.outcomeId, w.cost, rules.maxPerGame).blocked ->
                GAME_REACHED.format(money(rules.maxPerGame))
            rules.perGameBids > 0 && (r.gameBids[w.line.market.eventId] ?: 0) >= rules.perGameBids -> GAME_BIDS_REACHED.format(rules.perGameBids)
            rules.perGameBidDollars > 0.0 && (r.gameDollars[w.line.market.eventId] ?: 0.0) + w.cost > rules.perGameBidDollars + 1e-9 -> GAME_BID_DOLLARS_REACHED.format(money(rules.perGameBidDollars))
            rules.repeat != null && com.tjshea.vigilant.data.tracker.RepeatGuard.check(rules.repeat, r.pos, w.line.marketId, w.line.outcomeId, w.cost, w.contracts * EvMath.CONTRACT_PAYOUT_DOLLARS, w.line.market.outcomes.map { it.outcomeId }) != null -> REPEAT_GUARD
            w.cost > r.spend + (freed[w.line.outcomeId] ?: 0.0) + 1e-9 -> BUDGET_REACHED
            else -> null
        }
        fun take(w: MakerDecision.Post, r: Room) {
            if (rules.maxPerGame > 0.0) r.games = r.games + w.gameItem
            if (rules.repeat != null) r.pos = r.pos + com.tjshea.vigilant.data.tracker.RepeatGuard.Pos(w.line.marketId, w.line.outcomeId, w.cost, w.contracts * EvMath.CONTRACT_PAYOUT_DOLLARS, count = 0)
            r.bids++
            r.gameBids = r.gameBids + (w.line.market.eventId to ((r.gameBids[w.line.market.eventId] ?: 0) + 1))
            r.gameDollars = r.gameDollars + (w.line.market.eventId to ((r.gameDollars[w.line.market.eventId] ?: 0.0) + w.cost))
            r.dollars += w.cost
            r.spend -= (w.cost - (freed[w.line.outcomeId] ?: 0.0)).coerceAtLeast(0.0)
        }
        // Each game's best bids first and every game in turn, the kinds of market within a game in turn (Tj, 2026-10-14: diversify among many games and bid types): the priority order kept as the tiebreak.
        val ordered = BidSpread.interleave(wanted.filter { it.line.outcomeId !in covered }.sortedWith(priority(rules, now)))
        // The popular bids first (Tj, 2026-10-07: popular large markets first, then, if there is room, obscure ones). A popular bid held up by the room is remembered.
        val roomShort = ArrayList<MakerDecision.Post>()
        for (w in ordered.filter { !it.obscure }) {
            if (wouldTrade(w, onBook)) { wait(WASH); continue }
            val why = capacity(w, state, passCapped = true)
            if (why != null) {
                wait(why)
                if (why != PASS_FULL.format(rules.postsPerPass)) roomShort += w
                continue
            }
            onBook.getOrPut(w.line.marketId) { ArrayList() } += w.line.outcomeId to w.price
            places += w
            take(w, state)
        }
        // Which of those waiting popular bids WOULD go up if every small-market bid we placed by ourselves came down? Only they make room: a popular bid that doesn't fit even
        // then (dearer than the wallet or the dollar limit, held up by its game's limit through other bids) never costs a small-market bid its place or holds the others back.
        // Then the fewest, least valuable small-market bids that make room for all of those come down (the popular bids go up on the next pass: a cancelled bid's dollars
        // aren't spendable until the cancel has landed, it can still fill).
        val removable = kept.filter { it.obscure && it.auto }
        fun without(down: Collection<RestingBid>): List<MakerDecision.Post> {
            val games = state.games.toMutableList()
            for (d in down) d.gameItem?.let { games.remove(it) }
            val remaining = kept.filter { k -> down.none { d -> d.orderId == k.orderId } }
            val r = Room(state.bids - down.size, state.dollars - down.sumOf { it.restingDollars }, state.spend + down.sumOf { it.restingDollars }, games, state.pos.filter { p -> p.count > 0 || down.none { d -> d.outcomeId == p.outcomeId } },
                BidSpread.bidsPerGame(remaining) + state.gameBids.filterKeys { it !in BidSpread.bidsPerGame(kept).keys }, BidSpread.dollarsPerGame(remaining) + state.gameDollars.filterKeys { it !in BidSpread.dollarsPerGame(kept).keys })
            return roomShort.filter { w -> (capacity(w, r, passCapped = false) == null).also { ok -> if (ok) take(w, r) } }
        }
        var popularWaits = false
        if (roomShort.isNotEmpty() && removable.isNotEmpty()) {
            val fit = without(removable)
            if (fit.isNotEmpty()) {
                popularWaits = true
                val order = removable.sortedWith(compareBy<RestingBid> { it.leads }.thenBy { it.evAtFair })
                var n = 0
                while (n < order.size && without(order.take(n)).size < fit.size) n++
                for (r in order.take(n)) {
                    cancels += r to MADE_ROOM
                    kept.remove(r)
                }
            }
        }
        // The small-market bids behind them, with what the popular ones left over; held back while a popular bid that could go up waits for room.
        for (w in ordered.filter { it.obscure }) {
            when {
                wouldTrade(w, onBook) -> wait(WASH)
                popularWaits -> wait(POPULAR_WAITING)
                else -> {
                    val why = capacity(w, state, passCapped = true)
                    if (why != null) { wait(why); continue }
                    onBook.getOrPut(w.line.marketId) { ArrayList() } += w.line.outcomeId to w.price
                    places += w
                    take(w, state)
                }
            }
        }
        return MakerActions(cancels, places, kept, waiting, trimmed)
    }

    /** Which new bids are recommended first (auto-make off): popular ones before any small-market one, then the cheapest, then the most edge. */
    val RECOMMEND_ORDER: Comparator<MakerDecision.Post> = compareBy<MakerDecision.Post> { it.obscure }.thenBy { it.price }.thenByDescending { it.evAtFair }

    /**
     * Whether [w] would trade with a bid of ours on the other side of its market ([onBook]: market to (outcome, price)): a buyer of one side at P is a seller of
     * the other at 1 − P, so two bids on the two sides that add up to $1 or more meet (a wash: Novig sends a fill, the position doesn't change, NOVIG_API.md §17;
     * the Maker Credit terms name self-matching orders). Never with a margin under a coherent fair (the two sides' fairs add up to $1 and each bid is under its own),
     * so this only fires when a fair moved far between two passes.
     */
    fun wouldTrade(w: MakerDecision.Post, onBook: Map<String, List<Pair<String, Double>>>): Boolean =
        onBook[w.line.marketId].orEmpty().any { (outcome, price) -> outcome != w.line.outcomeId && price + w.price >= 1.0 - 1e-9 }

    /**
     * Which wanted bids go up first when the most bids, the most dollars or the wallet can't take them all: the ones that would lead their side (a
     * bid behind another fills only after it: Tj's v0.53.0 file, 0 fills with 59% of the bids behind one), then the cheapest (the underdog side earns
     * the most per bid, RESEARCH.md §70.2), then the most EV.
     */
    val PRIORITY: Comparator<MakerDecision.Post> = compareByDescending<MakerDecision.Post> { it.leads }.thenBy { it.price }.thenByDescending { it.evAtFair }

    /**
     * [PRIORITY] with Tj's popular tilt (2026-10-04: "more attractive bets that involve bets that are more popular than obscure players props"): the ones that lead
     * their side first, then the kinds of market takers trade most ([MarketPopularity.tier]: hot, popular, obscure, by the dollars Novig's takers put in a listed market of
     * that kind a day; a kind never measured by how many books price the line), then the cheapest and the most EV as before. Never changes which bids qualify, only which
     * go up when the bids, the dollars or the wallet run out.
     */
    fun priority(rules: MakerRules, now: Long = System.currentTimeMillis()): Comparator<MakerDecision.Post> {
        // Volume mode: the bids that fill fastest go up first ([BidVolume.score]); the edge breaks a tie.
        if (rules.volume) return compareByDescending<MakerDecision.Post> { BidVolume.score(it.line, it.leads, now, it.price) }.thenByDescending { it.evAtFair }
        val base = if (rules.tiered) compareBy<MakerDecision.Post> { BidTier.of(it.line) }.thenBy { it.obscure } else compareBy<MakerDecision.Post> { it.obscure }
        // Profit-proven only: the types of bid with the best history go up first (a neutral or unknown type scores 0, so nothing is held back for want of data).
        val byProfit = rules.profit?.let { pf ->
            compareByDescending<MakerDecision.Post> { pf.judge(ProfitProfile.Instrument.BID, it.line.league, it.line.kind.label, it.line.selection, it.evAtFair, (it.line.startsTs - now) / 60_000L).score }
        }
        return (byProfit?.let { base.then(it) } ?: base).then(popularPriority(rules, now))
    }

    /**
     * A game this far from its start (hours) is "far" for the order: its bid goes up after every nearer one when the wallet, the dollar limit or the most bids can't take them
     * all (RESEARCH.md §119.3). Tj's own bids: 30 min-2 h CLV +3.6% (10 closes), 6-24 h +2.7% (14); Novig's trade files (§72.3): a bid's EV per quote is +0.040% at 6-12 h and
     * +0.014% at 12-25 h with 1% filling; takers' CLV on props 12-24 h out -0.7% (n=272) against +1.6% at 1-6 h (n=190). It never decides which bids qualify.
     */
    const val FAR_HOURS = 12

    /** 1 for a game [FAR_HOURS] or more from its start at [now], else 0 (an unknown start counts as near: nothing is held back for a time nobody knows). */
    fun farOf(startsTs: Long?, now: Long): Int = if (startsTs != null && startsTs - now >= FAR_HOURS * 3_600_000L) 1 else 0

    private fun popularPriority(rules: MakerRules, now: Long): Comparator<MakerDecision.Post> = when {
        // Quick & likely to win: leading their side first (takers reach them first), then the games that start soonest (far ones last), then the kinds takers trade most, then the
        // likeliest fill (the price band's rate), then the most edge against the book that moves first. Not the cheapest: a longshot's bid fills sooner but is not what this is for.
        rules.quick -> compareByDescending<MakerDecision.Post> { it.leads }.thenBy { farOf(it.line.startsTs, now) }.thenBy { tierOf(it, rules) }
            .thenByDescending { QuickLikely.fillChancePerHour(it.price) }.thenByDescending { it.evAtFair }
        !rules.popularFirst -> compareByDescending<MakerDecision.Post> { it.leads }.thenBy { farOf(it.line.startsTs, now) }.thenBy { it.price }.thenByDescending { it.evAtFair }
        else -> compareByDescending<MakerDecision.Post> { it.leads }.thenBy { farOf(it.line.startsTs, now) }.thenBy { tierOf(it, rules) }.thenBy { it.price }.thenByDescending { it.evAtFair }
    }

    /** [MarketPopularity.tier] of the market a bid is on. */
    fun tierOf(p: MakerDecision.Post, rules: MakerRules): Int = MarketPopularity.tier(p.line.league, p.line.market.marketType, p.line.books, rules.popularBooks)

    /** Why a wanted bid waits (the tab and Diagnostics say how many each). */
    const val MAX_BIDS_REACHED = "the most bids up at once (%d) is reached"
    const val MAX_DOLLARS_REACHED = "the most dollars up at once (%s) is reached"
    const val BUDGET_REACHED = "the wallet (or today's limit for API bets) can't cover it beside the bids already up"
    const val REPEAT_GUARD = "the repeat guard holds it back: this side is already bet or bid to your limit, or the other side of its market is held and both sides would not be a sure profit"
    const val GAME_REACHED = "the most at risk on one game (%s) is reached"
    const val GAME_BIDS_REACHED = "the most bids on one game (%d) is reached"
    const val GAME_BID_DOLLARS_REACHED = "the most bid dollars on one game (%s) is reached"

    /** A wallet-filling bid is moved up only when the fair rose this many times [MakerRules.requoteSteps] steps. */
    const val WALLET_FILL_REQUOTE_FACTOR = 3

    /** A line skipped for a reason that says only that the fair is not fresh THIS pass: a resting bid on it stays until its own expiry. */
    fun transientSkip(why: String?): Boolean = why != null && (why.startsWith("The fair price goes old within") || why.startsWith("The fair price is too old") || why.startsWith("The fair price's age isn't known"))
    const val WASH = "it would trade with your own bid on the other side of that market (a wash: the two prices add up to \$1 or more); it goes up once that bid is gone"
    const val PASS_FULL = "a pass sends at most %d new bids; the rest go up on the next pass"

    /** A small-market bid waits (popular bids first, Tj 2026-10-07), and a small-market bid already up comes down so a popular one can go up. */
    const val POPULAR_WAITING = "a popular bid is waiting for the room: small-market bids only go up when there is room left over"
    const val MADE_ROOM = "Made room for a popular bid (small-market bids go up only with room to spare)"

    /** Why a bid that was up came down because the money behind it fell short (the bid's own reason in the Bids tab). */
    const val TRIMMED = "The wallet (or today's limit for API bets) no longer covers it beside the other bids up: taken down"

    private fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)
}
