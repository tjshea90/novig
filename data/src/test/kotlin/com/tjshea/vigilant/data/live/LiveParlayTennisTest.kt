package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Tennis through ParlayAPI against the REAL Novig catalog (Tj, 2026-10-01: "Build tennis through parlayapi"): both tours' odds (3 credits
 * each), Novig's matches paired with them, and a few of each kind of line priced from Novig's real books, so a sets line pricing a games
 * market would show as edges far off every other's. Skipped unless VIGILANT_LIVE=1 and PARLAY_KEY_FILE names a file holding a ParlayAPI key
 * (never a repo file): `VIGILANT_LIVE=1 PARLAY_KEY_FILE=… bash tools/test.sh :data:test --tests '*LiveParlayTennisTest'` (lines "LIVE PT").
 */
class LiveParlayTennisTest {

    @Test
    fun `real tennis - Novig's matches pair with ParlayAPI's tours and each kind of line prices near its market`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val keyFile = System.getenv("PARLAY_KEY_FILE")?.let(::File)
        assumeTrue(keyFile?.canRead() == true)
        val key = keyFile!!.readText().trim()
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val leagues = listOf("ATP", "WTA")
        val now = System.currentTimeMillis()
        val before = now + 3 * 86_400_000L
        val events = novig.events(leagues, listOf(NovigEvent.STATUS_PREGAME), before)
        val types = MarketFamily.MONEYLINE.novigTypes + MarketFamily.SPREAD.novigTypes + MarketFamily.TOTAL.novigTypes
        val markets = novig.markets(leagues, types, listOf(NovigEvent.STATUS_PREGAME), before)
        println("LIVE PT Novig: ${events.size} matches, markets ${markets.groupingBy { it.marketType }.eachCount()}")

        val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))
        val parlay = TheOddsApiClient(http, KeyPool(QuotaPolicy.PARLAY, { listOf(key) }, meter), json, baseUrl = OddsFeed.PARLAY.base, feed = OddsFeed.PARLAY)
        val s = ScanSettings(leagues = leagues.toSet(), minEvPercent = -1.0, maxEvPercent = 1.0, minBooks = 1, linesPerGame = 50)
        val refs = Leagues.ALL.filter { it.novigName in leagues }.map { parlay.odds(it, s) }
        for (r in refs) {
            val sets = r.events.sumOf { e -> e.markets.count { it.period == RefBookMarket.PERIOD_SETS } }
            println("LIVE PT ParlayAPI ${r.sportKey}: ${r.events.size} matches, ${sets} set lines, books ${r.events.flatMap { e -> e.markets.map { it.bookKey } }.groupingBy { it }.eachCount()}")
            assertTrue("a (Games) name survived", r.events.none { it.home.contains("(Games)") || it.away.contains("(Games)") })
            assertTrue("a doubles pair survived", r.events.none { '/' in it.home || '/' in it.away })
        }

        val plan = Planner.plan(events, markets, refs, s, now)
        val tennis = plan.events.filter { it.league.tennis }
        println("LIVE PT paired ${tennis.count { it.refEvent != null }} of ${tennis.size} Novig matches; lines with a fair source ${plan.markets.filter { it.lineKey != null }.groupingBy { it.market.marketType }.eachCount()}")
        tennis.filter { it.refEvent == null }.take(8).forEach { println("LIVE PT   unpaired: ${it.event.description}") }

        // A few of each kind, priced from Novig's real books.
        val chosen = plan.markets.filter { it.lineKey != null }.groupBy { it.market.marketType }.values.flatMap { it.take(6) }
        val batch = novig.books(chosen.map { it.market.marketId })
        val r = Pricing.price(plan, batch.books, s, now)
        val byType = r.opportunities.filter { it.evPercent != null }.groupBy { it.market.marketType }
        for ((type, opps) in byType) {
            opps.take(6).forEach { println("LIVE PT   $type ${it.selection} fair=${"%.3f".format(it.fairProbability)} price=${it.quote?.price} ev=${"%.1f".format(it.evPercent!! * 100)}% books=${it.fair?.booksUsed}") }
            // Novig's price sits near the fair line on most outcomes: a unit mix-up (sets pricing games) is off by 20-40 points.
            val median = opps.map { abs(it.evPercent!!) }.sorted().let { it[it.size / 2] }
            println("LIVE PT   $type median |EV| ${"%.1f".format(median * 100)}% over ${opps.size}")
            assertTrue("$type: median |EV| ${median * 100}% looks like a unit mix-up", median < 0.15)
        }
        assertTrue("no Novig tennis match paired with ParlayAPI", tennis.isEmpty() || tennis.any { it.refEvent != null })
    }
}
