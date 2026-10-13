package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import kotlin.math.abs

/**
 * Accuracy half of the API study (Tj, 2026-10-12): how closely each feed's prices for the SAME books agree with Pinnacle's own, game by game, on full-game moneylines, spreads and totals of
 * the same number. A soft book legitimately differs from Pinnacle by a point or two, but the same book through a stale or wrong feed differs more; so the comparison is made per feed, on the same
 * books ([books], e.g. DraftKings and FanDuel, which every feed carries), and the figure that matters is how the feeds rank, not the size. Pure.
 */
object SourceAgreement {
    data class Stat(val lines: Int, val meanPoints: Double, val within1Pct: Double)

    private const val MIN_SIMILARITY = 0.5
    private const val MAX_START_GAP_MS = 3 * 3_600_000L

    private fun home(o1: Double, o2: Double) = (1 / o1) / (1 / o1 + 1 / o2)

    /** Pinnacle's no-vig home (or over) probability by line: moneyline null, spread = home handicap, total = number. */
    private fun pinnacleLines(e: RefEvent): Map<Pair<LineKind, Double?>, Double> {
        val out = HashMap<Pair<LineKind, Double?>, Double>()
        for (m in e.markets) {
            if (m.bookKey != "pinnacle" || m.period != 0 || m.kind !in setOf(LineKind.MONEYLINE, LineKind.SPREAD, LineKind.TOTAL)) continue
            val a = m.quotes.getOrNull(0)?.decimalOdds ?: continue
            val b = m.quotes.getOrNull(1)?.decimalOdds ?: continue
            out[m.kind to m.line] = home(a, b)
        }
        return out
    }

    /** For each feed id in [bySource]: its agreement with the Pinnacle events held in [pinnacle], on [books]. Feeds with nothing comparable are left out. */
    fun compute(bySource: Map<String, List<RefSnapshot>>, pinnacle: List<RefEvent>, books: Set<String>): Map<String, Stat> {
        val pin = pinnacle.map { it to pinnacleLines(it) }
        val out = LinkedHashMap<String, Stat>()
        for ((id, snaps) in bySource) {
            if (id == "pinnacle") continue
            val diffs = ArrayList<Double>()
            for (snap in snaps) for (e in snap.events) {
                // The Pinnacle game with the same teams and start (home and away possibly the other way round).
                val hit = pin.firstOrNull { (p, _) ->
                    abs(p.commenceMs - e.commenceMs) <= MAX_START_GAP_MS &&
                        ((TeamMatcher.similarity(p.home, e.home) >= MIN_SIMILARITY && TeamMatcher.similarity(p.away, e.away) >= MIN_SIMILARITY) ||
                            (TeamMatcher.similarity(p.home, e.away) >= MIN_SIMILARITY && TeamMatcher.similarity(p.away, e.home) >= MIN_SIMILARITY))
                } ?: continue
                val swapped = TeamMatcher.similarity(hit.first.home, e.home) < TeamMatcher.similarity(hit.first.home, e.away)
                for (m in e.markets) {
                    if (m.bookKey !in books || m.period != 0 || m.kind !in setOf(LineKind.MONEYLINE, LineKind.SPREAD, LineKind.TOTAL)) continue
                    val a = m.quotes.getOrNull(0)?.decimalOdds ?: continue
                    val b = m.quotes.getOrNull(1)?.decimalOdds ?: continue
                    // Orient to Pinnacle's home: a swapped event turns a moneyline/spread into the other team; a total (over first) is unaffected.
                    val line = if (swapped && m.kind == LineKind.SPREAD) m.line?.let { -it } else m.line
                    val p = hit.second[m.kind to line] ?: continue
                    val mine = home(a, b).let { if (swapped && m.kind != LineKind.TOTAL) 1 - it else it }
                    diffs += abs(mine - p) * 100
                }
            }
            if (diffs.isNotEmpty()) out[id] = Stat(diffs.size, diffs.average(), diffs.count { it <= 1.0 }.toDouble() / diffs.size)
        }
        return out
    }
}
