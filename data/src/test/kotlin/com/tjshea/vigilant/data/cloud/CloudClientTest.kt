package com.tjshea.vigilant.data.cloud

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudClientTest {
    private lateinit var server: MockWebServer
    private fun client(token: String? = "ghp_FAKEtokenFAKEtokenFAKEtoken") = CloudClient(OkHttpClient(), { token }, "o/r", server.url("/").toString().trimEnd('/'))

    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() { server.shutdown() }

    @Test
    fun `the heartbeat is read with the token, and an unchanged one is a 304`() = runBlocking {
        server.enqueue(MockResponse().setBody(CloudStatus.text(CloudStatus(runId = "7", rows = 12, computedAtMs = 5))).setHeader("ETag", "\"abc\"").setHeader("X-RateLimit-Remaining", "4990"))
        server.enqueue(MockResponse().setResponseCode(304))
        val c = client()
        val first = c.pollStatus(null) as CloudClient.Poll.Fresh
        assertEquals(12, first.value.rows)
        assertEquals("\"abc\"", first.etag)
        assertEquals(4990, c.rateRemaining)
        val r1 = server.takeRequest()
        assertEquals("Bearer ghp_FAKEtokenFAKEtokenFAKEtoken", r1.getHeader("Authorization"))
        assertTrue(r1.path!!.contains("/repos/o/r/contents/status.json?ref=cloud-feed"))
        assertTrue(c.pollStatus("\"abc\"") is CloudClient.Poll.NotModified)
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `a good status read is handed to the offline copy as its text, a bad one is not`() = runBlocking {
        val kept = mutableMapOf<String, String>()
        val body = CloudStatus.text(CloudStatus(runId = "9", rows = 3, computedAtMs = 7))
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody("not json at all"))
        val c = client().also { it.rawSink = { n, t -> kept[n] = t } }
        assertTrue(c.pollStatus(null) is CloudClient.Poll.Fresh)
        assertEquals(body, kept["status.json"])
        assertTrue(c.pollStatus(null) is CloudClient.Poll.Failed)
        assertEquals("a bad answer leaves the last good copy", body, kept["status.json"])
    }

    @Test
    fun `no token means no request`() = runBlocking {
        val p = client(token = null).pollStatus(null)
        assertTrue(p is CloudClient.Poll.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `errors say what happened and never carry the token`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials ghp_FAKEtokenFAKEtokenFAKEtoken"}"""))
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset", ((System.currentTimeMillis() / 1000) + 90).toString()).setBody("""{"message":"API rate limit exceeded"}"""))
        val a = client().pollStatus(null) as CloudClient.Poll.Failed
        assertTrue(a.why.contains("refused the token"))
        assertFalse(a.why.contains("ghp_"))
        val b = client().pollStatus(null) as CloudClient.Poll.Failed
        assertTrue(b.why.contains("rate limit"))
        assertTrue("waits until the reset", (b.retryAfterMs ?: 0L) in 1_000L..95_000L)
    }

    @Test
    fun `a file is saved with the existing sha, and a missing branch is made first`() = runBlocking {
        // no such file on the branch (404), the branch is missing (404), then: main's head, create the branch, then the file is put
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404)) // the put to a branch that does not exist
        server.enqueue(MockResponse().setResponseCode(404)) // ensureBranch: branch check
        server.enqueue(MockResponse().setBody("""{"object":{"sha":"deadbeef"}}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(404)) // second attempt: file lookup
        server.enqueue(MockResponse().setBody("""{"name":"x"}""")) // branch exists now
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        val why = client().putFile("phone-data", "phone/bets/bets-2026-10-14.jsonl", "hello".toByteArray(), "m")
        assertNull(why)
        val paths = (1..server.requestCount).map { server.takeRequest().let { r -> r.method + " " + r.path } }
        assertTrue(paths.any { it.startsWith("POST /repos/o/r/git/refs") })
        assertTrue(paths.last().startsWith("PUT /repos/o/r/contents/phone/bets/bets-2026-10-14.jsonl"))
    }

    @Test
    fun `the settings the phone writes are read back by the engine`() {
        val c = CloudConfig(rev = 5, on = true, settings = com.tjshea.vigilant.data.scanner.ScanSettings(leagues = setOf("NHL", "MLB"), sgoPro = true, gates = com.tjshea.vigilant.data.scanner.ApiGates(cloudScan = true)), device = "Moto")
        val back = CloudConfig.parse(CloudConfig.text(c))!!
        assertEquals(c, back)
        // a newer phone's extra field, and a settings file with only some fields, still read
        assertEquals(true, CloudConfig.parse("""{"rev":1,"on":true,"futureThing":3,"settings":{"leagues":["NBA"]}}""")?.on)
    }

    @Test
    fun `the feed file round trips through gzip`() {
        val feed = CloudFeed(10L, 2L, com.tjshea.vigilant.data.scanner.ScanStats(1, 2, 3, 4, 5, 6), emptyList())
        val back = CloudFeed.decode(CloudFeed.encode(feed))!!
        assertEquals(10L, back.computedAtMs)
        assertEquals(6, back.stats.laterGames)
        assertNull(CloudFeed.decode("not gzip".toByteArray()))
    }

    @Test
    fun `the bid desk's candidate file is read from the feed branch, and an unreadable one is said so`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(CloudBids.encode(CloudBids(77L, true, 3L, emptyList())))).setHeader("ETag", "\"b1\""))
        server.enqueue(MockResponse().setBody("not gzip"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val c = client()
        val ok = c.fetchBids() as CloudClient.Poll.Fresh
        assertEquals(77L, ok.value.atMs)
        assertTrue(ok.value.partial)
        assertTrue(server.takeRequest().path!!.contains("/repos/o/r/contents/bids.json.gz?ref=cloud-feed"))
        assertTrue((c.fetchBids() as CloudClient.Poll.Failed).why.contains("candidate list could not be read"))
        assertTrue(c.fetchBids() is CloudClient.Poll.Failed)
        val before = server.requestCount
        assertTrue(client(token = null).fetchBids() is CloudClient.Poll.Failed)
        assertEquals("no token, no request", before, server.requestCount)
    }

    @Test
    fun `the engine's usage file is read from the feed branch, an unchanged one is a 304, and nothing is asked without a token`() = runBlocking {
        val text = CloudUsage.encode(com.tjshea.vigilant.data.keys.UsageBook(mapOf("novig" to com.tjshea.vigilant.data.keys.ProviderUsage(callsToday = 42))), "run-5", 1_000L, emptyList())
        server.enqueue(MockResponse().setBody(text).setHeader("ETag", "\"u1\""))
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setBody("garbage"))
        val c = client()
        val fresh = c.fetchUsage(null) as CloudClient.Poll.Fresh
        assertEquals(42, fresh.value.book.providers.getValue("novig").callsToday)
        assertEquals("run-5", fresh.value.run)
        assertTrue(server.takeRequest().path!!.contains("/repos/o/r/contents/usage.json?ref=cloud-feed"))
        assertTrue(c.fetchUsage("\"u1\"") is CloudClient.Poll.NotModified)
        assertEquals("\"u1\"", server.takeRequest().getHeader("If-None-Match"))
        assertTrue((c.fetchUsage(null) as CloudClient.Poll.Failed).why.contains("usage could not be read"))
        val before = server.requestCount
        assertTrue(client(token = null).fetchUsage(null) is CloudClient.Poll.Failed)
        assertEquals(before, server.requestCount)
    }
}
