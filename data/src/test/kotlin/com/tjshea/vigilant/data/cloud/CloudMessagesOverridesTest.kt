package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMessagesOverridesTest {
    @Test
    fun `a message round-trips, its file name sorts by time, and only unseen names are read`() {
        val m = CloudMessages.Message(id = "adv-refill-propline-20261015", at = 1_792_000_000_000L, from = "engine", kind = "recommendation", title = "PropLine is used up", body = "0 of 1000 left", priority = 1, action = "PROPLINE")
        assertEquals(m, CloudMessages.decode(CloudMessages.encode(m)))
        assertNull(CloudMessages.decode("{\"id\":\"\",\"at\":1,\"from\":\"x\",\"title\":\"t\"}"))
        assertNull(CloudMessages.decode("not json"))
        val older = CloudMessages.fileName(m.copy(id = "a", at = 5L))
        assertTrue(older < CloudMessages.fileName(m))
        assertEquals(listOf(CloudMessages.fileName(m)), CloudMessages.unseen(listOf(older, CloudMessages.fileName(m), "README.md"), setOf(older)))
        assertEquals(listOf("b"), CloudMessages.newestFirst(listOf(m.copy(id = "a", at = 1), m.copy(id = "b", at = 2))).map { it.id }.take(1))
    }

    @Test
    fun `text that looks like a key or a token is recognised so it never goes into a public message`() {
        assertTrue(CloudMessages.looksSecret("token ghp_abcdefghijklmnopqrstuvwxyz0123456789")) // FAKE test value
        assertTrue(CloudMessages.looksSecret("-----BEGIN EC PRIVATE KEY-----")) // FAKE test value
        assertFalse(CloudMessages.looksSecret("PropLine has 150 of 1000 requests left, resets in 3 h"))
    }

    @Test
    fun `an override sets only the api switches it names, ignores unknown ids and describes the change`() {
        val s = ApiToggles.setOn(ApiToggles.setOn(ScanSettings(), "parlay", true), "sgo", false)
        val r = CloudOverrides.Request("o1", 1L, "engine", "silent", mapOf("parlay" to false, "sgo" to true, "novig" to true, "autoBet" to true))
        val out = CloudOverrides.apply(s, r)
        assertEquals(false, ApiToggles.isOn(out, "parlay"))
        assertEquals(true, ApiToggles.isOn(out, "sgo"))
        assertEquals("nothing else changed", s.copy(useParlay = false, sgoPro = true), out)
        assertEquals("parlay off, sgo on", CloudOverrides.describe(s, r))
        assertEquals(r, CloudOverrides.decode(CloudOverrides.encode(r)))
    }

    @Test
    fun `bets, bids and quiet info are not worth showing, alerts and recommendations are`() {
        fun m(kind: String, pr: Int = 0) = CloudMessages.Message("x", 1L, "engine", kind, "t", priority = pr)
        org.junit.Assert.assertFalse(CloudMessages.worthShowing(m("bet", 1)))
        org.junit.Assert.assertFalse(CloudMessages.worthShowing(m("info")))
        org.junit.Assert.assertTrue(CloudMessages.worthShowing(m("info", 1)))
        org.junit.Assert.assertTrue(CloudMessages.worthShowing(m("alert")))
        org.junit.Assert.assertTrue(CloudMessages.worthShowing(m("recommendation")))
    }
}
