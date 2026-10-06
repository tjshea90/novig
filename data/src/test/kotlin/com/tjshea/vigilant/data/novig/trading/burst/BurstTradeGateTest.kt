package com.tjshea.vigilant.data.novig.trading.burst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one decision that stands between the recorder's windows and real money (RESEARCH.md §95): every reason, in its order, and the only way through. */
class BurstTradeGateTest {
    private fun gate(killed: Boolean = false, paused: Boolean = false, key: Boolean = true, wallet: Double? = 50.0, stake: Double = 1.0, proved: Boolean = true) =
        BurstTradeGate.reason(killed, paused, key, wallet, stake) { proved }

    @Test
    fun `only a key, a wallet that covers both legs, no STOP ALL or pause, and the recorder's proof let it through`() {
        assertNull(gate())
        assertEquals(BurstTradeGate.STOP_ALL, gate(killed = true))
        assertEquals(BurstTradeGate.PAUSED, gate(paused = true))
        assertEquals(BurstTradeGate.NO_KEY, gate(key = false))
        assertEquals(BurstTradeGate.NO_WALLET, gate(wallet = null))
        assertEquals(BurstTradeGate.WALLET_LOW, gate(wallet = 2.0))
        assertEquals(BurstTradeGate.NOT_PROVED, gate(proved = false))
    }

    @Test
    fun `the wallet must cover both legs and their fees, to the cent`() {
        assertEquals(BurstTradeGate.WALLET_LOW, gate(wallet = 2.19, stake = 1.0))
        assertNull(gate(wallet = 2.2, stake = 1.0))
        assertEquals(BurstTradeGate.WALLET_LOW, gate(wallet = 21.9, stake = 10.0))
    }

    @Test
    fun `the most absolute reason is the one named - STOP ALL over everything, and the proof is not even asked when something else already says no`() {
        assertEquals(BurstTradeGate.STOP_ALL, gate(killed = true, paused = true, key = false, wallet = null, proved = false))
        var asked = 0
        assertEquals(BurstTradeGate.NO_KEY, BurstTradeGate.reason(false, false, false, 50.0, 1.0) { asked++; true })
        assertEquals("the proof reads a file: not asked for nothing", 0, asked)
    }

    @Test
    fun `the trader's code can only send immediate-or-cancel orders and never cancels or rests anything`() {
        val code = java.io.File("src/main/kotlin/com/tjshea/vigilant/data/novig/trading/burst/BurstTrader.kt").readText().lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.joinToString("\n")
        assertEquals("every order is IOC: the two legs and the hedge", 3, Regex("\"IOC\"").findAll(code).count())
        for (word in listOf("\"PO\"", "\"GTT\"", "\"GTC\"", "\"FOK\"", "cancelOrder", "cancelOrders", "placeOrder(", "ttlMs =")) {
            assertEquals("the trader must not use $word", false, code.contains(word))
        }
    }
}
