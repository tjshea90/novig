package com.tjshea.vigilant.data.cno

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tj's pasted CNO Shared View link. */
class CnoViewTest {

    @Test
    fun `blank means Novig with CNO's recommended filters`() {
        assertEquals(CnoView.DEFAULT, CnoView.normalize("  "))
        assertEquals(listOf("17"), CnoView.DEFAULT.toHttpUrl().queryParameterValues("site_id"))
    }

    @Test
    fun `a Shared View link is kept with its filters, found inside surrounding text, and made https`() {
        val link = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?sv_title=Tj&ev_min=2_PCT_&main=1&site_id=17&liq_min=%2410&books_min=3"
        assertEquals(link, CnoView.normalize(link))
        assertEquals(link, CnoView.normalize("my view: $link  "))
        assertEquals(link, CnoView.normalize(link.replace("https://", "http://")))
        assertEquals(link, CnoView.normalize(link.removePrefix("https://")))
        val www = link.replace("//crazyninjaodds", "//www.crazyninjaodds")
        assertEquals(www, CnoView.normalize(www))
    }

    @Test
    fun `a link without a book gets Novig, one with other books keeps them`() {
        assertEquals(listOf("17"), CnoView.normalize("https://crazyninjaodds.com/site/tools/positive-ev.aspx?ev_min=1_PCT_")!!.toHttpUrl().queryParameterValues("site_id"))
        val two = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?site_id=17&site_id=15"
        assertEquals(listOf("17", "15"), CnoView.normalize(two)!!.toHttpUrl().queryParameterValues("site_id"))
    }

    @Test
    fun `other sites and other CNO pages are refused`() {
        assertNull(CnoView.normalize("https://example.com/site/tools/positive-ev.aspx?site_id=17"))
        assertNull(CnoView.normalize("https://crazyninjaodds.com.evil.io/site/tools/positive-ev.aspx"))
        assertNull(CnoView.normalize("https://crazyninjaodds.com/site/tools/arbitrage.aspx"))
        assertNull(CnoView.normalize("just some text"))
    }

    @Test
    fun `the filters read back in words`() {
        assertEquals("Novig · 3+ books", CnoView.describe(CnoView.DEFAULT))
        val link = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?sv_title=Props&ev_min=2_PCT_&main=1&league=2&odds_min=-200&odds_max=%2B300&site_id=17&liq_min=%2410&books_min=4"
        assertEquals("“Props” · Novig · NFL · EV ≥ 2% · odds -200 to +300 · \$10+ · 4+ books · main lines", CnoView.describe(link))
    }
}
