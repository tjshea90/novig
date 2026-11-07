package com.tjshea.vigilant.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReputableBooksTest {
    @Test fun smallSoftBooksAreBanned() {
        listOf("fliff", "Sportzino", "sportybet_ng", "betus", "mybookieag", "tonybet").forEach { assertTrue(ReputableBooks.isBanned(it), it) }
    }
    @Test fun reputableBooksAreKept() {
        listOf("pinnacle", "draftkings", "fanduel", "betmgm", "williamhill_us", "bovada", "betonlineag", "circa", "bookmaker_eu", "heritage", "tipico", "winamax", "novibet", "sazka").forEach { assertFalse(ReputableBooks.isBanned(it), it) }
    }
    @Test fun bannedBookDoesNotMoveTheFair() {
        fun bp(k: String, home: Double, away: Double) = BookPrices(k, k, listOf(home, away))
        val s = FairSettings()
        val base = FairValue.compute(listOf(bp("pinnacle", 1.9, 1.95), bp("draftkings", 1.91, 1.91), bp("fanduel", 1.9, 1.92)), s)!!
        val withBad = FairValue.compute(listOf(bp("pinnacle", 1.9, 1.95), bp("draftkings", 1.91, 1.91), bp("fanduel", 1.9, 1.92), bp("fliff", 2.3, 1.6)), s)!!
        assertEquals(base.fairProbabilities, withBad.fairProbabilities)
        assertEquals(base.perBook.size, withBad.perBook.size)
    }
}
