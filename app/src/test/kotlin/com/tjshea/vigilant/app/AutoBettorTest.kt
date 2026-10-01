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
import com.tjshea.vigilant.data.novig.signing.NovigApiException
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

    private class FakeNovig(val orders: AtomicInteger = AtomicInteger(), val loseAnswer: Boolean = false, val minDollars: Double = 0.0, val tooSmall: AtomicInteger = AtomicInteger()) :
        NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }), Json { ignoreUnknownKeys = true }) {
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
            // Novig's ORDER_TOO_SMALL (its threshold isn't published): refused before it is an order.
            if (qty * price * 0.01 < minDollars) {
                tooSmall.incrementAndGet()
                throw NovigApiException(400, "ORDER_TOO_SMALL", "order below the minimum")
            }
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
        // Placed like a ✓: the Tracker's bet carries the list key, so the next cycle's lists hide it (the next test); the mark itself is the one after.
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
        // 5.84% is under a 6% minimum: it never becomes a candidate (nothing is read or judged for it).
        val under = runBlocking { settings { it.copy(autoBetMinEv = 0.06) }.let { s -> bettor(novig).run(s, state(s)) } }
        assertEquals(0, under.looked)
        assertEquals(0, under.placed.size)
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

    @Test
    fun `a bet whose edge is implausibly high is left for Tj to place by hand`() = runBlocking {
        val novig = FakeNovig()
        // Novig's price now says +30% against CNO's fair odds: everything else about it is in order (books, price match, wallet).
        val r = bettor(novig).run(settings(), state(live = LivePrice(117, 88.0, 0.30, now - 5_000, "mkt", "out-jj")))
        assertEquals(0, novig.orders.get())
        assertEquals(0, r.placed.size)
        assertTrue(r.skipped.keys.toString(), r.skipped.keys.any { it.contains("usually a stale or mismatched price") })
    }

    @Test
    fun `a bet priced at another book is never placed through Novig`() = runBlocking {
        val novig = FakeNovig()
        val elsewhere = jefferson.copy(book = "DraftKings")
        // Everything else in order for it (books read, Novig's price read), so the book is the only thing standing in the way.
        val st = state(row = elsewhere).let {
            it.copy(
                cno = it.cno.copy(snapshot = it.cno.snapshot!!.copy(rows = listOf(elsewhere))),
                books = mapOf(elsewhere.key to com.tjshea.vigilant.data.cno.CnoBooksState(view = SampleCno.jeffersonBooks())),
            ).indexed(now)
        }
        assertTrue("the fixture reaches the judge", AlertPicks.cnoChecked(st, 0.03, now).isNotEmpty())
        val r = bettor(novig).run(settings(), st)
        assertEquals(0, novig.orders.get())
        assertEquals(0, r.placed.size)
        assertTrue(r.skipped.keys.toString(), r.skipped.keys.any { it.contains("not priced at Novig") })
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
    fun `the stake is held to what's left in the wallet, down to the cent, and an empty wallet stops it and Tj is told once`() = runBlocking {
        val s = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 7.25) }
        val novig = FakeNovig()
        // $2.40 left: the bet is $2.40, not $7.25.
        bettor(novig, wallet = 2.40).run(s, state(s))
        assertTrue(novig.last!!.let { (_, price, qty) -> qty * price * 0.01 } in 2.2..2.40)
        // 60 cents left (Tj, 2026-10-01: no $1 minimum): the bet is 60 cents, not skipped.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novigCents = FakeNovig()
        assertEquals(1, bettor(novigCents, wallet = 0.60).run(s, state(s)).placed.size)
        assertTrue(novigCents.last!!.let { (_, price, qty) -> qty * price * 0.01 } in 0.55..0.60)
        // Under a cent left: no bet, said so.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        val b = bettor(novig2, wallet = 0.004)
        val r = b.run(s, state(s))
        assertTrue(r.walletEmpty)
        assertEquals(0, novig2.orders.get())
        assertTrue(r.stopped!!, r.stopped!!.contains("under a cent"))
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
        // The book offers the outcome at 0.40 (a bid of 0.60 on the other side), nothing like the +117 (0.461) it was judged at, though the edge
        // looks even better: the placer refuses to send, because it may not be the same bet.
        val novig2 = FakeNovig()
        val r2 = bettor(novig2, placer = placer(novig2, book = book(bid = 600))).run(settings(), state())
        assertEquals(0, r2.placed.size)
        assertEquals(0, novig2.orders.get())
        assertTrue(r2.skipped.keys.toString(), r2.skipped.keys.any { it.contains("isn't the price it was judged at") })
        // And a price that moved against it (0.60, the edge gone) is refused too.
        val novig4 = FakeNovig()
        val r4 = bettor(novig4, placer = placer(novig4, book = book(bid = 400))).run(settings(), state())
        assertEquals(0, novig4.orders.get())
        assertEquals(0, r4.placed.size)
        assertTrue(r4.skipped.keys.toString(), r4.skipped.keys.any { it.contains("edge is gone") })
        // A bet that can't be found on Novig for certain isn't bet either.
        val novig3 = FakeNovig()
        val r3 = bettor(novig3, resolve = { null }).run(settings(), state())
        assertEquals(0, novig3.orders.get())
        assertTrue(r3.skipped.keys.any { it.contains("couldn't be found") })
    }

    /** Tj, 2026-10-01: "add an option for longest odds of any auto bet. For example, I don't want it to bet anything that is more of a longshot than +130 odds". */
    @Test
    fun `a bet longer than the longest odds Tj set is never placed, one at the limit is, and favorites always pass`() = runBlocking {
        // Jefferson is +117.
        val novig = FakeNovig()
        val over = bettor(novig).run(settings { it.copy(autoBetMaxOdds = 110) }, state(settings { it.copy(autoBetMaxOdds = 110) }))
        assertEquals(0, over.placed.size)
        assertEquals("nothing reached Novig", 0, novig.orders.get())
        assertEquals(listOf("its odds are longer than your +110 limit"), over.skipped.keys.toList())
        // At the limit, and above it: placed.
        for (limit in listOf(117, 130, 300, 0)) {
            val n = FakeNovig()
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            val s = settings { it.copy(autoBetMaxOdds = limit) }
            assertEquals("limit $limit", 1, bettor(n).run(s, state(s)).placed.size)
        }
    }

    @Test
    fun `the limit holds whatever the stake, a dollar, a typed amount and Kelly all skip a longshot`() = runBlocking {
        for (stake in listOf(AutoBetStake.ONE_DOLLAR, AutoBetStake.CUSTOM, AutoBetStake.QUARTER_KELLY)) {
            val novig = FakeNovig()
            val s = settings { it.copy(autoBetMaxOdds = 100, autoBetStake = stake, autoBetCustomStake = 5.0, bankroll = 1000.0) }
            val r = bettor(novig).run(s, state(s))
            assertEquals(stake.name, 0, r.placed.size)
            assertEquals(stake.name, 0, novig.orders.get())
        }
    }

    @Test
    fun `a price that drifts out past the limit between finding the bet and ordering it is refused on the order book`() = runBlocking {
        // Judged at +117 (passes a +120 limit), within the 3-point price match of the book: but the book is now at 0.44 = +127, over the limit.
        val novig = FakeNovig()
        val s = settings { it.copy(autoBetMaxOdds = 120) }
        val r = bettor(novig, placer = placer(novig, book = book(bid = 560))).run(s, state(s))
        assertEquals(0, novig.orders.get())
        assertEquals(0, r.placed.size)
        assertTrue(r.skipped.keys.toString(), r.skipped.keys.any { it.contains("longer than your +120 limit") })
        // Without the limit the same book is bet (so the refusal is the limit's, not something else's).
        val free = FakeNovig()
        assertEquals(1, bettor(free, placer = placer(free, book = book(bid = 560))).run(settings(), state()).placed.size)
    }

    /** Tj, 2026-10-01: "I don't want a $1 minimum bet for the auto bet feature. It can bet as low as 1 cent … Usually it will be a Kelly number and often under $1". */
    @Test
    fun `a Kelly stake under a dollar is placed as it is, and so is one cent`() = runBlocking {
        // Jefferson is +117 at a 5.84% edge: full Kelly about 5% of the bankroll, so 1/4 Kelly of $20 is about 25 cents.
        val s = settings { it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY, bankroll = 20.0) }
        val novig = FakeNovig()
        val r = bettor(novig).run(s, state(s))
        assertEquals(r.skipped.toString(), 1, r.placed.size)
        val bet = r.placed.single()
        assertTrue("about 25 cents, not a dollar: ${bet.stake}", bet.stake in 0.2..0.26)
        // One cent: one cent's worth of contracts (two at 46 cents).
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val penny = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 0.01) }
        val novig2 = FakeNovig()
        val r2 = bettor(novig2).run(penny, state(penny))
        assertEquals(1, r2.placed.size)
        assertTrue(novig2.last!!.let { (_, _, qty) -> qty in 1L..2L })
        assertTrue(r2.placed.single().stake <= 0.01)
        // A dollar is still a dollar.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig3 = FakeNovig()
        assertTrue(bettor(novig3).run(settings(), state()).placed.single().stake in 0.9..1.0)
    }

    @Test
    fun `Novig refusing a small order skips that bet and learns the size, and the other bets go on`() = runBlocking {
        // Novig refuses anything under 50 cents. A 25-cent Kelly stake is refused: no stop, no halt, no backoff, the bet skipped and the size remembered.
        val small = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 0.25) }
        val novig = FakeNovig(minDollars = 0.50)
        val b = bettor(novig)
        val r = b.run(small, state(small))
        assertEquals(1, novig.tooSmall.get())
        assertEquals(0, novig.orders.get())
        assertEquals(0, r.placed.size)
        assertNull("not a stop: the other bets go on", r.stopped)
        assertFalse(r.halted)
        assertNull(app.container.settingsStore.load().autoBetHalted)
        assertTrue(r.skipped.keys.toString(), r.skipped.keys.any { it.contains("too small") })
        // A bigger stake goes through.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val big = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 1.0) }
        assertEquals(1, bettor(novig).run(big, state(big)).placed.size)
    }

    @Test
    fun `once Novig has refused a size, a stake no bigger is skipped without asking again`() = runBlocking {
        var t = now
        val small = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 0.25) }
        val novig = FakeNovig(minDollars = 0.50)
        val b = AutoBettor(app, app.container, clock = { t }, placer = { placer(novig) }, wallet = { 25.0 }, resolve = { targetOf(it) })
        assertEquals(0, b.run(small, state(small)).placed.size)
        assertEquals(1, novig.tooSmall.get())
        // Past the bet's 2-minute cooldown, with a Novig price read just now: the same stake is skipped, and Novig isn't asked.
        t = now + 150_000L
        val later = state(small, live = LivePrice(117, 88.0, 0.0584, t - 5_000, "mkt", "out-jj")).indexed(t)
        val again = b.run(small, later)
        assertEquals(0, again.placed.size)
        assertEquals("Novig wasn't asked again", 1, novig.tooSmall.get())
        assertTrue(again.skipped.keys.toString(), again.skipped.keys.any { it.contains("is no bigger") })
        // A bigger one goes through on the same bettor.
        val big = small.copy(autoBetCustomStake = 1.0)
        assertEquals(1, b.run(big, state(big, live = LivePrice(117, 88.0, 0.0584, t - 5_000, "mkt", "out-jj")).indexed(t)).placed.size)
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
        val b = bettor(novig)
        val r = b.run(s, state(s))
        assertEquals(0, novig.orders.get())
        assertTrue(r.stopped!!, r.stopped!!.contains("daily limit"))
        assertTrue(notifications().any { it.extras.getString("android.title") == "Auto-bet paused" })
        // And it isn't asked again every cycle: the next one finds it waiting (nothing read, nothing sent).
        val again = b.run(s, state(s))
        assertTrue(again.stopped!!, again.stopped!!.contains("daily limit"))
        assertTrue(b.status.value.blocker!!, b.status.value.blocker!!.startsWith("waiting:"))
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
    fun `when Novig refuses an order nothing is sent again until the wait is over, however fast the cycles`() = runBlocking {
        val refusing = object : NovigTradingClient(
            NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
                override val keyId = "kid"
                override val algorithm = NovigKeyAlgorithm.P256
                override fun sign(message: ByteArray) = ByteArray(0)
            }),
            Json { ignoreUnknownKeys = true },
        ) {
            val sent = AtomicInteger()
            override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
                sent.incrementAndGet()
                throw com.tjshea.vigilant.data.novig.signing.NovigApiException(451, "GEOLOCATION_EXPIRED", "x")
            }
        }
        val p = ApiBetPlacer(refusing, app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        var t = now
        // The real wait is 5 minutes; 30 seconds here keeps the sample data (a minute's freshness for Novig's price) in date.
        assertEquals(5 * 60_000L, AutoBettor.FAIL_BACKOFF_MS)
        val b = AutoBettor(app, app.container, clock = { t }, placer = { p }, wallet = { 25.0 }, resolve = { targetOf(it) }, failBackoffMs = 30_000L)
        val first = b.run(settings(), state())
        assertTrue(first.stopped!!, first.stopped!!.contains("Novig refused"))
        assertEquals(1, refusing.sent.get())
        // 15 seconds later (the fastest interval): nothing is sent.
        t = now + 15_000L
        b.run(settings(), state())
        assertEquals(1, refusing.sent.get())
        assertTrue(b.status.value.blocker!!.contains("waiting after Novig refused"))
        // Once the wait is over, it tries again.
        t = now + 30_001L
        b.run(settings(), state())
        assertEquals(2, refusing.sent.get())
        assertEquals(0, app.container.tracker.all().size)
    }

    /** Tj's v0.38.0 report, 2026-10-01: the app crashed out of memory. A crash after Novig takes an order and before the Tracker has it must not become a second order. */
    @Test
    fun `an order in flight is saved before it is sent, so a process that dies mid-order leaves auto-bet stopped and the bet is never sent twice`() = runBlocking {
        class ProcessDied : Error("the process died mid-order")
        var savedWhenSent: String? = null
        val dying = object : NovigTradingClient(
            NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
                override val keyId = "kid"
                override val algorithm = NovigKeyAlgorithm.P256
                override fun sign(message: ByteArray) = ByteArray(0)
            }),
            Json { ignoreUnknownKeys = true },
        ) {
            val sent = AtomicInteger()
            override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
                sent.incrementAndGet()
                // What is on disk at the moment the order goes to Novig.
                savedWhenSent = app.container.settingsStore.read().autoBetHalted
                throw ProcessDied()
            }
        }
        val p = ApiBetPlacer(dying, app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        val b = AutoBettor(app, app.container, clock = { now }, placer = { p }, wallet = { 25.0 }, resolve = { targetOf(it) })
        val died = runCatching { b.run(settings(), state()) }.exceptionOrNull()
        assertTrue(died is ProcessDied)
        // The marker was already saved when the order went out, and is still there: it names the bet and says what to check.
        assertNotNull(savedWhenSent)
        assertTrue(savedWhenSent!!, savedWhenSent!!.contains("Justin Jefferson Under 69.5") && savedWhenSent!!.contains("was being placed"))
        val after = app.container.settingsStore.read()
        assertEquals(savedWhenSent, after.autoBetHalted)
        assertEquals(0, app.container.tracker.all().size)
        // A fresh process (a new bettor) with the saved settings places nothing, and the order isn't sent again.
        val restarted = AutoBettor(app, app.container, clock = { now }, placer = { p }, wallet = { 25.0 }, resolve = { targetOf(it) })
        val r = restarted.run(settings { it.copy(autoBetHalted = after.autoBetHalted) }, state())
        assertEquals(0, r.placed.size)
        assertTrue(r.halted)
        assertEquals("never sent again", 1, dying.sent.get())
    }

    @Test
    fun `the in-flight marker is cleared once the order has a definitive answer, placed or refused`() = runBlocking {
        val novig = FakeNovig()
        assertEquals(1, bettor(novig).run(settings(), state()).placed.size)
        assertNull("placed: nothing left to check", app.container.settingsStore.read().autoBetHalted)
        // Refused before anything is sent (the book's price is nothing like the one judged): cleared too.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        assertEquals(0, bettor(novig2, placer = placer(novig2, book = book(bid = 600))).run(settings(), state()).placed.size)
        assertNull("refused: cleared", app.container.settingsStore.read().autoBetHalted)
        // A marker that is a real halt (a lost answer's) is never cleared by a later bet's own: the lost-answer test above covers it.
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

    @Test
    fun `a bet placed by the auto-bet is marked placed in the lists, like a check mark`() = runBlocking {
        // The sample game is long past by the clock the marks expire by; a game a day away is what a real bet has.
        val realNow = System.currentTimeMillis()
        val target = targetOf(jefferson).copy(startsTs = realNow + 86_400_000L)
        val bet = app.container.tracker.logApi(target, "o1", listOf(NovigFill("f1", "o1", null, "mkt", "out-jj", 400, 1.85, true, 0.0, realNow)))!!
        markPlaced(app.container, target, com.tjshea.vigilant.data.novig.trading.PlaceResult.Placed(bet, 0), realNow)
        val mark = app.container.placed.load().bets.single()
        assertEquals(MiniWindow.cnoKey(jefferson), mark.key)
        assertEquals("out-jj", mark.outcomeId)
        assertFalse(mark.hidden)
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
