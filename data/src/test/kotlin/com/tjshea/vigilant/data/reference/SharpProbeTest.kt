package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.JsonFileStore
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.time.Instant

class SharpProbeTest {
    @Test
    fun probe() = runBlocking {
        val server = MockWebServer().also { it.start() }
        repeat(5) { server.enqueue(MockResponse().setBody(javaClass.classLoader!!.getResource("pinnwire-football.json")!!.readText())) }
        val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), System::currentTimeMillis)
        val now = Instant.parse("2026-09-27T07:00:00Z").toEpochMilli()
        val pinn = PinnapiClient(OkHttpClient(), Json { ignoreUnknownKeys = true },
            listOf(PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { listOf("k") }, meter), server.url("/kit/v1").toString().trimEnd('/'))), clock = { now })
        val s = ScanSettings(sharpConfirmAutoBet = true)
        val sharp = SharpBooks(sources = { listOf(pinn) }, settings = { s }, clock = { now })
        val rules = SharpConfirm.rules(s, autoBet = true)!!
        val start = Instant.parse("2026-09-27T17:00:00Z").toEpochMilli()
        for ((market, bet) in listOf("Player Receiving Yards" to "Chase Brown Under 21.5", "Player Receptions" to "Chase Brown Over 3.5", "Player Passing Yards" to "Aaron Rodgers Over 220.5", "Moneyline" to "Pittsburgh Steelers", "Total Points" to "Over 41")) {
            val a = sharp.quotes(SharpBooks.Bet("NFL", "Cincinnati Bengals @ Pittsburgh Steelers", start, market, bet), rules)
            println("PROBE $market / $bet -> $a ; judged ${SharpConfirm.judge(a.quotes, 110, false, rules, now, a.unavailable)}")
        }
        server.shutdown()
    }
}
