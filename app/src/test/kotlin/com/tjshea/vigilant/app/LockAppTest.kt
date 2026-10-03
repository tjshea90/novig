package com.tjshea.vigilant.app

import android.Manifest
import android.app.NotificationManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LockCard
import com.tjshea.vigilant.app.ui.LockText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.LockResult
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Locks on Tj's own bets in the app (Tj, 2026-10-02 ~18:50Z: "it finds proper arbitrage opportunities based on the bets I already placed … include an option to
 * auto bet these bets"): the scanner reads only the markets the subaccount holds, auto-lock places what its rules allow and nothing else, and the
 * bet sheet's card offers it with a confirm. RESEARCH.md §67.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class LockAppTest {

    @get:Rule val compose = createComposeRule()

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = SampleScan.NOW
    private var startsTs = now + 3 * 3_600_000L

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings() }
        }
    }

    private fun market() = NovigMarket(
        "mkt", "ev", "MONEYLINE", "OPEN", "B @ A", startsTs, MarketFee.GAME, listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    /** B costs 1 − A's best bid: [bidA] 450 → B at 0.55. */
    private fun book(bidA: Int = 450) = NovigBook("mkt", 1, mapOf("A" to listOf(BidLevel(bidA, 5_000)), "B" to listOf(BidLevel(530, 5_000))), now)

    private class FakeTrading(var positions: List<NovigPosition>) : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val sent = ArrayList<String>()
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            sent += "$outcomeId $price $qty $tif"
            last = Triple(outcomeId, price, qty)
            return "o-${sent.size}"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "mkt", last!!.first, last!!.second, last!!.third, 0, "FOK", "FILLED", 1)
        override suspend fun fills(orderId: String?, limit: Int) = last?.let { (o, p, q) -> listOf(NovigFill("f-$orderId", orderId ?: "o", null, "mkt", o, q, q * p * 0.01, true, 0.0, SampleScan.NOW)) }.orEmpty()
        override suspend fun positions(marketId: String?) = positions
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = emptyList<NovigOrder>()
    }

    /** An API bet on Team A: 1,000 contracts for $4.00 (+150). */
    private suspend fun betA() {
        val target = BetTarget(market(), "A", "NBA", "B @ A", startsTs, "Moneyline", "Team A", fair = 0.42, fairAsOfMs = now, source = BetTracker.SOURCE_CNO)
        app.container.tracker.logApi(target, "o-first", listOf(NovigFill("f0", "o-first", null, "mkt", "A", 1_000, 4.0, true, 0.0, now - 60_000)))
    }

    private fun scanner(book: NovigBook? = book(), clock: () -> Long = { now }) =
        LockScanner(clock = clock, readMarket = { market() }, readBooks = { ids -> if (book != null && "mkt" in ids) mapOf("mkt" to book) else emptyMap() })

    @Test
    fun `a market's sides are read once for hours, however often the Tracker's Novig-only read asks (v0 54 0 file - 1,655 public catalog reads refused)`() = runBlocking {
        var t = now
        var reads = 0
        val s = LockScanner(clock = { t }, readMarket = { reads++; market() }, readBooks = { emptyMap() })
        repeat(50) { s.market("mkt"); t += 60_000L }
        assertEquals(1, reads)
        t += LockScanner.MARKET_TTL_MS
        s.market("mkt")
        assertEquals(2, reads)
        assertTrue(LockScanner.MARKET_TTL_MS >= 3_600_000L)
    }

    @Test
    fun `the scanner finds the lock on an API bet from Novig's book alone, and words it`() = runBlocking {
        betA()
        // A bet marked by hand (✓) is never a lock candidate: its contracts can't be confirmed.
        app.container.tracker.logCno(SampleCno.rows[1], 0.05, false, placedKey = "cno:x")
        var asked = listOf<String>()
        val views = LockScanner(clock = { now }, readMarket = { market() }, readBooks = { ids -> asked = ids; mapOf("mkt" to book()) }).scan(app.container.tracker.all())
        assertEquals(listOf("mkt"), asked)
        val v = views.getValue("mkt")
        val plan = (v.result as LockResult.Ready).plan
        assertEquals(0.50, plan.guaranteed, 1e-9)
        assertEquals("Team A", v.heldName)
        assertEquals("Team B", v.otherName)
        assertEquals("🔓 Lock +$0.50", LockText.badge(v))
        assertTrue(LockText.headline(v), LockText.headline(v).startsWith("Lock in at least +$0.50 whichever side wins: buy 1,000 contracts of Team B at -122"))
        // Letting it ride: +$6.00 if A wins, −$4.00 if not; worth about +$0.60 at Novig's middle price (A's bid 0.45, offer 0.47: 0.46 × $10 − $4).
        assertEquals(6.0 to -4.0, LockText.rideRange(v))
        assertEquals(0.60, v.holdValue!!, 1e-9)
        // The other side too dear: no lock, and it says the price needed.
        val none = scanner(book(bidA = 390)).scan(app.container.tracker.all()).getValue("mkt")
        assertTrue(LockText.headline(none), LockText.headline(none).contains("a lock needs"))
        assertNull(LockText.badge(none))
    }

    @Test
    fun `auto-lock places a lock that clears its minimum, once, and says so`() = runBlocking {
        betA()
        val trading = FakeTrading(listOf(NovigPosition("mkt", "A", 1_000, 4.0)))
        val placer = ApiBetPlacer(trading, app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        val s = ScanSettings(autoLock = true, autoScan = AutoScanMode.CNO, autoLockMinPercent = 0.10)
        // 10% of $4 = $0.40 minimum; the lock pays $0.50: placed.
        val locker = AutoLocker(app, app.container, clock = { now }, placer = { placer }, scanner = { scanner() })
        val placed = locker.run(s)
        assertEquals(listOf("B 0.55 1000 FOK"), trading.sent)
        assertEquals(1, placed.size)
        assertNotNull(placed.single().lockFor)
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.any { it.extras.getString("android.title")!!.startsWith("Auto-lock +$0.50") })
        // Locked now: the next cycle sends nothing.
        trading.positions = listOf(NovigPosition("mkt", "A", 1_000, 4.0), NovigPosition("mkt", "B", 1_000, 5.5))
        locker.run(s)
        assertEquals(1, trading.sent.size)
        assertEquals("🔒 Locked +$0.50", LockText.badge(scanner().scan(app.container.tracker.all()).getValue("mkt")))
    }

    @Test
    fun `auto-lock waits under its minimum, while off, and in-game when told not to`() = runBlocking {
        betA()
        val trading = FakeTrading(listOf(NovigPosition("mkt", "A", 1_000, 4.0)))
        val placer = ApiBetPlacer(trading, app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        val locker = AutoLocker(app, app.container, clock = { now }, placer = { placer }, scanner = { scanner() })
        // 15% of $4 = $0.60 minimum: the $0.50 lock waits.
        locker.run(ScanSettings(autoLock = true, autoScan = AutoScanMode.CNO, autoLockMinPercent = 0.15))
        // Off, or the background scan off.
        locker.run(ScanSettings(autoLock = false, autoScan = AutoScanMode.CNO))
        locker.run(ScanSettings(autoLock = true, autoScan = AutoScanMode.OFF))
        assertTrue(trading.sent.isEmpty())
        // In-game with "also during the game" off: nothing.
        startsTs = now - 60_000L
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        betA()
        val liveLocker = AutoLocker(app, app.container, clock = { now }, placer = { placer }, scanner = { scanner() })
        liveLocker.run(ScanSettings(autoLock = true, autoScan = AutoScanMode.CNO, autoLockMinPercent = 0.005, autoLockLive = false))
        assertTrue(trading.sent.isEmpty())
    }

    @Test
    fun `the bet sheet's card asks before locking and hands over the profit confirmed`() {
        val v = runBlocking { betA(); scanner().scan(app.container.tracker.all()).getValue("mkt") }
        val asked = ArrayList<Pair<String, Double>>()
        compose.setContent {
            VigilantTheme(darkTheme = true) { Surface(color = MaterialTheme.colorScheme.background) { Column { LockCard(v, locking = false) { m, g -> asked += m to g } } } }
        }
        compose.onRoot().captureRoboImage("screenshots/4l_lock_card.png")
        compose.onNodeWithTag("lockButton").performClick()
        assertTrue(asked.isEmpty())
        compose.onNodeWithText("If the price moves first, nothing is bought", substring = true).assertExists()
        compose.onNodeWithTag("lockConfirm").performClick()
        assertEquals(listOf("mkt" to 0.50), asked.map { it.first to Math.round(it.second * 100) / 100.0 })
    }
}
