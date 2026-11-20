package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.BookCompany
import com.tjshea.vigilant.engine.ReputableBooks

/**
 * The parlay maker's fair store for player props, built from EVERY price the reference feeds carry for a player's stat, not only the lines the scan priced (MK22, Tj 2026-11-20: "propline and parlayapi both carry
 * all kinds of props and alternate markets. The engine should carry fair odds on almost everything"; "out of over 9000 trades the engine could price 2").
 *
 * The scan prices a Novig strike only when 2+ books post EXACTLY that strike two-sided and it discards the books' "N+" ladders ("20+ Points": a Yes-only price per strike, which is where a book's alternate
 * strikes are): measured on PropLine's real boards of 2026-11-20 00:28 UTC, that is 32% of Novig's NBA prop strikes, 73% NHL, 27% NFL. Counting the milestone rows too, 99% of those strikes have at least one price
 * at the same strike and 72-91% have four or more (research/prop_ladder_fair_2026-11-20.md). Per player, stat and strike:
 *  - every two-sided Over/Under row gives the books' over probability, devigged proportionally;
 *  - every "N+" row gives its implied probability 1 / decimal divided by the book's own margin ([calibrate]: the median ratio of that book's "N+" price to the two-sided consensus where both exist, 1.06-1.09
 *    measured, 1.07 where a book has too few shared strikes);
 *  - one vote per company ([BookCompany]); the estimate is their mean. Against Novig's own two-sided mid on the same strikes: with 4+ votes the mean absolute error is 1.4-2.4 probability points and a side's
 *    relative error 6-9% (sd); with 1-2 votes 3-5 points, which is why the maker still wants [RfqConfig.minBooks] companies.
 * A strike no book posts is left to [RfqLadder] (interpolation, extrapolation). Never used for a game line, never for a side the votes put outside [MIN_SIDE]..[MAX_SIDE].
 */
object PropLadderFair {
    /** Margin of a book's "N+" price over the fair when the book has too few strikes to measure it. */
    const val DEFAULT_HOLD = 1.07
    /** The calibrated margin is kept inside this range; a book outside it is mis-quoted and its ratio is not trusted. */
    const val HOLD_MIN = 1.02
    const val HOLD_MAX = 1.15
    /** Shared strikes needed to calibrate a book's margin. */
    const val CALIBRATE_MIN = 12
    /** A side priced from votes must sit between these (a long shot's or a heavy favourite's margin is not the mid-range's). */
    const val MIN_SIDE = 0.08
    const val MAX_SIDE = 0.93

    /** One strike of one player's stat: the votes at it. */
    class Strike(val strike: Double, val overProb: Double, val companies: Int, val asOfMs: Long?)

    /** (player key, Novig stat) of one game -> the strikes with votes, by strike. */
    class Table(val strikes: Map<Double, Strike>)

    /** All of one reference event's prop tables, keyed by "<playerKey>|<stat>". */
    class Store(val byRefEvent: Map<String, Map<String, Table>>, val holds: Map<String, Double>, val votes: Int, val milestoneVotes: Int) {
        fun table(refEventId: String, playerKey: String, stat: String): Table? = byRefEvent[refEventId]?.get("$playerKey|$stat")
        companion object { val EMPTY = Store(emptyMap(), emptyMap(), 0, 0) }
    }

    private class Vote(val bookKey: String, val overProb: Double, val milestone: Boolean, val seenMs: Long?)

    /** Proportional devig of a two-sided quote: the over probability, or null for a quote that is not a market (an arbitrage or a one-sided price). */
    fun devig(overDecimal: Double, underDecimal: Double): Double? {
        if (!(overDecimal > 1.0) || !(underDecimal > 1.0) || !overDecimal.isFinite() || !underDecimal.isFinite()) return null
        val o = 1.0 / overDecimal
        val u = 1.0 / underDecimal
        if (o + u < 1.0) return null
        return o / (o + u)
    }

    /**
     * Each book's margin on its "N+" prices: over the events given, the median of (its implied probability) / (the mean of the OTHER books' two-sided over probability) at the strikes where both exist and the
     * consensus is mid-range (15%-90%) with at least two two-sided books. Only books with [CALIBRATE_MIN] such strikes, inside [HOLD_MIN]..[HOLD_MAX].
     */
    fun calibrate(events: List<RefEvent>): Map<String, Double> {
        val ratios = HashMap<String, MutableList<Double>>()
        for (ev in events) {
            val two = twoSided(ev)
            for (ms in ev.milestones) {
                val votes = two["${PlayerNames.key(ms.subject)}|${ms.stat}|${ms.strike}"] ?: continue
                if (votes.size < 2) continue
                val cons = votes.map { it.overProb }.average()
                if (cons !in 0.15..0.90) continue
                ratios.getOrPut(ms.bookKey) { ArrayList() } += (1.0 / ms.decimal) / cons
            }
        }
        return ratios.filterValues { it.size >= CALIBRATE_MIN }.mapValues { (_, v) -> median(v) }.filterValues { it in HOLD_MIN..HOLD_MAX }
    }

