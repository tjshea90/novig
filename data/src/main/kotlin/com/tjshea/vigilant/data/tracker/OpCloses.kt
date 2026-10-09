package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.OddsPapiClient
import com.tjshea.vigilant.data.reference.OddsPapiFeed
import com.tjshea.vigilant.data.reference.OpBooks
import com.tjshea.vigilant.data.reference.OpClass
import com.tjshea.vigilant.data.reference.OpClv
import com.tjshea.vigilant.data.reference.OpFixture
import com.tjshea.vigilant.data.reference.OpParser
import com.tjshea.vigilant.data.reference.OpProps
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.Devig
import kotlin.math.abs

/**
 * Closing lines from OddsPapi (Tj, 2026-10-09: "clv/closing lines including clv for any non graded bets ... historical odds and clv"; ODDSPAPI_API.md §4): `/fixtures` finds the bet's game,
 * `/fixtures/odds/clv` gives each book's opening and closing price of an odds id, and the close is Pinnacle's (Circa's when Pinnacle has none), devigged across the bet's two sides at the exact number
 * the bet was on. Any bet however old, graded or not. OddsPapi's `clv` is "the last price before settlement"; so that a live price is never taken for the close, a `clv` stamped after the game
 * started is replaced by the last price on `/fixtures/odds/historical` at or before the start. A look is kept [KEEP_MS] so the 3-hourly pass does not ask again.
 */
