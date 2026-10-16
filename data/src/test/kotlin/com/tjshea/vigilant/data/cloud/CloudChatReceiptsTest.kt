package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudChatReceiptsTest {
    @Test
    fun `a read receipt is hidden from the thread, counted as an acknowledgement, and is not an answer`() {
        val ms = listOf(CloudChat.Msg("t1", 1, "tj", "hello"), CloudChat.Msg("a1", 5, "claude:x", "\uD83D\uDC4D", ack = "t1"), CloudChat.Msg("t2", 9, "tj", "again"))
        assertEquals(listOf("t1", "t2"), CloudChat.visible(ms).map { it.id })
        assertEquals(mapOf("t1" to 5L), CloudChat.acked(ms))
        assertEquals("a receipt does not answer", listOf("t1", "t2"), CloudChat.unanswered(ms).map { it.id })
    }

    @Test
    fun `an attachment-only message and a receipt decode, an empty one does not, and old files without the new fields still read`() {
        assertNotNull(CloudChat.decode(CloudChat.encode(CloudChat.Msg("t1", 1, "tj", "", files = listOf("chat-files/t1-a.png")))))
        assertNotNull(CloudChat.decode("{\"id\":\"a\",\"at\":1,\"from\":\"claude:x\",\"text\":\"x\",\"ack\":\"t1\"}"))
        assertEquals(null, CloudChat.decode("{\"id\":\"t1\",\"at\":1,\"from\":\"tj\",\"text\":\"\"}"))
        assertEquals("hi", CloudChat.decode("{\"id\":\"t1\",\"at\":1,\"from\":\"tj\",\"text\":\"hi\"}")!!.text)
    }

    @Test
    fun `an attachment path is safe and carries its message id for the cleanup`() {
        val p = CloudChat.filePath("t1792119006665", "Screen shot (1).png")
        assertTrue(p, p.startsWith("chat-files/t1792119006665-") && !p.contains(" ") && !p.contains("("))
        assertEquals("chat-files", CloudChat.FILES_BRANCH)
    }
}
