package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudTradeKeyTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() { server.shutdown() }

    private fun base() = server.url("").toString().trimEnd('/')
    private fun management(): ManagementKey { val (priv, _) = CloudNovigKey.generate(); return ManagementKey("mgmt-key-0001", priv) }

    @Test
    fun `swap revokes the live trading key, waits out a 409, mints a trading key from a software keypair and proves it`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))                                                                 // echo (management)
        server.enqueue(MockResponse().setBody("""[{"keyId":"live-1","label":"Vigilant","type":"trading"}]"""))        // list subaccounts
        server.enqueue(MockResponse().setBody("{}"))                                                                 // DELETE
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"CONFLICT","message":"live"}"""))      // revoke not yet effective
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"keyId":"trade-2","fingerprint":"f"}"""))     // mint
        server.enqueue(MockResponse().setBody("{}"))                                                                 // echo (new key)
        var waits = 0
        val r = CloudTradeKey.swap(OkHttpClient(), json, management(), "live-1", base(), clock = { 1L }, pause = { waits++ })
        assertEquals("trade-2", r.key.keyId)
        assertTrue(r.verified)
        assertEquals(1, waits)
        server.takeRequest(); server.takeRequest()
        val del = server.takeRequest()
        assertEquals("DELETE", del.method); assertEquals("/v3/keys/live-1", del.path)
        server.takeRequest()
        val mint = server.takeRequest()
        assertEquals("/v3/account/subaccounts/live-1/keys", mint.path)
        assertEquals("mgmt-key-0001", mint.getHeader("Novig-Key-Id"))
        val body = json.parseToJsonElement(mint.body.readUtf8()) as JsonObject
        assertEquals("trading", (body["scope"] as JsonPrimitive).content)
        assertEquals("trade-2", server.takeRequest().getHeader("Novig-Key-Id"))
        // the private half we hold is the one whose public half Novig was given
        val sig = PemSigningKey("trade-2", r.key.pem)
        assertTrue(sig.sign("x".toByteArray()).isNotEmpty())
        assertTrue("the key's text never appears in its own toString", !r.key.toString().contains("PRIVATE"))
    }
}
