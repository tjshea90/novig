package com.tjshea.vigilant.data.novig.trading.maker

import java.util.Locale

/**
 * The picked-off guard (Tj, 2026-10-05: "my bids right now are being taken fast and I'm worried they aren't true positive Ev"; RESEARCH.md §88.3), pure.
 *
 * A bid is posted a margin under the fair. When it fills, the question is whether the fair was still above the price: a fill on a bid whose fair had already
 * moved under it is a taker who knew something the bid didn't. The CLV that settles that takes hours to days; the fair on the first scan after the fill
 * ([MakerBid.fairAtFill]) is there within minutes. So: of the newest [window] fills that scan priced, when half or more were filled at a price above the fair
 * then ([MakerBid.fillEv] under zero) and the average EV at the fill is under [MIN_MEAN_EV] (the bids claimed 4% or more when posted), the bids are being
 * picked off and stop themselves until Tj resumes them. Nothing else about a bid changes.
 */
object MakerGuard {

    /** At least this many judged fills before the guard says anything: a few fills are a few coin flips. */
    const val MIN_JUDGED = 6

    /** The average EV at the fill under which half the fills being picked off is a stop (the bids were posted at 4% or more). */
    const val MIN_MEAN_EV = 0.01

    data class Verdict(
        /** Fills judged (a scan priced the side after the fill), of the newest [window]. */
        val judged: Int,
        /** Of those, fills the fair had already moved under. */
        val pickedOff: Int,
        /** The average EV at the fills' prices against the fair then; null with none judged. */
        val meanEvAtFill: Double?,
        /** The average EV claimed when those bids were posted. */
        val meanEvAtPost: Double?,
        /** True: the bids should stop. */
        val tripped: Boolean,
    ) {
        /** One line in words for the Bids tab, the notification and Diagnostics. */
        val text: String
            get() = if (judged == 0) "no fill has been judged yet" else
                "$pickedOff of the last $judged fills were picked off (the fair had already moved under the price when the next scan priced them): " +
                    "average EV at the fill ${pct(meanEvAtFill)} against ${pct(meanEvAtPost)} when posted"
    }

    /** The newest [window] of [bids]' fills judged since [fromMs] (the guard's own start: Tj's last Resume), and what they say. */
    fun check(bids: List<MakerBid>, fromMs: Long = 0L, anchorSharp: Boolean = true, window: Int = 8): Verdict {
        val fills = bids.filter { it.filled > 0 && it.fillEv(anchorSharp) != null && (it.firstFillAtMs ?: 0L) >= fromMs && (it.firstFillAtMs ?: 0L) > 0L }
            .sortedByDescending { it.firstFillAtMs }.take(window)
        if (fills.isEmpty()) return Verdict(0, 0, null, null, false)
        val evs = fills.map { it.fillEv(anchorSharp)!! }
        val picked = evs.count { it < 0.0 }
        val mean = evs.average()
        val tripped = fills.size >= MIN_JUDGED && picked * 2 >= fills.size && mean < MIN_MEAN_EV
        return Verdict(fills.size, picked, mean, fills.map { it.evAtFair }.average(), tripped)
    }

    /** The stop's own words (kept on the setting, shown on the tab): [Verdict.text] and what to do. */
    fun haltedText(v: Verdict): String = v.text + ". Bids are stopped: look at the fills (Diagnostics › Bids), then Resume bids on the Bids tab."

    private fun pct(v: Double?): String = v?.let { String.format(Locale.US, "%+.1f%%", it * 100) } ?: "–"
}
