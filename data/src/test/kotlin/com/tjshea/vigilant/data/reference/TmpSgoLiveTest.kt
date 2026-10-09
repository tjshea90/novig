package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume
import org.junit.Test

class TmpSgoLiveTest {
    @Test fun live() = runBlocking {
        val key = System.getenv("SGO_KEY_TMP"); Assume.assumeTrue(key != null)
        val json = Json { ignoreUnknownKeys = true }
        val http = com.tjshea.vigilant.data.vigilantHttpClient()
        val usage = UsageMeter(JsonFileStore(java.io.File.createTempFile("u", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }, json))
        val sgo = SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { listOf(key!!) }, usage), json)
        val games = SgoGamesSource(sgo)
        val nfl = Leagues.byNovigName("NFL")!!
        val s = ScanSettings(leagues = setOf("NFL"), sgoPro = true, includeLive = true)
        val snap = games.odds(nfl, s)
        println("TMP sgo events=${snap.events.size} markets=${snap.events.sumOf { it.markets.size }} books=${snap.events.flatMap { it.markets }.map { it.bookKey }.toSet()}")
        println("TMP sgo ages(s) median=" + snap.events.flatMap { it.markets }.mapNotNull { it.lastUpdateMs }.map { (snap.fetchedAtMs - it) / 1000 }.sorted().let { it[it.size / 2] })
        val novig = NovigPublicClient(http, json, usage = usage)
        val rep = Scanner(novig).scan(s, listOf(games))
        val ops = rep.result?.games.orEmpty().flatMap { it.outcomes }
        println("TMP scan outcomes=${ops.size} withFair=${ops.count { it.fairProbability != null }} booksDist=${ops.filter { it.fair != null }.groupBy { it.fair!!.booksUsed.size }.mapValues { it.value.size }}")
        println("TMP report errors: ${rep.errors} sources: ${rep.sources}")
        ops.filter { it.fair != null }.take(4).forEach { println("TMP ${it.eventName} ${it.selection} fair=${it.fairProbability} books=${it.fair!!.booksUsed} asof=${it.fairAsOfMs}") }
    }
}
