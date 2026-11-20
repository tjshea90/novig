package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import kotlin.math.abs

/**
 * The fair of a leg in a game that is IN PLAY, from Novig's own live book (MK31, Tj 2026-11-20: "Figure out how to price and fill live, in game parlay orders. I want fills but also not too risky and nothing over
 * a 50% or so chance of winning"). At that hour 95% of the market's executed parlays have a leg in play (research/review/CHANGES.md MK30), and the scan has no fair for a game in play (its books' lines are
 * minutes old: the REST scan never lists live games). Novig's live moneylines, spreads and totals are a liquid market of their own, so the middle of a market's best bids is the fair:
 *
 *  - the best bid on outcome A is what a buyer of A pays at least; the best bid on the OTHER outcome B turns into the cheapest price A can be bought at, 1 - bidB. The middle of the two, (bidA + 1 - bidB) / 2,
 *    is A's probability, and the gap between them (the spread) says how sure that is;
 *  - a market whose spread is wider than [DEFAULT_MAX_SPREAD], whose book has no bid on one side, whose lifecycle is closed or whose price is older than the caller allows is not priced at all (no fair beats a
 *    wrong one: the pricer declines the parlay);
 *  - only game lines (moneyline, spread, total, team total) of games Novig says are in play; never a player prop (no live prop fair), never a market the feed has closed.
 * The pricer adds a premium on top ([RfqConfig.livePremium], [RfqConfig.livePointsPerLeg]): a buyer accepts 6-29 s after the auction closes, and the game moves meanwhile.
 */
object LiveFair {
    /** [LegFair.source] of a leg priced this way. The pricer lets a live leg through only when it carries it. */
    const val SOURCE = "novig-live"

    /** The market types priced live. */
    val TYPES: List<String> = listOf("MONEY", "SPREAD", "TOTAL", "TEAM_TOTAL")

    /** Widest gap between the best bid and the cheapest buy price that still counts as a price (3 points of probability is a normal live moneyline; 4 allows a thin alternate line). */
    const val DEFAULT_MAX_SPREAD = 0.04

    /** One outcome of a live market: its probability and the evidence for it. */
    class Leg(
        val outcomeId: String,
        val marketId: String,
        val eventId: String,
        val eventDesc: String,
        val league: String,
        val marketType: String,
        val outcomeName: String,
        val strike: Double?,
        val startsMs: Long,
        val p: Double,
        val spread: Double,
        val asOfMs: Long,
        /** What [SameGame] needs to price this leg beside others of the same game (MK37): the line's kind, the side taken, a spread's HOME handicap, a team total's team. Null kind = not read: the leg prices alone only. */
        val kind: LineKind? = null,
        val side: Side? = null,
        val homeLine: Double? = null,
        val awayTeam: Boolean = false,
    ) {
        fun toLegFair(): LegFair = LegFair(
            outcomeId, eventId, "live-novig $marketType $outcomeName ($eventDesc)", p, 1, asOfMs, startsMs, true, marketType,
            kind = kind, side = side, period = 0, homeLine = homeLine, awayTeam = awayTeam,
            league = league, outcomeName = outcomeName, eventDesc = eventDesc, marketId = marketId, source = SOURCE,
        )
    }

    /** The kind of a market's line and the side each of its two outcomes takes, read from Novig's own names the way the scan's planner reads them. */
    class Structure(val kind: LineKind, val sideA: Side, val sideB: Side, val homeLine: Double? = null, val awayTeam: Boolean = false)

    /**
     * What the same-game copula needs of [m] (MK37, Tj 2026-11-20: 22,000 of 72,000 RFQs of one hour were declined as "correlation unmodelled", 1,500 of them in-play pairs of a moneyline or spread with the total of ONE game):
     * a moneyline's and spread's outcomes are matched to the event's away and home teams (a spread's must carry Novig's own `strike`, the HOME handicap, or the market is not read), a total's and a team total's are
     * Over and Under of one number. Null when the names do not say: the leg is then priced alone only, and a parlay holding it with another leg of its game still declines as unmodelled.
     */
    fun structure(m: NovigMarket, ev: NovigEvent): Structure? {
        if (m.outcomes.size != 2) return null
        val (o1, o2) = m.outcomes
        val matchup = ev.matchup
        fun overUnder(kind: LineKind, awayTeam: Boolean): Structure? {
            val p1 = NovigText.parseTotalOutcome(o1.name) ?: return null
            val p2 = NovigText.parseTotalOutcome(o2.name) ?: return null
            if (abs(p1.second - p2.second) > 1e-9 || p1.first == p2.first) return null
            m.strike?.let { if (abs(it - p1.second) > 1e-9) return null }
            return Structure(kind, if (p1.first) Side.OVER else Side.UNDER, if (p2.first) Side.OVER else Side.UNDER, awayTeam = awayTeam)
        }
        return when (m.marketType) {
            "TOTAL" -> overUnder(LineKind.TOTAL, false)
            "TEAM_TOTAL" -> {
                if (matchup == null) return null
                val team = NovigText.subjectOf(m.description, m.marketType) ?: return null
                val away = TeamMatcher.labelIsAway(team, matchup.away, matchup.home) ?: return null
                overUnder(LineKind.TEAM_TOTAL, away)
            }
            "MONEY" -> {
                if (matchup == null) return null
                val firstAway = TeamMatcher.firstLabelIsAway(o1.name, o2.name, matchup.away, matchup.home) ?: return null
                Structure(LineKind.MONEYLINE, if (firstAway) Side.AWAY else Side.HOME, if (firstAway) Side.HOME else Side.AWAY)
            }
            "SPREAD" -> {
                if (matchup == null) return null
                val (l1, p1) = NovigText.parseSpreadOutcome(o1.name) ?: return null
                val (l2, p2) = NovigText.parseSpreadOutcome(o2.name) ?: return null
                if (abs(p1 + p2) > 1e-9) return null
                val firstAway = TeamMatcher.firstLabelIsAway(l1, l2, matchup.away, matchup.home) ?: return null
                val homePoint = if (firstAway) p2 else p1
                m.strike?.let { if (abs(it - homePoint) > 1e-9) return null }
                Structure(LineKind.SPREAD, if (firstAway) Side.AWAY else Side.HOME, if (firstAway) Side.HOME else Side.AWAY, homeLine = homePoint)
            }
            else -> null
        }
    }

