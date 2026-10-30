package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.cloud.EngineNovigKey
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.SecretBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Tj, 2026-10-30: two devices must hold the SAME trading key, or the second revokes the first's. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TradeKeySharingTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private class Box(val tag: String) : SecretBox {
        override fun seal(plain: String) = "$tag:" + java.util.Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun open(sealed: String): String {
            require(sealed.startsWith("$tag:")) { "sealed by another device" }
            return String(java.util.Base64.getDecoder().decode(sealed.removePrefix("$tag:")))
        }
    }

    private val pem = "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIFAKEFAKEFAKE\n-----END PRIVATE KEY-----\n" // FAKE
    private val pass = "a long passphrase for the test"

    @Test
    fun `the second device takes the first device's key and revokes nothing`() = runBlocking {
        val a = app.container
        a.installSecretBoxForTest(Box("deviceA"))
        assertFalse("no key yet", TradeKeySharing.export(a, ByteArrayOutputStream(), pass).ok)
        a.cloudTradeKeys.save(EngineNovigKey("trade-key-0001", pem), "sub-1")
        val file = ByteArrayOutputStream()
        assertTrue(TradeKeySharing.export(a, file, pass).ok)

        // "Device B": its own Keystore, a connection from its own Enable betting that does not yet hold the key.
        val b = AppContainer(app)
        b.installSecretBoxForTest(Box("deviceB"))
        b.cloudTradeKeys.clear()
        b.novigConnection.save(NovigConnection("read-b", "alias-b", "sub-1", createdSubaccount = false, tradingKeyId = null, tradingAlias = null))
        assertFalse(TradeKeySharing.import(b, file.toByteArray().inputStream(), "the wrong passphrase!!!!") { error("must not be asked") }.ok)
        assertNull(b.cloudTradeKeys.load())
        // Novig refuses (the key was replaced): nothing is kept.
        assertFalse(TradeKeySharing.import(b, file.toByteArray().inputStream(), pass) { error("401") }.ok)
        assertNull(b.cloudTradeKeys.load())

        var checked: String? = null
        val r = TradeKeySharing.import(b, file.toByteArray().inputStream(), pass) { checked = it.keyId }
        assertTrue(r.note, r.ok)
        assertEquals("trade-key-0001", checked)
        assertEquals(pem, b.cloudTradeKeys.load()!!.pem)
        val conn = b.novigConnection.load()!!
        assertEquals("trade-key-0001", conn.tradingKeyId)
        assertEquals(com.tjshea.vigilant.app.data.SharedTradeVault.SHARED, conn.tradingAlias)
        assertEquals("read-b", conn.readKeyId)
        assertFalse(r.note.contains("PRIVATE"))
    }
}
