package com.tjshea.vigilant.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class MajorBooksTest {
    @Test fun countsCompaniesOnce() {
        assertEquals(2, MajorBooks.count(listOf("DraftKings", "fanduel", "FanDuel YourWay", "smallbook")))
        assertEquals(0, MajorBooks.count(listOf("tinybet", "obscureodds")))
    }
    @Test fun reasonWhenShort() {
        assertNotNull(MajorBooks.reason(1, 2)); assertNull(MajorBooks.reason(2, 2)); assertNull(MajorBooks.reason(1, 1))
    }
}
