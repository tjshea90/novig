package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoConfig
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.teams.PlayerTeams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The app's own CNO read pattern against the REAL site for a few minutes: the list on a timer,
 * the green-check books lane and the teams lane at the same time, with the app's HTTP settings.
 * Prints every error. Skipped unless VIGILANT_SOAK=1 (it takes minutes):
 * `VIGILANT_SOAK=1 ./gradlew :data:test --tests '*LiveCnoSoakTest' -i`.
 */
class LiveCnoSoakTest {

    @Test
    fun `real CNO - the list, books lane and teams lane together, for a few minutes`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_SOAK") == "1")
        val minutes = System.getenv("VIGILANT_SOAK_MIN")?.toLongOrNull() ?: 3L
        var requests = 0
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                requests++
                val r = chain.proceed(chain.request())
                if (!r.isSuccessful) println("SOAK HTTP ${r.code} ${chain.request().url.encodedPath}")
                r
            }
            .build()
        val feed = CnoFeed(CnoClient(http))
        val teams = PlayerTeams(http)
        val interval = System.getenv("VIGILANT_SOAK_INTERVAL")?.toIntOrNull() ?: CnoFeed.REALTIME
        val config = MutableStateFlow(CnoConfig(true, CnoView.DEFAULT, interval, CnoFilters()))
        val start = System.currentTimeMillis()
        val jobs = listOf(
            launch { feed.watch(config) },
            launch {
                feed.keepBooksFresh(feed.state.map { s ->
                    s.snapshot?.let { CnoChecks.screen(it, CnoFilters(), System.currentTimeMillis()).picks.map { p -> p.row } } ?: emptyList()
                })
            },
            launch { teams.keepFresh(feed.state.map { it.snapshot?.rows ?: emptyList() }) },
            launch {
                var lastErr: String? = null
                var lastFetch = 0L
                while (true) {
                    val s = feed.state.value
                    if (s.error != lastErr) {
                        println("SOAK t=${(System.currentTimeMillis() - start) / 1000}s error -> ${s.error} (errors ${s.errors}, paused ${s.pausedUntilMs})")
                        lastErr = s.error
                    }
                    val f = s.snapshot?.fetchedAtMs ?: 0L
                    if (f != lastFetch) {
                        println("SOAK t=${(System.currentTimeMillis() - start) / 1000}s read: ${s.snapshot?.rows?.size} rows, CNO ${s.snapshot?.cnoAgeSeconds}s old, requests $requests, books ${feed.books.value.size} (${feed.books.value.values.count { it.error != null }} failed)")
                        lastFetch = f
                    }
                    delay(250)
                }
            },
        )
        withTimeoutOrNull(minutes * 60_000) { kotlinx.coroutines.awaitCancellation() }
        jobs.forEach { it.cancel() }
        feed.books.value.filterValues { it.error != null }.forEach { (k, v) -> println("SOAK books error $k: ${v.error}") }
        println("SOAK done: $requests requests, error now ${feed.state.value.error}")
    }
}
