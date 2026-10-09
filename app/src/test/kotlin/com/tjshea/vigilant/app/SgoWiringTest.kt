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

    // ---- the switch off = the app as it was ------------------------------------------------------------------------------------

    @Test
    fun `off, the source list is exactly the one the app built before SportsGameOdds existed`() = runBlocking {
        val c = app.container
        keys(c)
        for (background in listOf(false, true)) for (scan in listOf(false, true)) {
            val off = c.referenceSources(settings(false), background, scan)
            val before = c.baseReferenceSources(settings(false), background, scan)
            assertEquals("same sources, same order, same kinds (no wrapper)", before.map { it.javaClass.name + ":" + it.id }, off.map { it.javaClass.name + ":" + it.id })
            assertTrue(off.none { it is com.tjshea.vigilant.data.reference.OutsideSgo })
        }
    }

    @Test
    fun `off, nothing is ever sent to SportsGameOdds whatever the app does`() = runBlocking {
        val c = app.container
        keys(c)
        c.syncSgo(settings(false))
        assertTrue(c.sgoCloses.closes(listOf(bet())).isEmpty())
        assertEquals(null, c.sgoScores.games("NFL", java.time.LocalDate.of(2026, 10, 11)))
        assertEquals(null, c.sgoScores.players(com.tjshea.vigilant.data.tracker.GameScore("sgo:E1", "NFL", "A", "B", 0L, true, false, 1, 0)))
        assertEquals(0, c.sgoClient.requests)
        // the tapped bet's other-books sheet leans on none of it
        assertFalse(c.sgoActive(settings(false)))
    }

    @Test
    fun `off, the usage page has no SportsGameOdds card until there is a key`() {
        val s = com.tjshea.vigilant.app.SampleScan.state()
        assertFalse(com.tjshea.vigilant.app.ui.meterViews(s, 0L).any { it.policy.id == com.tjshea.vigilant.data.keys.QuotaPolicy.SGO.id })
        assertTrue(com.tjshea.vigilant.app.ui.meterViews(s.copy(sgoKeys = listOf("k-1111-2222")), 0L).any { it.policy.id == com.tjshea.vigilant.data.keys.QuotaPolicy.SGO.id })
    }

    private fun bet() = com.tjshea.vigilant.data.tracker.TrackedBet(
        id = "b", createdAtMs = 1L, league = "NFL", eventName = "A @ B", startsTs = 1L, marketLabel = "Money", selection = "B",
        marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.5, evPercentAtBet = 1.0, stake = 1.0,
    )
}