    private fun twoSided(ev: RefEvent): Map<String, List<Vote>> {
        val out = HashMap<String, MutableList<Vote>>()
        for (m in ev.markets) {
            if (m.kind != LineKind.PLAYER_PROP || m.period != 0) continue
            val subject = m.subject ?: continue
            val stat = m.stat ?: continue
            val over = m.quotes.singleOrNull { it.side == Side.OVER } ?: continue
            val under = m.quotes.singleOrNull { it.side == Side.UNDER } ?: continue
            val point = over.point ?: continue
            val p = devig(over.decimalOdds, under.decimalOdds) ?: continue
            out.getOrPut("${PlayerNames.key(subject)}|$stat|$point") { ArrayList() } += Vote(m.bookKey, p, false, m.lastUpdateMs)
        }
        return out
    }

    /** The store for the reference events given (the PropLine props of the games the maker may be asked about). [holds] are the calibrated margins, or empty to calibrate from [events]. */
    fun build(events: List<RefEvent>, holds: Map<String, Double> = calibrate(events)): Store {
        val byEvent = HashMap<String, Map<String, Table>>()
        var votes = 0
        var milestoneVotes = 0
        for (ev in events) {
            val groups = HashMap<String, MutableMap<Double, MutableList<Vote>>>()   // "player|stat" -> strike -> votes
            for ((k, v) in twoSided(ev)) {
                val (player, stat, strike) = k.split('|').let { Triple(it[0], it[1], it[2].toDouble()) }
                groups.getOrPut("$player|$stat") { HashMap() }.getOrPut(strike) { ArrayList() } += v
            }
            for (ms in ev.milestones) {
                if (!(ms.decimal > 1.0) || !ms.decimal.isFinite()) continue
                val hold = holds[ms.bookKey] ?: DEFAULT_HOLD
                val p = (1.0 / ms.decimal) / hold
                if (p !in 0.01..0.99) continue
                groups.getOrPut("${PlayerNames.key(ms.subject)}|${ms.stat}") { HashMap() }.getOrPut(ms.strike) { ArrayList() } += Vote(ms.bookKey, p, true, ms.seenMs)
            }
            val tables = HashMap<String, Table>()
            for ((key, perStrike) in groups) {
                val strikes = HashMap<Double, Strike>()
                for ((strike, all) in perStrike) {
                    // one vote per company: a company's votes at one strike are averaged (its two-sided row and its "N+" row both count once), books the app never trusts are left out
                    val byCompany = all.filterNot { ReputableBooks.isBanned(it.bookKey) }.groupBy { BookCompany.of(it.bookKey) }
                    if (byCompany.isEmpty()) continue
                    val companyP = byCompany.values.map { vs -> vs.map { it.overProb }.average() }
                    // one stale vote must not date the whole strike: the median age of the votes, the older of the two middle ones with an even count (null when none says)
                    val ages = byCompany.values.mapNotNull { vs -> vs.mapNotNull { it.seenMs }.minOrNull() }.sorted()
                    votes += all.size
                    milestoneVotes += all.count { it.milestone }
                    strikes[strike] = Strike(strike, companyP.average(), byCompany.size, if (ages.isEmpty()) null else ages[(ages.size - 1) / 2])
                }
                if (strikes.isNotEmpty()) tables[key] = Table(strikes)
            }
            if (tables.isNotEmpty()) byEvent[ev.id] = tables
        }
        return Store(byEvent, holds, votes, milestoneVotes)
    }

    /** The over probability at [strike] from the votes AT that strike, or null: no vote there, or the side priced is outside [MIN_SIDE]..[MAX_SIDE]. */
    fun atStrike(table: Table, strike: Double, over: Boolean): Strike? {
        val s = table.strikes[strike] ?: return null
        val side = if (over) s.overProb else 1.0 - s.overProb
        if (side < MIN_SIDE || side > MAX_SIDE) return null
        return s
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    /** The store with the Novig event -> reference event pairing, as the ladder's [RfqLadder.Consensus]. */
    class Index(private val store: Store, private val refByNovigEvent: Map<String, String>) : RfqLadder.Consensus {
        override fun at(t: RfqLadder.Target): Strike? {
            val ref = refByNovigEvent[t.eventId] ?: return null
            // Novig's "_PROTECTED" markets (void when the player does not play, fair market value back) are the markets the books post (they void a player who does not play): the same ladder (MK27: 56 legs of 61 pregame trades with no fair were NFL touchdown props).
            val table = store.table(ref, t.player, t.marketType.removeSuffix("_PROTECTED")) ?: return null
            return atStrike(table, t.strike, t.over)
        }
        fun summary(): String = "${refByNovigEvent.size} games paired, ${store.byRefEvent.values.sumOf { it.size }} player stats, ${store.votes} votes (${store.milestoneVotes} from N+ ladders), margins ${store.holds.entries.sortedBy { it.key }.joinToString(" ") { "${it.key} %.3f".format(it.value) }.ifEmpty { "default" }}"
    }
}
