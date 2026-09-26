package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Bets Tj marked placed stay hidden through refreshes and restarts (Tj, 2026-09-26), then expire. */
class PlacedBetsTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000_000L

    private fun store(file: File = File(tmp.root, "placed.json")) =
        PlacedBets(JsonFileStore(file, PlacedBook.serializer(), { PlacedBook() }), clock = { now })

    private fun bet(key: String, startsAt: Long? = now + 3_600_000L) =
        PlacedBet(key = key, title = "Dalton Schultz Over 5.5", family = "x", placedAtMs = now, startsAtMs = startsAt)

    @Test
    fun `a placed bet is on disk, so a new process (a restart) still hides it`() = runTest {
        val file = File(tmp.root, "placed.json")
        store(file).mark(bet("cno:a"))
        store(file).mark(bet("cno:b"))
        val reopened = store(file).load()
        assertEquals(listOf("cno:a", "cno:b"), reopened.bets.map { it.key })
    }

    @Test
    fun `marking the same bet twice keeps one, and undo takes it off`() = runTest {
        val s = store()
        s.mark(bet("cno:a"))
        s.mark(bet("cno:a").copy(odds = "+141"))
        assertEquals(1, s.load().bets.size)
        assertEquals("+141", s.load().bets.single().odds)
        s.unmark("cno:a")
        assertTrue(s.load().bets.isEmpty())
    }

    @Test
    fun `placed bets drop off 12 hours after their game starts, or 3 days after marking when the start isn't known`() = runTest {
        val s = store()
        s.mark(bet("cno:game", startsAt = now + 60_000L))
        s.mark(bet("cno:nostart", startsAt = null))
        now += 60_000L + PlacedBets.KEEP_AFTER_START_MS - 1
        assertEquals(2, s.load().bets.size)
        now += 2
        assertEquals(listOf("cno:nostart"), s.load().bets.map { it.key })
        now += PlacedBets.KEEP_WITHOUT_START_MS
        assertTrue(s.load().bets.isEmpty())
    }

    @Test
    fun `a pick's family is the same player and side at any line, never the other side`() {
        assertEquals(Picks.family("Dalton Schultz Over 5.5"), Picks.family("Dalton Schultz Over 4.5"))
        assertFalse(Picks.family("Dalton Schultz Over 5.5") == Picks.family("Dalton Schultz Under 5.5"))
        assertEquals(Picks.family("Los Angeles Chargers +8.5"), Picks.family("Los Angeles Chargers +7.5"))
        assertFalse(Picks.family("Los Angeles Chargers +8.5") == Picks.family("Los Angeles Chargers -2.5"))
        assertEquals(Picks.family("Under 47.5"), Picks.family("Under 46.5"))
        assertEquals("Dallas Cowboys".lowercase(), Picks.family("Dallas Cowboys"))
        assertFalse(
            Picks.familyKey("A @ B", "Player Receptions", "Dalton Schultz Over 5.5") ==
                Picks.familyKey("A @ B", "Player Receiving Yards", "Dalton Schultz Over 5.5"),
        )
        assertEquals("O5.5", Picks.shortLine("Dalton Schultz Over 5.5"))
        assertEquals("U47.5", Picks.shortLine("Under 47.5"))
        assertEquals("-3.5", Picks.shortLine("New York Giants -3.5"))
    }
}
