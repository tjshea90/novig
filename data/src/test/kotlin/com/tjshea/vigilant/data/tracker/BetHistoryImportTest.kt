package com.tjshea.vigilant.data.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class BetHistoryImportTest {
    private fun line(id: String, result: String, outcome: String = "o1", placed: Long = 1_000_000L, contracts: Long = 100) =
        """{"id":"$id","placed":"x","placedMs":$placed,"day":"2026-10-10","made":"bet","how":"auto","scanner":"Vigilant","league":"NBA","kind":"Game totals","market":"m","event":"A @ B","selection":"Over 5","starts":"x","startsMs":${placed + 3_600_000},"cost":0.5,"stake":0.5,"contracts":$contracts,"paid":0.5,"fee":0.0,"result":"$result","marketId":"m1","outcomeId":"$outcome","ev":0.04,"fair":0.54}"""

    @Test fun settledRowsComeBackAndOpenOnesDoNot() {
        val rows = BetHistoryImport.parse(listOf(line("a1", "won"), line("a2", "open", "o2"), line("a3", "lost", "o3"), "garbage").joinToString("\n"))
        val got = BetHistoryImport.missing(rows, emptyList())
        assertEquals(listOf("a1", "a3"), got.map { it.id })
        assertEquals(BetStatus.WON, got[0].status)
        assertEquals(0.5, got[0].profit!!, 1e-9)
        assertEquals(-0.5, got[1].profit!!, 1e-9)
    }

    @Test fun aBetTheTrackerHasIsNotAddedTwice() {
        val rows = BetHistoryImport.parse(line("a1", "won") + "\n" + line("a2", "won", "o2"))
        val have = BetHistoryImport.toTracked(BetHistory.json.decodeFromString<BetHistory.Row>(line("zzz", "won", "o2", placed = 1_000_000L + 60_000L)))!!.copy(id = "tablet-1")
        val got = BetHistoryImport.missing(rows, listOf(have))
        assertEquals(listOf("a1"), got.map { it.id })
        assertEquals(emptyList(), BetHistoryImport.missing(rows, got + have).map { it.id })
    }
}
