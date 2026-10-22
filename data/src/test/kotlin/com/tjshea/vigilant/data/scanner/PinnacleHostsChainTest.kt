package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.ScanContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PinnacleHostsChainTest {
    private val nba = Leagues.ALL.first { it.novigName == "NBA" }
    private val settings = ScanSettings()

    @Test fun `pinnacle's request-metered hosts wait for the pushed feeds`() {
        assertEquals(listOf("sharpapi"), ApiChain.better("pinnacle", listOf("pinnacle", "sharpapi", "sgo")))
        assertEquals(emptyList(), ApiChain.better("pinnacle", listOf("pinnacle", "sgo")))
    }

    @Test fun `asked when the pushed feeds did not answer the league or sent no Pinnacle price`() {
        assertTrue(ApiChain.pinnacleNeeded(nba, settings, ScanContext(now = 1L)))
        val answered = ScanContext(now = 1L, firstAnswered = setOf("NBA"), coveredBooks = mapOf("NBA" to setOf("draftkings")))
        assertTrue(ApiChain.pinnacleNeeded(nba, settings, answered))
    }

    @Test fun `stands by when the pushed feeds sent Pinnacle and no game is left without lines`() {
        val ctx = ScanContext(now = 1L, firstAnswered = setOf("NBA"), coveredBooks = mapOf("NBA" to setOf("pinnacle")))
        assertFalse(ApiChain.pinnacleNeeded(nba, settings.copy(families = emptySet()), ctx))
    }
}
