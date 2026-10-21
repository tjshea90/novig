package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.boltodds.BoltMarkets
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily

/** SharpAPI's book ids, leagues and market names in Vigilant's words (SHARPAPI.md §2). */
object SharpApiBooks {
    /** SharpAPI id -> Vigilant's book key where they differ (The Odds API's keys, which Settings and the company table use). */
    private val TO_APP = mapOf(
        "caesars" to "williamhill_us", "thescorebet" to "espnbet", "betonline" to "betonlineag", "hardrock" to "hardrockbet",
        "betfair" to "betfair_ex_eu", "onexbet" to "1xbet", "sport888_it" to "888sport", "888sport_it" to "888sport",
    )
    private val TITLES = mapOf(
        "circa" to "Circa Sports", "bookmaker" to "Bookmaker", "sbobet" to "SBOBET", "1xbet" to "1xBet", "prophetx" to "ProphetX", "bet365" to "bet365", "betparx" to "betPARX",
        "smarkets" to "Smarkets", "stake" to "Stake", "rebet" to "Rebet", "gemini" to "Gemini", "betsson" to "Betsson", "bwin" to "bwin", "ladbrokes" to "Ladbrokes", "paddypower" to "Paddy Power",
        "unibet" to "Unibet", "betway" to "Betway", "skybet" to "Sky Bet", "betvictor" to "BetVictor", "betfred" to "Betfred", "saba" to "SABA", "888sport" to "888sport",
    )

    /**
     * Never a fair line from here: Novig is the venue being priced; Kalshi, Polymarket and SX Bet are read by Vigilant itself (fresher, from their own feeds); the DFS apps and Robinhood (a re-fronted
     * Kalshi) are not prices of their own; a lay feed is the other side of a back.
     */
    val EXCLUDED = setOf(
        "novig", "kalshi", "polymarket", "polymarket_us", "sx_bet", "prizepicks", "prizepicks_flex", "underdog", "underdog_multipliers", "underdog_predictions", "robinhood",
        "fanatics_markets", "betfair_lay", "sportzino", "thrillzz", "goldrush",
    )

    fun appKey(id: String): String? = if (id in EXCLUDED) null else TO_APP[id] ?: id

    fun title(appKey: String): String = TITLES[appKey] ?: TheOddsApiClient.bookTitle(appKey)

    /** SharpAPI's league slug for a Vigilant league (null = SharpAPI is not asked for it). */
    fun leagueSlug(league: League): String? = LEAGUES[league.novigName]

    private val LEAGUES = mapOf(
        "NFL" to "nfl", "NCAAF" to "ncaaf", "NBA" to "nba", "NCAAB" to "ncaab", "WNBA" to "wnba", "MLB" to "mlb", "NHL" to "nhl", "ATP" to "atp", "WTA" to "wta", "UFC" to "ufc",
    )
}

/**
 * SharpAPI's rows of one league as reference events. A market is built from the structured fields only (`selection_type`, `team_side`, `line`, `player_name`, `market_type` / `market_segment`), and a pair that
 * does not close is dropped, not repaired: a moneyline needs both teams, a spread both teams on numbers that are each other's negative, a total or prop both Over and Under on the same number. A row SharpAPI flags
 * suspended (`is_active: false`) or a pre-game price left standing after kick-off (`is_stale_pregame_price`) prices nothing. A quote is as old as the older of its two rows' delivery stamps.
 */
object SharpApiConvert {
    class Context(
        val league: League,
        /** Vigilant book keys to price with; null = every book SharpAPI sends. */
        val wanted: Set<String>?,
        val families: Set<MarketFamily>,
        val includeLive: Boolean,
        val nowMs: Long,
        /** Only rows of games in play (the live feed); false = the usual pregame reading. */
        val liveOnly: Boolean = false,
    )

    /** What one row is about, once its name is read. */
    data class Ref(val kind: LineKind, val period: Int, val stat: String? = null)

    fun events(rows: List<SaRow>, ctx: Context): List<RefEvent> {
        val byEvent = LinkedHashMap<String, MutableList<SaRow>>()
        for (r in rows) {
            if (!r.active || r.stalePregame) continue
            if (ctx.liveOnly != r.live) continue
            val start = r.startMs ?: continue
            if (!ctx.liveOnly && !ctx.includeLive && start <= ctx.nowMs) continue
            byEvent.getOrPut(r.eventId) { ArrayList() } += r
        }
        val out = ArrayList<RefEvent>(byEvent.size)
        for ((eventId, list) in byEvent) {
            val first = list.first()
            val markets = ArrayList<RefBookMarket>()
            for ((book, rowsOfBook) in list.groupBy { it.book }) {
                val key = SharpApiBooks.appKey(book) ?: continue
                if (ctx.wanted != null && key !in ctx.wanted) continue
                markets += marketsOf(rowsOfBook, key, ctx)
            }
            if (markets.isNotEmpty()) out += RefEvent("sharpapi-$eventId", ctx.league.oddsApiSportKey, first.startMs ?: continue, first.home, first.away, markets)
        }
        return out
    }

