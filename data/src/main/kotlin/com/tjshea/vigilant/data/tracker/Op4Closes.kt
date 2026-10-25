package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.Op4Budget
import com.tjshea.vigilant.data.reference.Op4Fixture
import com.tjshea.vigilant.data.reference.Op4Parser
import com.tjshea.vigilant.data.reference.Op4Store
import com.tjshea.vigilant.data.reference.OddsPapi4Client
import com.tjshea.vigilant.data.reference.OpBooks
import com.tjshea.vigilant.data.reference.OpMarkets
import com.tjshea.vigilant.data.reference.OpParser
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.Devig
import java.time.Instant
import kotlin.math.abs

/**
 * Pinnacle's closing line from OddsPapi's FREE v4 plan (ODDSPAPI_API.md addendum 2026-10-11). The expensive part of a close is finding the game and knowing which market is which; that costs a few
 * BILLABLE requests, all cached (the market catalogue and a league's tournament id for 30 days on disk, a game list for 12 hours per 6-hour window), and gated by [Op4Budget] so the month's small
 * allowance is never spent down. The close itself is the FREE, unmetered `/v4/historical-odds`: the last active Pinnacle price of each of the bet's two outcomes at or before the game's start, devigged across
 * the two sides exactly as [OpCloses] does. Game lines and first-half lines only (the free plan carries no player props). Last among the paid close sources: it asks only when OddsPapi v5 and SportsGameOdds had nothing.
 */
