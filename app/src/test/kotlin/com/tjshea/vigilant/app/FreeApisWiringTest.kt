package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * OddsPapi Free (v4) is a close source after SportsGameOdds and before ParlayAPI; off, nothing is sent to it. Unabated and OddsPapi v5 were removed for good (Tj, 2026-10-25, PB3: "remove unabated and
 * oddspapiv5 from the app and GitHub engine entirely"): no provider, page, switch, source or close lookup is left for either.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FreeApisWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private fun settings(free: Boolean = false) =
        ScanSettings(usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true, oddsPapiFree = free)

    private suspend fun keys(c: AppContainer, op4: Boolean = true) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.ODDSPAPI_FREE, if (op4) listOf("op4-key") else emptyList())
    }

    @Test
    fun `OddsPapi Free is a close source before ParlayAPI, asked only while it is on with a key`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(free = true))
        assertTrue(c.op4Closes.active)
        val order = c.closeBackfillSourceIds()
        assertTrue(order.toString(), order.indexOf("oddspapi_free") in 0 until order.indexOf("parlay"))
        c.syncSgo(settings())
        assertFalse(c.op4Closes.active)
        keys(c, op4 = false)
        c.syncSgo(settings(free = true))
        assertFalse("a switch with no key asks nothing", c.op4Closes.active)
    }

    @Test
    fun `Unabated and OddsPapi v5 are gone from every list`() {
        assertFalse(ApiProvider.entries.any { it.id == "unabated" || it.id == "oddspapi" })
        assertFalse(ApiEntry.entries.any { it.name == "UNABATED" || it.name == "ODDSPAPI" })
        assertFalse(com.tjshea.vigilant.app.ui.SettingsPage.entries.any { it.name == "UNABATED" || it.name == "ODDSPAPI" })
        assertFalse(com.tjshea.vigilant.data.keys.QuotaPolicy.ALL.any { it.id == "unabated" || it.id == "oddspapi" })
        assertFalse(com.tjshea.vigilant.data.scanner.Scanner.SOURCE_ORDER.any { it.startsWith("unabated") || it == "oddspapi" || it == "oddspapi-props" })
        assertFalse(com.tjshea.vigilant.data.cloud.CloudKeys.ALL.any { it == "UNABATED_API_KEY" || it == "ODDSPAPI_API_KEY" })
        assertTrue(com.tjshea.vigilant.app.ui.SettingsPage.ODDSPAPI_FREE in com.tjshea.vigilant.app.ui.SettingsPage.shown(ScanSettings()))
    }
}
