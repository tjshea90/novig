package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.reference.SxBetConvert
import com.tjshea.vigilant.data.scanner.League

/**
 * The live fair from SX Bet's exchange book (Tj, 2026-10-18, TCL118: "build the sx live feed"). Pure.
 *
 * SX is a peer-to-peer exchange, so its top of book is a SHARP price (makers are bettors and traders, not a margin-setting soft book): no consensus is needed, one market's two asks are enough. What keeps it honest in play, all
 * applied to every line every time:
 *  - the book must be a TAKER pair ([SxBetConvert.pair]: both sides at least $50 deep, asks summing to 1.00-1.06, prices between 3% and 97%): a thin or crossed top is no price;
 *  - the fair is the MID of the exchange spread, `(1 + ask1 - ask2) / 2` for outcome one (the two asks overlap by the spread; the mid is the unbiased price and needs no vig removal);
 *  - a price set BEFORE the last score change is not trusted: the book must have been pushed at or after [SxScore.changedAtMs] (the exchange re-quotes within a second of a goal; a stale top after a score is the classic live trap),
 *    and a game whose fixture is not in progress (finished, interrupted, postponed) has no line;
 *  - the book must be younger than [Rules.maxAgeMs] unless the socket proved the channel quiet and alive ([quietAliveMs]): a market that has not changed pushes nothing, so the price stands while the connection is heard;
 *  - one line per type: the main line ([SxBetConvert.Market.mainLine]) when SX flags one, else the pair closest to even.
 * Home and away: SX's team order is NOT a rule (research/sxbet/MAPPING_2026-10-18.md), so the game is built with teamOne as "away" and teamTwo as "home" and the live matcher tries both orders.
 */
object SxLiveBuild {
    data class Rules(
        /** A book older than this must be backed by a live connection ([quietAliveMs]) to count. */
        val maxAgeMs: Long = 20_000L,
        /** How recently ANY message was heard for a quiet (unchanged) book to stand. */
        val quietAliveMs: Long = 45_000L,
        /** A moneyline or spread side priced above this probability is not worth a live bet (a nearly settled game). */
        val maxFair: Double = 0.95,
        val minFair: Double = 0.05,
    )

    /** What the feed knows of one game from the market list: its markets and where it is. */
    data class EventInfo(val eventId: String, val league: League, val teamOne: String, val teamTwo: String, val startMs: Long, val markets: List<SxBetConvert.Market>)

    /** One live line: [fair] by side (sums to 1), [points] the HOME handicap of a spread or the number of a total. */
    data class Line(val type: PinnLineType, val points: Double?, val fair: Map<PinnSide, Double>, val marketHash: String, val bookAtMs: Long) {
        /** The same ids the Pinnodds-frame builders and the matcher use. */
        val key: String get() = when (type) {
            PinnLineType.MONEYLINE -> "s;0;m"
            PinnLineType.SPREAD -> "s;0;s;$points"
            PinnLineType.TOTAL -> "s;0;ou;$points"
        }
    }

    data class Game(val id: String, val league: String, val sportId: Int, val home: String, val away: String, val startMs: Long, val lines: List<Line>) {
        val tennis: Boolean get() = league == "ATP" || league == "WTA"
    }

    private val ML = setOf(226, 52)
    private val SPREAD_TEAM = setOf(342)
    private val SPREAD_SOLO = setOf(201)
    private val TOTAL_TEAM = setOf(28)
    private val TOTAL_SOLO = setOf(166, 29)

    /** True while the game is in play by SX's own word: the fixture status says 2, or (no status heard) a score frame is live and unfinished. */
    fun live(eventId: String, status: Int?, score: SxScore?): Boolean = when {
        status != null -> status == 2
        else -> score != null && !score.finished
    }

    /** The mid of the exchange spread for outcome one and two (sums to 1), or null when the book is no price ([SxBetConvert.pair]). */
    fun mid(b: SxBetConvert.Book): Pair<Double, Double>? {
        val (a1, a2) = SxBetConvert.pair(b) ?: return null
        val p1 = (1.0 + a1 - a2) / 2.0
        return p1 to (1.0 - p1)
    }