class Op4Closes(
    private val client: OddsPapi4Client,
    private val budget: Op4Budget,
    private val store: Op4Store,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloseSource {
    override val id: String get() = ID
    override val sharp: Boolean get() = true

    /** Settings' OddsPapi Free switch: off, nothing is asked. */
    @Volatile var enabled: Boolean = false
    override val active: Boolean get() = enabled && client.hasKey()

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        if (!active) return emptyMap()
        val out = HashMap<String, CloseLookup>()
        for (b in bets) {
            val league = leagueOf(b)
            if (league == null) { out[b.id] = CloseLookup.None("OddsPapi Free has no feed for ${b.league.ifBlank { "this league" }}"); continue }
            out[b.id] = try {
                lookup(league, b)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.tjshea.vigilant.data.keys.CreditsHeldBackException) {
                CloseLookup.Later("OddsPapi Free keeps its last requests")
            } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) {
                CloseLookup.Later("OddsPapi Free's monthly requests are used up")
            } catch (e: Exception) {
                CloseLookup.Later("OddsPapi Free didn't answer")
            }
        }
        return out
    }

    private suspend fun lookup(league: League, b: TrackedBet): CloseLookup {
        val pick = BetGrader.pickOf(b) ?: return CloseLookup.None("Couldn't read this bet's market")
        val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
        if (pick is BetGrader.Pick.FirstSet || pick is BetGrader.Pick.Prop) return CloseLookup.None("OddsPapi Free keeps game and 1st-half lines")
        if (b.startsTs < HISTORY_FROM_MS) return CloseLookup.None("OddsPapi Free's history starts in January 2026")
        if (b.startsTs > clock()) return CloseLookup.Later("The game has not started")
        val sport = OpBooks.sportId(league) ?: return CloseLookup.None("OddsPapi Free has no feed for ${league.novigName}")
        val acc = client.lastAccount ?: runCatching { client.account() }.getOrNull()
        if (acc != null && acc.sportIds.isNotEmpty() && sport !in acc.sportIds) return CloseLookup.None("Your OddsPapi Free plan doesn't include this sport")
        if (acc != null && acc.books.isNotEmpty() && "pinnacle" !in acc.books.keys) return CloseLookup.None("Your OddsPapi Free plan doesn't include Pinnacle")
        val tournament = tournamentId(league, sport) ?: return CloseLookup.None("OddsPapi's catalogue has no ${league.novigName}")
        val games = fixtures(tournament, b.startsTs)
        val near = games.filter { f -> (f.trueStartMs ?: f.startMs)?.let { abs(it - b.startsTs) <= GAP_MS } == true }
        val game = ParlayCloses.bestGame(m.home, m.away, b.startsTs, near, { it.p1 }, { it.p2 }, { it.trueStartMs ?: it.startMs })
            ?: ParlayCloses.bestGame(m.away, m.home, b.startsTs, near, { it.p1 }, { it.p2 }, { it.trueStartMs ?: it.startMs })
            ?: return CloseLookup.None("Not in OddsPapi")
        val start = game.trueStartMs ?: game.startMs ?: return CloseLookup.None("OddsPapi has no start time for the game")
        if (clock() < start) return CloseLookup.Later("The game has not started")
        val markets = markets(league, sport)
        val cls = opClassOf(pick, game.p1, game.p2, markets) ?: return CloseLookup.None("OddsPapi has no market for this bet")
        val side1 = when (pick) {
            is BetGrader.Pick.Moneyline -> TeamMatcher.whichOf(pick.team, game.p1, game.p2)
            is BetGrader.Pick.Spread -> TeamMatcher.whichOf(pick.team, game.p1, game.p2)
            is BetGrader.Pick.TeamTotal -> TeamMatcher.whichOf(pick.team, game.p1, game.p2)
            else -> 0
        }
        val mine: Long
        val other: Long
        when (pick) {
            is BetGrader.Pick.Moneyline, is BetGrader.Pick.Spread -> { if (side1 == 0) return CloseLookup.None("Couldn't tell which team the bet is on"); if (side1 == 1) { mine = cls.first; other = cls.second } else { mine = cls.second; other = cls.first } }
            is BetGrader.Pick.TeamTotal -> if (pick.over) { mine = cls.first; other = cls.second } else { mine = cls.second; other = cls.first }
            is BetGrader.Pick.Total -> if (pick.over) { mine = cls.first; other = cls.second } else { mine = cls.second; other = cls.first }
            else -> return CloseLookup.None("OddsPapi Free keeps game and 1st-half lines")
        }
        val p = closePrice(game, start, mine) ?: return CloseLookup.None("OddsPapi Free has no Pinnacle close here")
        val q = closePrice(game, start, other) ?: return CloseLookup.None("OddsPapi Free has no Pinnacle close here")
        return CloseLookup.Found(CloseDevig.fair(1.0 / p, 1.0 / q), "OddsPapi Free · Pinnacle close")
    }

    /** The last active Pinnacle price of [outcomeId] at or before [start], from the FREE historical odds (cached for the run: a finished game's history does not change). */
    private suspend fun closePrice(game: Op4Fixture, start: Long, outcomeId: Long): Double? {
        val key = "${game.id}|$outcomeId"
        val ticks = ticksCache[key] ?: Op4Parser.ticks(
            client.free("/v4/historical-odds", listOf("fixtureId" to game.id, "bookmakers" to BOOK, "outcomeId" to outcomeId.toString())), BOOK, outcomeId,
        ).also { ticksCache[key] = it }
        return ticks.filter { it.active && it.atMs <= start }.maxByOrNull { it.atMs }?.decimal
    }

    private val ticksCache = java.util.concurrent.ConcurrentHashMap<String, List<com.tjshea.vigilant.data.reference.Op4Tick>>()
    private val catalog = com.tjshea.vigilant.data.reference.Op4Catalog(client, budget, store, clock)

    private suspend fun tournamentId(league: League, sport: Int): Long? = catalog.tournamentId(league, sport)
    private suspend fun fixtures(tournament: Long, startMs: Long): List<Op4Fixture> = catalog.fixturesAround(tournament, startMs)
    private suspend fun markets(league: League, sport: Int): OpMarkets = catalog.markets(league, sport)

    companion object {
        const val ID = "oddspapi_free"
        const val BOOK = "pinnacle"
        const val GAP_MS = 3 * 3_600_000L

        /** The free plan's history starts January 2026 (docs: get-historical-odds). */
        val HISTORY_FROM_MS: Long = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()


        fun leagueOf(b: TrackedBet): League? =
            Leagues.ALL.firstOrNull { it.novigName.equals(b.league, true) || it.displayName.equals(b.league, true) }?.takeIf { OpBooks.supports(it) }
    }
}
/** The catalogue market a bet [pick] is on, for a game whose participants are [p1] and [p2] (OddsPapi v4's close source). */
internal fun opClassOf(pick: BetGrader.Pick, p1: String, p2: String, markets: com.tjshea.vigilant.data.reference.OpMarkets): OpClass? {
    fun period(p: BetGrader.Period): Int? = when (p) {
        BetGrader.Period.GAME -> 0
        BetGrader.Period.FIRST_HALF -> 1
        BetGrader.Period.FIRST_INNING -> RefBookMarket.PERIOD_FIRST_INNING
        else -> null
    }
    return when (pick) {
        is BetGrader.Pick.Moneyline -> markets.find(LineKind.MONEYLINE, 0, null)
        is BetGrader.Pick.Spread -> {
            val side = TeamMatcher.whichOf(pick.team, p1, p2).takeIf { it != 0 } ?: return null
            markets.find(LineKind.SPREAD, period(pick.period) ?: return null, if (side == 1) pick.line else -pick.line)
        }
        is BetGrader.Pick.Total -> markets.find(LineKind.TOTAL, period(pick.period) ?: return null, pick.line)
        is BetGrader.Pick.TeamTotal -> {
            val side = TeamMatcher.whichOf(pick.team, p1, p2).takeIf { it != 0 } ?: return null
            markets.find(LineKind.TEAM_TOTAL, 0, pick.line, team = side)
        }
        is BetGrader.Pick.Prop -> markets.find(LineKind.PLAYER_PROP, 0, pick.line, stat = pick.stat)
        is BetGrader.Pick.FirstSet -> null
    }
}
