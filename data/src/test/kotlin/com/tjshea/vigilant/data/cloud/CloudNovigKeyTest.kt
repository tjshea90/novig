package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.signing.SecretBox
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The Novig key the GitHub engine reads with (Tj, 2026-10-15): made on the phone from the management key, read-only, kept sealed, and used by the engine only to read ([CloudNovig]).
 */
class CloudNovigKeyTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() { server.shutdown(); scope.cancel() }

    private fun base() = server.url("").toString().trimEnd('/')

    /** A management key of the kind the web page makes (PKCS#8): here a fresh P-256 one. */
    private fun management(): ManagementKey { val (priv, _) = CloudNovigKey.generate(); return ManagementKey("mgmt-key-0001", priv) }

    @Test
    fun `the engine's key is made on the subaccount as trading read, proved with an echo, and its private half matches the public one Novig was given`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"eng-1","fingerprint":"f"}"""))
        server.enqueue(MockResponse().setBody("{}"))
        val minted = CloudNovigKey.mint(OkHttpClient(), json, management(), "sub-trade-1", base(), clock = { 42L })
        assertEquals("eng-1", minted.keyId)
        val mint = server.takeRequest()
        assertEquals("/v3/account/subaccounts/sub-trade-1/keys", mint.path)
        assertEquals("management signs it", "mgmt-key-0001", mint.getHeader("Novig-Key-Id"))
        val body = json.parseToJsonElement(mint.body.readUtf8()).let { it as kotlinx.serialization.json.JsonObject }
        assertEquals("trading::read", (body["scope"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(CloudNovigKey.NAME, (body["name"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("P-256", (body["algorithm"] as kotlinx.serialization.json.JsonPrimitive).content)
        val publicPem = (body["publicKey"] as kotlinx.serialization.json.JsonPrimitive).content
        assertTrue(publicPem.startsWith("-----BEGIN PUBLIC KEY-----\n") && publicPem.contains("\n-----END PUBLIC KEY-----"))
        // the echo is signed by the NEW key, and the private half we hold verifies against the public half Novig got
        assertEquals("eng-1", server.takeRequest().getHeader("Novig-Key-Id"))
        val signer = PemSigningKey("eng-1", minted.pem)
        assertEquals(NovigKeyAlgorithm.P256, signer.algorithm)
        val spki = Base64.getMimeDecoder().decode(publicPem.lines().filter { !it.startsWith("-----") }.joinToString(""))
        val verify = Signature.getInstance("SHA256withECDSA").apply { initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))); update("hello".toByteArray()) }
        assertTrue(verify.verify(signer.sign("hello".toByteArray())))
    }

    @Test
    fun `a refusal from Novig makes no key and carries no key text`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"SCOPE_INSUFFICIENT","message":"This key's scope can't do that"}"""))
        try {
            CloudNovigKey.mint(OkHttpClient(), json, management(), "sub-trade-1", base())
            fail("a refused mint must throw")
        } catch (e: NovigApiException) {
            assertEquals(403, e.status)
            assertFalse(e.message.orEmpty().contains("PRIVATE KEY"))
        }
        assertEquals("nothing else was asked", 1, server.requestCount)
    }

    @Test
    fun `the key is kept sealed, can be loaded to send again, and its saved record never holds the key`() = runBlocking {
        val box = object : SecretBox { override fun seal(plain: String) = "sealed:" + plain.reversed(); override fun open(sealed: String) = sealed.removePrefix("sealed:").reversed() }
        val file = tmp.newFile("k.json")
        val store = CloudNovigKeyStore(file, box, json, clock = { 99L })
        assertNull(store.load())
        val (priv, _) = CloudNovigKey.generate()
        store.save(EngineNovigKey("eng-9", priv), "sub-1")
        assertFalse("the file never holds the PEM in clear", file.readText().contains("PRIVATE KEY"))
        val back = store.load()!!
        assertEquals("eng-9", back.keyId); assertEquals(priv, back.pem)
        val saved = store.saved()!!
        assertEquals(99L, saved.mintedAtMs); assertEquals("sub-1", saved.subaccountKeyId); assertEquals("", saved.sealedPem)
        assertFalse(back.toString().contains(priv))
        store.clear()
        assertNull(store.load())
        // a phone that can no longer open it (restored elsewhere) has no key rather than a broken one
        store.save(EngineNovigKey("eng-9", priv), "sub-1")
        val other = CloudNovigKeyStore(file, object : SecretBox { override fun seal(plain: String) = plain; override fun open(sealed: String): String = error("another phone's Keystore") }, json)
        assertNull(other.load())
    }

    // ---- the engine's side -------------------------------------------------------------------------------------------------

    private fun wired(pem: String, keyId: String = "eng-1") = NovigPublicClient(OkHttpClient(), json, baseUrl = base()).let { n -> n to CloudNovig(n, OkHttpClient(), json, scope, keyId, pem, baseUrl = base(), wsUrl = "ws://127.0.0.1:1/v3/ws") }

    @Test
    fun `with the key the engine reads through the signed route and keeps the websocket as the phone's switches say`() {
        val (priv, _) = CloudNovigKey.generate()
        val (novig, c) = wired(priv)
        assertNull(c.problem)
        assertNull(novig.keyed)
        c.configure(ScanSettings())
        assertNotNull("the signed route is on", novig.keyed)
        assertNotNull("the stream follows novigSocket (on by default)", novig.stream)
        assertTrue(c.route(0L).startsWith("Novig read-only key active"))
        assertTrue(c.route(0L).contains("websocket on"))
        // the phone's socket switch off: every read by request, no stream
        c.configure(ScanSettings(gates = ApiGates(novigSocket = false)))
        assertNotNull(novig.keyed); assertNull(novig.stream)
        assertTrue(c.route(0L).contains("websocket off"))
        c.configure(ScanSettings())
        assertNotNull(novig.stream)
        c.close()
        assertNull(novig.stream)
    }

    @Test
    fun `a key that cannot be read is no key - the public routes, said in words, without the key text`() {
        val (novig, c) = wired("not a pem at all SECRETTEXT")
        assertNotNull(c.problem)
        assertFalse(c.problem!!.contains("SECRETTEXT"))
        c.configure(ScanSettings())
        assertNull(novig.keyed); assertNull(novig.stream)
        assertTrue(c.route(0L).startsWith("public routes: "))
    }

    @Test
    fun `an engine with no key says it reads the public routes`() {
        val novig = NovigPublicClient(OkHttpClient(), json, baseUrl = base())
        assertTrue(CloudNovig.route(novig, hasKey = false, streaming = false, problem = null, now = 0L).contains("no Novig key was sent"))
    }
}
