package com.tjshea.vigilant.data.tracker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02 17:01Z: "include this information for all bets in the diagnosis feature. The diagnosis file can be as large and comprehensive as needed for Claude
 * to properly diagnose and fine tune the app … the goal is profit and positive EV and clv." Every bet as a JSON line, and the close split by its record.
 */
class BetLedgerTest {

    private val start = 1_800_000_000_000L
    private val now = start + 86_400_000L

    private fun bet(id: String, at: AtBet?, status: BetStatus = BetStatus.WON, closeFair: Double? = 0.52) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = start - 3_600_000L, league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Player Receptions",
        selection = "Joe Over 4.5", marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0,
        status = status, settledAtMs = if (status == BetStatus.PENDING) null else now, closingFair = closeFair, closingSeenAtMs = closeFair?.let { start - 60_000L },
        source = BetTracker.SOURCE_CNO, american = 100, atBet = at,
    )

    private fun at(agreeing: Int, twoSided: Int, dissent: List<String> = emptyList(), minutes: Long = 90, veto: String = "PASSED", book: String? = "Kalshi") = AtBet(
        atMs = start - 3_600_000L, how = AtBet.HOW_AUTO, scanner = "CNO", preset = "Volume + safe CLV", league = "NFL", sport = "FOOTBALL", kind = "PROP",
        minutesToStart = minutes, american = 100, available = 120.0, ev = 0.04, checkEv = 0.035, twoSided = twoSided, agreeing = agreeing, dissent = dissent,
        sharpVerdict = veto, sharpBook = book, pageAgeSec = 12, books = listOf(AtBetBook("Kalshi", -105, -115, 0.49, 0.02)),
    )

    @Test
    fun `each bet is one JSON line with its record as placed, its close and its result`() {
        val b = bet("abcd1234", at(4, 4))
        val line = BetLedger.line(b, now)
        assertFalse(line.contains("\n"))
        val o = Json.parseToJsonElement(line).jsonObject
        assertEquals("abcd1234", o["id"]!!.jsonPrimitive.content)
        assertEquals("WON", o["status"]!!.jsonPrimitive.content)
        assertEquals(1.0, o["profit"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(0.04, o["clv"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        val a = o["atBet"]!!.jsonObject
        assertEquals("Kalshi", a["sharpBook"]!!.jsonPrimitive.content)
        assertEquals("90", a["minutesToStart"]!!.jsonPrimitive.content)
        assertTrue(line, line.contains("\"books\":[{\"book\":\"Kalshi\",\"odds\":-105,\"other\":-115"))
        // A bet from before v0.45.0 has no record, and still has its close and result.
        val old = Json.parseToJsonElement(BetLedger.line(bet("old", null), now)).jsonObject
        assertFalse(old.containsKey("atBet"))
        assertTrue(old.containsKey("clv"))
    }

    @Test
    fun `the record splits bets by agreement, dissent, the veto, the start and more`() {
        assertEquals("every one (4 of 4)", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.AGREEMENT))
        assertEquals("2 of 9 not agreeing", BetLedger.keyOf(bet("a", at(7, 9, listOf("DraftKings", "Caesars"))), BetLedger.Split.AGREEMENT))
        assertEquals("2", BetLedger.keyOf(bet("a", at(7, 9, listOf("DraftKings", "Caesars"))), BetLedger.Split.DISSENT))
        assertEquals("none", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.DISSENT))
        // A ✓ on a notification: no page, so no names, but its counts say 2 of 5 disagreed (it used to read "none").
        assertEquals("2", BetLedger.keyOf(bet("a", at(3, 5).copy(books = emptyList())), BetLedger.Split.DISSENT))
        assertEquals("none", BetLedger.keyOf(bet("a", at(5, 5).copy(books = emptyList())), BetLedger.Split.DISSENT))
        assertEquals("PASSED", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.SHARP))
        assertEquals("Kalshi agreed", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.SHARP_BOOK))
        assertEquals("none on the page", BetLedger.keyOf(bet("a", at(4, 4, veto = "NO_SHARP", book = null)), BetLedger.Split.SHARP_BOOK))
        assertEquals("30 min-2 h", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.LEAD))
        assertEquals("24 h or more", BetLedger.keyOf(bet("a", at(4, 4, minutes = 2_000)), BetLedger.Split.LEAD))
        assertEquals("3–4%", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.CHECK_EV))
        assertEquals("4-5", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.TWO_SIDED))
        assertEquals("Player props", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.KIND))
        assertEquals("Football", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.SPORT))
        assertEquals("Volume + safe CLV", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.PRESET))
        assertEquals("auto", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.HOW))
        assertEquals("$100-500", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.LIQUIDITY))
        assertEquals("under 30 s", BetLedger.keyOf(bet("a", at(4, 4)), BetLedger.Split.PAGE_AGE))
        for (split in BetLedger.Split.entries - BetLedger.Split.LEAD) assertEquals(split.name, BetLedger.NOT_RECORDED, BetLedger.keyOf(bet("old", null), split))
    }

    @Test
    fun `the time to the start is known for every bet, recorded as placed or not (RESEARCH 71: the trap guard's split)`() {
        val old = bet("old", null)
        val lead = (old.startsTs - old.createdAtMs) / 60_000L
        assertEquals(BetLedger.leadBand(lead), BetLedger.keyOf(old, BetLedger.Split.LEAD))
        assertTrue(BetLedger.keyOf(old, BetLedger.Split.LEAD) != BetLedger.NOT_RECORDED)
        // The record's own minutes win when there is one.
        assertEquals("24 h or more", BetLedger.keyOf(bet("a", at(4, 4, minutes = 2_000)), BetLedger.Split.LEAD))
        assertEquals(BetLedger.NOT_RECORDED, BetLedger.keyOf(old.copy(startsTs = 0), BetLedger.Split.LEAD))
    }

    @Test
    fun `a split's rows carry each group's close and results`() {
        val bets = listOf(bet("a", at(4, 4)), bet("b", at(4, 4), BetStatus.LOST, 0.48), bet("c", at(7, 9, listOf("DraftKings", "Caesars"))), bet("d", null))
        val rows = BetLedger.of(bets, BetLedger.Split.DISSENT, now).associateBy { it.label }
        assertEquals(setOf("none", "2", BetLedger.NOT_RECORDED), rows.keys)
        val none = rows.getValue("none").stats
        assertEquals(2, none.bets)
        assertEquals(1, none.won)
        assertEquals(1, none.lost)
        assertEquals(0.0, none.averageClv!!, 1e-9)
        assertEquals(0.04, rows.getValue("2").stats.averageClv!!, 1e-9)
    }
}
