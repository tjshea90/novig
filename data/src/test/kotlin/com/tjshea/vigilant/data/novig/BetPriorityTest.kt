package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bet comes first (Tj, 2026-10-12): scan requests wait at the gate while it is held, then go on where they were; the bet's own requests never wait. */
class BetPriorityTest {
    @Test fun `a scan request waits while a bet is held and goes on when it ends`() = runBlocking {
        val gate = RateGate(1_000.0, 1_000)
        val order = java.util.Collections.synchronizedList(ArrayList<String>())
        val bet = launch {
            BetPriority.hold {
                order += "bet-start"
                gate.acquire() // the bet's own request: never waits
                delay(300)
                order += "bet-end"
            }
        }
        delay(100)
        assertTrue(BetPriority.isActive)
        val scan = async { gate.acquire(); order += "scan" }
        delay(100)
        assertFalse("the scan is still waiting", order.contains("scan"))
        bet.join(); scan.await()
        assertEquals(listOf("bet-start", "bet-end", "scan"), order.toList())
        assertFalse(BetPriority.isActive)
    }

    @Test fun `nothing waits when no bet is held`() = runBlocking {
        val t0 = System.nanoTime()
        RateGate(1_000.0, 1_000).acquire()
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 100)
    }
}
