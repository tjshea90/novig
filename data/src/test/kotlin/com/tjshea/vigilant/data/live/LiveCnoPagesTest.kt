package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/** SCRATCH probe of CNO's real game pages (cost per read, what a page holds, which bets share one). VIGILANT_LIVE=1. */
class LiveCnoPagesTest {
    @Test
    fun `probe`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain -> requests++; chain.proceed(chain.request()) }.build()
        val client = CnoClient(http)
        val filters = CnoFilters(minEv = 0.0, maxOdds = 0, minBooks = 1, rows = 200, completeBook = false, minSides = 1)
        val list = client.fetch(CnoView.DEFAULT, filters)
        println("PROBE list: ${list.rows.size} rows")
        val byPage = list.rows.groupBy { r ->
            val u = r.gameUrl.orEmpty()
            Regex("game_id=(\\d+)").find(u)?.groupValues?.get(1) + "/" + Regex("market_id=(\\d+)").find(u)?.groupValues?.get(1)
        }
        println("PROBE distinct (game_id/market_id): ${byPage.size} for ${list.rows.size} rows; biggest groups: ${byPage.values.map { it.size }.sortedDescending().take(8)}")
        val byGame = list.rows.groupBy { Regex("game_id=(\\d+)").find(it.gameUrl.orEmpty())?.groupValues?.get(1) }
        println("PROBE distinct games: ${byGame.size}")
        println("PROBE markets: " + list.rows.map { it.market }.distinct().sorted().joinToString(" | "))
        // The biggest shared page: what's in its grid?
        val (key, group) = byPage.entries.maxByOrNull { it.value.size }!!
        val t0 = System.currentTimeMillis()
        val grid = client.gridHere(group.first().gameUrl!!)
        println("PROBE page $key read in ${System.currentTimeMillis() - t0} ms, ${grid.length} chars, requests so far $requests")
        val names = Regex("<tr id=\"(\\d+)\"[^>]*><td>([^<]*)</td>").findAll(grid).map { it.groupValues[1] to it.groupValues[2] }.toList()
        println("PROBE grid rows: ${names.size}; first 12: ${names.take(12)}")
        println("PROBE group bets: ${group.map { it.bet + " side " + it.sideId }}")
        // Timing of a few sequential book reads.
        val sample = byGame.values.map { it.first() }.take(5)
        for (r in sample) {
            val s = System.currentTimeMillis(); val before = requests
            val v = client.books(r)
            println("PROBE books(${r.bet}) ${System.currentTimeMillis() - s} ms, ${requests - before} requests, ${v?.prices?.size} books")
        }
    }
}
