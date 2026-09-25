package com.tjshea.vigilant.data.novig.signing

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** The phone's Keystore, in memory: P-256 keys that can sign but are never handed out. */
class MemoryVault : KeyVault {
    val keys = HashMap<String, KeyPair>()
    override fun generate(alias: String): String {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        keys[alias] = kp
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(kp.public.encoded) + "\n-----END PUBLIC KEY-----\n"
    }
    override fun signer(alias: String, keyId: String) = object : NovigSigningKey {
        override val keyId = keyId
        override val algorithm = NovigKeyAlgorithm.P256
        override fun sign(message: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run { initSign(keys.getValue(alias).private); update(message); sign() }
    }
    override fun delete(alias: String) { keys.remove(alias) }
}

class NovigSetupTest {

    private lateinit var server: MockWebServer
    private val mgmtPem = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair().let {
        "-----BEGIN PRIVATE KEY-----\n" + // FAKE: generated fresh by this test
            Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(it.private).encoded) + "\n-----END PRIVATE KEY-----"
    }

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun setup(vault: KeyVault) = NovigSetup(OkHttpClient(), Json { ignoreUnknownKeys = true }, vault, server.url("").toString().trimEnd('/'), clock = { 42L })

    @Test
    fun `first run opens a Vigilant subaccount and mints a read-only key`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))                                              // echo (mgmt)
        server.enqueue(MockResponse().setBody("[]"))                                              // no subaccounts
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"trade-1","fingerprint":"f"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"read-1","fingerprint":"g"}"""))
        server.enqueue(MockResponse().setBody("{}"))                                              // echo (read key)
        val vault = MemoryVault()
        val c = setup(vault).connect(" mgmt-1 ", mgmtPem)

        assertEquals("read-1", c.readKeyId)
        assertEquals("trade-1", c.subaccountKeyId)
        assertTrue(c.createdSubaccount)
        assertEquals(2, vault.keys.size) // trading + read, both generated on the "phone"

        val echo = server.takeRequest()
        assertEquals("mgmt-1", echo.getHeader("Novig-Key-Id"))
        server.takeRequest()
        val open = server.takeRequest()
        assertEquals("/v3/account/subaccounts", open.path)
        assertTrue(open.body.readUtf8().contains("\"label\":\"Vigilant\""))
        val mint = server.takeRequest()
        assertEquals("/v3/account/subaccounts/trade-1/keys", mint.path)
        assertTrue(mint.body.readUtf8().contains("\"scope\":\"trading::read\""))
        assertEquals("read-1", server.takeRequest().getHeader("Novig-Key-Id"))
    }

    @Test
    fun `a second run reuses the Vigilant subaccount instead of opening another`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""[{"keyId":"old-trade","label":"Vigilant","balance":"0.00000"}]"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"read-2","fingerprint":"g"}"""))
        server.enqueue(MockResponse().setBody("{}"))
        val c = setup(MemoryVault()).connect("mgmt-1", mgmtPem)
        assertEquals("old-trade", c.subaccountKeyId)
        assertEquals(false, c.createdSubaccount)
        server.takeRequest(); server.takeRequest()
        assertEquals("/v3/account/subaccounts/old-trade/keys", server.takeRequest().path)
    }

    @Test
    fun `a VPN refusal stops setup before any key is created`() {
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK","message":"vpn"}"""))
        val vault = MemoryVault()
        val e = assertThrows(NovigApiException::class.java) { runBlocking { setup(vault).connect("mgmt-1", mgmtPem) } }
        assertTrue(e.advice.contains("VPN"))
        assertTrue(vault.keys.isEmpty())
    }

    @Test
    fun `a failed mint deletes the key it generated`() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""[{"keyId":"t","label":"Vigilant","balance":"0"}]"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"FORBIDDEN","message":"api key scope is insufficient for this route"}"""))
        val vault = MemoryVault()
        assertThrows(NovigApiException::class.java) { runBlocking { setup(vault).connect("mgmt-1", mgmtPem) } }
        assertTrue(vault.keys.isEmpty())
    }
}
