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
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TrapGuard
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
            app.container.makerStore.update { emptyList() }
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
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
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
        // Never Novig's real /trades from a test: the trap guard's move rule reads this instead.
        trades: suspend (String) -> List<TrapGuard.Trade> = { emptyList() },
        // The injury reports: none by default (the sample player is healthy as far as the bettor knows).
        injuries: com.tjshea.vigilant.data.reference.InjuryBook = com.tjshea.vigilant.data.reference.InjuryBook.EMPTY,
    ) = AutoBettor(app, app.container, clock = { now }, placer = { placer }, wallet = { wallet }, resolve = resolve, recentTrades = trades, injuries = { injuries })

    /** On, CNO scanning in the background, 3 books agreeing, 3% edge, 2 books both sides, $1 a bet; the trap guard off (the sample games are 8 h+ off: TrapGuardAppTest). */
    private fun settings(f: (ScanSettings) -> ScanSettings = { it }) =
        f(ScanSettings(autoBet = true, autoScan = AutoScanMode.CNO, autoBetBooks = 3, autoBetMinEv = 0.025, autoBetTwoSided = 2, autoBetStake = AutoBetStake.ONE_DOLLAR, autoBetMaxStake = 10.0, apiMaxPerDay = 50.0, trapEarlyHours = 0))

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

    // ---- one game is one event (Tj, 2026-10-04: one team at +5, then +6, then +10) -----------------------------------------------------------

    /** An open $[dollars] API bet in another market of Jefferson's game (Novig's event "ev"), as the auto-bet or a hand bet would have left it. */
    private suspend fun openOnJeffersonsGame(marketId: String, dollars: Double, eventId: String? = null) {
        val t = targetOf(jefferson).let { it.copy(market = it.market.copy(marketId = marketId, eventId = eventId ?: it.market.eventId), outcomeId = "out-$marketId") }
        app.container.tracker.logApi(t, "o-$marketId", listOf(NovigFill("f-$marketId", "o-$marketId", null, marketId, "out-$marketId", (dollars * 200).toLong(), dollars, true, 0.0, 1_790_400_000_000L)))!!
    }

    private val tenDollars = { it: ScanSettings -> it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 10.0, apiMaxPerGame = 25.0) }

    @Test
    fun `a bet that would take its game past the per-game limit is skipped under one reason, before anything is read or sent`() = runBlocking {
        openOnJeffersonsGame("alt-1", 10.0)
        openOnJeffersonsGame("alt-2", 10.0)
        val novig = FakeNovig()
        val s = settings(tenDollars)
        var bookReads = 0
        val counting = ApiBetPlacer(novig, app.container.tracker, books = { bookReads++; book() }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        val report = bettor(novig, placer = counting).run(s, state(s))
        assertEquals(0, report.placed.size)
        assertEquals("nothing reached Novig", 0, novig.orders.get())
        assertEquals("not even a book was read for it", 0, bookReads)
        assertEquals(mapOf(AutoBet.GAME_LIMIT_SKIP to 1), report.skipped)
    }

    @Test
    fun `a hold the placer makes on its own read - a bet placed meanwhile - is counted under the same one reason`() = runBlocking {
        openOnJeffersonsGame("alt-1", 10.0)
        val novig = FakeNovig()
        val s = settings(tenDollars)
        // The cycle's snapshot sees $10 on the game; while the bet is being found on Novig another $10 lands (a bet by hand): only the placer's read has it.
        val report = bettor(novig, resolve = { row -> openOnJeffersonsGame("alt-2", 10.0); targetOf(row) }).run(s, state(s))
        assertEquals(0, novig.orders.get())
        assertEquals(mapOf(AutoBet.GAME_LIMIT_SKIP to 1), report.skipped)
    }

    @Test
    fun `the same bet on a game with room is placed, and so is one under the limit on a full game`() = runBlocking {
        openOnJeffersonsGame("alt-1", 10.0)
        openOnJeffersonsGame("alt-2", 10.0)
        val novig = FakeNovig()
        // $5 of the $5 left: exactly at the limit.
        val atLimit = settings { it.copy(autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 5.0, apiMaxPerGame = 25.0) }
        assertEquals(1, bettor(novig).run(atLimit, state(atLimit)).placed.size)
    }

    @Test
    fun `other games are never counted - a full game does not stop a bet on a different event`() = runBlocking {
        openOnJeffersonsGame("alt-1", 25.0, eventId = "some-other-game")
        val novig = FakeNovig()
        val s = settings(tenDollars)
        val report = bettor(novig).run(s, state(s))
        assertEquals(1, report.placed.size)
        assertTrue(report.skipped.toString(), AutoBet.GAME_LIMIT_SKIP !in report.skipped)
    }

    @Test
    fun `resting bids on the game count against the limit beside its open bets`() = runBlocking {
        openOnJeffersonsGame("alt-1", 10.0)
        app.container.makerStore.update {
            it + MakerBid(
                clientId = "c1", orderId = "ord-1", marketId = "alt-bid", eventId = "ev", outcomeId = "out-bid", league = jefferson.league, eventName = jefferson.event,
                startsTs = jefferson.startsAtMs!!, marketLabel = "Spread", selection = "x", price = 0.5, contracts = 2_400, fair = 0.55, evAtFair = 0.05, margin = 0.04,
                postedAtMs = now, status = MakerStatus.RESTING,
            )
        }
        val novig = FakeNovig()
        val s = settings(tenDollars)
        // $10 bet + $12 of bid (2,400 contracts at 50¢) = $22 at risk before this one: $32 > $25.
        val report = bettor(novig).run(s, state(s))
        assertEquals(0, novig.orders.get())
        assertEquals(mapOf(AutoBet.GAME_LIMIT_SKIP to 1), report.skipped)
    }

    @Test
    fun `no limit when it is set to 0`() = runBlocking {
        // $30 is over the $25 a game would be held to, and inside the day's $50: only the game limit is in question.
        openOnJeffersonsGame("alt-1", 30.0)
        val novig = FakeNovig()
        val s = settings { tenDollars(it).copy(apiMaxPerGame = 0.0) }
        assertEquals(1, bettor(novig).run(s, state(s)).placed.size)
    }

    // ---- the criteria -------------------------------------------------------------------------------------------

    private fun injuryBook(status: String) = com.tjshea.vigilant.data.reference.InjuryIndex { now }.apply {
        record("americanfootball_nfl", listOf(com.tjshea.vigilant.data.reference.Injury("Justin Jefferson", status)))
    }.book.value

    @Test
    fun `a bet on a player the injury reports say is out is never placed, and the report says why (Tj, 2026-10-07)`() = runBlocking {
        for (status in listOf("Out", "Injured Reserve", "Suspended")) {
            val novig = FakeNovig()
            val report = bettor(novig, injuries = injuryBook(status)).run(settings(), state())
            assertEquals(status, 0, report.placed.size)
            assertEquals("$status: nothing reached Novig", 0, novig.orders.get())
            assertTrue(report.skipped.keys.joinToString(), report.skipped.keys.any { it.startsWith("The player is out (") })
            assertTrue(app.container.tracker.all().isEmpty())
        }
    }

    @Test
    fun `a doubtful or questionable player is still bet - only a player who is certainly out is blocked`() = runBlocking {
        for (status in listOf("Doubtful", "Questionable")) {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            val novig = FakeNovig()
            assertEquals(status, 1, bettor(novig, injuries = injuryBook(status)).run(settings(), state()).placed.size)
            assertEquals(1, novig.orders.get())
        }
    }

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
        // The same bet with criteria it meets is placed (3 books, a 5.84% edge against 2.5%). The books' own check puts it near 3%, and the LOWER of the two edges is judged
        // (Tj, 2026-10-07, proposal 5): against a 5% minimum it is not placed, and the report says the books' check is why.
        assertEquals(1, bettor(novig).run(settings { it.copy(autoBetMinEv = 0.025, autoBetBooks = 3, autoBetTwoSided = 3) }, state()).placed.size)
        val lower = runBlocking { settings { it.copy(autoBetMinEv = 0.05, autoBetBooks = 3, autoBetTwoSided = 3) }.let { s -> bettor(novig).run(s, state(s)) } }
        assertEquals(0, lower.placed.size)
        assertTrue(lower.skipped.keys.toString(), lower.skipped.keys.any { it.startsWith("the books' own check puts its edge at ") })
    }

    @Test
    fun `the sharp veto skips a bet only when the sharpest book for its kind says no, and the bet keeps its record as placed`() = runBlocking {
        // Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement. Only skip a bet if the sharpest book for that market says it is not +ev." Jefferson's
        // Under is a prop: Kalshi, then ProphetX decide; Pinnacle doesn't.
        fun withBooks(view: com.tjshea.vigilant.data.cno.CnoBooksView, s: ScanSettings) =
            state(s).let { it.copy(books = mapOf(jefferson.key to com.tjshea.vigilant.data.cno.CnoBooksState(view = view))).indexed(now) }
        val veto = settings { it.copy(autoBetBooks = 2, autoBetMinEv = 0.01, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.VETO) }
        // Kalshi says no (+105/-135 under Novig's +117): vetoed, nothing reaches Novig, and the report says who.
        val kalshiNo = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "KI") com.tjshea.vigilant.data.cno.CnoBookPrice("KI", 105, 106.0, -135, 13_662.0) else it }) }
        val novig = FakeNovig()
        val b = bettor(novig)
        val r = b.run(veto, withBooks(kalshiNo, veto))
        assertEquals(0, r.placed.size)
        assertEquals(0, novig.orders.get())
        assertEquals(r.skipped.toString(), 1, r.skipped["Kalshi, the sharpest book for player props, says it isn't +EV at Novig's price"])
        assertEquals(mapOf("veto.VETOED" to 1), b.status.value.sharp)
        // Pinnacle saying no doesn't veto a prop (it isn't a prop sharp); Kalshi agreeing lets it through, and the bet records it all.
        val pinnacleNo = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "PN") com.tjshea.vigilant.data.cno.CnoBookPrice("PN", 105, null, -135, null) else it }) }
        val novig2 = FakeNovig()
        val placed = bettor(novig2).run(veto, withBooks(pinnacleNo, veto)).placed.single()
        val rec = placed.atBet!!
        assertEquals(com.tjshea.vigilant.data.tracker.AtBet.HOW_AUTO, rec.how)
        assertEquals("PASSED", rec.sharpVerdict)
        assertEquals("Kalshi", rec.sharpBook)
        assertEquals("PROP", rec.kind)
        assertEquals(listOf("Pinnacle"), rec.dissent)
        assertEquals(3, rec.twoSided)
        assertEquals(2, rec.agreeing)
        assertEquals(117, rec.american)
        assertEquals((jefferson.startsAtMs!! - now) / 60_000L, rec.minutesToStart)
        assertTrue(rec.books.any { it.book == "Kalshi" && it.fair != null && it.ev!! > 0.0 })
        assertEquals(placed.stake, rec.stake!!, 1e-9)
    }

    /** RESEARCH.md §72: what a bet keeps at the close is about the sharpest book's own edge, so a small sharp edge is vetoed (1% by default). */
    @Test
    fun `the sharp veto's bar - a bet the sharpest book gives under 1% isn't placed, the same bet with the bar at any +EV is`() = runBlocking {
        fun withBooks(view: com.tjshea.vigilant.data.cno.CnoBooksView, s: ScanSettings) =
            state(s).let { it.copy(books = mapOf(jefferson.key to com.tjshea.vigilant.data.cno.CnoBooksState(view = view))).indexed(now) }
        // Kalshi +108/-124 on Jefferson's Under: a small positive edge at Novig's +117.
        val small = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "KI") com.tjshea.vigilant.data.cno.CnoBookPrice("KI", 108, 106.0, -124, 13_662.0) else it }) }
        val ev = com.tjshea.vigilant.data.scanner.SharpVeto.judge(small, jefferson.league, jefferson.market, jefferson.bet, 117, false, 0.0).ev!!
        assertTrue("Kalshi's own edge here is small but positive: $ev", ev > 0.0 && ev < 0.01)
        val bar = settings { it.copy(autoBetBooks = 2, autoBetMinEv = 0.01, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.VETO) }
        assertEquals(0.01, bar.sharpVetoMinEv, 0.0)
        val novig = FakeNovig()
        val underBefore = app.container.eventLog.counters()[AutoBettor.SHARP_BAR_COUNTER] ?: 0L
        val r = bettor(novig).run(bar, withBooks(small, bar))
        assertEquals(0, r.placed.size)
        assertEquals(0, novig.orders.get())
        assertEquals(r.skipped.toString(), 1, r.skipped["Kalshi, the sharpest book for player props, gives Novig's price under the sharp veto's 1.0% edge"])
        // Diagnostics counts what the bar alone stopped (BRIEF.md: a new rule gets its own counter), apart from "the sharp book says it isn't +EV".
        assertEquals(underBefore + 1, app.container.eventLog.counters()[AutoBettor.SHARP_BAR_COUNTER] ?: 0L)
        // The old bar (any +EV) places it.
        val any = bar.copy(sharpVetoMinEv = 0.0)
        assertEquals(1, bettor(FakeNovig()).run(any, withBooks(small, any)).placed.size)
    }

    /** The money goes first to the edges most likely to hold (RESEARCH.md §72): the order the placement loop walks. */
    @Test
    fun `when not every bet can be placed, the one with the bigger credible edge goes first`() {
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoBettor.kt").readText()
        val sort = src.indexOf("val ordered = passing.sortedByDescending { AutoBet.credibleEv(")
        assertTrue("the passing bets are ordered by the credible edge", sort > 0)
        assertTrue("and the placement loop walks that order", src.indexOf("for (item in ordered)") > sort)
        assertFalse("not the shown edge's order", src.contains("for (item in passing)"))
    }

    /** Tj, 2026-10-01: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)". */
    @Test
    fun `with every book must agree on, a bet one book disagrees with is not placed, and one all of them agree with is`() = runBlocking {
        // Jefferson's books: three price both sides and all say +EV at +117 (3 of 3). Make one of them (KI) price the Under at +140 against -160: its own fair
        // line is under Novig's price, so it disagrees, while the others' consensus still says +EV (2 of 3).
        val split = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "KI") com.tjshea.vigilant.data.cno.CnoBookPrice("KI", 105, 106.0, -135, 13_662.0) else it }) }
        fun withBooks(view: com.tjshea.vigilant.data.cno.CnoBooksView, s: ScanSettings) =
            state(s).let { it.copy(books = mapOf(jefferson.key to com.tjshea.vigilant.data.cno.CnoBooksState(view = view))).indexed(now) }
        // The sharp veto off: the one book saying no is Kalshi, the sharpest for props, which would veto it (the next test's subject).
        val loose = settings { it.copy(autoBetBooks = 2, autoBetMinEv = 0.01, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF) }
        val check = AlertPicks.cnoChecked(withBooks(split, loose), 0.03, now).single().check
        assertEquals("the fixture: 2 of 3 agree and the consensus is still +EV", 3 to 2, check.twoSided to check.agreeing)
        assertTrue(check.ev!! > 0.0)
        // Off: 2 of 3 passes a minimum of 2.
        val novig = FakeNovig()
        assertEquals(1, bettor(novig).run(loose, withBooks(split, loose)).placed.size)
        // On: the same bet is skipped, with the count in the reason, and nothing reaches Novig.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val strict = loose.copy(autoBetAllAgree = true)
        val novig2 = FakeNovig()
        val r = bettor(novig2).run(strict, withBooks(split, strict))
        assertEquals(0, r.placed.size)
        assertEquals(0, novig2.orders.get())
        assertTrue(r.skipped.keys.toString(), r.skipped.keys.any { it.contains("only 2 of 3 books say +EV on their own (you need every one)") })
        // All three agree (the sample's own books): placed with the switch on.
        val novig3 = FakeNovig()
        val all = settings { it.copy(autoBetBooks = 2, autoBetAllAgree = true) }
        val placed = bettor(novig3).run(all, withBooks(SampleCno.jeffersonBooks(), all)).placed
        assertEquals(1, placed.size)
        // The notification for it says what agreed (the first bet above, with 2 of 3, said that).
        val texts = notifications().filter { it.extras.getString("android.title")!!.startsWith("Auto-bet") }.map { it.extras.getString("android.text")!! }
        assertTrue(texts.toString(), texts.any { it.contains("3 of 3 books agree") } && texts.any { it.contains("2 of 3 books agree") })
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
        // 1/8 Kelly of $1,000: CNO's fair 0.4878 at +117 (price 0.46083) would be (0.4878 - 0.46083) / (1 - 0.46083) = 0.050; x 0.125 x 1000 = $6.25. But the stake is sized on
        // the LOWEST of the fairs (Tj, 2026-10-07, proposal 5): here the books' own check (+2.99% at +117, fair about 0.4746): about half, $3.2. (Sharp veto off here.)
        val s = settings { it.copy(autoBetStake = AutoBetStake.EIGHTH_KELLY, bankroll = 1000.0, autoBetMaxStake = 100.0, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF) }
        val novig = FakeNovig()
        bettor(novig, placer = placer(novig, limits = BetLimits(100.0, 500.0, 0.01))).run(s, state(s))
        val cost = novig.last!!.let { (_, price, qty) -> qty * price * 0.01 }
        assertTrue("about \$3.2 of contracts (the books' own fair), never CNO's \$6.25: $cost", cost in 3.0..3.25)
        // The veto on (the default): Kalshi, the sharpest prop book on the page, backs less (its own fair ~0.474, +2.9% at +117), so the stake is sized
        // on Kalshi's fair, about half (RESEARCH.md §72: never more edge than the sharp book backs).
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val withVeto = s.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.VETO)
        val novigV = FakeNovig()
        bettor(novigV, placer = placer(novigV, limits = BetLimits(100.0, 500.0, 0.01))).run(withVeto, state(withVeto))
        val vetoCost = novigV.last!!.let { (_, price, qty) -> qty * price * 0.01 }
        assertTrue("sized on Kalshi's own fair: $vetoCost", vetoCost in 2.9..3.15)
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
        assertEquals(1, notifications().count { it.extras.getString("android.title") == "Wallet empty: Vigilant is asleep" })
    }

    @Test
    fun `an empty wallet puts Vigilant to sleep - every scan paused - once per emptying, and Tj's Resume is left alone until it refills and runs out again`() = runBlocking {
        // Tj, 2026-10-02 ~22:10Z: "make it also stop scanning and put the app to sleep once the wallet runs out of money".
        app.container.settingsStore.update { settings() }
        val s = settings()
        var wallet: Double? = 0.004
        val b = AutoBettor(app, app.container, clock = { now }, placer = { placer(FakeNovig()) }, wallet = { wallet }, resolve = { targetOf(it) })
        assertTrue(b.run(s, state(s)).walletEmpty)
        assertTrue("scanning paused", app.container.settingsStore.flow.value!!.paused)
        assertEquals(com.tjshea.vigilant.data.scanner.AutoScanMode.OFF, app.container.settingsStore.flow.value!!.activeAutoScan)
        val note = notifications().single { it.extras.getString("android.title") == "Wallet empty: Vigilant is asleep" }
        assertTrue(note.extras.getCharSequence("android.text")!!.contains("scanning is paused"))
        // Tj resumes with the wallet still empty: he's left alone.
        app.container.settingsStore.update { it.copy(pausedByHand = false) }
        b.run(s, state(s))
        assertFalse(app.container.settingsStore.flow.value!!.paused)
        // It refills, bets, then runs out again: asleep again.
        wallet = 5.0
        b.run(s, state(s))
        assertFalse(app.container.settingsStore.flow.value!!.paused)
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        wallet = 0.0
        b.run(s, state(s))
        assertTrue(app.container.settingsStore.flow.value!!.paused)
    }

    @Test
    fun `once the wallet put Vigilant to sleep, the rest of that background cycle (alerts, closes, locks, Vigilant's scan) doesn't run`() {
        val f = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").takeIf { it.exists() } ?: java.io.File("app/src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt")
        val cycle = f.readText().substringAfter("suspend fun cycle(forceVigilant: Boolean = false): Boolean").substringBefore("private suspend fun cnoRead")
        val bet = cycle.indexOf("c.autoBet.run(s, snapshot(s))")
        val asleep = cycle.indexOf("if (c.currentSettings().paused) return true")
        assertTrue(bet > 0 && asleep > bet)
        assertTrue("before the alerts", asleep < cycle.indexOf("cnoAlerts(s)"))
    }

    @Test
    fun `a wallet the cycle just read as empty puts Vigilant to sleep even with no bet on offer`() = runBlocking {
        app.container.settingsStore.update { settings() }
        val s = settings()
        val nothing = state(s).let { it.copy(cno = it.cno.copy(snapshot = it.cno.snapshot!!.copy(rows = emptyList()))).indexed(now) }
        app.container.wallet.record(0.0, at = now - 5_000)
        AutoBettor(app, app.container, clock = { now }, placer = { placer(FakeNovig()) }, wallet = { 0.0 }, resolve = { targetOf(it) }).run(s, nothing)
        assertTrue(app.container.settingsStore.flow.value!!.paused)
        // An old reading (not this cycle's) isn't enough to put it to sleep.
        app.container.settingsStore.update { it.copy(pausedByHand = false) }
        app.container.wallet.record(0.0, at = now - 5_000)
        val later = AutoBettor(app, app.container, clock = { now + WalletBalance.FRESH_MS + 5_000 }, placer = { placer(FakeNovig()) }, wallet = { 0.0 }, resolve = { targetOf(it) })
        later.run(s, nothing)
        assertFalse(app.container.settingsStore.flow.value!!.paused)
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
        // Jefferson is +117 at a 5.84% edge by CNO and about 3% by the books' own check (the lower is sized): full Kelly about 2.4% of the bankroll, so 1/4 Kelly of $20 is about 12 cents.
        val s = settings { it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY, bankroll = 20.0, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF) }
        val novig = FakeNovig()
        val r = bettor(novig).run(s, state(s))
        assertEquals(r.skipped.toString(), 1, r.placed.size)
        val bet = r.placed.single()
        assertTrue("about 12 cents, not a dollar: ${bet.stake}", bet.stake in 0.09..0.15)
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

    // ---- Tj, 2026-10-01 ~17:5x: a push notification for every automatic bet, with the stake and the EV ----------

    @Test
    fun `every automatic bet gets its own pop-up notification with the stake, the EV, the odds and what's left in the wallet`() = runBlocking {
        val novig = FakeNovig()
        val r = bettor(novig, wallet = 25.0).run(settings(), state())
        val bet = r.placed.single()
        val note = notifications().single { it.extras.getString("android.title")!!.startsWith("Auto-bet") }
        val title = note.extras.getString("android.title")!!
        val text = note.extras.getString("android.text")!!
        // The stake and the EV are in the title, so the collapsed notification already says what was bet.
        assertTrue(title, title.contains(String.format(java.util.Locale.US, "\$%.2f", bet.stake)))
        assertTrue(title, Regex("""[+-]\d+\.\d% EV""").containsMatchIn(title))
        assertTrue(title, title.contains("Justin Jefferson Under 69.5"))
        assertTrue(text, text.contains("+117"))
        assertTrue(text, text.contains("3 of 3 books agree"))
        assertTrue(text, text.contains("wallet \$24.") && text.endsWith("left"))
        // On the channel that pops up (the old one was normal importance).
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(AutoBetNotes.CHANNEL_BET)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(AutoBetNotes.CHANNEL_BET, note.channelId)
    }

    @Test
    fun `bets placed one after another each keep their own notification, none replaces another`() {
        val bet = runBlocking { bettor(FakeNovig(), wallet = 25.0).run(settings(), state()).placed.single() }
        val target = targetOf(jefferson)
        val check = AlertPicks.cnoChecked(state(), 0.03, now).first().check
        for (i in 1..5) AutoBetNotes.placed(app, target, bet.copy(id = "api-bet-$i", stake = i * 0.25), check, walletLeft = 10.0 - i)
        val mine = notifications().filter { it.channelId == AutoBetNotes.CHANNEL_BET }
        // The one the cycle posted plus five more, each its own.
        assertEquals(6, mine.size)
        val titles = mine.map { it.extras.getString("android.title")!! }
        for (i in 1..5) assertTrue(titles.toString(), titles.any { it.startsWith(String.format(java.util.Locale.US, "Auto-bet \$%.2f", i * 0.25)) })
    }

    @Test
    fun `a test notification is the same kind and says it's a test, and a blocked channel is named`() {
        assertTrue(AutoBetNotes.sample(app))
        val sample = notifications().single { it.extras.getString("android.title")!!.contains("Test bet") }
        assertEquals(AutoBetNotes.CHANNEL_BET, sample.channelId)
        assertTrue(sample.extras.getString("android.title")!!.contains("EV"))
        assertNull("nothing blocks it on a phone that allows notifications", AutoBetNotes.blocked(app))
        // Notifications switched off for the app: said, in words that say where to turn them on.
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertTrue(AutoBetNotes.blocked(app)!!.contains("switched off for Vigilant"))
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
    }

    // ---- Tj, 2026-10-01 ~17:5x: the stake rules in his own sentences --------------------------------------------

    private fun spent(novig: FakeNovig) = novig.last!!.let { (_, price, qty) -> qty * price * 0.01 }

    @Test
    fun `with one cent left in the wallet it bets, may empty the wallet completely, and then stops`() = runBlocking {
        // "bet stakes all the way down to 1 cent, even if there is only 1 cent left in the wallet. It is allowed to completely deplete the wallet."
        val s = settings { it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY, bankroll = 1000.0, autoBetMaxStake = 10.0) }
        val novig = FakeNovig()
        val r = bettor(novig, wallet = 0.01).run(s, state(s))
        assertEquals(r.skipped.toString(), 1, r.placed.size)
        assertEquals(1, novig.orders.get())
        assertTrue("at most the cent that was there: ${spent(novig)}", spent(novig) in 0.001..0.0100001)
        assertFalse("not an empty-wallet stop for a wallet that holds a cent", r.walletEmpty)
        // What is left (under a cent) is an empty wallet: no more bets, said so.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        val r2 = bettor(novig2, wallet = 0.01 - spent(novig)).run(s, state(s))
        assertTrue(r2.walletEmpty)
        assertEquals(0, novig2.orders.get())
    }

    @Test
    fun `a Kelly stake bigger than the wallet bets the rest of the wallet`() = runBlocking {
        // 1/4 Kelly of $1,000 at this edge is about $12.5; the wallet holds 37 cents: the bet is the 37 cents.
        val s = settings { it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY, bankroll = 1000.0, autoBetMaxStake = 100.0) }
        val novig = FakeNovig()
        assertEquals(1, bettor(novig, wallet = 0.37).run(s, state(s)).placed.size)
        assertTrue("the remainder of the wallet, ${spent(novig)}", spent(novig) in 0.36..0.37)
        // The same with a wallet of $3.10.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        assertEquals(1, bettor(novig2, wallet = 3.10).run(s, state(s)).placed.size)
        assertTrue("the remainder of the wallet, ${spent(novig2)}", spent(novig2) in 3.0..3.10)
    }

    @Test
    fun `a Kelly stake bigger than the maximum in the options bets the maximum`() = runBlocking {
        val s = settings { it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY, bankroll = 1000.0, autoBetMaxStake = 0.50) }
        val novig = FakeNovig()
        assertEquals(1, bettor(novig, wallet = 25.0).run(s, state(s)).placed.size)
        assertTrue("the maximum, ${spent(novig)}", spent(novig) in 0.49..0.50)
        // A smaller wallet than the maximum wins over it: the wallet is what there is.
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val novig2 = FakeNovig()
        assertEquals(1, bettor(novig2, wallet = 0.20).run(s, state(s)).placed.size)
        assertTrue("the wallet, ${spent(novig2)}", spent(novig2) in 0.19..0.20)
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
        assertNull(app.container.settingsStore.read().autoBetHalted)
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
            override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
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
            override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
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
        val read = cycle.indexOf("runCatching { timed(\"cno\") { cnoRead(s) } }")
        val bet = cycle.indexOf("c.autoBet.run(s, snapshot(s))")
        val alert = cycle.indexOf("alerts += cnoAlerts(s)")
        assertTrue("$read $bet $alert", read in 0 until bet && bet < alert)
        // Auto-bet only inside the CNO scanner's branch, so Vigilant only never reaches it.
        assertTrue(cycle.indexOf("if (settings.autoScansCno) {") < read)
        assertTrue(cycle.contains("listOfNotNull(s.alertMinEv.takeIf { it > 0.0 }, AutoBet.rules(s).minEv.takeIf { s.autoBetsNow }).minOrNull()"))
        assertTrue(java.io.File("src/main/kotlin/com/tjshea/vigilant/app/ApiBetting.kt").readText().contains("lock = c.orderLock"))
        assertTrue(java.io.File("src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt").readText().contains("lock = orderLock"))
    }

    // ---- the trap guard (Tj, 2026-10-03: "find these trap bets and avoid them"; RESEARCH.md §71) ----------------------------------------------

    @Test
    fun `the trap guard's early rule - a bet further off than its hours isn't placed and the report counts it, one inside them is`() = runBlocking {
        // Jefferson starts a day after the sample's clock.
        val novig = FakeNovig()
        val early = bettor(novig).run(settings { it.copy(trapEarlyHours = 12) }, state(settings { it.copy(trapEarlyHours = 12) }))
        assertEquals(0, novig.orders.get())
        // Jefferson, Bowers and St. Brown start a day off (Ohio, 8 h off, has no books read, so it isn't a candidate here).
        assertEquals(3, early.skipped[TrapGuard.earlyReason(12)])
        val inside = bettor(novig).run(settings { it.copy(trapEarlyHours = 24) }, state(settings { it.copy(trapEarlyHours = 24) }))
        assertEquals(1, inside.placed.size)
    }

    @Test
    fun `the trap guard's first-listed rule - a bet first listed more than its hours before the start isn't placed once the game is inside the window, the report counts it, and the switch turns it off`() = runBlocking {
        val novig = FakeNovig()
        val h = 3_600_000L
        val s = settings { it.copy(trapEarlyHours = 36) }
        fun withFirst(t: Long?, set: ScanSettings = s) = state(set).let { st -> st.copy(firstListed = t?.let { mapOf(jefferson.key to it) } ?: emptyMap()) }
        // First listed 30 h ago, the game 24 h off (54 h before the start, over 36): left alone, and said so.
        val old = bettor(novig).run(s, withFirst(now - 30 * h))
        assertEquals(0, novig.orders.get())
        assertEquals(1, old.skipped[TrapGuard.listedEarlyReason(36)])
        assertEquals(null, old.skipped[TrapGuard.earlyReason(36)])
        // First listed an hour ago, or never seen: placed.
        assertEquals(1, bettor(novig).run(s, withFirst(now - h)).placed.size)
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        assertEquals(1, bettor(novig).run(s, withFirst(null)).placed.size)
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        // The switch off: the old listing is placed.
        val off = settings { it.copy(trapEarlyHours = 36, trapFirstListed = false) }
        assertEquals(1, bettor(novig).run(off, withFirst(now - 30 * h, off)).placed.size)
    }

    /** Ohio −33.5 (a spread, 8 h off) at +117 with Pinnacle and two books pricing both sides at +EV, Novig's price read 5 s ago. */
    private val ohio = SampleCno.rows[2]

    private fun ohioState(s: ScanSettings): UiState {
        val view = com.tjshea.vigilant.data.cno.CnoBooksView(
            bet = "Ohio -33.5", otherBet = "Stonehill +33.5", cnoFair = 106,
            prices = listOf(
                com.tjshea.vigilant.data.cno.CnoBookPrice("PN", -105, null, -115, null),
                com.tjshea.vigilant.data.cno.CnoBookPrice("DK", -108, null, -112, null),
                com.tjshea.vigilant.data.cno.CnoBookPrice("FD", -106, null, -114, null),
                com.tjshea.vigilant.data.cno.CnoBookPrice("NV", 117, 100.0, -127, 120.0),
            ),
            fetchedAtMs = now - 5_000,
        )
        val base = SampleCno.state(SampleScan.state().copy(settings = s.copy(cnoLivePrices = true)))
        return base.copy(
            books = mapOf(ohio.key to com.tjshea.vigilant.data.cno.CnoBooksState(view = view)),
            novigLive = mapOf(ohio.key to LivePrice(117, 100.0, 0.0528, now - 5_000, "mkt", "out-jj")),
        ).indexed(now)
    }

    /** Our side traded around 0.53 this hour (the other side's makers at 0.47); [otherSide] dollars bought the other side 5 min ago. */
    private fun movedTrades(otherSide: Double): List<TrapGuard.Trade> =
        List(5) { TrapGuard.Trade("out-other", 0.47, 500, now - (50 - it) * 60_000L) } +
            listOfNotNull(TrapGuard.Trade("out-jj", 0.47, Math.round(otherSide / (0.53 * 0.01)), now - 5 * 60_000L).takeIf { otherSide > 0 })

    @Test
    fun `the trap guard's move rule - a spread whose Novig price just fell under its level as the other side was bought isn't placed, a quiet one is and records what was read`() = runBlocking {
        val s = settings { it.copy(trapEarlyHours = 12) }
        val novig = FakeNovig()
        val asked = ArrayList<String>()
        val trap = bettor(novig, trades = { asked += it; movedTrades(otherSide = 150.0) }).run(s, ohioState(s))
        assertEquals(listOf("mkt"), asked)
        assertEquals(0, novig.orders.get())
        assertEquals(1, trap.skipped[AutoBettor.MOVE_SKIP])
        // The same spread with nobody buying the other side: placed, and its record says what Novig's trades showed.
        val clear = bettor(novig, trades = { movedTrades(otherSide = 0.0) }).run(s, ohioState(s))
        assertEquals(1, clear.placed.size)
        val rec = app.container.tracker.all().single().atBet!!
        assertTrue(rec.novigMove, rec.novigMove!!.startsWith("CLEAR · Novig level 0.530"))
    }

    @Test
    fun `the move rule reads nothing for a prop or with the rule off, and a game line whose trades can't be read is skipped (not bet unchecked), then bet once they read`() = runBlocking {
        val novig = FakeNovig()
        var reads = 0
        // Jefferson is a prop: never read.
        assertEquals(1, bettor(novig, trades = { reads++; movedTrades(5_000.0) }).run(settings(), state()).placed.size)
        assertEquals(0, reads)
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        // Off: the spread isn't read either.
        val off = settings { it.copy(trapEarlyHours = 12, trapNovigMove = false) }
        assertEquals(1, bettor(novig, trades = { reads++; movedTrades(5_000.0) }).run(off, ohioState(off)).placed.size)
        assertEquals(0, reads)
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        // On, and Novig doesn't answer (Tj, 2026-10-07, proposal 10): the game line is skipped, not bet unchecked, and the report says so.
        val on = settings { it.copy(trapEarlyHours = 12) }
        val novig3 = FakeNovig()
        val failing = bettor(novig3, trades = { reads++; throw java.io.IOException("HTTP 429") })
        val unread = failing.run(on, ohioState(on))
        assertEquals(0, unread.placed.size)
        assertEquals(0, novig3.orders.get())
        assertEquals(1, unread.skipped[AutoBettor.MOVE_UNREAD_SKIP])
        assertTrue(reads > 0)
        // The next cycle reads again; with the trades back the bet goes through, and its record says what was read.
        assertEquals(1, bettor(novig3, trades = { movedTrades(otherSide = 0.0) }).run(on, ohioState(on)).placed.size)
        assertTrue(app.container.tracker.all().single().atBet!!.novigMove!!.startsWith("CLEAR"))
        // A prop never asks, so a failing read can't stop it.
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        assertEquals(1, bettor(novig, trades = { throw java.io.IOException("HTTP 429") }).run(settings(), state()).placed.size)
    }
}
