package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * Tj, 2026-09-27: "keep track whether each bet was a win or a loss … a background scores system".
 * Settled from final scores (the 2026-09-27 full test found Novig's catalog forgets finished games;
 * TASKS.md N7).
 */
class BetSettlerTest {

    @get:Rule val tmp = TemporaryFolder()
    private val start = BetGraderTest.METS_START
    private var now = start + 4 * 60 * 60_000L

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        File(tmp.root, "bets.json").writeText(
            kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()),
        )
        return BetTracker(File(tmp.root, "bets.json"), clock = { now })
    }

    private fun bet(id: String, market: String, selection: String, league: String = "MLB", startsTs: Long = start, settledBy: String? = null) = TrackedBet(
        id = id, createdAtMs = startsTs - 3_600_000L, league = league, eventName = "New York Mets @ Washington Nationals", startsTs = startsTs,
        marketLabel = market, selection = selection, marketId = "", outcomeId = "", price = 0.5, cost = 0.5,
        fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0, settledBy = settledBy,
    )

    /** Scores for the Mets game; [final] false = still being played; null games = feed out of reach. */
    private class FakeScores(var final: Boolean = true, var reachable: Boolean = true) : ScoreSource {
        val asked = ArrayList<Pair<String, LocalDate>>()
        var boxes = 0
        override fun covers(league: String) = league in setOf("MLB", "NFL", "NCAAF", "WNBA", "NBA", "NHL", "NCAAB")
        override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
            asked += league to date
            if (!reachable) return null
            if (league != "MLB" || date != LocalDate.of(2026, 9, 26)) return emptyList()
            return listOf(
                GameScore("822678", "MLB", "Washington Nationals", "New York Mets", BetGraderTest.METS_START, final, false, 1, 7,
                    listOf(0, 0, 1, 0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0, 4, 1, 2)),
            )
        }
        override suspend fun players(game: GameScore): List<PlayerLine>? {
            boxes++
            return listOf(PlayerLine("Carson Benge", mapOf("TOTAL_BASES" to 4.0)))
        }
    }

    @Test
    fun `a final score settles game lines and props, one scoreboard read for the day`() = runTest {
        val t = tracker(
            bet("ml", "Moneyline", "New York Mets"),
            bet("tot", "Total Runs", "Over 8.5"),
            bet("push", "Total", "Under 8"),
            bet("tb", "Player Total Bases", "Carson Benge Over 1.5"),
        )
        val scores = FakeScores()
        val report = BetSettler(t, scores, clock = { now }).run()
        assertEquals(4, report.settled)
        val byId = t.all().associateBy { it.id }
        assertEquals(BetStatus.WON, byId.getValue("ml").status)
        assertEquals(1.0, byId.getValue("ml").profit!!, 1e-9)
        assertEquals(BetStatus.LOST, byId.getValue("tot").status)
        assertEquals(BetStatus.PUSH, byId.getValue("push").status)
        assertEquals(BetStatus.WON, byId.getValue("tb").status)
        assertTrue(byId.values.all { it.settledBy == BetSettler.BY_SCORES && it.settledAtMs == now })
        // The scoreboard was read once (cached per league and day is the feed's job; the day itself was asked for each bet).
        assertTrue(scores.asked.all { it == ("MLB" to LocalDate.of(2026, 9, 26)) })
        assertEquals(1, scores.boxes)
    }

    @Test
    fun `a game still going waits half an hour, then settles`() = runTest {
        val t = tracker(bet("ml", "Moneyline", "New York Mets"))
        val scores = FakeScores(final = false)
        val settler = BetSettler(t, scores, clock = { now })
        assertEquals(0, settler.run().settled)
        scores.final = true
        now += 10 * 60_000L
        assertEquals(0, settler.run().asked)
        now += 25 * 60_000L
        assertEquals(1, settler.run().settled)
        assertEquals(BetStatus.WON, t.all().single().status)
    }

    @Test
    fun `games not an hour old, and results Tj tapped (or undid), are left alone`() = runTest {
        val t = tracker(
            bet("new", "Moneyline", "New York Mets", startsTs = now - 30 * 60_000L),
            bet("tapped", "Moneyline", "New York Mets", settledBy = BetSettler.BY_YOU),
        )
        val report = BetSettler(t, FakeScores(), clock = { now }).run()
        assertEquals(0, report.asked)
        assertTrue(t.all().all { it.status == BetStatus.PENDING })
    }

    @Test
    fun `a score feed out of reach stops the pass and guesses nothing`() = runTest {
        val t = tracker(bet("a", "Moneyline", "New York Mets"), bet("b", "Moneyline", "Washington Nationals"))
        val report = BetSettler(t, FakeScores(reachable = false), clock = { now }).run()
        assertTrue(report.stopped)
        assertEquals(0, report.settled)
        assertTrue(t.all().all { it.status == BetStatus.PENDING })
    }

    @Test
    fun `a bet imported without a league is found in whichever league has the game`() = runTest {
        val t = tracker(bet("old", "Moneyline", "New York Mets", league = ""))
        assertEquals(1, BetSettler(t, FakeScores(), clock = { now }).run().settled)
        assertEquals(BetStatus.WON, t.all().single().status)
    }

    @Test
    fun `a bet it can't grade stays open for a tap and is looked at again only every few hours`() = runTest {
        val t = tracker(bet("odd", "Some Novelty Market", "Something Over 1.5"), bet("ufc", "Moneyline", "Fighter A", league = "UFC"))
        val scores = FakeScores()
        val settler = BetSettler(t, scores, clock = { now })
        assertEquals(2, settler.run().asked)
        now += 60 * 60_000L
        assertEquals(0, settler.run().asked)
        now += 6 * 60 * 60_000L
        assertEquals(2, settler.run().asked)
        assertTrue(t.all().all { it.status == BetStatus.PENDING })
        // UFC has no score feed: nothing was read for it.
        assertTrue(scores.asked.none { it.first == "UFC" })
    }

    // ---- Tj, 2026-09-29: "Some bets are still pending in the open bets tab that are final" ------------

    @Test
    fun `every bet a pass can't settle says why on the bet, and a settled one says what it rests on`() = runTest {
        val t = tracker(
            bet("ok", "Moneyline", "New York Mets"),
            bet("odd", "Some Novelty Market", "Something Over 1.5"),
            bet("ufc", "Moneyline", "Fighter A", league = "UFC"),
            bet("nobody", "Player Total Bases", "Nobody Here Over 1.5"),
            bet("first", "First Touchdown Scorer", "Jonathan Taylor Yes"),
        )
        val report = BetSettler(t, FakeScores(), clock = { now }).run()
        assertEquals(1, report.settled)
        assertEquals(4, report.manual)
        val notes = t.all().associate { it.id to it.gradeNote }
        assertEquals("Final: New York Mets 7, Washington Nationals 1", notes["ok"])
        assertEquals(true, notes["odd"]!!.startsWith("Couldn't read \"Some Novelty Market\""))
        assertEquals("No score feed covers UFC: mark it yourself", notes["ufc"])
        assertEquals(true, notes["nobody"]!!.startsWith("Nobody Here isn't in the box score"))
        assertEquals(true, notes["first"]!!.contains("play-by-play"))
        assertEquals(now, t.all().first { it.id == "odd" }.gradeAtMs)
    }

    @Test
    fun `a game not over yet says so, and a postponed one says what the feed called it`() = runTest {
        val t = tracker(bet("live", "Moneyline", "New York Mets"))
        val scores = FakeScores(final = false)
        val settler = BetSettler(t, scores, clock = { now })
        val r = settler.run()
        assertEquals(1, r.waiting)
        assertEquals("The game isn't over yet", t.all().single().gradeNote)

        val called = object : ScoreSource by scores {
            override suspend fun games(league: String, date: LocalDate) = scores.games(league, date)?.map { it.copy(called = true, calledReason = "Postponed", final = false) }
        }
        val t2 = tracker(bet("ppd", "Moneyline", "New York Mets"))
        BetSettler(t2, called, clock = { now }).run()
        assertEquals(true, t2.all().single().gradeNote!!.startsWith("Postponed: the score feeds have no result"))
    }

    @Test
    fun `Grade now looks at every bet again, an unchanged note isn't rewritten, an old bet says it's left to a tap`() = runTest {
        val t = tracker(bet("odd", "Some Novelty Market", "Something Over 1.5"), bet("ancient", "Moneyline", "New York Mets", startsTs = now - BetSettler.GIVE_UP_MS - 1))
        val settler = BetSettler(t, FakeScores(), clock = { now })
        settler.run()
        val first = t.all().first { it.id == "odd" }.gradeAtMs
        assertEquals(BetSettler.TOO_OLD, t.all().first { it.id == "ancient" }.gradeNote)
        // Not due for six hours, unless the button forces it.
        assertEquals(0, settler.run().asked)
        now += 60_000L
        assertEquals(1, settler.run(force = true).asked)
        // Same note a minute later: left as it was (no rewrite of the file for it).
        assertEquals(first, t.all().first { it.id == "odd" }.gradeAtMs)
        now += BetSettler.NOTE_REFRESH_MS
        settler.run(force = true)
        assertEquals(now, t.all().first { it.id == "odd" }.gradeAtMs)
    }

    @Test
    fun `an undone result stays with Tj until he turns auto-grading back on`() = runTest {
        val t = tracker(bet("undone", "Moneyline", "New York Mets", settledBy = BetSettler.BY_YOU))
        val settler = BetSettler(t, FakeScores(), clock = { now })
        assertEquals(0, settler.run().asked)
        t.regrade("undone")
        assertEquals(1, settler.run().settled)
        assertEquals(BetStatus.WON, t.all().single().status)
        assertEquals(BetSettler.BY_SCORES, t.all().single().settledBy)
    }

    /** ESPN's tennis scoreboard for the Chengdu Open match (Muller d. Pavlovic 6-4 6-7 6-3). */
    private class TennisScores : ScoreSource {
        override fun covers(league: String) = league == "ATP"
        override suspend fun games(league: String, date: LocalDate): List<GameScore>? =
            if (league != "ATP") emptyList() else listOf(
                GameScore("186127", "ATP", "Alexandre Muller", "Luka Pavlovic", BetGraderTest.TENNIS_START, true, false, 2, 1, listOf(6, 6, 6), listOf(4, 7, 3)),
            )
        override suspend fun players(game: GameScore): List<PlayerLine>? = emptyList()
    }

    @Test
    fun `a tennis bet settles from the match's sets and games`() = runTest {
        val at = BetGraderTest.TENNIS_START
        fun tb(id: String, market: String, selection: String) = bet(id, market, selection, league = "ATP", startsTs = at).copy(eventName = "Luka Pavlovic @ Alexandre Muller")
        now = at + 6 * 60 * 60_000L
        val t = tracker(tb("ml", "Moneyline", "Alexandre Muller"), tb("games", "Total Games", "Under 31.5"), tb("aces", "Player Aces", "Alexandre Muller Over 9.5"))
        val r = BetSettler(t, TennisScores(), clock = { now }).run()
        assertEquals(2, r.settled)
        assertEquals(1, r.manual)
        val byId = t.all().associateBy { it.id }
        assertEquals(BetStatus.WON, byId.getValue("ml").status)
        assertEquals(BetStatus.LOST, byId.getValue("games").status) // 32 games, under 31.5
        assertEquals(true, byId.getValue("aces").gradeNote!!.contains("mark it yourself"))
    }
}
