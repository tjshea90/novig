package com.tjshea.vigilant.data.scanner

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The 1-5 star rating of a +EV bet (Tj, 2026-10-11): how much the bet is likely to make, and whether enough of the market backs it. Every rated bet (1 to 5 stars) is +EV on the evidence; a
 * 1-star bet has a small edge, a 5-star one a large edge that four or more companies price (Tj, 2026-10-24: "5 Star bets being excellent, profitable bets with good support and 1 star bets
 * still being positive ev, but less support and less profitable"). A line whose edge the data does not trust gets [NONE] (0, "no trusted edge") instead of a star: see [doubtOf].
 *
 * **The scale ([Scale.S6], since v1.0.69; research/star_rating_sb_2026-10-24.md).** On 10,428 graded +EV lines (the engine's and the phone's scan study) the stated edge was the one input that
 * predicted the sharp close, and monotonically (Pinnacle/Circa CLV +0.2% under +0.5% EV, about +1% at 0.5-3%, +3% at 3-6%, more above), while the support inputs (companies, a sharp book
 * agreeing, consensus, the history's type points) did not, in either sample. So:
 *  1. **The edge sets the stars** ([edgeStars]): +0.5-1% = 1, +1-1.5% = 1½, +1.5-2% = 2, +2-2.5% = 2½, +2.5-3% = 3, +3-4% = 3½, +4-5% = 4, +5-6% = 4½, +6% and more = 5.
 *  2. **The top needs breadth** ([breadthCeiling]): one company pricing both sides at most 2 stars, two at most 3, three at most 4; 5 stars take four or more. An edge over +12% is at most
 *     4 stars ([SUSPECT_EV]: often a stale or mismatched quote).
 *  3. **No trusted edge**: under +0.5% EV (noise: those lines lost to the close), over +25% (usually a stale or mismatched quote), a sharp book for the market saying it is not +EV, or an edge
 *     under +1.5% from one or two companies (such lines lost about 40% ROI; from four companies up the same small edges beat the close).
 * Re-rated that way, sharp CLV per level was 0 = -0.0%, 1-1½ = +1.4%, 2-2½ = +1.6%, 3-3½ = +3.1%, 4-4½ = +2.9%, 5 = +3.4%.
 *
 * **The support score** ([Rating.points], [Rating.support]) is the five facts weighted as before, kept for the bet sheet, ordering ties and the engine's stake sizing ([com.tjshea.vigilant.data.cloud.CloudKelly]
 * reads [Rating.support], so the sizing did not change with the scale):
 *  1. **Coverage** (20 points): how many companies price BOTH sides of the line (sister sites count once). A fair line from one book is that book's opinion; five is a market.
 *  2. **Agreement** (25): the share of those companies that, each on its own devigged worst case, make the price +EV.
 *  3. **Edge** (30): the EV at the price you would pay (taker fee included). 0% scores nothing, 6%+ is full marks; above 12% the score falls again.
 *  4. **Sharp book** (15): a sharp book for the market prices both sides and says +EV (full), none prices it (a third of the points), or one says it is NOT +EV (none).
 *  5. **Consensus** (10): how tightly the books' own fair chances sit together (standard deviation: 1 point or less is full, 5 or more nothing; unknown is half).
 *  6. **Type of bet** (-7.5 to +7.5): what the history says about this kind of bet ([ProfitProfile.typePoints]).
 * Those points become the support stars on a curve, `1 + 4 * (points/100)^1.5`, under the old ceilings (under two companies 1½, two 3 or 2 with no sharp book, three 4, a sharp book against 2½,
 * under +2% EV 3): the v1.0.67 stars, and [Scale.S5]'s with its floor.
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
        /** The bet's type for the evidence table ([StarEvidence], [Scale.SX]); null = unknown (SX then falls back to [Scale.S6]). */
        val type: BetType? = null,
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

    /** The weights of the support score. */
    val CURRENT: Weights = Weights.SR

    /** How points become stars: [S5] (v1.0.68: the support curve, with the no-trusted-edge floor) or [S6] (v1.0.69: the edge ladder with breadth ceilings). The star check scores both every day. */
    enum class Scale { S5, S6, SX }

    /** The scale the app rates with (SB, Tj 2026-10-24). Revert: [Scale.S5]. */
    val CURRENT_SCALE: Scale = Scale.S5

    /** Over this EV (to [MAX_TRUSTED_EV]) S6 rates a bet at most 4 stars: an edge that large is often stale or mismatched. */
    const val SUSPECT_EV = 0.12

    /** Under this EV, an edge priced by one or two companies only is not trusted (S6): such lines lost about 40% ROI. */
    const val THIN_MARKET_EV = 0.015

    /**
     * A rating: [stars] in half steps from 1.0 to 5.0 (or [NONE] with the [doubt] that put it there), the support [points], and the [support] stars those points make on the old curve
     * (what the engine's stake sizing reads).
     */
    data class Rating(val stars: Double, val points: Double, val doubt: String? = null, val support: Double = stars, val evidence: StarEvidence.Cell? = null) {
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

    fun rate(i: Inputs, w: Weights = CURRENT, scale: Scale = CURRENT_SCALE): Rating {
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
        val support = roundHalf(supportStars(points, books, i))
        val doubt = doubtOf(i.ev, i.sharpAgainst) ?: if (scale == Scale.S6) thinMarketDoubt(books, i.ev) else null
        if (doubt != null) return Rating(NONE, points, doubt, support)
        // SX: the record of bets of this type at Novig (null: no type, no table, or no cell with the games: the bet is then unproven, 1 star).
        val cell = if (scale == Scale.SX) i.type?.let { t -> StarEvidence.shipped?.lookup(t) } else null
        val stars = when (scale) {
            // The smallest real edges make the least (S5): under +1% at most 1 star.
            Scale.S5 -> if (i.ev < MIN_EV_FOR_TWO_STARS - 1e-12) MIN else support
            // An edge over 12% is often a stale or mismatched quote (the edge score has always fallen there; too few graded lines to say more): rated, but not top.
            Scale.S6 -> s6Stars(i.ev, books)
            Scale.SX -> if (cell == null) MIN else {
                losingDoubt(cell)?.let { return Rating(NONE, points, it, support, cell) }
                // The backtest saying this type LOSES to Novig's close holds a real record back to 2 stars until the real lines outweigh it.
                val proof = if ((cell.btZ ?: 0.0) <= -LOSING_Z) minOf(proofStars(cell), 2.0) else proofStars(cell)
                minOf(proof, breadthCeiling(books), if (i.ev > SUSPECT_EV) 4.0 else MAX)
            }
        }
        return Rating(roundHalf(stars), points, null, support, cell)
    }

    private fun s6Stars(ev: Double, books: Int): Double = minOf(edgeStars(ev), breadthCeiling(books), if (ev > SUSPECT_EV) 4.0 else MAX)

    /**
     * SX: the stars a type's record earns at Novig ([StarEvidence.Cell.z]: its real lines' CLV against Novig's own close, in standard errors): under 0.5 = 1 (+EV against the books, not
     * proven at Novig), then a half star at each of z 0.5, 1, 1.32, 1.64 (90%), 1.81, 1.96 (95%), 2.33, 2.58 (99%) = 5. Four stars and up also need a CLV worth having
     * ([MIN_CLV_FOR_FOUR], [MIN_CLV_FOR_FIVE]: against Novig's own close, where a taker on average gives up about 2%).
     */
    fun proofStars(cell: StarEvidence.Cell): Double {
        val z = cell.z
        var stars = Z_STEPS.count { z >= it } * 0.5 + 1.0
        if (cell.clv < MIN_CLV_FOR_FIVE) stars = minOf(stars, 4.5)
        if (cell.clv < MIN_CLV_FOR_FOUR) stars = minOf(stars, 3.5)
        return stars.coerceIn(MIN, MAX)
    }

    /**
     * SX: a type that LOSES at Novig has no trusted edge: its real lines' CLV against Novig's close below [LOSING_CLV] even at its upper 95% bound, and the backtest not saying otherwise
     * (it agrees, or has no such type). Both sources agree today on game-total Unders and favourites at -150..-100 (research/star_rating_sx_2026-10-24.md).
     */
    private fun losingDoubt(cell: StarEvidence.Cell): String? =
        if (cell.upper < LOSING_CLV && (cell.btZ == null || cell.btZ <= -LOSING_Z)) "bets like this lost to Novig's close (${pct(cell.clv)} over ${cell.games} games)" else null

    /** SX's z steps: each one passed adds half a star to 1 (eight steps: 5 stars at 2.58, 99% one-sided). */
    val Z_STEPS = listOf(0.5, 1.0, 1.32, 1.64, 1.81, 1.96, 2.33, 2.58)
    const val MIN_CLV_FOR_FOUR = 0.005
    const val MIN_CLV_FOR_FIVE = 0.01
    const val LOSING_Z = 1.64
    /** A type loses when even the upper 95% bound of its CLV against Novig's close is below this (half a point: more than rounding). */
    const val LOSING_CLV = -0.005

    /** The old curve's stars from the support points, under its ceilings (v1.0.67's stars): [Rating.support]. */
    private fun supportStars(points: Double, books: Int, i: Inputs): Double {
        var stars = 1.0 + 4.0 * (points / 100.0).coerceIn(0.0, 1.0).pow(1.5)
        stars = minOf(stars, ceiling(books))
        if (i.sharpAgainst) stars = minOf(stars, 2.5)
        // Two companies and no sharp book among them = two small names agreeing with each other (Tj, 2026-10-23): at most 2 stars.
        if (books in 1..2 && !i.sharpAgrees && !i.sharpAgainst) stars = minOf(stars, MAX_STARS_SMALL_BOOKS)
        // 3½ stars and up take a real edge (Tj, 2026-10-11): under +2% EV at the price you would pay, a bet is at most 3 stars however well the books agree.
        if (i.ev < MIN_EV_FOR_HIGH_STARS - 1e-12) stars = minOf(stars, MAX_STARS_UNDER_MIN_EV)
        if (i.ev <= 0.0) stars = MIN
        return stars
    }

    /** S6's stars from the edge alone (full marks at 6%, as the edge score has): the profit side of the rating. */
    fun edgeStars(ev: Double): Double = when {
        ev < 0.01 -> 1.0
        ev < 0.015 -> 1.5
        ev < 0.02 -> 2.0
        ev < 0.025 -> 2.5
        ev < 0.03 -> 3.0
        ev < 0.04 -> 3.5
        ev < 0.05 -> 4.0
        ev < 0.06 -> 4.5
        else -> MAX
    }

    /** S6's support ceiling: how many companies price both sides. One at most 2 stars, two at most 3, three at most 4; 5 stars take four or more. */
    fun breadthCeiling(books: Int): Double = when {
        books <= 1 -> 2.0
        books == 2 -> 3.0
        books == 3 -> 4.0
        else -> MAX
    }

    /** S6: a small edge from one or two companies is noise (research/star_rating_sb_2026-10-24.md: one company at +0.5-1.5% ROI -45%, two -39%). */
    private fun thinMarketDoubt(books: Int, ev: Double): String? =
        if (books <= 2 && ev < THIN_MARKET_EV - 1e-12) "an edge under ${pct(THIN_MARKET_EV)} from ${if (books <= 1) "one book" else "two books"} is noise (such lines lost)" else null

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
