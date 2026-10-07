package com.tjshea.vigilant.data.live

import java.text.Normalizer
import java.util.Locale

/**
 * Which free live score or odds feed shows a play first, and does any beat Novig's own price? (Tj, 2026-10-07: "test all available sources that can be used as a rapid source of odds or
 * scores ... implemented in the app for live betting"; RESEARCH.md §99, §106.) The in-app counterpart of `tools/research/live_feed_race.py`, pure: feeds hand it what they saw, each stamped
 * on arrival ([Sighting], [OddsTick]), plus Novig's moneyline trades with the engine's own time on each ([NovigTick]: the instant Novig's price MOVED), and [FeedRace.report] says,
 * per feed, how far behind the earliest feed it was, whether it showed each score BEFORE Novig's price moved, and what really traded at the old price after it did (a floor on
 * what a taker could have had). There is no ground truth of when a play happened, so every number is RELATIVE to the other feeds and to Novig; no order is ever placed from here.
 */
object FeedRace {

    /** One feed's view of a game's score at one moment: [atMs] is when it ARRIVED here (the middle of the request for a poll). [init] = the first score seen for that game from that feed (a baseline, not a play). */
    data class Sighting(
        val src: String, val gameId: String, val home: String, val away: String, val h: Int, val a: Int, val atMs: Long,
        val init: Boolean = false, val live: Boolean = true, val rttMs: Long = 0,
    )

    /** One Novig moneyline trade: [event] is Novig's description ("Away @ Home"), [tsMs] the engine's time (when the price moved), [price] the resting order's price. */
    data class NovigTick(val event: String, val marketId: String, val outcomeId: String, val price: Double, val qty: Long, val tsMs: Long)

    /** Polymarket's match-winner odds for one outcome as they changed: the mid of its best bid and ask, [seenMs] on arrival, [serverMs] the exchange's own time when it gave one. */
    data class OddsTick(val event: String, val outcome: String, val mid: Double, val seenMs: Long, val serverMs: Long? = null)

    /** Records a [Sighting] only when a game's score changes (and a baseline the first time it is seen), like the research tool's tracker. */
    class Tracker(private val src: String, private val sink: (Sighting) -> Unit) {
        private val last = HashMap<String, Pair<Int, Int>>()

        @Synchronized
        fun see(gameId: String, home: String, away: String, h: Int, a: Int, atMs: Long, rttMs: Long = 0, live: Boolean = true) {
            val key = h to a
            val prev = last[gameId]
            if (prev == key) return
            last[gameId] = key
            sink(Sighting(src, gameId, home, away, h, a, atMs, init = prev == null, live = live, rttMs = rttMs))
        }
    }

    // ---- names ----------------------------------------------------------------------------------------------------------------

    private val STOP = setOf(
        "fc", "cf", "sc", "ac", "the", "de", "of", "city", "united", "real", "club", "st", "saint", "a", "and", "los", "angeles", "la", "new", "york", "ny", "san", "north", "south", "west", "east",
        // Novig's tennis names end in the round ("Marco Trungelliti Round of 128"): never a reason for two players to be one team.
        "round", "128", "64", "32", "16", "quarterfinal", "semifinal", "final", "qualifying",
    )

    fun tokens(name: String): Set<String> =
        Normalizer.normalize(name, Normalizer.Form.NFKD).filter { it.code < 128 }.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"), " ").split(' ')
            .filter { it.length > 1 && it !in STOP }.toSet()

    fun sameTeam(a: String, b: String): Boolean = tokens(a).intersect(tokens(b)).isNotEmpty()

    /** (home, away) of two sources are one game: some token of one team matches a team of the other, both ways, in either order. */
    fun sameGame(g1: Pair<String, String>, g2: Pair<String, String>): Boolean =
        (sameTeam(g1.first, g2.first) && sameTeam(g1.second, g2.second)) || (sameTeam(g1.first, g2.second) && sameTeam(g1.second, g2.first))

    // ---- the analysis ---------------------------------------------------------------------------------------------------------

    class Game(val names: Pair<String, String>, val recs: MutableList<Sighting> = ArrayList())

    /** Score records grouped into games across feeds by team-name tokens. */
    fun cluster(scores: List<Sighting>): List<Game> {
        val games = ArrayList<Game>()
        for (r in scores) {
            if (r.home.isBlank() || r.away.isBlank()) continue
            val g = games.firstOrNull { sameGame(it.names, r.home to r.away) } ?: Game(r.home to r.away).also { games += it }
            g.recs += r
        }
        return games
    }

