package com.tjshea.vigilant.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ReputableBooksTest {
    @Test fun smallSoftBooksAreBanned() {
        listOf("fliff", "Sportzino", "sportybet_ng", "betus", "mybookieag", "tonybet").forEach { assertTrue(it, ReputableBooks.isBanned(it)) }
    }
    @Test fun reputableBooksAreKept() {
        listOf("pinnacle", "draftkings", "fanduel", "betmgm", "williamhill_us", "bovada", "betonlineag", "circa", "bookmaker_eu", "heritage", "tipico", "winamax", "novibet", "sazka").forEach { assertFalse(it, ReputableBooks.isBanned(it)) }
    }
    @Test fun bannedBookDoesNotMoveTheFair() {
        fun bp(k: String, home: Double, away: Double) = BookPrices(k, k, listOf(home, away))
        val s = FairSettings()
        val base = FairValue.compute(listOf(bp("pinnacle", 1.9, 1.95), bp("draftkings", 1.91, 1.91), bp("fanduel", 1.9, 1.92)), s)!!
        val withBad = FairValue.compute(listOf(bp("pinnacle", 1.9, 1.95), bp("draftkings", 1.91, 1.91), bp("fanduel", 1.9, 1.92), bp("fliff", 2.3, 1.6)), s)!!
        assertEquals(base.probabilities, withBad.probabilities)
        assertEquals(base.perBook.size, withBad.perBook.size)
    }
}
