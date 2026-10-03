package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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
import java.net.URLDecoder

/**
 * The scan study's wide read (Tj, 2026-10-03: "log all cno finds on every scan with all the information for each bet cno shows even if these bets don't meet my
 * criteria … They should still be hidden in the app"): a second CNO session with the numeric filters opened, kept apart from the list's own.
 */
class CnoWideTest {

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

    private fun page(cookie: String) = MockResponse().setBody(CnoFixtures.page()).addHeader("Set-Cookie", "ASP.NET_SessionId=$cookie; path=/; HttpOnly")

    private fun reply(viewState: String) = MockResponse().setBody(CnoFixtures.reply(viewState = viewState))

    private fun form(r: RecordedRequest): Map<String, String> =
        r.body.readUtf8().split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    private fun Map<String, String>.v(suffix: String): String? = entries.firstOrNull { it.key.endsWith(suffix) }?.value

    private val tj = CnoFilters(devig = CnoDevig.CONSERVATIVE, maxOdds = 150, minBooks = 5, minEv = 0.015, rows = 40, completeBook = true, minSides = 2)

    @Test
    fun `the wide read posts CNO's numeric filters opened right up, with Tj's devig and the view's scope`() = runBlocking {
        // A view whose link ticked "Require a Complete Sportsbook".
        val ticked = CnoFixtures.page().replace("id=\"complete\" type=\"checkbox\"", "id=\"complete\" type=\"checkbox\" checked=\"checked\"")
        assertTrue(ticked.contains("checked=\"checked\" name=\"") || ticked.contains("id=\"complete\" type=\"checkbox\" checked=\"checked\""))
        server.enqueue(MockResponse().setBody(ticked).addHeader("Set-Cookie", "ASP.NET_SessionId=wide1; path=/; HttpOnly"))
        server.enqueue(reply("wide+two=="))
        val snap = client.fetchWide(url, tj, 1000)!!

        assertEquals("GET", server.takeRequest().method)
        val post = server.takeRequest()
        val f = form(post)
        assertEquals("8", f.v("DropDownListDevigMethod")) // his devig: the EV column means the same thing
        assertEquals("", f.v("TextBoxMaximumOdds"))
        assertEquals("", f.v("TextBoxMinimumOdds"))
        assertEquals("1", f.v("TextBoxMinimumOddsProviderCount"))
        assertEquals("0%", f.v("TextBoxMinimumEVPercentage"))
        assertEquals("1", f.v("TextBoxMinimumSubMarketSideCount"))
        assertEquals("1000", f.v("TextBoxMaximumResultCount"))
        assertNull("the complete-book rule is off", f.v("CheckBoxRequireACompleteSportsbook"))
        assertEquals("the view's book stays", "17", f.v("DropDownListSportsbookSite_All"))
        assertEquals("the view's main-lines scope stays", "on", f.v("CheckBoxIsMain"))
        assertEquals("wide1", post.getHeader("Cookie")!!.substringAfter("="))

        assertTrue(snap.wide)
        assertEquals(tj, snap.filters) // Tj's, for the app's own screen to judge each row by
        assertEquals(3, snap.rows.size)
        // What was asked is on the snapshot, filter fields only (no view state).
        val asked = snap.asked!!
        assertTrue(asked, "TextBoxMinimumEVPercentage=0%" in asked)
        assertTrue(asked, "TextBoxMaximumResultCount=1000" in asked)
        assertTrue(asked, "CheckBoxIsMain=on" in asked)
        assertFalse(asked, "VIEWSTATE" in asked)
    }

    @Test
    fun `the wide read keeps every column CNO printed, and the list's own reads don't`() = runBlocking {
        server.enqueue(page("w"))
        server.enqueue(reply("w2"))
        server.enqueue(page("n"))
        server.enqueue(reply("n2"))
        val wide = client.fetchWide(url, tj, 1000)!!
        val narrow = client.fetch(url, tj)

        val first = wide.rows.first()
        assertEquals("Joe Receiver Over 4.5", first.cols["Bet Name"])
        assertEquals("+135 ($12)", first.cols["Odds"])
        assertEquals("Novig", first.cols["Sportsbook"])
        assertEquals("+118", first.cols["Fair Odds"])
        assertEquals("5", first.cols["Books"])
        assertEquals("11.59%", first.cols["LW-WC EV%"])
        assertEquals("0.4587", first.cols["@data-fairpercentage"])
        assertTrue(narrow.rows.all { it.cols.isEmpty() })
        assertEquals(narrow.rows.map { it.copy(cols = emptyMap()) }, wide.rows.map { it.copy(cols = emptyMap()) })
    }

