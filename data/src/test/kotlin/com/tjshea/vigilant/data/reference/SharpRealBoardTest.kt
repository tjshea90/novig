package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tj, 2026-10-02 16:05Z: "I'm getting no volume so far on auto bet with the option for each bet to be verified positive EV by a sharp book. Is this working
 * correctly? Is it getting sharp book pricing?" The whole path on a real PinnWire answer (2026-09-27, NFL with Pinnacle's player props): the feed's client, the
 * sharp-book lookup with the bet as CNO names it, and the verdict. RESEARCH.md §64.3.
 */
class SharpRealBoardTest {

    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-09-27T07:00:00Z").toEpochMilli()
    private val start = Instant.parse("2026-09-27T17:00:00Z").toEpochMilli()
    private val settings = ScanSettings(sharpConfirmAutoBet = true)
    private val rules = SharpConfirm.rules(settings, autoBet = true)!!

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        server.enqueue(MockResponse().setBody(javaClass.classLoader!!.getResource("pinnwire-football.json")!!.readText()))
    }

    @After fun tearDown() { server.shutdown() }

    private fun sharp(): SharpBooks {
        val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { now })
        val pinnacle = PinnapiClient(
            OkHttpClient(), Json { ignoreUnknownKeys = true },
            listOf(PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { listOf("k") }, meter), server.url("/kit/v1").toString().trimEnd('/'))), clock = { now },
        )
        return SharpBooks(sources = { listOf(pinnacle) }, settings = { settings }, clock = { now })
    }

    private fun bet(market: String, selection: String) = SharpBooks.Bet("NFL", "Cincinnati Bengals @ Pittsburgh Steelers", start, market, selection)

    @Test
    fun `CNO's bets find Pinnacle's own two sides at the exact line, dated by the read, and are judged on them`() = runBlocking {
        val s = sharp()
        // Chase Brown receiving yards 21.5: Pinnacle 1.893 / 1.893 (−112 / −112), 50% devigged: +10% EV at Novig's +110 is confirmed.
        val yards = s.quotes(bet("Player Receiving Yards", "Chase Brown Under 21.5"), rules)
        assertNull(yards.unavailable)
        assertEquals(listOf(SharpConfirm.Quote("PN", -112, -112, now, "Pinnacle")), yards.quotes)
        assertEquals(SharpConfirm.Verdict.CONFIRMED, SharpConfirm.judge(yards.quotes, 110, false, rules, now).verdict)
        // His receptions Over 3.5: Pinnacle +106 / −134 says −4.4% at +110: vetoed. Same board, no second call.
        val catches = s.quotes(bet("Player Receptions", "Chase Brown Over 3.5"), rules)
        assertEquals(listOf(SharpConfirm.Quote("PN", 106, -134, now, "Pinnacle")), catches.quotes)
        assertEquals(SharpConfirm.Verdict.NOT_CONFIRMED, SharpConfirm.judge(catches.quotes, 110, false, rules, now).verdict)
        // A game line from the same board, and a line Pinnacle doesn't have (21.5 is its only number): no quote, never a guess from a nearby line.
        assertEquals("PN", s.quotes(bet("Moneyline", "Pittsburgh Steelers"), rules).quotes.single().code)
        val other = s.quotes(bet("Player Receiving Yards", "Chase Brown Under 24.5"), rules)
        assertEquals(emptyList<SharpConfirm.Quote>(), other.quotes)
        assertEquals(SharpConfirm.Verdict.NO_QUOTE, SharpConfirm.judge(other.quotes, 110, false, rules, now, other.unavailable).verdict)
        assertEquals(1, server.requestCount)
        assertEquals(1, s.calls)
    }
}
