package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-11: "Make the same switch for sgo. And any other API with a ton of sports books": every book for the fair average, for SportsGameOdds, PropLine and OddsPapi. */
class EveryBookTest {
    @Test fun sgoEveryBookIsEverythingItListsExceptTheBooksThatAreNeverTheFairLine() {
        val all = SgoBooks.everyBook()
        assertTrue(all.size > 60)
        for (never in listOf("novig", "kalshi", "polymarket", "prizepicks", "underdog")) assertFalse(never, never in all)
        for (book in listOf("pinnacle", "draftkings", "fanduel", "bet365", "circa", "betmgm")) assertTrue(book, book in all)
        assertEquals("the picked list is much smaller", true, SgoBooks.wanted(ScanSettings().referenceBooks, true).size < all.size / 2)
        assertTrue("every book it sends is a bookmakerID SGO knows, so no filter can 400", SgoBooks.filter(all).split(",").size == all.size)
    }

    @Test fun propLineEveryBookIsItsWholeListNotThePickedOnes() {
        val picked = PropLineClient.books(ScanSettings(referenceBooks = listOf("draftkings", "fanduel")))
        val all = PropLineClient.books(ScanSettings(referenceBooks = listOf("draftkings", "fanduel"), gates = ApiGates(propLineAllBooks = true)))
        assertEquals(listOf("draftkings", "fanduel"), picked.sorted())
        assertTrue(all.size > 15)
        for (never in listOf("novig", "kalshi", "polymarket", "prophetx", "prizepicks", "underdog")) assertFalse(never, never in all)
        assertTrue("draftkings" in all && "fanduel" in all)
    }

    @Test fun oddsPapiEveryBookFoldsRegionalClonesAndDropsTheExcludedOnes() {
        val cat = listOf("bet365", "bet365.de", "bet365.es", "betmgm", "betmgm.co.uk", "novig.us", "kalshi", "obscurebook", "bovada.lv").map { OpBookInfo(it, it, null, null) }
        assertEquals(setOf("bet365", "betmgm", "obscurebook", "bovada"), OpBooks.everyBook(cat))
    }
}