    /** For each new score of [g], each feed's first time it showed a score above the one it first showed: {(h, a): {src: atMs}}. */
    fun transitions(g: Game): Map<Pair<Int, Int>, Map<String, Long>> {
        val out = LinkedHashMap<Pair<Int, Int>, MutableMap<String, Long>>()
        for ((src, rs) in g.recs.sortedBy { it.atMs }.groupBy { it.src }) {
            var top: Int? = null
            for (r in rs) {
                val tot = r.h + r.a
                if (top == null) { top = tot; continue }
                if (tot > top) {
                    out.getOrPut(r.h to r.a) { LinkedHashMap() }.putIfAbsent(src, r.atMs)
                    top = tot
                }
            }
        }
        return out
    }

    /** One (market, outcome)'s trades as (seconds, price, contracts), oldest first. */
    class Series(val key: Pair<String, String>, val pts: List<Triple<Double, Double, Long>>)

    /** The moneyline trades of the Novig event matching [names]. */
    fun novigSeries(trades: List<NovigTick>, names: Pair<String, String>): List<Series> {
        val by = LinkedHashMap<Pair<String, String>, MutableList<Triple<Double, Double, Long>>>()
        for (t in trades) {
            val a = t.event.substringBefore(" @ ", "")
            val b = t.event.substringAfter(" @ ", "")
            if (a.isEmpty() || b.isEmpty()) continue
            if (sameGame(b to a, names) || sameGame(a to b, names)) by.getOrPut(t.marketId to t.outcomeId) { ArrayList() } += Triple(t.tsMs / 1000.0, t.price, t.qty)
        }
        return by.map { (k, v) -> Series(k, v.sortedBy { it.first }) }
    }

    class Move(val atSec: Double, val delta: Double, val refs: Map<Pair<String, String>, Double>)

