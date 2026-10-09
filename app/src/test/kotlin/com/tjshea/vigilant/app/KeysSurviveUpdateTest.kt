package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.SecretBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-09-29: "Make this and all keys persist even through app updates". An update replaces the app's code and keeps its files and its
 * Keystore (same app, same signing certificate: BRIEF.md), then starts a new process: a second [AppContainer] over the same files is that new
 * process. Every key Tj enters must come back in it: the odds keys, the Novig connection (read and trading keys) and the management key.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class KeysSurviveUpdateTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private object TestBox : SecretBox {
        override fun seal(plain: String) = "sealed:" + java.util.Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun open(sealed: String) = String(java.util.Base64.getDecoder().decode(sealed.removePrefix("sealed:")))
    }

    private val pem = "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIFAKEFAKEFAKE\n-----END PRIVATE KEY-----\n" // FAKE

    @Test
    fun `every key Tj entered is there after an update`() = runBlocking {
        val before = app.container
        before.installSecretBoxForTest(TestBox)
        before.keyStore.setKeys(ApiProvider.PINNWIRE, listOf("pinnwire-a", "pinnwire-b"))
        before.keyStore.setKeys(ApiProvider.PROPLINE, listOf("propline-a"))
        before.keyStore.setKeys(ApiProvider.THE_ODDS_API, listOf("odds-a"))
        before.keyStore.setKeys(ApiProvider.SPORTSGAMEODDS, listOf("sgo-a", "sgo-b"))
        val conn = NovigConnection("read-1", "vigilant_novig_read_1", "sub-1", createdSubaccount = false, tradingKeyId = "trading-1", tradingAlias = "vigilant_novig_trading_1")
        before.novigConnection.save(conn)
        before.managementKeys.save(ManagementKey("mgmt-key-12345678", pem))

        // The updated app's first process.
        val after = AppContainer(app)
        after.installSecretBoxForTest(TestBox)
        assertEquals(listOf("pinnwire-a", "pinnwire-b"), after.keyStore.getKeys(ApiProvider.PINNWIRE))
        assertEquals(listOf("propline-a"), after.keyStore.getKeys(ApiProvider.PROPLINE))
        assertEquals(listOf("odds-a"), after.keyStore.getKeys(ApiProvider.THE_ODDS_API))
        assertEquals(listOf("sgo-a", "sgo-b"), after.keyStore.getKeys(ApiProvider.SPORTSGAMEODDS))
        val loaded = after.novigConnection.load()
        assertEquals(conn.readKeyId, loaded!!.readKeyId)
        assertEquals(conn.tradingKeyId, loaded.tradingKeyId)
        assertEquals(conn.tradingAlias, loaded.tradingAlias)
        val mgmt = after.managementKeys.load()
        assertNotNull(mgmt)
        assertEquals("mgmt-key-12345678", mgmt!!.keyId)
        assertEquals(pem, mgmt.pem)
        // Loading the app (the move from v0.6.0's store, the connection) leaves every key in place.
        after.ensureLoaded()
        assertEquals(listOf("pinnwire-a", "pinnwire-b"), after.keyStore.getKeys(ApiProvider.PINNWIRE))
        assertNotNull(after.managementKeys.load())
    }

    @Test
    fun `backups keep the odds keys and leave out what only this phone can open`() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name")).first { it.exists() }.readText()
            val excludes = Regex("""<exclude domain="file" path="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
            // Sealed by this phone's Keystore: useless anywhere else.
            assertTrue("$name: management key", "novig_management_key.json" in excludes)
            assertTrue("$name: Novig connection", "datastore/vigilant_api_keys.preferences_pb" in excludes)
            // Plain JSON: restores on a new phone.
            assertFalse("$name: odds keys", "api_keys.json" in excludes)
            assertFalse("$name: settings", "settings.json" in excludes)
            assertFalse("$name: bets", "bets.json" in excludes)
        }
    }
}
