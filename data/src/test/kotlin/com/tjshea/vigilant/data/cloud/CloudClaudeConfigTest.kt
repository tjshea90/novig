package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudClaudeConfigTest {
    private val local = ScanSettings()

    @Test
    fun `only a claude write is adopted`() {
        assertNull(CloudClaudeConfig.adopt(CloudConfig(device = "moto g - 2026", settings = local.copy(pausedByHand = true)), local))
    }

    @Test
    fun `a claude write changes the settings but stop all stays the phone's own`() {
        val killedPhone = local.copy(killed = true, killedAtMs = 5L)
        val remote = CloudConfig(device = "claude:acct-7542bf", settings = local.copy(killed = false, killedAtMs = null, pausedByHand = true))
        val next = CloudClaudeConfig.adopt(remote, killedPhone)!!
        assertTrue("a session cannot release STOP ALL", next.killed)
        assertEquals(5L, next.killedAtMs)
        val pressed = CloudClaudeConfig.adopt(CloudConfig(device = "claude:x", settings = local.copy(killed = true, pausedByHand = true)), local)!!
        assertFalse("and cannot press it", pressed.killed)
    }

    @Test
    fun `a wanted-on engine un-pauses the scan even when the app toggles are off`() {
        val off = local.copy(pausedByHand = true, gates = local.gates.copy(cloudScan = false))
        val next = CloudClaudeConfig.adopt(CloudConfig(on = true, device = "claude:x", settings = off), off)!!
        assertFalse(next.pausedByHand)
        assertTrue(next.gates.cloudScan)
        val killed = off.copy(killed = true)
        assertNull("STOP ALL still wins over a wanted-on engine: nothing is un-paused", CloudClaudeConfig.adopt(CloudConfig(on = true, device = "claude:x", settings = off), killed))
    }
}
