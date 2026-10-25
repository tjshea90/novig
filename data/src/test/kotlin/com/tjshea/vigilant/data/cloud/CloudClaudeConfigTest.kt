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

    // PX10e (2026-10-25): the phone's push must not undo a session's change it has not adopted yet, nor a session's write undo the phone's own tap.
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val base = ScanSettings(usePinnacle = true, gates = com.tjshea.vigilant.data.scanner.ApiGates(usePinnodds = true))

    @Test
    fun `a session's change and the phone's own change since its last push both survive`() {
        val claude = CloudConfig(device = "claude:acct-7542bf", settings = base.copy(gates = base.gates.copy(usePinnodds = false)))
        val phone = base.copy(usePinnacle = false)
        val next = CloudClaudeConfig.merge(base, claude, phone, json)!!
        assertFalse("the session's switch", next.gates.usePinnodds)
        assertFalse("the phone's own tap", next.usePinnacle)
    }

    @Test
    fun `on a clash the phone's tap wins, and stop all stays the phone's`() {
        val was = base.copy(propLineGamesPerScan = 10)
        val claude = CloudConfig(device = "claude:x", settings = was.copy(propLineGamesPerScan = 20, killed = true, gates = was.gates.copy(usePinnodds = false)))
        val phone = was.copy(propLineGamesPerScan = 30, gates = was.gates.copy(useSxBet = !was.gates.useSxBet))
        val next = CloudClaudeConfig.merge(was, claude, phone, json)!!
        assertEquals("both changed it: the phone's value", 30, next.propLineGamesPerScan)
        assertFalse("only the session changed usePinnodds", next.gates.usePinnodds)
        assertEquals("only the phone changed useSxBet", !was.gates.useSxBet, next.gates.useSxBet)
        assertFalse("stop all is never taken from a session", next.killed)
    }

    @Test
    fun `without a base it is adopt, and a phone write is never merged`() {
        val claude = CloudConfig(device = "claude:x", settings = base.copy(gates = base.gates.copy(usePinnodds = false)))
        assertEquals(CloudClaudeConfig.adopt(claude, base), CloudClaudeConfig.merge(null, claude, base, json))
        assertNull(CloudClaudeConfig.merge(base, claude.copy(device = "moto g - 2026"), base.copy(usePinnacle = false), json))
    }
}
