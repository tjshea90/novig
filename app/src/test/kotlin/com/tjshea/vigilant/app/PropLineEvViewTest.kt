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
        val rows = listOf(row(ev = 2.0), row(ev = 6.0, player = "A"), row(ev = 4.0, league = "NHL", player = "B"), row(ev = 9.0, starts = now - 1), row(ev = 0.6, player = "", market = "totals", outcome = "Over", point = 220.5))
        assertEquals(listOf(6.0, 4.0, 2.0), PropLineEvView.select(rows, "All", 1.0, "All", now).map { it.evPct })
        assertEquals(listOf(6.0, 2.0), PropLineEvView.select(rows, "NBA", 1.0, "All", now).map { it.evPct })
        assertEquals(listOf(0.6), PropLineEvView.select(rows, "All", 0.5, "Game lines", now).map { it.evPct })
        assertEquals(3, PropLineEvView.select(rows, "All", 0.5, "Props", now).size)
    }

    @Test
    fun picksAreReadable() {
        assertEquals("Tyrese Maxey Over 24.5 · Points", PropLineEvView.pick(row()))
        assertEquals("Over 220.5 · Totals", PropLineEvView.pick(row(player = "", market = "totals", point = 220.5)))
        assertEquals("Celtics -3 · Spreads", PropLineEvView.pick(row(player = "", market = "spreads", outcome = "Celtics", point = -3.0)))
        assertEquals("in 60 min", PropLineEvView.starts(row(), now))
    }
}
