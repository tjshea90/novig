package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The amount Tj types for the Vigilant wallet (Tj, 2026-09-29: "allow me to add custom amounts ... by typing in an amount"). */
class WalletAmountTest {

    @Test
    fun `dollars and cents are read the way they're typed`() {
        assertEquals(25.0, WalletAmount.parse("25")!!, 1e-9)
        assertEquals(12.5, WalletAmount.parse("12.50")!!, 1e-9)
        assertEquals(12.5, WalletAmount.parse("12.5")!!, 1e-9)
        assertEquals(7.0, WalletAmount.parse("7.")!!, 1e-9)
        assertEquals(0.75, WalletAmount.parse(".75")!!, 1e-9)
        assertEquals(1250.0, WalletAmount.parse("$1,250")!!, 1e-9)
        assertEquals(37.25, WalletAmount.parse(" 37.25 ")!!, 1e-9)
        assertEquals(10_000.0, WalletAmount.parse("10000")!!, 1e-9)
    }

    @Test
    fun `nothing that isn't a sendable amount gets through`() {
        listOf("", "abc", "0", "0.00", "0.001", "12.345", "-5", "1e3", "10000.01", "50000", "1.2.3", ".").forEach {
            assertNull("\"$it\"", WalletAmount.parse(it))
        }
    }

    @Test
    fun `the field says what's wrong, and nothing while it's empty or fine`() {
        assertNull(WalletAmount.problem(""))
        assertNull(WalletAmount.problem("25"))
        assertNotNull(WalletAmount.problem("abc"))
        assertEquals("At most $10,000 at a time", WalletAmount.problem("50000"))
        assertEquals("More than \$0, please", WalletAmount.problem("0"))
        assertEquals("Dollars and cents only (two decimal places)", WalletAmount.problem("12.345"))
    }

    @Test
    fun `a bet's shortfall suggests whole dollars, at least one`() {
        assertEquals(1.0, WalletAmount.suggest(0.85), 1e-9)
        assertEquals(1.0, WalletAmount.suggest(0.0), 1e-9)
        assertEquals(4.0, WalletAmount.suggest(3.01), 1e-9)
        assertEquals(3.0, WalletAmount.suggest(3.0), 1e-9)
        assertEquals("25", WalletAmount.text(25.0))
        assertEquals("12.50", WalletAmount.text(12.5))
    }
}
