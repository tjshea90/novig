package com.tjshea.vigilant.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * The auto-bet on a real app container with a fake Novig (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all"). REAL
 * MONEY, so these pin what must never happen as much as what must: a bet outside the criteria, a live game, a wallet that can't cover it, a
 * bet whose Novig outcome isn't the one priced, the same bet twice, or an order whose answer was lost being re-sent.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoBettorTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = SampleScan.NOW
    private val jefferson = SampleCno.rows[1]

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings() }
        }
    }

    // ---- a fake Novig: fills at the plan's price, counts orders, can lose an answer ------------------------------

    private class FakeNovig(val orders: AtomicInteger = AtomicInteger(), val loseAnswer: Boolean = false) :
        NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }), Json { ignoreUnknownKeys = true }) {
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
            orders.incrementAndGet()
            last = Triple(outcomeId, price, qty)
            if (loseAnswer) throw java.io.IOException("connection reset")
            return "order-${orders.get()}"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "", last!!.first, last!!.second, last!!.third, 0, "IOC", "FILLED", 1)
        // Fills at the limit price the order was sent at: a dollar figure the tests can compute.
        override suspend fun fills(orderId: String?, limit: Int) = last?.let { (outcome, price, qty) ->
            listOf(NovigFill("f-$orderId", orderId ?: "o", null, "mkt", outcome, qty, qty * price * 0.01, true, 0.0, 1_790_400_000_000L))
        }.orEmpty()
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = emptyList<NovigOrder>()
    }

    /** A Novig market for [row]: its outcome "out-jj" (the bet) and "out-other"; the bet's ask is 1 − the other side's best bid. */
    private val market = NovigMarket("mkt", "ev", "TOTAL", "OPEN", "Player Receiving Yards", now + 86_400_000L, MarketFee.GAME, listOf(NovigOutcome("out-jj", "Under 69.5", "TBD"), NovigOutcome("out-other", "Over 69.5", "TBD")))

    /** The bet's ask at +117 is 0.4608; a bid of 0.539 on the other side makes it 0.461 here. */
    private fun book(bid: Int = 539, contracts: Long = 5_000) = NovigBook("mkt", 1, mapOf("out-other" to listOf(BidLevel(bid, contracts)), "out-jj" to listOf(BidLevel(400, 50))), now)

    private fun targetOf(row: CnoRow) = BetTarget(
        market = market, outcomeId = "out-jj", league = row.league, eventName = row.event, startsTs = row.startsAtMs!!, marketLabel = row.market, selection = row.bet,
        fair = CnoChecks.fairProbability(row)!!, fairAsOfMs = now - 20_000L, source = BetTracker.SOURCE_CNO, placedKey = MiniWindow.cnoKey(row),
        book = row.book, gameUrl = row.gameUrl, betUrl = row.betUrl,
    )

    private fun placer(novig: FakeNovig, book: NovigBook? = book(), limits: BetLimits = BetLimits(10.0, 50.0, 0.01)) =
        ApiBetPlacer(novig, app.container.tracker, books = { book }, limits = { limits }, clock = { now }, pause = { }, lock = app.container.orderLock)

    private fun bettor(
        novig: FakeNovig,
        wallet: Double? = 25.0,
        placer: ApiBetPlacer = placer(novig),
        resolve: suspend (CnoRow) -> BetTarget? = { targetOf(it) },
    ) = AutoBettor(app, app.container, clock = { now }, placer = { placer }, wallet = { wallet }, resolve = resolve)

    /** On, CNO scanning in the background, 3 books agreeing, 3% edge, 2 books both sides, $1 a bet. */
    private fun settings(f: (ScanSettings) -> ScanSettings = { it }) =
        f(ScanSettings(autoBet = true, autoScan = AutoScanMode.CNO, autoBetBooks = 3, autoBetMinEv = 0.03, autoBetTwoSided = 2, autoBetStake = AutoBetStake.ONE_DOLLAR, autoBetMaxStake = 10.0, apiMaxPerDay = 50.0))

    /** Jefferson Under 69.5 (+117, 5.8% EV, 3 of 3 books agree) with Novig's price read 5 s ago, as a background cycle leaves it. */
    private fun state(s: ScanSettings = settings(), row: CnoRow = jefferson, live: LivePrice? = LivePrice(117, 88.0, 0.0584, now - 5_000, "mkt", "out-jj")): UiState {
        val base = SampleCno.withBooks(SampleCno.state(SampleScan.state().copy(settings = s.copy(cnoLivePrices = true))))
        return base.copy(novigLive = live?.let { mapOf(row.key to it) } ?: emptyMap()).indexed(now)
    }

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    // ---- a bet that passes everything ---------------------------------------------------------------------------

    @Test
    fun `a bet that passes every criterion is placed, tracked like a Bet-sheet bet, hidden from the lists and announced`() = runBlocking {
        val novig = FakeNovig()
        val report = bettor(novig).run(settings(), state())
        assertEquals(1, novig.orders.get())
        assertEquals(1, report.placed.size)
        // Tracked from the fills, exactly as a Bet-sheet bet: its order, contracts, real cost, the CNO source and the list key.
        val bet = app.container.tracker.all().single()
        assertEquals(report.placed.single(), bet)
        assertTrue(bet.viaApi && bet.auto)
        assertEquals(BetTracker.SOURCE_CNO, bet.source)
        assertEquals(MiniWindow.cnoKey(jefferson), bet.placedKey)
        assertEquals("out-jj", bet.outcomeId)
        assertTrue(bet.gradeNote!!.startsWith("Auto-bet through Novig's API"))
        assertTrue("a \$1 stake buys about \$1 of contracts", bet.stake in 0.9..1.0)
        // Placed like a ✓: out of the lists.
        assertTrue(app.container.placed.load().bets.any { it.key == MiniWindow.cnoKey(jefferson) })
        // A notification for the bet.
        val note = notifications().single { it.extras.getString("android.title")!!.startsWith("Auto-bet") }
        assertTrue(note.extras.getString("android.title")!!.contains("Justin Jefferson Under 69.5"))
        assertTrue(note.extras.getString("android.text")!!.contains("3 of 3 books agree"))
    }

    @Test
    fun `the same bet is never placed twice - once it is in the Tracker the next cycle leaves it alone`() = runBlocking {
        val novig = FakeNovig()
        val b = bettor(novig)
        assertEquals(1, b.run(settings(), state()).placed.size)
        // The next cycle's state includes the Tracker's bets and the placed marks, as the cycle's own snapshot does.
        val after = state().copy(bets = app.container.tracker.all(), placed = app.container.placed.load().bets).indexed(now)
        val second = b.run(settings(), after)
        assertEquals(0, second.placed.size)
        assertEquals(1, novig.orders.get())
    }

    @Test
    fun `only one bet per Novig market while one is open, so the other side of a line is never bet after the first`() = runBlocking {
        val novig = FakeNovig()
        // An open bet of Tj's in this market (the other outcome), not in the lists' placed index by key or outcome.
        val open = app.container.tracker.logCno(jefferson.copy(bet = "Justin Jefferson Over 69.5", gameUrl = "https://crazyninjaodds.com/site/browse/game.aspx?side_id=77"), 0.03, false, "cno:other", marketId = "mkt", outcomeId = "out-other")
        assertEquals("mkt", open.marketId)
        val report = bettor(novig).run(settings(), state())
        assertEquals(0, report.placed.size)
        assertEquals(0, novig.orders.get())
        assertTrue(report.skipped.keys.toString(), report.skipped.keys.any { it.contains("already open") })
    }

    // ---- the criteria -------------------------------------------------------------------------------------------

    @Test
    fun `a bet outside Tj's criteria is not placed, and the report says which one`() = runBlocking {
        val novig = FakeNovig()
        fun reasons(s: ScanSettings) = runBlocking { bettor(novig).run(s, state(s)).skipped.keys.joinToString() }
        assertTrue(reasons(settings { it.copy(autoBetBooks = 5) }).contains("3 books say +EV on their own (you need 5)"))
        assertTrue(reasons(settings { it.copy(autoBetMinEv = 0.06) }).contains("under your +6.00% minimum"))
        assertEquals("nothing reached Novig", 0, novig.orders.get())
        // The same bet with criteria it meets is placed (3 books, a 5.84% edge against 5.0%).
        assertEquals(1, bettor(novig).run(settings { it.copy(autoBetMinEv = 0.05, autoBetBooks = 3, autoBetTwoSided = 3) }, state()).placed.size)
    }

    @Test
    fun `only pregame bets at Novig, on a Novig price read in the last minute`() = runBlocking {
        val novig = FakeNovig()
        // A game that started (live betting isn't available).
        val started = jefferson.copy(startsAtMs = now - 10 * 60_000L)
        val r1 = bettor(novig).run(settings(), state(row = started).let { it.copy(cno = it.cno.copy(snapshot = it.cno.snapshot!!.copy(rows = listOf(started)))).indexed(now) })
        assertEquals(0, r1.placed.size)
        // A game starting in 30 seconds: Novig is about to take it live.
        val soon = jefferson.copy(startsAtMs = now + 30_000L)
        val r2 = bettor(novig).run(settings(), state(row = soon).let { it.copy(cno = it.cno.copy(snapshot = it.cno.snapshot!!.copy(rows = listOf(soon)))).indexed(now) })
        assertEquals(0, r2.placed.size)
        // No Novig price read in the last minute: CNO's listed price may be old.
        val r3 = bettor(novig).run(settings(), state(live = LivePrice(117, 88.0, 0.0584, now - 5 * 60_000L, "mkt", "out-jj")))
        assertEquals(0, r3.placed.size)
        assertTrue(r3.skipped.keys.toString(), r3.skipped.keys.any { it.contains("no Novig price") })
        assertEquals(0, novig.orders.get())
    }

    // ---- the stake ----------------------------------------------------------------------------------------------

    @Test
    fun `Kelly stakes follow the bet's odds, are held to the per-bet maximum, and bet no more than the order book fills`() = runBlocking {
        // 1/8 Kelly of $1,000: fair 0.4878 at +117 (price 0.46083): (0.4878 - 0.46083) / (1 - 0.46083) = 0.050; x 0.125 x 1000 = $6.25.
        val s = settings { it.copy(autoBetStake = AutoBetStake.EIGHTH_KELLY, bankroll = 1000.0, autoBetMaxStake = 100.0) }
        val novig = FakeNovig()
        bettor(novig, placer = placer(novig, limits = BetLimits(100.0, 500.0, 0.01))).run(s, state(s))
        val cost = novig.last!!.let { (_, price, qty) -> qty * price * 0.01 }
        assertTrue("about \$6.25 of contracts, never more: $cost", cost in 6.0..6.25)
        // 1/2 Kelly would be $25: Tj's maximum of $10 holds it.
        val capped = settings { it.copy(autoBetStake = AutoBetStake.HALF_KELLY, bankroll = 1000.0, autoBetMaxStake = 10.0) }
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        bettor(novig2).run(capped, state(capped))
        assertTrue(novig2.last!!.let { (_, price, qty) -> qty * price * 0.01 } in 9.5..10.0)
    }

    @Test
    fun `a typed amount is the stake`() = runBlocking {
        val s = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 7.25) }
        val novig = FakeNovig()
        bettor(novig).run(s, state(s))
        assertTrue(novig.last!!.let { (_, price, qty) -> qty * price * 0.01 } in 7.0..7.25)
    }

    // ---- the wallet ---------------------------------------------------------------------------------------------

    @Test
    fun `the stake is held to what's left in the wallet, and under a dollar nothing is placed and Tj is told once`() = runBlocking {
        val s = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 7.25) }
        val novig = FakeNovig()
        // $2.40 left: the bet is $2.40, not $7.25.
        bettor(novig, wallet = 2.40).run(s, state(s))
        assertTrue(novig.last!!.let { (_, price, qty) -> qty * price * 0.01 } in 2.2..2.40)
        // 60 cents left: no bet, said so.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        val b = bettor(novig2, wallet = 0.60)
        val r = b.run(s, state(s))
        assertTrue(r.walletEmpty)
        assertEquals(0, novig2.orders.get())
        assertTrue(r.stopped!!, r.stopped!!.contains("under the \$1.00 minimum"))
        // Told once, however many cycles find it empty.
        b.run(s, state(s))
        b.run(s, state(s))
        assertEquals(1, notifications().count { it.extras.getString("android.title") == "Wallet empty" })
    }

    // ---- never the wrong bet ------------------------------------------------------------------------------------

    @Test
    fun `a bet whose Novig outcome isn't the one priced, or whose book price isn't the price judged, sends nothing`() = runBlocking {
        val novig = FakeNovig()
        // The catalog found another outcome than the one Novig's price was read from.
        val r1 = bettor(novig, resolve = { targetOf(it).copy(outcomeId = "out-other") }).run(settings(), state())
        assertEquals(0, r1.placed.size)
        assertTrue(r1.skipped.keys.toString(), r1.skipped.keys.any { it.contains("different outcomes") })
        // The book offers the outcome at 0.60, nothing like the +117 (0.461) it was judged at: the placer refuses to send.
        val novig2 = FakeNovig()
        val r2 = bettor(novig2, placer = placer(novig2, book = book(bid = 400))).run(settings(), state())
        assertEquals(0, r2.placed.size)
        assertEquals(0, novig2.orders.get())
        assertTrue(r2.skipped.keys.toString(), r2.skipped.keys.any { it.contains("isn't the price it was judged at") })
        // A bet that can't be found on Novig for certain isn't bet either.
        val novig3 = FakeNovig()
        val r3 = bettor(novig3, resolve = { null }).run(settings(), state())
        assertEquals(0, novig3.orders.get())
        assertTrue(r3.skipped.keys.any { it.contains("couldn't be found") })
    }

    @Test
    fun `a bet that was refused isn't tried again at once`() = runBlocking {
        val novig = FakeNovig()
        val b = bettor(novig, placer = placer(novig, book = book(bid = 400)))
        b.run(settings(), state())
        val again = b.run(settings(), state())
        assertTrue(again.skipped.keys.toString(), again.skipped.keys.any { it.contains("tried a moment ago") })
    }

    // ---- limits and stops ---------------------------------------------------------------------------------------

    @Test
    fun `the daily limit for API bets stops it, with a note`() = runBlocking {
        val s = settings { it.copy(apiMaxPerDay = 0.5) }
        val novig = FakeNovig()
        val r = bettor(novig).run(s, state(s))
        assertEquals(0, novig.orders.get())
        assertTrue(r.stopped!!, r.stopped!!.contains("daily limit"))
        assertTrue(notifications().any { it.extras.getString("android.title") == "Auto-bet paused" })
    }

    @Test
    fun `an order whose answer was lost halts auto-bet for good until Tj resumes it, and is never sent again`() = runBlocking {
        val novig = FakeNovig(loseAnswer = true)
        val b = bettor(novig)
        val r = b.run(settings(), state())
        assertTrue(r.halted)
        assertEquals(1, novig.orders.get())
        assertEquals(0, app.container.tracker.all().size)
        // Saved: the background cycle sees it (autoBetsNow is off) and so does the next run even if something calls it anyway.
        val saved = app.container.settingsStore.read()
        assertNotNull(saved.autoBetHalted)
        assertFalse(settings { it.copy(autoBetHalted = saved.autoBetHalted) }.autoBetsNow)
        val second = b.run(settings { it.copy(autoBetHalted = saved.autoBetHalted) }, state())
        assertEquals(0, second.placed.size)
        assertEquals("the lost order is not re-sent", 1, novig.orders.get())
        assertTrue(notifications().any { it.extras.getString("android.title") == "Auto-bet stopped" })
    }

    @Test
    fun `off, or betting not set up, places nothing`() = runBlocking {
        val novig = FakeNovig()
        assertEquals(0, bettor(novig).run(settings { it.copy(autoBet = false) }, state()).placed.size)
        val none = AutoBettor(app, app.container, clock = { now }, placer = { null }, wallet = { null }, resolve = { null })
        assertEquals(0, none.run(settings(), state()).placed.size)
        assertTrue(none.status.value.blocker!!.contains("isn't set up"))
        assertEquals(0, novig.orders.get())
    }

    @Test
    fun `a bet already placed with a check mark isn't bet again`() = runBlocking {
        val novig = FakeNovig()
        val placed = state().copy(placed = listOf(com.tjshea.vigilant.data.tracker.PlacedBet(MiniWindow.cnoKey(jefferson), jefferson.bet, placedAtMs = now - 60_000, startsAtMs = jefferson.startsAtMs))).indexed(now)
        assertEquals(0, bettor(novig).run(settings(), placed).placed.size)
        assertEquals(0, novig.orders.get())
    }

    // ---- the wiring ---------------------------------------------------------------------------------------------

    /** Source pins for what needs a live cycle: the order of things in it, and that the Bet sheet and the auto-bet share one lock. */
    @Test
    fun `a cycle reads at the lowest threshold, bets before it alerts, and the two placers share one order lock`() {
        val cycle = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        // Novig's own price now whatever the live-price setting says, only when auto-bet will run.
        assertTrue(cycle.contains("val s = if (settings.autoBetsNow) settings.copy(cnoLivePrices = true) else settings"))
        // Reads, then bets, then alerts: a bet just placed doesn't also alert.
        val read = cycle.indexOf("runCatching { cnoRead(s) }")
        val bet = cycle.indexOf("c.autoBet.run(s, snapshot(s))")
        val alert = cycle.indexOf("alerts += cnoAlerts(s)")
        assertTrue("$read $bet $alert", read in 0 until bet && bet < alert)
        // Auto-bet only inside the CNO scanner's branch, so Vigilant only never reaches it.
        assertTrue(cycle.indexOf("if (settings.autoScansCno) {") < read)
        assertTrue(cycle.contains("listOfNotNull(s.alertMinEv.takeIf { it > 0.0 }, AutoBet.rules(s).minEv.takeIf { s.autoBetsNow }).minOrNull()"))
        assertTrue(java.io.File("src/main/kotlin/com/tjshea/vigilant/app/ApiBetting.kt").readText().contains("lock = c.orderLock"))
        assertTrue(java.io.File("src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt").readText().contains("lock = orderLock"))
    }
}
