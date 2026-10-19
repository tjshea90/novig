package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.Base64
import java.util.Collections

/**
 * The app chat's client against a stand-in for GitHub (CR, Tj 2026-10-19: "It misses some of my messages ... doesn't get my attachments ... work like a real chat"): nothing Tj sends is lost or sent
 * twice, attachments are on GitHub before the message that names them, the reader reads only what changed, receipts and answers show, and each reply is announced once.
 */
class ChatSyncTest {
    /** One `chat` branch in memory: files by path, a commit count for the head, folder shas from their contents. */
    private class FakeGitHub : Dispatcher() {
        val files: MutableMap<String, String> = Collections.synchronizedMap(LinkedHashMap())
        @Volatile var commits = 0
        @Volatile var seat: String? = null
        val puts: MutableList<String> = Collections.synchronizedList(ArrayList())
        val reads: MutableList<String> = Collections.synchronizedList(ArrayList())
        @Volatile var failPuts = 0
        @Volatile var loseAnswers = 0
        @Volatile var refuse = 0
        val failReads: MutableSet<String> = Collections.synchronizedSet(HashSet())

        fun head() = "c$commits"
        fun put(path: String, text: String) { files[path] = text; commits++ }
        private fun treeSha(dir: String) = files.entries.filter { it.key.startsWith("$dir/") }.sortedBy { it.key }.joinToString("|") { it.key + "=" + it.value.hashCode() }.hashCode().toString(16)

