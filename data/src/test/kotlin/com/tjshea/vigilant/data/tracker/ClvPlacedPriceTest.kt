package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-10-01: "make sure the app is properly tracking clv based on real closing lines and the actual odds I placed the bet at". CLV is the
 * closing fair probability over the COST of the bet, minus 1: the cost is the price actually paid (a ✓ at the price shown, the price Tj corrected
 * it to, or the average of Novig's fills plus their fee), so worked numbers here pin every way a bet's price gets in.
 */
class ClvPlacedPriceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val min = 60_000L
    private val start = 1_800_000_000_000L
    private val placedAt = start - 60 * min

    private fun tracker() = BetTracker(File(tmp.root, "bets.json"), clock = { placedAt })

    private val row = CnoRow(
        0.05, start, "Football", "NFL", "Houston Texans @ Indianapolis Colts", "Moneyline", "Houston Texans",
        141, 60.0, "Novig", 120, 0.4375, 8, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=9", betUrl = "https://crazyninjaodds.com/d?l=9",
    )

    /** [b] with the line read [beforeStart] before the start (the close), seen from [now] after the start. */
    private fun closed(b: TrackedBet, fair: Double, beforeStart: Long = 2 * min) = b.copy(closingFair = fair, closingSeenAtMs = start - beforeStart)

    @Test
    fun `a check at +141 is measured from +141, the price shown, whatever the line did after`() = runTest {
        val bet = closed(tracker().logCno(row, ev = 0.05, live = false, placedKey = "cno:a"), fair = 0.45)
        assertEquals(141, bet.american)
        // +141 pays 2.41 per $1: cost 1 / 2.41 = 0.41494. The close was 0.45: 0.45 × 2.41 − 1 = +8.45%.
        assertEquals(1 / 2.41, bet.cost, 1e-12)
        assertEquals(0.45 * 2.41 - 1, ClosingLine.clv(bet, start)!!, 1e-12)
        assertEquals(0.0845, ClosingLine.clv(bet, start)!!, 1e-12)
        // The close moved to 0.40 (the bet looked worse afterwards): −3.6%, not a recomputation from the line then.
        assertEquals(0.40 * 2.41 - 1, ClosingLine.clv(bet.copy(closingFair = 0.40), start)!!, 1e-12)
    }

    @Test
    fun `the price Tj says he got replaces the one shown, and the CLV follows it`() = runTest {
        val t = tracker()
        val shown = t.logCno(row, ev = 0.05, live = false, placedKey = "cno:a")
        t.setPrice(shown.id, 150)
        val got = closed(t.all().single(), fair = 0.45)
        // +150 pays 2.50 per $1: cost 0.40. 0.45 / 0.40 − 1 = +12.5%.
        assertEquals(0.4, got.cost, 1e-12)
        assertEquals(0.125, ClosingLine.clv(got, start)!!, 1e-12)
        // A worse price than shown: −110 costs 110/210 = 0.52381; the close 0.50 = −4.5%.
        t.setPrice(shown.id, -110)
        assertEquals(0.5 / (110.0 / 210) - 1, ClosingLine.clv(closed(t.all().single(), fair = 0.50), start)!!, 1e-12)
    }

    @Test
    fun `a bet placed through Novig's API is measured from the average of its fills plus the fee`() = runTest {
        val market = NovigMarket("mkt", "ev", "MONEY", "OPEN", "Team A vs Team B", start, MarketFee.GAME, listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")))
        val target = BetTarget(market, "A", "NFL", "Team B @ Team A", start, "Moneyline", "Team A", fair = 0.5, fairAsOfMs = null, source = BetTracker.SOURCE_VIGILANT, placedKey = "k")
        // 400 contracts in two fills: 100 at 45¢ and 300 at 47¢ = $1.86 for $4.00 of payout: an average of 46.5¢, a pregame fill with no fee.
        val fills = listOf(
            NovigFill("f1", "o1", null, "mkt", "A", 100, 0.45, true, 0.0, placedAt),
            NovigFill("f2", "o1", null, "mkt", "A", 300, 1.41, true, 0.0, placedAt + 1_000),
        )
        val bet = closed(tracker().logApi(target, "o1", fills)!!, fair = 0.50)
        assertEquals(0.465, bet.price, 1e-12)
        assertEquals(0.465, bet.cost, 1e-12)
        assertEquals(1.86, bet.stake, 1e-12)
        assertEquals(0.5 / 0.465 - 1, ClosingLine.clv(bet, start)!!, 1e-12)
        // With a fee (a live fill would have one): the cost is price plus fee per payout dollar, and the CLV is measured from that.
        val withFee = closed(tracker().logApi(target, "o2", listOf(NovigFill("f3", "o2", null, "mkt", "A", 400, 1.86, true, 0.04, placedAt))) !!, fair = 0.50)
        assertEquals(0.475, withFee.cost, 1e-12)
        assertEquals(0.5 / 0.475 - 1, ClosingLine.clv(withFee, start)!!, 1e-12)
        assertTrue(ClosingLine.clv(withFee, start)!! < ClosingLine.clv(bet, start)!!)
    }

    @Test
    fun `a bet placed live (after its start) has no CLV however its price compares`() = runTest {
        val t = BetTracker(File(tmp.root, "live.json"), clock = { start + 10 * min })
        val live = closed(t.logCno(row, ev = 0.05, live = true, placedKey = "cno:live"), fair = 0.45)
        assertNull(ClosingLine.clv(live, start + 3_600_000L))
    }

    @Test
    fun `the average CLV is each bet's own price against its own close, averaged`() = runTest {
        val t = tracker()
        val a = closed(t.logCno(row, 0.05, false, "cno:a"), fair = 0.45)                       // +141: +8.45%
        val b = closed(t.logCno(row.copy(odds = -110, bet = "Other"), 0.05, false, "cno:b"), fair = 0.50, beforeStart = 4 * min) // −110: 0.5 / (110/210) − 1
        val s = ClvStats.of(listOf(a, b), now = start + min)
        assertEquals(2, s.closed)
        assertEquals((0.45 * 2.41 - 1 + (0.5 / (110.0 / 210) - 1)) / 2, s.averageClv!!, 1e-12)
        assertEquals(1, s.beat)
    }
}
