package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.ApiChain
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-12: "I got a new api. It is boltodds pro ... Integrate it just like all the other apis" and the standing rule that an API toggled on is used for any data and one toggled off is never called.
 * BoltOdds is read from its sockets' boards (free once connected), so nothing rests for it and it rests for nothing: it is NOT on an [ApiChain], it follows its switch and its key, it joins the order after
 * SportsGameOdds and OddsPapi (until its first sample is checked), and Live games only mode switches it off with the rest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BoltOddsWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val nfl = Leagues.byNovigName("NFL")!!
    private val atp = Leagues.byNovigName("ATP")!!

    private fun settings(on: Boolean = false, families: Set<MarketFamily> = MarketFamily.entries.toSet()) =
        ScanSettings(usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true, families = families, gates = ApiGates(useBoltOdds = on))

    private suspend fun keys(c: AppContainer, bolt: Boolean) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.BOLTODDS, if (bolt) listOf("bolt-key") else emptyList())
    }

    @Test
    fun `off, the source list is what it was and nothing is open or sent`() = runBlocking {
        val c = app.container
        keys(c, bolt = true)
        val s = settings(on = false)
        assertFalse(c.boltActive(s))
        assertFalse(c.referenceSources(s).any { it.id.startsWith("bolt") })
        assertFalse(s.enabledSources.any { it.startsWith("bolt") })
        c.syncSgo(s)
        assertEquals(com.tjshea.vigilant.data.boltodds.BoltOddsStream.State.OFF, c.boltFeed.lines.state)
        assertEquals(com.tjshea.vigilant.data.boltodds.BoltOddsStream.State.OFF, c.boltFeed.props.state)
        assertEquals("no request was made", 0, c.boltClient.requests)
    }

    @Test
    fun `on without a key it does nothing`() = runBlocking {
        val c = app.container
        keys(c, bolt = false)
        val s = settings(on = true)
        assertFalse(c.boltActive(s))
        assertFalse(c.referenceSources(s).any { it.id.startsWith("bolt") })
    }

    @Test
    fun `on with a key its two sources join every league it carries, beside the other APIs`() = runBlocking {
        val c = app.container
        keys(c, bolt = true)
        val s = settings(on = true)
        assertTrue(c.boltActive(s))
        val ids = c.referenceSources(s).map { it.id }
        assertTrue(ids.toString(), "bolt" in ids && "bolt-props" in ids)
        // nothing else is dropped for it
        assertTrue(ids.toString(), listOf("pinnacle", "kalshi", "polymarket", "propline", "parlay", "oddsapi").all { it in ids })
        assertEquals(setOf("bolt", "bolt-props"), s.enabledSources.filter { it.startsWith("bolt") }.toSet())
        assertTrue(c.referenceSources(s).filter { it.supports(nfl) }.map { it.id }.containsAll(listOf("bolt", "bolt-props")))
        assertFalse("tennis is not a BoltOdds league here", c.referenceSources(s).filter { it.supports(atp) }.any { it.id.startsWith("bolt") })
        // its background reads (the Tracker's pricing, bids) include it too
        assertTrue(c.referenceSources(s, background = true).map { it.id }.containsAll(listOf("bolt", "bolt-props")))
    }

    @Test
    fun `the props socket is not used when player props are not priced`() = runBlocking {
        val c = app.container
        keys(c, bolt = true)
        val ids = c.referenceSources(settings(on = true, families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL))).map { it.id }
        assertTrue("bolt" in ids)
        assertFalse("bolt-props" in ids)
    }

    @Test
    fun `it is on no chain and breaks a tie after SportsGameOdds and OddsPapi`() {
        assertEquals(null, ApiChain.kindOf("bolt"))
        assertEquals(null, ApiChain.kindOf("bolt-props"))
        val order = Scanner.SOURCE_ORDER
        for (id in listOf("bolt", "bolt-props")) {
            assertTrue(order.indexOf(id) > order.indexOf("sgo-props"))
            assertTrue(order.indexOf(id) > order.indexOf("oddspapi-props"))
            assertTrue(order.indexOf(id) < order.indexOf("parlay"))
        }
        assertEquals("every source id is listed once", order.size, order.toSet().size)
    }

    @Test
    fun `Live games only mode switches it off and back on`() {
        val on = settings(on = true)
        val live = LiveOnly.enter(on)
        assertFalse(live.gates.useBoltOdds)
        assertFalse(ApiEntry.BOLTODDS.isOn(live))
        assertTrue(LiveOnly.leave(live).gates.useBoltOdds)
        val off = settings(on = false)
        assertFalse(LiveOnly.leave(LiveOnly.enter(off)).gates.useBoltOdds)
    }

    @Test
    fun `its entry in Settings APIs has its own page and a key list`() {
        assertEquals(com.tjshea.vigilant.app.ui.SettingsPage.BOLTODDS, ApiEntry.BOLTODDS.page)
        assertEquals(com.tjshea.vigilant.app.ui.SettingsPage.APIS, com.tjshea.vigilant.app.ui.SettingsPage.BOLTODDS.parent)
        assertEquals(ApiProvider.BOLTODDS, ApiEntry.BOLTODDS.provider)
        assertFalse(ApiEntry.BOLTODDS.keyless)
    }
}

/** The streaming engine's rules (Tj, 2026-10-12): the pace between passes, and what a pass reads. */
class StreamPacerTest {
    @Test
    fun `a pass waits out the rest of the gap, never less than a second`() {
        assertEquals(0L, StreamPacer.waitMs(10_000, 0, 3_000))
        assertEquals(2_000L, StreamPacer.waitMs(11_000, 10_000, 3_000).let { it + 1_000 })
        assertEquals(1_000L, StreamPacer.waitMs(10_000, 10_000, 0))
        assertEquals(0L, StreamPacer.waitMs(12_000, 10_000, 2_000))
    }
}
