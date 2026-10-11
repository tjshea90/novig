package com.tjshea.vigilant.data.livebid

import kotlin.math.roundToInt

/**
 * The Safety / Volume slider (Tj, 2026-10-11: "on one side is the very safest and best bets and bids with highest EV but least likely to fill, on the other side is pure volume more risky but very high fill rate ... I can slide the
 * slider and vigilant will adjust the settings accordingly in the background ... make sure the settings are logical and mathematically sound. The wider the range the better"). ONE number, 0 (safest) to 100 (most volume), sets every
 * rule of [LiveBidQuality] that trades edge against fills, together, so they always agree with each other:
 *
 *  - **The price.** A bid sits [margin] under Pinnacle's devigged fair, so its edge when posted IS the margin (makers pay no fee). A wider margin is a better price and a rarer fill (the tapes: fills a bid-hour fell from about 2 at 3% to
 *    about 0 at 8%; a 5% margin kept about +4% a fill, 3% under +1%: RESEARCH.md §124). 9% to 1.2% across the slider.
 *  - **What the edge must survive.** A bid is pulled when its edge falls under [LiveBidQuality.pullBelowEv], always a share of the margin (55% of it at the safe end, 20% at the volume end), so the pull line is always BELOW the price's edge and
 *    the gap is exactly the drift a bid tolerates before it comes down. A pull takes about 2 s (measured), so a fill can still land inside it: the third guard is [LiveBidQuality.minKeptEv], the average edge the last fills must still show 30 s later (+2%
 *    safe end, never under 0): a run of fills that did not keep their edge halts the feature.
 *  - **Which prices to trust.** Safe: only mid-range fairs (22-78%), Pinnacle margin at most 6.5%, limit at least $3,000, a price settled for 6 s, a 45 s hold after a score. Volume: fairs 6-94%, margin 10%, limit $150, 1 s, 8 s.
 *  - **How the self-checks judge.** Strict at the safe end (stop after 2 of 5 picked-off fills, a pull over 8 s), forgiving at the other (7 of 10, 16 s).
 *
 * Every field is a straight line (or a geometric one for dollars) between the two ends, except the margin (anchored on the tapes) and a few switches that turn on at a position, so a step of the slider is always a small, same-direction
 * change in every rule. [apply] keeps what the slider does not own: the line kinds, tennis, the leagues, the devig method.
 */
object LiveBidSlider {
    const val MIN = 0
    const val MAX = 100

    /** The named stops the quick buttons jump to. */
    val STOPS: List<Pair<Int, String>> = listOf(5 to "Safest", 25 to "Careful", 40 to "Steady", 55 to "Balanced", 75 to "More fills", 90 to "Volume", 100 to "Maximum volume")

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    private fun geo(a: Double, b: Double, t: Double) = a * Math.pow(b / a, t)

    /** The margin at [level]: anchored on the tapes (6% at 20, 5% at 40, 2.5% at 80), straight between anchors. */
    fun margin(level: Int): Double {
        val l = level.coerceIn(MIN, MAX)
        val a = listOf(0 to 0.09, 20 to 0.065, 40 to 0.05, 60 to 0.038, 80 to 0.025, 100 to 0.012)
        val i = a.indexOfLast { it.first <= l }.coerceAtMost(a.size - 2)
        val (x0, y0) = a[i]
        val (x1, y1) = a[i + 1]
        return y0 + (y1 - y0) * (l - x0) / (x1 - x0).toDouble()
    }

