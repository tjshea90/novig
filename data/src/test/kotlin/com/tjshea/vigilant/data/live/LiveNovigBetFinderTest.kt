package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.cno.NovigBetFinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Finds CNO's REAL list in Novig's REAL public catalog, the way a tap does when CNO can't hand
 * over its link (Tj, 2026-09-27: "make it so vigilant can open the bet in novig even if it can't
 * reach cno servers"), and checks each exact find against CNO's own link where CNO gives one:
 * a wrong outcome would put the wrong bet in Tj's bet slip. Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveNovigBetFinderTest' -i`.
 */
class LiveNovigBetFinderTest {

    @Test
    fun `real CNO bets are found in Novig's real catalog, and every exact find is CNO's own outcome`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val http = OkHttpClient()
        val cno = CnoClient(http)
        val snap = cno.fetch(CnoView.DEFAULT, CnoFilters(maxOdds = 0, minBooks = 3, minEv = 0.0, rows = 60))
        val finder = NovigBetFinder(http)
        var exact = 0
        var game = 0
        var none = 0
        var checked = 0
        val wrong = mutableListOf<String>()
        for (row in snap.rows) {
            val found = finder.find(row)
            when (found) {
                is NovigBetFinder.Found.Bet -> exact++
                is NovigBetFinder.Found.Game -> game++
                null -> none++
            }
            val tag = when (found) { is NovigBetFinder.Found.Bet -> "BET "; is NovigBetFinder.Found.Game -> "GAME"; null -> "NONE" }
            println("LIVE FIND $tag ${row.league} | ${row.market} | ${row.bet} | ${row.event} -> ${found?.link}")
            // Ground truth for exact finds: CNO's own link names the outcome (paced like the app).
            if (found is NovigBetFinder.Found.Bet && checked < 25 && row.betUrl != null) {
                delay(1_500)
                val truth = runCatching { cno.novigLink(row) }.getOrNull()
                val truthId = truth?.removePrefix("novigapp://events/")?.removePrefix("https://novig.com/events/")?.substringBefore('/')
                if (truthId != null) {
                    checked++
                    if (truthId != found.outcomeId) wrong += "${row.bet} (${row.market}): found ${found.outcomeId}, CNO says $truthId"
                }
            }
        }
        println("LIVE FIND summary: ${snap.rows.size} rows: $exact exact, $game game only, $none not found; $checked checked against CNO, ${wrong.size} wrong; ${finder.requests} Novig reads")
        wrong.forEach { println("LIVE FIND WRONG: $it") }
        assertTrue("an exact find must never be the wrong outcome: $wrong", wrong.isEmpty())
    }
}
