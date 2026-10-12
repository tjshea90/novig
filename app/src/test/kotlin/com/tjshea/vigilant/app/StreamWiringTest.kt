package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The streaming engine's wiring (Tj, 2026-10-12): what a streaming pass reads, and that the switch is the only thing that starts it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StreamWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private fun settings(bolt: Boolean, stream: Boolean = false, props: Boolean = true) = ScanSettings(
        usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true,
        families = if (props) MarketFamily.entries.toSet() else MarketFamily.entries.toSet() - MarketFamily.PLAYER_PROPS,
        gates = ApiGates(useBoltOdds = bolt, streamScan = stream),
    )

    @Test
    fun `a streaming pass reads only the BoltOdds board, which costs nothing, and nothing when BoltOdds is off`() = runBlocking {
        val c = app.container
        c.keyStore.setKeys(ApiProvider.BOLTODDS, listOf("bolt-key"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        val on = c.streamSources(settings(bolt = true))
        assertEquals(listOf("bolt", "bolt-props"), on.map { it.id })
        assertEquals(listOf("bolt"), c.streamSources(settings(bolt = true, props = false)).map { it.id })
        assertTrue(c.streamSources(settings(bolt = false)).isEmpty())
        // The normal scan still reads everything that is on.
        assertTrue(c.referenceSources(settings(bolt = true)).size > on.size)
    }

    @Test
    fun `the engine runs only while its switch is on`() {
        val c = app.container
        c.streamEngine.tick(settings(bolt = true, stream = false))
        assertFalse(c.streamEngine.status.value.running)
        c.streamEngine.tick(settings(bolt = true, stream = true))
        assertTrue(c.streamEngine.status.value.running)
        c.streamEngine.tick(settings(bolt = true, stream = false))
        assertFalse(c.streamEngine.status.value.running)
        c.streamEngine.tick(settings(bolt = true, stream = true).copy(pausedByHand = true))
        assertFalse("STOP ALL / pause stops it", c.streamEngine.status.value.running)
    }
}

/** The soft-book live feed's settings (Tj, 2026-10-12): paper only until real orders are allowed, no Pinnodds key needed, BoltOdds kept awake by Live games only mode. */
class BoltLiveSettingsTest {
    @Test
    fun `the soft-book average needs no Pinnodds key and starts paper only`() {
        val s = ScanSettings(pinnWebsite = com.tjshea.vigilant.data.scanner.PinnWebsiteSettings(feed = com.tjshea.vigilant.data.scanner.PinnFeedChoice.BOLT))
        assertTrue(s.pinnWebsite.bolt)
        assertTrue(s.pinnWebsite.noPinnKey)
        assertFalse(s.pinnWebsite.website)
        assertFalse("real orders are off until Tj allows them", s.gates.boltLiveReal)
        assertEquals(4, s.gates.boltLiveMinCompanies)
        assertFalse(ScanSettings().pinnWebsite.noPinnKey)
    }

    @Test
    fun `Live games only mode keeps BoltOdds on when it is the live fair`() {
        val bolt = ScanSettings(
            gates = ApiGates(useBoltOdds = true), pinnWebsite = com.tjshea.vigilant.data.scanner.PinnWebsiteSettings(feed = com.tjshea.vigilant.data.scanner.PinnFeedChoice.BOLT),
        )
        assertTrue(LiveOnly.enter(bolt).gates.useBoltOdds)
        assertFalse(LiveOnly.enter(bolt.copy(pinnWebsite = com.tjshea.vigilant.data.scanner.PinnWebsiteSettings())).gates.useBoltOdds)
    }
}
