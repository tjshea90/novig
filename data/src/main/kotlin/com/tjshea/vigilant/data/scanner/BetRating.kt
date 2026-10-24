package com.tjshea.vigilant.data.scanner

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The 1-5 star rating of a +EV bet (Tj, 2026-10-11): how much to TRUST the edge and how much it is likely to make. Every rated bet (1 to 5 stars) is +EV on the evidence; a 1-star bet is one
 * with a small edge or thin support, a 5-star one has a large edge that many books back (S5, Tj 2026-10-24: "5 Star bets being excellent, profitable bets with good support and 1 star bets
 * still being positive ev, but less support and less profitable"). A line whose edge the data does not trust gets [NONE] (0, "no trusted edge") instead of a star: see [doubtOf].
 * One rating for both scanners, from the same five facts about a bet, all worked out the same way (RESEARCH.md §131):
 *
 *  1. **Coverage** (20 points): how many companies price BOTH sides of the line (sister sites count once). A fair line from one book is that book's opinion; five is a market.
 *  2. **Agreement** (25; 20 before SR): the share of those companies that, each on its own devigged worst case, make the price +EV.
 *  3. **Edge** (30; 25 before SR): the EV at the price you would pay (taker fee included). 0% scores nothing, 6%+ is full marks; above 12% the score falls again, because an edge that large on a
 *     priced market is usually a stale or mismatched quote, not a gift.
 *  4. **Sharp book** (15; 25 before SR): a sharp book for the market prices both sides and says +EV (full), none prices it (a third of the points: unproven, not disproven), or one says it is NOT +EV (none).
 *  5. **Consensus** (10): how tightly the books' own fair chances sit together (standard deviation: 1 point or less is full, 5 or more nothing; unknown is half).
 *
 * 6. **Type of bet** (-7.5 to +7.5 since SR; ±15 in ST1, Tj 2026-10-23): what the history says about this kind of bet (league, kind, over/under, lead time), shrunk by sample size ([ProfitProfile.typePoints]).
 *
 * The weights are [Weights]; [CURRENT] is SR's (Tj, 2026-10-24), chosen on the engine's graded lines by the daily star check, which keeps scoring the alternatives beside it.
 *
 * The points total 0-100 (plus or minus the type points, kept in 0-100) and become stars on a curve, `1 + 4 * (points/100)^1.5`, so the top half stars take real evidence while a middling bet lands in the middle. Then ceilings that
 * evidence alone cannot be argued past: under two pricing companies at most 1½ stars, two at most 3, three at most 4. A bet under +2% EV is at most 3 stars (Tj, 2026-10-11: 3½ and up need a real edge),
 * under +1% at most 1 star (S5: the smallest edges make the least). Rounded to the nearest half star.
 *
 * **No trusted edge** (S5, [NONE]): under +0.5% EV, over +25%, or a sharp book for the market saying it is not +EV. On the engine's 2,880 graded +EV lines (research/star_rating_s5_2026-10-24.md)
 * the 440 under +0.5% lost to the close (CLV -2.1% / -0.4%, ROI -14% / -18%), the 98 over +30% lost (ROI -27%; mostly two-book props: a stale or mismatched quote) and the sharp book's "no" is
 * the sharpest price there is; every star level from 1 up beat the close.
 */
object BetRating {

    /** What a rating is worked out from. [spread]: the standard deviation of the pricing companies' fair chances for this side, null when unknown. */
    data class Inputs(
        val books: Int,
        /** How many of the [books] say +EV alone; null = not known (an older record): the agreement share then scores as a middling 0.6. */
        val agreeing: Int?,
        val ev: Double,
        val sharpAgrees: Boolean,
        val sharpAgainst: Boolean,
        val spread: Double? = null,
        /** Points (of 100) for the TYPE of bet from the history ([ProfitProfile.typePoints]): negative for a type that has lost, positive for one that has won. */
        val typePoints: Double = 0.0,
    )

    /**
     * How many of the 100 points each part is worth, and how much of the type-of-bet points counts ([typeScale]). [CURRENT] is what the app rates with; the daily star check (cloud/StarCheckMain.kt)
     * scores other weights beside it on the engine's graded lines, so a change of weights is chosen on data (SR, Tj 2026-10-24: "If your findings warrant revising the star rating method, do it now").
     */
    data class Weights(
        val coverage: Double = 20.0,
        val agreement: Double = 20.0,
        val edge: Double = 25.0,
        val sharp: Double = 25.0,
        val consensus: Double = 10.0,
        val typeScale: Double = 1.0,
    ) {
        companion object {
            /** ST1 (v1.0.64): the weights before SR. */
            val ST1 = Weights()

            /**
             * SR (Tj, 2026-10-24): the sharp book 25 -> 15 points, the edge 25 -> 30, agreement 20 -> 25, the type of bet at half. Chosen from six principled weightings re-scored on the engine's 1,968 graded
             * +EV lines (research/star_rating_review_2026-10-23.md, "SR"): CLV per star +0.77 (ST1 +0.50), ROI per star +6.2 points (ST1 +5.0), better in the first half of the period on both; the sharp book's
             * agreement showed no CLV or ROI of its own there (partly circular: the fair leans on it and the close is Pinnacle's), and the history's type points did not hold out of sample.
             */
            val SR = Weights(agreement = 25.0, edge = 30.0, sharp = 15.0, typeScale = 0.5)
        }
    }

    /** The weights the app rates with. */
    val CURRENT: Weights = Weights.SR

    /** A rating: [stars] in half steps from 1.0 to 5.0 (or [NONE] with the [doubt] that put it there), and the [points] it came from. */
    data class Rating(val stars: Double, val points: Double, val doubt: String? = null) {
        /** "3½" or "4"; the glyphs are [glyphs]. */
        val label: String get() = BetRating.label(stars)
        val glyphs: String get() = BetRating.glyphs(stars)
        /** A 1-5 star bet: +EV on the evidence. False = no trusted edge ([doubt] says why). */
        val trusted: Boolean get() = stars >= MIN
    }

    /** Why the data does not trust this edge, or null when it does (S5): the line is then [NONE] whatever its other inputs say. */
    fun doubtOf(ev: Double, sharpAgainst: Boolean): String? = when {
        ev < THIN_EV -> "an edge under ${pct(THIN_EV)} is noise (such lines lost to the close)"
        ev > MAX_TRUSTED_EV -> "an edge over ${pct(MAX_TRUSTED_EV)} is usually a stale or mismatched price"
        sharpAgainst -> "a sharp book's own price says it is not +EV"
        else -> null
    }

    private fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100).replace(".0%", "%")

    const val MIN = 1.0
    const val MAX = 5.0

    /** "No trusted edge" (S5): the rating of a line the data says is not reliably +EV ([doubtOf]); shown as no stars, and under every star minimum. */
    const val NONE = 0.0

    /** Under this EV (a fraction) an edge is noise: the engine's lines under +0.5% lost to the close (S5). The auto-bet's own read floor is the same ([com.tjshea.vigilant.data.novig.trading.AutoBet.MIN_EV_FLOOR]). */
    const val THIN_EV = 0.005

    /** Over this EV an edge is not believed: usually a stale or mismatched quote (S5; the feed's default cap, [ScanSettings.maxEvPercent]). */
    const val MAX_TRUSTED_EV = 0.25

    /** Under this EV a bet is at most 1 star: a real edge, but the smallest (S5). */
    const val MIN_EV_FOR_TWO_STARS = 0.01

    /** The least EV (a fraction: 0.02 = 2%) a bet needs to be rated 3½ stars or better, and the most it can have under it (Tj, 2026-10-11). */
    const val MIN_EV_FOR_HIGH_STARS = 0.02
    const val MAX_STARS_UNDER_MIN_EV = 3.0
    const val MAX_STARS_SMALL_BOOKS = 2.0

    /**
     * Points for how long before the game the bet is taken (ST1, from the engine's graded +EV lines: CLV vs the Pinnacle/Circa close was +1.9% / +3.1% / +1.1% inside 1 h / 1-6 h / 6-13 h, +0.2% beyond 24 h):
     * the closer to the start the more the price tends to hold, the earlier the more it tends to give back. Small on purpose; [ProfitProfile.typePoints] carries the history's own lead term.
     */
    fun leadPoints(minToStart: Long?): Double = when {
        minToStart == null -> 0.0
        minToStart <= 360 -> 3.0
        minToStart <= 1440 -> 0.0
        else -> -4.0
    }

    fun rate(i: Inputs, w: Weights = CURRENT): Rating {
        val books = i.books.coerceAtLeast(0)
        val coverage = when (books) { 0 -> 0.0; 1 -> 0.1; 2 -> 0.45; 3 -> 0.7; 4 -> 0.88; else -> 1.0 }
        val agree = if (books == 0) 0.0 else i.agreeing?.let { it.coerceIn(0, books).toDouble() / books } ?: 0.6
        val edge = edgeScore(i.ev)
        val sharp = when {
            i.sharpAgainst -> 0.0
            i.sharpAgrees -> 1.0
            else -> 0.3
        }
        val tight = i.spread?.let { ((0.05 - it) / 0.04).coerceIn(0.0, 1.0) } ?: 0.5
        val points = w.coverage * coverage + w.agreement * agree + w.edge * edge + w.sharp * sharp + w.consensus * tight + w.typeScale * i.typePoints
        doubtOf(i.ev, i.sharpAgainst)?.let { return Rating(NONE, points, it) }
        var stars = 1.0 + 4.0 * (points / 100.0).coerceIn(0.0, 1.0).pow(1.5)
        stars = minOf(stars, ceiling(books))
        // Two companies and no sharp book among them = two small names agreeing with each other (Tj, 2026-10-23): at most 2 stars (research/star_rating_2026-10-23.md: <=2 books closed -1.7 points worse, t -1.7).
        if (books in 1..2 && !i.sharpAgrees && !i.sharpAgainst) stars = minOf(stars, MAX_STARS_SMALL_BOOKS)
        // 3½ stars and up take a real edge (Tj, 2026-10-11): under +2% EV at the price you would pay, a bet is at most 3 stars however well the books agree.
        if (i.ev < MIN_EV_FOR_HIGH_STARS - 1e-12) stars = minOf(stars, MAX_STARS_UNDER_MIN_EV)
        // The smallest real edges make the least (S5): under +1% at most 1 star.
        if (i.ev < MIN_EV_FOR_TWO_STARS - 1e-12) stars = MIN
        return Rating(roundHalf(stars), points)
    }

    /** The edge's share of its points: a straight climb to full at 6%, level to 12%, then back down to half at 25% and under (a too-good price is suspect). */
    internal fun edgeScore(ev: Double): Double = when {
        ev <= 0.0 -> 0.0
        ev < 0.06 -> ev / 0.06
        ev <= 0.12 -> 1.0
        ev < 0.25 -> 1.0 - 0.5 * (ev - 0.12) / 0.13
        else -> 0.5
    }

    private fun ceiling(books: Int): Double = when {
        books < 2 -> 1.5
        books == 2 -> 3.0
        books == 3 -> 4.0
        else -> MAX
    }

    fun roundHalf(stars: Double): Double = (Math.round(stars.coerceIn(MIN, MAX) * 2.0) / 2.0)

    /** "3½", "4", "2". */
    fun label(stars: Double): String {
        val whole = stars.toInt()
        return if (abs(stars - whole) > 0.25) "$whole½" else "$whole"
    }

    /** "★★★½": a star for each whole one and a ½ for the half; "☆" for [NONE]. */
    fun glyphs(stars: Double): String = if (stars < MIN) "☆" else "★".repeat(stars.toInt()) + if (abs(stars - stars.toInt()) > 0.25) "½" else ""

    /** The standard deviation of [values] (null under two of them). */
    fun spreadOf(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    fun text(stars: Double): String = String.format(Locale.US, "%.1f", stars)

    /**
     * Whether a sharp book agreed with the bet as [a] records it: true, false, or null (none priced it / not known). The live check's own answer when it was kept
     * ([AtBet.sharpAgrees]); else the sharp veto's verdict (PASSED / VETOED) or, for a bid, whether the sharpest book's own fair was above its price.
     */
    fun sharpOf(a: com.tjshea.vigilant.data.tracker.AtBet): Boolean? = a.sharpAgrees ?: when (a.sharpVerdict) {
        "PASSED" -> true
        "VETOED" -> false
        "ANCHORED" -> a.sharpEv?.let { it > 0.0 }
        else -> null
    }

    /**
     * The rating worked out from what a record holds ([a]: an older bet, a bid, an alert's ✓), null with no EV to judge. Companies pricing both sides are [AtBet.twoSided], else the
     * books the fair line used (one a company); how many of them agree is [AtBet.agreeing], else unknown (a middling score). The spread comes from the page's books when they were kept.
     */
    fun estimate(a: com.tjshea.vigilant.data.tracker.AtBet): Rating? {
        val ev = a.ev ?: a.checkEv ?: return null
        val books = a.twoSided ?: a.fairBooks.map { com.tjshea.vigilant.engine.BookCompany.of(it) }.distinct().size.takeIf { it > 0 } ?: 0
        val sharp = sharpOf(a)
        val spread = a.fairSpread ?: spreadOf(a.books.mapNotNull { it.fair })
        return rate(Inputs(books, a.agreeing, ev, sharpAgrees = sharp == true, sharpAgainst = sharp == false, spread = spread))
    }

    /** The rating of [a] as the record keeps it, or the estimate from it; null with neither. [Second]: whether it was estimated. */
    fun of(a: com.tjshea.vigilant.data.tracker.AtBet?): Pair<Double, Boolean>? {
        a ?: return null
        a.stars?.let { return it to (a.starsEstimated == true) }
        return estimate(a)?.let { it.stars to true }
    }
}