    /**
     * One game's live lines from [info]'s markets, their [books], the game's [score] and fixture [status] at [now]. [aliveAt] is when the socket last heard anything. Empty when the game is not in play or has no
     * trusted book.
     */
    fun game(info: EventInfo, books: (String) -> SxBetConvert.Book?, score: SxScore?, status: Int?, aliveAt: Long, now: Long, rules: Rules = Rules()): Game? {
        if (!live(info.eventId, status, score)) return null
        val solo = info.league.tennis || info.league.novigName in setOf("UFC", "Boxing")
        val ml = ML; val spr = if (solo) SPREAD_SOLO else SPREAD_TEAM; val tot = if (solo) TOTAL_SOLO else TOTAL_TEAM
        val scoreChanged = score?.changedAtMs ?: 0L
        fun trusted(b: SxBetConvert.Book): Boolean {
            if (b.atMs <= 0L) return false
            if (b.atMs < scoreChanged) return false
            return now - b.atMs <= rules.maxAgeMs || now - aliveAt <= rules.quietAliveMs
        }
        val away = info.teamOne
        val home = info.teamTwo
        fun side(name: String): PinnSide? {
            val base = name.trim().replace(Regex("\\s*[+-]\\d+(\\.\\d+)?$"), "").trim()
            return when {
                base.equals(away, true) -> PinnSide.AWAY
                base.equals(home, true) -> PinnSide.HOME
                else -> null
            }
        }
        val lines = ArrayList<Line>()
        // moneyline: one per game
        val mlCandidates = info.markets.filter { it.type in ml && (it.status == null || it.status == "ACTIVE") }.mapNotNull { m ->
            val b = books(m.marketHash)?.takeIf(::trusted) ?: return@mapNotNull null
            val (p1, p2) = mid(b) ?: return@mapNotNull null
            val s1 = side(m.outcomeOneName) ?: return@mapNotNull null
            val s2 = side(m.outcomeTwoName) ?: return@mapNotNull null
            if (s1 == s2 || p1 !in rules.minFair..rules.maxFair) return@mapNotNull null
            Line(PinnLineType.MONEYLINE, null, mapOf(s1 to p1, s2 to p2), m.marketHash, b.atMs)
        }
        // the type 226 (including overtime) is the full-game line; 52 only when no 226 priced
        (mlCandidates.firstOrNull { c -> info.markets.first { it.marketHash == c.marketHash }.type == 226 } ?: mlCandidates.firstOrNull())?.let { lines += it }
        // spreads and totals: the main line, else the pair closest to even
        fun pick(types: Set<Int>, build: (SxBetConvert.Market, SxBetConvert.Book, Double) -> Line?): Line? {
            val cands = info.markets.filter { it.type in types && (it.status == null || it.status == "ACTIVE") }.mapNotNull { m ->
                val b = books(m.marketHash)?.takeIf(::trusted) ?: return@mapNotNull null
                val (p1, _) = mid(b) ?: return@mapNotNull null
                val line = build(m, b, p1) ?: return@mapNotNull null
                Triple(m, line, kotlin.math.abs(p1 - 0.5))
            }
            if (cands.isEmpty()) return null
            return (cands.filter { it.first.mainLine == true }.minByOrNull { it.third } ?: cands.minByOrNull { it.third })?.second
        }
        pick(spr) { m, b, _ ->
            val l = m.line ?: return@pick null
            if (kotlin.math.abs(kotlin.math.abs(l % 1.0) - 0.5) > 1e-9) return@pick null
            val (p1, p2) = mid(b) ?: return@pick null
            val s1 = side(m.outcomeOneName) ?: return@pick null
            val s2 = side(m.outcomeTwoName) ?: return@pick null
            if (s1 == s2 || p1 !in rules.minFair..rules.maxFair) return@pick null
            // `line` is outcome one's handicap; the HOME handicap is that of whichever outcome is the home team
            val homeHandicap = if (s1 == PinnSide.HOME) l else -l
            Line(PinnLineType.SPREAD, homeHandicap, mapOf(s1 to p1, s2 to p2), m.marketHash, b.atMs)
        }?.let { lines += it }
        pick(tot) { m, b, _ ->
            val l = m.line ?: return@pick null
            if (kotlin.math.abs(kotlin.math.abs(l % 1.0) - 0.5) > 1e-9) return@pick null
            val (p1, p2) = mid(b) ?: return@pick null
            val over1 = m.outcomeOneName.startsWith("Over", true)
            val over2 = m.outcomeTwoName.startsWith("Over", true)
            if (over1 == over2) return@pick null
            Line(PinnLineType.TOTAL, l, mapOf(PinnSide.OVER to (if (over1) p1 else p2), PinnSide.UNDER to (if (over1) p2 else p1)), m.marketHash, b.atMs)
        }?.let { lines += it }
        if (lines.isEmpty()) return null
        return Game("sx:${info.eventId}", info.league.novigName, info.league.pinnacleSportId ?: 0, home, away, info.startMs, lines)
    }
}