class OpCloses(
    private val client: OddsPapiClient,
    private val feed: OddsPapiFeed,
    private val hasKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloseSource {
    override val id: String get() = ID

    /** Settings' OddsPapi switch: off, nothing is asked. */
    @Volatile var enabled: Boolean = false
    override val active: Boolean get() = enabled && hasKey()

    private class Kept<T>(val atMs: Long, val value: T)
    private val fixtures = java.util.concurrent.ConcurrentHashMap<String, Kept<List<OpFixture>>>()
    private val clvs = java.util.concurrent.ConcurrentHashMap<String, Kept<List<OpClv>>>()

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        if (!active) return emptyMap()
        val out = HashMap<String, CloseLookup>()
        for (b in bets) {
            val league = leagueOf(b)
            if (league == null) { out[b.id] = CloseLookup.None("OddsPapi has no feed for ${b.league.ifBlank { "this league" }}"); continue }
            out[b.id] = try {
                lookup(league, b)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CloseLookup.Later("OddsPapi didn't answer")
            }
        }
        return out
    }

    private suspend fun lookup(league: League, b: TrackedBet): CloseLookup {
        val pick = BetGrader.pickOf(b) ?: return CloseLookup.None("Couldn't read this bet's market")
        val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
        if (pick is BetGrader.Pick.FirstSet) return CloseLookup.None("OddsPapi keeps full-game and 1st-half lines")
        val tournament = feed.tournamentFor(league) ?: return CloseLookup.None("OddsPapi's catalogue has no ${league.novigName}")
        val from = (b.startsTs - GAP_MS) / 1000L
        val to = (b.startsTs + GAP_MS) / 1000L
        val fkey = "${tournament.id}|${from / 3600}|${to / 3600}"
        val list = fixtures[fkey]?.takeIf { clock() - it.atMs < KEEP_MS }?.value
            ?: OpParser.fixtures(client.get("/fixtures", listOf("tournamentId" to tournament.id.toString(), "startTimeFrom" to from.toString(), "startTimeTo" to to.toString()))).also { fixtures[fkey] = Kept(clock(), it) }
        val game = ParlayCloses.bestGame(m.home, m.away, b.startsTs, list.filter { f -> f.startMs?.let { abs(it - b.startsTs) <= GAP_MS } == true }, { it.p1 }, { it.p2 }, { it.startMs })
            ?: return CloseLookup.None("Not in OddsPapi")
        if (!game.started && !game.finished) return CloseLookup.Later("The game has not started")
        val markets = feed.marketsFor(league)
        val cls = classOf(pick, game, markets) ?: return CloseLookup.None("OddsPapi has no market for this bet")
        val catalog = runCatching { feed.bookCatalog() }.getOrDefault(emptyList())
        var first: CloseLookup? = null
        for (book in BOOKS) {
            val slug = catalog.firstOrNull { OpBooks.appKey(it.slug, it.name) == book }?.slug ?: book
            val r = closeAt(game, cls, pick, slug, book)
            if (r is CloseLookup.Found) return r
            if (first == null) first = r
        }
        return first ?: CloseLookup.None("No close")
    }

    /** The catalogue market [pick] is on (a spread's number is participant 1's). */
    private fun classOf(pick: BetGrader.Pick, game: OpFixture, markets: com.tjshea.vigilant.data.reference.OpMarkets): OpClass? {
        fun period(p: BetGrader.Period): Int? = when (p) {
            BetGrader.Period.GAME -> 0
            BetGrader.Period.FIRST_HALF -> 1
            BetGrader.Period.FIRST_INNING -> RefBookMarket.PERIOD_FIRST_INNING
            else -> null
        }
        return when (pick) {
            is BetGrader.Pick.Moneyline -> markets.find(LineKind.MONEYLINE, 0, null)
            is BetGrader.Pick.Spread -> {
                val side = TeamMatcher.whichOf(pick.team, game.p1, game.p2).takeIf { it != 0 } ?: return null
                markets.find(LineKind.SPREAD, period(pick.period) ?: return null, if (side == 1) pick.line else -pick.line)
            }
            is BetGrader.Pick.Total -> markets.find(LineKind.TOTAL, period(pick.period) ?: return null, pick.line)
            is BetGrader.Pick.TeamTotal -> {
                val side = TeamMatcher.whichOf(pick.team, game.p1, game.p2).takeIf { it != 0 } ?: return null
                markets.find(LineKind.TEAM_TOTAL, 0, pick.line, team = side)
            }
            is BetGrader.Pick.Prop -> markets.find(LineKind.PLAYER_PROP, 0, pick.line, stat = pick.stat)
            is BetGrader.Pick.FirstSet -> null
        }
    }

    private suspend fun closeAt(game: OpFixture, cls: OpClass, pick: BetGrader.Pick, slug: String, book: String): CloseLookup {
        val label = book.replaceFirstChar { it.uppercase() }
        val bets = when (pick) {
            is BetGrader.Pick.Moneyline, is BetGrader.Pick.Spread, is BetGrader.Pick.TeamTotal -> {
                val team = when (pick) { is BetGrader.Pick.Moneyline -> pick.team; is BetGrader.Pick.Spread -> pick.team; is BetGrader.Pick.TeamTotal -> pick.team; else -> "" }
                val side = TeamMatcher.whichOf(team, game.p1, game.p2).takeIf { it != 0 } ?: return CloseLookup.None("Couldn't tell which team $team is")
                when (pick) {
                    is BetGrader.Pick.TeamTotal -> if (pick.over) cls.first to cls.second else cls.second to cls.first
                    else -> if (side == 1) cls.first to cls.second else cls.second to cls.first
                }
            }
            is BetGrader.Pick.Total -> if (pick.over) cls.first to cls.second else cls.second to cls.first
            is BetGrader.Pick.Prop -> if (pick.over) cls.first to cls.second else cls.second to cls.first
            is BetGrader.Pick.FirstSet -> return CloseLookup.None("OddsPapi keeps full-game and 1st-half lines")
        }
        val player: Long = if (pick is BetGrader.Pick.Prop) playerOf(game, slug, cls, pick) ?: return CloseLookup.None("Player not in OddsPapi's game") else 0L
        val (mine, other) = bets
        val prices = closePrices(game, slug, listOf(mine, other), player) ?: return CloseLookup.None("OddsPapi has no $book close here")
        val p = prices[mine]?.takeIf { it > 1.0 }?.let { 1.0 / it } ?: return CloseLookup.None("No $book close price")
        val q = prices[other]?.takeIf { it > 1.0 }?.let { 1.0 / it } ?: return CloseLookup.None("No $book close price")
        return CloseLookup.Found(Devig.multiplicative(listOf(p, q))[0], "OddsPapi · $label close")
    }

    private suspend fun clvOf(game: OpFixture, slug: String, onlyOutcomes: List<Long>?, player: Long): List<OpClv> {
        val key = "${game.id}|$slug|${onlyOutcomes?.joinToString(",") ?: "*"}|$player"
        clvs[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return it.value }
        val params = buildList {
            add("fixtureId" to game.id); add("bookmakers" to slug)
            if (onlyOutcomes != null) add("oddsIds" to onlyOutcomes.joinToString(",") { "${game.id}:$slug:$it:$player" })
        }
        return OpParser.clv(client.get("/fixtures/odds/clv", params)).also { clvs[key] = Kept(clock(), it) }
    }

    private suspend fun playerOf(game: OpFixture, slug: String, cls: OpClass, pick: BetGrader.Pick.Prop): Long? {
        val all = clvOf(game, slug, null, 0L).filter { it.outcomeId == cls.first || it.outcomeId == cls.second }
        val ids = all.map { it.playerId }.filter { it != 0L }.toSet()
        if (ids.isEmpty()) return null
        val names = feed.playerNames(ids)
        return ids.firstOrNull { id -> names[id]?.let { PlayerNames.same(OpProps.displayName(it), pick.player) } == true }
    }

    /** The close of each of [outcomes] at [slug]: `clv`, unless it was made after the game began, then the last historical price at or before the start. */
    private suspend fun closePrices(game: OpFixture, slug: String, outcomes: List<Long>, player: Long): Map<Long, Double>? {
        val rows = if (player == 0L) clvOf(game, slug, outcomes, 0L) else clvOf(game, slug, null, player)
        val start = game.startMs ?: return null
        val by = rows.filter { it.playerId == player && it.outcomeId in outcomes }.associateBy { it.outcomeId }
        if (by.size < outcomes.size) return null
        if (by.values.all { (it.closeMs ?: 0L) <= start + GRACE_MS && it.closeDecimal != null }) return by.mapValues { it.value.closeDecimal!! }
        // A live price: ask the timeline for the last price before the start.
        val ticks = OpParser.historical(client.get("/fixtures/odds/historical", listOf("fixtureId" to game.id, "bookmaker" to slug, "oddsIds" to outcomes.joinToString(",") { "${game.id}:$slug:$it:$player" })))
        val out = HashMap<Long, Double>()
        for (o in outcomes) {
            val last = ticks.filter { it.outcomeId == o && it.playerId == player && it.changedMs <= start && it.active && it.decimal != null }.maxByOrNull { it.changedMs } ?: return null
            out[o] = last.decimal!!
        }
        return out
    }

    companion object {
        const val ID = "oddspapi"

        /** Pinnacle's close first, then Circa's: the two sharpest. */
        val BOOKS = listOf("pinnacle", "circa")

        /** A close's game must start within this of the bet's. */
        const val GAP_MS = 3 * 3_600_000L

        /** A `clv` stamped later than this after the start is a live price. */
        const val GRACE_MS = 2 * 60_000L

        const val KEEP_MS = 30 * 60_000L

        fun leagueOf(b: TrackedBet): League? =
            Leagues.ALL.firstOrNull { it.novigName.equals(b.league, true) || it.displayName.equals(b.league, true) }?.takeIf { OpBooks.supports(it) }
    }
}
