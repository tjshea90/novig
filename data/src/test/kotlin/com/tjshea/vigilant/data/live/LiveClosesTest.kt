package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.EspnCloses
import com.tjshea.vigilant.data.tracker.NovigTradeCloses
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.vigilantHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant

/**
 * The closing lines found after the start, against the real ESPN and data.novig.com (VIGILANT_LIVE=1): Monday night's Eagles @ Bears
 * (2026-09-29 00:15Z) moneyline from ESPN's DraftKings close and from Novig's trade history. Prints what it found and what it cost.
 */
class LiveClosesTest {
    @Test
    fun `ESPN and Novig give Monday night's closing moneyline`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val http = vigilantHttpClient()
        val start = Instant.parse("2026-09-29T00:15:00Z").toEpochMilli()
        fun bet(id: String, selection: String, outcome: String) = TrackedBet(
            id, start - 86_400_000L, "NFL", "Philadelphia Eagles @ Chicago Bears", start, "Moneyline", selection, "01a0aa74-3a32-75b2-bd49-deda4d75ed0c",
            outcome, 0.6, 0.6, 0.62, 0.03, 10.0,
        )
        val bets = listOf(bet("phi", "Philadelphia Eagles", "01a0aa74-3a32-75b2-bd49-def757268c4f"), bet("chi", "Chicago Bears", "01a0aa74-3a32-75b2-bd49-dee69a477443"))
        val espn = EspnCloses(http)
        val e = espn.closes(bets)
        println("ESPN (${espn.requests} requests): $e")
        val novig = NovigTradeCloses(http)
        val t0 = System.currentTimeMillis()
        val n = novig.closes(bets)
        println("Novig (${novig.bytesRead / 1024} KB, ${System.currentTimeMillis() - t0} ms): $n")
        check(e["phi"] is CloseLookup.Found && n["phi"] is CloseLookup.Found)
    }
}
