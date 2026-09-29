package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.TimeZone

/**
 * The "Grading check" (Tj, 2026-09-29: "tell me what you need me to do or show you to make sure the grading works after the bets are done"): a text
 * report of what Novig's ledger and positions say about each API bet beside what the Tracker did with it.
 */
class ApiGradingCheckTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"
    private val requests = ArrayList<RecordedRequest>()

    private val start = 1_800_000_000_000L
    private val now = start + 5 * 3_600_000L
    private val hour = 3_600_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })
    private fun trading() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid", pem), server.url("").toString().trimEnd('/')), json)

    private val market = NovigMarket(
        "mkt-1", "ev", "MONEY", "OPEN", "Team A vs Team B", start, MarketFee.GAME,
        listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    private fun target() = BetTarget(market, "A", "NFL", "Team B @ Team A", start, "Moneyline", "Team A", fair = 0.5, fairAsOfMs = null, source = BetTracker.SOURCE_VIGILANT, placedKey = "k")

    private fun novig(ledger: String = """{"items":[]}""", positions: String = "[]", down: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                if (down) return MockResponse().setResponseCode(503)
                return when {
                    path.contains("/transactions") -> MockResponse().setBody(ledger)
                    path.startsWith("/v3/portfolio/positions") -> MockResponse().setBody(positions)
                    path.endsWith("/balance") -> MockResponse().setBody("""{"keyId":"sub","balance":"12.50000"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private suspend fun betOn(t: BetTracker) {
        t.logApi(target(), "order-1", listOf(NovigFill("fill-1", "order-1", null, "mkt-1", "A", 400, 1.85, true, 0.0, start - hour)))
    }

    private fun check(t: BetTracker) = ApiGradingCheck(t, trading(), "sub", clock = { now }, zone = TimeZone.getTimeZone("UTC"))

    @Test
    fun `nothing to check before an API bet`() = runBlocking {
        novig()
        val text = check(tracker()).report()
        assertTrue(text, text.contains("API bets in the Tracker: 0"))
        assertTrue(text, text.contains("Nothing to check yet"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a settled bet shows Novig's payout row, what its ref names, and no position, beside the Tracker's grade`() = runBlocking {
        novig(ledger = """{"items":[{"transactionId":"t1","kind":"SETTLEMENT","amount":"4.00000","ref":"mkt-1","ts":${now - 60_000L}},{"transactionId":"t0","kind":"FILL","amount":"-1.85000","ref":"fill-1","ts":${start - hour}}]}""")
        val t = tracker(); betOn(t)
        t.editMany(mapOf(t.all().single().id to { b: TrackedBet -> b.copy(status = BetStatus.WON, settledBy = BetSettler.BY_NOVIG, gradeNote = "Novig paid $4.00 on 400 contracts: a win") }))
        val text = check(t).report()
        assertTrue(text, text.contains("API bets in the Tracker: 1 (open 0, graded by Novig 1"))
        assertTrue(text, text.contains("Wallet: $12.50"))
        assertTrue(text, text.contains("Positions held: 0 (none)"))
        assertTrue(text, text.contains("SETTLEMENT · +4.00000 · ref mkt-1 (market of Team A)"))
        assertTrue(text, text.contains("FILL · -1.85000 · ref fill-1 (fill of Team A)"))
        assertTrue(text, text.contains("order order-1 · fills 1 (fill-1)"))
        assertTrue(text, text.contains("400 contracts · paid $1.85000"))
        assertTrue(text, text.contains("a win pays $4.00"))
        assertTrue(text, text.contains("Tracker: WON, graded by novig: Novig paid $4.00 on 400 contracts: a win"))
        assertTrue(text, text.contains("Novig's ledger rows naming it: SETTLEMENT +4.00000 (ref market of Team A); FILL -1.85000 (ref fill of Team A)"))
        assertTrue(text, text.contains("Novig's position on it: not held"))
        // Every kind is asked for (a loss leaves no SETTLEMENT row, so the fill and fee rows are what tell), inside the ±12 h window.
        val ledgerAsk = requests.first { it.path!!.contains("/transactions") }.path!!
        assertFalse(ledgerAsk, ledgerAsk.contains("kind="))
        assertTrue(ledgerAsk, ledgerAsk.contains("startsAfter=${start - ApiSettler.START_SLACK_MS}") && ledgerAsk.contains("startsBefore=${start + ApiSettler.START_SLACK_MS}"))
    }

    @Test
    fun `an open bet still held, and one with a row that names none of Tj's ids, read plainly`() = runBlocking {
        novig(
            ledger = """{"items":[{"transactionId":"t9","kind":"SETTLEMENT","amount":"1.00000","ref":"someone-else","ts":${now - 1000L}}]}""",
            positions = """[{"marketId":"mkt-1","outcomeId":"A","qty":400,"cost":"1.85000"}]""",
        )
        val t = tracker(); betOn(t)
        val text = check(t).report()
        assertTrue(text, text.contains("Positions held: 1"))
        assertTrue(text, text.contains("market mkt-1 · outcome A · 400 contracts · cost $1.85000"))
        assertTrue(text, text.contains("ref someone-else (not one of your bets' ids)"))
        assertTrue(text, text.contains("Novig's ledger rows naming it: none"))
        assertTrue(text, text.contains("Novig's position on it: held 400 contracts, cost $1.85000"))
        assertTrue(text, text.contains("Tracker: PENDING"))
    }

    @Test
    fun `when Novig can't be read the report says so instead of failing`() = runBlocking {
        novig(down = true)
        val t = tracker(); betOn(t)
        val text = check(t).report()
        assertTrue(text, text.contains("Wallet: couldn't be read"))
        assertTrue(text, text.contains("Positions: couldn't be read"))
        assertTrue(text, text.contains("Ledger: couldn't be read"))
        assertTrue(text, text.contains("1. Team A"))
    }
}
