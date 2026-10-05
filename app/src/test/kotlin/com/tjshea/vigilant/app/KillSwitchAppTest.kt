package com.tjshea.vigilant.app

import android.Manifest
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanRun
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The kill switch (Tj, 2026-10-05: "a stop button kill switch in the app visible everywhere that immediately stops all scanning, all auto betting, all auto
 * bidding, and all background scan. If I press this, everything remains off, even if I close the app and open it again, until I press resume"), on a real
 * app: pressing it saves it twice and takes every bid off Novig; nothing but its own Resume lifts it; a settings file that lost it gets it back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class KillSwitchAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = SampleScan.NOW

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.makerStore.update { emptyList() }
            app.container.killMarker.set(false)
            app.container.settingsStore.update { SampleScan.settings.copy(maker = true, autoBet = true, autoScan = AutoScanMode.BOTH, killed = false, killedAtMs = null) }
        }
    }

    @After fun tearDown() {
        runBlocking {
            app.container.killMarker.set(false)
            app.container.settingsStore.update { ScanSettings() }
        }
    }

    private class FakeNovig : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val orders = java.util.concurrent.ConcurrentHashMap<String, NovigOrder>()
        private var n = 0
        override suspend fun balance(subaccountKeyId: String) = 100.0
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            val id = "o${++n}"
            orders[id] = NovigOrder(id, clientId, "m", outcomeId, price, qty, qty, tif, "OPEN", SampleScan.NOW, ttlMs?.let { SampleScan.NOW + it })
            return id
        }
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = orders.values.filter { it.status == status }
        override suspend fun order(orderId: String) = orders[orderId]
        override suspend fun fills(orderId: String?, limit: Int) = emptyList<NovigFill>()
        override suspend fun fillsStartingAfter(startsAfterMs: Long, limit: Int) = emptyList<NovigFill>()
        override suspend fun cancelOrder(orderId: String): String? {
            val o = orders[orderId] ?: return null
            if (o.status == "OPEN") orders[orderId] = o.copy(status = "CANCELED")
            return o.status
        }
        override suspend fun cancelOrders(marketId: String?, eventId: String?): Int {
            val open = orders.values.filter { it.status == "OPEN" }
            open.forEach { orders[it.orderId] = it.copy(status = "CANCELED") }
            return open.size
        }
    }

    private fun runner(novig: FakeNovig) = MakerRunner(
        app, app.container, clock = { now }, scan = { ScanRun(result = SampleScan.result(), finished = 1) },
        desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { now }) },
    )

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    @Test
    fun `pressing STOP saves it twice, takes every resting bid off Novig, and the files still say stopped when the app opens again`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        runner(novig).run("test")
        val up = novig.orders.values.count { it.status == "OPEN" }
        assertTrue("bids are up: $up", up > 0)

        val result = KillSwitch.engage(app, app.container, "test", now = 1_234_000L)

        assertTrue(result.saved)
        assertEquals(up, result.bidsCancelled)
        assertTrue(result.toast, result.toast.contains("$up bids taken down") && result.toast.contains("stays off until you tap Resume"))
        assertEquals("every bid is off Novig", 0, novig.orders.values.count { it.status == "OPEN" })
        assertTrue("and off Vigilant's books", app.container.makerDesk()!!.bids().none { it.active })
        // Saved in the settings, with when, and in the second copy.
        val saved = app.container.currentSettings()
        assertTrue(saved.killed && saved.paused)
        assertEquals(1_234_000L, saved.killedAtMs)
        assertTrue(app.container.killMarker.on)
        // Closing the app and opening it again reads the very same file: a new store on settings.json says stopped, with Tj's switches as they were.
        val reopened = JsonFileStore(File(app.filesDir, "settings.json"), ScanSettings.serializer(), { ScanSettings() }, Json { ignoreUnknownKeys = true; encodeDefaults = true }).read()
        assertTrue(reopened.killed)
        assertTrue(reopened.autoBet && reopened.maker && reopened.autoScan == AutoScanMode.BOTH)
        assertFalse(reopened.autoBetsNow)
        assertFalse(reopened.makerNow)
        assertEquals(AutoScanMode.OFF, reopened.activeAutoScan)
        // Pressed again: still one stop, the first time kept.
        KillSwitch.engage(app, app.container, "again", now = 9_999_000L)
        assertEquals(1_234_000L, app.container.currentSettings().killedAtMs)
    }

    @Test
    fun `a settings file that lost the switch - reset, restored from a backup, damaged - gets it back from the second copy, and only Resume turns it off`() = runBlocking {
        app.container.killMarker.set(true, 777L)
        // Reset to the defaults (what a damaged file reads as): the container's default and its reconcile both say stopped.
        val fresh = KillSwitch.reconcile(ScanSettings(), app.container.killMarker)
        assertTrue(fresh.killed)
        assertEquals(777L, fresh.killedAtMs)
        // A file that says running while the marker says stopped is corrected, never the other way round.
        app.container.settingsStore.update { ScanSettings(autoBet = true) }
        app.container.settingsStore.update { KillSwitch.reconcile(it, app.container.killMarker) }
        assertTrue(app.container.currentSettings().killed)
        assertTrue(app.container.currentSettings().autoBet)
        app.container.killMarker.set(false)
        assertTrue("a marker that is off never turns a saved stop off", KillSwitch.reconcile(ScanSettings(killed = true), app.container.killMarker).killed)
        // The restart reset (auto-bet, auto-scan and auto-make off after the phone restarts) leaves the stop alone.
        assertTrue(LaunchReset.apply(ScanSettings(killed = true, autoBet = true)).killed)
        // Resume: both copies off, Tj's switches as they were.
        app.container.killMarker.set(true, 5L)
        app.container.settingsStore.update { it.copy(killed = true, killedAtMs = 5L, autoBet = true, maker = true) }
        val after = KillSwitch.release(app, app.container, "test")
        assertFalse(after.killed)
        assertEquals(null, after.killedAtMs)
        assertFalse(app.container.killMarker.on)
        assertTrue(after.autoBet && after.maker)
    }

    @Test
    fun `Pause, a pull to refresh, Scan and Check odds now cannot lift it, and the red bar's Resume can`() {
        val vm = MainViewModel(app)
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { vm.toasts.collect { toasts += it } }
        try {
            waitFor("loaded") { vm.state.value.loaded }
            vm.killAll()
            // On screen at once, then saved.
            assertTrue(vm.state.value.settings.killed)
            waitFor("saved") { runBlocking { app.container.currentSettings() }.killed && app.container.killMarker.on }
            waitFor("the stop's own toast") { toasts.any { it.startsWith("Everything is stopped") } }

            toasts.clear()
            vm.setPaused(false)
            vm.refreshCno(resume = true)
            vm.checkOdds()
            vm.scan(resume = true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(runBlocking { app.container.currentSettings() }.killed)
            assertFalse(vm.state.value.checkingOdds)
            assertFalse(app.container.runner.running)
            assertTrue(toasts.toString(), toasts.isNotEmpty() && toasts.all { it == KILLED_TOAST })

            vm.resumeAfterKill()
            waitFor("resumed") { !runBlocking { app.container.currentSettings() }.killed && !app.container.killMarker.on }
            waitFor("on screen") { !vm.state.value.settings.killed }
            assertTrue(runBlocking { app.container.currentSettings() }.autoBet)
            assertTrue(toasts.any { it.startsWith("Resumed") })
        } finally {
            listen.cancel()
        }
    }

    @Test
    fun `the auto-bet places nothing while stopped, however it is called`() = runBlocking {
        val bettor = AutoBettor(app, app.container)
        val report = bettor.run(SampleScan.settings.copy(autoBet = true, killed = true), UiState())
        assertEquals(0, report.placed.size)
        assertEquals(0, report.looked)
        assertTrue(bettor.status.value.blocker!!, bettor.status.value.blocker!!.contains("STOP"))
        // Even handed settings that say running, if the saved ones are stopped.
        app.container.settingsStore.update { it.copy(killed = true) }
        val again = bettor.run(SampleScan.settings.copy(autoBet = true), UiState())
        assertEquals(0, again.placed.size)
        assertNotNull(bettor.status.value.blocker)
    }
}
