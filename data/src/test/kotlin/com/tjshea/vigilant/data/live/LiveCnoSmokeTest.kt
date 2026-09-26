package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Reads CrazyNinjaOdds' REAL page twice (first load, then one Refresh postback 30 s later, as
 * the app would). Skipped unless VIGILANT_LIVE=1. Run by hand:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveCnoSmokeTest'`.
 */
class LiveCnoSmokeTest {

    @Test
    fun `real CNO - the Novig view reads, then refreshes with one postback`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain -> requests++; chain.proceed(chain.request()) }.build()
        val client = CnoClient(http)
        val first = client.fetch(CnoView.DEFAULT)
        println("LIVE CNO: ${first.rows.size} rows, CNO data ${first.cnoAgeSeconds}s old, EV column ${first.evLabel}, note ${first.note}")
        first.rows.take(5).forEach { println("  ${"%.2f".format(it.ev * 100)}% ${it.bet} | ${it.market} | ${it.event} | ${it.odds} (\$${it.available}) fair ${it.fairOdds} ${it.books} books | ${it.book}") }
        assertEquals(2, requests)
        assertTrue(first.rows.all { it.book == "Novig" })
        assertTrue(first.rows.all { it.ev > 0 && it.gameUrl != null })
        delay(31_000) // CNO's crawl delay
        val second = client.fetch(CnoView.DEFAULT)
        println("LIVE CNO refresh: ${second.rows.size} rows, CNO data ${second.cnoAgeSeconds}s old")
        assertEquals(3, requests)
        assertTrue(second.rows.isNotEmpty() || first.rows.isEmpty())
    }
}
