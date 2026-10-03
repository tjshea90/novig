package com.tjshea.vigilant.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.ScanRun
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Make orders in the app (Tj, 2026-10-03; RESEARCH.md §70): a pass on Vigilant's latest scan posts post-only bids with an expiry from the Vigilant wallet, a
 * fill becomes a maker bet in the Tracker with a notification (wallet line included), switching bids off or pausing takes every bid down, and
 * Diagnostics says what they did.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MakerAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = SampleScan.NOW

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.makerStore.update { emptyList() }
            app.container.settingsStore.update { SampleScan.settings.copy(maker = true) }
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
        val fillsBy = java.util.concurrent.ConcurrentHashMap<String, List<NovigFill>>()
        val placed = java.util.concurrent.CopyOnWriteArrayList<String>()
        private var n = 0

        override suspend fun balance(subaccountKeyId: String) = 100.0

        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            val id = "o${++n}"
            placed += "$outcomeId $price $qty $tif $ttlMs"
            orders[id] = NovigOrder(id, clientId, "m", outcomeId, price, qty, qty, tif, "OPEN", SampleScan.NOW, ttlMs?.let { SampleScan.NOW + it })
            return id
        }

        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = orders.values.filter { it.status == status }
        override suspend fun order(orderId: String) = orders[orderId]
        override suspend fun fills(orderId: String?, limit: Int) = fillsBy[orderId].orEmpty()
        override suspend fun fillsStartingAfter(startsAfterMs: Long, limit: Int) = fillsBy.values.flatten()
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

        fun fillAll(orderId: String) {
            val o = orders.getValue(orderId)
            orders[orderId] = o.copy(remaining = 0, status = "FILLED")
            fillsBy[orderId] = listOf(NovigFill("f-$orderId", orderId, o.clientId, "m", o.outcomeId, o.qty, o.qty * o.price * 0.01, false, 0.0, SampleScan.NOW))
        }
    }

    /** The runner and its desk on the sample scan's clock (the container's own desk keeps the phone's: it only cancels here). */
    private fun runner(novig: FakeNovig) = MakerRunner(
        app, app.container, clock = { now }, scan = { ScanRun(result = SampleScan.result(), finished = 1) },
        desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { now }) },
    )

    @Test
    fun `the background cycle's pass is skipped when another ran in the last 15 seconds - two passes at once was a pass every 10 s`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        var t = now
        val runner = MakerRunner(
            app, app.container, clock = { t }, scan = { ScanRun(result = SampleScan.result(), finished = 1) },
            desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { t }) },
        )
        assertTrue(runner.run("during a scan") != null)
        t += 10_000
        assertNull(runner.run("background cycle", minGapMs = MakerRunner.BACKGROUND_GAP_MS))
        // A scan's own passes (and the Bids tab's) are never skipped.
        assertTrue(runner.run("during a scan") != null)
        t += MakerRunner.BACKGROUND_GAP_MS
        assertTrue(runner.run("background cycle", minGapMs = MakerRunner.BACKGROUND_GAP_MS) != null)
    }

    @Test
    fun `a pass posts post-only bids with an expiry, a fill is a maker bet with a notification, and switching bids off takes the rest down`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val r = runner(novig).run("test")!!
        assertTrue("placed ${r.placed}", r.placed >= 2)
        // Post-only, living only as long as their fair stays fresh: the sample's prices are 3 min old on games 50 h off (fresh 10 min) = 7 min.
        assertTrue(novig.placed.joinToString(), novig.placed.all { it.endsWith(" PO 420000") })
        // A taker fills the first bid.
        val first = novig.orders.values.minBy { it.orderId }
        novig.fillAll(first.orderId)
        val r2 = runner(novig).run("test")!!
        val bet = r2.fills.single()
        assertTrue(bet.maker)
        assertEquals(first.qty, bet.contracts)
        assertTrue(bet.evPercentAtBet!! >= 0.04 - 1e-9)
        assertEquals(bet.id, app.container.tracker.all().single { it.maker }.id)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val n = posted.single { it.extras.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Bid filled") == true }
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString().startsWith("Wallet"))
        // Auto-make off: the container takes the bids it posted down (the background cycle wouldn't move them any more).
        app.container.settingsStore.update { it.copy(maker = false) }
        withTimeout(10_000) {
            while (novig.orders.values.any { it.status == "OPEN" }) delay(50)
        }
        withTimeout(10_000) {
            while (app.container.makerDesk()!!.bids().any { it.active }) delay(50)
        }
        assertTrue(app.container.makerDesk()!!.bids().filter { it.status == MakerStatus.CANCELED }.all { it.why == "Auto-make switched off" })
    }

    @Test
    fun `fully automatic posts while a scan is still running, not only at its end (Tj, 2026-10-03 - "it didn't actually make any bids by itself")`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val running = MakerRunner(
            app, app.container, clock = { now },
            scan = { ScanRun(scanning = true, result = SampleScan.result().copy(freshSinceMs = now - 60_000), finished = 1) },
            desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { now }) },
        )
        val r = running.run("during a scan")!!
        assertTrue("placed ${r.placed}", r.placed >= 2)
        assertTrue(r.partial)
        assertEquals(r.placed, novig.placed.size)
        assertEquals("${r.placed} posted, scan running", com.tjshea.vigilant.app.ui.MakerText.passLine(r))
    }

    @Test
    fun `pausing takes every bid down too`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        runner(novig).run("test")
        assertTrue(novig.orders.values.any { it.status == "OPEN" })
        app.container.settingsStore.update { it.copy(paused = true) }
        withTimeout(10_000) {
            while (novig.orders.values.any { it.status == "OPEN" }) delay(50)
        }
        withTimeout(10_000) {
            while (app.container.makerDesk()!!.bids().any { it.active }) delay(50)
        }
        assertTrue(app.container.makerDesk()!!.bids().all { it.why == "Scanning is paused" || it.status != MakerStatus.CANCELED })
    }

    @Test
    fun `Diagnostics says what bids are set to and did`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val run = runner(novig)
        run.run("test")
        val bids = app.container.makerDesk()!!.bids()
        val state = SampleScan.fresh(SampleScan.settings.copy(maker = true))
        val text = Diagnostics.report(state, Diagnostics.Extras("t", 1, "d", makerBids = bids, maker = run.status.value), now)
        assertTrue(text, text.contains("Make orders / Bids (RESEARCH.md §70): ON · 4% under the fair"))
        assertTrue(text, text.contains("${bids.count { it.active }} resting"))
        // Why bids fill or don't (Tj, 2026-10-03: "not one of them was taken"): who posted them, how long they rested, whether they led their side.
        assertTrue(text, text.contains("bids posted ${bids.size} (auto-make ${bids.size}, by hand 0) · rested 0 min at the median"))
        assertTrue(text, text.contains("led their side (no bid as high)"))
        assertEquals(ScanSettings().maker, false)
    }

    @Test
    fun `the bids' line counts hand and auto bids, their lives, and the book they were posted against`() {
        val base = com.tjshea.vigilant.data.novig.trading.maker.MakerBid(
            clientId = "c", orderId = "o", marketId = "m", eventId = "e", outcomeId = "x", league = "NFL", eventName = "A @ B", startsTs = now + 3_600_000L,
            marketLabel = "Yards", selection = "P Over 50.5", price = 0.45, contracts = 100, fair = 0.47, evAtFair = 0.04, margin = 0.04, postedAtMs = now - 600_000,
        )
        val bids = listOf(
            base.copy(clientId = "a", auto = false, endedAtMs = now - 300_000, bestBidAtPost = 0.40, offerAtPost = 0.50, bookAtMs = now - 660_000),
            base.copy(clientId = "b", auto = false, endedAtMs = now - 540_000, bestBidAtPost = 0.46, offerAtPost = 0.48, bookAtMs = now - 900_000),
            base.copy(clientId = "c", auto = true, offerAtPost = 0.52, bookAtMs = now - 600_000),
        )
        assertEquals(
            "bids posted 3 (auto-make 1, by hand 2) · rested 5 min at the median, 5 at the 90th · filled 0 · led their side (no bid as high) 67% of 3 · " +
                "under Novig's price to take 5.0¢ at the median · priced with a book 1 min old at the median",
            MakerStats.line(bids, now),
        )
        assertNull(MakerStats.line(emptyList(), now))
    }

    @Test
    fun `with auto-make off it recommends a few new bids once each, Approve posts one after re-checking it, and the next pass leaves it up`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        app.container.settingsStore.update { it.copy(maker = false, makerRecommend = true) }
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        nm.allNotifications.forEach { }
        val run = runner(novig)
        assertEquals(null, run.run("test"))
        fun recommended() = nm.allNotifications.filter { it.extras.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Bid to approve") == true }
        val first = recommended()
        assertTrue("${first.size}", first.size in 1..MakerRunner.MAX_RECOMMENDED)
        assertEquals(listOf("Approve", "Deny"), first.first().actions.map { it.title.toString() })
        assertTrue(first.all { it.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString().startsWith("Wallet") })
        val sent = app.container.eventLog.counters()["maker.recommended"] ?: 0L
        run.run("test")
        assertEquals(first.size, recommended().size)
        // Each side once: the second pass recommends nothing new.
        assertEquals(sent, app.container.eventLog.counters()["maker.recommended"] ?: 0L)
        assertTrue(novig.placed.isEmpty())
        // Approve: the first recommended side is posted.
        val side = run.status.value.decisions.filterIsInstance<com.tjshea.vigilant.data.novig.trading.maker.MakerDecision.Post>().minBy { it.price }.line.outcomeId
        assertNull(run.post(side))
        assertEquals(1, novig.placed.size)
        // The next pass (auto-make off) keeps the approved bid up.
        val r = run.run("test")!!
        assertEquals(0, r.cancelled)
        assertEquals(0, r.placed)
        assertTrue(novig.orders.values.single().status == "OPEN")
    }

    @Test
    fun `recommendations are capped at six an hour`() = runBlocking {
        val rec = MakerRecommended(java.io.File.createTempFile("rec", ".json").also { it.delete() })
        rec.mark((1..6).map { "side-$it" to now + 3_600_000L }, now - 10 * 60_000)
        assertEquals(6, rec.since(now - 3_600_000L))
        assertEquals(0, rec.since(now - 60_000))
        assertEquals(setOf("side-7"), rec.unseen(listOf("side-1" to now + 1, "side-7" to now + 1), now))
    }

    @Test
    fun `Deny from the notification skips that side until its game, and Cancel by hand denies the side too`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        app.container.settingsStore.update { it.copy(maker = false) }
        app.container.makerDenials.all(now).forEach { app.container.makerDenials.undo(it.outcomeId) }
        val run = runner(novig)
        run.preview()
        val post = run.status.value.decisions.filterIsInstance<com.tjshea.vigilant.data.novig.trading.maker.MakerDecision.Post>().first()
        val intent = android.content.Intent(app, MakerActionReceiver::class.java).setAction(MakerNotes.ACTION_DENY)
            .putExtra(MakerNotes.EXTRA_OUTCOME, post.line.outcomeId).putExtra(MakerNotes.EXTRA_STARTS, post.line.startsTs).putExtra(MakerNotes.EXTRA_SELECTION, post.line.selection)
        MakerActionReceiver().onReceive(app, intent)
        withTimeout(10_000) { while (post.line.outcomeId !in app.container.makerDenials.outcomes(now)) delay(50) }
        val again = run.preview().first { it.line.outcomeId == post.line.outcomeId }
        assertEquals(com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.DENIED, (again as com.tjshea.vigilant.data.novig.trading.maker.MakerDecision.Skip).why)
        assertEquals(com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.DENIED, run.post(post.line.outcomeId))
        run.undoDeny(post.line.outcomeId)
        assertNull(run.post(post.line.outcomeId))
        val id = novig.orders.keys.single()
        assertNull(run.cancel(id))
        assertTrue(post.line.outcomeId in app.container.makerDenials.outcomes(now))
    }

    @Test
    fun `a phone restart switches auto-make off with auto-bet, and says so`() {
        val s = ScanSettings(maker = true, autoBet = true)
        assertTrue(!LaunchReset.apply(s).maker)
        assertEquals("Auto-bet and auto-make (Bids tab) are off after the phone restarted. Switch them on when you want them.", LaunchReset.note(s))
        assertEquals("Auto-make (Bids tab) is off after the phone restarted. Switch it on when you want it.", LaunchReset.note(ScanSettings(maker = true)))
    }
}
