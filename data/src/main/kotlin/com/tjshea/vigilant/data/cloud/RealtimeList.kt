package com.tjshea.vigilant.data.cloud

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
     */
    @Synchronized
    fun adopt(result: ScanResult, now: Long) {
        stats = result.stats
        val next = HashMap<String, Opportunity>(held.size + 256)
        for ((k, o) in held) if (keep(o, now)) next[k] = o
        for (o in result.opportunities) {
            if (!keep(o, now)) continue
            val old = next[o.key]
            if (old == null || newer(o, old)) next[o.key] = o
        }
        held = trim(next)
        watched = held.size
    }

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

    /** One line for the engine's log and status. */
    fun describe(): String = "real-time list: $watched sides watched (within ${nearPoints.toInt()} pts of +EV), $passes passes, last ${lastPassMs} ms; $turnedPositive turned +EV between scans, $dropped left the list"

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
        /** Sides this far below +EV are watched: a move of a few points (steam) makes them bets. */
        const val NEAR_POINTS = 4.0
        /** At most this many sides are re-priced a pass (a pass is a few milliseconds of arithmetic per thousand). */
        const val MAX_ROWS = 6_000
        /** How often a pass runs. */
        const val EVERY_MS = 3_000L
        private val EMPTY = ScanStats(0, 0, 0, 0, 0)
    }
}
