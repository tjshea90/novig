package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import kotlin.math.abs

/**
 * One Novig market tied to the Pinnacle line that prices it: [outcomeBySide] says which Novig outcome is which side of Pinnacle's line, so a fair price for a side buys the right outcome.
 * [strike]: a spread's HOME handicap, a total's line, null for a moneyline, in NOVIG's orientation ([swapped]: Novig's home is Pinnacle's away, which happens for players).
 */
data class LiveTarget(
    val pinnEventId: Long,
    val event: NovigEvent,
    val market: NovigMarket,
    val type: PinnLineType,
    val strike: Double?,
    val swapped: Boolean,
    val outcomeBySide: Map<PinnSide, String>,
) {
    /** The Pinnacle line that prices this market now, or null when Pinnacle has none for it (the line moved off this strike, the market is not offered). */
    fun line(e: PinnEvent): PinnLine? = e.lines.values.firstOrNull { l ->
        l.period == 0 && l.type == type && !l.alternate && l.fair.keys.none { it == PinnSide.DRAW } && when (type) {
            PinnLineType.MONEYLINE -> true
            PinnLineType.SPREAD -> l.points?.let { abs(it - (if (swapped) -strike!! else strike!!)) < 1e-9 } == true
            PinnLineType.TOTAL -> l.points?.let { abs(it - strike!!) < 1e-9 } == true
        }
    }
}

object LiveMatcher {
    /** A Pinnacle matchup and a Novig game are the same game when their start times are within this (a rematch the same day is not). */
    const val MAX_START_GAP_MS = 5 * 3_600_000L

    /** A matched pair: [swapped] when Novig's home team is Pinnacle's away. */
    data class Pair(val pinnEventId: Long, val event: NovigEvent, val swapped: Boolean)

    /**
     * For each Novig game, the Pinnacle matchup that is it: the best name match (both teams must fit), starting near the same time, preferring a live and recently heard-from matchup (one fixture
     * can have a stale re-issued child beside the live one, docs: "key by rec.id"). Games with no clear match are left out, never guessed.
     */
    fun matchEvents(pinn: Collection<PinnEvent>, novig: Collection<NovigEvent>, nowMs: Long, accept: (PinnEvent, NovigEvent) -> Boolean = { _, _ -> true }): List<Pair> {
        val usable = pinn.filter { it.regular && it.home.isNotBlank() && it.away.isNotBlank() }
        val used = HashSet<Long>()
        val out = ArrayList<Pair>()
        for (n in novig) {
            val m = n.matchup ?: continue
            var best: PinnEvent? = null
            var bestScore = 0.0
            var bestSwapped = false
            for (p in usable) {
                if (p.id in used || !accept(p, n)) continue
                if (p.startMs > 0 && n.startsTs > 0 && abs(p.startMs - n.startsTs) > MAX_START_GAP_MS) continue
                val straight = TeamMatcher.gameScore(m.home, m.away, p.home, p.away)
                val swapped = TeamMatcher.gameScore(m.home, m.away, p.away, p.home)
                val score = maxOf(straight, swapped)
                if (score <= 0.0) continue
                // A live, recently heard-from matchup beats a stale one with the same teams.
                // The nearer start wins a tie (a doubleheader's two games have the same teams a few hours apart).
                val gapPenalty = if (p.startMs > 0 && n.startsTs > 0) abs(p.startMs - n.startsTs) / 3_600_000.0 * 0.01 else 0.0
                val rank = score + (if (p.live) 0.25 else 0.0) + (if (nowMs - p.lastFrameAtMs < 60_000L) 0.1 else 0.0) - gapPenalty
                val bestRank = bestScore
                if (best == null || rank > bestRank) {
                    best = p
                    bestScore = rank
                    bestSwapped = swapped > straight
                }
            }
            if (best != null) {
                used += best.id
                out += Pair(best.id, n, bestSwapped)
            }
        }
        return out
    }

    /** The targets a matched game offers: its full-game moneyline, half-point spreads and half-point totals whose outcomes can be told apart for certain. */
    fun targets(pair: Pair, markets: Collection<NovigMarket>): List<LiveTarget> = markets.mapNotNull { m ->
        if (m.eventId != pair.event.eventId || !m.isOpen || m.outcomes.size != 2) return@mapNotNull null
        when (m.marketType) {
            "MONEY" -> money(pair, m)
            "SPREAD" -> spread(pair, m)
            "TOTAL" -> total(pair, m)
            else -> null
        }
    }

    private fun side(novigHome: Boolean, swapped: Boolean): PinnSide = if (novigHome != swapped) PinnSide.HOME else PinnSide.AWAY

    private fun money(pair: Pair, m: NovigMarket): LiveTarget? {
        val matchup = pair.event.matchup ?: return null
        val homeFlags = m.outcomes.map { o -> TeamMatcher.labelIsAway(o.name, matchup.away, matchup.home)?.not() ?: return null }
        if (homeFlags[0] == homeFlags[1]) return null
        val map = m.outcomes.indices.associate { i -> side(homeFlags[i], pair.swapped) to m.outcomes[i].outcomeId }
        return LiveTarget(pair.pinnEventId, pair.event, m, PinnLineType.MONEYLINE, null, pair.swapped, map)
    }

    /** A spread's outcome names end in their handicap ("OKC -31.5" / "MIL +31.5"); the home one is the handicap that equals the market's strike. Whole numbers can push: left alone. */
    private fun spread(pair: Pair, m: NovigMarket): LiveTarget? {
        val strike = m.strike ?: return null
        if (strike % 1.0 == 0.0) return null
        val nums = m.outcomes.map { trailingNumber(it.name) ?: return null }
        val homeIdx = nums.indexOfFirst { abs(it - strike) < 1e-9 }
        if (homeIdx < 0 || nums[1 - homeIdx] != -nums[homeIdx]) return null
        val map = mapOf(side(true, pair.swapped) to m.outcomes[homeIdx].outcomeId, side(false, pair.swapped) to m.outcomes[1 - homeIdx].outcomeId)
        return LiveTarget(pair.pinnEventId, pair.event, m, PinnLineType.SPREAD, strike, pair.swapped, map)
    }

    private fun total(pair: Pair, m: NovigMarket): LiveTarget? {
        val strike = m.strike ?: m.outcomes.firstNotNullOfOrNull { trailingNumber(it.name) } ?: return null
        if (strike % 1.0 == 0.0) return null
        val over = m.outcomes.firstOrNull { it.name.startsWith("Over", ignoreCase = true) } ?: return null
        val under = m.outcomes.firstOrNull { it.name.startsWith("Under", ignoreCase = true) } ?: return null
        return LiveTarget(pair.pinnEventId, pair.event, m, PinnLineType.TOTAL, strike, pair.swapped, mapOf(PinnSide.OVER to over.outcomeId, PinnSide.UNDER to under.outcomeId))
    }

    private val NUMBER = Regex("""([+-]?\d+(?:\.\d+)?)\s*$""")

    internal fun trailingNumber(name: String): Double? = NUMBER.find(name.trim())?.groupValues?.get(1)?.toDoubleOrNull()
}
