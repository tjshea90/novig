package com.tjshea.vigilant.data.book

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.Plan
import com.tjshea.vigilant.data.scanner.PlannedMarket
import com.tjshea.vigilant.data.scanner.PlannedOutcome
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel

/**
 * A sportsbook's own lines (Vigilant MGM: BetMGM's), gathered from the feeds that already carry them
 * for the fair line (PropLine's league boards and per-game props first, then The Odds API's), shaped
 * like Novig's catalog so the same planner, pricing and screens work on it unchanged: one event per
 * game, one market per line, the book's posted odds as each outcome's exact price ([NovigBook.posted]),
 * and no fee (a sportsbook's vig is in its odds).
 *
 * A game's lines are kept in the book's own home/away orientation (the first feed that listed it);
 * a later feed's copy of the same game is matched by teams and start time and turned to match.
 */
class BookBoard private constructor(
    val book: Sportsbook,
    val games: List<Game>,
    /** When the scan that built this board fetched it: every line's "price read at". */
    val fetchedAtMs: Long,
) {
    class Game(
        val league: League,
        val event: NovigEvent,
        val home: String,
        val away: String,
        /** Line id ([lineId]) -> the book's quote, in this game's orientation. */
        val lines: Map<String, RefBookMarket>,
    )

    private val byId: Map<String, Game> = games.associateBy { it.event.eventId }

    val events: List<NovigEvent> get() = games.map { it.event }

    /** Lines on the board (each is one market with two outcomes). */
    val lineCount: Int get() = games.sumOf { it.lines.size }

    fun game(eventId: String): Game? = byId[eventId]

    /** What [plan] hands [com.tjshea.vigilant.data.scanner.Pricing]: the plan, the book's prices, and when the book last showed each. */
    class Priceable(val plan: Plan, val books: Map<String, NovigBook>, val seenAt: Map<String, Long>)

    /**
     * Every line of the book that some other book also quotes, matched to [fair] (the fair-line feeds,
     * the book itself already taken out), like [Planner.plan] does for Novig's catalog. Games no fair
     * feed lists keep their moneyline (the Games tab shows the board), for the first
     * [NO_FAIR_GAME_CAP] of them. With [youngOnly], a line the book's feed last saw over
     * [Freshness.MAX_QUOTE_AGE_MS] ago is left out: an old price is never offered as a bet.
     */
    fun plan(fair: Collection<RefSnapshot>, settings: ScanSettings, now: Long, youngOnly: Boolean): Priceable {
        val eligible = Planner.eligibleEvents(events, settings, now)
        val matches = Planner.matchEvents(eligible, fair)
        val types = settings.novigMarketTypes.toSet()
        val planned = ArrayList<PlannedMarket>()
        val books = HashMap<String, NovigBook>()
        val seen = HashMap<String, Long>()
        var noFairGames = 0
        for (m in matches.sortedBy { it.event.startsTs }) {
            val game = byId[m.event.eventId] ?: continue
            val ref = m.refEvent
            if (ref == null && noFairGames >= NO_FAIR_GAME_CAP) continue
            var any = false
            for ((id, line) in game.lines) {
                val type = typeOf(line) ?: continue
                if (type !in types) continue
                if (youngOnly && !Freshness.fresh(line.lastUpdateMs, now)) continue
                if (ref == null && line.kind != LineKind.MONEYLINE) continue
                val oriented = if (m.refSwapped) line.flipped() else line
                val key = ref?.let { LineKey(it.id, line.kind, oriented.line, line.period, oriented.subject, line.stat) }
                // No other book quotes this line: it can't be priced.
                if (key != null && ref.markets.none { key.matches(it) }) continue
                val marketId = "${game.event.eventId}|$id"
                val outcomes = line.quotes.sortedBy { it.side.ordinal }.map { q ->
                    val text = selection(game, line, q.side, q.point)
                    val outcome = NovigOutcome("$marketId|${q.side}", outcomeName(game, line, q.side, q.point), "TBD", BookRef(line.bookEventId, q.bookOutcomeId, line.link))
                    Triple(outcome, q, text)
                }
                if (outcomes.size != 2) continue
                val market = NovigMarket(
                    marketId = marketId,
                    eventId = game.event.eventId,
                    marketType = type,
                    status = "OPEN",
                    description = description(game, line, type),
                    startsTs = game.event.startsTs,
                    fee = NO_FEE,
                    outcomes = outcomes.map { it.first },
                )
                planned += PlannedMarket(
                    game.league, game.event, ref, market, line.kind, key, label(game.league, type),
                    outcomes.map { (o, q, text) -> PlannedOutcome(o, OutcomeTarget.Is(if (m.refSwapped) flip(q.side) else q.side), text) },
                )
                books[marketId] = NovigBook(
                    marketId, 0, emptyMap(), fetchedAtMs,
                    posted = outcomes.associate { (o, q, _) -> o.outcomeId to listOf(TakeLevel(1.0 / q.decimalOdds, DEPTH)) },
                )
                line.lastUpdateMs?.let { seen[marketId] = it }
                any = true
            }
            if (ref == null && any) noFairGames++
        }
        return Priceable(Plan(planned, matches), books, seen)
    }

    companion object {
        /** A sportsbook charges no fee on top of its odds. */
        val NO_FEE = MarketFee(coefficient = 0.0, makerCredit = 0.0, charged = FeeCharge.WHEN_LIVE)

        /**
         * Stand-in depth for a posted price: a sportsbook's limit isn't published, so a stake is never
         * capped by it (the screens don't show depth for a sportsbook).
         */
        const val DEPTH = 1_000_000_000L

        /** Games with no fair price shown on the Games tab (moneyline only), soonest first. */
        const val NO_FAIR_GAME_CAP = 20

        /**
         * The [book]'s lines out of [snapshots] (the order is the priority: PropLine first), one game per
         * matchup: a later feed adds only games and lines the earlier ones didn't have. [fetchedAtMs]:
         * when this scan read them.
         */
        fun build(book: Sportsbook, snapshots: List<RefSnapshot>, fetchedAtMs: Long, now: Long): BookBoard {
            class Building(val league: League, val event: NovigEvent, val home: String, val away: String) {
                val lines = LinkedHashMap<String, RefBookMarket>()
            }
            val games = LinkedHashMap<String, Building>()
            // A feed's game id -> (board game, whether that feed has home and away the other way round).
            val known = HashMap<String, Pair<String, Boolean>>()
            for (snap in snapshots) {
                val league = Leagues.ALL.firstOrNull { it.oddsApiSportKey == snap.sportKey } ?: continue
                val withBook = snap.events.mapNotNull { e ->
                    e.copy(markets = e.markets.filter { it.bookKey == book.feedKey }).takeIf { it.markets.isNotEmpty() }
                }
                if (withBook.isEmpty()) continue
                val unknown = withBook.filter { it.id !in known }
                val existing = games.values.filter { it.league == league }.map { it.event }
                if (unknown.isNotEmpty() && existing.isNotEmpty()) {
                    val matches = Planner.matchEvents(existing, listOf(RefSnapshot(snap.sportKey, unknown, snap.fetchedAtMs, provider = snap.provider)))
                    for (m in matches) {
                        val ref = m.refEvent ?: continue
                        known[ref.id] = m.event.eventId to m.refSwapped
                    }
                }
                for (e in withBook) {
                    val (eventId, swapped) = known[e.id] ?: run {
                        val id = "${book.id}:${e.id}"
                        val status = if (e.commenceMs > now) NovigEvent.STATUS_PREGAME else NovigEvent.STATUS_LIVE
                        val event = NovigEvent(id, sportOf(snap.sportKey), league.novigName, status, "${e.away} @ ${e.home}", e.commenceMs)
                        games[id] = Building(league, event, e.home, e.away)
                        known[e.id] = id to false
                        id to false
                    }
                    val game = games[eventId] ?: continue
                    for (market in e.markets) {
                        val oriented = if (swapped) market.flipped() else market
                        val id = lineId(oriented) ?: continue
                        if (id !in game.lines) game.lines[id] = oriented
                    }
                }
            }
            return BookBoard(book, games.values.map { Game(it.league, it.event, it.home, it.away, it.lines) }, fetchedAtMs)
        }

        /**
         * [result] with each bet's "odds as of" ([com.tjshea.vigilant.data.scanner.Opportunity.fairAsOfMs])
         * no later than when the book's own feed last saw its price: a sportsbook's price is a quote
         * like the others, so it ages out of the feed the same way (RESEARCH.md §24).
         */
        fun withBookAges(result: ScanResult, seenAt: Map<String, Long>): ScanResult {
            if (seenAt.isEmpty()) return result
            val aged = result.opportunities.map { o ->
                val seen = seenAt[o.market.marketId] ?: return@map o
                o.copy(fairAsOfMs = o.fairAsOfMs?.let { minOf(it, seen) } ?: seen)
            }
            val byKey = aged.associateBy { it.key }
            return result.copy(
                opportunities = aged,
                games = result.games.map { g -> g.copy(outcomes = g.outcomes.map { byKey[it.key] ?: it }) },
            )
        }

        /** A line's identity within a game: kind, period, whose, which stat, which number. Null for one the board can't hold. */
        internal fun lineId(m: RefBookMarket): String? {
            if (typeOf(m) == null || m.quotes.size != 2) return null
            val who = when (m.kind) {
                LineKind.PLAYER_PROP -> PlayerNames.key(m.subject ?: return null)
                LineKind.TEAM_TOTAL -> m.subject ?: return null
                else -> ""
            }
            return listOf(m.kind.name, m.period.toString(), who.replace('|', ' ').replace('/', ' '), m.stat.orEmpty(), m.line?.let(Planner::fmt).orEmpty()).joinToString("|")
        }

        /** Novig's market type for a line (what the feed's filters and the Tracker read), null for one Vigilant doesn't price. */
        internal fun typeOf(m: RefBookMarket): String? = when (m.kind) {
            LineKind.MONEYLINE -> "MONEY".takeIf { m.period == 0 }
            LineKind.SPREAD -> when (m.period) {
                0 -> "SPREAD"
                1 -> "SPREAD_1H"
                else -> null
            }
            LineKind.TOTAL -> when (m.period) {
                0 -> "TOTAL"
                1 -> "TOTAL_1H"
                RefBookMarket.PERIOD_FIRST_INNING -> "FIRST_INNING_TOTAL"
                else -> null
            }
            LineKind.TEAM_TOTAL -> "TEAM_TOTAL".takeIf { m.period == 0 }
            LineKind.PLAYER_PROP -> m.stat?.takeIf { m.period == 0 && it in PropStats.NOVIG_TYPES }
        }

        private fun label(league: League, type: String): String {
            val half = if (league.oddsApiSportKey.startsWith("baseball")) "F5" else "1H"
            return when (type) {
                "MONEY" -> "Moneyline"
                "SPREAD" -> "Spread"
                "SPREAD_1H" -> "$half Spread"
                "TOTAL" -> "Total"
                "TOTAL_1H" -> "$half Total"
                "FIRST_INNING_TOTAL" -> "1st Inning Total"
                "TEAM_TOTAL" -> "Team Total"
                else -> PropStats.displayName(type)
            }
        }

        private fun team(game: BookBoard.Game, side: Side): String = if (side == Side.AWAY) game.away else game.home

        private fun subjectTeam(game: BookBoard.Game, line: RefBookMarket): String =
            if (line.subject == RefBookMarket.AWAY) game.away else game.home

        private fun overUnder(side: Side, point: Double?): String =
            (if (side == Side.OVER) "Over" else "Under") + (point?.let { " " + Planner.fmt(it) } ?: "")

        /** How the bet reads everywhere ("Dallas Cowboys -3.5", "Rams Over 22.5", "Patrick Mahomes Over 233.5"): like Novig's. */
        private fun selection(game: BookBoard.Game, line: RefBookMarket, side: Side, point: Double?): String = when (line.kind) {
            LineKind.MONEYLINE -> team(game, side)
            LineKind.SPREAD -> "${team(game, side)} ${point?.let(Planner::signed).orEmpty()}".trim()
            LineKind.TOTAL -> overUnder(side, point)
            LineKind.TEAM_TOTAL -> "${subjectTeam(game, line)} ${overUnder(side, point)}"
            LineKind.PLAYER_PROP -> "${line.subject} ${overUnder(side, point)}"
        }

        /** The outcome's own name, as Novig would print it ("DAL +3.5" style, full names here). */
        private fun outcomeName(game: BookBoard.Game, line: RefBookMarket, side: Side, point: Double?): String = when (line.kind) {
            LineKind.MONEYLINE, LineKind.SPREAD -> selection(game, line, side, point)
            else -> overUnder(side, point)
        }

        /** Novig's description shapes: "Los Angeles Rams 22.5 TEAM_TOTAL", "Patrick Mahomes 233.5 PASSING_YARDS". */
        private fun description(game: BookBoard.Game, line: RefBookMarket, type: String): String = when (line.kind) {
            LineKind.TEAM_TOTAL -> "${subjectTeam(game, line)} ${line.line?.let(Planner::fmt).orEmpty()} $type"
            LineKind.PLAYER_PROP -> "${line.subject} ${line.line?.let(Planner::fmt).orEmpty()} $type"
            else -> "${game.away} @ ${game.home} $type"
        }

        private fun flip(side: Side): Side = when (side) {
            Side.HOME -> Side.AWAY
            Side.AWAY -> Side.HOME
            else -> side
        }

        private fun sportOf(sportKey: String): String = sportKey.substringBefore('_').uppercase()
    }
}
