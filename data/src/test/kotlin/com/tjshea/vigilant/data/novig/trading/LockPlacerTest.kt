package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Placing a lock (Tj, 2026-10-02 ~18:50Z: "take the other side of the bet later on and guarantee a profit no matter which side of the bet wins … It must
 * guarantee profit because I will put real money on it"). Against a fake Novig: what goes out (one fill-or-kill order at the lock's limit), what it
 * refuses to send, and how the fills are recorded. RESEARCH.md §67.
 */
class LockPlacerTest {

    private var now = 1_800_000_000_000L
    private val startsTs = now + 3 * 3_600_000L

    private val market = NovigMarket(
        marketId = "mkt", eventId = "ev", marketType = "MONEYLINE", status = "OPEN", description = "B @ A", startsTs = startsTs,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    /** Bids on A at 0.45 (so B can be bought for 0.55, 5,000 contracts), and on B at 0.53. */
    private fun book(at: Long = now, bidA: Int = 450) = NovigBook("mkt", 1, mapOf("A" to listOf(BidLevel(bidA, 5_000)), "B" to listOf(BidLevel(530, 5_000))), at)

    private inner class FakeNovig(
        var positions: List<NovigPosition> = listOf(NovigPosition("mkt", "A", 1_000, 4.0)),
        val fillPrice: Double = 0.55,
        val rejectFok: Boolean = false,
        val positionsFail: Boolean = false,
    ) : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val sent = ArrayList<List<Any>>()
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
            sent += listOf(outcomeId, price, qty, tif)
            return "o-lock"
        }
        override suspend fun order(orderId: String): NovigOrder =
            NovigOrder(orderId, null, "mkt", "B", 0.55, 1_000, 0, "FOK", if (rejectFok) "REJECTED" else "FILLED", now)
        override suspend fun fills(orderId: String?, limit: Int): List<NovigFill> =
            if (rejectFok) emptyList() else listOf(NovigFill("f1", "o-lock", null, "mkt", "B", 1_000, 1_000 * 0.01 * fillPrice, true, 0.0, now))
        override suspend fun positions(marketId: String?): List<NovigPosition> {
            if (positionsFail) throw java.io.IOException("no connection")
            return positions
        }
    }

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })

    /** An API bet on A: 1,000 contracts for $4.00 (0.40). */
    private suspend fun placedA(t: BetTracker): TrackedBet {
        val target = BetTarget(market, "A", "NBA", "B @ A", startsTs, "Moneyline", "Team A", fair = 0.42, fairAsOfMs = now, source = BetTracker.SOURCE_CNO)
        return t.logApi(target, "o-first", listOf(NovigFill("f0", "o-first", null, "mkt", "A", 1_000, 4.0, true, 0.0, now - 60_000)))!!.copy()
    }

    private fun placer(t: BetTracker, novig: FakeNovig, book: NovigBook? = book(), paused: Boolean = false) =
        ApiBetPlacer(novig, t, books = { book }, limits = { BetLimits(10.0, 50.0) }, paused = { paused }, clock = { now }, pause = { now += it })

    private fun holding(t: BetTracker) = runBlocking { LockPositions.of(t.all(), now).single() }

    @Test
    fun `a lock goes out as one fill-or-kill order for the other side at the lock's limit, and is recorded as the bet's lock`() = runBlocking {
        val t = tracker()
        val first = placedA(t)
        val novig = FakeNovig()
        val r = placer(t, novig).placeLock(holding(t), market, minProfit = 0.01, pushable = false, auto = false) as PlaceResult.Placed
        assertEquals(listOf(listOf<Any>("B", 0.55, 1_000L, "FOK")), novig.sent)
        val lock = r.bet
        assertEquals(first.id, lock.lockFor)
        assertTrue(lock.isLock)
        assertEquals("B", lock.outcomeId)
        assertEquals(1_000L, lock.contracts)
        assertEquals(5.50, lock.stake, 1e-9)
        assertTrue(lock.gradeNote!!.startsWith("Lock through Novig's API"))
        // Both sides now held equally: $10 either way against $9.50 spent.
        val after = LockPositions.of(t.all(), now).single()
        assertEquals(1_000L, after.held.getValue("A").contracts)
        assertEquals(1_000L, after.held.getValue("B").contracts)
        assertEquals(9.50, after.spent, 1e-9)
        // And nothing more to lock.
        novig.positions = listOf(NovigPosition("mkt", "A", 1_000, 4.0), NovigPosition("mkt", "B", 1_000, 5.5))
        assertTrue(placer(t, novig).placeLock(after, market, 0.01, pushable = false, auto = false) is PlaceResult.Refused)
        assertEquals(1, novig.sent.size)
    }

    @Test
    fun `once settled, a locked pair's money is the locked profit, and the record, EV and CLV count only the pick`() = runBlocking {
        val t = tracker()
        val first = placedA(t)
        val lock = (placer(t, FakeNovig()).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Placed).bet
        t.settle(first.id, com.tjshea.vigilant.data.tracker.BetStatus.WON)
        t.settle(lock.id, com.tjshea.vigilant.data.tracker.BetStatus.LOST)
        val stats = BetTracker.stats(t.all(), now)
        // $6.00 won on A, $5.50 lost on the lock: $0.50, what the lock promised.
        assertEquals(0.50, stats.profit, 1e-9)
        assertEquals(9.50, stats.staked, 1e-9)
        assertEquals(1, stats.won)
        assertEquals(0, stats.lost)
        assertEquals(1, stats.bets)
        assertEquals(1, stats.locks)
        assertEquals(first.evPercentAtBet!!, stats.averageEv!!, 1e-9)
    }

    @Test
    fun `nothing is sent when Novig's positions don't match the Tracker, or can't be read`() = runBlocking {
        val t = tracker()
        placedA(t)
        // Novig holds 1,200 of A (a fill the Tracker never synced): a lock worked out from 1,000 could lose.
        val off = FakeNovig(positions = listOf(NovigPosition("mkt", "A", 1_200, 4.8)))
        val r = placer(t, off).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Refused
        assertTrue(r.reason, r.reason.contains("Sync with Novig"))
        assertTrue(off.sent.isEmpty())
        val down = FakeNovig(positionsFail = true)
        assertTrue((placer(t, down).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Refused).reason.contains("positions couldn't be read"))
        assertTrue(down.sent.isEmpty())
    }

    @Test
    fun `nothing is sent when the price doesn't lock, the book is old, the market is closed, or paused for auto`() = runBlocking {
        val t = tracker()
        placedA(t)
        val novig = FakeNovig()
        // A's bid at 0.40: B costs 0.60, and $4 + $6 = $10 locks nothing.
        assertTrue((placer(t, novig, book(bidA = 400)).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Refused).reason.contains("a lock needs"))
        assertTrue((placer(t, novig, book(at = now - 60_000)).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Refused).reason.contains("seconds old"))
        assertTrue((placer(t, novig, null).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Refused).reason.contains("couldn't be read"))
        assertTrue((placer(t, novig).placeLock(holding(t), market.copy(status = "CLOSED"), 0.01, pushable = false, auto = false) as PlaceResult.Refused).reason.contains("closed"))
        assertTrue((placer(t, novig, paused = true).placeLock(holding(t), market, 0.01, pushable = false, auto = true) as PlaceResult.Refused).reason.contains("paused"))
        // The minimum profit: $0.50 is what this lock pays; asking $0.60 sends nothing.
        assertTrue(placer(t, novig).placeLock(holding(t), market, 0.60, pushable = false, auto = false) is PlaceResult.Refused)
        assertTrue(novig.sent.isEmpty())
    }

    @Test
    fun `a fill-or-kill that Novig ends unfilled moved no money and logs nothing`() = runBlocking {
        val t = tracker()
        placedA(t)
        val novig = FakeNovig(rejectFok = true)
        val r = placer(t, novig).placeLock(holding(t), market, 0.01, pushable = false, auto = true)
        assertTrue(r.toString(), r is PlaceResult.NotFilled)
        assertEquals(1, t.all().size)
        assertNull(t.all().single().lockFor)
    }

    @Test
    fun `a lock doesn't use up the day's limit for other bets`() = runBlocking {
        val t = tracker()
        placedA(t)
        val novig = FakeNovig()
        placer(t, novig).placeLock(holding(t), market, 0.01, pushable = false, auto = false) as PlaceResult.Placed
        // $4 of bets and a $5.50 lock today; a $10 limit per day leaves $6 for bets, not $0.50.
        val limits = BetLimits(maxStake = 10.0, maxPerDay = 10.0)
        val market2 = market.copy(marketId = "mkt2", outcomes = listOf(NovigOutcome("C", "Team C", "TBD"), NovigOutcome("D", "Team D", "TBD")))
        val book2 = NovigBook("mkt2", 1, mapOf("D" to listOf(BidLevel(450, 5_000)), "C" to listOf(BidLevel(400, 5_000))), now)
        val p = ApiBetPlacer(novig, t, books = { book2 }, limits = { limits }, clock = { now }, dayStart = { now - 3_600_000L }, pause = { })
        val other = BetTarget(market2, "C", "NBA", "D @ C", startsTs, "Moneyline", "Team C", fair = 0.60, fairAsOfMs = now, source = BetTracker.SOURCE_CNO)
        val r = p.plan(other, 6.0, limitsOverride = limits)
        // $4 + $6 = the $10 limit exactly (with the lock counted it would be $15.50, refused).
        assertTrue(r.toString(), r is PlanResult.Ready)
    }
}
