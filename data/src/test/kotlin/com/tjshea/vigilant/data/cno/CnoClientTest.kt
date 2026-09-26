package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/** How Vigilant reads a CNO view: page + loader postback once, then one Refresh postback per read. */
class CnoClientTest {

    private lateinit var server: MockWebServer
    private var now = 1_000_000L
    private lateinit var client: CnoClient
    private lateinit var url: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CnoClient(OkHttpClient(), clock = { now })
        url = server.url("/site/tools/positive-ev.aspx?site_id=17&books_min=3").toString()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun page() = MockResponse().setBody(CnoFixtures.page()).addHeader("Set-Cookie", "ASP.NET_SessionId=abc123; path=/; HttpOnly")

    private fun reply(viewState: String = "state+two==", ago: String = "27 seconds ago") =
        MockResponse().setBody(CnoFixtures.reply(viewState = viewState, info = CnoFixtures.info(ago)))

    private fun form(r: RecordedRequest): Map<String, String> =
        r.body.readUtf8().split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    @Test
    fun `the first read loads the page, then posts back as the page's loader timer does`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        val snap = client.fetch(url)

        val get = server.takeRequest()
        assertEquals("GET", get.method)
        assertTrue(get.getHeader("User-Agent")!!.endsWith("Vigilant"))
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/site/tools/positive-ev.aspx?site_id=17&books_min=3", post.path)
        assertEquals("Delta=true", post.getHeader("X-MicrosoftAjax"))
        assertEquals("ASP.NET_SessionId=abc123", post.getHeader("Cookie"))
        val f = form(post)
        assertEquals("${CnoFixtures.GRID_PANEL}|${CnoFixtures.TIMER}", f[CnoFixtures.SCRIPT_MANAGER])
        assertEquals(CnoFixtures.TIMER, f["__EVENTTARGET"])
        assertEquals("true", f["__ASYNCPOST"])
        assertEquals("state+one==", f["__VIEWSTATE"])
        assertEquals("17", f.entries.first { it.key.endsWith("DropDownListSportsbookSite_All") }.value)
        assertTrue(CnoFixtures.BUTTON !in f) // the timer's postback doesn't press the button

        assertEquals(url, snap.url)
        assertEquals(3, snap.rows.size)
        assertEquals(27, snap.cnoAgeSeconds)
        assertEquals(now, snap.fetchedAtMs)
        assertEquals(now - 27_000, snap.dataAtMs)
        assertEquals("LW-WC", snap.evLabel)
    }

    @Test
    fun `later reads are one postback through the Refresh button, carrying the state the last reply handed back`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply(viewState = "state+two=="))
        server.enqueue(reply(viewState = "state+three==", ago = "5 seconds ago"))
        server.enqueue(reply(viewState = "state+four=="))
        client.fetch(url)
        now += 60_000
        val second = client.fetch(url)
        now += 60_000
        client.fetch(url)

        assertEquals(4, server.requestCount) // GET + 3 postbacks, not a page load per read
        server.takeRequest(); server.takeRequest()
        val refresh = form(server.takeRequest())
        assertEquals("${CnoFixtures.GRID_PANEL}|${CnoFixtures.BUTTON}", refresh[CnoFixtures.SCRIPT_MANAGER])
        assertEquals("Update", refresh[CnoFixtures.BUTTON])
        assertEquals("", refresh["__EVENTTARGET"])
        assertEquals("state+two==", refresh["__VIEWSTATE"])
        assertEquals("valid/two", refresh["__EVENTVALIDATION"])
        assertEquals("state+three==", form(server.takeRequest())["__VIEWSTATE"])
        assertEquals(5, second.cnoAgeSeconds)
    }

    @Test
    fun `a failed refresh starts over with a fresh page load, once`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(page())
        server.enqueue(reply(viewState = "fresh=="))
        client.fetch(url)
        now += 60_000
        val snap = client.fetch(url)
        assertEquals(3, snap.rows.size)
        assertEquals(5, server.requestCount)
        repeat(3) { server.takeRequest() }
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `a session left idle past its lifetime isn't reused`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        server.enqueue(page())
        server.enqueue(reply())
        client.fetch(url)
        now += CnoClient.SESSION_MS + 1
        client.fetch(url)
        repeat(2) { server.takeRequest() }
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `busy answers carry a pause, and aren't retried on the spot`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "90"))
        try {
            client.fetch(url)
            fail("expected CnoException")
        } catch (e: CnoException) {
            assertEquals(90, e.retryAfterSeconds)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an ASP-NET error record is an error, not an empty list`() = runBlocking {
        server.enqueue(page())
        server.enqueue(MockResponse().setBody(CnoFixtures.delta(Triple("error", "500", "Something broke"))))
        try {
            client.fetch(url)
            fail("expected CnoException")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("Something broke"))
        }
    }
}
