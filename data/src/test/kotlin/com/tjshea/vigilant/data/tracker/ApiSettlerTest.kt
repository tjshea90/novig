package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * Grading the bets placed through Novig's API from Novig's own ledger (Tj, 2026-09-29: "include the API grading bets feature for the tracker
 * system"), against a mock Novig speaking the documented ledger and positions routes (NOVIG_API.md §14-15).
 */
class ApiSettlerTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    private val start = 1_800_000_000_000L
    private var now = start + 5 * 3_600_000L
    private val hour = 3_600_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })
    private fun trading() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid", pem), server.url("").toString().trimEnd('/')), json)

    private val market = NovigMarket(
        "mkt", "ev", "MONEY", "OPEN", "Team A vs Team B", start, MarketFee.GAME,
        listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    private fun target(key: String? = "k") = BetTarget(
        market, "A", "NFL", "Team B @ Team A", start, "Moneyline", "Team A", fair = 0.5, fairAsOfMs = null, source = BetTracker.SOURCE_VIGILANT, placedKey = key,
    )

    /** 400 contracts for $1.85: a win pays $4.00. */
    private fun fill(orderId: String = "o1", cost: String = "1.85000", ts: Long = start - hour) =
        NovigFill("f-$orderId", orderId, null, "mkt", "A", 400, cost.toDouble(), true, 0.0, ts)

    private suspend fun bet(t: BetTracker, orderId: String = "o1"): TrackedBet {
        t.logApi(target(key = "k-$orderId"), orderId, listOf(fill(orderId)))
        return t.all().first { it.orderId == orderId }
    }

    /** Novig's side: [ledger] SETTLEMENT rows as (ref, amount), [held] the positions still held. */
    private fun novig(ledger: List<Pair<String, String>> = emptyList(), held: Boolean = false, down: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                if (down) return MockResponse().setResponseCode(503)
                return when {
                    path.contains("/transactions") -> MockResponse().setBody(
                        """{"items":[${ledger.mapIndexed { i, (ref, amt) -> """{"transactionId":"t$i","kind":"SETTLEMENT","amount":"$amt","ref":"$ref","ts":1}""" }.joinToString(",")}]}""",
                    )
                    path.startsWith("/v3/portfolio/positions") -> MockResponse().setBody(if (held) """[{"marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000"}]""" else "[]")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun settler(t: BetTracker, feed: suspend (TrackedBet) -> BetGrader.Grade? = { null }) = ApiSettler(t, trading(), "sub", feed, clock = { now })

    @Test
    fun `a payout of a full win settles the bet as won with Novig as the grader`() = runBlocking {
        novig(ledger = listOf("mkt" to "4.00000"))
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertEquals(ApiSettler.Report(1, 1, 0, 0), r)
        val b = t.all().single()
        assertEquals(BetStatus.WON, b.status)
        assertEquals(BetSettler.BY_NOVIG, b.settledBy)
        assertEquals("Novig paid $4.00 on 400 contracts: a win", b.gradeNote)
        assertEquals(2.15, b.profit!!, 1e-9)
        // The request asked for SETTLEMENT rows around the game's start (a wide window: Novig's own start can differ from CNO's).
        val ask = server.takeRequest().path!!
        assertTrue(ask, ask.contains("kind=SETTLEMENT") && ask.contains("startsAfter=${start - ApiSettler.START_SLACK_MS}") && ask.contains("startsBefore=${start + ApiSettler.START_SLACK_MS}"))
    }

    @Test
    fun `a ledger row that names the fill or the order settles the bet as well as one naming the market`() = runBlocking {
        novig(ledger = listOf("f-o1" to "4.00000"))
        val t = tracker(); bet(t)
        assertEquals(BetStatus.WON, run { settler(t).run(); t.all().single().status })
        novig(ledger = listOf("o1" to "4.00000"))
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        assertEquals(BetStatus.WON, t2.all().single().status)
    }

    @Test
    fun `getting the cost back is a push, and any other payout is a fair-value settlement with its exact profit`() = runBlocking {
        novig(ledger = listOf("mkt" to "1.85000"))
        val t = tracker(); bet(t)
        settler(t).run()
        assertEquals(BetStatus.PUSH, t.all().single().status)
        assertEquals(0.0, t.all().single().profit!!, 1e-9)

        novig(ledger = listOf("mkt" to "2.10000"))
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        val fmv = t2.all().single()
        assertEquals(BetStatus.FMV, fmv.status)
        assertEquals(2.10 / 4.00, fmv.settleValue!!, 1e-9)
        assertEquals(2.10 - 1.85, fmv.profit!!, 1e-9)
        assertTrue(fmv.gradeNote!!.startsWith("Novig settled it at a fair value: paid $2.10"))
    }

    @Test
    fun `a market Novig hasn't paid and still holds is waiting, and a day later it's flagged`() = runBlocking {
        novig(held = true)
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertEquals(1, r.waiting)
        assertEquals(BetStatus.PENDING, t.all().single().status)
        assertEquals("Novig hasn't settled this market yet", t.all().single().gradeNote)
        assertFalse(t.all().single().gradeManual)
        now = start + 26 * hour
        val late = settler(t).run()
        assertEquals(1, late.manual)
        assertTrue(t.all().single().gradeNote!!.contains("a day after the game"))
        assertTrue(t.all().single().gradeManual)
    }

    @Test
    fun `no payout and no position is a loss, but the score feeds are asked first`() = runBlocking {
        novig()
        // The feeds say lost: settled as a loss with their evidence.
        val t = tracker(); bet(t)
        settler(t) { BetGrader.Grade.Result(BetStatus.LOST, "Final: Team B 24, Team A 17") }.run()
        assertEquals(BetStatus.LOST, t.all().single().status)
        assertTrue(t.all().single().gradeNote!!.startsWith("Novig paid nothing for it and no longer holds the position: a loss (Final: Team B 24"))
        assertEquals(-1.85, t.all().single().profit!!, 1e-9)

        // The feeds say WON while Novig paid nothing: left to a tap.
        val t2 = tracker(); bet(t2)
        val r2 = settler(t2) { BetGrader.Grade.Result(BetStatus.WON, "Final: Team A 31, Team B 17") }.run()
        assertEquals(1, r2.manual)
        assertEquals(BetStatus.PENDING, t2.all().single().status)
        assertTrue(t2.all().single().gradeNote!!.contains("the score feeds say won") && t2.all().single().gradeManual)

        // No readable feed: waits, and takes the loss only six hours after the start.
        val t3 = tracker(); bet(t3)
        assertEquals(1, settler(t3).run().waiting)
        assertEquals(BetStatus.PENDING, t3.all().single().status)
        now = start + 7 * hour
        settler(t3).run()
        assertEquals(BetStatus.LOST, t3.all().single().status)
    }

    @Test
    fun `when Novig can't be read nothing changes`() = runBlocking {
        novig(down = true)
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertTrue(r.stopped)
        assertEquals(BetStatus.PENDING, t.all().single().status)
    }

    @Test
    fun `a result Tj tapped is never overwritten, and other markets' payouts are ignored`() = runBlocking {
        novig(ledger = listOf("mkt" to "4.00000"))
        val t = tracker(); val b = bet(t)
        t.settle(b.id, BetStatus.LOST)
        assertEquals(0, settler(t).run().asked)
        assertEquals(BetStatus.LOST, t.all().single().status)
        novig(ledger = listOf("some-other-market" to "9.00000"), held = true)
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        assertEquals(BetStatus.PENDING, t2.all().single().status)
    }

    @Test
    fun `an API bet is left to the ledger while betting is set up, and graded from scores otherwise`() = runBlocking {
        val t = tracker(); bet(t)
        val scores = object : ScoreSource {
            override fun covers(league: String) = false
            override suspend fun games(league: String, date: java.time.LocalDate): List<GameScore>? = emptyList()
            override suspend fun players(game: GameScore): List<PlayerLine>? = emptyList()
        }
        val leaving = BetSettler(t, scores, clock = { now }, leaveApiBets = { true })
        assertTrue(leaving.due(t.all(), now).isEmpty())
        val grading = BetSettler(t, scores, clock = { now }, leaveApiBets = { false })
        assertEquals(1, grading.due(t.all(), now).size)
    }

    // ---- syncing fills the Tracker never got -----------------------------------------------------------------

    private class FakeNovig(val market: NovigMarket?, val event: NovigEvent?) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = emptyList<NovigEvent>()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = emptyList<NovigMarket>()
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, 0, 0)
        override suspend fun market(marketId: String) = market
        override suspend fun event(eventId: String) = event
    }

    private fun fills(vararg f: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path!!.startsWith("/v3/portfolio/fills")) MockResponse().setBody("""{"items":[${f.joinToString(",")}]}""") else MockResponse().setResponseCode(404)
        }
    }

    private fun fillJson(orderId: String, ts: Long) =
        """{"fillId":"f-$orderId","orderId":"$orderId","marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000","taker":true,"fee":"0.00000","ts":$ts}"""

    @Test
    fun `a fill the Tracker never got is added with no EV claimed, and known or old orders are skipped`() = runBlocking {
        val t = tracker()
        bet(t, "known")
        fills(fillJson("known", now - 60_000), fillJson("lost-answer", now - 60_000), fillJson("old", now - 3 * hour))
        val event = NovigEvent("ev", "FOOTBALL", "NFL", "OPEN_PREGAME", "Team B @ Team A", start)
        val sync = ApiBetSync(t, trading(), FakeNovig(market, event), clock = { now })
        val r = sync.run()
        assertEquals(ApiBetSync.Report(1, 0), r)
        val added = t.all().first { it.orderId == "lost-answer" }
        assertEquals("Team A", added.selection)
        assertEquals("Team B @ Team A", added.eventName)
        assertEquals("NFL", added.league)
        assertTrue(added.imported)
        assertNull(added.evPercentAtBet)
        assertEquals(400L, added.contracts)
        assertEquals(1.85, added.stake, 1e-9)
        assertTrue("the old order stayed out", t.all().none { it.orderId == "old" })
        // Syncing again adds nothing.
        assertEquals(0, sync.run().added)
        // "Sync with Novig" asks for everything: the old order comes in too.
        assertEquals(1, ApiBetSync(t, trading(), FakeNovig(market, event), clock = { now }).run(sinceMs = null).added)
    }

    @Test
    fun `a fill whose market Novig no longer lists is counted, not guessed`() = runBlocking {
        val t = tracker()
        fills(fillJson("gone", now - 60_000))
        val r = ApiBetSync(t, trading(), FakeNovig(null, null), clock = { now }).run()
        assertEquals(ApiBetSync.Report(0, 1), r)
        assertTrue(t.all().isEmpty())
    }
}
