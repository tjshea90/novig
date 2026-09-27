package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoPace
import com.tjshea.vigilant.data.cno.CnoView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * CNO under rapid refreshing (Tj, 2026-09-27: "consider ways to make cno respond even with high
 * traffic and rapid refreshing"): the list re-read once a second for a minute, as mashing Refresh
 * would, recording each read's time, size, compression and any refusal. Skipped unless
 * VIGILANT_BURST=1: `VIGILANT_BURST=1 ./gradlew :data:test --tests '*LiveCnoBurstTest' -i`.
 */
class LiveCnoBurstTest {

    @Test
    fun `real CNO - the list once a second for a minute`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_BURST") == "1")
        val seconds = System.getenv("VIGILANT_BURST_S")?.toIntOrNull() ?: 60
        val codes = mutableMapOf<Int, Int>()
        var encodings = mutableSetOf<String>()
        var bytes = 0L
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val r = chain.proceed(chain.request())
                synchronized(codes) { codes[r.code] = (codes[r.code] ?: 0) + 1 }
                r.header("Content-Encoding")?.let { encodings += it }
                r.header("Content-Length")?.toLongOrNull()?.let { bytes += it }
                if (r.code >= 400) println("BURST HTTP ${r.code} retry-after=${r.header("Retry-After")} ${r.header("Server")}")
                r
            }
            .build()
        val client = CnoClient(http, pace = CnoPace(minGapMs = 0))
        val times = mutableListOf<Long>()
        var errors = 0
        val filters = CnoFilters()
        repeat(seconds) { i ->
            val t0 = System.nanoTime()
            val ok = runCatching { client.fetch(CnoView.DEFAULT, filters) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            times += ms
            ok.exceptionOrNull()?.let { errors++; println("BURST #$i error after ${ms}ms: ${it.message}") }
            ok.getOrNull()?.let { if (i % 10 == 0) println("BURST #$i ${ms}ms ${it.rows.size} rows, CNO data ${it.cnoAgeSeconds}s old") }
            delay((1_000 - ms).coerceAtLeast(0))
        }
        val sorted = times.sorted()
        fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        println("BURST summary: ${times.size} reads in ${seconds}s, $errors errors, HTTP $codes, encodings $encodings, ~${bytes / 1024} KB compressed total")
        println("BURST latency ms: min ${sorted.first()} p50 ${pct(0.5)} p90 ${pct(0.9)} max ${sorted.last()}")
    }
}
