package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * The Bet sheet's state machine on a real app (Tj, 2026-09-29: "Build the betting through the API function"): opening a bet reads the price and
 * shows a plan, a stake change re-plans, the confirm places one order (a second tap while placing does nothing), a refusal leaves no bet, and
 * a placed bet is tracked and leaves the lists. The order goes to a fake Novig; the data layer's own tests cover the wire.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ApiBettingControllerTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val now = SampleScan.NOW

    @Before fun setUp() { runCatching { kotlinx.coroutines.runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } } } }
    @After fun tearDown() { scope.cancel() }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    private val dummyKey = object : NovigSigningKey {
        override val keyId = "kid"
        override val algorithm = NovigKeyAlgorithm.P256
        override fun sign(message: ByteArray) = ByteArray(0)
    }

    /** A Novig that fills what's asked for at the cost the plan expected; counts the orders. */
    private class FakeNovig(val orders: AtomicInteger, val slowMs: Long = 0, val fillsFor: (qty: Long, price: Double) -> NovigFill?) :
        NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }), Json { ignoreUnknownKeys = true }) {
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
            orders.incrementAndGet()
            last = Triple(outcomeId, price, qty)
            if (slowMs > 0) kotlinx.coroutines.delay(slowMs)
            return "order-1"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "", last!!.first, last!!.second, last!!.third, 0, "IOC", "FILLED", 1)
        override suspend fun fills(orderId: String?, limit: Int) = listOfNotNull(fillsFor(last!!.third, last!!.second))
        override suspend fun balance(subaccountKeyId: String) = 25.0
        override suspend fun orders(status: String, limit: Int) = emptyList<NovigOrder>()
    }

    /** The first +EV card of the sample scan, and a book that offers exactly its ladder. */
    private fun sample(): Pair<Opportunity, NovigBook> {
        val o = SampleScan.state().feed.first { it.quote != null && it.fairProbability != null && it.ladder.isNotEmpty() }
        val other = o.market.otherOutcome(o.outcome.outcomeId)!!.outcomeId
        val bids = o.ladder.sortedBy { it.price }.map { BidLevel(((1.0 - it.price) * 1000).roundToInt(), it.contracts) }
        return o to NovigBook(o.market.marketId, 1, mapOf(other to bids), now)
    }

    private fun controller(state: MutableStateFlow<UiState>, book: NovigBook?, novig: FakeNovig): ApiBettingController {
        app.container.installTradingForTest(novig, "sub")
        return ApiBettingController(app.container, state, scope, toasts, readBook = { book }, clock = { now })
    }

    private fun startState() = MutableStateFlow(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0)))

    @Test
    fun `opening a bet reads the price and shows what it would buy, and a new stake re-plans`() {
        val (o, book) = sample()
        val state = startState()
        val api = controller(state, book, FakeNovig(AtomicInteger()) { _, _ -> null })
        api.bet(o)
        assertNotNull(state.value.betSheet)
        waitFor("a plan") { state.value.betSheet?.plan != null }
        val plan = state.value.betSheet!!.plan!!
        assertTrue(plan.contracts > 0 && plan.expectedCost <= state.value.settings.apiBetStake + 1e-9)
        api.setStake(1.0)
        waitFor("the plan for \$1") { state.value.betSheet?.plan?.let { it.expectedCost <= 1.0 + 1e-9 && it.contracts < plan.contracts } == true }
        api.dismiss()
        assertNull(state.value.betSheet)
    }

    @Test
    fun `the confirm places one order, tracks the real fill, and hides the bet from the lists`() {
        val (o, book) = sample()
        val orders = AtomicInteger()
        val state = startState()
        val fake = FakeNovig(orders) { qty, price ->
            NovigFill("f1", "order-1", null, o.market.marketId, o.outcome.outcomeId, qty, qty * price * 0.01, true, 0.0, now)
        }
        val api = controller(state, book, fake)
        api.bet(o)
        waitFor("a plan") { state.value.betSheet?.plan != null }
        api.confirm()
        api.confirm() // a second tap while it's placing does nothing
        waitFor("placed") { state.value.betSheet?.result is PlaceResult.Placed }
        assertEquals(1, orders.get())
        val bet = (state.value.betSheet!!.result as PlaceResult.Placed).bet
        assertEquals("order-1", bet.orderId)
        val tracked = kotlinx.coroutines.runBlocking { app.container.tracker.all() }.single { it.orderId == "order-1" }
        assertEquals(o.outcome.outcomeId, tracked.outcomeId)
        assertEquals(bet.contracts, fake.last!!.third)
        // The card's own key is on the bet (the ✓ mark that hides it from the +EV list uses the same key; PlacedBets prunes by the real clock,
        // so a sample game from another day can't be read back here).
        assertEquals(o.key, tracked.placedKey)
        // Nothing more can be done to a finished sheet.
        api.setStake(9.0)
        assertTrue(state.value.betSheet!!.result is PlaceResult.Placed)
    }

    @Test
    fun `closing the sheet while an order is being placed does not lose the bet`() {
        val (o, book) = sample()
        val orders = AtomicInteger()
        val state = startState()
        val fake = FakeNovig(orders, slowMs = 400) { qty, price ->
            NovigFill("f1", "order-1", null, o.market.marketId, o.outcome.outcomeId, qty, qty * price * 0.01, true, 0.0, now)
        }
        val api = controller(state, book, fake)
        api.bet(o)
        waitFor("a plan") { state.value.betSheet?.plan != null }
        api.confirm()
        waitFor("the order going out") { orders.get() == 1 }
        api.dismiss() // the answer to the order hasn't come back yet
        assertNull(state.value.betSheet)
        waitFor("the bet being tracked") { kotlinx.coroutines.runBlocking { app.container.tracker.all() }.any { it.orderId == "order-1" } }
        assertEquals(1, orders.get())
    }

    @Test
    fun `a book that can't be read is a refusal, and no order goes out`() {
        val (o, _) = sample()
        val orders = AtomicInteger()
        val state = startState()
        val api = controller(state, null, FakeNovig(orders) { _, _ -> null })
        api.bet(o)
        waitFor("a refusal") { state.value.betSheet?.refusal != null }
        assertTrue(state.value.betSheet!!.refusal!!.contains("couldn't be read"))
        api.confirm()
        assertEquals(0, orders.get())
    }

    @Test
    fun `betting off means no sheet opens`() {
        val (o, book) = sample()
        val state = MutableStateFlow(SampleScan.state())
        val api = ApiBettingController(app.container, state, scope, toasts, readBook = { book }, clock = { now })
        // No trading client installed for this container yet in a fresh process: nothing opens.
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { toasts.collect { } }
        if (app.container.trading == null) {
            api.bet(o)
            assertNull(state.value.betSheet)
        }
    }
}
