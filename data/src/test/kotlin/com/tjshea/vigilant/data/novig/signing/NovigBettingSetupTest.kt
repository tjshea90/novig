package com.tjshea.vigilant.data.novig.signing

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
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Enabling betting and moving money (Tj, 2026-09-29), against a mock Novig speaking the documented key and transfer routes. */
class NovigBettingSetupTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val mgmtPem = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair().let {
        "-----BEGIN PRIVATE KEY-----\n" + // FAKE: generated fresh by this test
            Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(it.private).encoded) + "\n-----END PRIVATE KEY-----"
    }
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var now = 1_800_000_000_000L
    private val conn = NovigConnection("read-1", "vigilant_novig_read_1", "sub-1", createdSubaccount = false)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun setup(vault: MemoryVault) = NovigBettingSetup(OkHttpClient(), json, vault, server.url("").toString().trimEnd('/'), clock = { now }, pause = { now += it })

    /** [liveTradingKey] is the ID Novig treats as the subaccount's live trading key; [staleFor] mint attempts answer 409 first. */
    private fun novig(liveTradingKey: String = "sub-1", staleFor: Int = 0, transferStatuses: List<String> = listOf("Applied"), actualBalance: String? = null) {
        var mintTries = 0
        var live = liveTradingKey
        val statuses = transferStatuses.toMutableList()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                val signedBy = request.getHeader("Novig-Key-Id")
                return when {
                    request.method == "POST" && path == "/v3/echo" ->
                        // A trading key that isn't the live one is unknown to Novig.
                        if (signedBy != null && signedBy.startsWith("trading-") && signedBy != live) MockResponse().setResponseCode(401).setBody("""{"code":"SIGNATURE_REJECTED","message":"api key not found"}""")
                        else MockResponse().setBody("""{"hello":"vigilant"}""")
                    path == "/v3/account/subaccounts" -> MockResponse().setBody("""[{"keyId":"$liveTradingKey","label":"Vigilant","balance":"0.00000"}]""")
                    request.method == "DELETE" && path.startsWith("/v3/keys/") -> MockResponse().setResponseCode(204)
                    request.method == "POST" && path.endsWith("/keys") ->
                        if (mintTries++ < staleFor) MockResponse().setResponseCode(409).setBody("""{"code":"CONFLICT","message":"a live trading key already reaches this subaccount"}""")
                        else { live = "trading-new"; MockResponse().setResponseCode(201).setBody("""{"keyId":"trading-new","fingerprint":"sha256:x"}""") }
                    request.method == "POST" && path.endsWith("/transfer") -> MockResponse().setResponseCode(202).setBody("""{"transferId":"tr1","status":"Requested","direction":"fund","amount":"10.00000"}""")
                    path.contains("/transfers/tr1") -> {
                        val s = if (statuses.size > 1) statuses.removeAt(0) else statuses.first()
                        MockResponse().setBody("""{"transferId":"tr1","status":"$s","actualBalance":${actualBalance?.let { "\"$it\"" } ?: "null"}}""")
                    }
                    path.endsWith("/balance") -> MockResponse().setBody("""{"keyId":"sub-1","balance":"10.00000"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `a trading key already on the phone that Novig accepts is used, and nothing is revoked`() = runBlocking {
        novig(liveTradingKey = "sub-1")
        val vault = MemoryVault().also { it.generate("vigilant_novig_trading_5") }
        // The alias signs as the live key "sub-1", which the mock accepts for every signer id; ids starting "trading-" are the stale ones.
        val out = setup(vault).enable(conn.copy(tradingAlias = null), "mgmt-1", mgmtPem)
        assertEquals("vigilant_novig_trading_5", out.tradingAlias)
        assertEquals("sub-1", out.tradingKeyId)
        assertTrue("nothing was revoked", requests.none { it.method == "DELETE" })
        assertTrue(requests.none { it.method == "POST" && it.path!!.endsWith("/keys") })
    }

    @Test
    fun `with no usable trading key, the live one is revoked and a new one minted from the phone, retrying while Novig finishes revoking`() = runBlocking {
        novig(liveTradingKey = "sub-1", staleFor = 2)
        val vault = MemoryVault()
        val steps = ArrayList<String>()
        val out = setup(vault).enable(conn, "mgmt-1", mgmtPem) { steps += it }
        assertEquals("trading-new", out.tradingKeyId)
        assertEquals("sub-1", out.subaccountKeyId) // the subaccount's address doesn't change
        assertTrue(vault.keys.containsKey(out.tradingAlias!!))
        assertTrue(out.tradingAlias!!.startsWith(NovigBettingSetup.TRADING_PREFIX))
        assertEquals("the old key was revoked", "/v3/keys/sub-1", requests.single { it.method == "DELETE" }.path)
        assertEquals("two 409s, then the third mint worked", 3, requests.count { it.method == "POST" && it.path!!.endsWith("/keys") })
        assertTrue(steps.any { it.contains("Waiting for Novig to finish revoking") })
        // The mint asked for the trading scope, on the subaccount's address.
        val mint = requests.first { it.method == "POST" && it.path!!.endsWith("/keys") }
        assertEquals("/v3/account/subaccounts/sub-1/keys", mint.path)
        assertTrue(mint.body.clone().readUtf8().contains("\"scope\":\"trading\""))
    }

    @Test
    fun `a mint that never works leaves no key behind on the phone`() = runBlocking {
        novig(staleFor = 1_000)
        val vault = MemoryVault()
        val e = runCatching { setup(vault).enable(conn, "mgmt-1", mgmtPem) }.exceptionOrNull()
        assertTrue(e.toString(), e is NovigApiException && e.status == 409)
        assertTrue("the unused key was deleted", vault.keys.isEmpty())
    }

    @Test
    fun `funding waits for Novig to apply the transfer and reports the balance`() = runBlocking {
        novig(transferStatuses = listOf("Requested", "Requested", "Applied"))
        val out = setup(MemoryVault()).transfer(conn, "mgmt-1", mgmtPem, "fund", 10.0)
        assertTrue(out.message, out.applied)
        assertEquals(10.0, out.balance!!, 1e-9)
        assertTrue(out.message.startsWith("Added $10.00"))
        val sent = requests.first { it.path!!.endsWith("/transfer") }
        assertEquals("/v3/account/subaccounts/sub-1/transfer", sent.path)
        val body = sent.body.clone().readUtf8()
        assertTrue(body, body.contains("\"direction\":\"fund\"") && body.contains("\"amount\":\"10.00000\""))
        // The id is a plain UUID, the shape Novig parses an order's clientId as (its first real order, 2026-09-29).
        val id = Regex("\"clientTransferId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertTrue(id, com.tjshea.vigilant.data.novig.trading.NovigTradingClient.isUuid(id))
    }

    @Test
    fun `a rejected transfer says no money moved, and one that stays requested says not to send it again`() = runBlocking {
        novig(transferStatuses = listOf("Rejected"), actualBalance = "3.00000")
        val rejected = setup(MemoryVault()).transfer(conn, "mgmt-1", mgmtPem, "defund", 50.0)
        assertFalse(rejected.applied)
        assertTrue(rejected.message, rejected.message.contains("no money moved") && rejected.message.contains("\$3.00"))
        assertNull(rejected.balance)
        novig(transferStatuses = listOf("Requested"))
        val slow = setup(MemoryVault()).transfer(conn, "mgmt-1", mgmtPem, "fund", 5.0)
        assertFalse(slow.applied)
        assertTrue(slow.message, slow.message.contains("Don't send it again"))
    }
}
