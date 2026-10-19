package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app chat's model (CR, Tj 2026-10-19): the JSON tools/chat.py writes and reads, the thread the screen draws, the red number, the line above the thread. */
class CloudChatTest {
    private val now = 1_792_400_000_000L
    private fun tj(id: String, at: Long, text: String = "q $id") = CloudChat.Msg(id, at, "tj", text)
    private fun reply(id: String, at: Long, vararg to: String, text: String = "a $id", kind: String = "reply") = CloudChat.Msg(id, at, "claude", text, to.toList(), kind, name = "mirage")

    @Test
    fun `reads what tools-chat-py writes, forgivingly`() {
        // exactly as tools/chat.py cmd_reply writes it
        val m = CloudChat.decode("""{"v": 2, "id": "c1792400000000-ab12", "at": 1792400000000, "from": "claude", "account": "acct-5d1e2d", "name": "mirage", "holder": "acct-5d1e2d/c98f4d", "replyTo": ["t1792399990000-0a1b"], "kind": "reply", "text": "on it"}""")!!
        assertEquals(listOf("t1792399990000-0a1b"), m.replyTo)
        assertEquals("reply", m.kind)
        assertEquals("mirage", m.name)
        assertFalse(m.fromTj)
        // a single id instead of a list, attachments as paths, a reply to the old app's message with its quote
        val one = CloudChat.decode("""{"id":"c1","at":5,"from":"claude","text":"x","replyTo":"t9","files":["files/t9/1-a.png"],"quote":"old words"}""")!!
        assertEquals(listOf("t9"), one.replyTo)
        assertEquals("1-a.png", one.files.single().name)
        assertEquals("old words", one.quote)
        assertNull(CloudChat.decode("""{"id":"","at":1,"from":"tj"}"""))
        assertNull(CloudChat.decode("""{"id":"x","from":"tj"}"""))
        assertNull(CloudChat.decode("not json"))
    }

    @Test
    fun `Tj's message names its attachments where they are uploaded`() {
        val o = CloudChat.Outgoing("t1792400000000-00ff", now, "look", listOf(CloudChat.LocalFile("/x/1-shot.png", "Screen shot (1).png", 1234, "image/png")))
        val back = CloudChat.decode(CloudChat.encode(o))!!
        assertTrue(back.fromTj)
        assertEquals("look", back.text)
        assertEquals("files/t1792400000000-00ff/1-Screen_shot__1_.png", back.files.single().path)
        assertEquals("Screen shot (1).png", back.files.single().name)
        assertEquals(1234L, back.files.single().size)
        assertEquals(back, CloudChat.sent(o))
        assertEquals("msgs/${CloudChat.dayOf(now)}/t1792400000000-00ff.json", CloudChat.msgPath(o.id, o.at))
    }

    @Test
    fun `ids are what the chat tools accept`() {
        val id = CloudChat.newId(now, java.util.Random(7))
        assertTrue(id, Regex("""^t\d{13}-[0-9a-f]{4}$""").matches(id))
        assertEquals("2026-10-19", CloudChat.dayOf(1_792_400_000_000L))
        assertEquals("file", CloudChat.safeName("///"))
        assertEquals("r_sum_.pdf", CloudChat.safeName("résumé.pdf").takeLast(10))
    }

