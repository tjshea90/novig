package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily

/** What turning a board into reference events needs (all from discovery calls, UNABATED_API.md §3). */
class UnabatedContext(
    val league: League,
    /** Unabated source id -> Vigilant book key, for the books to price with. */
    val books: Map<Long, String>,
    /** Source ids Unabated lists as high-frequency (`statusId` 1): their unchanged main lines are current. */
    val highFrequency: Set<Long>,
    val betTypes: Map<Int, UnBetType>,
    val periods: Map<Int, UnPeriod>,
    val teams: Map<Long, String>,
    val people: Map<Long, String>,
    val families: Set<MarketFamily>,
    val includeLive: Boolean,
    /** When the board was read: every quote is dated from this, never from "now" (a cached board is only as fresh as its read). */
    val readAtMs: Long,
    val boardUpdatedMs: Long?,
)

/**
 * An Unabated board as reference events (UNABATED_API.md §3). The docs give a bet type's NAME only through `/bettype` and say not to hard-code ids, so a group is classified by its STRUCTURE (two sides with
 * team ids and no points = moneyline; team ids with points = spread; no team id with points = total; both sides the same team = team total; a side with a `personId` = player prop, stat from the bet type's
 * name through the OddsPapi stat table). Anything else (three-way markets, futures, odd/even…) is left alone. Quotes: American -> decimal; spreads carry the HOME handicap on HOME and its negative on AWAY.
 */
object UnabatedConvert {
    /** Free data is delayed at least this long: a quote read at T was true at T minus this. */
    const val DELAY_MS = 15_000L

    fun events(board: UnBoard, ctx: UnabatedContext): List<RefEvent> {
        val byEvent = LinkedHashMap<Long, MutableList<UnGroup>>()
        board.groups.forEach { g -> if (g.statusId == 1 || (ctx.includeLive && g.statusId == 2)) byEvent.getOrPut(g.eventId) { ArrayList() } += g }
        val out = ArrayList<RefEvent>()
        for ((eventId, groups) in byEvent) {
            val first = groups.first()
            val start = first.startMs ?: continue
            val (away, home) = names(first, ctx) ?: continue
            val markets = groups.flatMap { marketsOf(it, ctx, home, away) }
            if (markets.isNotEmpty()) out += RefEvent("un-$eventId", ctx.league.oddsApiSportKey, start, home, away, markets)
        }
        return out
    }

