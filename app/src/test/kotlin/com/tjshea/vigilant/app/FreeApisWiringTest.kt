package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-11: "Add two more apis ... unabated and oddspapi ... free tiers ... use the best API first and when usage runs out or the API doesn't respond, automatically fall back to the other apis that serve
 * the same data" and "keep v5 but add v4 in separate settings". Unabated is the LAST source (it costs nothing, so nothing rests for it); OddsPapi Free (v4) is only a close source, after v5 and SportsGameOdds;
 * off, the app is what it was and nothing is sent to either.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FreeApisWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val nfl = Leagues.byNovigName("NFL")!!

    private fun settings(un: Boolean = false, free: Boolean = false, sgo: Boolean = false) =
        ScanSettings(usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true, unabated = un, oddsPapiFree = free, sgoPro = sgo)

    private suspend fun keys(c: AppContainer, un: Boolean = true, op4: Boolean = true, sgo: Boolean = false) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.UNABATED, if (un) listOf("un-key") else emptyList())
        c.keyStore.setKeys(ApiProvider.ODDSPAPI_FREE, if (op4) listOf("op4-key") else emptyList())
        c.keyStore.setKeys(ApiProvider.SPORTSGAMEODDS, if (sgo) listOf("sgo-key") else emptyList())
    }

    @Test
    fun `off, the source list is unchanged and nothing is sent to either`() = runBlocking {
        val c = app.container
        keys(c)
        val ids = c.referenceSources(settings()).map { it.id }
        assertFalse(ids.toString(), ids.any { it.startsWith("unabated") })
        assertFalse(c.unabatedActive(settings()))
        assertFalse(settings().enabledSources.any { it.startsWith("unabated") })
        c.syncSgo(settings())
        assertFalse(c.op4Closes.active)
    }

    @Test
    fun `on without a key it does nothing`() = runBlocking {
        val c = app.container
        keys(c, un = false, op4 = false)
        assertFalse(c.unabatedActive(settings(un = true)))
        assertFalse(c.referenceSources(settings(un = true)).any { it.id.startsWith("unabated") })
        c.syncSgo(settings(free = true))
        assertFalse("a switch with no key asks nothing", c.op4Closes.active)
    }

    @Test
    fun `on with a key Unabated is last and nothing else rests for it`() = runBlocking {
        val c = app.container
        keys(c)
        val s = settings(un = true)
        assertTrue(c.unabatedActive(s))
        val ids = c.referenceSources(s).map { it.id }
        assertTrue(ids.toString(), ids.last().startsWith("unabated"))
        assertTrue(ids.toString(), "unabated" in ids)
        // the paid feeds all stay: Unabated is free, so none of them rests
        val without = c.referenceSources(settings()).map { it.id }
        assertEquals(without, ids.filterNot { it.startsWith("unabated") })
        assertTrue(c.referenceSources(s).first { it.id == "unabated" }.supports(nfl))
        assertTrue("unabated" in s.enabledSources && "unabated-props" in s.enabledSources)
    }

    @Test
    fun `with SGO Pro on Unabated still comes after it and after the feeds that rest`() = runBlocking {
        val c = app.container
        keys(c, sgo = true)
        val ids = c.referenceSources(settings(un = true, sgo = true)).map { it.id }
        assertTrue(ids.toString(), ids.indexOf("sgo") in 0 until ids.indexOf("unabated"))
        assertTrue(ids.toString(), ids.takeLastWhile { it.startsWith("unabated") }.isNotEmpty())
    }

    @Test
    fun `OddsPapi Free is a close source after v5 and SportsGameOdds and before ParlayAPI`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(free = true))
        assertTrue(c.op4Closes.active)
        val order = c.closeBackfillSourceIds()
        assertTrue(order.toString(), order.indexOf("oddspapi") < order.indexOf("oddspapi_free") && order.indexOf("oddspapi_free") < order.indexOf("parlay"))
        c.syncSgo(settings())
        assertFalse(c.op4Closes.active)
    }

    @Test
    fun `the two new pages are Novig-only settings`() {
        assertTrue(com.tjshea.vigilant.app.ui.SettingsPage.UNABATED in com.tjshea.vigilant.app.ui.SettingsPage.shown(ScanSettings()))
        assertTrue(com.tjshea.vigilant.app.ui.SettingsPage.ODDSPAPI_FREE in com.tjshea.vigilant.app.ui.SettingsPage.shown(ScanSettings()))
        assertTrue(com.tjshea.vigilant.app.ui.SettingsPage.ODDSPAPI in com.tjshea.vigilant.app.ui.SettingsPage.shown(ScanSettings()))
    }
}
