package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudChatTest {
    private val a = CloudChat.Msg("m1", 1_000, "tj", "hello")
    private val b = CloudChat.Msg("r1", 2_000, "claude:acct-5d1e2d", "hi", replyTo = "m1")
    private val c = CloudChat.Msg("m2", 3_000, "tj", "status?")

    @Test fun `round trip and file name sorts by time`() {
        assertEquals(a, CloudChat.decode(CloudChat.encode(a)))
        assertNull(CloudChat.decode("{\"id\":\"\",\"at\":1,\"from\":\"tj\",\"text\":\"x\"}"))
        assertTrue(CloudChat.fileName(a) < CloudChat.fileName(c))
    }

    @Test fun `thread is ordered and unanswered lists only Tj's open messages`() {
        assertEquals(listOf("m1", "r1", "m2"), CloudChat.thread(listOf(c, b, a, a)).map { it.id })
        assertEquals(listOf("m2"), CloudChat.unanswered(listOf(a, b, c)).map { it.id })
    }

    @Test fun `who line reads the lease`() {
        assertTrue(CloudChat.whoLine("acct-5d1e2d/ab12", "interactive", 30).contains("acct-5d1e2d is working"))
        assertTrue(CloudChat.whoLine("acct-5d1e2d/ab12", "routine", 25 * 60, nextSlot = "7:20 pm").contains("waits on GitHub"))
        assertTrue(CloudChat.whoLine(null, null, null).startsWith("No Claude session"))
    }
}
