package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.ParlayPropsSource
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * "Check odds now"'s backup for CrazyNinjaOdds (Tj, 2026-09-30: "it said crazyninjaodds didn't answer … if there is a good backup that does
 * the same exact odds check, I think parlayapi can do this same odds check"): an open bet's every-book prices from ParlayAPI, in the shape
 * CNO's game page gives them ([CnoBooksView]), so [CnoBooks.check] judges them with exactly CNO's check (each two-sided book devigged worst
 * case, the lower of their mean and median). One game-lines call (5 credits, Pinnacle's alternate numbers included) and one props call
 * (3 credits) per league, each kept [KEEP_MS] so a whole pass shares them. Only while ParlayAPI is on with a key ([active]).
 */
class ParlayBooks(
    private val client: TheOddsApiClient,
    private val active: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Its props answers' injury reports go here too (free). */
    injuries: com.tjshea.vigilant.data.reference.InjuryIndex? = null,
) {
    private class Kept(val atMs: Long, val snap: RefSnapshot?)

    private val props = ParlayPropsSource(client, injuries)

    private val kept = HashMap<String, Kept>()
    private val mutex = Mutex()

    /** ParlayAPI calls made (tests, Diagnostics). */
    @Volatile
    var requests: Int = 0
        private set

    /** [bet]'s books as ParlayAPI has them now, or null (off, not a bet it carries, not found, or no answer). */
    suspend fun view(bet: TrackedBet): CnoBooksView? {
        val pick = BetGrader.pickOf(bet) ?: return null
        return view(bet.league, bet.eventName, bet.startsTs, bet.selection, pick)
    }

    /**
     * A bet that isn't in the Tracker (a ParlayAPI pick's sheet, TASKS.md P4: "opens a screen that shows other sports books odds on the same
     * bet"), by its league, game ("Away @ Home"), start (null: not known) and wording: its books as ParlayAPI has them now, or null.
     */
    suspend fun view(league: String, eventName: String, startsTs: Long?, marketLabel: String, selection: String): CnoBooksView? {
        val pick = BetGrader.pickOf(marketLabel, selection) ?: return null
        return view(league, eventName, startsTs, selection, pick)
    }

    private suspend fun view(leagueName: String, eventName: String, startsTs: Long?, selection: String, pick: BetGrader.Pick): CnoBooksView? {
        if (!active()) return null
        val league = Leagues.byNovigName(leagueName)?.takeIf { it.oddsApiListed } ?: return null
        val sport = league.oddsApiSportKey
        val snap = when (pick) {
            // Every page of the league's props, as a scan reads them.
            is BetGrader.Pick.Prop -> snapshot("props:$sport") { props.odds(league, ScanSettings()) }
            is BetGrader.Pick.Moneyline, is BetGrader.Pick.Spread, is BetGrader.Pick.Total ->
                snapshot("odds:$sport") { client.fetchCurrent(sport, client.booksFor(ScanSettings()), GAME_MARKETS, startsBeforeMs = clock() + HORIZON_MS) }
            else -> null
        } ?: return null
        return viewOf(snap, eventName, startsTs, selection, pick, clock())
    }

    private suspend fun snapshot(key: String, read: suspend () -> RefSnapshot): RefSnapshot? = mutex.withLock {
        kept[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return@withLock it.snap }
        requests++
        val snap = try {
            read()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null // no answer, no key, or credits held back: tried again after KEEP_MS
        }
        kept[key] = Kept(clock(), snap)
        snap
    }

    companion object {
        /** A league's answer is shared by a whole pass (and the pass after it, a minute later). */
        const val KEEP_MS = 2 * 60_000L

        /** Games this far ahead are asked for (an open bet's game is rarely further). */
        private const val HORIZON_MS = 15L * 24 * 3_600_000L

        /** A reference game must start within this of the bet's. */
        private const val START_GAP_MS = 3 * 3_600_000L

        private val GAME_MARKETS = listOf("h2h", "spreads", "totals", "alternate_spreads", "alternate_totals")

        /** ParlayAPI's book keys to CNO's column codes (others keep their own key, upper-cased: [CnoBooks.name] shows it as is). */
        private val CODES = mapOf(
            "pinnacle" to "PN", "draftkings" to "DK", "fanduel" to "FD", "caesars" to "CZR", "williamhill_us" to "CZR", "betmgm" to "MGM",
            "betrivers" to "BR", "bet365" to "B365", "fanatics" to "FN", "bovada" to "BV", "betonlineag" to "BO", "betonline" to "BO",
            "prophetx" to "PX", "kalshi" to "KI", "novig" to CnoBooks.NOVIG, "hardrockbet" to "HR-IN", "hardrock" to "HR-IN",
            "pointsbetus" to "PB", "pointsbet" to "PB", "circasports" to "CS", "espnbet" to "TSB", "ballybet" to "BB", "fliff" to "FL",
        )

        fun codeOf(bookKey: String): String = CODES[bookKey.lowercase()] ?: bookKey.uppercase()

        /** [pick]'s side and the other side in [snap], one price pair per book, as a CNO game page would list them. Pure. */
        fun viewOf(snap: RefSnapshot, bet: TrackedBet, pick: BetGrader.Pick, now: Long): CnoBooksView? =
            viewOf(snap, bet.eventName, bet.startsTs, bet.selection, pick, now)

        /** The same for a bet by its game ("Away @ Home"), start (null: any game of those two teams in [snap]) and wording. Pure. */
        fun viewOf(snap: RefSnapshot, eventName: String, startsTs: Long?, selection: String, pick: BetGrader.Pick, now: Long): CnoBooksView? {
            val m = NovigText.parseMatchup(eventName) ?: return null
            val game = snap.events.filter { startsTs == null || abs(it.commenceMs - startsTs) <= START_GAP_MS }
                .map { e -> e to (TeamMatcher.similarity(m.home, e.home) + TeamMatcher.similarity(m.away, e.away)) }
                .filter { (e, _) -> TeamMatcher.similarity(m.home, e.home) >= 0.5 && TeamMatcher.similarity(m.away, e.away) >= 0.5 }
                // With no start to go by, the soonest of a series' games (the one a pick at Novig now is on).
                .sortedBy { it.first.commenceMs }
                .maxByOrNull { it.second }?.first ?: return null
            val prices = LinkedHashMap<String, CnoBookPrice>()
            for (mk in game.markets) {
                if (mk.period != 0) continue
                val pair = pairFor(mk, game, pick) ?: continue
                val code = codeOf(mk.bookKey)
                if (code !in prices) prices[code] = CnoBookPrice(code, odds = pair.first, otherOdds = pair.second)
            }
            if (prices.isEmpty()) return null
            return CnoBooksView(bet = selection, prices = prices.values.toList(), fetchedAtMs = snap.fetchedAtMs.takeIf { it > 0 } ?: now)
        }

        private fun american(d: Double?): Int? = d?.takeIf { it > 1.0 }?.let { Odds.decimalToAmerican(it) }

        private fun near(a: Double?, b: Double) = a != null && abs(a - b) < 1e-6

        /** (this side, other side) American prices of [mk] for [pick], or null when [mk] isn't [pick]'s line. */
        private fun pairFor(mk: RefBookMarket, game: RefEvent, pick: BetGrader.Pick): Pair<Int, Int>? {
            fun quote(side: Side, point: Double? = null) = mk.quotes.firstOrNull { it.side == side && (point == null || near(it.point, point)) }
            fun both(mine: Side, other: Side, myPoint: Double? = null, otherPoint: Double? = null): Pair<Int, Int>? {
                val a = american(quote(mine, myPoint)?.decimalOdds) ?: return null
                val b = american(quote(other, otherPoint)?.decimalOdds) ?: return null
                return a to b
            }
            fun homeSide(team: String) = TeamMatcher.similarity(team, game.home) >= TeamMatcher.similarity(team, game.away)
            return when (pick) {
                is BetGrader.Pick.Moneyline -> if (mk.kind != LineKind.MONEYLINE) null
                else if (homeSide(pick.team)) both(Side.HOME, Side.AWAY) else both(Side.AWAY, Side.HOME)
                is BetGrader.Pick.Spread -> if (mk.kind != LineKind.SPREAD || pick.period != BetGrader.Period.GAME) null
                else if (homeSide(pick.team)) both(Side.HOME, Side.AWAY, pick.line, -pick.line) else both(Side.AWAY, Side.HOME, pick.line, -pick.line)
                is BetGrader.Pick.Total -> if (mk.kind != LineKind.TOTAL || pick.period != BetGrader.Period.GAME) null
                else if (pick.over) both(Side.OVER, Side.UNDER, pick.line, pick.line) else both(Side.UNDER, Side.OVER, pick.line, pick.line)
                is BetGrader.Pick.Prop -> if (mk.kind != LineKind.PLAYER_PROP || mk.stat != pick.stat || !PlayerNames.same(mk.subject, pick.player)) null
                else if (pick.over) both(Side.OVER, Side.UNDER, pick.line, pick.line) else both(Side.UNDER, Side.OVER, pick.line, pick.line)
                else -> null
            }
        }
    }
}
