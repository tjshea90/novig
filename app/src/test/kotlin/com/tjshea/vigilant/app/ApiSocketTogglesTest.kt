package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-13: a socket on/off switch for every API that has a WebSocket. On = the connection is kept; off with the API on = the regular requests; the API off = never called. The switches are
 * independent of each other, change nothing else, and a socket switch cannot turn its API on.
 */
class ApiSocketTogglesTest {
    private val withSockets = ApiEntry.entries.filter { it.hasSocket }

    @Test fun `exactly the APIs with a websocket have a socket switch`() {
        assertEquals(setOf(ApiEntry.BOLTODDS, ApiEntry.PINNODDS, ApiEntry.KALSHI), withSockets.toSet())
        ApiEntry.entries.filter { !it.hasSocket }.forEach { assertFalse("${it.name} has no socket", it.socketOn(ScanSettings())) }
    }

    @Test fun `each socket switch changes only its own gate`() {
        for (e in withSockets) {
            val base = ScanSettings()
            val off = e.setSocket(base, false)
            assertFalse(e.name, e.socketOn(off))
            assertTrue(e.name, e.socketOn(e.setSocket(off, true)))
            for (other in withSockets.filter { it != e }) assertEquals("${other.name} untouched by ${e.name}", other.socketOn(base), other.socketOn(off))
            assertEquals("the API's own switch is untouched", e.isOn(base), e.isOn(off))
            assertEquals(base.copy(gates = base.gates.copy(boltSocket = off.gates.boltSocket, pinnSocket = off.gates.pinnSocket, kalshiSocket = off.gates.kalshiSocket, sxSocket = off.gates.sxSocket)), off)
        }
    }

    @Test fun `the socket switches default on, and turning an API off leaves its socket switch as it was`() {
        val s = ScanSettings()
        assertTrue(s.gates.novigSocket && s.gates.boltSocket && s.gates.pinnSocket && s.gates.kalshiSocket)
        for (e in withSockets) {
            val apiOn = e.setOn(e.setSocket(s, false), true)
            assertTrue(e.isOn(apiOn))
            assertFalse("the socket stays off while the API is on", e.socketOn(apiOn))
            val apiOff = e.setOn(apiOn, false)
            assertFalse(e.isOn(apiOff))
            assertFalse("the setting is remembered", e.socketOn(apiOff))
        }
    }
}
