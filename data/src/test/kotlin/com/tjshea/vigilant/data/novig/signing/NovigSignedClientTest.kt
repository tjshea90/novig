package com.tjshea.vigilant.data.novig.signing

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class NovigSignedClientTest {

    private lateinit var server: MockWebServer
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, PemSigningKey("kid-1", pem),
        server.url("").toString().trimEnd('/'), clock = { 1_755_000_000_000 })

    @Test
    fun `echo is signed over the exact bytes it sends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"hello":"vigilant"}"""))
        client().echo()
        val r = server.takeRequest()
        val body = r.body.readByteArray()
        assertEquals("/v3/echo", r.path)
        assertEquals("kid-1", r.getHeader("Novig-Key-Id"))
        assertTrue(r.getHeader("Content-Type")!!.startsWith("application/json"))
        val text = NovigV3.stringToSign(1_755_000_000_000, "POST", "/v3/echo", null, body).toByteArray()
        val sig = Base64.getDecoder().decode(r.getHeader("Novig-Signature"))
        assertTrue(Ed25519Signer().run { init(false, pair.public); update(text, 0, text.size); verifySignature(sig) })
    }

    @Test
    fun `a VPN refusal becomes plain advice`() = runTest {
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK","message":"anonymized network"}"""))
        val e = assertThrows(NovigApiException::class.java) { kotlinx.coroutines.runBlocking { client().echo() } }
        assertEquals("ANONYMIZED_NETWORK", e.code)
        assertTrue(e.advice.contains("VPN"))
    }

    @Test
    fun `a stale geolocation says to open the Novig app`() = runTest {
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"GEOLOCATION_EXPIRED","message":"x"}"""))
        val e = assertThrows(NovigApiException::class.java) { kotlinx.coroutines.runBlocking { client().echo() } }
        assertTrue(e.advice.contains("open the Novig app"))
    }

    @Test
    fun `subaccounts parse from a bare array and creation sends label, key and algorithm`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"keyId":"t1","label":"Vigilant","balance":"0.00000"}]"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"r1","fingerprint":"ab"}"""))
        val c = client()
        assertEquals("Vigilant", c.listSubaccounts().single().label)
        val created = c.createSubaccountKey("t1", "Vigilant scanner", "PUBPEM", NovigKeyAlgorithm.P256, "trading::read")
        assertEquals("r1", created.keyId)
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("/v3/account/subaccounts/t1/keys", post.path)
        assertEquals("""{"name":"Vigilant scanner","publicKey":"PUBPEM","algorithm":"P-256","scope":"trading::read"}""", post.body.readUtf8())
    }
}
