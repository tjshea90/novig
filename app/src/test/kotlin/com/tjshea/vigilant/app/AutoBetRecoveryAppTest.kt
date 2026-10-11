package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Auto-bet gets going again by itself after an order whose answer was lost (Tj, 2026-10-11: "make it automatically recheck every few seconds and if it is fixed then automatically resume
 * auto bet. If it cannot be fixed, make a notification that auto bet stopped and explain the problem").
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoBetRecoveryAppTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private class FakeNovig : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        @Volatile var down: String? = null
        override suspend fun fills(orderId: String?, limit: Int): List<NovigFill> {
            down?.let { throw NovigApiException(503, null, it) }
            return emptyList()
        }
    }

    private var now = 1_000_000L
    private val sent = ArrayList<Pair<String, String>>()
    private val novig = FakeNovig()

    @Before fun setUp() {
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings(autoBet = true, autoBetHalted = null, killed = false) }
        }
        app.container.installTradingForTest(novig, "sub")
    }

    @After fun tearDown() = runBlocking { app.container.settingsStore.update { ScanSettings() }; Unit }

    private fun recovery() = AutoBetRecovery(app.container, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), app, clock = { now }, notify = { t, x -> sent += t to x })
    private fun halt(text: String) = runBlocking { app.container.settingsStore.update { it.copy(autoBetHalted = text) }; Unit }
    private fun halted() = runBlocking { app.container.currentSettings().autoBetHalted }

    @Test
    fun `a lost-order halt waits for the order to end, reads Novig's fills, then clears itself`() = runBlocking {
        val r = recovery()
        halt("Vigilant stopped while an order for Austin Hooper Over 7.5 (\$1.47) was being placed, so nothing says whether Novig filled it.")
        assertFalse(r.step())
        assertNotNull("not yet: the order may still be ending", halted())
        now += AutoBetRecovery.SETTLE_MS + 1
        assertTrue(r.step())
        assertNull(halted())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a halt that is not about a lost order is left for Tj`() = runBlocking {
        val r = recovery()
        halt("Novig refused: location check failed")
        now += 10 * AutoBetRecovery.SETTLE_MS
        assertFalse(r.step())
        assertEquals("Novig refused: location check failed", halted())
    }

    @Test
    fun `when Novig cannot be read it keeps trying, says so once after three minutes, and clears the moment Novig answers`() = runBlocking {
        val r = recovery()
        halt("${AutoBettor.LOST_ORDER_PREFIX}: Something went wrong while placing it (timeout).")
        novig.down = "Novig is down for maintenance"
        now += AutoBetRecovery.SETTLE_MS + 1
        assertFalse(r.step())
        assertTrue(sent.isEmpty())
        now += AutoBetRecovery.GIVE_UP_MS
        assertFalse(r.step())
        assertFalse(r.step())
        assertEquals("one notification, not one per try", 1, sent.size)
        assertEquals("Auto-bet is stopped", sent.single().first)
        assertTrue(sent.single().second.contains("Novig is down for maintenance"))
        assertNotNull(halted())
        novig.down = null
        assertTrue(r.step())
        assertNull(halted())
    }

    @Test
    fun `the kill switch is never lifted by it`() = runBlocking {
        val r = recovery()
        halt("${AutoBettor.LOST_ORDER_PREFIX}: x")
        app.container.settingsStore.update { it.copy(killed = true) }
        now += 10 * AutoBetRecovery.SETTLE_MS
        assertFalse(r.step())
        assertNotNull(halted())
    }
}
