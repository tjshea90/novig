package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.ApiProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CloudKeysTest {
    @Test
    fun `every provider is either sent to a secret the workflow reads, or kept on the phone on purpose`() {
        val covered = CloudKeys.BY_PROVIDER.keys + CloudKeys.PHONE_ONLY
        assertEquals("a new ApiProvider must be added to CloudKeys.BY_PROVIDER or PHONE_ONLY", ApiProvider.entries.toSet(), covered)
        assertTrue(CloudKeys.BY_PROVIDER.keys.intersect(CloudKeys.PHONE_ONLY).isEmpty())
        assertTrue("Pinnodds never goes to GitHub", ApiProvider.PINNODDS !in CloudKeys.BY_PROVIDER)
        assertTrue("BoltOdds never goes to GitHub", ApiProvider.BOLTODDS !in CloudKeys.BY_PROVIDER)
        // every secret the phone sends is one the engine reads, and the workflow passes it
        assertEquals(CloudKeys.ALL.toSet(), CloudKeys.BY_PROVIDER.values.toSet())
        val wf = java.io.File("../.github/workflows/cloud-scan.yml").takeIf { it.exists() } ?: java.io.File(".github/workflows/cloud-scan.yml")
        if (wf.exists()) CloudKeys.ALL.forEach { n -> assertTrue("cloud-scan.yml passes $n", wf.readText().contains("$n: \${{ secrets.$n }}")) }
    }

    @Test
    fun `a secret is sealed to the repository key and sent without the value or the token in clear`() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            val rnd = java.security.SecureRandom()
            val sk = ByteArray(32).also { rnd.nextBytes(it) }
            val pk = ByteArray(32).also { org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(sk, 0, it, 0) }
            server.enqueue(MockResponse().setBody("""{"key_id":"568250167242549743","key":"${Base64.getEncoder().encodeToString(pk)}"}"""))
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Resource not accessible by personal access token"}"""))
            val c = CloudClient(OkHttpClient(), { "ghp_FAKEtokenFAKEtokenFAKEtoken" }, "o/r", server.url("/").toString().trimEnd('/'))
            val key = (c.secretsKey() as CloudClient.Poll.Fresh).value
            assertEquals("568250167242549743", key.keyId)
            assertNull(c.putSecret("SGO_API_KEY", "super-secret-key-value", key))
            server.takeRequest()
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertTrue(put.path!!.endsWith("/repos/o/r/actions/secrets/SGO_API_KEY"))
            val body = put.body.readUtf8()
            assertFalse("the value is not sent in clear", body.contains("super-secret-key-value"))
            assertTrue(body.contains("\"key_id\":\"568250167242549743\""))
            val why = c.putSecret("X", "v", key)
            assertTrue(why!!.contains("Secrets: read and write"))
            assertFalse(why.contains("ghp_"))
        } finally { server.shutdown() }
    }
}
