package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pinnacle only asks a backup feed (PropLine, ParlayAPI) only where the Pinnacle feeds did not answer (RESEARCH.md §88.5). */
class PinnacleBackupTest {
    private val nfl: League = Leagues.byNovigName("NFL")!!
    private val mlb: League = Leagues.byNovigName("MLB")!!
    private val on = ScanSettings(pinnacleOnly = true)
    private val event = NovigEvent("e1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "A @ B", 1_800_000_000_000L)

    private class Inner(override val id: String, override val propsOnly: Boolean = false, private val inner: Boolean = true) : ReferenceSource {
        var odds = 0
        override val displayName = "inner $id"
        override val metered = true
        override suspend fun needed(league: League, settings: ScanSettings, context: ScanContext) = inner
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot { odds++; return RefSnapshot(league.oddsApiSportKey, emptyList(), 0) }
    }

    @Test
    fun `it backs up the Pinnacle source and otherwise is the feed it wraps`() = runTest {
        val lines = Inner("propline")
        val backup = PinnacleBackup(lines)
        assertEquals(PinnapiClient.ID, backup.fallbackFor)
        assertEquals("propline", backup.id)
        assertEquals("inner propline", backup.displayName)
        assertTrue(backup.metered)
        backup.odds(nfl, on)
        assertEquals(1, lines.odds)
    }

    @Test
    fun `game lines are asked for only a league the Pinnacle feeds did not answer`() = runTest {
        val backup = PinnacleBackup(Inner("propline"))
        assertFalse(backup.needed(nfl, on, ScanContext(firstAnswered = setOf("NFL"))))
        assertTrue(backup.needed(nfl, on, ScanContext(firstAnswered = setOf("MLB"))))
        assertTrue(backup.needed(nfl, on, ScanContext()))
    }

    @Test
    fun `props are asked for only a league where Pinnacle priced no prop at all`() = runTest {
        val backup = PinnacleBackup(Inner("propline_props", propsOnly = true))
        val none = ScanContext(novigEvents = listOf(event), firstAnswered = setOf("NFL"), covered = mapOf("e1" to setOf("MONEYLINE:0", "TOTAL:0")))
        assertTrue("Pinnacle answered the league's lines but no props (pinnapi's trial key): the backup is asked", backup.needed(nfl, on, none))
        val priced = ScanContext(novigEvents = listOf(event), firstAnswered = setOf("NFL"), covered = mapOf("e1" to setOf("MONEYLINE:0", "PROP:RECEIVING_YARDS")))
        assertFalse("PinnWire priced props for the league: no per-game requests to another feed", backup.needed(nfl, on, priced))
        // Props priced for a game of another league don't count for this one.
        val other = ScanContext(novigEvents = listOf(event.copy(eventId = "e2", league = "MLB")), covered = mapOf("e2" to setOf("PROP:HITS")))
        assertTrue(backup.needed(nfl, on, other))
        assertFalse(backup.needed(mlb, on, other))
    }

    @Test
    fun `outside Pinnacle only it asks the feed it wraps`() = runTest {
        assertFalse(PinnacleBackup(Inner("propline", inner = false)).needed(nfl, ScanSettings(), ScanContext()))
        assertTrue(PinnacleBackup(Inner("propline", inner = true)).needed(nfl, ScanSettings(), ScanContext(firstAnswered = setOf("NFL"))))
    }
}
