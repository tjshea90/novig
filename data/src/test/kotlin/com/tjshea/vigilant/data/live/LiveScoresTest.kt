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

    /**
     * Real hockey, basketball and tennis finals (ESPN, read 2026-09-29): Wild 5 @ Red Wings 4 (2026-04-05), Raptors 105 @ Cavaliers 115
     * (2026-04-20), Muller d. Pavlovic 6-4 6-7 6-3 (2026-09-22). Graded directly (the settler only looks 30 days back).
     */
    @Test
    fun `real hockey, basketball and tennis finals grade real bets`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val scores = FreeScores(OkHttpClient())
        suspend fun grade(league: String, event: String, start: String, market: String, selection: String): BetStatus? {
            val startMs = Instant.parse(start).toEpochMilli()
            val bet = TrackedBet(
                id = "x", createdAtMs = startMs, league = league, eventName = event, startsTs = startMs, marketLabel = market, selection = selection,
                marketId = "", outcomeId = "", price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
            )
            val pick = BetGrader.pickOf(bet) ?: return null
            val day = FreeScores.etDate(startMs)
            val game = listOf(day, day.minusDays(1), day.plusDays(1)).firstNotNullOfOrNull { d -> scores.games(league, d)?.let { BetGrader.gameOf(bet, it) } } ?: return null
            val box = if (pick is BetGrader.Pick.Prop) scores.players(game) else null
            return BetGrader.grade(pick, game, box).also { println("LIVE GRADE $league $selection ($market) -> $it") }
        }
        val nhl = "Minnesota Wild @ Detroit Red Wings"
        assertEquals(BetStatus.WON, grade("NHL", nhl, "2026-04-05T17:00:00Z", "Moneyline", "Minnesota Wild"))
        assertEquals(BetStatus.WON, grade("NHL", nhl, "2026-04-05T17:00:00Z", "Player Shots on Goal", "Matt Boldy Over 2.5")) // 3 shots
        assertEquals(BetStatus.WON, grade("NHL", nhl, "2026-04-05T17:00:00Z", "Player Points", "Matt Boldy Over 1.5")) // 1 goal + 1 assist
        assertEquals(BetStatus.LOST, grade("NHL", nhl, "2026-04-05T17:00:00Z", "Player Saves", "Filip Gustavsson Over 24.5")) // 20 saves
        val nba = "Toronto Raptors @ Cleveland Cavaliers"
        assertEquals(BetStatus.WON, grade("NBA", nba, "2026-04-20T23:00:00Z", "Point Spread", "Cleveland Cavaliers -9.5")) // won by 10
        assertEquals(BetStatus.WON, grade("NBA", nba, "2026-04-20T23:00:00Z", "Total Points", "Over 219.5")) // 220
        assertEquals(BetStatus.WON, grade("NBA", nba, "2026-04-20T23:00:00Z", "Player Steals", "James Harden Over 4.5")) // 5
        assertEquals(BetStatus.LOST, grade("NBA", nba, "2026-04-20T23:00:00Z", "Player Points + Rebounds", "James Harden Over 34.5")) // 28 + 5
        val atp = "Luka Pavlovic @ Alexandre Muller"
        assertEquals(BetStatus.WON, grade("ATP", atp, "2026-09-22T05:00:00Z", "Moneyline", "Alexandre Muller"))
        assertEquals(BetStatus.WON, grade("ATP", atp, "2026-09-22T05:00:00Z", "Total Games", "Over 31.5")) // 18 + 14
        assertEquals(BetStatus.WON, grade("ATP", atp, "2026-09-22T05:00:00Z", "1st Set Winner", "Alexandre Muller")) // 6-4
        assertEquals(BetStatus.LOST, grade("ATP", atp, "2026-09-22T05:00:00Z", "Games Spread", "Luka Pavlovic +3.5")) // 14 + 3.5 < 18
    }
}

