package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.reference.ScanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pinnacle's request-metered hosts wait for the pushed feeds and are asked only for what those did not send (Tj, 2026-10-22). */
class PinnacleHostsChainTest {
    private val now = 1_791_000_000_000L
    private val nfl = Leagues.ALL.first { it.novigName == "NFL" }
    private val game = NovigEvent("ev1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Denver Broncos @ Los Angeles Chargers", now + 3_600_000L)
    private val noProps = ScanSettings().copy(families = setOf(MarketFamily.GAME_LINES))
    private fun ctx(books: Set<String>, answered: Boolean = true, covered: Set<String> = setOf("MONEYLINE:0", "SPREAD:0", "TOTAL:0"), pinnProps: Set<String> = emptySet()) =
        ScanContext(novigEvents = listOf(game), now = now, covered = mapOf("ev1" to covered), firstAnswered = if (answered) setOf("NFL") else emptySet(), coveredBooks = mapOf("NFL" to books), pinnacleProps = pinnProps)

    @Test fun `the hosts wait for the pushed feeds that are on`() {
        assertEquals(listOf("sharpapi"), ApiChain.better("pinnacle", listOf("pinnacle", "sharpapi", "sgo")))
        assertEquals(emptyList<String>(), ApiChain.better("pinnacle", listOf("pinnacle", "sgo")))
    }

    @Test fun `asked when the pushed feeds did not answer or sent no Pinnacle price`() {
        assertTrue(ApiChain.pinnacleNeeded(nfl, noProps, ctx(setOf("pinnacle"), answered = false)))
        assertTrue(ApiChain.pinnacleNeeded(nfl, noProps, ctx(setOf("draftkings"))))
    }

    @Test fun `asked when a game has no lines from them`() {
        assertTrue(ApiChain.pinnacleNeeded(nfl, noProps, ctx(setOf("pinnacle"), covered = emptySet())))
    }

    @Test fun `stands by when Pinnacle came pushed and every game has lines`() {
        assertFalse(ApiChain.pinnacleNeeded(nfl, noProps, ctx(setOf("pinnacle"))))
    }

    @Test fun `with props on it is still asked until a pushed feed sends Pinnacle props`() {
        val withProps = ScanSettings()
        if (MarketFamily.PLAYER_PROPS !in withProps.families) return
        assertTrue(ApiChain.pinnacleNeeded(nfl, withProps, ctx(setOf("pinnacle"))))
        assertFalse(ApiChain.pinnacleNeeded(nfl, withProps, ctx(setOf("pinnacle"), pinnProps = setOf("NFL"))))
    }
}
