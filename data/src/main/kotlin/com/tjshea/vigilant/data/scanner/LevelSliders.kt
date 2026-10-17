package com.tjshea.vigilant.data.scanner

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The 1-100 safety / volume sliders of the Auto-bet and Bids tabs (Tj, 2026-10-11: "1 is the strictest settings with lowest volume but safest, highest EV ... and 100 is the riskiest but most volume ... while still maintaining
 * positive EV ... make sure the steps from 1-100 make sense ... automatically change any settings in the app needed"). One number sets every selection rule together, so they always agree; each rule moves one way only, in small steps.
 * Not owned (your money and your on/off choices): bankroll, the dollar limits, how much one bid or bet may cost, whether auto-bet / bids are on, the leagues, the stake rule. Floors that keep every level positive-EV: a bid is never
 * posted less than [BidSlider.MIN_MARGIN] under the fair, an auto-bet never takes less than [BetSlider.MIN_EV] edge, and at least two books always agree.
 */
internal object Levels {
    const val MIN = 1
    const val MAX = 100
    fun t(level: Int): Double = (level.coerceIn(MIN, MAX) - MIN) / (MAX - MIN).toDouble()
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** Straight between anchors (level to value). */
    fun curve(level: Int, anchors: List<Pair<Int, Double>>): Double {
        val l = level.coerceIn(MIN, MAX)
        if (l <= anchors.first().first) return anchors.first().second
        val i = anchors.indexOfLast { it.first <= l }.coerceIn(0, anchors.size - 2)
        val (x0, y0) = anchors[i]
        val (x1, y1) = anchors[i + 1]
        return y0 + (y1 - y0) * (l - x0) / (x1 - x0).toDouble()
    }

    /** The scan has to read as far as the rule looks: a horizon of [hours] needs a window and book props at least that long. */
    fun reach(s: ScanSettings, hours: Int): ScanSettings {
        var n = s
        if (n.startsWithinHours in 1 until hours) n = n.copy(startsWithinHours = hours)
        val days = ceil(hours / 24.0).toInt()
        if (n.daysAhead < days) n = n.copy(daysAhead = days)
        if (n.bookPropHours < hours) n = n.copy(bookPropHours = hours)
        return n
    }

    fun name(level: Int): String = when {
        level <= 10 -> "Safest"
        level <= 30 -> "Careful"
        level <= 45 -> "Steady"
        level <= 62 -> "Balanced"
        level <= 80 -> "More volume"
        level <= 92 -> "High volume"
        else -> "Maximum volume"
    }

    val STOPS: List<Pair<Int, String>> = listOf(5 to "Safest", 20 to "Careful", 40 to "Steady", 55 to "Balanced", 75 to "More volume", 90 to "High volume", 100 to "Maximum")
}

/**
 * Auto bids. See [Levels]. Bids may be made for games 12 hours away and more: the horizon runs from 12 h (safest) to 24 h (maximum volume). It was 48 h until v0.89.35: Tj's 2026-10-11 export had 2,698 bids posted
 * 24+ h before the start and 2 of them filled (0.07%), 4,835 posted 12-24 h out and 39 filled, 6,958 posted 6-12 h out and 81 filled, and bids filled 12-24 h out closed at -0.2% (33 closes) against +2.1% inside 12 h.
 */
object BidSlider {
    const val MIN = Levels.MIN
    const val MAX = Levels.MAX
    const val MIN_MARGIN = 0.015
    val STOPS = Levels.STOPS
    fun name(level: Int) = Levels.name(level)

    fun margin(level: Int): Double = Levels.curve(level, listOf(1 to 0.08, 25 to 0.055, 50 to 0.04, 75 to 0.027, 100 to MIN_MARGIN))
    fun horizonHours(level: Int): Int = Levels.curve(level, listOf(1 to 12.0, 50 to 18.0, 100 to HORIZON_MAX_HOURS.toDouble())).roundToInt()
    const val HORIZON_MAX_HOURS = 24

    fun minBooks(level: Int): Int = when { level <= 15 -> 5; level <= 35 -> 4; level <= 60 -> 3; else -> 2 }
    /** Never unlimited and never past [LONGEST_ODDS] (RESEARCH.md §8.1, §16.2, §69: buys under 20¢ lose to the favorite-longshot bias and a longshot's fair is the least reliable; the bids' own +140 was Tj's rule, 2026-10-05). */
    const val LONGEST_ODDS = 200
    fun maxOdds(level: Int): Int = when { level <= 20 -> 120; level <= 45 -> 140; level <= 60 -> 150; level <= 80 -> 175; else -> LONGEST_ODDS }
    fun ttlMinutes(level: Int): Int = when { level <= 25 -> 10; level <= 60 -> 30; level <= 85 -> 60; else -> 120 }
    fun stopMinutes(level: Int): Int = when { level <= 25 -> 30; level <= 60 -> 15; else -> 5 }
    fun kinds(level: Int): Set<BetKind> = when {
        level <= 30 -> setOf(BetKind.PROP)
        level <= 85 -> ScanSettings.MAKER_DEFAULT_KINDS
        else -> ScanSettings.MAKER_DEFAULT_KINDS + setOf(BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL)
    }

    /** [s] with every bid rule the slider owns set for [level], and the scan reaching as far as the bids look. */
    fun apply(s: ScanSettings, level: Int): ScanSettings {
        val l = level.coerceIn(MIN, MAX)
        val m = margin(l)
        val quick = l >= 35
        val h = horizonHours(l)
        val n = s.copy(
            makerMargin = m,
            makerMinBooks = minBooks(l),
            makerSharpVeto = true, makerAnchorSharp = true, makerGuard = true, makerRequireSharp = l <= 40,
            makerBothSides = l >= 50,
            makerKinds = kinds(l),
            makerFocus = if (quick) BidFocus.QUICK_LIKELY else BidFocus.ALL,
            makerQuickMinBooks = Levels.curve(l, listOf(35 to 6.0, 100 to 3.0)).roundToInt().coerceIn(3, 6),
            makerObscureFill = l >= 65,
            makerObscureMargin = (m * 1.6).coerceIn(0.04, 0.10),
            makerObscureSharpMinEv = Levels.curve(l, listOf(65 to 0.04, 100 to 0.02)),
            makerObscureMinBooks = if (l >= 90) 2 else if (l >= 80) 3 else 4,
            makerObscureStake = Levels.curve(l, listOf(65 to 0.25, 100 to 0.75)),
            makerMaxOdds = maxOdds(l),
            makerTtlMinutes = ttlMinutes(l), makerStopMinutes = stopMinutes(l),
            makerPopularFirst = true,
            trapBidHours = h, trapNovigMove = true,
            vigGuards = s.vigGuards.copy(bidLevel = l),
        )
        return Levels.reach(if (s.vigGuards.bidMany) many(n) else n, h)
    }

    /** Most bids up at once in "More, smaller bids" mode; the money limits (Tj's dollars, the wallet) still bind first. */
    const val MANY_BIDS = 400

    /**
     * "More, smaller bids" (Tj, 2026-10-13: "many bids up at a time at lower stakes, best bids up first ... shouldn't sacrifice the EV rules"). Over the slider's own rules: every kind of bid is allowed
     * (the planner's [com.tjshea.vigilant.data.novig.trading.maker.BidTier] ranks them from the study, proven types first, and asks more of the risky ones: every book plus a sharp book must agree, a
     * wider margin, half the stake), 1/16 Kelly a bid, up to [MANY_BIDS] at once, no popularity filter (the tiers do that job). The slider's margin, books, sharp veto, longest odds, horizon and
     * expiry still apply to every tier, so each bid is still +EV at the floor [MIN_MARGIN]. Your dollar limits and the wallet are untouched: smaller bids just let the same money cover more markets.
     */
    private fun many(n: ScanSettings): ScanSettings = n.copy(
        makerStakeMode = AutoBetStake.SIXTEENTH_KELLY, makerMaxBids = MANY_BIDS,
        makerKinds = BetKind.entries.toSet(), makerFocus = BidFocus.ALL, makerObscureFill = false, makerPopularFirst = true,
    )

    /** [s] with the mode switched [on] or off; off puts back the two settings the mode changed that the slider does not own. */
    fun withMany(s: ScanSettings, on: Boolean, level: Int): ScanSettings {
        val flipped = s.copy(vigGuards = s.vigGuards.copy(bidMany = on))
        val restored = if (on) flipped else flipped.copy(
            makerStakeMode = if (flipped.makerStakeMode == AutoBetStake.SIXTEENTH_KELLY) AutoBetStake.QUARTER_KELLY else flipped.makerStakeMode,
            makerMaxBids = if (flipped.makerMaxBids == MANY_BIDS) 20 else flipped.makerMaxBids,
        )
        return apply(restored, level)
    }

    /** Whether [s] still has the rules [level] sets (the scan window may only be longer). */
    fun matches(s: ScanSettings, level: Int): Boolean = level in MIN..MAX && apply(s, level) == s

    fun describe(level: Int, custom: Boolean, many: Boolean = false): List<String> {
        val l = level.coerceIn(MIN, MAX)
        return listOf(
            (if (custom) "Custom rules (edited in Advanced), last slider position $l: ${name(l)}" else "${name(l)} · $l of 100"),
            "Each bid sits ${pct(margin(l))} under the fair price: its edge when it goes up (never under ${pct(MIN_MARGIN)}).",
            "${minBooks(l)}+ books must each say it is +EV; a sharp book that disagrees vetoes it" + (if (l <= 40) " and one must confirm it" else "") + ".",
            "Games up to ${horizonHours(l)} h away; each bid rests ${ttlMinutes(l)} min and stops ${stopMinutes(l)} min before the start.",
            "Never longer than +${maxOdds(l)} (longshots lose to the favorite-longshot bias). " + (if (kinds(l).size == 1) "Player props only." else if (kinds(l).size == 3) "Props, periods and team totals." else "Props, periods, team totals and game lines.") +
                (if (l >= 35) " Takes the likeliest-to-fill bids first" else "") + (if (l >= 65) " and fills leftover money with small markets." else "."),
            "Expect " + (if (l <= 20) "few fills, the best price each" else if (l <= 50) "a fair number of fills" else if (l <= 80) "many fills at a thinner edge" else "the most fills, each at a thin but positive edge") + ".",
        ) + (if (many) listOf(
            "More, smaller bids: up to $MANY_BIDS at once at 1/16 Kelly each, every kind of bid. Proven types (team totals outside the NFL, the NHL) go up first; steady ones next with a sharp book and one more book agreeing; risky ones last (prop overs, the NFL, game lines): every book that prices them and a sharp book must agree, a 1.5-point wider margin, half the stake.",
        ) else emptyList())
    }

    private fun pct(v: Double) = String.format(java.util.Locale.US, "%.1f%%", v * 100).replace(".0%", "%")
}

/** Auto-bet. See [Levels]. */
object BetSlider {
    const val MIN = Levels.MIN
    const val MAX = Levels.MAX
    const val MIN_EV = 0.015
    val STOPS = Levels.STOPS
    fun name(level: Int) = Levels.name(level)

    fun minEv(level: Int): Double = Levels.curve(level, listOf(1 to 0.06, 25 to 0.045, 50 to 0.033, 75 to 0.024, 100 to MIN_EV))
    fun vetoBar(level: Int): Double = Levels.curve(level, listOf(1 to 0.03, 50 to 0.01, 100 to 0.005))
    fun books(level: Int): Int = when { level <= 15 -> 5; level <= 35 -> 4; level <= 65 -> 3; else -> 2 }
    fun twoSided(level: Int): Int = if (level <= 70) 3 else 2
    /** Never unlimited and never past +150, the research's range (RESEARCH.md §8.1, §16.2, §65-§66, §69: favorite-longshot bias, thin books on longshots; the Strict preset's +130). Longer odds were +200 to +300 to unlimited before v0.89.32. */
    const val LONGEST_ODDS = 150
    fun maxOdds(level: Int): Int = when { level <= 10 -> 110; level <= 25 -> 120; level <= 45 -> 130; else -> LONGEST_ODDS }
    fun minOdds(level: Int): Int = when { level <= 15 -> -150; level <= 60 -> -200; level <= 85 -> -300; else -> 0 }
    fun favouriteExtra(level: Int): Double = Levels.curve(level, listOf(1 to 0.015, 100 to 0.005))
    fun horizonHours(level: Int): Int = Levels.curve(level, listOf(1 to 6.0, 50 to 8.0, 100 to 24.0)).roundToInt()
    /**
     * Totals come in from the first tier (Tj, 2026-10-17: "analyze what bets and bids I'm profiting on ... and if the samples are big enough, tweak the engine"; research/review/pnl_2026-10-17.md): over 2,000 settled
     * bets and bids from 09-25 to 10-16, game and team totals returned +33.4% on 390 (z +2.5, positive in both halves of the period, CLV +1.6%), and PropLine's resolved archive agrees on 1,471 game totals (+9.5%, t about 3.7),
     * while the taker lane's old kinds (props, moneylines, spreads) were its losers. They only ADD volume here: every bet still needs the same edge, books, trap guard and Kelly limits.
     */
    fun kinds(level: Int): Set<BetKind> = when {
        level <= 70 -> setOf(BetKind.PROP, BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL, BetKind.TEAM_TOTAL)
        level <= 88 -> setOf(BetKind.PROP, BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL, BetKind.TEAM_TOTAL, BetKind.PERIOD)
        else -> BetKind.entries.toSet()
    }

    fun apply(s: ScanSettings, level: Int): ScanSettings {
        val l = level.coerceIn(MIN, MAX)
        val ev = minEv(l)
        val h = horizonHours(l)
        val n = s.copy(
            autoBetMinEv = ev, alertMinEv = ev,
            autoBetBooks = books(l), autoBetTwoSided = twoSided(l), autoBetAllAgree = l <= 12,
            autoBetMaxOdds = maxOdds(l), autoBetMinOdds = minOdds(l), autoBetFavouriteExtraEv = favouriteExtra(l),
            autoBetKinds = kinds(l),
            sharpAutoBet = SharpMode.VETO, sharpAlerts = SharpMode.VETO, sharpVetoMinEv = vetoBar(l),
            propGuardShare = Levels.curve(l, listOf(1 to 0.15, 100 to 0.35)),
            propGuardPerGame = Levels.curve(l, listOf(1 to 1.0, 100 to 4.0)).roundToInt(),
            trapEarlyHours = h, trapNovigMove = true,
            // A star minimum, when picked, overrides every rule above (AutoBet.Rules.byStars) and leaves the slider unused: the slider never changes it (Tj, 2026-10-12).
            vigGuards = s.vigGuards.copy(betLevel = l),
        )
        return Levels.reach(n, h)
    }

    fun matches(s: ScanSettings, level: Int): Boolean = level in MIN..MAX && apply(s, level) == s

    fun describe(level: Int, custom: Boolean): List<String> {
        val l = level.coerceIn(MIN, MAX)
        return listOf(
            (if (custom) "Custom rules (edited in Advanced), last slider position $l: ${name(l)}" else "${name(l)} · $l of 100"),
            "Bets only at ${String.format(java.util.Locale.US, "%.1f", minEv(l) * 100)}%+ edge at Novig's price now (favorites ${String.format(java.util.Locale.US, "%.1f", favouriteExtra(l) * 100)} points more); ${books(l)}+ books agree, ${twoSided(l)}+ price both sides" + (if (l <= 12) ", every one agreeing" else "") + ".",
            "A sharp book that gives it under ${String.format(java.util.Locale.US, "%.1f", vetoBar(l) * 100)}% vetoes it.",
            "Odds up to +${maxOdds(l)}, never a longshot" + (if (minOdds(l) < 0) ", none shorter than ${minOdds(l)}" else "") + "; games up to ${horizonHours(l)} h away.",
            (if (kinds(l).size == 3) "Props, moneylines and spreads." else if (kinds(l).size == 5) "Plus team totals and period lines." else "Every kind of bet."),
            "Expect " + (if (l <= 20) "few bets, the best edge each" else if (l <= 50) "a steady number of bets" else if (l <= 80) "many bets at a thinner edge" else "the most bets, each at a thin but positive edge") + ".",
        )
    }
}
