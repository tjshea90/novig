package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats

/**
 * The engine's real-time list (PB19, Tj 2026-10-25: "instead of scheduled scans, the scanner system would constantly keep real time data 24/7 and post positive EV bets immediately when found ... keeping
 * positive EV bets in the scanned results until the real time feeds from the books say it is no longer positive EV, or the real time novig socket says it is no longer available at the listed price.
 * basically, all bets listed in the scanner should be real time information"; research/PARLAYAPI_BUSINESS_2026-10-25.md §4-5).
 *
 * A full scan is still what FINDS a market (Novig's catalog, every feed's boards, the matching). Between and during scans this holds every priced pregame side within [nearPoints] of +EV (the listed bets
 * and the ones a move could make +EV: steam) and, every pass, has them priced again from memory only ([com.tjshea.vigilant.data.scanner.OddsScanner.liveReprice]: the boards with PropLine's and
 * ParlayAPI's pushes laid over them, Novig's price from the engine's socket). Pure state: the engine's loop drives it.
 */
class RealtimeList(
    private val nearPoints: Double = NEAR_POINTS,
    private val maxRows: Int = MAX_ROWS,
) {
    /** The watched sides by [Opportunity.key], each at its newest price. */
    private var held: Map<String, Opportunity> = emptyMap()
    private var stats: ScanStats? = null

    @Volatile var passes = 0L
        private set
    @Volatile var lastPassMs = 0L
        private set
    @Volatile var lastPassAtMs = 0L
        private set
    @Volatile var watched = 0
        private set
    /** Sides that turned +EV between two scans (a feed or Novig moved: what the trader is handed at once). */
    @Volatile var turnedPositive = 0L
        private set
    /** Listed sides a pass took off the list (no longer +EV, or Novig's price gone). */
    @Volatile var dropped = 0L
        private set

    /**
     * A scan's result (partial or final): its sides within reach are watched. A side the scan read (its Novig book or fair is no older than the held one) replaces the held one; a held side the scan has not
     * reached yet stays with the newer price this list gave it.
     *
     * PX9 (Tj 2026-10-25, "many results only have 1 book behind the fair odds"; GB@DET's spread listed with Kalshi alone beside the same game's 1st-half total priced by 7 feeds): a league whose fair-odds
     * sources have not all answered ([ScanResult.waitingFor]) is priced from the first to answer, and the scan's own list and the bid desk hide it for that reason; this list took it, and it stayed whenever
     * the finished scan's fuller fair put the side more than [nearPoints] below +EV (a row out of reach was ignored, never removed). Now: such leagues are skipped until they are complete, a scan row that is
     * newer or out of reach takes the held row out with it, and a scan row that matched as many feeds as the held one but was read earlier still gives the held row its fair ([rebase]: the scan's boards under the
     * held row's newer Novig price; null keeps the held row).
     */
    @Synchronized
    fun adopt(result: ScanResult, now: Long, rebase: ((held: Opportunity, scan: Opportunity) -> Opportunity?)? = null) {
        stats = result.stats
        val next = HashMap<String, Opportunity>(held.size + 256)
        for ((k, o) in held) if (keep(o, now)) next[k] = o
        for (o in result.opportunities) {
            if (result.waitingFor.isNotEmpty() && ScanResult.waitKey(o.league.novigName, o.kind == LineKind.PLAYER_PROP) in result.waitingFor) { skippedWaiting++; continue }
            val old = next[o.key]
            if (old == null) { if (keep(o, now)) next[o.key] = o; continue }
            val row = when {
                // The scan read this side's Novig book no earlier than the held row did: its row is the newest word on both halves, and if it is out of reach the side goes.
                (o.bookFetchedAtMs ?: 0L) >= (old.bookFetchedAtMs ?: 0L) -> o
                // Read earlier than the held row's live price, but from as many feeds: its fair under the held row's price.
                rebase != null && o.refEvent != null && old.refEvent !== o.refEvent && feedsOf(o) >= feedsOf(old) -> { rebased++; runCatching { rebase(old, o) }.getOrNull() ?: old }
                // A fresher fair with nothing to put it under the newer price: kept only while it is within reach (it could not say more than that before).
                (o.fairAsOfMs ?: 0L) > (old.fairAsOfMs ?: 0L) -> if (keep(o, now)) o else old
                else -> old
            }
            if (keep(row, now)) next[o.key] = row else next.remove(o.key)
        }
        held = trim(next)
        watched = held.size
    }

    /** Sides of partial scans left out because their league was still waiting for a fair-odds source, and held rows given a later scan's fair ([adopt]). */
    @Volatile var skippedWaiting = 0L
        private set
    @Volatile var rebased = 0L
        private set

    /** How many feeds' games the row's fair came from (a merged game's id joins the ids of every feed that matched it). */
    private fun feedsOf(o: Opportunity): Int = o.refEvent?.id?.count { it == '+' } ?: 0

    /** One pass: every held side priced again by [reprice]; returns the list as [settings] filter it. */
    fun pass(settings: ScanSettings, now: Long, reprice: (List<Opportunity>) -> List<Opportunity>): List<Opportunity> {
        val before = synchronized(this) { held }
        if (before.isEmpty()) return emptyList()
        val t0 = System.nanoTime()
        val repriced = reprice(before.values.toList())
        val st = stats ?: EMPTY
        val wasListed = ScanResult(emptyList(), before.values.toList(), st, now).feed(settings).mapTo(HashSet()) { it.key }
        val list = ScanResult(emptyList(), repriced, st, now).feed(settings)
        val nowListed = list.mapTo(HashSet()) { it.key }
        synchronized(this) {
            // A scan may have adopted newer rows while this pass ran: those win.
            val cur = HashMap(held)
            for (o in repriced) {
                val c = cur[o.key] ?: continue
                if (c === before[o.key]) { if (keep(o, now)) cur[o.key] = o else cur.remove(o.key) }
            }
            held = cur
            watched = cur.size
        }
        turnedPositive += nowListed.count { it !in wasListed }
        dropped += wasListed.count { it !in nowListed }
        passes++
        lastPassAtMs = now
        lastPassMs = (System.nanoTime() - t0) / 1_000_000L
        return list
    }

    /** The watched sides at their newest prices, by [Opportunity.key]. */
    @Synchronized fun current(): Map<String, Opportunity> = held

    /** One line for the engine's log and status. */
    fun describe(): String = "real-time list: $watched sides watched (within ${Math.round(nearPoints * 100)} pts of +EV), $passes passes, last ${lastPassMs} ms; $turnedPositive turned +EV between scans, $dropped left the list; $skippedWaiting partial-scan sides left out (a fair source still to answer), $rebased held sides given a later scan's fair"

    /** Pregame, priced, not started, and within reach of +EV. */
    private fun keep(o: Opportunity, now: Long): Boolean =
        !o.isLive && o.event.startsTs > now && o.fairProbability != null && (o.evPercent ?: Double.NEGATIVE_INFINITY) >= -nearPoints

    /** [a] was read more recently than [b] (its Novig book or its fair odds). */
    private fun newer(a: Opportunity, b: Opportunity): Boolean =
        (a.bookFetchedAtMs ?: 0L) >= (b.bookFetchedAtMs ?: 0L) || (a.fairAsOfMs ?: 0L) > (b.fairAsOfMs ?: 0L)

    /** The best [maxRows] by edge (the list's are first). */
    private fun trim(m: HashMap<String, Opportunity>): Map<String, Opportunity> =
        if (m.size <= maxRows) m else m.values.sortedByDescending { it.evPercent ?: Double.NEGATIVE_INFINITY }.take(maxRows).associateBy { it.key }

    companion object {
        /** Sides this far below +EV are watched (EV is a fraction: 0.04 = 4 points): a move of a few points (steam) makes them bets. */
        const val NEAR_POINTS = 0.04
        /** At most this many sides are re-priced a pass (a pass is a few milliseconds of arithmetic per thousand). */
        const val MAX_ROWS = 6_000
        /** How often a pass runs. */
        const val EVERY_MS = 3_000L
        private val EMPTY = ScanStats(0, 0, 0, 0, 0)
    }
}
