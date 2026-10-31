package com.tjshea.vigilant.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MajorBooksTest {
    @Test fun countsCompaniesOnce() {
        assertEquals(2, MajorBooks.count(listOf("DraftKings", "fanduel", "FanDuel YourWay", "smallbook")))
        assertEquals(0, MajorBooks.count(listOf("tinybet", "obscureodds")))
    }
    @Test fun reasonWhenShort() {
        assertNotNull(MajorBooks.reason(1, 2)); assertNull(MajorBooks.reason(2, 2)); assertNull(MajorBooks.reason(1, 1))
    }
}