    private fun median(xs: List<Double>): Double = xs.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2.0 }

    /**
     * The earliest trade at or after [tRef] - [pre] whose price is [jump] or more from the median price of the [lookback] seconds before it (within [horizon] of [tRef]), over every
     * series; null when none moved or no series has two trades before. [Move.refs] are each series' own pre-score median.
     */
    fun firstMove(ser: List<Series>, tRef: Double, jump: Double, lookback: Double = 30.0, pre: Double = 15.0, horizon: Double = 90.0): Move? {
        var best: Pair<Double, Double>? = null
        val refs = HashMap<Pair<String, String>, Double>()
        for (s in ser) {
            val before = s.pts.filter { it.first >= tRef - pre - lookback && it.first < tRef - pre }.map { it.second }
            if (before.size < 2) continue
            val ref = median(before).also { refs[s.key] = it }
            for ((t, p, _) in s.pts) {
                if (t >= tRef - pre && t - tRef <= horizon && Math.abs(p - ref) >= jump) {
                    if (best == null || t < best.first) best = t to (p - ref)
                    break
                }
            }
        }
        return best?.let { Move(it.first, it.second, refs) }
    }

    /** What a taker who saw the score at [tFeed] could have bought at the OLD price (trades that happened, before the market settled at [tMove] + [settle]), net of Novig's in-play taker fee. */
    class Stale(val trades: Int, val payout: Double, val gain: Double)

    fun staleFills(ser: List<Series>, refs: Map<Pair<String, String>, Double>, tFeed: Double, tMove: Double, jump: Double, settle: Double = 3.0, after: Double = 20.0): Stale {
        var n = 0
        var payout = 0.0
        var gain = 0.0
        for (s in ser) {
            val ref = refs[s.key] ?: continue
            val post = s.pts.filter { it.first >= tMove && it.first <= tMove + after }.map { it.second }
            if (post.isEmpty()) continue
            val new = median(post)
            if (ref - new < jump) continue   // this outcome did not fall: its resting bids were not too high
            for ((t, p, qty) in s.pts) {
                if (t >= tFeed && t <= tMove + settle && p >= ref - jump / 2) {
                    val g = p - new - FEE_C * p * (1 - p)
                    if (g > 0) { n++; payout += qty / 100.0; gain += g * qty / 100.0 }
                }
            }
        }
        return Stale(n, payout, gain)
    }

    /** Novig's in-play taker fee coefficient: 0.03 * p * (1 - p) per $1 of payout (NOVIG_API.md §8). */
    const val FEE_C = 0.03

    /** How far each feed was behind the earliest one over the scores two or more feeds saw (seconds). */
    class Lag(val src: String, val scores: Int, val first: Int, val median: Double, val p25: Double, val p75: Double, val p90: Double, val max: Double)

    /** Against Novig's price: per feed, the scores on games Novig traded whose moneyline moved, in how many the feed was BEFORE the move, by 3 s or more, and the lead in seconds (move minus feed). */
    class Lead(val src: String, val scores: Int, val before: Int, val by3s: Int, val median: Double, val p10: Double, val p90: Double)

    class StaleRow(val src: String, val trades: Int, val payout: Double, val gain: Double)

    /** Polymarket's ODDS against Novig's: of the scores whose Novig price moved, how often Polymarket's mid moved first, and by how much (seconds, Novig's move minus Polymarket's). */
    class OddsLead(val scores: Int, val before: Int, val by3s: Int, val median: Double, val p10: Double, val p90: Double)

    class Report(
        val sightings: Int, val feeds: List<String>, val novigTrades: Int, val games: Int, val newScores: Int, val seenByTwo: Int,
        val lags: List<Lag>, val eventsOnNovig: Int, val moved: Int, val leads: List<Lead>, val stale: List<StaleRow>, val odds: OddsLead?,
        val roundTripMs: Map<String, Double>,
    ) {
        /** The verdict in words, for Diagnostics: which feed (if any) showed scores before Novig's price moved, and by how much. Honest when there is not enough data. */
        fun verdict(): String {
            if (sightings == 0) return "Live feed test: nothing recorded yet (it needs live games)."
            if (newScores == 0) return "Live feed test: $sightings readings from ${feeds.joinToString(", ")} over $games games, but no score has changed yet."
            val head = "Live feed test: $newScores scores seen live ($seenByTwo by two or more feeds), $moved moved Novig's moneyline."
            val best = leads.filter { it.scores >= MIN_SCORES }.maxByOrNull { it.median }
            val tail = when {
                moved < MIN_SCORES -> " Too few moves to say whether any feed leads Novig (needs $MIN_SCORES)."
                best == null -> " No feed has enough scores yet."
                best.median >= 3.0 && best.before * 2 >= best.scores -> " ${best.src} showed the score before Novig's price moved in ${best.before} of ${best.scores}, by ${fmt(best.median)} s at the median: worth a closer look."
                else -> " No feed was ahead of Novig's price: the best was ${best.src} at ${fmt(best.median)} s (negative = after Novig moved), before it in ${best.before} of ${best.scores}."
            }
            return head + tail
        }

        /** The full table as lines (Diagnostics and the share file). */
        fun lines(): List<String> = buildList {
            add("$sightings score readings from ${feeds.size} feeds (${feeds.joinToString(", ")}); $novigTrades Novig trades; $games games")
            add("$newScores new scores seen live; $seenByTwo seen by two or more feeds")
            if (lags.isNotEmpty()) {
                add("how far behind the earliest feed (s; 0 = first), scores two or more feeds saw:")
                for (l in lags) add("  ${l.src.padEnd(10)} scores ${l.scores} first ${l.first} median ${fmt(l.median)} p25 ${fmt(l.p25)} p75 ${fmt(l.p75)} p90 ${fmt(l.p90)} max ${fmt(l.max)}")
            }
            if (novigTrades > 0) {
                add("against Novig (moneyline moved 0.03+ from its median of the 30 s before; positive = the feed showed the score BEFORE Novig moved): $eventsOnNovig scores on games Novig traded, $moved moved it")
                for (l in leads) add("  ${l.src.padEnd(10)} scores ${l.scores} before ${l.before} by 3 s+ ${l.by3s} median ${fmt(l.median)} p10 ${fmt(l.p10)} p90 ${fmt(l.p90)}")
                if (stale.isNotEmpty()) {
                    add("stale fills a taker could have had at each feed's time (trades that really happened at the old price, net of the in-play taker fee):")
                    for (s in stale) add("  ${s.src.padEnd(10)} trades ${s.trades} payout \$${"%.2f".format(Locale.US, s.payout)} net gain \$${"%.2f".format(Locale.US, s.gain)}")
                }
            }
            odds?.let { add("Polymarket odds against Novig: ${it.scores} scores, Polymarket's mid moved first in ${it.before} (by 3 s+ in ${it.by3s}), median ${fmt(it.median)} s (Novig's move minus Polymarket's) p10 ${fmt(it.p10)} p90 ${fmt(it.p90)}") }
            if (roundTripMs.isNotEmpty()) add("request round trip (ms, median): " + roundTripMs.entries.joinToString("; ") { "${it.key} ${it.value.toLong()}" })
        }
    }

    /** At least this many scores before a number is called a finding. */
    const val MIN_SCORES = 8

    private fun fmt(v: Double) = "%+.1f".format(Locale.US, v).removePrefix("+")

    private fun q(xs: List<Double>, f: Double): Double = xs.sorted().let { it[minOf(it.size - 1, (f * it.size).toInt())] }

    /** Builds the report from what was recorded. [jump] is the move that counts as Novig's price reacting (probability). */
    fun report(scores: List<Sighting>, trades: List<NovigTick>, odds: List<OddsTick> = emptyList(), jump: Double = 0.03): Report {
        val games = cluster(scores)
        val feeds = scores.map { it.src }.distinct().sorted()
        val rows = games.flatMap { g -> transitions(g).map { (key, d) -> Triple(g, key, d) } }
        val multi = rows.filter { it.third.size >= 2 }
        val lagBy = HashMap<String, MutableList<Double>>()
        val firstBy = HashMap<String, Int>()
        for ((_, _, d) in multi) {
            val t0 = d.values.min()
            for ((s, t) in d) {
                lagBy.getOrPut(s) { ArrayList() } += (t - t0) / 1000.0
                if (t == t0) firstBy.merge(s, 1, Int::plus)
            }
        }
        val lags = lagBy.entries.sortedBy { median(it.value) }.map { (s, l) ->
            Lag(s, l.size, firstBy[s] ?: 0, median(l), q(l, .25), q(l, .75), q(l, .9), l.max())
        }
        val leadBy = HashMap<String, MutableList<Double>>()
        val staleBy = HashMap<String, DoubleArray>()
        val oddsLeads = ArrayList<Double>()
        var nEv = 0
        var nMv = 0
        for ((g, _, d) in rows) {
            val ser = novigSeries(trades, g.names)
            if (ser.isEmpty()) continue
            nEv++
            val tFirst = d.values.min() / 1000.0
            val mv = firstMove(ser, tFirst, jump) ?: continue
            nMv++
            for ((s, tMs) in d) {
                val t = tMs / 1000.0
                leadBy.getOrPut(s) { ArrayList() } += mv.atSec - t
                val st = staleFills(ser, mv.refs, t, mv.atSec, jump)
                val acc = staleBy.getOrPut(s) { DoubleArray(3) }
                acc[0] += st.trades.toDouble(); acc[1] += st.payout; acc[2] += st.gain
            }
            polyMoveSec(odds, g.names, tFirst, jump)?.let { oddsLeads += mv.atSec - it }
        }
        val leads = leadBy.entries.sortedByDescending { median(it.value) }.map { (s, l) ->
            Lead(s, l.size, l.count { it > 0 }, l.count { it >= 3 }, median(l), q(l, .1), q(l, .9))
        }
        val stale = staleBy.entries.sortedByDescending { it.value[2] }.map { (s, a) -> StaleRow(s, a[0].toInt(), a[1], a[2]) }
        val oddsLead = oddsLeads.takeIf { it.isNotEmpty() }?.let { OddsLead(it.size, it.count { x -> x > 0 }, it.count { x -> x >= 3 }, median(it), q(it, .1), q(it, .9)) }
        val rtt = scores.filter { it.rttMs > 0 }.groupBy { it.src }.mapValues { median(it.value.map { r -> r.rttMs.toDouble() }) }
        return Report(scores.size, feeds, trades.size, games.size, rows.size, multi.size, lags, nEv, nMv, leads, stale, oddsLead, rtt)
    }

    /** When Polymarket's mid for [names]' game first moved [jump] or more from its median of the 30 s before ([tRef] - 15 s), within 90 s; null when it did not or has no series. */
    fun polyMoveSec(odds: List<OddsTick>, names: Pair<String, String>, tRef: Double, jump: Double): Double? {
        val mine = odds.filter {
            val title = it.event.substringAfter(": ", it.event)
            val p = title.split(Regex(" vs\\.? | @ "))
            p.size >= 2 && sameGame(p[0] to p[1], names)
        }.groupBy { it.outcome }
        var best: Double? = null
        for ((_, pts) in mine) {
            val ts = pts.map { (it.serverMs ?: it.seenMs) / 1000.0 to it.mid }.sortedBy { it.first }
            val before = ts.filter { it.first >= tRef - 15 - 30 && it.first < tRef - 15 }.map { it.second }
            if (before.size < 2) continue
            val ref = median(before)
            for ((t, m) in ts) {
                if (t >= tRef - 15 && t - tRef <= 90 && Math.abs(m - ref) >= jump) {
                    if (best == null || t < best) best = t
                    break
                }
            }
        }
        return best
    }
}
