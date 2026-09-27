package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.tracker.BetGrader
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
 * Settles REAL bets from REAL final scores (ESPN, MLB's Stats API): Mets 7 @ Nationals 1
 * (2026-09-26) and Falcons 35 @ Packers 14 (2026-09-24). Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveScoresTest'`.
 */
class LiveScoresTest {
    @Test
    fun `real scores settle real bets`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val mets = Instant.parse("2026-09-26T16:35:00Z").toEpochMilli()
        val falcons = Instant.parse("2026-09-25T00:15:00Z").toEpochMilli()
        fun bet(id: String, league: String, event: String, start: Long, market: String, selection: String) = TrackedBet(
            id = id, createdAtMs = start, league = league, eventName = event, startsTs = start, marketLabel = market, selection = selection,
            marketId = "", outcomeId = "", price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
        )
        val bets = listOf(
            bet("ml", "MLB", "New York Mets @ Washington Nationals", mets, "Moneyline", "New York Mets"),
            bet("f5", "MLB", "New York Mets @ Washington Nationals", mets, "1st 5 Innings Total Runs", "Under 4.5"),
            bet("k", "MLB", "New York Mets @ Washington Nationals", mets, "Player Pitching Strikeouts", "Jonah Tong Over 7.5"),
            bet("tb", "MLB", "New York Mets @ Washington Nationals", mets, "Player Total Bases", "Carson Benge Over 2.5"),
            bet("spr", "NFL", "Atlanta Falcons @ Green Bay Packers", falcons, "Point Spread", "Green Bay Packers +6.5"),
            bet("rush", "NFL", "Atlanta Falcons @ Green Bay Packers", falcons, "Player Rushing Yards", "Bijan Robinson Over 90.5"),
            bet("rec", "NFL", "Atlanta Falcons @ Green Bay Packers", falcons, "Player Receptions", "Drake London Under 6.5"),
            bet("old", "", "Atlanta Falcons @ Green Bay Packers", falcons, "Total Points", "Over 44.5"),
        )
        val file = File.createTempFile("bets", ".json").also { it.delete() }
        file.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets))
        val tracker = BetTracker(file)
        val scores = FreeScores(OkHttpClient())
        val report = BetSettler(tracker, scores).run()
        val byId = tracker.all().associateBy { it.id }
        byId.values.forEach { println("LIVE SETTLE ${it.id}: ${it.selection} (${it.marketLabel}) -> ${it.status}") }
        println("LIVE SETTLE report $report, ${scores.requests} requests")
        assertEquals(BetStatus.WON, byId.getValue("ml").status)
        assertEquals(BetStatus.WON, byId.getValue("f5").status)
        assertEquals(BetStatus.WON, byId.getValue("k").status) // Tong struck out 9
        assertEquals(BetStatus.WON, byId.getValue("tb").status) // Benge 4 total bases
        assertEquals(BetStatus.LOST, byId.getValue("spr").status) // 35-14
        assertEquals(BetStatus.WON, byId.getValue("rush").status) // 194 yards
        assertEquals(BetStatus.LOST, byId.getValue("rec").status) // 9 catches
        assertEquals(BetStatus.WON, byId.getValue("old").status) // 49 points, league found
        assertEquals(8, report.settled)
        assertEquals(BetSettler.BY_SCORES, byId.getValue("ml").settledBy)
        assertEquals(null, BetGrader.pickOf(bets[0].copy(marketLabel = "Moneyline 3-way")))
    }
}
