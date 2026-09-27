package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigHttpException
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tj, 2026-09-27: "keep track whether each bet was a win or a loss … a background scores system". */
class BetSettlerTest {

    @get:Rule val tmp = TemporaryFolder()
    private val start = 1_790_528_400_000L
    private var now = start + 3 * 60 * 60_000L

    private fun tracker() = BetTracker(File(tmp.root, "bets.json"), clock = { now })

    private fun bet(id: String, market: String = "m-$id", outcome: String = "o-$id", startsTs: Long = start, cost: Double = 0.5) = TrackedBet(
        id = id, createdAtMs = startsTs - 3_600_000L, league = "NFL", eventName = "Seattle Seahawks @ Washington Commanders", startsTs = startsTs,
        marketLabel = "Moneyline", selection = "SEA", marketId = market, outcomeId = outcome, price = cost, cost = cost,
        fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0,
    )

    private fun market(id: String, vararg outcomes: Pair<String, String>) =
        NovigMarket(id, "E1", "MONEY", "SETTLED", "SEA", start, MarketFee.GAME, outcomes.map { (o, st) -> NovigOutcome(o, o, st) })

    private suspend fun BetTracker.seed(vararg bets: TrackedBet) {
        // Through the public API: log then rewrite, the way the app's own writes go.
        for (b in bets) {
            importPlaced(emptyList())
            edit(b.id) { it }
        }
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()),
        )
    }

    @Test
    fun `Novig's WIN, LOSS, PUSH and a fair-value payout settle each bet, TBD waits`() = runTest {
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()),
                listOf(bet("w"), bet("l"), bet("p"), bet("f"), bet("t")),
            ),
        )
        val t = tracker()
        val markets = mapOf(
            "m-w" to market("m-w", "o-w" to "WIN", "x" to "LOSS"),
            "m-l" to market("m-l", "o-l" to "LOSS"),
            "m-p" to market("m-p", "o-p" to "PUSH"),
            "m-f" to market("m-f", "o-f" to "0.47"),
            "m-t" to market("m-t", "o-t" to "TBD"),
        )
        val settler = BetSettler(t, market = { markets[it] }, resolve = { null }, clock = { now })
        val report = settler.run()
        assertEquals(4, report.settled)
        val byId = t.all().associateBy { it.id }
        assertEquals(BetStatus.WON, byId.getValue("w").status)
        assertEquals(1.0, byId.getValue("w").profit!!, 1e-9)
        assertEquals(BetStatus.LOST, byId.getValue("l").status)
        assertEquals(-1.0, byId.getValue("l").profit!!, 1e-9)
        assertEquals(BetStatus.PUSH, byId.getValue("p").status)
        assertEquals(BetStatus.FMV, byId.getValue("f").status)
        assertEquals(-0.06, byId.getValue("f").profit!!, 1e-9) // 0.47 back per 0.50 spent
        assertEquals(BetSettler.BY_NOVIG, byId.getValue("w").settledBy)
        assertEquals(BetStatus.PENDING, byId.getValue("t").status)
        // Undecided: asked again only after a while.
        assertEquals(0, settler.run().asked)
        now += BetSettler.RETRY_MS
        assertEquals(1, settler.run().asked)
    }

    @Test
    fun `games not an hour old, and results Tj tapped (or undid), are left alone`() = runTest {
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()),
                listOf(bet("young", startsTs = now - 30 * 60_000L), bet("tapped")),
            ),
        )
        val t = tracker()
        t.settle("tapped", BetStatus.LOST)
        t.settle("tapped", BetStatus.PENDING) // undo: still his call
        var asked = 0
        val settler = BetSettler(t, market = { asked++; market(it, "o-young" to "WIN", "o-tapped" to "WIN") }, resolve = { null }, clock = { now })
        settler.run()
        assertEquals(0, asked)
        assertEquals(listOf(BetStatus.PENDING, BetStatus.PENDING), t.all().map { it.status })
    }

    @Test
    fun `a bet without Novig's ids is looked up once, kept, then settled`() = runTest {
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()),
                listOf(bet("cno", market = "", outcome = "")),
            ),
        )
        val t = tracker()
        var lookups = 0
        val settler = BetSettler(t, market = { if (it == "m9") market("m9", "o9" to "LOSS") else null }, resolve = { lookups++; "m9" to "o9" }, clock = { now })
        assertEquals(1, settler.run().settled)
        val b = t.all().single()
        assertEquals("m9", b.marketId)
        assertEquals("o9", b.outcomeId)
        assertEquals(BetStatus.LOST, b.status)
        assertEquals(1, lookups)
    }

    @Test
    fun `Novig busy stops the pass - nothing is guessed, the rest go next time`() = runTest {
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()),
                listOf(bet("a"), bet("b", startsTs = start + 1)),
            ),
        )
        val t = tracker()
        val settler = BetSettler(t, market = { throw NovigHttpException(429, "busy", 5) }, resolve = { null }, clock = { now })
        val r = settler.run()
        assertEquals(true, r.stopped)
        assertEquals(1, r.asked)
        assertEquals(listOf(BetStatus.PENDING, BetStatus.PENDING), t.all().map { it.status })
    }

    @Test
    fun `Novig's statuses read as results`() {
        assertEquals(BetStatus.WON to null, BetSettler.resultOf("WIN"))
        assertEquals(BetStatus.LOST to null, BetSettler.resultOf("LOSS"))
        assertEquals(BetStatus.FMV to 0.25, BetSettler.resultOf("0.25"))
        assertNull(BetSettler.resultOf("TBD"))
        assertNull(BetSettler.resultOf("1.5"))
    }
}
