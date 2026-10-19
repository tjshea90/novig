package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.sxbet.SxLiveBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SxLiveConfirmTest {
    private val event = NovigEvent("ev", "Basketball", "NBA", NovigEvent.STATUS_LIVE, "Miami Heat @ Boston Celtics", 0L)
    private fun game(home: String, away: String, p: Double) = SxLiveBuild.Game(
        "g", "NBA", 1, home, away, 0L,
        listOf(SxLiveBuild.Line(PinnLineType.MONEYLINE, null, mapOf(PinnSide.HOME to p, PinnSide.AWAY to 1 - p), "h", 0L)),
    )

    @Test fun `home team's chance as SX has it, and flipped for the away team`() {
        val c = SxLiveConfirm { listOf(game("Boston Celtics", "Miami Heat", 0.9)) }
        assertEquals(0.9, c.moneyline(event, "Boston Celtics", 0L)!!, 1e-9)
        assertEquals(0.1, c.moneyline(event, "Miami Heat", 0L)!!, 1e-9)
    }

    @Test fun `SX's team order is not a rule`() {
        val c = SxLiveConfirm { listOf(game("Miami Heat", "Boston Celtics", 0.2)) }
        assertEquals(0.8, c.moneyline(event, "Boston Celtics", 0L)!!, 1e-9)
    }

    @Test fun `no game, no opinion, and the first source with one wins`() {
        assertNull(SxLiveConfirm { emptyList() }.moneyline(event, "Boston Celtics", 0L))
        val none = LiveConfirm { _, _, _ -> null }
        val sx = SxLiveConfirm { listOf(game("Boston Celtics", "Miami Heat", 0.9)) }
        val other = LiveConfirm { _, _, _ -> 0.5 }
        assertEquals(0.9, FirstLiveConfirm(listOf(none, sx, other)).moneyline(event, "Boston Celtics", 0L)!!, 1e-9)
    }
}
