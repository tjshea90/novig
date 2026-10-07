package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-10-07: "make the app bet logging differentiate from bets and bids … so I can see stats and ev filtered my bids as well as bets, and also for the diagnostics
 * and studies sections." One rule says which a record is ([TrackedBet.isBid]); the tags an older record lacks are stamped ([BetTracker.tagBids]); every file says it.
 */
class BetOrBidTest {

    @get:Rule val tmp = TemporaryFolder()

    private val start = 1_800_000_000_000L
    private val now = start + 86_400_000L

    private fun at(how: String) = AtBet(atMs = start - 3_600_000L, how = how, scanner = BetTracker.SOURCE_VIGILANT, league = "NFL", kind = "PROP", minutesToStart = 60, american = 100, ev = 0.04)

    private fun bet(
        id: String,
        how: String? = null,
        maker: Boolean = false,
        orderId: String? = null,
        status: BetStatus = BetStatus.WON,
        ev: Double = 0.04,
        lockFor: String? = null,
    ) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = start - 3_600_000L, league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Player Receptions",
        selection = "Joe Over 4.5", marketId = "m", outcomeId = "o-$id", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = ev, stake = 1.0,
        status = status, settledAtMs = if (status == BetStatus.PENDING) null else now, closingFair = 0.52, closingSeenAtMs = start - 60_000L,
        source = BetTracker.SOURCE_VIGILANT, american = 100, atBet = how?.let(::at), maker = maker, orderId = orderId, lockFor = lockFor,
    )

    @Test
    fun `a record is a bid when either tag says so, and a bet otherwise (never from price, time or size)`() {
        assertTrue("the make-order flag alone (a fill logged before the second tag)", bet("a", maker = true).isBid)
        assertTrue("the record as placed alone", bet("b", how = AtBet.HOW_BID).isBid)
        assertTrue(bet("c", how = AtBet.HOW_BID, maker = true).isBid)
        for (how in listOf(AtBet.HOW_AUTO, AtBet.HOW_SHEET, AtBet.HOW_MARKED, AtBet.HOW_ALERT, AtBet.HOW_STUDY)) assertFalse(how, bet("d", how = how).isBid)
        assertFalse("a bet with no record as placed", bet("e").isBid)
        assertFalse("an API bet is still a taker order", bet("f", orderId = "o1").isBid)
        assertFalse("a lock is a taker order", bet("g", how = AtBet.HOW_AUTO, lockFor = "x").isBid)
        assertEquals(BetOrBid.BID, BetOrBid.of(bet("a", maker = true)))
        assertEquals(BetOrBid.BET, BetOrBid.of(bet("e")))
    }

    @Test
    fun `only keeps the kind asked for, and everything for none`() {
        val all = listOf(bet("a", maker = true), bet("b"), bet("c", how = AtBet.HOW_BID), bet("d", how = AtBet.HOW_AUTO))
        assertEquals(listOf("a", "c"), BetOrBid.only(all, BetOrBid.BID).map { it.id.take(1) })
        assertEquals(listOf("b", "d"), BetOrBid.only(all, BetOrBid.BET).map { it.id.take(1) })
        assertEquals(4, BetOrBid.only(all, null).size)
    }

    @Test
    fun `tagBids gives a bid's record both tags, from the bid store's orders, and touches nothing else`() = runTest {
        val file = File(tmp.root, "bets.json")
        // Written the way an older app wrote them.
        file.writeText(
            Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()),
                listOf(
                    bet("imp", orderId = "bid-1").copy(imported = true), // imported from Novig's fills before the desk merged it
                    bet("old", maker = true), // logged before the second tag: no record as placed
                    bet("half", how = AtBet.HOW_AUTO, maker = true), // a record as placed that says auto
                    bet("tap", how = AtBet.HOW_SHEET, orderId = "taker-1"), // a taker bet
                    bet("done", how = AtBet.HOW_BID, maker = true), // already both
                ),
            ),
        )
        val fresh = BetTracker(file, clock = { now })
        assertEquals(3, fresh.tagBids(setOf("bid-1", "bid-2")))
        val byId = fresh.all().associateBy { it.id.take(4) }
        val imp = byId.getValue("imp-")
        assertTrue(imp.maker)
        assertNull("nothing is invented for a record that has no record as placed", imp.atBet)
        assertTrue(byId.getValue("old-").maker)
        assertNull(byId.getValue("old-").atBet)
        assertEquals(AtBet.HOW_BID, byId.getValue("half").atBet!!.how)
        assertFalse(byId.getValue("tap-").isBid)
        assertEquals(AtBet.HOW_SHEET, byId.getValue("tap-").atBet!!.how)
        assertEquals("a second look finds nothing left to stamp", 0, fresh.tagBids(setOf("bid-1", "bid-2")))
        assertEquals(1, fresh.all().count { !it.isBid })
    }

    @Test
    fun `tagBids with no bids leaves a taker-only store alone`() = runTest {
        val file = File(tmp.root, "bets.json")
        file.writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), listOf(bet("a", how = AtBet.HOW_AUTO), bet("b"))))
        val t = BetTracker(file, clock = { now })
        assertEquals(0, t.tagBids(emptySet()))
        assertEquals(0, t.all().count { it.isBid })
    }

    @Test
    fun `every ledger line says bet or bid, and so does the split, with no record as placed needed`() {
        val bid = Json.parseToJsonElement(BetLedger.line(bet("a", maker = true), now)).jsonObject
        val taker = Json.parseToJsonElement(BetLedger.line(bet("b"), now)).jsonObject
        assertEquals("bid", bid["made"]!!.jsonPrimitive.content)
        assertEquals("bet", taker["made"]!!.jsonPrimitive.content)
        assertEquals(BetOrBid.BID.group, BetLedger.keyOf(bet("a", maker = true), BetLedger.Split.MADE))
        assertEquals(BetOrBid.BET.group, BetLedger.keyOf(bet("b"), BetLedger.Split.MADE))
        val rows = BetLedger.of(listOf(bet("a", maker = true), bet("c", how = AtBet.HOW_BID), bet("b"), bet("d", how = AtBet.HOW_AUTO)), BetLedger.Split.MADE, now).associateBy { it.label }
        assertEquals(2, rows.getValue(BetOrBid.BID.group).stats.bets)
        assertEquals(2, rows.getValue(BetOrBid.BET.group).stats.bets)
    }

    @Test
    fun `the Tracker's breakdown splits bets from bids with their own EV and results`() {
        val list = listOf(
            bet("a", maker = true, ev = 0.06), bet("b", maker = true, ev = 0.02, status = BetStatus.LOST),
            bet("c", ev = 0.03), bet("d", ev = 0.03), bet("e", ev = 0.03, status = BetStatus.LOST),
        )
        val rows = TrackerBreakdown.of(list, TrackerBreakdown.By.MADE).associateBy { it.label }
        val bids = rows.getValue(BetOrBid.BID.group).stats
        val bets = rows.getValue(BetOrBid.BET.group).stats
        assertEquals(2, bids.bets)
        assertEquals(3, bets.bets)
        assertEquals(0.04, bids.averageEv!!, 1e-9)
        assertEquals(0.03, bets.averageEv!!, 1e-9)
        assertEquals(1, bids.won)
        assertEquals(1, bids.lost)
        assertEquals(2, bets.won)
        // The words for a group say bids for bids.
        assertTrue(TrackerBreakdown.describe(bids, closed = 2, noun = "bid").startsWith("2 bids (0 open) · 1-1"))
        assertTrue(TrackerBreakdown.describe(bets, closed = 3).startsWith("3 bets (0 open) · 2-1"))
        assertTrue(TrackerBreakdown.describe(bids, closed = 2, noun = "bid").contains("EV when bet +4.0%"))
    }
}
