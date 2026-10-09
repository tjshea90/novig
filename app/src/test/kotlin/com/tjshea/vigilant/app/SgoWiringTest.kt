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
 * Tj, 2026-10-09: "redundant apis that do the same thing as sportsgamesodds pro should be turned off to save their usage ... Only use other apis at the same time ... if they are free or significantly
 * add". With the switch on and a key saved, SportsGameOdds prices every league it carries and the paid feeds rest for them; tennis, which it does not carry, keeps them; the free exchanges stay.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SgoWiringTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val nfl = Leagues.byNovigName("NFL")!!
    private val atp = Leagues.byNovigName("ATP")!!

    private fun settings(sgo: Boolean) = ScanSettings(usePinnacle = true, usePropLine = true, useOddsApi = true, useParlay = true, useKalshi = true, usePolymarket = true, sgoPro = sgo)

    private suspend fun keys(c: AppContainer, sgoKey: Boolean = true) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.SPORTSGAMEODDS, if (sgoKey) listOf("sgo-key") else emptyList())
    }

    @Test
    fun `off, nothing changes and SportsGameOdds is not asked`() = runBlocking {
        val c = app.container
        keys(c)
        val ids = c.referenceSources(settings(false)).map { it.id }
        assertFalse("sgo" in ids || "sgo-props" in ids)
        assertTrue("pinnacle" in ids || ids.any { it.contains("pinn") })
        assertFalse(c.sgoActive(settings(false)))
    }

    @Test
    fun `on without a key it does nothing either`() = runBlocking {
        val c = app.container
        keys(c, sgoKey = false)
        assertFalse(c.sgoActive(settings(true)))
        assertFalse("sgo" in c.referenceSources(settings(true)).map { it.id })
    }

    @Test
    fun `on with a key it leads and the paid feeds rest for the leagues it carries`() = runBlocking {
        val c = app.container
        keys(c)
        val s = settings(true)
        assertTrue(c.sgoActive(s))
        val sources = c.referenceSources(s)
        assertEquals("sgo", sources.first().id)
        val forNfl = sources.filter { it.supports(nfl) }.map { it.id }
        assertTrue("sgo" in forNfl && "sgo-props" in forNfl)
        // the paid feeds that sell the same thing are not asked for the NFL
        assertTrue(forNfl.toString(), forNfl.none { it == "propline" || it == "oddsapi" || it.startsWith("parlay") || it == "pinnacle" })
        // free exchanges stay
        assertTrue(forNfl.toString(), forNfl.any { it == "kalshi" } && forNfl.any { it == "polymarket" })
        // tennis is not an SGO league: the other feeds keep it
        val forAtp = sources.filter { it.supports(atp) }.map { it.id }
        assertFalse("sgo" in forAtp)
        assertTrue(forAtp.toString(), forAtp.any { it == "pinnacle" || it.startsWith("parlay") })
    }

    @Test
    fun `when SportsGameOdds stops answering the feeds it replaced come back`() = runBlocking {
        val c = app.container
        keys(c)
        val s = settings(true)
        assertTrue(c.sgoActive(s))
        assertTrue(c.sgoClient.down(Long.MAX_VALUE / 2).not())   // never failed: not down
    }

    @Test
    fun `the closes and the grader follow the switch`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(true))
        assertTrue(c.sgoCloses.active)
        assertTrue(c.sgoScores.covers("NFL"))
        c.syncSgo(settings(false))
        assertFalse(c.sgoCloses.active)
        assertFalse(c.sgoScores.covers("NFL"))
    }
}
