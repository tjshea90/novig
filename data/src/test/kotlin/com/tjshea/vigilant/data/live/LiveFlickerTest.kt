package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "The app found several positive EV bets while scanning but they quickly disappeared." A real scan
 * (Novig's public books, Kalshi and Polymarket: the sources that need no key), watching every partial result: which
 * bets were shown and later left the feed, and why. Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveFlickerTest'`.
 */
class LiveFlickerTest {

    @Test
    fun `real scan - bets that appear mid-scan and then leave the feed, with the reason`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val sources = listOf(PolymarketClient(http, json), KalshiClient(http, json))
        val s = ScanSettings(leagues = setOf("NFL", "NCAAF", "MLB", "WNBA"), maxBooksPerScan = 250, daysAhead = 7)
        val partials = ArrayList<Pair<Long, ScanResult>>()
        val t0 = System.currentTimeMillis()
        val report = Scanner(novig).scan(s, sources, emptySet(), {}, { partials += (System.currentTimeMillis() - t0) to it })
        val final = report.result!!
        val finalFeed = final.feed(s).associateBy { it.key }
        println("LIVE FLICKER: ${partials.size} partial results over ${(System.currentTimeMillis() - t0) / 1000}s; final feed ${finalFeed.size} bets; reread ${report.booksReread}")
        // Every bet ever shown, when first and last, and its EV then.
        data class Seen(val first: Long, var last: Long, val firstEv: Double, var lastEv: Double, val fairFirst: Double?, var fairLast: Double?, val books: String)
        val seen = LinkedHashMap<String, Seen>()
        for ((t, r) in partials) {
            for (o in r.feed(s)) {
                val ev = o.evPercent ?: continue
                val prev = seen[o.key]
                if (prev == null) seen[o.key] = Seen(t, t, ev, ev, o.fairProbability, o.fairProbability, o.fair?.booksUsed?.joinToString("+").orEmpty())
                else { prev.last = t; prev.lastEv = ev; prev.fairLast = o.fairProbability }
            }
        }
        val all = final.opportunities.associateBy { it.key }
        var gone = 0
        for ((key, v) in seen) {
            if (key in finalFeed) continue
            gone++
            val o = all[key]
            val why = when {
                o == null -> "no longer priced"
                o.evPercent == null -> "no price on Novig now"
                o.fairProbability != v.fairLast -> "fair price moved ${"%.4f".format(v.fairLast)} -> ${"%.4f".format(o.fairProbability)} (books now ${o.fair?.booksUsed?.joinToString("+")})"
                else -> "Novig price moved (re-read at the end): EV ${"%.2f".format(o.evPercent!! * 100)}%"
            }
            println("LIVE FLICKER GONE: ${o?.selection ?: key} ${o?.marketLabel ?: ""}: shown ${v.first / 1000}s-${v.last / 1000}s at ${"%.2f".format(v.firstEv * 100)}% from [${v.books}] -> $why")
        }
        println("LIVE FLICKER: shown mid-scan ${seen.size}, gone by the end $gone, kept ${seen.size - gone}")
    }
}
