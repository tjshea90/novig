package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How long reading many bets' CNO game pages really takes (Tj, 2026-09-29: "Check odds now … scanned very slow"): one at a time at
 * CNO's shared one-second pace (a page is two requests: about 2.4 s each), against three at a time at the bulk pace. CNO answers
 * a page in ~1.2 s per request, so the pace, not the wait, limits it. Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveCnoBooksSpeedTest'` (prints the timings).
 */
class LiveCnoBooksSpeedTest {
    @Test
    fun `real CNO - a page per bet, one at a time and three at a time`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain -> requests++; chain.proceed(chain.request()) }.build()
        val client = CnoClient(http)
        val filters = CnoFilters(minEv = 0.0, maxOdds = 0, minBooks = 1, rows = 200, completeBook = false, minSides = 1)
        val rows = client.fetch(CnoView.DEFAULT, filters).rows.filter { it.gameUrl != null }
        val one = rows.take(6)
        val many = rows.drop(6).take(18)
        val t0 = System.currentTimeMillis()
        val before1 = requests
        val serial = one.map { client.books(it) }
        val serialMs = System.currentTimeMillis() - t0
        println("LIVE SPEED one at a time: ${one.size} bets, ${requests - before1} requests, $serialMs ms (${serialMs / one.size} ms a bet)")
        val t1 = System.currentTimeMillis()
        val before2 = requests
        val queue = Channel<com.tjshea.vigilant.data.cno.CnoRow>(Channel.UNLIMITED).apply { many.forEach { trySend(it) }; close() }
        val bulk = coroutineScope { (1..3).map { async { buildList { for (r in queue) add(client.booksBulk(r)) } } }.awaitAll().flatten() }
        val bulkMs = System.currentTimeMillis() - t1
        println("LIVE SPEED three at a time: ${many.size} bets, ${requests - before2} requests, $bulkMs ms (${bulkMs / many.size} ms a bet)")
        assertTrue("every bet's books read", serial.all { it != null } && bulk.all { it != null })
        assertTrue("three at a time is faster per bet", bulkMs / many.size < serialMs / one.size)
    }
}
