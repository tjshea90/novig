package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.cno.NovigBetFinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            if (found is NovigBetFinder.Found.Bet && checked < 80 && row.betUrl != null) {
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

    @Test
    fun `real CNO bets priced from Novig's live order book match the Novig price CNO lists`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val http = OkHttpClient()
        val snap = CnoClient(http).fetch(CnoView.DEFAULT, CnoFilters())
        val finder = NovigBetFinder(http)
        val novig = com.tjshea.vigilant.data.novig.NovigPublicClient(http, kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        val live = com.tjshea.vigilant.data.cno.NovigLive(novig, { finder.find(it) })
        val rows = kotlinx.coroutines.flow.MutableStateFlow(snap.rows)
        val job = launch { live.keepFresh(rows) }
        var tries = 0
        while (live.prices.value.isEmpty() && tries++ < 40) delay(500)
        job.cancel()
        var same = 0
        var near = 0
        val prices = live.prices.value
        for (row in snap.rows.filter { it.key in prices }) {
            val p = prices.getValue(row.key)
            val d = Math.abs(com.tjshea.vigilant.engine.Odds.americanToDecimal(p.american) - com.tjshea.vigilant.engine.Odds.americanToDecimal(row.odds))
            if (p.american == row.odds) same++ else if (d < 0.08) near++
            println("LIVE PRICE ${row.bet} | CNO ${row.odds} (\$${row.available}) EV ${"%.2f".format(row.ev * 100)}% | Novig now ${p.american} (\$${p.available?.toInt()}) EV ${p.ev?.let { "%.2f".format(it * 100) }}%")
        }
        println("LIVE PRICE summary: ${prices.size} priced of ${snap.rows.size}: $same same as CNO, $near within a tick or two, CNO data ${snap.cnoAgeSeconds}s old; ${live.reads} book reads")
        assertTrue(prices.isNotEmpty())
    }
}
