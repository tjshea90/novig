package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.americanToDecimal
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily

/** What turning the board into reference events needs. */
class BoltContext(
    val league: League,
    /** Vigilant book keys to price with; null = every book BoltOdds lists ([com.tjshea.vigilant.data.scanner.ApiGates.boltAllBooks]). */
    val wanted: Set<String>?,
    val families: Set<MarketFamily>,
    val includeLive: Boolean,
    val nowMs: Long,
    /** When [book] was last heard on [sport] AND the connection that heard it is healthy; null when that cannot be vouched for (the quote then ages by its own time). */
    val aliveAt: (book: String, sport: String) -> Long?,
    /** Only games BoltOdds says are in play (the live engine's); false = the usual pregame reading, which leaves a game flagged live out whatever [includeLive] says. */
    val liveOnly: Boolean = false,
)

/**
 * The BoltOdds board as reference events (BOLTODDS_API.md §3). A market is built from the structured fields only: a team's side is the outcome's `outcome_target` against the game's two team names, a
 * number is `outcome_line`, Over/Under is `outcome_over_under`, and the kind is [BoltMarkets]. A pair that does not close is dropped, not repaired: a moneyline needs both teams, a total both Over and Under on
 * the SAME number, a spread both teams on numbers that are each other's negative (so a feed that gave the home number on both sides, or the wrong sign, prices nothing instead of a wrong line), a prop both
 * Over and Under of one player on one number.
 *
 * Time: a quote is dated by the newest message that set it, but BoltOdds only sends CHANGES, so a steady line is hours old and still current while the connection is. The quote therefore counts as seen when its
 * book last spoke about the sport ([BoltContext.aliveAt]: only while the socket is healthy and that book is not silent), whichever is later. A dead or silent feed ages quotes out by the usual freshness rules.
 */
object BoltConvert {
    fun events(games: List<BoltBookGame>, ctx: BoltContext): List<RefEvent> {
        val byGame = LinkedHashMap<String, MutableList<BoltBookGame>>()
        for (g in games) {
            // Tennis is read only for the live engine (a tournament is its own BoltOdds sport, and only the match winner is used: Bolt's games and sets lines are not told apart).
            val league = BoltBooks.leagueOfSport(g.sport) ?: (if (ctx.liveOnly) BoltBooks.tennisLeagueOf(g.sport) else null)
            if (league != ctx.league.novigName) continue
            val start = g.startMs ?: continue
            if (!ctx.liveOnly && !ctx.includeLive && start <= ctx.nowMs) continue
            byGame.getOrPut(g.game) { ArrayList() } += g
        }
        val out = ArrayList<RefEvent>()
        for ((gameId, books) in byGame) {
            // TBP9: a game BoltOdds flags as in play is the live engine's, never the pregame fair's; and the live engine reads only those.
            val inPlay = books.any { it.live }
            if (ctx.liveOnly != inPlay) continue
            val canon = books.maxByOrNull { it.outcomes.size } ?: continue
            val start = books.firstNotNullOfOrNull { it.startMs } ?: continue
            val markets = ArrayList<RefBookMarket>()
            for (b in books) {
                val key = BoltBooks.appKey(b.sportsbook) ?: continue
                if (ctx.wanted != null && key !in ctx.wanted) continue
                val flip = sameTeam(b.home, canon.away) && sameTeam(b.away, canon.home) && !sameTeam(b.home, canon.home)
                val ms = marketsOf(b, key, ctx)
                markets += if (flip) ms.map { it.flipped() } else ms
            }
            if (markets.isNotEmpty()) out += RefEvent(eventId(ctx.league.novigName, gameId), ctx.league.oddsApiSportKey, start, canon.home, canon.away, markets)
        }
        return out
    }

    /** The reference event id of BoltOdds game [gameId] in [league] (the same wherever it is built). */
    fun eventId(league: String, gameId: String): String = "bolt-$league-${gameId.hashCode().toUInt().toString(16)}"

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** The same team named two ways: equal letters and digits, or one inside the other ("Mets" in "New York Mets"). */
    fun sameTeam(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (x.length >= 4 && y.length >= 4 && (x.contains(y) || y.contains(x)))
    }

    /**
     * A book's markets of one game. Converting an entry is the costly part of reading the board (a phone took 7-17 s for a whole refresh, Tj's v0.95.0 file), and an entry is replaced as a whole when its
     * book pushes, so the conversion is kept on the entry itself and redone only for an entry that changed. What depends on the clock ([BoltContext.aliveAt]) is applied on top, after.
     */
    private fun marketsOf(g: BoltBookGame, book: String, ctx: BoltContext): List<RefBookMarket> {
        val seenAt = ctx.aliveAt(g.sportsbook, g.sport)
        // The slot keeps the conversion with the quotes' OWN times (no lift): the lift depends on when the book last spoke about the sport, which changes with every push of that book and used to
        // throw every one of its entries away (v0.95.3 file: 7-15 s per refresh). The lift is applied after, as a cheap copy, only where it changes a time.
        val key = "${ctx.league.novigName}|${ctx.families.hashCode()}"
        val base = g.marketsCache?.let { (k, v) -> if (k == key) v else null } ?: convert(g, book, ctx, null).also { g.marketsCache = key to it }
        if (seenAt == null) return base
        return base.map { m -> val t = m.lastUpdateMs; if (t != null && t < seenAt) m.copy(lastUpdateMs = seenAt) else m }
    }

