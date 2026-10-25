package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

/** `GET /v3/portfolio/pnl` (Novig changelog 2026-10-23): the sums over every open position are read with one row asked for, and the dashboard says them in words. */
class PortfolioPnlTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/')), json)

    @Test fun theSumsAreReadAndOneRowIsAsked() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"outcomeId":"o1","qty":"10","cost":"5.5","last":"0.6"}],"next":"c1","basis":"55","value":"60.25"}"""))
        val p = client().pnl()!!
        assertEquals(55.0, p.basis, 1e-9)
        assertEquals(5.25, p.unrealized, 1e-9)
        val r = server.takeRequest()
        assertEquals("/v3/portfolio/pnl", r.requestUrl!!.encodedPath)
        assertEquals("1", r.requestUrl!!.queryParameter("limit"))
    }

    @Test fun noSumsIsNull() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        assertNull(client().pnl())
    }

    @Test fun theDashboardLine() {
        val now = 1_790_500_000_000L
        assertNull(DashboardModel.markedWallet(CloudStatus(), now))
        val st = CloudStatus(walletCash = 100.0, walletPositionsValue = 60.25, walletPositionsCost = 55.0, walletPnlAtMs = now - 60_000L)
        val line = DashboardModel.markedWallet(st, now)!!
        assertTrue(line, line.contains("$60.25") && line.contains("+$5.25") && line.contains("$160.25"))
    }
}
