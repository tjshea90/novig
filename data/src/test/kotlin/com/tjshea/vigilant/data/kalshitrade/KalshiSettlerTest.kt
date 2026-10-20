package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.book.BookRef
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** KV8: a pending bet ends only when Kalshi reports the market's settlement, and wins or loses by the side it bought. */
class KalshiSettlerTest {
    private val now = 1_790_500_000_000L

    private class Fake(val result: String) : KalshiTradingClient(OkHttpClient(), Json, { null }) {
        override suspend fun order(orderId: String): KalshiOrder? = KalshiOrder(orderId, null, "T-1", "no", "executed", 0.6, 0.4, 5.0, 5.0, 0.0, 2.0, 0.0, 0.1, 0.0, null, null)
        override suspend fun settlements(sinceMs: Long?, limit: Int): List<KalshiSettlement> =
            if (result.isEmpty()) emptyList() else listOf(KalshiSettlement("T-1", "E", result, 0.0, 0.0, 5.0, 2.0, 5.0, 0.1, now))
    }

    private suspend fun logged(): BetTracker {
        val t = BetTracker(File.createTempFile("kalshi-settle", ".json").also { it.delete() }, { now })
        val o = NovigOutcome("m|NO", "Under", "TBD", BookRef(null, "T-1|no", null))
        val m = NovigMarket("m", "e", "TOTAL", "OPEN", "d", now - 10_000, KalshiFees.TAKER, listOf(o))
        val target = BetTarget(m, "m|NO", "NFL", "A @ B", now - 10_000, "Total", "Under 40.5", 0.45, now, "vigilant", book = "Kalshi")
        t.logApi(target, "o-1", listOf(NovigFill("f1", "o-1", null, "m", "m|NO", 500, 2.0, true, 0.1, now - 100_000)))
        return t
    }

    @Test
    fun `the side held decides it - a NO bought wins when Kalshi settles NO`() = runTest {
        val t = logged()
        assertEquals(1, KalshiSettler(Fake("no"), t) { now }.run())
        assertEquals(BetStatus.WON, t.all().single().status)
    }

    @Test
    fun `and loses when Kalshi settles YES, voids on a scalar, and waits with no settlement`() = runTest {
        val a = logged()
        KalshiSettler(Fake("yes"), a) { now }.run()
        assertEquals(BetStatus.LOST, a.all().single().status)
        val b = logged()
        KalshiSettler(Fake("scalar"), b) { now }.run()
        assertEquals(BetStatus.VOID, b.all().single().status)
        val c = logged()
        assertEquals(0, KalshiSettler(Fake(""), c) { now }.run())
        assertEquals(BetStatus.PENDING, c.all().single().status)
    }
}
