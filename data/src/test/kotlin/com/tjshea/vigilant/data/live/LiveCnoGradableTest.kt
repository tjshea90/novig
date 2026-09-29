package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.tracker.BetGrader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every market CrazyNinjaOdds' list carries right now, read the way the Tracker grades a bet (Tj, 2026-09-29: "a lot of bets can't be
 * tracked"): which market names `BetGrader.pickOf` reads and which it leaves to a tap, and why. Prints the list; skipped unless
 * VIGILANT_LIVE=1: `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveCnoGradableTest' -i`.
 */
class LiveCnoGradableTest {
    @Test
    fun `which of CNO's real markets the grader can read`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val client = CnoClient(OkHttpClient())
        val filters = CnoFilters(minEv = -100.0, maxOdds = 0, minBooks = 1, rows = 200, completeBook = false, minSides = 1)
        val rows = client.fetch(CnoView.DEFAULT, filters).rows
        val byMarket = rows.groupBy { it.market }
        var unread = 0
        byMarket.entries.sortedByDescending { it.value.size }.forEach { (market, list) ->
            val bad = list.filter { BetGrader.pickOf(market, it.bet) == null }
            unread += bad.size
            println("LIVE GRADABLE ${if (bad.isEmpty()) "ok  " else "MISS"} ${list.size} × $market${bad.firstOrNull()?.let { " e.g. \"${it.bet}\" (${it.league}): ${BetGrader.whyNot(market, it.bet)}" }.orEmpty()}")
        }
        println("LIVE GRADABLE ${rows.size} rows, ${byMarket.size} markets, $unread not read")
    }
}
