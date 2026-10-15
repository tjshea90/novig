package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The lines of a priced board worth taking to Novig's books for a bid (Tj, 2026-10-15: "GitHub provides the data that recommends the positive EV bids, and all the phone does is post the bids"). Made on GitHub from
 * the engine's own scan, under the phone's mirrored bid rules; the phone gives each one Novig's current book and runs the unchanged desk ([MakerLines.from]'s successors: [MakerQuote.decideAll], [MakerPlan]).
 *
 * A line is a candidate when every check of [MakerQuote.precheck] that does NOT need Novig's book passes: the start and trap windows, the market, the kind, the popularity filters, the fair and its age, the held
 * life, the price window. The last check of precheck, "Novig already offers it at or under this bid", needs the book and is the phone's to make with the book it reads, so the line is judged with no offer here.
 * It is judged at [now] and at [now] + [LOOKAHEAD_MS] (a line that enters a window while the list is on its way is still sent), and the phone judges it again at its own clock with its own rules, so a
 * candidate is only ever a superset: nothing here decides a bid.
 */
object BidCandidates {
    /** A candidate line and the event's own start (the fair's freshness rule keys on it, not on the earlier of the event's and the market's that [MakerLine.startsTs] holds). */
    data class BidLine(val line: MakerLine, val eventStartsTs: Long)

    /** How far ahead of [now] a line is also judged. */
    const val LOOKAHEAD_MS = 15 * 60_000L

    /**
     * The candidates of [result] under [settings] at [now], soonest game first (the order the phone reads their books in). [unavailable]: why a side must not be bid on (a player who is out), or null.
     * Each line carries its own book fairs and the sharp books' where it passes (the heavy devig, done here), and the Novig side the engine's scan read (the phone's own read replaces it when newer).
     */
    fun from(result: ScanResult?, settings: ScanSettings, now: Long, unavailable: (Opportunity) -> String? = { null }): List<BidLine> {
        result ?: return emptyList()
        val rules = MakerRules.of(settings)
        return result.opportunities.asSequence()
            .filter { MakerLines.inScope(it, result, settings) }
            .distinctBy { it.outcome.outcomeId }
            .mapNotNull { o ->
                val line = MakerLines.lineOf(o, now, unavailable(o))
                val fairSide = line.copy(offer = null, bestBid = null)
                val wanted = listOf(now, now + LOOKAHEAD_MS).any { t -> MakerQuote.precheck(fairSide.copy(fairOld = o.fairIsOld(t)), rules, t) !is MakerQuote.Pre.No }
                if (wanted) BidLine(MakerLines.withFairs(line, o), o.event.startsTs) else null
            }
            .sortedBy { it.line.startsTs }
            .toList()
    }
}
