package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.SecretBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Tj, 2026-10-30: a 100% copy of the app, keys included, to move into Samsung's Secure Folder. The target has its own Keystore, so a secret sealed on the source cannot be opened there: the backup carries it opened
 * (inside the passphrase-encrypted file) and the target seals it again. Here "another phone" is a different [SecretBox].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FullBackupTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private class Box(val tag: String) : SecretBox {
        override fun seal(plain: String) = "$tag:" + java.util.Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun open(sealed: String): String {
            require(sealed.startsWith("$tag:")) { "sealed by another phone" }
            return String(java.util.Base64.getDecoder().decode(sealed.removePrefix("$tag:")))
        }
    }

    private val pem = "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIFAKEFAKEFAKE\n-----END PRIVATE KEY-----\n" // FAKE
    private val pass = "a long passphrase for the test"

    @Test
    fun `a backup made on one phone restores every key and file on another`() = runBlocking {
        val src = app.container
        src.installSecretBoxForTest(Box("phoneA"))
        src.keyStore.setKeys(ApiProvider.PROPLINE, listOf("propline-a", "propline-b"))
        src.managementKeys.save(ManagementKey("mgmt-key-12345678", pem))
        src.cloudNovigKeys.save(com.tjshea.vigilant.data.cloud.EngineNovigKey("engine-read-0001", pem), "sub-1")
        src.cloudTradeKeys.save(com.tjshea.vigilant.data.cloud.EngineNovigKey("engine-trade-0002", pem), "sub-1")
        src.kalshiKeyStore.update { com.tjshea.vigilant.data.kalshi.KalshiCredentials("kalshi-id", src.sealSecret(pem)) }
        File(app.filesDir, "history_test.json").writeText("""{"bets":["a-real-bet-history"]}""")
        File(app.filesDir, "sub/history.jsonl").also { it.parentFile!!.mkdirs() }.writeText("line1\nline2\n")

        val out = ByteArrayOutputStream()
        val made = FullBackup.write(src, out, pass)
        assertTrue(made.note, made.ok)
        assertTrue(made.secrets.containsAll(listOf("Novig management key", "Novig read key for GitHub", "Novig trading key", "Kalshi private key")))
        // The file is the passphrase's, not plain: no key text shows in it.
        assertFalse(String(out.toByteArray(), Charsets.ISO_8859_1).contains("PRIVATE KEY")) // FAKE
        assertFalse(String(out.toByteArray(), Charsets.ISO_8859_1).contains("propline-a"))

        // "Another phone": nothing here, a different Keystore.
        app.filesDir.listFiles()!!.forEach { it.deleteRecursively() }
        val dst = AppContainer(app)
        dst.installSecretBoxForTest(Box("phoneB"))
        val wrong = FullBackup.stage(dst, out.toByteArray().inputStream(), "not the passphrase at all")
        assertFalse(wrong.ok)
        assertFalse("a wrong passphrase leaves nothing staged", FullBackup.pending(app))

        val staged = FullBackup.stage(dst, out.toByteArray().inputStream(), pass)
        assertTrue(staged.note, staged.ok)
        assertTrue(FullBackup.pending(app))
        FullBackup.applyPending(app)
        assertFalse(FullBackup.pending(app))

        val after = AppContainer(app)
        after.installSecretBoxForTest(Box("phoneB"))
        assertEquals(listOf("propline-a", "propline-b"), after.keyStore.getKeys(ApiProvider.PROPLINE))
        assertEquals(pem, after.managementKeys.load()!!.pem)
        assertEquals("mgmt-key-12345678", after.managementKeys.load()!!.keyId)
        assertEquals("engine-read-0001", after.cloudNovigKeys.load()!!.keyId)
        assertEquals(pem, after.cloudTradeKeys.load()!!.pem)
        val kalshi = after.kalshiKeyStore.read()
        assertEquals("kalshi-id", kalshi.keyId)
        assertEquals(pem, after.openSecret(kalshi.pem))
        assertEquals("""{"bets":["a-real-bet-history"]}""", File(app.filesDir, "history_test.json").readText())
        assertEquals("line1\nline2\n", File(app.filesDir, "sub/history.jsonl").readText())
        assertNotNull(after.managementKeys)
    }
}
