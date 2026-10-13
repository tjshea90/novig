package com.tjshea.vigilant.data.kalshi

import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.PSSSigner
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.RSAEngine
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Kalshi's signing (KALSHI_API.md), the socket's board and trust rule, and the client laying the socket's prices over its REST catalog. Nothing here has met a live key. */
class KalshiTest {

    // The PEM headers are written in two pieces so the repo's secret scan does not take a test key for a real one.
    private fun pem(kind: String, der: ByteArray) =
        "-----BEGIN " + kind + "-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END " + kind + "-----\n"

    private fun edKey(): Pair<Ed25519PrivateKeyParameters, String> {
        val p = Ed25519PrivateKeyParameters(SecureRandom())
        return p to pem("PRIVATE " + "KEY", PrivateKeyInfoFactory.createPrivateKeyInfo(p).encoded)
    }

    @Test
    fun `an Ed25519 key signs timestamp, method and path without the query, and the signature verifies`() {
        val (priv, text) = edKey()
        val signer = KalshiSigner.of(KalshiCredentials("key-id-1", text)).getOrThrow()
        assertEquals("Ed25519", signer.algorithm)
        val h = signer.headers("get", "/trade-api/v2/account/limits?x=1", 1_700_000_000_123L)
        assertEquals("key-id-1", h[KalshiSigner.HEADER_KEY])
        assertEquals("1700000000123", h[KalshiSigner.HEADER_TIMESTAMP])
        val verifier = Ed25519Signer().apply { init(false, priv.generatePublicKey()) }
        val msg = "1700000000123GET/trade-api/v2/account/limits".toByteArray()
        verifier.update(msg, 0, msg.size)
        assertTrue("the query string is not signed", verifier.verifySignature(Base64.getDecoder().decode(h[KalshiSigner.HEADER_SIGNATURE])))
    }

    @Test
    fun `a key pasted with escaped newlines or as one line is read, and a wrong one says why`() {
        val (_, text) = edKey()
        val escaped = text.trim().replace("\n", "\\n")
        assertTrue(KalshiSigner.of(KalshiCredentials("k", escaped)).isSuccess)
        val body = text.lines().filterNot { it.startsWith("-----") }.joinToString("")
        assertTrue("just the base64 body works too", KalshiSigner.of(KalshiCredentials("k", body)).isSuccess)
        assertTrue(KalshiSigner.of(KalshiCredentials("", text)).isFailure)
        assertTrue(KalshiSigner.of(KalshiCredentials("k", "not a key")).isFailure)
    }

    @Test
    fun `an RSA key signs with PSS, SHA-256 and a 32-byte salt whether its PEM is PKCS8 or PKCS1`() {
        val gen = RSAKeyPairGenerator().apply { init(RSAKeyGenerationParameters(BigInteger.valueOf(65537), SecureRandom(), 2048, 12)) }
        val pair = gen.generateKeyPair()
        val priv = pair.private as RSAPrivateCrtKeyParameters
        val pub = pair.public as RSAKeyParameters
        val info = PrivateKeyInfoFactory.createPrivateKeyInfo(priv)
        for (text in listOf(pem("PRIVATE " + "KEY", info.encoded), pem("RSA PRIVATE " + "KEY", info.parsePrivateKey().toASN1Primitive().encoded))) {
            val signer = KalshiSigner.of(KalshiCredentials("rsa-key", text)).getOrThrow()
            assertEquals("RSA", signer.algorithm)
            val h = signer.headers("GET", "/trade-api/ws/v2", 5L)
            val v = PSSSigner(RSAEngine(), SHA256Digest(), SHA256Digest(), 32).apply { init(false, pub) }
            val msg = "5GET/trade-api/ws/v2".toByteArray()
            v.update(msg, 0, msg.size)
            assertTrue(v.verifySignature(Base64.getDecoder().decode(h[KalshiSigner.HEADER_SIGNATURE])))
        }
    }

