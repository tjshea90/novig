package com.tjshea.vigilant.data.reference

import kotlin.math.abs

/**
 * OddsPapi's fixtures as Vigilant's reference events (ODDSPAPI_API.md §5, §6): each book's two sides of a market are paired into one [RefBookMarket] (moneyline, spread, game total, team totals, 1st-half and
 * first-5/first-inning lines, player props) and every alternate line becomes a market of its own at its own number, capped at [MAX_LINES] a book and market (the rungs far from the main line are the
 * ones nothing is priced on, and the heap has been tight). A side that is not active, has no price, or has no opposite side is left out: a fair line needs both sides of a book's price. A book whose
 * connection dropped (`staleOdds`), that suspended the game, or whose participants are rotated against OddsPapi's (the price orientation is not documented, so its prices are not used) gives nothing.
 *
 * The age of a quote is what the scan's freshness rule judges. A MAIN line of a book that is connected is as old as the read that brought it (it is the line the book is offering now, and `changedAt`
 * is only when it last MOVED: a steady Pinnacle total is hours old and current); any other line is as old as its older side's `changedAt`, because an alternate the book stopped offering would look
 * current otherwise.
 */
object OpConvert {
    const val PREFIX = "op:"
    const val MAX_LINES = 13

    /**
     * [wanted]: Vigilant book keys to keep. [appKeyOf]: OddsPapi slug -> Vigilant key (null drops it; the key's catalogue may name a book the table does not). [names]: player id -> "Last, First".
     * [games] / [props]: which kinds to produce, so the games source and the props source take their own share of one read. [nowMs]: when the read was made.
     */
    fun toRef(
        f: OpFixture, markets: OpMarkets, sportKey: String, wanted: Set<String>, nowMs: Long, names: Map<Long, String>,
        games: Boolean = true, props: Boolean = true, appKeyOf: (String) -> String? = { OpBooks.appKey(it) },
    ): RefEvent? {
        val start = f.startMs ?: return null
        if (f.p1.isBlank() || f.p2.isBlank() || f.cancelled) return null
        val groups = LinkedHashMap<Triple<String, Long, Long>, MutableList<OpPrice>>()
        for (p in f.prices) {
            val market = p.marketId ?: markets.marketOf(p.outcomeId) ?: continue
            groups.getOrPut(Triple(p.book, market, p.playerId)) { ArrayList() } += p
        }
        val out = ArrayList<RefBookMarket>()
        val mainOf = HashMap<String, Boolean>()
        for ((key, prices) in groups) {
            val (slug, marketId, playerId) = key
            val cls = markets.classify(marketId) ?: continue
            val isProp = cls.kind == LineKind.PLAYER_PROP
            if (isProp && !props || !isProp && !games) continue
            val book = appKeyOf(slug) ?: continue
            if (book !in wanted) continue
            val meta = f.books[slug]
            if (meta != null && (meta.stale || meta.suspended || meta.rotated || !meta.hasOdds)) continue
            val a = prices.firstOrNull { it.outcomeId == cls.first && it.active && it.marketActive && it.decimal != null } ?: continue
            val b = prices.firstOrNull { it.outcomeId == cls.second && it.active && it.marketActive && it.decimal != null } ?: continue
            val da = a.decimal ?: continue
            val db = b.decimal ?: continue
            val connectedMain = meta != null && !meta.stale && a.mainLine && b.mainLine
            val changed = listOfNotNull(a.bookChangedMs ?: a.changedMs, b.bookChangedMs ?: b.changedMs).let { if (it.size == 2) it.min() else null }
            val updated = if (connectedMain) nowMs else changed
            val point = cls.point
            val title = OpBooks.title(book)
            val m = when (cls.kind) {
                LineKind.MONEYLINE -> RefBookMarket(book, title, cls.kind, listOf(RefQuote(Side.HOME, da, null), RefQuote(Side.AWAY, db, null)), updated, period = cls.period)
                LineKind.SPREAD -> {
                    val pt = point ?: continue
                    RefBookMarket(book, title, cls.kind, listOf(RefQuote(Side.HOME, da, pt), RefQuote(Side.AWAY, db, -pt)), updated, period = cls.period)
                }
                LineKind.TOTAL -> RefBookMarket(book, title, cls.kind, listOf(RefQuote(Side.OVER, da, point ?: continue), RefQuote(Side.UNDER, db, point)), updated, period = cls.period)
                LineKind.TEAM_TOTAL -> RefBookMarket(
                    book, title, cls.kind, listOf(RefQuote(Side.OVER, da, point ?: continue), RefQuote(Side.UNDER, db, point)), updated, period = 0,
                    subject = if (cls.team == 1) RefBookMarket.HOME else RefBookMarket.AWAY,
                )
                LineKind.PLAYER_PROP -> {
                    val name = names[playerId]?.let { OpProps.displayName(it) } ?: continue
                    RefBookMarket(book, title, cls.kind, listOf(RefQuote(Side.OVER, da, point ?: continue), RefQuote(Side.UNDER, db, point)), updated, period = 0, subject = name, stat = cls.stat)
                }
            }
            out += if (cls.fallback) m.copy(bookEventId = FALLBACK) else m
            if (a.mainLine && b.mainLine) mainOf["$book|${m.coverage}|${m.subject}|${m.line}"] = true
        }
        // An American-football 2-way winner on `fulltime` is used only when the book has no `result` one.
        val hasResultMl = out.filter { it.kind == LineKind.MONEYLINE && it.bookEventId != FALLBACK }.map { it.bookKey }.toSet()
        val kept = out.filter { !(it.bookEventId == FALLBACK && it.bookKey in hasResultMl) }.map { if (it.bookEventId == FALLBACK) it.copy(bookEventId = null) else it }
        return RefEvent(PREFIX + f.id, sportKey, start, f.p1, f.p2, cap(kept, mainOf))
    }

    private const val FALLBACK = "op-fallback"

    /** At most [MAX_LINES] lines of one book and market: the main line (the one the book flags, else the first) and the lines closest to it. */
    private fun cap(markets: List<RefBookMarket>, mains: Map<String, Boolean>): List<RefBookMarket> {
        val result = ArrayList<RefBookMarket>(markets.size)
        val groups = markets.groupBy { listOf(it.bookKey, it.kind, it.period, it.subject, it.stat) }
        for ((_, group) in groups) {
            if (group.size <= MAX_LINES) { result += group; continue }
            val main = group.firstOrNull { mains["${it.bookKey}|${it.coverage}|${it.subject}|${it.line}"] == true } ?: group.first()
            val at = main.line ?: 0.0
            result += main
            result += group.filter { it !== main }.sortedBy { abs((it.line ?: 0.0) - at) }.take(MAX_LINES - 1)
        }
        return result
    }
}