    @Test
    fun `a wide read between two list reads changes nothing the list posts`() = runBlocking {
        // The list's session: page, loader postback, then one Refresh postback. The wide read's own session in between has its own cookie and state.
        server.enqueue(page("narrow"))
        server.enqueue(reply("narrow+two=="))
        server.enqueue(page("wide"))
        server.enqueue(reply("wide+two=="))
        server.enqueue(reply("narrow+three=="))
        val first = client.fetch(url, tj)
        now += 20_000
        client.fetchWide(url, tj, 1000)
        now += 5_000
        client.fetch(url, tj)

        assertEquals(5, server.requestCount) // the list's second read is still one postback, no new page load
        repeat(2) { server.takeRequest() }
        val widePage = server.takeRequest()
        assertEquals("GET", widePage.method)
        val widePost = server.takeRequest()
        assertEquals("wide", widePost.getHeader("Cookie")!!.substringAfter("="))
        val narrowAgain = server.takeRequest()
        assertEquals("narrow", narrowAgain.getHeader("Cookie")!!.substringAfter("="))
        val f = form(narrowAgain)
        assertEquals("narrow+two==", f["__VIEWSTATE"]) // not the wide session's
        assertEquals("+150", f.v("TextBoxMaximumOdds"))
        assertEquals("5", f.v("TextBoxMinimumOddsProviderCount"))
        assertEquals("1.5%", f.v("TextBoxMinimumEVPercentage"))
        assertEquals("40", f.v("TextBoxMaximumResultCount"))
        assertEquals("2", f.v("TextBoxMinimumSubMarketSideCount"))
        assertEquals("on", f.v("CheckBoxRequireACompleteSportsbook"))
        assertFalse(first.wide)
        assertNull(first.asked)
    }

    @Test
    fun `a second wide read is one postback through the Refresh button`() = runBlocking {
        server.enqueue(page("w"))
        server.enqueue(reply("w+two=="))
        server.enqueue(reply("w+three=="))
        client.fetchWide(url, tj, 1000)
        now += 40_000
        client.fetchWide(url, tj, 500)

        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        val f = form(server.takeRequest())
        assertEquals("${CnoFixtures.GRID_PANEL}|${CnoFixtures.BUTTON}", f[CnoFixtures.SCRIPT_MANAGER])
        assertEquals("w+two==", f["__VIEWSTATE"])
        assertEquals("500", f.v("TextBoxMaximumResultCount"))
        assertEquals("0%", f.v("TextBoxMinimumEVPercentage"))
    }

