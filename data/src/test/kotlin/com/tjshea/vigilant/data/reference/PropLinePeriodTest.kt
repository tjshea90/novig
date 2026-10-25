package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS9 (2026-10-25): PropLine's `?period=h1` board becomes 1st-half lines (period 1); the full-game read never takes a period market, and a period read never takes a full-game one or a prop. */
class PropLinePeriodTest {
    private val seen = "2026-10-25T15:00:00Z"
    private val board = """
    [{"id":"555","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens","commence_time":"2026-10-25T20:25:00Z","is_outright":false,
      "bookmakers":[
        {"key":"draftkings","title":"DraftKings","last_update":"$seen","markets":[
          {"key":"spreads","last_update":"$seen","period":null,"outcomes":[
            {"name":"Dallas Cowboys","price":-110,"point":-3.5,"side":"home","last_seen_at":"$seen"},
            {"name":"Baltimore Ravens","price":-110,"point":3.5,"side":"away","last_seen_at":"$seen"}]},
          {"key":"spreads","last_update":"$seen","period":"h1","outcomes":[
            {"name":"Dallas Cowboys","price":-115,"point":-1.5,"side":"home","last_seen_at":"$seen"},
            {"name":"Baltimore Ravens","price":-105,"point":1.5,"side":"away","last_seen_at":"$seen"}]},
          {"key":"totals","last_update":"$seen","period":"h1","team":null,"outcomes":[
            {"name":"Over","price":-110,"point":24.5,"last_seen_at":"$seen"},
            {"name":"Under","price":-110,"point":24.5,"last_seen_at":"$seen"}]},
          {"key":"totals","last_update":"$seen","period":"q1","team":null,"outcomes":[
            {"name":"Over","price":-110,"point":10.5,"last_seen_at":"$seen"},
            {"name":"Under","price":-110,"point":10.5,"last_seen_at":"$seen"}]}]}
      ]}]
    """.trimIndent()

    @Test
    fun `the h1 read keeps only the 1st half, marked period 1`() {
        val e = PropLineClient.parseBoard(board, Json, "americanfootball_nfl", "h1").events.single()
        assertEquals(2, e.markets.size)
        assertTrue(e.markets.all { it.period == 1 })
        assertEquals(setOf(LineKind.SPREAD, LineKind.TOTAL), e.markets.map { it.kind }.toSet())
        assertEquals(24.5, e.markets.first { it.kind == LineKind.TOTAL }.quotes[0].point!!, 1e-9)
    }

    @Test
    fun `the full-game read keeps only the full game`() {
        val e = PropLineClient.parseBoard(board, Json, "americanfootball_nfl").events.single()
        assertEquals(1, e.markets.size)
        assertEquals(0, e.markets.single().period)
        assertEquals(-3.5, e.markets.single().quotes.first { it.side == Side.HOME }.point!!, 1e-9)
    }

    @Test
    fun `the period source names the halves Novig lists`() {
        assertEquals("h1", PropLinePeriodSource.PERIODS["basketball_nba"])
        assertEquals("f5", PropLinePeriodSource.PERIODS["baseball_mlb"])
        assertEquals(ParlayPeriodSource.PERIODS.keys, PropLinePeriodSource.PERIODS.keys)
    }
}