    private fun names(g: UnGroup, ctx: UnabatedContext): Pair<String, String>? {
        val away = g.awayTeamId?.let { ctx.teams[it] }
        val home = g.homeTeamId?.let { ctx.teams[it] }
        if (away != null && home != null) return away to home
        // "Thunder Oklahoma City OKC @ Pacers Indiana IND": the docs' own example; the trailing 2-4 capital letters are the abbreviation.
        val parts = g.eventName.split(" @ ")
        if (parts.size != 2) return null
        fun clean(s: String) = s.trim().replace(Regex("\\s+[A-Z]{2,4}$"), "")
        return (away ?: clean(parts[0])) to (home ?: clean(parts[1]))
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val CORE_GAME_NAMES = setOf(
        "moneyline", "spread", "pointspread", "runline", "puckline", "total", "totalpoints", "totalruns", "totalgoals", "overunder", "teamtotal", "teamtotalpoints", "teamtotalruns", "teamtotalgoals",
    )

    /**
     * The bet types to ask a straight board for. An unfiltered NFL straight board is 14.7 MB (1,631 bet types exist; real answer, 2026-10-11), so only the game lines Vigilant prices are requested; empty when no
     * bet type's name is one of them (the caller then asks for everything rather than nothing).
     */
    fun gameBetTypeIds(types: Collection<UnBetType>): List<Int> = types.filter { norm(it.name) in CORE_GAME_NAMES }.map { it.id }.sorted()

    /** The player-prop bet types of [league] Vigilant has a stat for ("Passing Yards", "Player Field Goals Made"). */
    fun propBetTypeIds(types: Collection<UnBetType>, league: League): List<Int> = types.filter { t ->
        val k = norm(t.name)
        OpProps.stat(league.novigName, k.removePrefix("player")) != null || OpProps.stat(league.novigName, k) != null
    }.map { it.id }.sorted()

    /** Events read in several requests, joined by event id (their markets pooled). */
    fun merge(parts: List<List<RefEvent>>): List<RefEvent> {
        val by = LinkedHashMap<String, RefEvent>()
        for (e in parts.flatten()) by[e.id] = by[e.id]?.let { it.copy(markets = it.markets + e.markets) } ?: e
        return by.values.toList()
    }

    /** Vigilant's period for an Unabated period type, or null for one Novig has no market for. */
    fun periodOf(g: UnGroup, ctx: UnabatedContext): Int? {
        val name = norm(g.periodTypeId?.let { ctx.periods[it]?.name } ?: g.periodKey)
        return when {
            name in setOf("game", "fullgame", "gameincludingot", "fullgameincludingot", "match") -> 0
            name.contains("1sthalf") || name.contains("firsthalf") || name == "1h" || name == "h1" -> 1
            name.contains("first5") || name.contains("1st5") || name == "f5" -> 1
            name.contains("1stinning") || name.contains("firstinning") -> RefBookMarket.PERIOD_FIRST_INNING
            else -> null
        }
    }

    private fun marketsOf(g: UnGroup, ctx: UnabatedContext, home: String, away: String): List<RefBookMarket> {
        val period = periodOf(g, ctx) ?: return emptyList()
        val sides = g.sides.sortedBy { it.sideIndex }
        val prop = sides.any { it.personId != null }
        return if (prop) {
            if (MarketFamily.PLAYER_PROPS !in ctx.families || period != 0) emptyList() else props(g, sides, ctx)
        } else {
            games(g, sides, ctx, period)
        }
    }

    private fun games(g: UnGroup, sides: List<UnSide>, ctx: UnabatedContext, period: Int): List<RefBookMarket> {
        val s0 = sides.firstOrNull { it.sideIndex == 0 } ?: return emptyList()
        val s1 = sides.firstOrNull { it.sideIndex == 1 } ?: return emptyList()
        val anyPoints = (s0.lines + s1.lines).any { it.points != null }
        val sameTeam = s0.teamId != null && s0.teamId == s1.teamId
        val out = ArrayList<RefBookMarket>()
        for ((sourceId, book) in ctx.books) {
            val a = s0.lines.filter { it.sourceId == sourceId }
            val b = s1.lines.filter { it.sourceId == sourceId }
            if (a.isEmpty() || b.isEmpty()) continue
            fun st(x: UnLine, y: UnLine) = stamp(minOfNotNull(x, y), ctx, sourceId)
            when {
                // Team total: over (0) and under (1) of ONE team.
                sameTeam && anyPoints -> {
                    if (MarketFamily.TEAM_TOTAL !in ctx.families || period != 0) continue
                    val subject = if (s0.teamId == g.homeTeamId) RefBookMarket.HOME else if (s0.teamId == g.awayTeamId) RefBookMarket.AWAY else continue
                    for (x in a) for (y in b.filter { it.points == x.points && x.points != null }) {
                        out += RefBookMarket(book, UnabatedBooks.title(book), LineKind.TEAM_TOTAL, listOf(RefQuote(Side.OVER, americanToDecimal(x.price), x.points), RefQuote(Side.UNDER, americanToDecimal(y.price), x.points)), st(x, y), period, subject)
                    }
                }
                // Total: no team id, a number on both sides.
                s0.teamId == null && s1.teamId == null && anyPoints -> {
                    val fam = if (period == 0) MarketFamily.TOTAL else MarketFamily.FIRST_HALF
                    if (fam !in ctx.families) continue
                    for (x in a) for (y in b.filter { it.points == x.points && x.points != null }) {
                        out += RefBookMarket(book, UnabatedBooks.title(book), LineKind.TOTAL, listOf(RefQuote(Side.OVER, americanToDecimal(x.price), x.points), RefQuote(Side.UNDER, americanToDecimal(y.price), x.points)), st(x, y), period)
                    }
                }
                // Spread: team ids and a handicap on each side; side 0 is the away team, side 1 the home team.
                s0.teamId != null && s1.teamId != null && anyPoints -> {
                    if (period == RefBookMarket.PERIOD_FIRST_INNING) continue
                    val fam = if (period == 0) MarketFamily.SPREAD else MarketFamily.FIRST_HALF
                    if (fam !in ctx.families) continue
                    for (y in b) for (x in a.filter { it.points != null && y.points != null && Math.abs(it.points!! + y.points!!) < 1e-9 }) {
                        out += RefBookMarket(book, UnabatedBooks.title(book), LineKind.SPREAD, listOf(RefQuote(Side.HOME, americanToDecimal(y.price), y.points), RefQuote(Side.AWAY, americanToDecimal(x.price), x.points)), st(x, y), period)
                    }
                }
                // Moneyline: team ids, no points. First-half moneylines are not priced (3-way at the books, 2-way at Novig: ScanSettings.MarketFamily.FIRST_HALF).
                s0.teamId != null && s1.teamId != null && !anyPoints -> {
                    if (MarketFamily.MONEYLINE !in ctx.families || period != 0) continue
                    val x = a.firstOrNull { !it.alternate } ?: continue
                    val y = b.firstOrNull { !it.alternate } ?: continue
                    out += RefBookMarket(book, UnabatedBooks.title(book), LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, americanToDecimal(x.price), null), RefQuote(Side.HOME, americanToDecimal(y.price), null)), st(x, y), period)
                }
            }
        }
        return out
    }

    private fun props(g: UnGroup, sides: List<UnSide>, ctx: UnabatedContext): List<RefBookMarket> {
        val bt = ctx.betTypes[g.betTypeId]
        val key = norm(bt?.name.orEmpty())
        val stat = OpProps.stat(ctx.league.novigName, key.removePrefix("player")) ?: OpProps.stat(ctx.league.novigName, key) ?: return emptyList()
        val out = ArrayList<RefBookMarket>()
        val byPerson = sides.filter { it.personId != null }.groupBy { it.personId!! }
        for ((person, ps) in byPerson) {
            val name = ctx.people[person] ?: continue
            val over = ps.firstOrNull { it.sideIndex == 0 } ?: continue
            val under = ps.firstOrNull { it.sideIndex == 1 } ?: continue
            for ((sourceId, book) in ctx.books) {
                val a = over.lines.filter { it.sourceId == sourceId }
                val b = under.lines.filter { it.sourceId == sourceId }
                for (x in a) for (y in b.filter { it.points == x.points }) {
                    // A Yes/No prop with no number (anytime touchdown) is Over/Under 0.5, as Novig lists it.
                    val point = x.points ?: 0.5
                    out += RefBookMarket(book, UnabatedBooks.title(book), LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, americanToDecimal(x.price), point), RefQuote(Side.UNDER, americanToDecimal(y.price), point)), stamp(minOfNotNull(x, y), ctx, sourceId), 0, name, stat)
                }
            }
        }
        return out
    }

    private fun minOfNotNull(x: UnLine, y: UnLine): List<UnLine> = listOf(x, y)

    /**
     * When the quote was last true. A price's `modifiedOn` is when it MOVED, so a steady line is hours old and still current; the docs give `freshnessExpiresAt` for that: a line not yet expired at the read is
     * current as of the read (minus the 15-second delay). Without it, an unchanged MAIN line of a source Unabated lists as high-frequency is current as of the read when the board itself is recent; anything else
     * keeps its own `modifiedOn` and ages out under Vigilant's freshness rules. UNVERIFIED against a live sample (UNABATED_API.md §4).
     */
    private fun stamp(lines: List<UnLine>, ctx: UnabatedContext, sourceId: Long): Long? {
        val asOfRead = ctx.readAtMs - DELAY_MS
        val per = lines.map { l ->
            val expiry = l.expiresMs
            when {
                expiry != null && expiry > ctx.readAtMs -> asOfRead
                expiry == null && !l.alternate && sourceId in ctx.highFrequency && (ctx.boardUpdatedMs ?: ctx.readAtMs) >= ctx.readAtMs - 5 * 60_000L -> asOfRead
                else -> l.modifiedMs ?: ctx.boardUpdatedMs?.takeIf { it > 0 } ?: asOfRead
            }
        }
        return per.minOrNull()
    }
}
