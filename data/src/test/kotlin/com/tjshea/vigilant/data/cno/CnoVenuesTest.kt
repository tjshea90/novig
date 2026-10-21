package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.kalshitrade.KalshiFees
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The CNO scan over Novig and Kalshi (CK, 2026-10-21): one read per book that is on, merged best first, Kalshi's EV net of its fee. */
class CnoVenuesTest {

    private class Fake(val book: String, var ev: Double = 0.05, var odds: Int = 110, var fail: Exception? = null) : CnoSource {
        val urls = mutableListOf<String>()
        override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
            urls += url
            fail?.let { throw it }
            return CnoSnapshot(url, listOf(CnoRow(ev, event = "A at B", market = "Total", bet = "Over 5.5", odds = odds, book = book, gameUrl = "g?side_id=1")), 1_000L, cnoAgeSeconds = if (book == "Kalshi") 40 else 10, filters = filters)
        }
    }

    private val url = CnoView.DEFAULT

    @Test
    fun `both books are read on their own site and merged best first`() = runTest {
        val n = Fake("Novig", ev = 0.03)
        val k = Fake("Kalshi", ev = 0.09)
        val snap = CnoVenueSource(n, k).fetch(url, CnoFilters(minEv = 0.01))
        assertTrue(n.urls.single().contains("site_id=17") && !n.urls.single().contains("site_id=31"))
        assertTrue(k.urls.single().contains("site_id=31") && !k.urls.single().contains("site_id=17"))
        assertEquals(listOf("Kalshi", "Novig"), snap.rows.map { it.book })
        assertEquals(url, snap.url)
        // The list is as old as its oldest part.
        assertEquals(40, snap.cnoAgeSeconds)
    }

    @Test
    fun `a Kalshi row's EV is net of the taker fee and dropped under the floor`() = runTest {
        val p = 1.0 / Odds.americanToDecimal(110)
        val fair = 1.04 * p
        val expected = fair / (p + KalshiFees.takerPerContract(p)) - 1.0
        val row = CnoRow(0.04, event = "e", market = "m", bet = "b", odds = 110, book = "Kalshi")
        assertEquals(expected, CnoVenueSource.kalshiNetEv(row), 1e-12)
        assertTrue(expected < 0.04)
        // 4% before the fee is under a 3% floor after it (the fee at about 48c is ~1.75c a contract): the row is dropped; a Novig row is untouched.
        val snap = CnoVenueSource(Fake("Novig", ev = 0.04), Fake("Kalshi", ev = 0.04)).fetch(url, CnoFilters(minEv = 0.03))
        assertEquals(listOf("Novig"), snap.rows.map { it.book })
        assertEquals(0.04, snap.rows.single().ev, 1e-12)
    }

    @Test
    fun `a book switched off is not read`() = runTest {
        val n = Fake("Novig")
        val k = Fake("Kalshi")
        val snap = CnoVenueSource(n, k).fetch(url, CnoFilters(venues = CnoVenues(novig = true, kalshi = false)))
        assertTrue(k.urls.isEmpty())
        assertEquals(listOf("Novig"), snap.rows.map { it.book })
        val none = CnoVenueSource(n, k).fetch(url, CnoFilters(venues = CnoVenues(novig = false, kalshi = false)))
        assertTrue(none.rows.isEmpty())
        assertEquals(CnoVenueSource.OFF_NOTE, none.note)
        assertEquals(1, n.urls.size)
    }

    @Test
    fun `one book failing keeps the other's rows and says so, both failing is an error`() = runTest {
        val n = Fake("Novig")
        val k = Fake("Kalshi", fail = CnoException("CrazyNinjaOdds is busy (HTTP 429)", retryAfterSeconds = 30))
        val snap = CnoVenueSource(n, k).fetch(url, CnoFilters())
        assertEquals(listOf("Novig"), snap.rows.map { it.book })
        assertTrue(snap.note!!.contains("Kalshi could not be read"))
        n.fail = CnoException("timeout")
        try {
            CnoVenueSource(n, k).fetch(url, CnoFilters())
            fail("both books failed")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("Novig") && e.message!!.contains("Kalshi"))
        }
        // Only Novig on and failing: its own error, unchanged (as before the venues existed).
        try {
            CnoVenueSource(n, k).fetch(url, CnoFilters(venues = CnoVenues(kalshi = false)))
            fail("Novig failed")
        } catch (e: CnoException) {
            assertEquals("timeout", e.message)
        }
    }

    @Test
    fun `the link for one book replaces every site id`() {
        val two = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?site_id=17&site_id=4&books_min=3"
        val k = CnoView.forSite(two, CnoView.KALSHI_SITE_ID)
        assertTrue(k.contains("site_id=31") && !k.contains("site_id=17") && !k.contains("site_id=4") && k.contains("books_min=3"))
        assertTrue(CnoVenueSource.isKalshi(CnoRow(0.0, event = "e", market = "m", bet = "b", odds = 100, book = "Kalshi")))
        assertFalse(CnoVenueSource.isKalshi(CnoRow(0.0, event = "e", market = "m", bet = "b", odds = 100, book = "Novig")))
        // Old saved settings (no venues) read as both books on.
        assertTrue(CnoFilters().venues.both)
    }
}