    @Test
    fun `each message says where it is - sending, sent, seen, answered - and a done is a thumbs-up`() {
        val msgs = listOf(tj("t1", now - 9_000), tj("t2", now - 8_000), tj("t3", now - 7_000), reply("c1", now - 5_000, "t1"), reply("c2", now - 4_000, "t2", kind = "done", text = ""))
        val out = listOf(CloudChat.Outgoing("t4", now - 1_000, "new"), CloudChat.Outgoing("t5", now, "again", tries = 2, problem = "GitHub answered 502"),
            CloudChat.Outgoing("t6", now + 1, "nope", stuck = true, problem = "GitHub refused the token"), CloudChat.Outgoing("t3", now - 7_000, "q t3"))
        val rows = CloudChat.thread(msgs, setOf("t3"), out)
        assertEquals(listOf("t1", "t2", "t3", "c1", "t4", "t5", "t6"), rows.map { it.id })
        val by = rows.associateBy { it.id }
        assertEquals(CloudChat.Delivery.ANSWERED, by["t1"]!!.delivery)
        assertEquals(CloudChat.Delivery.ANSWERED, by["t2"]!!.delivery)
        assertTrue("a done is a thumbs-up on what it names", by["t2"]!!.liked)
        assertEquals(CloudChat.Delivery.SEEN, by["t3"]!!.delivery)
        assertEquals(CloudChat.Delivery.SENDING, by["t4"]!!.delivery)
        assertEquals(CloudChat.Delivery.RETRYING, by["t5"]!!.delivery)
        assertEquals(CloudChat.Delivery.FAILED, by["t6"]!!.delivery)
        assertEquals("GitHub refused the token", by["t6"]!!.problem)
        assertEquals("Claude (mirage)", by["c1"]!!.author)
        assertEquals("the outbox copy of a message GitHub has is not shown twice", 1, rows.count { it.id == "t3" })
    }

    @Test
    fun `a reply sorts after what it answers and quotes it when it is not right above`() {
        // the session's clock is 20 s behind the phone's: its answer still comes after the question
        val msgs = listOf(tj("t1", now, "first?"), reply("c1", now - 20_000, "t1"))
        assertEquals(listOf("t1", "c1"), CloudChat.thread(msgs, emptySet()).map { it.id })
        assertNull(CloudChat.thread(msgs, emptySet()).last().quote)
        // two questions, answered in turn: the second answer quotes its question (another message is between them)
        val two = listOf(tj("t1", now, "first?"), tj("t2", now + 1, "second?"), reply("c2", now + 5, "t2"), reply("c1", now + 6, "t1"))
        val rows = CloudChat.thread(two, emptySet())
        assertNull("t2 is right above", rows.first { it.id == "c2" }.quote)
        assertEquals("first?", rows.first { it.id == "c1" }.quote)
        // one answer to both: no quote
        assertNull(CloudChat.thread(listOf(tj("t1", now), tj("t2", now + 1), reply("c", now + 2, "t1", "t2")), emptySet()).last().quote)
        // a reply to a message of the old app carries the words it answers
        val old = CloudChat.Msg("c9", now, "claude", "done", kind = "reply", quote = "old question")
        assertEquals("old question", CloudChat.thread(listOf(old), emptySet()).single().quote)
    }

    @Test
    fun `the red number counts only Claude's chat replies after the last look`() {
        val msgs = listOf(tj("t1", now), reply("c1", now + 1, "t1"), reply("c2", now + 2, "t1", kind = "done", text = ""), reply("c3", now + 3, "t1"),
            CloudChat.Msg("s1", now + 4, "claude", "still there?", kind = "system"))
        assertEquals(3, CloudChat.unread(msgs, 0L))
        assertEquals(1, CloudChat.unread(msgs, now + 3))
        assertEquals(now + 4, CloudChat.newestFromClaude(msgs))
        assertEquals(0, CloudChat.unread(msgs, CloudChat.newestFromClaude(msgs)))
    }

