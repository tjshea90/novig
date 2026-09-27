package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.cno.NovigBetFinder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveDebugTest {
    @Test
    fun debug() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val http = OkHttpClient()
        val snap = CnoClient(http).fetch(CnoView.DEFAULT, CnoFilters())
        val finder = NovigBetFinder(http)
        val novig = com.tjshea.vigilant.data.novig.NovigPublicClient(http, kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        for (row in snap.rows.take(10)) {
            val f = finder.find(row)
            val mid = (f as? NovigBetFinder.Found.Bet)?.marketId
            val m = mid?.let { runCatching { novig.market(it) }.getOrElse { e -> println("DBG market err $e"); null } }
            val b = mid?.let { novig.books(listOf(it)).books[it] }
            val ladder = if (m != null && b != null) b.takeLadder(m, (f as NovigBetFinder.Found.Bet).outcomeId) else null
            println("DBG ${row.book} ${row.bet} -> $f market=${m?.marketType} fee=${m?.fee} outcomes=${m?.outcomes?.size} bids=${b?.bidsByOutcome?.mapValues { it.value.size }} ladder=${ladder?.size}")
        }
    }
}
