package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TennisOnlyTest {
    private val kalshi = KalshiClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { ignoreUnknownKeys = true })

    @Test fun `a feed asked for tennis alone supports the tours and nothing else`() {
        val only = TennisOnly(kalshi)
        assertTrue(only.supports(Leagues.byNovigName("ATP")!!) && only.supports(Leagues.byNovigName("WTA")!!))
        assertFalse(only.supports(Leagues.byNovigName("NFL")!!))
        assertTrue("the plain feed carries more than tennis", kalshi.supports(Leagues.byNovigName("NFL")!!))
    }

    @Test fun `with tennis picked the tennis feeds price it even when their switches are off, and not otherwise`() {
        val off = ScanSettings(usePinnacle = false, useKalshi = false, useParlay = false, usePolymarket = false, useOddsApi = false, usePropLine = false)
        assertFalse(off.copy(leagues = setOf("NFL")).enabledSources.any { it in TennisOnly.FEEDS })
        assertTrue(off.copy(leagues = setOf("NFL", "ATP")).enabledSources.containsAll(TennisOnly.FEEDS))
    }
}