    /** [base] with every rule the slider owns set for [level]. */
    fun apply(base: LiveBidQuality, level: Int): LiveBidQuality {
        val l = level.coerceIn(MIN, MAX)
        val t = l / 100.0
        val m = margin(l)
        val lo = lerp(0.22, 0.06, t)
        val ttl = lerp(15.0, 40.0, t).roundToInt()
        val pullShare = lerp(0.55, 0.20, t)
        val limitShare = lerp(0.40, 0.70, t)
        val window = lerp(5.0, 10.0, t).roundToInt()
        return base.copy(
            margin = m,
            pullBelowEv = maxOf(0.002, m * pullShare),
            minKeptEv = lerp(0.02, 0.0, t),
            keptWindow = 8,
            neverLead = l < 20,
            bothSides = l >= 40,
            minFair = lo, maxFair = 1.0 - lo,
            minPrice = maxOf(0.04, lo * 0.85), maxPrice = (1.0 - lo) * 0.97,
            maxOverround = lerp(0.065, 0.10, t),
            minPinnLimit = geo(3000.0, 150.0, t).roundToInt().toDouble(),
            maxBookGap = lerp(0.06, 0.15, t),
            maxQuietSec = lerp(8.0, 25.0, t).roundToInt(),
            maxFairAgeSec = if (l < 50) lerp(20.0, 60.0, l / 50.0).roundToInt() else 0,
            settleSec = lerp(6.0, 1.0, t).roundToInt(),
            ttlSec = ttl, refreshBeforeSec = maxOf(4, (ttl * 0.3).roundToInt()),
            overlapRepost = l >= 30,
            pullOnScore = true,
            scoreHoldSec = lerp(45.0, 8.0, t).roundToInt(),
            dangerHoldSec = maxOf(3, lerp(20.0, 4.0, t).roundToInt()),
            novigMovePull = lerp(0.02, 0.06, t),
            coolOffSec = lerp(120.0, 8.0, t).roundToInt(),
            pickOffWindow = window, pickOffLimit = maxOf(2, (window * lerp(0.40, 0.70, t)).roundToInt()),
            maxCancelSec = lerp(8.0, 16.0, t).roundToInt(), maxPlaceSec = lerp(10.0, 20.0, t).roundToInt(),
            countCredit = l >= 70,
            level = l,
        )
    }

    /** The slider position whose margin is closest to [q]'s (where Custom rules sit on the line), 0-100. */
    fun nearest(q: LiveBidQuality): Int = (MIN..MAX).minByOrNull { Math.abs(margin(it) - q.margin) } ?: 55

    fun name(level: Int): String = when {
        level <= 10 -> "Safest"
        level <= 30 -> "Careful"
        level <= 45 -> "Steady"
        level <= 62 -> "Balanced"
        level <= 80 -> "More fills"
        level <= 92 -> "Volume"
        else -> "Maximum volume"
    }

    /** How often a bid at [margin] fills, in words (the tapes: about 2 fills a bid-hour at 3%, about 0 at 8%). */
    fun fillWord(margin: Double): String = when {
        margin <= 0.02 -> "very many fills"
        margin <= 0.035 -> "many fills"
        margin <= 0.05 -> "a fair number of fills"
        margin <= 0.065 -> "some fills"
        margin <= 0.08 -> "few fills"
        else -> "rare fills"
    }

    /** What the slider position means, in plain words: the numbers it set, and what to expect. */
    fun describe(q: LiveBidQuality): List<String> {
        val level = if (q.level in MIN..MAX) q.level else nearest(q)
        val custom = q.level !in MIN..MAX
        val kept = (q.margin - 0.012).coerceAtLeast(0.0)
        return listOf(
            (if (custom) "Custom rules (a saved preset or edits in Advanced), nearest to ${name(level)}" else "${name(level)} · $level of 100"),
            "Each bid sits ${LiveBidQuality.pct(q.margin)} under Pinnacle's fair price: that is its edge when it goes up.",
            "Expect ${fillWord(q.margin)}; a fill kept about +${LiveBidQuality.pct(kept)} on the stored tapes at this margin (a guide, not a promise).",
            "A bid comes down when its edge falls under ${LiveBidQuality.pct(q.pullBelowEv)}; the feature stops if the last ${q.keptWindow} fills kept less than ${LiveBidQuality.pct(q.minKeptEv)} on average after 30 s.",
            "Bids only where Pinnacle's fair is ${LiveBidQuality.pct(q.minFair)}-${LiveBidQuality.pct(q.maxFair)}, its own margin is under ${LiveBidQuality.pct(q.maxOverround)} and its limit is at least $${q.minPinnLimit.toLong()}.",
            "Waits ${q.settleSec} s for a price to settle and ${q.scoreHoldSec} s after a score" + (if (q.neverLead) "; never leads the book" else "") + (if (q.bothSides) "; may bid both sides" else "") + ".",
        )
    }

    /**
     * Why the feature should stop because its fills are not keeping their edge: the last [LiveBidQuality.keptWindow] real fills' edge 30 s later ([kept], oldest first) averaged under [LiveBidQuality.minKeptEv]. Null while there are too
     * few fills or the average is fine. Pure.
     */
    fun keptHalt(kept: List<Double>, q: LiveBidQuality): String? {
        val w = q.keptWindow.coerceAtLeast(1)
        if (kept.size < w) return null
        val recent = kept.takeLast(w)
        val avg = recent.average()
        return if (avg < q.minKeptEv) "the last $w live bid fills kept an average ${LiveBidQuality.pct(avg)} edge 30 s after the fill, under the ${LiveBidQuality.pct(q.minKeptEv)} you require: bids are being filled when they are wrong" else null
    }
}