    /** Both outcomes' probabilities from the two best bids (prices 0-1), or null when one side has no bid or the book is crossed or too wide. */
    fun mid(bidA: Double?, bidB: Double?, maxSpread: Double = DEFAULT_MAX_SPREAD): Pair<Double, Double>? {
        if (bidA == null || bidB == null || bidA <= 0.0 || bidB <= 0.0) return null
        val ask = 1.0 - bidB
        val spread = ask - bidA
        if (spread < -1e-9 || spread > maxSpread + 1e-9) return null
        return Pair((bidA + ask) / 2.0, spread)
    }

    /** Every priceable outcome of [markets] (two-outcome game lines of in-play [events]) from [books] at [now], keyed by outcome id. */
    fun build(markets: Collection<NovigMarket>, books: Map<String, NovigBook>, events: Map<String, NovigEvent>, maxSpread: Double = DEFAULT_MAX_SPREAD, maxAgeMs: Long = 10_000L, now: Long, streamCurrent: Boolean = false): Map<String, Leg> {
        val out = HashMap<String, Leg>(markets.size * 2)
        for (m in markets) {
            if (m.marketType !in TYPES || m.outcomes.size != 2) continue
            val ev = events[m.eventId]?.takeIf { it.isLive } ?: continue
            val b = books[m.marketId] ?: continue
            val lc = b.lifecycle
            if (lc != null && (lc.closed || lc.live == false)) continue
            // A book the stream holds is current as of now (every change since its snapshot has been applied: NovigStream.read): a quiet market's last CHANGE may be a minute old and its price still right.
            val asOf = if (streamCurrent) now else b.fetchedAtMs
            if (now - asOf > maxAgeMs) continue
            val a = m.outcomes[0]; val c = m.outcomes[1]
            val bidA = b.bidsByOutcome[a.outcomeId]?.firstOrNull()?.price
            val bidC = b.bidsByOutcome[c.outcomeId]?.firstOrNull()?.price
            val (pA, spread) = mid(bidA, bidC, maxSpread) ?: continue
            val st = structure(m, ev)
            out[a.outcomeId] = Leg(a.outcomeId, m.marketId, m.eventId, ev.description, ev.league, m.marketType, a.name, m.strike, ev.startsTs, pA, spread, asOf, st?.kind, st?.sideA, st?.homeLine, st?.awayTeam ?: false)
            out[c.outcomeId] = Leg(c.outcomeId, m.marketId, m.eventId, ev.description, ev.league, m.marketType, c.name, m.strike, ev.startsTs, 1.0 - pA, spread, asOf, st?.kind, st?.sideB, st?.homeLine, st?.awayTeam ?: false)
        }
        return out
    }

    /** The live fair of every in-play leg right now: replaced whole by the engine's loop, read by [Fairs] without a lock. */
    class Book {
        /** Outcome ids an RFQ asked about that the book had no price for, with when: the engine watches those markets (only what is asked for costs stream tokens). */
        val demand = java.util.concurrent.ConcurrentHashMap<String, Long>()
        fun note(outcomeId: String, atMs: Long) { if (demand.size < 200_000) demand[outcomeId] = atMs }
        /** The outcomes asked for within [windowMs] of [now]; older ones are forgotten. */
        fun recentDemand(now: Long, windowMs: Long): List<String> {
            val it = demand.entries.iterator(); val out = ArrayList<Pair<String, Long>>()
            while (it.hasNext()) { val e = it.next(); if (now - e.value > windowMs) it.remove() else out += e.key to e.value }
            return out.sortedByDescending { it.second }.map { it.first }
        }
        @Volatile private var legs: Map<String, Leg> = emptyMap()
        @Volatile var builtAtMs: Long = 0L
            private set
        fun replace(next: Map<String, Leg>, at: Long) { legs = next; builtAtMs = at }
        fun leg(outcomeId: String): Leg? = legs[outcomeId]
        fun size(): Int = legs.size
    }

    /**
     * [inner] first, with the game's state taken into account: a leg whose game has STARTED is priced only from the live [book] (the scan's own fair for it is a pregame line, minutes old), and when the live
     * book has no price for it the leg is returned marked as in play with no live source, which the pricer declines ("a leg is in play"). A leg of a game that has not started is [inner]'s, as before.
     * [enabled] false = exactly [inner].
     */
    class Fairs(private val inner: LegFairs, private val book: () -> Book?, private val enabled: () -> Boolean, private val clock: () -> Long = System::currentTimeMillis) : LegFairs {
        override fun leg(outcomeId: String): LegFair? {
            val base = inner.leg(outcomeId)
            if (!enabled()) return base
            val b = book()
            b?.leg(outcomeId)?.let { return it.toLegFair() }
            b?.note(outcomeId, clock())
            if (base != null && !base.live && base.startsMs <= clock()) return base.copy(live = true)
            return base
        }
    }
}
