package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.Presets
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07: "for the cno only scanner, right now I can't filter sports leagues at all. Make sure the cno scanner has plenty of filters just like vigilant scanner" (RESEARCH.md
 * §85): which games CNO's list looks at: leagues (17, by CNO's own labels), kinds of bet, pregame only, a liquidity floor, words in or out, props per game. Everything defaults to
 * "everything", and presets neither carry nor reset it.
 */
class CnoScopeTest {

    private val now = 1_000_000_000L

    /** A consistent CNO row in [league]: its EV is exactly what its fair probability gives at [odds]. */
    private fun row(
        bet: String, league: String = "NFL", market: String = "Player Receiving Yards", odds: Int = 120, fair: Double = 0.48, event: String = "A @ B",
        startsAt: Long? = now + 3_600_000, available: Double? = 80.0,
    ) = CnoRow(
        ev = fair * Odds.americanToDecimal(odds) - 1, startsAtMs = startsAt, league = league, event = event, market = market, bet = bet, odds = odds, available = available,
        book = "Novig", fairProbability = fair, books = 8, gameUrl = "https://x/g?side_id=$bet",
    )

    private fun screen(scope: CnoScope, vararg rows: CnoRow) =
        CnoChecks.screen(CnoSnapshot("u", rows.toList(), now, cnoAgeSeconds = 20), CnoFilters(scope = scope), now)

    // ---- the table: CNO's own dropdown ---------------------------------------------------------------------------------------------------------

    @Test
    fun `the table is CNO's live dropdown - 17 leagues, ids and sports as the page lists them, and a row's league cell is the label`() {
        assertEquals(17, CnoLeagues.ALL.size)
        assertEquals("labels are unique", 17, CnoLeagues.ALL.map { CnoLeagues.norm(it.label) }.toSet().size)
        assertEquals("ids are unique", 17, CnoLeagues.ALL.map { it.id }.toSet().size)
        val live = mapOf(
            "MLB" to 1, "NFL" to 2, "NCAAF" to 3, "NHL" to 4, "NBA" to 5, "NCAAB" to 6, "WNBA" to 7, "NCAAW" to 8, "FIFA World Cup" to 9, "MLS (USA)" to 10, "Serie A (Brazil)" to 11,
            "Liga MX (Mexico)" to 12, "LaLiga (Spain)" to 13, "Bundesliga (Germany)" to 14, "Ligue 1 (France)" to 15, "Premier League (England)" to 16, "Serie A (Italy)" to 17,
        )
        assertEquals(live, CnoLeagues.ALL.associate { it.label to it.id })
        assertEquals(listOf("NFL", "NCAAF"), CnoLeagues.of(CnoLeagues.Sport.FOOTBALL).map { it.label })
        assertEquals(setOf("WNBA", "NBA", "NCAAB", "NCAAW"), CnoLeagues.of(CnoLeagues.Sport.BASKETBALL).map { it.label }.toSet())
        assertEquals(9, CnoLeagues.of(CnoLeagues.Sport.SOCCER).size)
        assertEquals(mapOf("Football" to 2, "Basketball" to 4, "Baseball" to 1, "Hockey" to 3, "Soccer" to 5), CnoLeagues.Sport.entries.associate { it.label to it.id })
        // The old link-description table (CnoView) agrees with the live ids.
        for (l in CnoLeagues.ALL) assertNotNull(l.label, CnoLeagues.byLabel(l.label))
        assertNull("a league CNO doesn't list", CnoLeagues.byLabel("ATP"))
        assertEquals("Serie A (Italy) is not Serie A (Brazil)", false, CnoLeagues.same("Serie A (Italy)", "Serie A (Brazil)"))
        assertTrue(CnoLeagues.same("  mls  (usa) ", "MLS (USA)"))
    }