    /** The kind and period of a SharpAPI market, or null for one Vigilant does not price (3-way moneylines, period moneylines, game props, futures...). */
    fun refOf(league: String, marketType: String, segment: String?): Ref? {
        val tennis = league == "ATP" || league == "WTA"
        val seg = segment ?: when {
            marketType.startsWith("1st_half_") -> "1st_half"
            marketType.startsWith("1st_5_innings_") -> "1st_5_innings"
            marketType.startsWith("1st_inning_") -> "1st_inning"
            else -> "full_game"
        }
        val base = marketType.removePrefix("1st_half_").removePrefix("1st_5_innings_").removePrefix("1st_inning_")
        val period = when (seg) {
            "full_game", "" -> 0
            "1st_half", "1st_5_innings" -> 1
            "1st_inning" -> RefBookMarket.PERIOD_FIRST_INNING
            else -> return null
        }
        if (tennis) return when (base) {
            "moneyline" -> if (period == 0) Ref(LineKind.MONEYLINE, 0) else null
            "set_handicap" -> if (period == 0) Ref(LineKind.SPREAD, RefBookMarket.PERIOD_SETS) else null
            "total_sets" -> if (period == 0) Ref(LineKind.TOTAL, RefBookMarket.PERIOD_SETS) else null
            "game_handicap", "point_spread" -> if (period == 0) Ref(LineKind.SPREAD, 0) else null
            "total_games" -> if (period == 0) Ref(LineKind.TOTAL, 0) else null
            else -> null
        }
        return when {
            base == "moneyline" -> if (period == 0) Ref(LineKind.MONEYLINE, 0) else null
            base == "point_spread" || base == "run_line" || base == "puck_line" -> if (period == RefBookMarket.PERIOD_FIRST_INNING) null else Ref(LineKind.SPREAD, period)
            base == "total_points" || base == "total_runs" || base == "total_goals" -> Ref(LineKind.TOTAL, period)
            base == "team_total" -> if (period == 0) Ref(LineKind.TEAM_TOTAL, 0) else null
            base.startsWith("player_") && period == 0 -> BoltMarkets.stat(league, base.filter { it.isLetterOrDigit() })?.let { Ref(LineKind.PLAYER_PROP, 0, it) }
            else -> null
        }
    }

    private fun side(r: SaRow): Side? = when {
        r.selectionType == "home" || r.teamSide == "home" && r.selectionType !in OVER_UNDER -> Side.HOME
        r.selectionType == "away" || r.teamSide == "away" && r.selectionType !in OVER_UNDER -> Side.AWAY
        r.selectionType == "over" || r.selectionType.endsWith("_over") -> Side.OVER
        r.selectionType == "under" || r.selectionType.endsWith("_under") -> Side.UNDER
        else -> null
    }

    /** A team total's team: `home_over`/`away_under` or the adapter's `team_side`. */
    private fun teamOf(r: SaRow): String? = when {
        r.selectionType.startsWith("home_") || r.teamSide == "home" -> RefBookMarket.HOME
        r.selectionType.startsWith("away_") || r.teamSide == "away" -> RefBookMarket.AWAY
        else -> null
    }

    private val OVER_UNDER = setOf("over", "under")

    private fun marketsOf(rows: List<SaRow>, book: String, ctx: Context): List<RefBookMarket> {
        val league = ctx.league.novigName
        val title = SharpApiBooks.title(book)
        val groups = LinkedHashMap<Triple<Ref, String?, Double?>, MutableList<Pair<SaRow, Side>>>()
        for (r in rows) {
            val ref = refOf(league, r.marketType, r.segment) ?: continue
            if (familyOf(ref) !in ctx.families) continue
            val s = side(r) ?: continue
            val subject = when (ref.kind) {
                LineKind.PLAYER_PROP -> r.player?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                LineKind.TEAM_TOTAL -> teamOf(r) ?: continue
                else -> null
            }
            // A spread's two sides carry opposite numbers: grouped by the home side's number.
            val line = when (ref.kind) {
                LineKind.MONEYLINE -> null
                LineKind.SPREAD -> r.line?.let { if (s == Side.AWAY) -it else it } ?: continue
                else -> r.line ?: continue
            }
            groups.getOrPut(Triple(ref, subject, line)) { ArrayList() } += r to s
        }
        val out = ArrayList<RefBookMarket>()
        for ((k, list) in groups) {
            val (ref, subject, line) = k
            val a: Side
            val b: Side
            if (ref.kind == LineKind.MONEYLINE || ref.kind == LineKind.SPREAD) { a = Side.HOME; b = Side.AWAY } else { a = Side.OVER; b = Side.UNDER }
            // The newest row of each side (a book can carry the same side twice for a moment while its line moves).
            val x = list.filter { it.second == a }.maxByOrNull { it.first.atMs }?.first ?: continue
            val y = list.filter { it.second == b }.maxByOrNull { it.first.atMs }?.first ?: continue
            val pa = when (ref.kind) { LineKind.MONEYLINE -> null; LineKind.SPREAD -> line; else -> line }
            val pb = when (ref.kind) { LineKind.MONEYLINE -> null; LineKind.SPREAD -> line?.let { -it }; else -> line }
            out += RefBookMarket(
                book, title, ref.kind, listOf(RefQuote(a, x.decimal, pa), RefQuote(b, y.decimal, pb)), minOf(x.atMs, y.atMs).takeIf { it > 0 }, ref.period, subject, ref.stat,
            )
        }
        return out
    }

    fun familyOf(ref: Ref): MarketFamily = when (ref.kind) {
        LineKind.MONEYLINE -> MarketFamily.MONEYLINE
        LineKind.SPREAD -> if (ref.period == 0 || ref.period == RefBookMarket.PERIOD_SETS) MarketFamily.SPREAD else MarketFamily.FIRST_HALF
        LineKind.TOTAL -> if (ref.period == 0 || ref.period == RefBookMarket.PERIOD_SETS) MarketFamily.TOTAL else MarketFamily.FIRST_HALF
        LineKind.TEAM_TOTAL -> MarketFamily.TEAM_TOTAL
        LineKind.PLAYER_PROP -> MarketFamily.PLAYER_PROPS
    }
}