    private fun convert(g: BoltBookGame, book: String, ctx: BoltContext, seenAt: Long?): List<RefBookMarket> {
        val league = ctx.league.novigName
        val tennis = league == "ATP" || league == "WTA"
        val title = BoltBooks.title(book)
        val stamp: (List<BoltOutcome>) -> Long = { l -> l.minOf { x -> if (seenAt != null) maxOf(x.atMs, seenAt) else x.atMs } }

        // Group the book's outcomes: (kind, period, stat, subject) -> the outcomes of that market.
        val groups = LinkedHashMap<Pair<BoltMarketRef, String?>, MutableList<BoltOutcome>>()
        for (o in g.outcomes.values) {
            val ref = BoltMarkets.classify(league, o) ?: continue
            if (tennis && ref.kind != LineKind.MONEYLINE) continue
            val subject = when (ref.kind) {
                LineKind.PLAYER_PROP -> o.target?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                LineKind.TEAM_TOTAL -> (if (sameTeam(o.target, g.home)) RefBookMarket.HOME else if (sameTeam(o.target, g.away)) RefBookMarket.AWAY else null) ?: continue
                else -> null
            }
            groups.getOrPut(ref to subject) { ArrayList() } += o
        }
        val out = ArrayList<RefBookMarket>()
        for ((k, list) in groups) {
            val (ref, subject) = k
            val family = familyOf(ref)
            if (family !in ctx.families) continue
            when (ref.kind) {
                LineKind.MONEYLINE -> {
                    val h = list.firstOrNull { sameTeam(it.target, g.home) } ?: continue
                    val a = list.firstOrNull { sameTeam(it.target, g.away) } ?: continue
                    out += RefBookMarket(book, title, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, americanToDecimal(h.odds), null), RefQuote(Side.AWAY, americanToDecimal(a.odds), null)), stamp(listOf(h, a)), ref.period)
                }
                LineKind.SPREAD -> {
                    val homes = list.filter { sameTeam(it.target, g.home) && it.line != null }
                    val aways = list.filter { sameTeam(it.target, g.away) && it.line != null }
                    for (h in homes) for (a in aways.filter { Math.abs(it.line!! + h.line!!) < 1e-9 }) {
                        out += RefBookMarket(book, title, LineKind.SPREAD, listOf(RefQuote(Side.HOME, americanToDecimal(h.odds), h.line), RefQuote(Side.AWAY, americanToDecimal(a.odds), a.line)), stamp(listOf(h, a)), ref.period)
                    }
                }
                LineKind.TOTAL -> overUnder(list, book, title, LineKind.TOTAL, ref.period, null, null, stamp, out)
                LineKind.TEAM_TOTAL -> overUnder(list, book, title, LineKind.TEAM_TOTAL, 0, subject, null, stamp, out)
                LineKind.PLAYER_PROP -> overUnder(list.filter { it.target?.trim() == subject }, book, title, LineKind.PLAYER_PROP, 0, subject, ref.stat, stamp, out)
            }
        }
        return out
    }

    private fun overUnder(
        list: List<BoltOutcome>, book: String, title: String, kind: LineKind, period: Int, subject: String?, stat: String?,
        stamp: (List<BoltOutcome>) -> Long, out: MutableList<RefBookMarket>,
    ) {
        val overs = list.filter { it.isOver && it.line != null }
        val unders = list.filter { it.isUnder && it.line != null }
        for (o in overs) for (u in unders.filter { Math.abs(it.line!! - o.line!!) < 1e-9 }) {
            out += RefBookMarket(book, title, kind, listOf(RefQuote(Side.OVER, americanToDecimal(o.odds), o.line), RefQuote(Side.UNDER, americanToDecimal(u.odds), o.line)), stamp(listOf(o, u)), period, subject, stat)
        }
    }

    private fun familyOf(ref: BoltMarketRef): MarketFamily = when (ref.kind) {
        LineKind.MONEYLINE -> MarketFamily.MONEYLINE
        LineKind.SPREAD -> if (ref.period == 0) MarketFamily.SPREAD else MarketFamily.FIRST_HALF
        LineKind.TOTAL -> if (ref.period == 0) MarketFamily.TOTAL else MarketFamily.FIRST_HALF
        LineKind.TEAM_TOTAL -> MarketFamily.TEAM_TOTAL
        LineKind.PLAYER_PROP -> MarketFamily.PLAYER_PROPS
    }
}