        override fun dispatch(request: RecordedRequest): MockResponse {
            val full = request.path!!.removePrefix("/repos/o/r/")
            val p = URLDecoder.decode(full.substringBefore('?'), "UTF-8")
            return when {
                request.method == "GET" && p == "git/ref/heads/chat" -> {
                    val tag = "\"${head()}\""
                    if (request.getHeader("If-None-Match") == tag) MockResponse().setResponseCode(304)
                    else MockResponse().setBody("""{"ref":"refs/heads/chat","object":{"sha":"${head()}","type":"commit"}}""").setHeader("ETag", tag)
                }
                request.method == "GET" && p == "branches/chat" -> MockResponse().setBody("{}")
                request.method == "GET" && p == "contents/seat.json" -> seat?.let {
                    val tag = "\"s${it.hashCode()}\""
                    if (request.getHeader("If-None-Match") == tag) MockResponse().setResponseCode(304) else MockResponse().setBody(it).setHeader("ETag", tag)
                } ?: MockResponse().setResponseCode(404)
                request.method == "PUT" && p.startsWith("contents/") -> {
                    val path = p.removePrefix("contents/")
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    assertEquals("chat", body["branch"]!!.jsonPrimitive.content)
                    val content = String(Base64.getDecoder().decode(body["content"]!!.jsonPrimitive.content))
                    puts += path
                    when {
                        refuse > 0 -> { refuse--; MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}""") }
                        failPuts > 0 -> { failPuts--; MockResponse().setResponseCode(500) }
                        files.containsKey(path) -> MockResponse().setResponseCode(422).setBody("""{"message":"Invalid request.\n\n\"sha\" wasn't supplied."}""")
                        else -> {
                            put(path, content)
                            if (loseAnswers > 0) { loseAnswers--; MockResponse().setResponseCode(502) } else MockResponse().setResponseCode(201).setBody("{}")
                        }
                    }
                }
                request.method == "GET" && p.startsWith("contents/") -> {
                    val path = p.removePrefix("contents/")
                    if (request.getHeader("Accept") == "application/vnd.github.raw+json") {
                        reads += path
                        if (failReads.remove(path)) return MockResponse().setResponseCode(500)
                        return files[path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
                    }
                    files[path]?.let { return MockResponse().setBody("""{"name":"${path.substringAfterLast('/')}","sha":"${it.hashCode()}","type":"file"}""") }
                    reads += "$path/"
                    val kids = files.keys.toList().filter { it.startsWith("$path/") }.map { it.removePrefix("$path/") }
                    if (kids.isEmpty()) return MockResponse().setResponseCode(404)
                    val entries = kids.groupBy { it.substringBefore('/') }.map { (name, under) ->
                        if (under.any { it.contains('/') }) """{"name":"$name","sha":"${treeSha("$path/$name")}","type":"dir","size":0}"""
                        else """{"name":"$name","sha":"${files["$path/$name"].hashCode()}","type":"file","size":1}"""
                    }
                    MockResponse().setBody("[" + entries.joinToString(",") + "]")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var gh: FakeGitHub
    private lateinit var dir: File
    private var now = 1_792_400_000_000L
    private val day get() = CloudChat.dayOf(now)

    @Before fun up() {
        gh = FakeGitHub()
        server = MockWebServer().also { it.dispatcher = gh; it.start() }
        dir = kotlin.io.path.createTempDirectory("chatsync").toFile()
    }

    @After fun down() { server.shutdown(); dir.deleteRecursively() }

    private fun sync(token: String? = "ghp_FAKEtokenFAKEtokenFAKEtoken", file: String = "chat_v2.json"): ChatSync {
        val client = CloudClient(OkHttpClient(), { token }, "o/r", server.url("/").toString().trimEnd('/'), clock = { now })
        return ChatSync(client, JsonFileStore(File(dir, file), ChatState.serializer(), { ChatState() }), File(dir, "files"), clock = { now }, random = java.util.Random(1))
    }

    private fun claude(id: String, at: Long, replyTo: List<String>, text: String = "answer", kind: String = "reply") =
        gh.put("msgs/${CloudChat.dayOf(at)}/$id.json", """{"v": 2, "id": "$id", "at": $at, "from": "claude", "account": "acct-5d1e2d", "name": "mirage", "replyTo": [${replyTo.joinToString { "\"$it\"" }}], "kind": "$kind", "text": "$text"}""")

    private fun seen(id: String, at: Long) = gh.put("msgs/${CloudChat.dayOf(at)}/$id.seen.json", """{"v": 2, "seen": "$id", "at": $at, "by": "acct-5d1e2d"}""")

    private fun pick(name: String, bytes: ByteArray, type: String = "image/png") = ChatSync.Pick(name, type) { ByteArrayInputStream(bytes) }

    @Test
    fun `a message goes on the phone at once, its attachments reach GitHub first, then the message`() = runBlocking {
        val c = sync()
        c.load()
        assertNull(c.send("look at this", listOf(pick("shot 1.png", ByteArray(3000) { 7 }), pick("log.txt", "hello".toByteArray(), "text/plain"))))
        val o = c.state.value.outbox.single()
        assertEquals(CloudChat.Delivery.SENDING, CloudChat.thread(c.state.value.msgs, emptySet(), c.state.value.outbox).single().delivery)
        assertNull("nothing waits after it went", c.sendPending())
        assertEquals(listOf("files/${o.id}/1-shot_1.png", "files/${o.id}/2-log.txt", "msgs/$day/${o.id}.json"), gh.puts)
        val m = CloudChat.decode(gh.files.getValue("msgs/$day/${o.id}.json"))!!
        assertEquals("tj", m.from)
        assertEquals(listOf("files/${o.id}/1-shot_1.png", "files/${o.id}/2-log.txt"), m.files.map { it.path })
        assertEquals(3000L, m.files[0].size)
        assertEquals("hello", gh.files.getValue("files/${o.id}/2-log.txt"))
        assertTrue(c.state.value.outbox.isEmpty())
        assertEquals(CloudChat.Delivery.SENT, CloudChat.thread(c.state.value.msgs, emptySet(), c.state.value.outbox).single().delivery)
        assertNotNull("Tj's own copy stays for the thumbnail", c.localFile(o.id, 1, "shot 1.png"))
    }

    @Test
    fun `a lost answer is found by its id - the message is on GitHub once`() = runBlocking {
        val c = sync()
        c.load()
        gh.loseAnswers = 1   // GitHub saved it but the answer never came
        c.send("hello?")
        val wait = c.sendPending()
        assertNotNull(wait)
        assertEquals(CloudChat.Delivery.RETRYING, CloudChat.thread(c.state.value.msgs, emptySet(), c.state.value.outbox).single().delivery)
        now += wait!!
        assertNull(c.sendPending())
        assertEquals(1, gh.files.keys.count { it.startsWith("msgs/") })
        assertTrue(c.state.value.outbox.isEmpty())
    }

    @Test
    fun `a failing connection is tried again with a growing pause, and nothing is lost over a restart`() = runBlocking {
        val c = sync()
        c.load()
        gh.failPuts = 3
        c.send("first")
        val p1 = c.sendPending()!!
        now += p1; val p2 = c.sendPending()!!
        now += p2; val p3 = c.sendPending()!!
        assertTrue("$p1 < $p2 < $p3", p1 < p2 && p2 < p3)
        assertEquals(3, c.state.value.outbox.single().tries)
        assertEquals("GitHub answered 500", c.state.value.outbox.single().problem)
        // the app is closed and opened again: the queue is still there and goes
        val again = sync()
        again.load()
        assertEquals("first", again.state.value.outbox.single().text)
        now += p3
        assertNull(again.sendPending())
        assertTrue(gh.files.keys.any { it.startsWith("msgs/") })
    }

    @Test
    fun `a refused token stops the message until Retry, and a later message is not held back by it`() = runBlocking {
        val c = sync()
        c.load()
        gh.refuse = 1
        c.send("one")
        assertNull("it stopped: nothing to wait for", c.sendPending())
        val stuck = c.state.value.outbox.single()
        assertTrue(stuck.stuck)
        assertEquals(CloudChat.Delivery.FAILED, CloudChat.thread(c.state.value.msgs, emptySet(), c.state.value.outbox).single().delivery)
        c.send("two")
        c.sendPending()
        assertEquals("the later message went", listOf(stuck.id), c.state.value.outbox.map { it.id })
        c.retry(stuck.id)
        assertNull(c.sendPending())
        assertEquals(2, gh.files.keys.count { it.startsWith("msgs/") })
    }

    @Test
    fun `Delete takes back a message that is not on GitHub, with its attachments`() = runBlocking {
        val c = sync()
        c.load()
        gh.refuse = 1
        c.send("oops", listOf(pick("a.png", ByteArray(10))))
        c.sendPending()
        val id = c.state.value.outbox.single().id
        c.discard(id)
        assertTrue(c.state.value.outbox.isEmpty())
        assertNull(c.localFile(id, 1, "a.png"))
        assertNull(c.sendPending())
        assertFalse(gh.files.keys.any { it.startsWith("msgs/") })
    }

    @Test
    fun `keys and oversized attachments are refused before anything is queued`() = runBlocking {
        val c = sync()
        c.load()
        assertNotNull(c.send("my key ghp_FAKEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        val big = object : java.io.InputStream() {
            var left = CloudChat.MAX_FILE_BYTES + 10
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int { if (left <= 0) return -1; val n = minOf(len.toLong(), left).toInt(); left -= n; return n }
        }
        assertNotNull(c.send("big", listOf(ChatSync.Pick("huge.bin", null) { big })))
        assertNotNull(c.send("unreadable", listOf(ChatSync.Pick("gone.png", null) { null })))
        assertTrue(c.state.value.outbox.isEmpty())
        assertTrue(File(dir, "files").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `the reader sees receipts and answers, and reads nothing while nothing changed`() = runBlocking {
        val c = sync()
        c.load()
        c.send("status?")
        c.sendPending()
        val id = c.state.value.msgs.single().id
        c.poll()
        seen(id, now + 2_000)
        c.poll()
        assertEquals(CloudChat.Delivery.SEEN, CloudChat.thread(c.state.value.msgs, c.state.value.seen.toSet()).single().delivery)
        claude("c${now + 5_000}-00aa", now + 5_000, listOf(id), "all green")
        c.poll()
        val rows = CloudChat.thread(c.state.value.msgs, c.state.value.seen.toSet())
        assertEquals(CloudChat.Delivery.ANSWERED, rows.first().delivery)
        assertEquals("all green", rows.last().text)
        val before = server.requestCount
        assertEquals(0, c.poll().messages)
        assertEquals("one look at the head (a free 304), nothing else", before + 1, server.requestCount)
    }

    @Test
    fun `only changed day folders are read, and each file once`() = runBlocking {
        val yesterday = now - 86_400_000L
        gh.put("msgs/${CloudChat.dayOf(yesterday)}/t${yesterday}-0001.json", """{"v":2,"id":"t$yesterday-0001","at":$yesterday,"from":"tj","text":"yesterday"}""")
        claude("c${yesterday + 1}-0002", yesterday + 1, listOf("t$yesterday-0001"))
        val c = sync()
        c.load()
        c.poll()
        assertEquals(2, c.state.value.msgs.size)
        gh.reads.clear()
        claude("c$now-0003", now, emptyList(), "good morning", kind = "say")
        c.poll()
        assertEquals(3, c.state.value.msgs.size)
        assertFalse("yesterday's folder was not read again: ${gh.reads}", gh.reads.any { it.contains(CloudChat.dayOf(yesterday)) })
        assertEquals(1, gh.reads.count { it.endsWith(".json") })
    }

    @Test
    fun `a file that could not be read is read on the next look`() = runBlocking {
        val c = sync()
        c.load()
        c.poll()
        val path = "msgs/$day/c$now-00bb.json"
        claude("c$now-00bb", now, emptyList(), "hi", kind = "say")
        gh.failReads += path
        c.poll()
        assertTrue(c.state.value.msgs.isEmpty())
        assertNotNull(c.problem.value)
        c.poll()
        assertEquals("hi", c.state.value.msgs.single().text)
        assertNull(c.problem.value)
    }

    @Test
    fun `each reply is announced once, nothing old on the first read, nothing while the chat is open`() = runBlocking {
        claude("c${now - 60_000}-0001", now - 60_000, emptyList(), "from before", kind = "say")
        val c = sync()
        c.load()
        assertTrue("the first read announces nothing", c.poll().announce.isEmpty())
        assertEquals("what was there is read", 0, c.state.value.unread)
        claude("c$now-0002", now, emptyList(), "new one", kind = "say")
        val r = c.poll()
        assertEquals(listOf("new one"), r.announce.map { it.text })
        assertEquals(1, c.state.value.unread)
        claude("c${now + 1}-0003", now + 1, emptyList(), "", kind = "done")
        assertTrue("a thumbs-up is not announced", c.poll().announce.isEmpty())
        assertTrue("and not again", c.poll().announce.isEmpty())
        c.open = true
        claude("c${now + 2}-0004", now + 2, emptyList(), "while open", kind = "say")
        assertTrue(c.poll().announce.isEmpty())
        assertEquals("open = read", 0, c.state.value.unread)
        c.open = false
        // the phone restarts: the same messages are not announced again
        val again = sync()
        again.load()
        gh.put("files/x/1-a.png", "png")   // the head moves without a new message
        assertTrue(again.poll().announce.isEmpty())
    }

    @Test
    fun `the seat is read while the chat is open`() = runBlocking {
        gh.seat = """{"v": 2, "seat": {"holder": "acct-5d1e2d/n", "name": "mirage", "since": ${now - 60_000}, "beat": ${now - 5_000}, "state": "idle"}}"""
        val c = sync()
        c.load()
        c.poll(withSeat = false)
        assertNull(c.seat.value)
        c.poll(withSeat = true)
        assertEquals("mirage", c.seat.value!!.name)
        assertEquals(CloudChat.Tone.ONLINE, CloudChat.presence(c.seat.value, emptyList(), now).tone)
    }

    @Test
    fun `no token - nothing is sent, the message waits for Retry, and nothing is read`() = runBlocking {
        val c = sync(token = null)
        c.load()
        c.send("hi")
        assertNull(c.sendPending())
        assertEquals("no GitHub token saved", c.state.value.outbox.single().problem)
        assertTrue(c.state.value.outbox.single().stuck)
        c.poll()
        assertEquals("no GitHub token saved", c.problem.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `earlier days are read only when asked`() = runBlocking {
        for (back in 6 downTo 0) {
            val at = now - back * 86_400_000L
            claude("c$at-00${back}0", at, emptyList(), "day $back", kind = "say")
        }
        val c = sync()
        c.load()
        c.poll()
        assertEquals(listOf("day 2", "day 1", "day 0"), c.state.value.msgs.sortedBy { it.at }.map { it.text })
        c.loadEarlier()
        c.poll()
        assertEquals(6, c.state.value.msgs.size)
        c.loadEarlier()
        c.poll()
        assertEquals(7, c.state.value.msgs.size)
        assertTrue("an old message read later is not announced as new", c.poll().announce.isEmpty())
    }

    @Test
    fun `the reader rests longer when nothing is awaited`() = runBlocking {
        val c = sync()
        c.load()
        assertEquals(ChatSync.OFF_SCREEN_MS, c.pace(onScreen = false))
        assertEquals(ChatSync.ON_SCREEN_MS, c.pace(onScreen = true))
        c.send("waiting for an answer")
        assertEquals(ChatSync.AWAIT_OFF_SCREEN_MS, c.pace(onScreen = false))
        c.open = true
        assertEquals(ChatSync.OPEN_MS, c.pace(onScreen = true))
    }
}
