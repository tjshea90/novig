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
        val snap = client.fetch(url, CnoFilters())

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
        client.fetch(url, CnoFilters())
        now += 60_000
        val second = client.fetch(url, CnoFilters())
        now += 60_000
        client.fetch(url, CnoFilters())

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
        client.fetch(url, CnoFilters())
        now += 60_000
        val snap = client.fetch(url, CnoFilters())
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
        client.fetch(url, CnoFilters())
        now += CnoClient.SESSION_MS + 1
        client.fetch(url, CnoFilters())
        repeat(2) { server.takeRequest() }
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `busy answers carry a pause, and aren't retried on the spot`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "90"))
        try {
            client.fetch(url, CnoFilters())
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
            client.fetch(url, CnoFilters())
            fail("expected CnoException")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("Something broke"))
        }
    }

    @Test
    fun `every read posts the scanner's filters in CNO's own form`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        server.enqueue(reply())
        val f = CnoFilters(devig = CnoDevig.CONSERVATIVE, maxOdds = 150, minBooks = 5, minEv = 0.015, rows = 40, completeBook = true)
        val snap = client.fetch(url, f)
        assertEquals(f, snap.filters)
        server.takeRequest()
        fun check(form: Map<String, String>) {
            fun v(suffix: String) = form.entries.first { it.key.endsWith(suffix) }.value
            assertEquals("8", v("DropDownListDevigMethod"))
            assertEquals("+150", v("TextBoxMaximumOdds"))
            assertEquals("5", v("TextBoxMinimumOddsProviderCount"))
            assertEquals("1.5%", v("TextBoxMinimumEVPercentage"))
            assertEquals("40", v("TextBoxMaximumResultCount"))
            assertEquals("2", v("TextBoxMinimumSubMarketSideCount"))
            assertEquals("on", v("CheckBoxRequireACompleteSportsbook"))
        }
        check(form(server.takeRequest()))
        now += 5_000
        client.fetch(url, f)
        check(form(server.takeRequest())) // the Refresh postback carries them too
    }

    // ---- the games Tj looks at (CnoScope, 2026-10-07) ----------------------------------------------------------------------------------------------

    private fun posted(f: Map<String, String>, suffix: String) = f.entries.first { it.key.endsWith(suffix) }.value

    @Test
    fun `one league is posted to CNO's own League dropdown, with the page's own id and the row limit as it is`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        client.fetch(url, CnoFilters(rows = 50, scope = CnoScope(leagues = setOf("NHL"))))
        server.takeRequest()
        val f = form(server.takeRequest())
        assertEquals("4", posted(f, "DropDownListLeague"))
        assertEquals("0", posted(f, "DropDownListSport"))
        assertEquals("50", posted(f, "TextBoxMaximumResultCount"))
    }

    @Test
    fun `a league's id is read from the page's own options, never assumed - a dropdown renumbered by CNO still works`() = runBlocking {
        val renumbered = CnoFixtures.page().replace("""<option value="4">NHL</option>""", """<option value="44">NHL</option>""")
        assertTrue(renumbered.contains("""value="44">NHL"""))
        server.enqueue(MockResponse().setBody(renumbered))
        server.enqueue(reply())
        client.fetch(url, CnoFilters(scope = CnoScope(leagues = setOf("NHL"))))
        server.takeRequest()
        assertEquals("44", posted(form(server.takeRequest()), "DropDownListLeague"))
    }

    @Test
    fun `every league of one sport posts the sport, leagues of several sports post nothing and the read is longer, and clearing the pick puts the page's own values back`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply()); server.enqueue(reply()); server.enqueue(reply())
        client.fetch(url, CnoFilters(rows = 50, scope = CnoScope(leagues = setOf("NFL", "NCAAF"))))
        server.takeRequest()
        val football = form(server.takeRequest())
        assertEquals("2", posted(football, "DropDownListSport"))
        assertEquals("0", posted(football, "DropDownListLeague"))
        assertEquals("50", posted(football, "TextBoxMaximumResultCount"))
        // Leagues of two sports: nothing for CNO's dropdowns, and 300 rows so the app's screen has something to choose from.
        now += 5_000
        client.fetch(url, CnoFilters(rows = 50, scope = CnoScope(leagues = setOf("NHL", "WNBA"))))
        val mixed = form(server.takeRequest())
        assertEquals("0", posted(mixed, "DropDownListSport"))
        assertEquals("0", posted(mixed, "DropDownListLeague"))
        assertEquals("300", posted(mixed, "TextBoxMaximumResultCount"))
        // The pick is cleared: the same session posts what the page began with, not the last pick.
        client.fetch(url, CnoFilters(rows = 50, scope = CnoScope(leagues = setOf("NHL"))))
        assertEquals("4", posted(form(server.takeRequest()), "DropDownListLeague"))
        server.enqueue(reply())
        now += 5_000
        client.fetch(url, CnoFilters(rows = 50))
        val cleared = form(server.takeRequest())
        assertEquals("0", posted(cleared, "DropDownListLeague"))
        assertEquals("0", posted(cleared, "DropDownListSport"))
        assertEquals("50", posted(cleared, "TextBoxMaximumResultCount"))
    }

    @Test
    fun `a Shared View link that already scopes the list wins - the picks are not posted over it`() = runBlocking {
        server.enqueue(MockResponse().setBody(CnoFixtures.page(league = "2", sport = "2")))
        server.enqueue(reply())
        client.fetch(url, CnoFilters(scope = CnoScope(leagues = setOf("NHL"))))
        server.takeRequest()
        val f = form(server.takeRequest())
        assertEquals("the link's NFL stays", "2", posted(f, "DropDownListLeague"))
        assertEquals("2", posted(f, "DropDownListSport"))
    }

    @Test
    fun `the fewest dollars is posted to CNO's own box, a stricter minimum in the link wins, and nothing is posted when it is 0`() = runBlocking {
        server.enqueue(page())
        server.enqueue(reply())
        client.fetch(url, CnoFilters(scope = CnoScope(minLiquidity = 50)))
        server.takeRequest()
        assertEquals("\$50", posted(form(server.takeRequest()), "TextBoxMinimumLiquidity"))
        server.enqueue(MockResponse().setBody(CnoFixtures.page(liquidity = "\$200")))
        server.enqueue(reply())
        val other = CnoClient(okhttp3.OkHttpClient(), clock = { now }, pace = CnoPace(0), bulkPace = CnoPace(0))
        other.fetch(server.url("/site/tools/positive-ev.aspx?site_id=17").toString(), CnoFilters(scope = CnoScope(minLiquidity = 50)))
        server.takeRequest()
        assertEquals("the link's \$200 is stricter", "\$200", posted(form(server.takeRequest()), "TextBoxMinimumLiquidity"))
    }

    @Test
    fun `an app-side filter widens the read to 300 rows, and when CNO refuses a widened read the next read asks for the usual limit`() = runBlocking {
        server.enqueue(page())
        server.enqueue(MockResponse().setBody(CnoFixtures.delta(Triple("error", "500", "Too big"))))
        server.enqueue(page())
        server.enqueue(reply())
        val f = CnoFilters(rows = 50, scope = CnoScope(hideLive = true))
        try { client.fetch(url, f); fail("expected CnoException") } catch (e: CnoException) { assertTrue(e.message!!.contains("Too big")) }
        server.takeRequest()
        assertEquals("300", posted(form(server.takeRequest()), "TextBoxMaximumResultCount"))
        // The fresh read after the refusal asks for the list's own limit.
        now += 5_000
        client.fetch(url, f)
        server.takeRequest()
        assertEquals("50", posted(form(server.takeRequest()), "TextBoxMaximumResultCount"))
    }

    @Test
    fun `a stricter odds cap in the Shared View link wins, but the fewest books is always the app's`() = runBlocking {
        server.enqueue(MockResponse().setBody(CnoFixtures.page(maxOdds = "+120", minBooks = "8")))
        server.enqueue(reply())
        // Tj, 2026-09-28: "add options for 1 and 2 books": 1 picked in Settings reaches CNO though the link says 8.
        client.fetch(url, CnoFilters(maxOdds = 150, minBooks = 1))
        server.takeRequest()
        val f = form(server.takeRequest())
        assertEquals("+120", f.entries.first { it.key.endsWith("TextBoxMaximumOdds") }.value)
        assertEquals("1", f.entries.first { it.key.endsWith("TextBoxMinimumOddsProviderCount") }.value)
    }

    @Test
    fun `a bet's books come from its game page, loaded like the list`() = runBlocking {
        server.enqueue(MockResponse().setBody(CnoFixtures.page(action = "./game.aspx?game_id=9&amp;side_id=102")))
        server.enqueue(MockResponse().setBody(CnoFixtures.reply(grid = CnoFixtures.gameGrid())))
        val row = CnoRow(0.05, event = "A @ B", market = "Player Receiving Yards", bet = "Joe Receiver Under 69.5", odds = 120, book = "Novig",
            gameUrl = server.url("/site/browse/game.aspx?game_id=9&side_id=102").toString())
        val view = client.books(row)!!
        assertEquals("Joe Receiver Over 69.5", view.otherBet)
        assertEquals(109, view.cnoFair)
        assertEquals(listOf("PN", "PX", "KI", "NV", "FD", "DK", "PPp"), view.prices.map { it.code }) // sharp first
        assertEquals("GET", server.takeRequest().method)
        assertEquals("${CnoFixtures.GRID_PANEL}|${CnoFixtures.TIMER}", form(server.takeRequest())[CnoFixtures.SCRIPT_MANAGER])
    }

    @Test
    fun `a bet's Novig link comes from CNO's deeplink, consent already given`() = runBlocking {
        server.enqueue(MockResponse().setBody("<script>location.replace('novigapp://events/01a0ce98-95d4-7162-b49a-79802cf97e4e/cno');</script>"))
        val row = CnoRow(0.05, event = "A @ B", market = "M", bet = "B", odds = 120, book = "Novig", betUrl = server.url("/site/redirect/deeplink.aspx?line_id=1").toString())
        assertEquals("novigapp://events/01a0ce98-95d4-7162-b49a-79802cf97e4e/cno", client.novigLink(row))
        assertEquals("BetaDeepLinkIntro=Read=1", server.takeRequest().getHeader("Cookie"))
        server.enqueue(MockResponse().setBody("<html>consent page</html>"))
        assertEquals(null, client.novigLink(row))
    }

    /** Counts the tasks sent to it; runs them on a plain thread. */
    private class CountingDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        val dispatched = java.util.concurrent.atomic.AtomicInteger()
        private val pool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cno-work") }
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            dispatched.incrementAndGet()
            pool.execute(block)
        }
    }

    @Test
    fun `the page is parsed off the caller's thread (the main one, in the app)`() = runBlocking {
        val work = CountingDispatcher()
        val c = CnoClient(OkHttpClient(), clock = { now }, work = work)
        server.enqueue(page())
        server.enqueue(reply())
        val snap = c.fetch(url, CnoFilters())
        assertEquals(3, snap.rows.size)
        assertTrue("parsed on the work dispatcher", work.dispatched.get() > 0)
    }
}