    // ---- the socket and the client ---------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ws: MockWebServer
    private lateinit var rest: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val nfl = Leagues.byNovigName("NFL")!!
    private val received = CopyOnWriteArrayList<String>()
    @Volatile private var serverSocket: WebSocket? = null
    private val restCalls = AtomicInteger(0)
    private val restHeaders = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var sidCounter = 0
    @Volatile private var tickerSubs = 0
    @Volatile private var okSeq = 10
    @Volatile private var restBid = "0.5100"
    @Volatile private var restAsk = "0.5200"

    @Before fun setUp() {
        ws = MockWebServer()
        rest = MockWebServer()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { serverSocket = webSocket }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                received += text
                val o = json.parseToJsonElement(text) as JsonObject
                if ((o["cmd"] as? JsonPrimitive)?.content == "subscribe") {
                    val id = (o["id"] as JsonPrimitive).longOrNull
                    val channel = ((o["params"] as JsonObject)["channels"] as kotlinx.serialization.json.JsonArray).first().let { (it as JsonPrimitive).content }
                    // Like the real server (Tj's report): the first subscribe to a channel is `subscribed`, a later one to the same channel is added to it and answered `ok`.
                    if (channel == "ticker" && tickerSubs++ > 0) webSocket.send("""{"id":$id,"sid":2,"seq":${++okSeq},"type":"ok"}""")
                    else webSocket.send("""{"id":$id,"type":"subscribed","msg":{"channel":"$channel","sid":${++sidCounter}}}""")
                }
            }
        }
        ws.enqueue(MockResponse().withWebSocketUpgrade(listener))
        ws.start()
        rest.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                restHeaders += request
                if (request.requestUrl!!.encodedPath.endsWith("/account/limits")) {
                    return MockResponse().setBody("""{"usage_tier":"basic","read":{"refill_rate":200,"bucket_capacity":200},"write":{"refill_rate":100,"bucket_capacity":100},"grants":[]}""")
                }
                restCalls.incrementAndGet()
                return MockResponse().setBody(
                    if (request.requestUrl!!.queryParameter("series_ticker") == "KXNFLTOTAL") {
                        """{"events":[{"event_ticker":"KXNFLTOTAL-26SEP27CARCLE","series_ticker":"KXNFLTOTAL","title":"CAR Panthers vs CLE Browns: Total Points","sub_title":"CAR vs CLE (Sep 27)",
                          "markets":[{"ticker":"KXNFLTOTAL-26SEP27CARCLE-44","title":"Full Game: over 43.5 points scored?","yes_sub_title":"Over 43.5 points scored","yes_bid_dollars":"$restBid","yes_ask_dollars":"$restAsk","floor_strike":43.5,"status":"active"}]}],"cursor":""}"""
                    } else {
                        """{"events":[]}"""
                    },
                )
            }
        }
        rest.start()
    }

    @After fun tearDown() { scope.cancel(); ws.shutdown(); rest.shutdown() }

    private fun until(what: String, ms: Long = 6_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { if (System.currentTimeMillis() > end) error("timed out waiting for $what"); Thread.sleep(20) }
    }

    private fun push(frame: String) { serverSocket!!.send(frame) }

    private fun ticker(bid: String, ask: String, size: String = "500.00") =
        """{"type":"ticker","sid":1,"seq":${++seq},"sending_ts_ms":${System.currentTimeMillis()},"msg":{"market_ticker":"KXNFLTOTAL-26SEP27CARCLE-44","yes_bid_dollars":"$bid","yes_ask_dollars":"$ask","yes_bid_size_fp":"$size","yes_ask_size_fp":"$size"}}"""

    private var seq = 1

    @Test
    fun `the socket carries a signed handshake, its subscriptions are acknowledged, and a read is current only after its market was subscribed`() {
        val (_, text) = edKey()
        val signer = KalshiSigner.of(KalshiCredentials("kid", text)).getOrThrow()
        val stream = KalshiStream(OkHttpClient(), { signer }, scope, wsUrl = ws.url("/trade-api/ws/v2").toString().replace("http", "ws"))
        val before = System.currentTimeMillis()
        stream.want("KXNFLTOTAL", listOf("KXNFLTOTAL-26SEP27CARCLE-44"))
        until("subscribed") { stream.subscribedCount == 1 }
        val req = ws.takeRequest()
        assertEquals("/trade-api/ws/v2", req.path)
        assertEquals("kid", req.getHeader("KALSHI-ACCESS-KEY"))
        assertNotNull(req.getHeader("KALSHI-ACCESS-SIGNATURE"))
        assertTrue(req.getHeader("KALSHI-ACCESS-TIMESTAMP")!!.toLong() >= before)
        assertTrue("both channels were asked for", received.any { it.contains("market_lifecycle_v2") } && received.any { it.contains("\"ticker\"") && it.contains("KXNFLTOTAL-26SEP27CARCLE-44") })
        assertFalse("a read from before the subscription is not vouched for", stream.trusted("KXNFLTOTAL-26SEP27CARCLE-44", before))
        assertTrue(stream.trusted("KXNFLTOTAL-26SEP27CARCLE-44", System.currentTimeMillis() + 1))
        assertFalse("a market never subscribed is not vouched for", stream.trusted("OTHER", System.currentTimeMillis() + 1))
        push(ticker("0.60", "0.61"))
        until("a push") { stream.top("KXNFLTOTAL-26SEP27CARCLE-44") != null }
        assertEquals(0.61, stream.top("KXNFLTOTAL-26SEP27CARCLE-44")!!.yesAsk!!, 1e-9)
        // A lost message (a gap in seq) takes the trust away until a newer read.
        val t = System.currentTimeMillis()
        seq += 5
        push(ticker("0.62", "0.63"))
        until("the gap") { stream.gaps == 1 }
        assertFalse(stream.trusted("KXNFLTOTAL-26SEP27CARCLE-44", t))
        push("""{"type":"market_lifecycle_v2","sid":2,"seq":1,"msg":{"market_ticker":"KXNFLTOTAL-26SEP27CARCLE-44","event_type":"determined","result":"yes"}}""")
        until("closed") { stream.isClosed("KXNFLTOTAL-26SEP27CARCLE-44") }
        stream.close()
        assertEquals(KalshiStream.State.OFF, stream.state)
    }

    @Test
    fun `markets added to a subscribed channel are acknowledged with ok and count as subscribed`() {
        val (_, text) = edKey()
        val signer = KalshiSigner.of(KalshiCredentials("kid", text)).getOrThrow()
        val stream = KalshiStream(OkHttpClient(), { signer }, scope, wsUrl = ws.url("/trade-api/ws/v2").toString().replace("http", "ws"))
        stream.want("A", listOf("A-1", "A-2"))
        until("the first chunk") { stream.subscribedCount == 2 }
        stream.want("B", listOf("B-1", "B-2", "B-3"))
        until("the second chunk, answered ok") { stream.subscribedCount == 5 }
        assertTrue(stream.oks >= 1)
        assertTrue(stream.trusted("B-2", System.currentTimeMillis() + 1))
        stream.close()
    }

    @Test
    fun `with a key and the socket on, a scan reads REST twice to seed, then asks nothing and reads the pushed prices`() = runBlocking {
        val (_, text) = edKey()
        val signer = KalshiSigner.of(KalshiCredentials("kid", text)).getOrThrow()
        val stream = KalshiStream(OkHttpClient(), { signer }, scope, wsUrl = ws.url("/trade-api/ws/v2").toString().replace("http", "ws"))
        val base = rest.url("/trade-api/v2").toString().trimEnd('/')
        val client = KalshiClient(OkHttpClient(), json, base, altBaseUrl = base, signer = { signer }, stream = stream, socketOn = { true })
        val s = ScanSettings().copy(families = setOf(MarketFamily.TOTAL))
        fun over(snap: com.tjshea.vigilant.data.reference.RefSnapshot) = snap.events.single().markets.single().quotes.first { it.side == com.tjshea.vigilant.data.reference.Side.OVER }.decimalOdds

        val first = client.odds(nfl, s)
        assertEquals(1.0 / 0.52, over(first), 1e-9)
        assertEquals(1, restCalls.get())
        val signed = restHeaders.first { it.requestUrl!!.encodedPath.endsWith("/events") }
        assertEquals("kid", signed.getHeader("KALSHI-ACCESS-KEY"))
        assertTrue("the account's limits were read first", restHeaders.any { it.requestUrl!!.encodedPath.endsWith("/account/limits") })
        until("subscribed") { stream.subscribedCount == 1 }
        push(ticker("0.60", "0.61"))
        until("a push") { stream.top("KXNFLTOTAL-26SEP27CARCLE-44") != null }

        // The first read came before the subscription: it is read once more, and now it is current. (A real exchange's REST already shows what it pushed.)
        restBid = "0.6000"; restAsk = "0.6100"
        val second = client.odds(nfl, s)
        assertEquals(2, restCalls.get())
        assertEquals(1.0 / 0.61, over(second), 1e-9)

        push(ticker("0.70", "0.71"))
        until("the second push") { stream.top("KXNFLTOTAL-26SEP27CARCLE-44")!!.yesAsk == 0.71 }
        val third = client.odds(nfl, s)
        assertEquals("no request: the socket vouches for the catalog", 2, restCalls.get())
        assertEquals(1.0 / 0.71, over(third), 1e-9)
        assertTrue("its prices are as new as the push", System.currentTimeMillis() - third.fetchedAtMs < 3_000)

        // A market Kalshi paused leaves the quote list.
        push("""{"type":"market_lifecycle_v2","sid":2,"seq":2,"msg":{"market_ticker":"KXNFLTOTAL-26SEP27CARCLE-44","event_type":"deactivated","is_deactivated":true}}""")
        until("paused") { stream.isClosed("KXNFLTOTAL-26SEP27CARCLE-44") }
        assertTrue(client.odds(nfl, s).events.isEmpty() || client.odds(nfl, s).events.single().markets.isEmpty())
        assertNull(restHeaders.firstOrNull { it.requestUrl!!.encodedPath.endsWith("/events") && it.getHeader("KALSHI-ACCESS-KEY") == null })
        assertTrue(client.report().any { it.contains("tier basic") })
        stream.close()
    }

    @Test
    fun `socket off or no key leaves the reads exactly as they were`() = runBlocking {
        val base = rest.url("/trade-api/v2").toString().trimEnd('/')
        val s = ScanSettings().copy(families = setOf(MarketFamily.TOTAL))
        val plain = KalshiClient(OkHttpClient(), json, base)
        plain.odds(nfl, s); plain.odds(nfl, s)
        assertEquals("every scan reads REST", 2, restCalls.get())
        assertTrue("and nothing is signed", restHeaders.all { it.getHeader("KALSHI-ACCESS-KEY") == null })
        val (_, text) = edKey()
        val signer = KalshiSigner.of(KalshiCredentials("kid", text)).getOrThrow()
        val stream = KalshiStream(OkHttpClient(), { signer }, scope, wsUrl = ws.url("/trade-api/ws/v2").toString().replace("http", "ws"))
        val off = KalshiClient(OkHttpClient(), json, base, altBaseUrl = base, signer = { signer }, stream = stream, socketOn = { false })
        off.odds(nfl, s); off.odds(nfl, s)
        assertEquals("a key with the socket off still reads REST every scan", 4, restCalls.get())
        assertEquals("and never opens the socket", KalshiStream.State.OFF, stream.state)
    }
}
