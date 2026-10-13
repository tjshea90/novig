package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.tracker.PlacedBets
import com.tjshea.vigilant.data.tracker.PlacedBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-10-13: "as soon as I bet on something and the bet slip disappears, remove the bet from the list immediately. Add it back to the list only if the bet did not go through after
 * about 15 seconds." The provisional mark ([markProvisional]) hides the bet at once; [restoreAfterMiss] takes it off after the wait, unless a real Placed mark replaced it.
 */
class MissRestoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000_000L
    private fun placed() = PlacedBets(JsonFileStore(File(tmp.root, "placed.json"), PlacedBook.serializer(), { PlacedBook() }), clock = { now })

    private val target = BetTarget(
        market = NovigMarket("m1", "e1", "TEAM_TOTAL", "OPEN", "t", 0L, MarketFee.GAME, listOf(NovigOutcome("o1", "Over", "TBD"), NovigOutcome("o2", "Under", "TBD"))),
        outcomeId = "o1", league = "NHL", eventName = "A @ B", startsTs = now + 3_600_000L, marketLabel = "Team total", selection = "A Over 2.5",
        fair = 0.5, fairAsOfMs = now, source = "vigilant", placedKey = "m1/o1",
    )

    @Test fun `the bet is off the list the moment the slip closes`() = runTest {
        val p = placed()
        markProvisional(p, target, now)
        assertTrue(p.flow.value!!.bets.any { "m1/o1" in it.keys })
    }

    @Test fun `a miss brings it back after the wait, not before`() = runTest {
        val p = placed()
        markProvisional(p, target, now)
        val sent = now
        // 14 s in: the wait is not over, so a restore that starts then still waits the last second (virtual time) and only then removes the mark.
        now = sent + 14_000L
        restoreAfterMiss(p, target, sent, { now })
        assertTrue(p.flow.value!!.bets.none { "m1/o1" in it.keys })
    }

    @Test fun `a bet that filled keeps its mark - the real mark replaces the provisional one and is never removed`() = runTest {
        val p = placed()
        markProvisional(p, target, now)
        val sent = now
        p.mark(PlacedBet(key = "m1/o1", title = "A Over 2.5", odds = "+110", placedAtMs = now + 5_000L, startsAtMs = target.startsTs))
        restoreAfterMiss(p, target, sent, { now + 20_000L })
        assertEquals(1, p.flow.value!!.bets.count { "m1/o1" in it.keys })
    }

    @Test fun `a target with no list key marks nothing`() = runTest {
        val p = placed()
        markProvisional(p, target.copy(placedKey = null), now)
        assertTrue(p.flow.value?.bets.orEmpty().isEmpty())
    }
}
