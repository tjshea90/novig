package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28 (after v0.19.0): "Baseball is not over. Mlb still has games, college football has games. There
 * are way more than 7 total games for it to scan. Research novig API docs too. Make sure the app is taking full
 * advantage of the API and using it efficiently and as the docs describe." That Monday, Novig listed 55 college
 * games and 15 NFL games Thursday–Sunday: all past the old 3-day window.
 */
class ScanReachTest {

    private val day = 86_400_000L
    private val now = Fixtures.START_MS - 10 * day

    private fun game(id: String, inDays: Double, desc: String = "Away $id @ Home $id", status: String = NovigEvent.STATUS_PREGAME, league: String = "NCAAF") =
        NovigEvent(id, "FOOTBALL", league, status, desc, now + (inDays * day).toLong())

    // ---- a week ahead ------------------------------------------------------------------------------

    @Test
    fun `scans read a week ahead by default, and a saved 3-day window moves to 7 once`() {
        assertEquals(7, ScanSettings().daysAhead)
        assertEquals(7, ScanSettings(daysAhead = 3, schema = 7).migrate().daysAhead)
        // Tj's own other picks stay, and so does a 3 chosen after the move.
        for (kept in listOf(1, 2, 5, 10)) assertEquals(kept, ScanSettings(daysAhead = kept, schema = 7).migrate().daysAhead)
        assertEquals(3, ScanSettings(daysAhead = 3, schema = 8).migrate().daysAhead)
        assertTrue(ScanSettings.DAYS_AHEAD_CHOICES.containsAll(listOf(3, 7, 10)))
        // A file saved before the field changed gets the new default.
        assertEquals(7, Json { ignoreUnknownKeys = true }.decodeFromString(ScanSettings.serializer(), """{"schema":8}""").daysAhead)
    }

    @Test
    fun `games past the window are counted, futures and other leagues aren't`() {
        val events = listOf(
            game("mon", 0.5),
            game("sat", 5.5),
            game("sun", 6.5, league = "NFL"),
            game("champ", 20.0, desc = "Championship Winner"), // a future: no two sides
            game("mlb", 5.0, league = "MLB"), // not picked
        )
        val s = ScanSettings(leagues = setOf("NCAAF", "NFL"), daysAhead = 3)
        val plan = Planner.plan(events, emptyList(), emptyList<RefSnapshot>(), s, now)
        assertEquals(listOf("mon"), Planner.eligibleEvents(events, s, now).map { it.eventId })
        assertEquals(2, plan.laterGames)
        assertEquals(2, Pricing.price(plan, emptyMap(), s, now).stats.laterGames)
        // A week takes them in.
        assertEquals(0, Planner.plan(events, emptyList(), emptyList<RefSnapshot>(), s.copy(daysAhead = 7), now).laterGames)
    }

    @Test
    fun `a game held before its start (DELAYED) is still scanned, one held mid-game isn't`() {
        val events = listOf(
            game("held", 0.2, status = NovigEvent.STATUS_DELAYED),
            game("started", -0.1, status = NovigEvent.STATUS_DELAYED),
            game("gone", 0.2, status = "CANCELED"),
        )
        val s = ScanSettings(leagues = setOf("NCAAF"))
        assertEquals(listOf("held"), Planner.eligibleEvents(events, s, now).map { it.eventId })
        assertEquals(listOf("held"), Planner.eligibleEvents(events, s.copy(includeLive = true), now).map { it.eventId })
    }

    @Test
    fun `a scan asks Novig for every game (small) but only the window's markets (the big part)`() = runTest {
        val asked = ArrayList<Pair<Collection<String>, Long?>>()
        var marketsBefore: Long? = -1
        val source = object : NovigSource {
            override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> {
                asked += statuses to startsBefore
                return listOf(game("mon", 0.5), game("sat", 5.5))
            }
            override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> {
                marketsBefore = startsBefore
                return emptyList()
            }
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, 0, 0)
            override suspend fun market(marketId: String): NovigMarket? = null
        }
        val s = ScanSettings(leagues = setOf("NCAAF"), daysAhead = 3)
        val r = Scanner(source, clock = { now }).scan(s, emptyList(), emptySet(), {}, {})
        assertNull(asked.single().second)
        assertTrue(NovigEvent.STATUS_DELAYED in asked.single().first)
        assertEquals(now + 4 * day, marketsBefore)
        assertEquals(1, r.result!!.stats.laterGames)
    }

    // ---- Novig's `strike`: the documented line ------------------------------------------------------

    private val nfl = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)

    private fun market(id: String, type: String, strike: Double?, vararg outcomes: String) =
        NovigMarket(id, Fixtures.EVENT_ID, type, "OPEN", "", Fixtures.START_MS, MarketFee.GAME, outcomes.mapIndexed { i, n -> NovigOutcome("$id-$i", n, "TBD") }, strike)

    private val refs = listOf(
        RefSnapshot(
            "americanfootball_nfl",
            listOf(
                RefEvent(
                    "r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens",
                    markets = listOf(
                        RefBookMarket("pinnacle", "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1.9, 3.5), RefQuote(Side.AWAY, 1.95, -3.5)), Fixtures.START_MS),
                        RefBookMarket("pinnacle", "Pinnacle", LineKind.TOTAL, listOf(RefQuote(Side.OVER, 1.9, 47.5), RefQuote(Side.UNDER, 1.95, 47.5)), Fixtures.START_MS),
                    ),
                ),
            ),
            Fixtures.START_MS - 2 * day,
        ),
    )

    @Test
    fun `a line read from the names must be Novig's strike, a spread's on the home side, or it's skipped`() {
        val s = ScanSettings(leagues = setOf("NFL"))
        val markets = listOf(
            // Dallas (home) +3.5: strike 3.5 is the home side's. Priced.
            market("ok", "SPREAD", 3.5, "DAL +3.5", "BAL -3.5"),
            // Same names, but Novig says the home side is -3.5: the names were read the wrong way round.
            market("flip", "SPREAD", -3.5, "DAL +3.5", "BAL -3.5"),
            market("tot", "TOTAL", 47.5, "Over 47.5", "Under 47.5"),
            market("totBad", "TOTAL", 45.5, "Over 47.5", "Under 47.5"),
            // No strike sent: the names alone, as before.
            market("noStrike", "TOTAL", null, "Over 47.5", "Under 47.5"),
        )
        val planned = Planner.plan(listOf(nfl), markets, refs, s, Fixtures.START_MS - 2 * day).marketIds.toSet()
        assertEquals(setOf("ok", "tot", "noStrike"), planned)
    }
}