    @Test
    fun `CNO's busy answer to a wide read carries its pause`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "90"))
        val e = runCatching { client.fetchWide(url, tj, 1000) }.exceptionOrNull() as CnoException
        assertEquals(90, e.retryAfterSeconds)
        assertEquals(1, server.requestCount)
    }

    // ---- the table's columns -------------------------------------------------------------------------------------------

    @Test
    fun `table keeps columns only when asked, a repeated or blank header numbered`() {
        val headers = """<th>LW-WC EV%</th><th>Bet Name</th><th></th><th>Odds</th><th>Odds</th><th>Event</th><th>Market</th><th>Sportsbook</th>"""
        val grid = CnoFixtures.grid(
            headers = headers,
            rows = listOf(
                """<tr data-fairpercentage="0.4" data-other="x"><td>3.00%</td><td>Bet A</td><td>icon</td><td>+120 ($5)</td><td>second</td><td>E</td><td>M</td><td>Novig</td></tr>""",
            ),
        )
        val base = "https://crazyninjaodds.com/site/tools/positive-ev.aspx".toHttpUrl()
        assertTrue(CnoPage.table(grid, base).rows.single().cols.isEmpty())
        val cols = CnoPage.table(grid, base, keepColumns = true).rows.single().cols
        assertEquals("Bet A", cols["Bet Name"])
        assertEquals("icon", cols["col2"])
        assertEquals("+120 ($5)", cols["Odds"])
        assertEquals("second", cols["Odds#5"])
        assertEquals("0.4", cols["@data-fairpercentage"])
        assertEquals("x", cols["@data-other"])
    }

    // ---- CnoFeed.readWide ------------------------------------------------------------------------------------------------

    private class WideSource(val clock: () -> Long) : CnoSource {
        var age = 10
        var wideAge = 10
        var failWith: Exception? = null
        var note: String? = null
        var wideRows = 3
        val asked = mutableListOf<Int>()
        val wideAt = mutableListOf<Long>()
        var supported = true

        private fun rows(n: Int) = List(n) { CnoRow(0.05, event = "E$it", market = "M", bet = "B", odds = 110, book = "Novig") }

        override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, rows(1), clock(), cnoAgeSeconds = age, filters = filters)

        override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? {
            if (!supported) return null
            asked += rows
            wideAt += clock()
            failWith?.let { throw it }
            return CnoSnapshot(url, if (note != null) emptyList() else rows(wideRows), clock(), cnoAgeSeconds = wideAge, filters = filters, wide = true, note = note)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a wide read is kept apart from the list`() = runTest {
        val source = WideSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        feed.refresh("u")
        val before = feed.state.value
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals("the list's state is exactly what the list read made it", before, feed.state.value)
        assertEquals(3, feed.wide.value.snapshot!!.rows.size)
        assertEquals(1, feed.wide.value.reads)
        assertNull(feed.wide.value.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `wide reads are 30 seconds apart at the least, and only when CNO's odds have moved`() = runTest {
        val source = WideSource { currentTime }
        var t = 0L
        val feed = CnoFeed(source, clock = { t })
        feed.refresh("u")
        assertTrue(feed.readWide("u", CnoFilters()))
        t += 10_000
        assertFalse("under 30 s", feed.readWide("u", CnoFilters()))
        t += 25_000
        // The list was read again, but CNO's data is as old as it was at the last wide read (its odds haven't moved).
        source.age = 45
        feed.refresh("u")
        assertFalse("the same odds again", feed.readWide("u", CnoFilters()))
        // CNO published: the list's data time moves on.
        t += 20_000
        source.age = 2
        feed.refresh("u")
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals(2, source.wideAt.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `no wide read while the list is failing or CNO asked for a pause`() = runTest {
        var t = 0L
        val failing = object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot = throw CnoException("down")
            override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? = error("must not be asked")
        }
        val feed2 = CnoFeed(failing, clock = { t })
        feed2.refresh("u")
        assertNotNull(feed2.state.value.error)
        assertFalse(feed2.readWide("u", CnoFilters()))

        // And inside a pause.
        val busy = CnoFeed(object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), t, cnoAgeSeconds = 5)
            override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? = error("must not be asked")
            override suspend fun books(row: CnoRow): CnoBooksView? = throw CnoException("busy", retryAfterSeconds = 120)
        }, clock = { t })
        busy.refresh("u")
        busy.loadBooks(CnoRow(0.05, event = "E", market = "M", bet = "B", odds = 110, book = "Novig", gameUrl = "https://x/g?side_id=1"))
        assertNotNull(busy.state.value.pausedUntilMs)
        assertFalse(busy.readWide("u", CnoFilters()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a refused wide read asks for fewer rows next time, waits longer, and never marks the list as failing`() = runTest {
        var t = 0L
        val source = WideSource { t }
        val feed = CnoFeed(source, clock = { t })
        feed.refresh("u")
        source.failWith = CnoException("CrazyNinjaOdds answered an error")
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals(listOf(1000), source.asked)
        assertEquals(500, feed.wide.value.rowsAsked)
        assertEquals(1, feed.wide.value.errors)
        assertNull("the list's own state is untouched", feed.state.value.error)

        assertFalse("a minute, not 30 s, after one failure", run { t += 30_000; feed.readWide("u", CnoFilters()) })
        t += 31_000
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals(200, feed.wide.value.rowsAsked)
        t += 200_000
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals("200 is the least it asks", 200, feed.wide.value.rowsAsked)
        assertEquals(listOf(1000, 500, 200), source.asked)

        // It recovers: a good read clears the error and keeps the rows that worked.
        source.failWith = null
        t += 700_000
        assertTrue(feed.readWide("u", CnoFilters()))
        assertNull(feed.wide.value.error)
        assertEquals(0, feed.wide.value.errors)
        assertEquals(200, feed.wide.value.rowsAsked)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CNO's red message and no table is a refusal too, and the last good rows stay`() = runTest {
        var t = 0L
        val source = WideSource { t }
        val feed = CnoFeed(source, clock = { t })
        feed.refresh("u")
        feed.readWide("u", CnoFilters())
        val good = feed.wide.value.snapshot
        t += 40_000
        source.age = 1
        feed.refresh("u")
        source.note = "Maximum result count is 500"
        assertTrue(feed.readWide("u", CnoFilters()))
        assertEquals(good, feed.wide.value.snapshot)
        assertEquals("CrazyNinjaOdds: Maximum result count is 500", feed.wide.value.error)
        assertEquals(500, feed.wide.value.rowsAsked)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `CNO asking for a pause during a wide read pauses every lane and keeps the row count`() = runTest {
        var t = 0L
        val source = WideSource { t }
        val feed = CnoFeed(source, clock = { t })
        feed.refresh("u")
        source.failWith = CnoException("CrazyNinjaOdds is busy (HTTP 429)", retryAfterSeconds = 90)
        feed.readWide("u", CnoFilters())
        assertEquals(1000, feed.wide.value.rowsAsked)
        assertEquals(90_000L, feed.state.value.pausedUntilMs)
        assertTrue(feed.state.value.lastPause!!.contains("wide read"))
        assertTrue("the list waits it out too", feed.waitForGapMs(t) > 0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a source with no wide read does nothing`() = runTest {
        val source = WideSource { currentTime }.apply { supported = false }
        val feed = CnoFeed(source, clock = { currentTime })
        feed.refresh("u")
        feed.readWide("u", CnoFilters())
        assertNull(feed.wide.value.snapshot)
        assertNull(feed.wide.value.error)
    }
}
