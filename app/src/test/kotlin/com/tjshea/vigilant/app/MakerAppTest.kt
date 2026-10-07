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
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanRun
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        /** The Vigilant wallet's balance on Novig now; a test moves it as a bet by hand or an auto-bet would. */
        @Volatile var walletDollars = 100.0

        override suspend fun balance(subaccountKeyId: String) = walletDollars

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

    /** RESEARCH.md §72: game-line bids get the trap guard's move rule; game lines are off for bids by default, so nothing is read by default. */
    @Test
    fun `a pass reads Novig's trades only for game-line bids - none by default, a few markets with game lines on, and a failed read stops nothing`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        fun runner(fail: Boolean) = MakerRunner(
            app, app.container, clock = { now }, scan = { ScanRun(result = SampleScan.result(), finished = 1) },
            desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { now }) },
            recentTrades = { id -> asked += id; if (fail) throw java.io.IOException("429") else emptyList() },
        )
        assertTrue(runner(fail = false).run("default kinds") != null)
        assertTrue("game lines are off for bids by default: nothing read ($asked)", asked.isEmpty())
        // Game lines on (the sample's moneylines have Pinnacle in the fair): their markets are read, a few a pass, and the bids still go up when every read fails.
        app.container.makerStore.update { emptyList() }
        app.container.settingsStore.update { it.copy(makerKinds = it.makerKinds + BetKind.MONEYLINE, trapEarlyHours = 0) }
        val r = runner(fail = true).run("game lines on")!!
        assertTrue("read $asked", asked.isNotEmpty() && asked.size <= MakerRunner.MAX_MOVE_READS)
        assertTrue(r.problems.toString(), r.placed > 0)
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
        app.container.settingsStore.update { it.copy(pausedByHand = true) }
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

    /** Tj, 2026-10-05: longest odds for bids, and a bid held back from trading with one of its own: Diagnostics says what the rule is and what a pass held back. */
    @Test
    fun `Diagnostics says the longest odds a bid may be posted at and what the last pass held back`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val run = runner(novig)
        run.run("test")
        val limited = SampleScan.settings.copy(maker = true, makerMaxOdds = 140)
        val status = run.status.value.let { st -> st.copy(lastReport = st.lastReport!!.copy(waiting = mapOf(com.tjshea.vigilant.data.novig.trading.maker.MakerPlan.WASH to 2))) }
        val text = Diagnostics.report(SampleScan.fresh(limited), Diagnostics.Extras("t", 1, "d", makerBids = app.container.makerDesk()!!.bids(), maker = status), now)
        assertTrue(text, text.contains("no bid longer than +140"))
        assertTrue(text, text.contains("held back: 2 because it would trade with your own bid on the other side of that market"))
        val free = Diagnostics.report(SampleScan.fresh(SampleScan.settings.copy(maker = true)), Diagnostics.Extras("t", 1, "d", makerBids = emptyList(), maker = run.status.value), now)
        assertFalse(free, free.contains("no bid longer than"))
    }

    /** Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked in the scan/diagnosis feature and all information logged so I can see how well my auto bids do". */
    @Test
    fun `Diagnostics lists the newest fills with how fast they were taken and whether they were picked off, and what the guard says`() {
        fun bid(n: Int, delayMs: Long, fairAfter: Double) = com.tjshea.vigilant.data.novig.trading.maker.MakerBid(
            clientId = "c$n", orderId = "o$n", marketId = "m$n", eventId = "e$n", outcomeId = "x$n", league = "NFL", eventName = "A @ B", startsTs = now + 3_600_000L,
            marketLabel = "Receiving Yards", selection = "Player $n Over 50.5", kind = BetKind.PROP, price = 0.45, contracts = 1_000, fair = 0.468, evAtFair = 0.04, margin = 0.04, books = 4,
            postedAtMs = now - 20 * 60_000L, status = MakerStatus.FILLED, filled = 1_000, paid = 4.5, endedAtMs = now - 10 * 60_000L,
            bestBidAtPost = 0.44, offerAtPost = 0.50, bookAtMs = now - 22 * 60_000L, blendFair = 0.48, sharpFairAtPost = 0.468,
            firstFillAtMs = now - 20 * 60_000L + delayMs, fairAtFill = fairAfter, sharpFairAtFill = null,
        )
        val bids = (1..8).map { bid(it, delayMs = 20_000L * it, fairAfter = if (it <= 6) 0.43 else 0.50) }
        val state = SampleScan.fresh(SampleScan.settings.copy(maker = true))
        val text = Diagnostics.report(state, Diagnostics.Extras("t", 1, "d", makerBids = bids, maker = MakerRunner.Status()), now)
        assertTrue(text, text.contains("picked-off guard: on · price under the sharp book's fair: on · focus: All bids · most bids 20"))
        assertTrue(text, text.contains("6 of the last 8 fills were picked off") && text.contains("(would stop the bids)"))
        assertTrue(text, text.contains("ALL FILLS: 8 fills"))
        assertTrue(text, text.contains("-- fills by how fast they were taken --") && text.contains("-- fills by bid price (about the chance the side wins) --"))
        assertTrue(text, text.contains("the newest fills (when"))
        assertTrue(text, text.lines().count { it.contains("PICKED OFF") } == 6)
        // The health checks say it too: picked off, and most fills within two minutes of posting.
        val checks = HealthChecks.of(state, Diagnostics.Extras("t", 1, "d", makerBids = bids, maker = MakerRunner.Status()), now).filter { it.area == "Bids" }
        assertTrue(checks.toString(), checks.any { it.finding == "fills are being picked off" && it.level == HealthChecks.Level.WARN })
        // Quick fills alone are not a fault (Tj's v0.70.1 file: 3 of 6 fired a warning against its own split): with no close to judge them by it is information, not a warning.
        val quick = checks.single { it.finding == "most fills came within 2 minutes of posting" }
        assertEquals(quick.toString(), HealthChecks.Level.OK, quick.level)
        assertTrue(quick.toString(), quick.evidence!!.contains("normal while a bid rests only a few minutes"))
        // And once the guard has stopped them, that is the first thing it says.
        val haltedChecks = HealthChecks.of(state.copy(settings = state.settings.copy(makerHalted = "6 of the last 8 fills were picked off")), Diagnostics.Extras("t", 1, "d", makerBids = bids, maker = MakerRunner.Status()), now).filter { it.area == "Bids" }
        assertTrue(haltedChecks.toString(), haltedChecks.any { it.finding == "stopped by the picked-off guard" })
        // The guard having stopped the bids is said in capitals, with its words.
        val stopped = Diagnostics.report(state.copy(settings = state.settings.copy(makerHalted = "6 of the last 8 fills were picked off")), Diagnostics.Extras("t", 1, "d", makerBids = bids, maker = MakerRunner.Status()), now)
        assertTrue(stopped, stopped.contains("BIDS STOPPED BY THE GUARD: 6 of the last 8 fills were picked off"))
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

    /**
     * Tj, 2026-10-03 ("The bids are still not getting filled, how long do they usually take to get filled?"): the file's 14-day line mixed a month of
     * versions (320 bids, mostly one-minute re-posts). The last 24 hours says how many bid-hours were really up and what the research expects from them.
     */
    @Test
    fun `the last 24 hours of bids are counted in bid-hours against the fills the research expects`() {
        val base = com.tjshea.vigilant.data.novig.trading.maker.MakerBid(
            clientId = "c", orderId = "o", marketId = "m", eventId = "e", outcomeId = "x", league = "NFL", eventName = "A @ B", startsTs = now + 3_600_000L,
            marketLabel = "Yards", selection = "P Over 50.5", price = 0.45, contracts = 100, fair = 0.47, evAtFair = 0.04, margin = 0.04, postedAtMs = now - 600_000,
        )
        val min = 60_000L
        // 26 bids resting ~20 minutes each (about 8.7 bid-hours): too few to expect much; none filled.
        val few = (1..26).map { i ->
            base.copy(clientId = "c$i", postedAtMs = now - 30 * min, endedAtMs = now - 10 * min, status = MakerStatus.CANCELED, why = "The fair price goes old")
        }
        val lines = MakerStats.recent(few, now)
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("last 24 h: 26 bids posted, 8.7 bid-hours up (longest 20 min) · filled 0 · "))
        assertTrue(lines[0], lines[0].contains("too few bid-hours to judge"))
        assertEquals("last 24 h ended: ×26 Cancelled: The fair price goes old", lines[1])
        // A day of hour-long bids with none filled is well under the research's rate.
        val many = (1..60).map { i -> base.copy(clientId = "m$i", postedAtMs = now - 4 * 3_600_000L, endedAtMs = now - 3 * 3_600_000L, status = MakerStatus.EXPIRED) }
        assertTrue(MakerStats.recent(many, now)[0], MakerStats.recent(many, now)[0].contains("well under the research's rate"))
        // With fills in line: no complaint. Older bids and bids with no order don't count.
        val fine = many.mapIndexed { i, b -> if (i < 10) b.copy(filled = 100, status = MakerStatus.FILLED) else b } +
            base.copy(clientId = "old", postedAtMs = now - 3 * 24 * 3_600_000L, endedAtMs = now - 3 * 24 * 3_600_000L + min) + base.copy(clientId = "no", orderId = null)
        assertTrue(MakerStats.recent(fine, now)[0], MakerStats.recent(fine, now)[0].startsWith("last 24 h: 60 bids posted, 60.0 bid-hours up"))
        assertTrue(MakerStats.recent(fine, now)[0].contains("in line with the research"))
        assertTrue(MakerStats.recent(emptyList(), now).isEmpty())
        // The yardstick: the research's table, straight between its rows.
        assertEquals(0.04, MakerStats.fillChance(0.25), 1e-9)
        assertEquals(0.11, MakerStats.fillChance(1.0), 1e-9)
        assertEquals(0.18, MakerStats.fillChance(2.0), 1e-9)
        assertEquals(0.37, MakerStats.fillChance(6.0), 1e-9)
        assertEquals(0.40, MakerStats.fillChance(100.0), 1e-9)
        assertEquals(0.0, MakerStats.fillChance(0.0), 1e-9)
    }

    @Test
    fun `a bid that would take its game past the per-game limit is not recommended, and is once the limit allows it`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        // A one-cent limit on a game: every bid is over it (Tj, 2026-10-04: one event is one risk).
        app.container.settingsStore.update { it.copy(maker = false, makerRecommend = true, apiMaxPerGame = 0.01) }
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val run = runner(novig)
        run.run("test")
        fun recommended() = nm.allNotifications.filter { it.extras.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Bid to approve") == true }
        assertTrue("nothing is suggested past the limit", recommended().isEmpty())
        assertEquals(0L, app.container.eventLog.counters()["maker.recommended"] ?: 0L)
        // Not marked as seen: with the limit lifted the same sides are recommended on the next pass.
        app.container.settingsStore.update { it.copy(apiMaxPerGame = 0.0) }
        run.run("test")
        assertTrue(recommended().isNotEmpty())
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

    // ---- the wallet kept ahead of the bids (Tj, 2026-10-04) ---------------------------------------------------------------

    private suspend fun upDollars() = app.container.makerDesk()!!.bids().filter { it.active }.sumOf { it.restingDollars }

    @Test
    fun `a balance reading under the bids up takes the extra bids down on its own - the wallet watch`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        val r = runner(novig).run("test")!!
        assertTrue("placed ${r.placed}", r.placed >= 2)
        val total = upDollars()
        val low = total / 2
        // A bet by hand or an auto-bet takes the wallet under what's up (Novig holds nothing for a resting bid): its answer is recorded like every reading.
        novig.walletDollars = low
        app.container.wallet.record(low)
        withTimeout(30_000) { while (upDollars() > low + 0.005) delay(50) }
        val open = novig.orders.values.filter { it.status == "OPEN" }.sumOf { it.remaining * it.price * 0.01 }
        assertTrue("$open on Novig for a wallet of $low", open <= low + 0.005)
        val taken = app.container.makerDesk()!!.bids().filter { it.status == MakerStatus.CANCELED }
        assertTrue("taken down: ${taken.map { it.why }}", taken.isNotEmpty() && taken.all { it.why == com.tjshea.vigilant.data.novig.trading.maker.MakerPlan.TRIMMED })
        // The count is written when the pass that took them down returns, a moment after the last cancel is on record: wait for what is asserted, not race it
        // (the full floor failed once on this line with no message; a 400 ms pause before the count reproduces it).
        withTimeout(10_000) { while ((app.container.eventLog.counters()["maker.trimmed"] ?: 0L) < taken.size) delay(50) }
        assertTrue("trimmed ${app.container.eventLog.counters()["maker.trimmed"]} for ${taken.size} taken down", (app.container.eventLog.counters()["maker.trimmed"] ?: 0L) >= taken.size)
        // Money back in the wallet: nothing more comes down, whatever the reading.
        val left = upDollars()
        novig.walletDollars = 100.0
        app.container.wallet.record(100.0)
        delay(300)
        assertEquals(left, upDollars(), 1e-9)
    }

    @Test
    fun `a bid is never taken down while the wallet covers the bids up, whatever auto-make is set to`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        runner(novig).run("test")
        val total = upDollars()
        app.container.settingsStore.update { it.copy(maker = false) }
        withTimeout(10_000) { while (app.container.makerDesk()!!.bids().any { it.active && it.auto }) delay(50) }
        // Auto-make off took its own bids down; what's left (none) fits. A reading that covers what's up changes nothing.
        novig.walletDollars = total + 1.0
        app.container.wallet.record(total + 1.0)
        delay(300)
        assertTrue(novig.orders.values.none { it.status == "OPEN" })
        assertEquals(0, (app.container.eventLog.counters()["maker.trimmed"] ?: 0L).toInt())
    }

    @Test
    fun `with no scan yet in this process a pass still takes down the bids the wallet can't cover`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        runner(novig).run("test")
        val low = upDollars() / 2
        novig.walletDollars = low
        app.container.wallet.record(low)
        val noScan = MakerRunner(
            app, app.container, clock = { now }, scan = { ScanRun() },
            desk = { com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(novig, app.container.tracker, app.container.makerStore, lock = app.container.orderLock, clock = { now }) },
        )
        assertNull(noScan.run("background cycle"))
        withTimeout(30_000) { while (upDollars() > low + 0.005) delay(50) }
    }

    @Test
    fun `Approve holds a bid to the wallet beside the bids already up, not to the whole wallet`() = runBlocking {
        val novig = FakeNovig()
        app.container.installTradingForTest(novig, "sub-1")
        app.container.settingsStore.update { it.copy(maker = false, makerRecommend = true) }
        val run = runner(novig)
        assertNull(run.run("test"))
        val posts = run.status.value.decisions.filterIsInstance<com.tjshea.vigilant.data.novig.trading.maker.MakerDecision.Post>().sortedBy { it.price }
        assertTrue("${posts.size} bids on offer", posts.size >= 2)
        // The wallet holds either bid alone, not both.
        novig.walletDollars = maxOf(posts[0].cost, posts[1].cost) + 0.01
        assertNull(run.post(posts[0].line.outcomeId))
        val why = run.post(posts[1].line.outcomeId)
        assertTrue(why.toString(), why != null && why.contains("wallet") && why.contains("already up"))
        assertEquals(1, novig.placed.size)
    }

    @Test
    fun `wiring - the container watches the wallet and the bids, the no-scan pass and Approve use the wallet and the day's limit`() {
        fun source(path: String) = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()
        val container = source("VigilantApp.kt")
        assertTrue(container.contains("combine(wallet.flow, makerStore.flow)"))
        assertTrue(container.contains("MakerRunner.overWallet("))
        assertTrue(container.contains("maker.fitToWallet(\"wallet check\")"))
        val runner = source("MakerRunner.kt")
        assertTrue(runner.contains("desk.fit(rules, s.apiMaxPerDay, wallet)"))
        assertTrue(runner.contains("desk.fit(MakerRules.of(s), s.apiMaxPerDay, wallet)"))
        assertTrue(runner.contains("desk.post(d, rules, wallet = wallet, maxPerDay = s.apiMaxPerDay)"))
        // The pure check the watch asks: more than half a cent over, and a resting bid to take down.
        val bid = com.tjshea.vigilant.data.novig.trading.maker.MakerBid(
            clientId = "c", orderId = "o", marketId = "m", eventId = "e", outcomeId = "x", league = "NFL", eventName = "A @ B", startsTs = now + 3_600_000L,
            marketLabel = "Yards", selection = "P Over 50.5", price = 0.50, contracts = 1_000, fair = 0.52, evAtFair = 0.04, margin = 0.04, postedAtMs = now - 60_000,
            status = MakerStatus.RESTING,
        )
        val reading = WalletBalance.Reading(4.99, now)
        assertTrue(MakerRunner.overWallet(listOf(bid), reading))
        assertTrue(!MakerRunner.overWallet(listOf(bid), reading.copy(dollars = 5.0)))
        assertTrue(!MakerRunner.overWallet(listOf(bid), null))
        assertTrue(!MakerRunner.overWallet(emptyList(), reading))
        // Only bids already coming down: nothing resting to take down, so no request.
        assertTrue(!MakerRunner.overWallet(listOf(bid.copy(status = MakerStatus.CANCELING)), reading))
        assertTrue(!MakerRunner.overWallet(listOf(bid.copy(status = MakerStatus.CANCELED)), reading))
    }
}
