package com.tjshea.vigilant.data.tracker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The `/bets/settle` answer shape is PropLine's docs and the real 2026-11-17 answers (research/propline_unused_features_2026-11-17.md). */
class PropLineSettleTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun answer(result: String, extra: String = "") = """{"summary":{"bets":1},"bets":[{"ref":"b1","result":"$result","legs":[]}]$extra}"""

    @Test
    fun onlyCleanResultsBecomeStatuses() {
        assertEquals(BetStatus.WON, PropLineSettle.statusOf(answer("won"), json, "b1"))
        assertEquals(BetStatus.LOST, PropLineSettle.statusOf(answer("lost"), json, "b1"))
        assertEquals(BetStatus.PUSH, PropLineSettle.statusOf(answer("push"), json, "b1"))
        assertEquals(BetStatus.VOID, PropLineSettle.statusOf(answer("void"), json, "b1"))
        // half results, unmatched, pending: the bet stays as it was
        for (r in listOf("half_won", "half_lost", "unmatched", "pending", "redacted")) assertNull(r, PropLineSettle.statusOf(answer(r), json, "b1"))
    }

    @Test
    fun aRedactedFreeKeyAnswerAndAnotherBetsRowAreNeverUsed() {
        assertNull(PropLineSettle.statusOf(answer("won", ""","redacted":true"""), json, "b1"))
        assertNull(PropLineSettle.statusOf(answer("won"), json, "other"))
        assertNull(PropLineSettle.statusOf("not json", json, "b1"))
    }

    private fun bet() = TrackedBet(
        id = "b1", createdAtMs = 0, league = "NBA", eventName = "Charlotte Hornets @ Los Angeles Lakers", startsTs = 1_000_000L, marketLabel = "Total", selection = "Over 215.5",
        marketId = "m", outcomeId = "o", price = 1.9, cost = 0.5, fairAtBet = 0.5, evPercentAtBet = 0.02, stake = 5.0,
    )
    private val game = GameScore(id = "pl:150791", league = "NBA", home = "Los Angeles Lakers", away = "Charlotte Hornets", startMs = 1_000_000L, final = true, called = false, homeScore = 110, awayScore = 105)

    @Test
    fun theRequestNamesThePropLineGameAndLine() {
        val total = PropLineSettle.bodyOf(bet(), BetGrader.Pick.Total(true, 215.5, BetGrader.Period.GAME), "basketball_nba", game)!![0] as JsonObject
        assertEquals("totals", (total["market"] as JsonPrimitive).content); assertEquals("Over", (total["selection"] as JsonPrimitive).content)
        assertEquals("215.5", (total["point"] as JsonPrimitive).content); assertEquals("150791", (total["event_id"] as JsonPrimitive).content); assertEquals("b1", (total["ref"] as JsonPrimitive).content)
        val spread = PropLineSettle.bodyOf(bet(), BetGrader.Pick.Spread("Lakers", -4.5, BetGrader.Period.GAME), "basketball_nba", game)!![0] as JsonObject
        assertEquals("Los Angeles Lakers", (spread["selection"] as JsonPrimitive).content); assertEquals("-4.5", (spread["point"] as JsonPrimitive).content)
        val ml = PropLineSettle.bodyOf(bet(), BetGrader.Pick.Moneyline("Charlotte Hornets"), "basketball_nba", game)!![0] as JsonObject
        assertEquals("h2h", (ml["market"] as JsonPrimitive).content); assertEquals("Charlotte Hornets", (ml["selection"] as JsonPrimitive).content)
    }

    @Test
    fun propsAndPeriodLinesAreNotAsked() {
        assertNull(PropLineSettle.bodyOf(bet(), BetGrader.Pick.Prop("A B", "POINTS", true, 20.5), "basketball_nba", game))
        assertNull(PropLineSettle.bodyOf(bet(), BetGrader.Pick.Total(true, 100.5, BetGrader.Period.FIRST_HALF), "basketball_nba", game))
        assertNull(PropLineSettle.bodyOf(bet(), BetGrader.Pick.Spread("Nobody Real", -1.0, BetGrader.Period.GAME), "basketball_nba", game))
    }
}
