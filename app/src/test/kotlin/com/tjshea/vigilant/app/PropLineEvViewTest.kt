package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.PropLineEvView
import com.tjshea.vigilant.data.reference.PlEvRow
import org.junit.Assert.assertEquals
import org.junit.Test

class PropLineEvViewTest {
    private val now = 1_000_000_000_000L
    private fun row(league: String = "NBA", player: String = "Tyrese Maxey", market: String = "player_points", ev: Double = 3.0, starts: Long = now + 3_600_000L, point: Double? = 24.5, outcome: String = "Over") =
        PlEvRow("basketball_nba", league, "1", "76ers", "Celtics", starts, market, player, point, outcome, -105, ev, 0.51, "pinnacle", now - 60_000L)

    @Test
    fun filtersSortsAndDropsStartedGames() {
        val rows = listOf(row(ev = 2.0, outcome = "Yes"), row(ev = 6.0, player = "A", outcome = "Yes"), row(ev = 4.0, league = "NHL", player = "B", outcome = "Yes"), row(ev = 9.0, starts = now - 1, outcome = "Yes"), row(ev = 0.6, player = "", market = "totals", outcome = "Over", point = 220.5))
        assertEquals(listOf(6.0, 4.0, 2.0), PropLineEvView.select(rows, "All", 1.0, "All", now).map { it.evPct })
        assertEquals(listOf(6.0, 2.0), PropLineEvView.select(rows, "NBA", 1.0, "All", now).map { it.evPct })
        assertEquals(listOf(0.6), PropLineEvView.select(rows, "All", 0.5, "Game lines", now).map { it.evPct })
        assertEquals(3, PropLineEvView.select(rows, "All", 0.5, "Props", now).size)
    }

    @Test
    fun longshotsAreCutByTheOddsLimit() {
        val rows = listOf(row(ev = 28.0, player = "A", outcome = "Yes").copy(price = 1941), row(ev = 5.0, player = "B", outcome = "Yes").copy(price = 250), row(ev = 4.0, player = "C", outcome = "Yes").copy(price = -120))
        assertEquals(listOf(5.0, 4.0), PropLineEvView.select(rows, "All", 0.5, "All", now, 300).map { it.evPct })
        assertEquals(listOf(4.0), PropLineEvView.select(rows, "All", 0.5, "All", now, 150).map { it.evPct })
        assertEquals(3, PropLineEvView.select(rows, "All", 0.5, "All", now).size)
    }

    @Test
    fun aPropOverIsRatedAfterVigilantsOwnCut() {
        // PropLine +7% on an Over at -105 with a 51% fair: 44% after the 7-point cut is a losing bet; an Under loses only 3 points; a game line is not cut.
        val over = row(ev = 7.0)
        assertEquals(true, PropLineEvView.calibratedEv(over) < 0)
        assertEquals(0.51 * (1 + 100 / 105.0) * 100 - 100 - 3 * (1 + 100 / 105.0), PropLineEvView.calibratedEv(row(ev = 7.0, outcome = "Under")), 1e-9)
        assertEquals(0.6, PropLineEvView.calibratedEv(row(ev = 0.6, player = "", market = "totals", point = 220.5)), 1e-9)
        assertEquals(emptyList<Double>(), PropLineEvView.select(listOf(over), "All", 0.5, "All", now).map { it.evPct })
    }

    @Test
    fun picksAreReadable() {
        assertEquals("Tyrese Maxey Over 24.5 · Points", PropLineEvView.pick(row()))
        assertEquals("Over 220.5 · Totals", PropLineEvView.pick(row(player = "", market = "totals", point = 220.5)))
        assertEquals("Celtics -3 · Spreads", PropLineEvView.pick(row(player = "", market = "spreads", outcome = "Celtics", point = -3.0)))
        assertEquals("in 60 min", PropLineEvView.starts(row(), now))
    }

    @Test
    fun theVerdictSaysHowManyBooksBackIt() {
        val base = row()
        assertEquals(PropLineEvView.Level.NONE, PropLineEvView.verdict(base).level)
        val some = base.copy(others = listOf(com.tjshea.vigilant.data.reference.PlBookPrice("draftkings", -108, now)))
        assertEquals(PropLineEvView.Level.WARN, PropLineEvView.verdict(some.copy(twoSided = 0)).level)
        assertEquals(PropLineEvView.Level.GOOD, PropLineEvView.verdict(some.copy(twoSided = 8, agree = 6, pinnacleAgrees = true)).level)
        assertEquals("a sharp book against it is never GOOD", PropLineEvView.Level.WARN, PropLineEvView.verdict(some.copy(twoSided = 8, agree = 6, pinnacleAgrees = false)).level)
        assertEquals(PropLineEvView.Level.BAD, PropLineEvView.verdict(some.copy(twoSided = 9, agree = 1, pinnacleAgrees = false)).level)
        assertEquals("7 of 9 books that price both sides say Novig's price is +EV. Pinnacle agrees.", PropLineEvView.verdict(some.copy(twoSided = 9, agree = 7, pinnacleAgrees = true)).text)
    }

    @Test
    fun bookNamesAndOldTags() {
        assertEquals("Hard Rock", PropLineEvView.bookName("hardrock")); assertEquals("Polymarket Us", PropLineEvView.bookName("polymarket_us"))
        assertEquals("", PropLineEvView.oldTag(com.tjshea.vigilant.data.reference.PlBookPrice("x", 100, now - 5 * 60_000L), now))
        assertEquals(" (25 min old)", PropLineEvView.oldTag(com.tjshea.vigilant.data.reference.PlBookPrice("x", 100, now - 25 * 60_000L), now))
        assertEquals(null, PropLineEvView.opportunity(row()))
        assertEquals(null, PropLineEvView.opportunity(row().copy(opp = "not json")))
    }

    @Test
    fun theEngineMatchesARowToTheSameNovigBetAndThePhoneDecodesIt() {
        val all = SampleScan.state().result!!.opportunities
        // a game total of the sample scan: the PropLine row for the same game, line and side
        val o = all.first { it.kind == com.tjshea.vigilant.data.reference.LineKind.TOTAL && it.quote != null && it.lineKey?.period == 0 }
        val teams = o.event.description.split(" @ ")
        val side = (o.target as com.tjshea.vigilant.data.scanner.OutcomeTarget.Is).side
        val r = PlEvRow("x", o.league.displayName, "1", teams[1], teams[0], o.event.startsTs, "totals", "", o.lineKey!!.line, if (side == com.tjshea.vigilant.data.reference.Side.OVER) "Over" else "Under", -105, 3.0, 0.52, "pinnacle", now)
        val found = com.tjshea.vigilant.data.reference.PropLineMatch.find(r, all)
        assertEquals(o.key, found?.key)
        val withOpp = com.tjshea.vigilant.data.reference.PropLineMatch.attach(r, all)
        assertEquals(o.key, PropLineEvView.opportunity(withOpp)?.key)
        // a different line is a different bet
        assertEquals(null, com.tjshea.vigilant.data.reference.PropLineMatch.find(r.copy(point = o.lineKey!!.line!! + 0.5), all))
    }
}
