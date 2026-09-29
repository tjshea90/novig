package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Tj's 2026-09-29 screenshot: three open bets on finished games the Tracker said it couldn't grade. Graded here from the REAL
 * feeds (ESPN for the two football games, MLB's Stats API for Kade Anderson's start). Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveUngradedBetsTest' -i`.
 */
class LiveUngradedBetsTest {
    @Test
    fun `the three bets that could not be graded now settle from the real box scores`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val sunday = Instant.parse("2026-09-27T17:00:00Z").toEpochMilli()
        val mariners = Instant.parse("2026-09-27T01:40:00Z").toEpochMilli()
        fun bet(id: String, league: String, event: String, start: Long, market: String, selection: String) = TrackedBet(
            id = id, createdAtMs = start - 3_600_000L, league = league, eventName = event, startsTs = start, marketLabel = market, selection = selection,
            marketId = "", outcomeId = "", price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
        )
        val bets = listOf(
            bet("rush", "NFL", "Carolina Panthers @ Cleveland Browns", sunday, "Player Rushing Yards", "KC Concepcion Over 5.5"),
            bet("rec", "NFL", "Cincinnati Bengals @ Pittsburgh Steelers", sunday, "Player Receptions", "Erick All Jr. Under 0.5"),
            bet("er", "MLB", "Los Angeles Angels @ Seattle Mariners", mariners, "Player Earned Runs Allowed", "Kade Anderson Over 1.5"),
        )
        val file = File.createTempFile("bets", ".json").also { it.delete() }
        file.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets))
        val tracker = BetTracker(file)
        val report = BetSettler(tracker, FreeScores(OkHttpClient())).run(force = true)
        val byId = tracker.all().associateBy { it.id }
        byId.values.forEach { println("LIVE UNGRADED ${it.id}: ${it.selection} (${it.marketLabel}) -> ${it.status} · ${it.gradeNote}") }
        println("LIVE UNGRADED report $report")
        assertEquals(BetStatus.LOST, byId.getValue("rush").status) // no rushing yards: 0
        assertEquals(BetStatus.WON, byId.getValue("rec").status) // played, caught nothing
        assertEquals(BetStatus.LOST, byId.getValue("er").status) // 0 earned runs in 3.1 innings
        assertEquals(3, report.settled)
    }
}