    // ---- the picks -------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `nothing picked is every league, and a pick keeps only its leagues - exactly as CNO spells them`() {
        val all = CnoScope()
        assertTrue(all.isDefault && all.allows("NHL") && all.allows("Some New League"))
        val nhl = all.toggleLeague("NHL")
        assertEquals(setOf("NHL"), nhl.leagues)
        assertTrue(nhl.allows("NHL") && nhl.allows("nhl "))
        assertFalse(nhl.allows("NFL"))
        val two = nhl.toggleLeague("MLS (USA)")
        assertTrue(two.allows("MLS (USA)") && !two.allows("Premier League (England)"))
        // Un-ticking the last returns to "all", never to "none".
        assertTrue(two.toggleLeague("NHL").toggleLeague("MLS (USA)").isDefault)
    }

    @Test
    fun `picking every league collapses to all, and a sport's heading ticks and clears its leagues`() {
        var s = CnoScope()
        for (l in CnoLeagues.ALL) s = s.toggleLeague(l.label)
        assertTrue("all 17 is the same as none picked", s.leagues.isEmpty())
        val football = CnoScope().toggleSport(CnoLeagues.Sport.FOOTBALL)
        assertEquals(setOf("NFL", "NCAAF"), football.leagues)
        assertTrue("again clears them", football.toggleSport(CnoLeagues.Sport.FOOTBALL).isDefault)
        val mixed = football.toggleLeague("NHL").toggleSport(CnoLeagues.Sport.FOOTBALL)
        assertEquals("football cleared, hockey stays", setOf("NHL"), mixed.leagues)
        // A saved label CNO no longer lists is kept (shown as a chip) so it can be removed.
        val old = CnoScope(leagues = setOf("Old League"))
        assertEquals(listOf("Old League"), old.pickedLabels())
        assertTrue(old.toggleLeague("Old League").isDefault)
    }

    // ---- the screen ------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `a row outside the picked leagues is left out and counted as LEAGUE, before any price rule`() {
        val nfl = row("Juwan Johnson Under 39.5"); val nhl = row("Leon Draisaitl Over 3.5", league = "NHL", market = "Player Shots On Goal")
        val s = screen(CnoScope(leagues = setOf("NHL")), nfl, nhl)
        assertEquals(listOf("Leon Draisaitl Over 3.5"), s.picks.map { it.row.bet })
        assertEquals(mapOf(CnoChecks.Reason.LEAGUE to 1), s.hidden)
        // The scan study's per-row reason says the same.
        assertEquals(CnoChecks.Reason.LEAGUE, CnoChecks.rejection(nfl, CnoFilters(scope = CnoScope(leagues = setOf("NHL"))), now))
        assertNull(CnoChecks.rejection(nhl, CnoFilters(scope = CnoScope(leagues = setOf("NHL"))), now))
        // No league picked lists every league, a league CNO adds later too.
        assertEquals(3, screen(CnoScope(), nfl, nhl, row("X", league = "Brand New League")).picks.size)
    }

    @Test
    fun `kinds of bet - only the picked kinds are listed, none picked is every kind`() {
        val prop = row("Juwan Johnson Under 39.5"); val spread = row("Ohio -33.5", league = "NCAAF", market = "Point Spread")
        assertEquals(listOf("Ohio -33.5"), screen(CnoScope(kinds = setOf(BetKind.SPREAD)), prop, spread).picks.map { it.row.bet })
        assertEquals(mapOf(CnoChecks.Reason.KIND to 1), screen(CnoScope(kinds = setOf(BetKind.SPREAD)), prop, spread).hidden)
        assertEquals(2, screen(CnoScope(), prop, spread).picks.size)
        // Every kind picked collapses to none.
        var s = CnoScope(); for (k in BetKind.entries) s = s.toggleKind(k)
        assertTrue(s.kinds.isEmpty())
    }

    @Test
    fun `hide live leaves out games already under way, liquidity leaves out thin bets, a row with no dollars shown stays`() {
        val pre = row("Pre", startsAt = now + 3_600_000); val live = row("Live", startsAt = now - 60_000, odds = 120, fair = 0.50)
        assertEquals(listOf("Pre"), screen(CnoScope(hideLive = true), pre, live).picks.map { it.row.bet })
        assertEquals(mapOf(CnoChecks.Reason.LIVE to 1), screen(CnoScope(hideLive = true), pre, live).hidden)
        assertEquals("live rows are listed when it is off, as today", 2, screen(CnoScope(), pre, live).picks.size)
        val thin = row("Thin", available = 12.0); val deep = row("Deep", available = 150.0); val unknown = row("Unknown", available = null)
        val s = screen(CnoScope(minLiquidity = 50), thin, deep, unknown)
        assertEquals(listOf("Deep", "Unknown"), s.picks.map { it.row.bet }.sorted())
        assertEquals(mapOf(CnoChecks.Reason.LIQUIDITY to 1), s.hidden)
    }

    @Test
    fun `words - include keeps bets with any term, exclude drops bets with any, case and accents ignored`() {
        val a = row("José Ramírez Over 1.5", league = "MLB", market = "Player Hits", event = "Cleveland Guardians @ Detroit Tigers")
        val b = row("Tarik Skubal Over 6.5", league = "MLB", market = "Pitcher Strikeouts", event = "Cleveland Guardians @ Detroit Tigers")
        val c = row("Aaron Judge Over 1.5", league = "MLB", market = "Player Hits", event = "Yankees @ Red Sox")
        assertEquals(listOf("José Ramírez Over 1.5"), screen(CnoScope(include = "ramirez"), a, b, c).picks.map { it.row.bet })
        assertEquals(listOf("Aaron Judge Over 1.5", "José Ramírez Over 1.5"), screen(CnoScope(include = "RAMIREZ, judge"), a, b, c).picks.map { it.row.bet }.sorted())
        assertEquals("Tarik Skubal Over 6.5", screen(CnoScope(exclude = "hits"), a, b, c).picks.single().row.bet)
        assertEquals("a team name is in the event", listOf("Aaron Judge Over 1.5"), screen(CnoScope(include = "yankees"), a, b, c).picks.map { it.row.bet })
        assertEquals(mapOf(CnoChecks.Reason.TEXT to 2), screen(CnoScope(include = "ramirez"), a, b, c).hidden)
        assertEquals("blank means no filter", 3, screen(CnoScope(include = " , ", exclude = ""), a, b, c).picks.size)
    }

    @Test
    fun `props per game keeps the best edges of each game, game lines are not counted, and 0 is no cap`() {
        val game = "Chiefs @ Bills"
        val p1 = row("P1", fair = 0.52, event = game); val p2 = row("P2", fair = 0.50, event = game); val p3 = row("P3", fair = 0.49, event = game)
        val other = row("Q1", fair = 0.49, event = "Jets @ Lions"); val line = row("Bills -3.5", market = "Point Spread", fair = 0.49, event = game)
        val s = screen(CnoScope(propsPerGame = 2), p1, p2, p3, other, line)
        assertEquals(setOf("P1", "P2", "Q1", "Bills -3.5"), s.picks.map { it.row.bet }.toSet())
        assertEquals(mapOf(CnoChecks.Reason.PROPS to 1), s.hidden)
        assertEquals(5, screen(CnoScope(), p1, p2, p3, other, line).picks.size)
    }

    // ---- what CNO is asked ------------------------------------------------------------------------------------------------------------------------

    private val ids = { l: String -> CnoLeagues.byLabel(l)?.id }
    private val sports = { s: CnoLeagues.Sport -> s.id }

    @Test
    fun `nothing picked asks CNO for nothing new and the same rows`() {
        val p = CnoScope().plan(50, ids, sports)
        assertEquals(CnoScope.Plan(null, null, 50, false), p)
    }

    @Test
    fun `one league is posted to CNO exactly, so its row limit is spent on that league`() {
        assertEquals(CnoScope.Plan(4, null, 50, false), CnoScope(leagues = setOf("NHL")).plan(50, ids, sports))
        // The page's own option list wins over the built-in table, and a league the page does not list cannot be posted: the app filters, with more rows.
        assertEquals(CnoScope.Plan(99, null, 50, false), CnoScope(leagues = setOf("NHL")).plan(50, { 99 }, sports))
        assertEquals(CnoScope.Plan(null, null, 300, true), CnoScope(leagues = setOf("NHL")).plan(50, { null }, sports))
    }

    @Test
    fun `every league of one sport posts the sport, some of one sport's leagues post it and the app does the rest, several sports are the app's with a wider read`() {
        assertEquals(CnoScope.Plan(null, 2, 50, false), CnoScope(leagues = setOf("NFL", "NCAAF")).plan(50, ids, sports))
        assertEquals(CnoScope.Plan(null, 4, 300, true), CnoScope(leagues = setOf("NBA", "NCAAB")).plan(50, ids, sports))
        assertEquals(CnoScope.Plan(null, null, 300, true), CnoScope(leagues = setOf("NHL", "WNBA")).plan(50, ids, sports))
        // A bigger row limit of Tj's stays when widened.
        assertEquals(500, CnoScope(leagues = setOf("NHL", "WNBA")).plan(500, ids, sports).askRows)
    }

    @Test
    fun `app-side filters widen the read too, because CNO cuts at its row limit before the app drops anything`() {
        for (scope in listOf(CnoScope(kinds = setOf(BetKind.PROP)), CnoScope(hideLive = true), CnoScope(include = "x"), CnoScope(exclude = "x"), CnoScope(propsPerGame = 2))) {
            val p = scope.plan(50, ids, sports)
            assertTrue(scope.toString(), p.appFilters && p.askRows == 300)
        }
        assertFalse("a liquidity floor is posted to CNO, not the app's alone", CnoScope(minLiquidity = 50).appOnly)
        assertEquals(50, CnoScope(minLiquidity = 50).plan(50, ids, sports).askRows)
    }

    // ---- saved files and presets -----------------------------------------------------------------------------------------------------------------

    @Test
    fun `an old settings file with no scope decodes to the default, and a scope round-trips`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"devig":"CONSERVATIVE","maxOdds":150,"minBooks":4,"minEv":0.01,"rows":50,"completeBook":true,"minSides":2}"""
        assertEquals(CnoFilters(), json.decodeFromString(CnoFilters.serializer(), old))
        val scope = CnoScope(leagues = setOf("NHL", "MLS (USA)"), kinds = setOf(BetKind.PROP), hideLive = true, minLiquidity = 25, include = "a, b", exclude = "c", propsPerGame = 3)
        val f = CnoFilters(scope = scope)
        assertEquals(f, json.decodeFromString(CnoFilters.serializer(), json.encodeToString(CnoFilters.serializer(), f)))
        // A snapshot saved by an older build (filters without a scope) still equals the default filters: no re-read at the upgrade.
        assertEquals(CnoFilters(), json.decodeFromString(CnoFilters.serializer(), old))
    }

    @Test
    fun `a preset neither carries nor resets the scope - applying one keeps it, saving one stores none, matches stays true`() {
        val mine = ScanSettings(cnoFilters = CnoFilters(scope = CnoScope(leagues = setOf("NHL"), hideLive = true)))
        for (preset in Presets.BUILT_IN) {
            val applied = Presets.apply(mine, preset)
            assertEquals(CnoScope(leagues = setOf("NHL"), hideLive = true), applied.cnoFilters.scope)
            assertEquals(preset.rules.cnoFilters.copy(scope = CnoScope()), applied.cnoFilters.copy(scope = CnoScope()))
            assertTrue("still the preset's rules after picking a league", Presets.matches(applied, preset))
            assertNotNull(Presets.active(applied))
        }
        val saved = Presets.save(mine, "My rules")!!
        assertTrue(saved.presets.single().rules.cnoFilters.scope.isDefault)
        assertTrue("a preset saved with the default scope matches a user who then picks a league", Presets.matches(Presets.apply(mine, saved.presets.single()), saved.presets.single()))
        assertTrue(CnoScope().summary().isEmpty())
        assertEquals("NHL, MLS (USA) · prop bets only".replace("prop bets only", "player props") + " · pregame only", CnoScope(leagues = setOf("NHL", "MLS (USA)"), kinds = setOf(BetKind.PROP), hideLive = true).summary())
    }
}
