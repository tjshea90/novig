package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Live features master switch (Tj, 2026-10-14): OFF switches every live feature and socket off, ON puts each back exactly as it was. */
class LiveMasterTest {
    private fun busy(): ScanSettings {
        val s = ScanSettings(includeLive = true, pinnLive = true, liveBid = true, tailLive = com.tjshea.vigilant.data.scanner.TailLiveSettings(on = true, bids = true, bet = true, stake = 3.0))
        return s.copy(gates = s.gates.copy(streamLive = true, streamScan = true))
    }

    @Test fun `off turns every live feature and every socket off, and nothing else`() {
        val before = busy()
        val n = LiveMaster.set(before, false)
        assertFalse(LiveMaster.isOn(n))
        assertFalse(n.tailLive.on || n.tailLive.bids || n.tailLive.bet || n.liveBid || n.pinnLive || n.includeLive)
        assertFalse(n.gates.streamLive || n.gates.streamScan || n.gates.novigSocket || n.gates.boltSocket || n.gates.pinnSocket || n.gates.kalshiSocket)
        assertEquals("a stake he set is not touched", 3.0, n.tailLive.stake, 0.0)
        assertEquals(LiveMaster.set(n, false), n)   // idempotent
    }

    @Test fun `on puts each switch back exactly as it was`() {
        val s = busy().let { it.copy(gates = it.gates.copy(boltSocket = false)) }   // a socket he had already switched off stays off
        val back = LiveMaster.set(LiveMaster.set(s, false), true)
        assertTrue(LiveMaster.isOn(back))
        assertEquals(s.tailLive.copy(bet = false), back.tailLive)   // real money is never switched back on by itself
        assertTrue(back.liveBid && back.pinnLive && back.includeLive)
        assertTrue(back.gates.streamLive && back.gates.streamScan && back.gates.novigSocket && back.gates.pinnSocket && back.gates.kalshiSocket)
        assertFalse(back.gates.boltSocket)
    }

    @Test fun `Live games only mode is left first so its own snapshot is not lost`() {
        val only = LiveOnly.enter(busy())
        val off = LiveMaster.set(only, false)
        assertFalse(off.liveOnly)
        assertFalse(LiveMaster.isOn(off))
    }
}