    @Test
    fun `the seat file reads as tools-chat-py writes it`() {
        val s = CloudChat.parseSeat("""{"v": 2, "seat": {"holder": "acct-5d1e2d/c98f4d", "account": "acct-5d1e2d", "name": "mirage", "since": 100, "beat": 200, "state": "working", "note": "building v1.0.6", "noteAt": 150},
            "wake": {"at": 90, "ids": ["t1"], "result": "fired acct-5d1e2d (mirage)", "fires": [80, 90]}}""")!!
        assertEquals("acct-5d1e2d/c98f4d", s.holder)
        assertEquals("mirage", s.name)
        assertEquals("building v1.0.6", s.note)
        assertEquals(listOf(80L, 90L), s.fires)
        assertTrue(s.live(200 + CloudChat.SEAT_STALE_MS))
        assertFalse(s.live(201 + CloudChat.SEAT_STALE_MS))
        assertEquals(CloudChat.Seat(), CloudChat.parseSeat("""{"v": 2}"""))
        assertEquals(CloudChat.Seat(), CloudChat.parseSeat(null))
        assertNull(CloudChat.parseSeat("{"))
    }

    @Test
    fun `the line above the thread says who is there`() {
        val live = CloudChat.Seat("acct-5d1e2d/x", "mirage", now - 600_000, now - 20_000)
        val asked = CloudChat.thread(listOf(tj("t1", now - 30_000)), emptySet())
        val seen = CloudChat.thread(listOf(tj("t1", now - 30_000)), setOf("t1"))
        assertEquals(CloudChat.Tone.UNKNOWN, CloudChat.presence(null, asked, now).tone)
        assertEquals(CloudChat.Presence(CloudChat.Tone.ONLINE, "online"), CloudChat.presence(live, asked, now))
        assertTrue("seen and not answered = replying", CloudChat.presence(live, seen, now).replying)
        assertEquals("working: building", CloudChat.presence(live.copy(state = "working", note = "building"), emptyList(), now).line)
        val gone = live.copy(beat = now - 10 * 60_000)
        assertEquals(CloudChat.Tone.OFFLINE, CloudChat.presence(gone, emptyList(), now).tone)
        assertTrue(CloudChat.presence(gone, emptyList(), now).line.contains("your next message wakes it"))
        assertEquals(CloudChat.Tone.WAKING, CloudChat.presence(gone, asked, now).tone)
        val woken = gone.copy(wakeAt = now - 20_000, wakeResult = "fired acct-5d1e2d (mirage)", fires = listOf(now - 20_000))
        assertTrue(CloudChat.presence(woken, asked, now).line.startsWith("waking up"))
        val broken = gone.copy(wakeAt = now - 20_000, wakeResult = "failed: the wake link is not saved")
        assertEquals(CloudChat.Tone.PROBLEM, CloudChat.presence(broken, asked, now).tone)
        val tired = gone.copy(since = now - 2 * 3_600_000L, wakeAt = now - 60_000, wakeResult = "fired x", fires = listOf(now - 50 * 60_000, now - 40 * 60_000, now - 60_000))
        val longAgo = CloudChat.thread(listOf(tj("t1", now - 55 * 60_000)), emptySet())
        assertEquals(CloudChat.Tone.PROBLEM, CloudChat.presence(tired, longAgo, now).tone)
        assertTrue(CloudChat.presence(tired, longAgo, now).line.contains("out of usage"))
    }

    @Test
    fun `the old app's chat is shown read only, its receipts as thumbs-up`() {
        val rows = CloudChat.oldRows(listOf(CloudChat.OldMsg("t1", 1, "tj", "hello", files = listOf("chat-files/t1-screen.png")), CloudChat.OldMsg("a1", 2, "claude:acct-1", "👍", ack = "t1"),
            CloudChat.OldMsg("r1", 3, "claude:acct-1 (mirage)", "hi", replyTo = "t1")))
        assertEquals(listOf("old-t1", "old-r1"), rows.map { it.id })
        assertTrue(rows.all { it.old })
        assertTrue(rows[0].liked)
        assertEquals(listOf("screen.png"), rows[0].files)
        assertEquals("acct-1 (mirage)", rows[1].author)
    }

    @Test
    fun `key-shaped words are refused`() {
        assertTrue(CloudChat.looksSecret("here ghp_FAKEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertFalse(CloudChat.looksSecret("what did the engine do today?"))
        assertNotNull(CloudChat.parseSeat("{}"))
    }
}
