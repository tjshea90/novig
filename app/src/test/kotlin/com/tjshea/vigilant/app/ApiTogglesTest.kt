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
 * Tj, 2026-10-11: "Confirm that all apis can be used at any time for any data and long as I have them toggled on, and if any are toggled off, they are not used or called at all."
 * Two rules, each proved for every API in Settings › APIs ([ApiEntry]): (1) one that is on (with its key) is in the source list, the other-books sheet, the lab and the closes whatever else is on, for every
 * league it carries; (2) one that is off is in none of them, key or no key.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ApiTogglesTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val nfl = Leagues.byNovigName("NFL")!!
    private val atp = Leagues.byNovigName("ATP")!!

    /** Every API on. */
    private fun everything() = ScanSettings(
        leagues = setOf("NFL", "ATP"), usePinnacle = true, usePolymarket = true, useKalshi = true, usePropLine = true, useParlay = true, useOddsApi = true,
        sgoPro = true, oddsPapi = true, oddsPapiFree = true, unabated = true, gates = com.tjshea.vigilant.data.scanner.ApiGates(useBoltOdds = true, useSxBet = true),
    )

    private suspend fun allKeys(c: AppContainer) {
        c.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pw"))
        c.keyStore.setKeys(ApiProvider.PINNAPI, listOf("pa2"))
        c.keyStore.setKeys(ApiProvider.PINNODDS, listOf("pinn"))
        c.keyStore.setKeys(ApiProvider.PROPLINE, listOf("pl"))
        c.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("oa"))
        c.keyStore.setKeys(ApiProvider.PARLAY, listOf("pa"))
        c.keyStore.setKeys(ApiProvider.SPORTSGAMEODDS, listOf("sgo"))
        c.keyStore.setKeys(ApiProvider.ODDSPAPI, listOf("op"))
        c.keyStore.setKeys(ApiProvider.ODDSPAPI_FREE, listOf("op4"))
        c.keyStore.setKeys(ApiProvider.UNABATED, listOf("un"))
        c.keyStore.setKeys(ApiProvider.BOLTODDS, listOf("bolt"))
        c.keyStore.setKeys(ApiProvider.SXBET, listOf("sx"))
    }

    /** The source ids each API contributes (Pinnacle's three hosts share one source). */
    private val idsOf: Map<ApiEntry, Set<String>> = mapOf(
        ApiEntry.SGO to setOf("sgo", "sgo-props"),
        ApiEntry.ODDSPAPI to setOf("oddspapi", "oddspapi-props"),
        ApiEntry.ODDSPAPI_FREE to setOf("oddspapi_free", "oddspapi_free-props"),
        ApiEntry.UNABATED to setOf("unabated", "unabated-props"),
        ApiEntry.BOLTODDS to setOf("bolt", "bolt-props"),
        ApiEntry.PARLAY to setOf("parlay", "parlay_1h", "parlay_props"),
        ApiEntry.PROPLINE to setOf("propline", "propline_props"),
        ApiEntry.ODDS_API to setOf("oddsapi", "oddsapi_props"),
        ApiEntry.KALSHI to setOf("kalshi"),
        ApiEntry.POLYMARKET to setOf("polymarket"),
        ApiEntry.SXBET to setOf("sxbet"),
    )

    @Test
    fun `with every API on, every one is in the list and none rests for another`() = runBlocking {
        val c = app.container
        allKeys(c)
        val s = everything()
        val ids = c.referenceSources(s).map { it.id }.toSet()
        for ((entry, own) in idsOf) assertTrue("$entry: $own missing from $ids", own.all { it in ids })
        assertTrue("pinnacle in $ids", "pinnacle" in ids)
        // the same for the Tracker's pricing, the sharp-book check and the bids' pricing (background reads)
        val background = c.referenceSources(s, background = true).map { it.id }.toSet()
        for ((entry, own) in idsOf) assertTrue("$entry (background): $own missing from $background", own.all { it in background })
        // for a league, every source that carries it is asked: nothing is left out for the leagues SGO or OddsPapi carry
        val forNfl = c.referenceSources(s).filter { it.supports(nfl) }.map { it.id }.toSet()
        for (id in listOf("sgo", "oddspapi", "bolt", "parlay", "propline", "oddsapi", "kalshi", "polymarket", "pinnacle")) assertTrue("$id for the NFL in $forNfl", id in forNfl)
        val forAtp = c.referenceSources(s).filter { it.supports(atp) }.map { it.id }.toSet()
        for (id in listOf("sgo", "pinnacle", "kalshi", "parlay")) assertTrue("$id for tennis in $forAtp", id in forAtp)
    }

    @Test
    fun `each API switched off alone is absent everywhere and the others are unchanged`() = runBlocking {
        val c = app.container
        allKeys(c)
        val all = everything()
        val full = c.referenceSources(all).map { it.id }
        for ((entry, own) in idsOf) {
            val s = entry.setOn(all, false)
            assertFalse("$entry is off", entry.isOn(s))
            val ids = c.referenceSources(s).map { it.id }
            assertTrue("$entry: ${own.intersect(ids.toSet())} still in $ids", own.none { it in ids })
            assertTrue("$entry: nothing else changes", ids.toSet() == full.toSet() - own)
            assertTrue("$entry: the settings do not list it", own.none { it in s.enabledSources })
            // a switched-off API with tennis picked is still off
            assertTrue("$entry with tennis picked", own.none { it in s.enabledSources } && own.none { it in c.referenceSources(s).filter { r -> r.supports(atp) }.map { r -> r.id } })
        }
    }

    @Test
    fun `Pinnacle is in the list while any of its three hosts is on and gone when all are off`() = runBlocking {
        val c = app.container
        allKeys(c)
        val s = everything()
        fun pinnacle(x: ScanSettings) = "pinnacle" in c.referenceSources(x).map { it.id }
        val none = s.copy(gates = s.gates.copy(usePinnwire = false, usePinnapi = false, usePinnodds = false))
        assertFalse(pinnacle(none))
        assertTrue(pinnacle(none.copy(gates = none.gates.copy(usePinnwire = true))))
        assertTrue(pinnacle(none.copy(gates = none.gates.copy(usePinnapi = true))))
        // Pinnodds needs neither SportsGameOdds Pro nor OddsPapi on
        val onlyPinnodds = none.copy(gates = none.gates.copy(usePinnodds = true), sgoPro = false, oddsPapi = false)
        assertTrue(c.pinnoddsActive(onlyPinnodds))
        assertTrue(pinnacle(onlyPinnodds))
        // switching it on from Settings › APIs also turns the Pinnacle boards on
        val viaApis = ApiEntry.PINNODDS.setOn(onlyPinnodds.copy(usePinnacle = false, gates = onlyPinnodds.gates.copy(usePinnodds = false)), true)
        assertTrue(viaApis.usePinnacle && ApiEntry.PINNODDS.isOn(viaApis))
        assertTrue(pinnacle(viaApis))
    }

    @Test
    fun `a switched-on API without a key is not called, and a key does not turn a switched-off API on`() = runBlocking {
        val c = app.container
        allKeys(c)
        val all = everything()
        // keys saved, every switch off: nothing keyed is in the list (the free exchanges have no switch but their own)
        val off = ApiEntry.entries.fold(all) { acc, e -> e.setOn(acc, false) }.copy(usePinnacle = false)
        val ids = c.referenceSources(off).map { it.id }
        assertEquals("only what has no key and is off: nothing", emptyList<String>(), ids)
        assertFalse(c.sgoActive(off) || c.opActive(off) || c.unabatedActive(off) || c.op4FairActive(off) || c.pinnoddsActive(off) || c.boltActive(off))
        // switches on, keys gone: still nothing that needs a key
        for (p in ApiProvider.entries) c.keyStore.setKeys(p, emptyList())
        val keyless = c.referenceSources(all).map { it.id }.toSet()
        for (e in listOf(ApiEntry.SGO, ApiEntry.ODDSPAPI, ApiEntry.ODDSPAPI_FREE, ApiEntry.UNABATED, ApiEntry.BOLTODDS, ApiEntry.PARLAY, ApiEntry.PROPLINE, ApiEntry.ODDS_API)) assertTrue("$e without a key", idsOf.getValue(e).none { it in keyless })
        assertFalse("pinnacle without any key", "pinnacle" in keyless)
        assertTrue("the keyless exchanges stay", "kalshi" in keyless && "polymarket" in keyless)
    }

    @Test
    fun `tennis picked does not switch any API on`() {
        val off = ScanSettings(leagues = setOf("NFL", "ATP", "WTA"), usePinnacle = false, usePolymarket = false, useKalshi = false, useParlay = false, usePropLine = false, useOddsApi = false)
        assertEquals(emptySet<String>(), off.enabledSources)
    }

    @Test
    fun `the other-books sheet asks every API that is on and none that is off`() = runBlocking {
        val c = app.container
        allKeys(c)
        val all = everything()
        val on = c.otherBookSources(all)
        assertTrue(on.parlay && on.propLine && on.oddsApi && on.sgo && on.op)
        assertFalse(c.otherBookSources(all.copy(useParlay = false)).parlay)
        assertFalse(c.otherBookSources(all.copy(usePropLine = false)).propLine)
        assertFalse(c.otherBookSources(all.copy(useOddsApi = false)).oddsApi)
        assertFalse(c.otherBookSources(all.copy(sgoPro = false)).sgo)
        assertFalse(c.otherBookSources(all.copy(oddsPapi = false)).op)
        // each one's being on never switches another off
        val sgoOnly = c.otherBookSources(all.copy(useParlay = true, usePropLine = true, useOddsApi = true, oddsPapi = false))
        assertTrue(sgoOnly.sgo && sgoOnly.parlay && sgoOnly.propLine && sgoOnly.oddsApi)
    }

    @Test
    fun `low-usage bids narrow the credit-metered feeds but never the flat-rate ones that are on`() = runBlocking {
        val c = app.container
        allKeys(c)
        val low = everything().copy(
            maker = true, makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE, makerSource = com.tjshea.vigilant.data.scanner.BidSource.VIGILANT,
            lowUsageBooks = setOf("kalshi", "pinnacle"),
        )
        assertTrue(low.lowUsageNow)
        val scan = c.referenceSources(low, scan = true).map { it.id }.toSet()
        for (id in listOf("sgo", "sgo-props", "oddspapi", "oddspapi-props", "oddspapi_free", "unabated")) assertTrue("$id in low-usage scan $scan", id in scan)
        // the metered game-line boards are not asked for a scan that only needs the picked books' props
        assertFalse("oddsapi in $scan", "oddsapi" in scan)
        // and with SGO and OddsPapi off the flat-rate ones are gone from it
        val noFlat = c.referenceSources(low.copy(sgoPro = false, oddsPapi = false, oddsPapiFree = false, unabated = false), scan = true).map { it.id }.toSet()
        assertTrue(noFlat.toString(), noFlat.none { it.startsWith("sgo") || it.startsWith("oddspapi") || it.startsWith("unabated") })
    }
}
