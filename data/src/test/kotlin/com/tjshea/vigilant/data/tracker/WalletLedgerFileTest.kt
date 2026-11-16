package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class WalletLedgerFileTest {
    private val a = LedgerRow("t1", "FILL", -5.0, "m1", 100)
    private val b = LedgerRow("t2", "SETTLEMENT", 10.0, null, 200)

    @Test fun roundTripKeepsEveryField() {
        val book = WalletLedgerFile.Book(listOf(a, b), 5L, 3L)
        val back = WalletLedgerFile.decode(WalletLedgerFile.encode(book))!!
        assertEquals(listOf(a, b), back.rows)
        assertEquals(5L, back.atMs); assertEquals(3L, back.fullAtMs)
    }

    @Test fun mergeKeepsOneRowPerTransactionOldestFirst() {
        val merged = WalletLedgerFile.merge(listOf(b, a), listOf(a, LedgerRow("t3", "FEE", -0.1, "m1", 150)))
        assertEquals(listOf("t1", "t3", "t2"), merged.map { it.transactionId })
    }

    @Test fun garbageIsNoBook() = assertNull(WalletLedgerFile.decode("nope"))
}
