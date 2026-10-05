package com.tjshea.vigilant.app

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PositiveDepth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * The auto-bet in Pinnacle only on a real app container with a fake Novig (Tj, 2026-10-05; RESEARCH.md §88.5). REAL MONEY: what must never happen is a bet on a Pinnacle
 * price older than Tj's limit, a bet whose fair isn't Pinnacle's alone, a bet after the STOP button, or a quote re-read that is never used.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PinnacleAutoBetTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = SampleScan.NOW

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.makerStore.update { emptyList() }
            app.container.settingsStore.update { ScanSettings() }
            app.container.killMarker.set(false)
        }
    }

    private class FakeNovig(val orders: AtomicInteger = AtomicInteger(), val afterOrder: suspend () -> Unit = {}) :
        NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }), Json { ignoreUnknownKeys = true }) {
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            orders.incrementAndGet()
            last = Triple(outcomeId, price, qty)
            afterOrder()
            return "order-${orders.get()}"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "", last!!.first, last!!.second, last!!.third, 0, "IOC", "FILLED", 1)
        override suspend fun fills(orderId: String?, limit: Int) = last?.let { (outcome, price, qty) ->
            listOf(NovigFill("f-$orderId", orderId ?: "o", null, "mkt", outcome, qty, qty * price * 0.01, true, 0.0, 1_790_400_000_000L))
        }.orEmpty()
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = emptyList<NovigOrder>()
    }

    private val starts = now + 8 * 3_600_000L
    private val event = NovigEvent("ev", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "A @ B", starts)
    private val market = NovigMarket(
        "mkt", "ev", "RECEIVING_YARDS", "OPEN", "Player Receiving Yards", starts, MarketFee.GAME,
        listOf(NovigOutcome("out-over", "Over 69.5", "TBD"), NovigOutcome("out-under", "Under 69.5", "TBD")),
    )

    private val market2 = market.copy(marketId = "mkt2", eventId = "ev2", outcomes = listOf(NovigOutcome("out2-over", "Over 40.5", "TBD"), NovigOutcome("out2-under", "Under 40.5", "TBD")))

    /** A bid of 0.539 on the other side makes the Over's ask 0.461. */
    private fun book(id: String = "mkt") =
        if (id == "mkt2") NovigBook("mkt2", 1, mapOf("out2-under" to listOf(BidLevel(539, 5_000)), "out2-over" to listOf(BidLevel(400, 50))), now)
        else NovigBook("mkt", 1, mapOf("out-under" to listOf(BidLevel(539, 5_000)), "out-over" to listOf(BidLevel(400, 50))), now)

    private fun fair(vararg books: String, asOf: Long) = FairLine(
        listOf(0.5, 0.5), FairSource.SHARP, FairSource.SHARP, DevigMethod.WORST_CASE,
        books.map { BookFair(BookPrices(it.lowercase(), it, listOf(2.0, 1.9), asOf), listOf(0.5, 0.5), 0.04, true) }, books.toList(), emptyList(),
    )

    /** Over 69.5 at Novig's 0.461 against Pinnacle's 0.50: +8.5%, with Pinnacle's quote [asOf] ms. */
    private fun opp(asOf: Long, books: Array<String> = arrayOf("Pinnacle"), cost: Double = 0.461, second: Boolean = false) = (if (second) market2 else market).let { m -> Opportunity(
        league = Leagues.byNovigName("NFL")!!, event = if (second) event.copy(eventId = "ev2") else event, market = m, outcome = m.outcomes.first(), marketLabel = "Player Receiving Yards", kind = LineKind.PLAYER_PROP,
        selection = "Player Over 69.5", fair = fair(*books, asOf = asOf), fairProbability = 0.5, quote = EvQuote(0.5, cost, 0.0), ladder = emptyList(),
        depth = PositiveDepth(1000, 200.0, 5.0, cost), suggestedStake = null, novigWidth = null, bookFetchedAtMs = now - 5_000, fairUpdatedMs = asOf,
        refEvent = null, lineKey = LineKey("pin-1", LineKind.PLAYER_PROP, 69.5, 0, "Player", "RECEIVING_YARDS"), target = OutcomeTarget.Is(Side.OVER), fairAsOfMs = asOf,
    ) }

    private fun result(vararg o: Opportunity) = ScanResult(emptyList(), o.toList(), ScanStats(0, 0, 0, 0, 0), now)

    private fun placer(novig: FakeNovig) =
        ApiBetPlacer(novig, app.container.tracker, books = { id -> book(id) }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)

    private fun bettor(novig: FakeNovig, wallet: Double? = 25.0) =
        AutoBettor(app, app.container, clock = { now }, placer = { placer(novig) }, wallet = { wallet }, recentTrades = { emptyList() })

    private fun settings(f: (ScanSettings) -> ScanSettings = { it }) = f(
        ScanSettings(
            pinnacleOnly = true, pinnacleMaxAgeSeconds = 90, autoBet = true, autoScan = AutoScanMode.BOTH, leagues = setOf("NFL"), autoBetMinEv = 0.03,
            autoBetStake = AutoBetStake.ONE_DOLLAR, autoBetMaxStake = 10.0, apiMaxPerDay = 50.0, trapEarlyHours = 0,
        ),
    )

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `a bet under Pinnacle's price read this minute is placed with no re-read, and its record says Pinnacle only`() = runBlocking {
        val novig = FakeNovig()
        var refreshed = 0
        val report = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 10_000))) { refreshed++; null }
        assertEquals(0, refreshed)
        assertEquals(1, report.placed.size)
        assertEquals(1, novig.orders.get())
        val bet = app.container.tracker.all().single()
        assertTrue(bet.viaApi && bet.auto)
        assertEquals("out-over", bet.outcomeId)
        val at: AtBet = bet.atBet!!
        assertTrue(at.pinnacleOnly)
        assertEquals(AtBet.HOW_AUTO, at.how)
        assertEquals(10L, at.pinnacleAgeSec)
        val pin = at.books.single()
        assertEquals("Pinnacle", pin.book)
        assertEquals(100, pin.odds)
        assertEquals(-111, pin.other)
        assertEquals(0.5, pin.fair!!, 1e-9)
        assertEquals(0.5 / 0.461 - 1.0, pin.ev!!, 1e-9)
        assertTrue(notifications().any { it.extras.getString("android.text")!!.contains("vs Pinnacle devigged") })
    }

    @Test
    fun `a Pinnacle quote past a third of the limit is read again first and the bet goes on the new price, once`() = runBlocking {
        val novig = FakeNovig()
        val asked = ArrayList<Set<String>>()
        val report = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 60_000))) { leagues -> asked += leagues; result(opp(asOf = now - 2_000)) }
        assertEquals(listOf(setOf("NFL")), asked)
        assertEquals(1, report.placed.size)
        assertEquals(2L, app.container.tracker.all().single().atBet!!.pinnacleAgeSec)
    }

    @Test
    fun `when the re-read cannot be made a quote past the limit is never bet on, and one inside the limit still is`() = runBlocking {
        val novig = FakeNovig()
        val old = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 120_000))) { null }
        assertEquals(0, old.placed.size)
        assertEquals(0, novig.orders.get())
        assertTrue(old.skipped.keys.toString(), old.skipped.keys.contains("Pinnacle's price is older than your limit"))
        // 60 s old with a re-read that fails (it throws): inside the 90 s limit, so it is bet.
        val ok = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 60_000))) { throw java.io.IOException("PinnWire is down") }
        assertEquals(1, ok.placed.size)
    }

    @Test
    fun `a re-read that takes the edge away stops the bet`() = runBlocking {
        val novig = FakeNovig()
        val report = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 60_000))) { result(opp(asOf = now - 1_000, cost = 0.49)) }
        assertEquals(0, report.placed.size)
        assertEquals(0, novig.orders.get())
        assertTrue(report.skipped.keys.toString(), report.skipped.keys.contains("its edge against Pinnacle is under your minimum"))
    }

    @Test
    fun `a fair line with another book in it is never bet`() = runBlocking {
        val novig = FakeNovig()
        val report = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 5_000, books = arrayOf("Pinnacle", "DraftKings")))) { null }
        assertEquals(0, novig.orders.get())
        assertEquals(1, report.skipped["its fair line isn't Pinnacle's alone"])
    }

    @Test
    fun `after the STOP button, or with auto-bet off, or halted, nothing is placed`() = runBlocking {
        val novig = FakeNovig()
        val r = result(opp(asOf = now - 5_000))
        assertTrue(bettor(novig).runPinnacle(settings { it.copy(autoBet = false) }, r) { null }.placed.isEmpty())
        assertTrue(bettor(novig).runPinnacle(settings { it.copy(autoBetHalted = "an answer was lost") }, r) { null }.halted)
        app.container.settingsStore.update { it.copy(killed = true) }
        val killed = bettor(novig).runPinnacle(settings { it.copy(killed = true) }, r) { null }
        assertTrue(killed.placed.isEmpty())
        // Even settings handed in that still say running: the saved switch is asked again.
        val stale = bettor(novig).runPinnacle(settings(), r) { null }
        assertTrue(stale.placed.isEmpty())
        assertEquals(0, novig.orders.get())
        assertFalse(notifications().any { it.extras.getString("android.title")!!.startsWith("Auto-bet \$") })
    }

    @Test
    fun `a STOP pressed while a pass is placing its bets stops the rest - nothing more is sent after the order that was in flight`() = runBlocking {
        val novig = FakeNovig(afterOrder = { app.container.settingsStore.update { it.copy(killed = true) } })
        // Two bets that both pass, in two markets of two games; the first is sent, the STOP lands during it, the second must not be sent.
        val report = bettor(novig).runPinnacle(settings(), result(opp(asOf = now - 5_000), opp(asOf = now - 5_000, second = true))) { null }
        assertEquals(1, novig.orders.get())
        assertEquals(1, report.placed.size)
        assertEquals("the STOP button was pressed while this pass ran", report.stopped)
    }

    @Test
    fun `one bet per Novig market, and the same bet is not bet again`() = runBlocking {
        val novig = FakeNovig()
        val b = bettor(novig)
        assertEquals(1, b.runPinnacle(settings(), result(opp(asOf = now - 5_000))) { null }.placed.size)
        val again = b.runPinnacle(settings(), result(opp(asOf = now - 5_000))) { null }
        assertEquals(0, again.placed.size)
        assertEquals(1, novig.orders.get())
        assertNotNull(again.skipped.keys.firstOrNull { it.contains("already open") })
    }
}
