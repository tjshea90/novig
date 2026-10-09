package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.reference.OutsideOp
import com.tjshea.vigilant.data.reference.OutsideSgo
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-09: "make a toggle ... with it on use it to full power everywhere and turn redundant APIs off to save usage (keep free ones and ones that add data/speed/accuracy like Pinnodds) ... toggle off =
 * app exactly as before". With OddsPapi on and a key saved it prices every league it carries and the paid feeds rest for them; tennis keeps them; the free exchanges and Pinnodds stay; off, the source
 * list is the one the app built before OddsPapi existed and nothing is ever sent to it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OpWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val nfl = Leagues.byNovigName("NFL")!!
    private val atp = Leagues.byNovigName("ATP")!!

    private fun settings(op: Boolean, sgo: Boolean = false) =
        ScanSettings(usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true, oddsPapi = op, sgoPro = sgo)

    private suspend fun keys(c: AppContainer, opKey: Boolean = true, sgoKey: Boolean = false, pinnodds: Boolean = false) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.ODDSPAPI, if (opKey) listOf("op-key") else emptyList())
        c.keyStore.setKeys(ApiProvider.SPORTSGAMEODDS, if (sgoKey) listOf("sgo-key") else emptyList())
        c.keyStore.setKeys(ApiProvider.PINNODDS, if (pinnodds) listOf("pinn-key") else emptyList())
    }

    @Test
    fun `off, nothing changes and OddsPapi is not asked`() = runBlocking {
        val c = app.container
        keys(c)
        val ids = c.referenceSources(settings(false)).map { it.id }
        assertFalse("oddspapi" in ids || "oddspapi-props" in ids)
        assertFalse(c.opActive(settings(false)))
        assertFalse("oddspapi" in settings(false).enabledSources || "oddspapi-props" in settings(false).enabledSources)
    }

    @Test
    fun `on without a key it does nothing either`() = runBlocking {
        val c = app.container
        keys(c, opKey = false)
        assertFalse(c.opActive(settings(true)))
        assertFalse("oddspapi" in c.referenceSources(settings(true)).map { it.id })
    }

    @Test
    fun `on with a key it leads and the paid feeds rest for the leagues it carries`() = runBlocking {
        val c = app.container
        keys(c)
        val s = settings(true)
        assertTrue(c.opActive(s))
        assertTrue("oddspapi" in s.enabledSources && "oddspapi-props" in s.enabledSources)
        val sources = c.referenceSources(s)
        assertEquals("oddspapi", sources.first().id)
        val forNfl = sources.filter { it.supports(nfl) }.map { it.id }
        assertTrue("oddspapi" in forNfl && "oddspapi-props" in forNfl)
        assertTrue(forNfl.toString(), forNfl.none { it == "propline" || it == "oddsapi" || it.startsWith("parlay") || it == "pinnacle" })
        assertTrue(forNfl.toString(), forNfl.any { it == "kalshi" } && forNfl.any { it == "polymarket" })
        // tennis is not an OddsPapi league here: the other feeds keep it
        val forAtp = sources.filter { it.supports(atp) }.map { it.id }
        assertFalse("oddspapi" in forAtp)
        assertTrue(forAtp.toString(), forAtp.any { it == "pinnacle" || it.startsWith("parlay") })
    }

    @Test
    fun `Pinnodds stays when its key is saved, the other Pinnacle feeds rest`() = runBlocking {
        val c = app.container
        keys(c, pinnodds = true)
        val s = settings(true)
        assertTrue(c.pinnoddsActive(s))
        assertTrue("Pinnacle's fresh push feed adds speed: it keeps pricing the NFL", c.referenceSources(s).filter { it.supports(nfl) }.any { it.id == "pinnacle" })
        keys(c, pinnodds = false)
        assertFalse(c.pinnoddsActive(s))
    }

    @Test
    fun `with SportsGameOdds Pro also on both lead and both rest the paid feeds`() = runBlocking {
        val c = app.container
        keys(c, sgoKey = true)
        val s = settings(true, sgo = true)
        val ids = c.referenceSources(s).map { it.id }
        assertEquals(listOf("sgo", "sgo-props", "oddspapi", "oddspapi-props"), ids.take(4))
        val forNfl = c.referenceSources(s).filter { it.supports(nfl) }.map { it.id }
        assertTrue(forNfl.toString(), forNfl.none { it == "propline" || it == "oddsapi" || it.startsWith("parlay") })
        // SGO earlier in the scan's source order wins a book both send
        assertTrue(com.tjshea.vigilant.data.scanner.Scanner.SOURCE_ORDER.indexOf("sgo") < com.tjshea.vigilant.data.scanner.Scanner.SOURCE_ORDER.indexOf("oddspapi"))
    }

    @Test
    fun `the closes and the grader follow the switch`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(true))
        assertTrue(c.opCloses.active)
        assertTrue(c.opScores.covers("NFL"))
        c.syncSgo(settings(false))
        assertFalse(c.opCloses.active)
        assertFalse(c.opScores.covers("NFL"))
    }

    // ---- the switch off = the app as it was ------------------------------------------------------------------------------------

    @Test
    fun `off, the source list is exactly the one the app built before OddsPapi existed`() = runBlocking {
        val c = app.container
        keys(c)
        for (background in listOf(false, true)) for (scan in listOf(false, true)) {
            val off = c.referenceSources(settings(false), background, scan)
            val before = c.baseReferenceSources(settings(false), background, scan)
            assertEquals(before.map { it.javaClass.name + ":" + it.id }, off.map { it.javaClass.name + ":" + it.id })
            assertTrue(off.none { it is OutsideOp || it is OutsideSgo })
        }
    }

    @Test
    fun `off, nothing is ever sent to OddsPapi whatever the app does`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(false))
        assertTrue(c.opCloses.closes(listOf(bet())).isEmpty())
        assertNull(c.opScores.games("NFL", java.time.LocalDate.of(2026, 10, 11)))
        assertNull(c.opScores.players(com.tjshea.vigilant.data.tracker.GameScore("op:E1", "NFL", "A", "B", 0L, true, false, 1, 0)))
        assertEquals(0, c.opClient.requests)
    }

    @Test
    fun `the keys are kept and listed like every provider's, and the usage card appears only with a key or the switch`() {
        val s = SampleScan.state()
        assertEquals(emptyList<String>(), s.keysOf(ApiProvider.ODDSPAPI))
        val withKey = s.withKeys(ApiProvider.ODDSPAPI, listOf("k-1111-2222"))
        assertEquals(listOf("k-1111-2222"), withKey.keysOf(ApiProvider.ODDSPAPI))
        assertEquals(emptyList<String>(), withKey.keysOf(ApiProvider.SPORTSGAMEODDS))
        assertFalse(com.tjshea.vigilant.app.ui.meterViews(s, 0L).any { it.policy.id == QuotaPolicy.ODDSPAPI.id })
        assertTrue(com.tjshea.vigilant.app.ui.meterViews(withKey, 0L).any { it.policy.id == QuotaPolicy.ODDSPAPI.id })
        assertTrue(com.tjshea.vigilant.app.ui.meterViews(s.copy(settings = s.settings.copy(oddsPapi = true)), 0L).any { it.policy.id == QuotaPolicy.ODDSPAPI.id })
    }

    @Test
    fun `the keys survive a restart`() = runBlocking {
        val c = app.container
        c.keyStore.setKeys(ApiProvider.ODDSPAPI, listOf("op-a", "op-b"))
        assertEquals(listOf("op-a", "op-b"), c.keyStore.getKeys(ApiProvider.ODDSPAPI))
    }

    private fun bet() = com.tjshea.vigilant.data.tracker.TrackedBet(
        id = "b", createdAtMs = 1L, league = "NFL", eventName = "A @ B", startsTs = 1L, marketLabel = "Money", selection = "B",
        marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.5, evPercentAtBet = 1.0, stake = 1.0,
    )
}
