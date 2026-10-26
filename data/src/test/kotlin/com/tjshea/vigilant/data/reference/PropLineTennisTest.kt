package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PropLine's tennis board (PX11e), shaped as the real answer of 2026-10-26 (propline.yml task `shape`): one `tennis` key, `tour`/`tournament` per match, sets and games in one event. */
class PropLineTennisTest {
    private fun mkt(key: String, line: String, a: String, b: String, pa: Double?, pb: Double?) =
        """{"key":"$key","description":"","last_update":"2026-10-26T01:00:00Z","period":null,"team":null,"line_type":"$line","outcomes":[{"name":"$a","price":-110${pa?.let { ",\"point\":$it" } ?: ""}},{"name":"$b","price":-110${pb?.let { ",\"point\":$it" } ?: ""}}]}"""
    private fun ev(id: String, tour: String, tournament: String, home: String, away: String, books: String) =
        """{"id":"$id","sport_key":"tennis","tour":"$tour","tournament":"$tournament","home_team":"$home","away_team":"$away","commence_time":"2026-10-26T12:00:00Z","bookmakers":[$books]}"""
    private fun book(key: String, vararg m: String) = """{"key":"$key","title":"$key","last_update":"2026-10-26T01:00:00Z","markets":[${m.joinToString(",")}]}"""

    private val board = "[" + listOf(
        // Pinnacle: a main SETS spread (-1.5) beside a games total, a sets total (2.5), games alternates; BetMGM: games lines
        ev("1", "ATP", "ATP Basel", "Bruno Kokot", "Chris Norlin", book("pinnacle",
            mkt("h2h", "main", "Bruno Kokot", "Chris Norlin", null, null), mkt("spreads", "main", "Bruno Kokot", "Chris Norlin", -1.5, 1.5),
            mkt("totals", "main", "Over", "Under", 2.5, 2.5), mkt("totals", "alternate", "Over", "Under", 22.5, 22.5), mkt("spreads", "alternate", "Bruno Kokot", "Chris Norlin", -3.5, 3.5),
        ) + "," + book("betmgm", mkt("spreads", "main", "Bruno Kokot", "Chris Norlin", 1.5, -1.5), mkt("totals", "main", "Over", "Under", 21.5, 21.5))),
        ev("2", "WTA", "WTA Tokyo", "Ana One", "Bea Two", book("pinnacle", mkt("h2h", "main", "Ana One", "Bea Two", null, null))),
        ev("3", "ITF", "ITF Women Qual. Glasgow", "Cat Three", "Dee Four", book("fanduel", mkt("h2h", "main", "Cat Three", "Dee Four", null, null))),
        ev("4", "ITF", "ITF Men Qual. Sheffield", "Ed Five", "Fin Six", book("fanduel", mkt("h2h", "main", "Ed Five", "Fin Six", null, null))),
        ev("5", "UTR", "UTR Pro Series (M)", "Gus Seven", "Hal Eight", book("fanduel", mkt("h2h", "main", "Gus Seven", "Hal Eight", null, null))),
        ev("6", "ATP", "ATP Doubles Vienna", "A One / B Two", "C Three / D Four", book("fanduel", mkt("h2h", "main", "A One / B Two", "C Three / D Four", null, null))),
        ev("7", "Challenger", "ATP Brest Challenger", "Ivo Nine", "Jon Ten", book("fanduel", mkt("h2h", "main", "Ivo Nine", "Jon Ten", null, null))),
    ).joinToString(",") + "]"

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the one tennis board is split by tour, doubles and UTR left out`() {
        val atp = PropLineClient.parseBoard(board, json, "tennis_atp").events
        val wta = PropLineClient.parseBoard(board, json, "tennis_wta").events
        assertEquals(setOf("Bruno Kokot", "Ed Five", "Ivo Nine"), atp.map { it.home }.toSet())
        assertEquals(setOf("Ana One", "Cat Three"), wta.map { it.home }.toSet())
        assertTrue(atp.all { it.sportKey == "tennis_atp" })
    }

    @Test
    fun `a total under 6 is sets, Pinnacle's spreads within 2_5 are dropped, the other books' spreads are games`() {
        val e = PropLineClient.parseBoard(board, json, "tennis_atp").events.first { it.home == "Bruno Kokot" }
        val pin = e.markets.filter { it.bookKey == "pinnacle" }
        assertEquals(RefBookMarket.PERIOD_SETS, pin.single { it.kind == LineKind.TOTAL && it.line == 2.5 }.period)
        assertEquals(0, pin.single { it.kind == LineKind.TOTAL && it.line == 22.5 }.period)
        assertEquals("Pinnacle's -1.5 could be sets: left out; -3.5 can only be games", listOf(3.5), pin.filter { it.kind == LineKind.SPREAD }.map { kotlin.math.abs(it.line!!) })
        assertEquals(1, pin.count { it.kind == LineKind.MONEYLINE })
        val mgm = e.markets.filter { it.bookKey == "betmgm" }
        assertEquals("BetMGM's ±1.5 is games", 0, mgm.single { it.kind == LineKind.SPREAD }.period)
    }

    @Test
    fun `the tour map`() {
        assertEquals("tennis_atp", PropLineTennis.leagueOf("Challenger", "ATP Challenger Seoul"))
        assertEquals("tennis_wta", PropLineTennis.leagueOf("ITF", "ITF W15 Essen GER Q"))
        assertEquals("tennis_atp", PropLineTennis.leagueOf("ITF", "ITF M25 Sheffield"))
        assertNull(PropLineTennis.leagueOf("UTR", "UTR Pro Series (M)"))
        assertNull(PropLineTennis.leagueOf(null, null))
    }
}
