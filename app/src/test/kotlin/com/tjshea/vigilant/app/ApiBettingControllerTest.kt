package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.SecretBox
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private class FakeNovig(
        val orders: AtomicInteger, val slowMs: Long = 0, val wallet: Double = 25.0, val walletMs: Long = 0, val fillsFor: (qty: Long, price: Double) -> NovigFill?,
    ) :
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
        override suspend fun balance(subaccountKeyId: String): Double {
            if (walletMs > 0) kotlinx.coroutines.delay(walletMs)
            return wallet
        }
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = emptyList<NovigOrder>()
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
    fun `a bet placed by hand has no minimum edge and no pause, only Tj's dollar limits`() {
        // Tj, 2026-10-02: "Remove the restriction of minimum bet EV on bet slips in the app, I should be able to bet on whatever I want manually.
        // Only keep the hard restrictions on the auto bet function". Scanning paused: the sheet still plans (the old minimum-edge setting is gone).
        val (o, book) = sample()
        val state = startState().also { st -> st.update { it.copy(settings = it.settings.copy(paused = true)) } }
        val api = controller(state, book, FakeNovig(AtomicInteger()) { _, _ -> null })
        api.bet(o)
        waitFor("a plan or a refusal") { state.value.betSheet?.let { it.plan != null || it.refusal != null } == true }
        assertNull(state.value.betSheet!!.refusal)
        assertTrue(state.value.betSheet!!.plan!!.contracts > 0)
        api.dismiss()
    }

    /** A state whose Novig connection has a subaccount, so the sheet reads the wallet as it opens; [balance] is the one on hand (maybe old). */
    private fun walletState(balance: Double?) = MutableStateFlow(
        SampleScan.state().let { st ->
            st.copy(
                betting = BettingUi(enabled = true, balance = balance),
                novig = st.novig.copy(connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("r", "r", "sub", false, tradingKeyId = "t")),
            )
        },
    )

    /** Tj, 2026-09-30: "if I have less than one dollar in the wallet, it automatically enters whatever is left in the wallet as the bet amount". */
    @Test
    fun `a wallet holding less than the bet amount opens the sheet at what's left, read as the sheet opens`() {
        val (o, book) = sample()
        val state = walletState(balance = 25.0) // the balance on hand is old: the wallet now holds 63 cents
        val api = controller(state, book, FakeNovig(AtomicInteger(), wallet = 0.638) { _, _ -> null })
        api.bet(o)
        waitFor("the wallet's 63 cents as the amount") { state.value.betSheet?.stake == 0.63 }
        assertEquals(0.638, state.value.betting.balance!!, 0.0)
        assertEquals(0.638, state.value.betSheet!!.balance!!, 0.0)
        assertEquals(false, state.value.betSheet!!.stakeChosen)
        // Its price is read for that amount (a plan, or why 63 cents can't buy it).
        waitFor("an answer for 63 cents") { state.value.betSheet?.let { it.stake == 0.63 && (it.plan != null || it.refusal != null) } == true }
        state.value.betSheet!!.plan?.let { assertTrue(it.expectedCost <= 0.63 + 1e-9) }
        // A sheet opened when the balance on hand is already small starts there straight away.
        api.dismiss()
        api.bet(o)
        assertEquals(0.63, state.value.betSheet!!.stake, 0.0)
    }

    @Test
    fun `an amount Tj picks or types is never changed by the wallet read, and a typed one is priced once typing pauses`() {
        val (o, book) = sample()
        val state = walletState(balance = null)
        val api = controller(state, book, FakeNovig(AtomicInteger(), wallet = 0.5, walletMs = 300) { _, _ -> null })
        api.bet(o)
        api.typeStake(3.0)
        api.typeStake(3.5) // the next keystroke
        assertEquals(3.5, state.value.betSheet!!.stake, 0.0)
        assertTrue(state.value.betSheet!!.stakeChosen)
        waitFor("the wallet read") { state.value.betting.balance == 0.5 }
        assertEquals(3.5, state.value.betSheet!!.stake, 0.0)
        waitFor("the plan for \$3.50") { state.value.betSheet?.plan?.let { it.expectedCost <= 3.5 + 1e-9 } == true }
        // Over the limit is held at the limit (the field says so and doesn't send it; this is the guard behind it).
        api.typeStake(500.0)
        assertEquals(state.value.settings.apiMaxStake, state.value.betSheet!!.stake, 0.0)
    }

    /**
     * A plan started while the sheet's first one is still running (an amount typed or picked right after it opened) always answers. The
     * placer is made once under a lock: two plans on two threads once saw it half set up and the second planned nothing (seen in about one
     * run of this class in five, 2026-09-30). This loop exercises that path; it can't force the threads' exact timing, so it doesn't prove
     * the race is gone on its own (the lock in `ApiBettingController.placer` does).
     */
    @Test
    fun `an amount changed while the first price read is running is always priced`() {
        val (o, book) = sample()
        val novig = FakeNovig(AtomicInteger()) { _, _ -> null }
        repeat(40) { i ->
            // A new controller each round: the placer is made on its first plan, where the two plans met.
            val state = startState()
            val api = controller(state, book, novig)
            api.bet(o)
            val stake = if (i % 2 == 0) 1.0 else 2.0
            api.setStake(stake)
            waitFor("a plan for \$$stake (round $i)") { state.value.betSheet?.let { it.stake == stake && (it.plan != null || it.refusal != null) } == true }
            assertNotNull("round $i: ${state.value.betSheet?.refusal}", state.value.betSheet!!.plan)
            api.dismiss()
        }
    }

    /** Tj, 2026-10-01: "Make sure it enters the Kelley value if I select it". */
    @Test
    fun `with Kelly chosen for bet slips the sheet opens at the bet's Kelly stake, and the wallet still has its say`() {
        val (o, book) = sample()
        val kellySettings = SampleScan.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.KELLY, apiMaxStake = 100.0, apiBetStake = 1.0)
        val state = MutableStateFlow(SampleScan.state(kellySettings).copy(betting = BettingUi(enabled = true, balance = 500.0)))
        val api = controller(state, book, FakeNovig(AtomicInteger()) { _, _ -> null })
        val kelly = o.suggestedStake!!
        assertTrue("the sample bet has a Kelly stake over a dollar", kelly > 1.0)
        api.bet(o)
        val sheet = state.value.betSheet!!
        assertEquals(Math.round(kelly * 100) / 100.0, sheet.stake, 1e-9)
        assertTrue(sheet.stakeNote!!, sheet.stakeNote!!.contains("Kelly"))
        waitFor("a plan for the Kelly stake") { state.value.betSheet?.plan != null }
        api.dismiss()
        // A wallet holding less than the Kelly stake starts at what's left.
        state.value = state.value.copy(betting = state.value.betting.copy(balance = 0.75))
        api.bet(o)
        assertEquals(0.75, state.value.betSheet!!.stake, 1e-9)
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

    // ---- the wallet: "Add money" from the sheet and back (Tj, 2026-09-29) ------------------------------------------

    @Test
    fun `a bet the wallet can't cover goes to the wallet with the shortfall typed in, and comes back priced again`() {
        val (o, book) = sample()
        val state = MutableStateFlow(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 0.10)))
        val api = controller(state, book, FakeNovig(AtomicInteger()) { _, _ -> null })
        api.bet(o)
        // The sheet opens at the 10 cents the wallet holds (BetAmount.starting); Tj picks more than that.
        assertEquals(0.10, state.value.betSheet!!.stake, 1e-9)
        api.setStake(state.value.settings.apiBetStake.coerceAtLeast(1.0))
        waitFor("an answer") { state.value.betSheet?.let { it.stakeChosen && (it.plan != null || it.refusal != null) } == true }
        val sheet = state.value.betSheet!!
        assertNotNull("refused: ${sheet.refusal}", sheet.plan)
        val cost = sheet.plan!!.expectedCost
        assertTrue("the sample bet must cost more than the wallet holds", cost > 0.10)
        api.requestTopUp()
        assertNull(state.value.betSheet)
        val top = state.value.betting.topUp!!
        assertEquals(cost - 0.10, top.needed, 1e-9)
        assertEquals(kotlin.math.ceil(cost - 0.10), top.amount, 1e-9)
        assertEquals(cost, top.cost, 1e-9)
        // Money added (the transfer's answer sets the balance), then back to the bet: same bet and amount, priced again.
        state.value = state.value.copy(betting = state.value.betting.copy(balance = 25.0))
        api.backToBet()
        assertNull(state.value.betting.topUp)
        waitFor("the plan again") { state.value.betSheet?.plan != null }
        val again = state.value.betSheet!!
        assertEquals(sheet.stake, again.stake, 1e-9)
        assertEquals(sheet.target!!.outcomeId, again.target!!.outcomeId)
        assertEquals(25.0, again.balance!!, 1e-9)
    }

    @Test
    fun `leaving the wallet without going back lets the bet go`() {
        val (o, book) = sample()
        val state = MutableStateFlow(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 0.10)))
        val api = controller(state, book, FakeNovig(AtomicInteger()) { _, _ -> null })
        api.bet(o)
        waitFor("a plan") { state.value.betSheet?.plan != null }
        api.requestTopUp()
        api.dismissTopUp()
        assertNull(state.value.betting.topUp)
        api.backToBet() // nothing pending: nothing opens
        assertNull(state.value.betSheet)
    }

    // ---- the management key, entered once (Tj, 2026-09-29) --------------------------------------------------------

    /** Stands in for the Keystore (Robolectric has none). */
    private object TestBox : SecretBox {
        override fun seal(plain: String) = "sealed:" + java.util.Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun open(sealed: String) = String(java.util.Base64.getDecoder().decode(sealed.removePrefix("sealed:")))
    }

    /** A real P-256 key made for this test, as the .pem Novig hands out. */
    private fun freshPem(): String {
        val gen = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
        val der = gen.generateKeyPair().private.encoded
        return "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END PRIVATE KEY-----\n" // FAKE
    }

    /** A Novig for the management key's routes: every request's signing key ID and body are kept; [status] other than 200 refuses them all. */
    private class FakeAccount(val status: Int = 200) {
        val keyIds = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            keyIds += req.header("Novig-Key-Id")
            bodies += okio.Buffer().also { b -> req.body?.writeTo(b) }.readUtf8()
            val path = req.url.encodedPath
            val (code, body) = when {
                status != 200 -> status to """{"code":"SIGNATURE_REJECTED","message":"api key not found"}"""
                path == "/v3/echo" -> 200 to """{"hello":"vigilant"}"""
                path.endsWith("/transfer") -> 202 to """{"transferId":"tr1","status":"Applied"}"""
                path.endsWith("/balance") -> 200 to """{"keyId":"sub-1","balance":"37.34000"}"""
                else -> 404 to "{}"
            }
            okhttp3.Response.Builder().request(req).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    private fun accountController(account: FakeAccount): Pair<ApiBettingController, MutableStateFlow<UiState>> {
        app.container.installSecretBoxForTest(TestBox)
        kotlinx.coroutines.runBlocking { app.container.managementKeys.clear() }
        app.container.installBettingSetupForTest(
            com.tjshea.vigilant.data.novig.signing.NovigBettingSetup(account.http, Json { ignoreUnknownKeys = true }, NoVault, "https://api.novig.test", clock = { System.currentTimeMillis() }, pause = { }),
        )
        val conn = com.tjshea.vigilant.data.novig.signing.NovigConnection("read-1", "alias", "sub-1", createdSubaccount = false)
        val state = MutableStateFlow(SampleScan.state().copy(novig = NovigUi(connection = conn), betting = BettingUi(enabled = true, balance = 25.0)))
        return ApiBettingController(app.container, state, scope, toasts, readBook = { null }, clock = { now }) to state
    }

    private object NoVault : com.tjshea.vigilant.data.novig.signing.KeyVault {
        override fun generate(alias: String): String = error("not used")
        override fun signer(alias: String, keyId: String): NovigSigningKey = error("not used")
        override fun delete(alias: String) {}
        override fun aliases(prefix: String): List<String> = emptyList()
    }

    @Test
    fun `a typed management key is saved once Novig accepts it, and the next transfer needs no typing`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        val typed = ManagementKey("mgmt-key-12345678", freshPem())
        api.transfer("fund", 12.34, typed)
        waitFor("the transfer") { state.value.betting.message?.startsWith("Added \$12.34") == true }
        assertTrue(account.bodies.any { it.contains("\"amount\":\"12.34000\"") })
        assertEquals(37.34, state.value.betting.balance!!, 1e-9)
        val saved = kotlinx.coroutines.runBlocking { app.container.managementKeys.load() }
        assertEquals("mgmt-key-12345678", saved!!.keyId)
        assertEquals(typed.pem, saved.pem)
        assertEquals("5678", state.value.novig.managementKey!!.keyIdEnd)
        // Nothing typed this time: the saved key signs it.
        account.keyIds.clear()
        api.transfer("defund", 5.0, typed = null)
        waitFor("the second transfer") { state.value.betting.message?.startsWith("Took back \$5.00") == true }
        assertTrue(account.keyIds.isNotEmpty() && account.keyIds.all { it == "mgmt-key-12345678" })
    }

    // ---- "Add money" inside the Bet sheet (Tj, 2026-10-01) -----------------------------------------------------------

    private fun openSheet(state: MutableStateFlow<UiState>, balance: Double = 0.01) {
        state.update { it.copy(betting = it.betting.copy(balance = balance), betSheet = BetSheetUi("Team A", "Moneyline · B @ A", stake = 0.01, resolving = false, balance = balance)) }
    }

    @Test
    fun `Add money from the Bet sheet sends the amount with the saved key, says what Novig answered, and leaves the sheet open`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        kotlinx.coroutines.runBlocking { app.container.managementKeys.save(ManagementKey("mgmt-key-12345678", freshPem())) }
        openSheet(state)
        api.fundFromSheet(5.0)
        waitFor("the sheet's transfer") { state.value.betSheet?.fundMessage?.startsWith("Added \$5.00") == true }
        assertTrue(account.bodies.any { it.contains("\"amount\":\"5.00000\"") })
        assertTrue(account.keyIds.all { it == "mgmt-key-12345678" })
        val sheet = state.value.betSheet!!
        assertTrue("not still adding", !sheet.funding)
        assertNull(sheet.fundError)
        assertEquals("the wallet shows what Novig now holds", 37.34, state.value.betting.balance!!, 1e-9)
        assertEquals("the bet is still open", "Team A", sheet.title)
    }

    @Test
    fun `after money is added the sheet reads the wallet again, so its balance and the bet's amount follow`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        app.container.installTradingForTest(FakeNovig(AtomicInteger(), wallet = 25.0) { _, _ -> null }, "sub")
        kotlinx.coroutines.runBlocking { app.container.managementKeys.save(ManagementKey("mgmt-key-12345678", freshPem())) }
        // The sheet opened at the wallet's last cent, though the bet's own amount is $5.
        state.update { it.copy(betting = it.betting.copy(balance = 0.01), betSheet = BetSheetUi("Team A", "Moneyline · B @ A", stake = 0.01, resolving = false, balance = 0.01, baseStake = 5.0)) }
        api.fundFromSheet(20.0)
        waitFor("the wallet read after the transfer") { state.value.betSheet?.balance == 25.0 }
        assertEquals("the amount goes back to the bet's own now the wallet covers it", 5.0, state.value.betSheet!!.stake, 1e-9)
    }

    @Test
    fun `Add money from the sheet that Novig refuses says why in the sheet, and a second tap while one is under way does nothing`() {
        val account = FakeAccount(status = 401)
        val (api, state) = accountController(account)
        kotlinx.coroutines.runBlocking { app.container.managementKeys.save(ManagementKey("mgmt-key-12345678", freshPem())) }
        openSheet(state)
        api.fundFromSheet(10.0)
        api.fundFromSheet(10.0)
        waitFor("the refusal") { state.value.betSheet?.fundError != null }
        assertNull(state.value.betSheet!!.fundMessage)
        assertTrue(state.value.betSheet!!.fundError!!.contains("doesn't know that key ID"))
        assertTrue(!state.value.betSheet!!.funding)
        assertEquals("one request, not two", 1, account.keyIds.size)
    }

    @Test
    fun `Add money from a sheet that is placing, or with no sheet, or for nothing, sends nothing`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        kotlinx.coroutines.runBlocking { app.container.managementKeys.save(ManagementKey("mgmt-key-12345678", freshPem())) }
        api.fundFromSheet(5.0) // no sheet
        openSheet(state)
        state.update { it.copy(betSheet = it.betSheet!!.copy(placing = true)) }
        api.fundFromSheet(5.0) // placing
        state.update { it.copy(betSheet = it.betSheet!!.copy(placing = false)) }
        api.fundFromSheet(0.0) // nothing
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(account.keyIds.isEmpty())
        assertTrue(!state.value.betSheet!!.funding)
    }

    @Test
    fun `without a saved key the sheet's Add money goes to Settings with the amount typed in`() {
        val (api, state) = accountController(FakeAccount())
        openSheet(state)
        api.requestTopUp(7.0)
        val top = state.value.betting.topUp!!
        assertEquals(7.0, top.amount, 0.0)
        assertNull("the sheet closes, the bet is kept for Back to the bet", state.value.betSheet)
        assertEquals("Team A", top.bet.title)
        // Without an amount, as before: what the bet is short by, to whole dollars.
        openSheet(state, balance = 0.01)
        state.update { it.copy(betSheet = it.betSheet!!.copy(stake = 2.3)) }
        api.requestTopUp()
        assertEquals(3.0, state.value.betting.topUp!!.amount, 0.0)
    }

    @Test
    fun `a key Novig refuses is never saved`() {
        val (api, state) = accountController(FakeAccount(status = 401))
        api.transfer("fund", 10.0, ManagementKey("mgmt-key-12345678", freshPem()))
        waitFor("the refusal") { state.value.betting.error != null }
        assertTrue(state.value.betting.error!!.contains("doesn't know that key ID"))
        assertNull(kotlinx.coroutines.runBlocking { app.container.managementKeys.load() })
        assertNull(state.value.novig.managementKey)
    }

    @Test
    fun `with nothing typed and nothing saved, it asks for the key and sends nothing`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        api.transfer("fund", 10.0, typed = null)
        waitFor("the ask") { state.value.betting.error != null }
        assertTrue(state.value.betting.error!!.contains("Enter your management key"))
        assertTrue(account.keyIds.isEmpty())
        assertTrue(!state.value.betting.busy)
    }

    @Test
    fun `Save key checks the key with Novig first, and Forget removes it`() {
        val account = FakeAccount()
        val (api, state) = accountController(account)
        api.saveKey(ManagementKey("mgmt-key-12345678", freshPem()))
        waitFor("saved") { state.value.novig.managementKey != null }
        assertEquals(listOf<String?>("mgmt-key-12345678"), account.keyIds.toList()) // one signed echo
        assertTrue(state.value.betting.message!!.contains("saved on this phone"))
        api.forgetKey()
        waitFor("forgotten") { state.value.novig.managementKey == null }
        assertNull(kotlinx.coroutines.runBlocking { app.container.managementKeys.load() })
    }

    @Test
    fun `a saved key Novig now refuses says how to replace it`() {
        val account = FakeAccount(status = 401)
        val (api, state) = accountController(account)
        kotlinx.coroutines.runBlocking { app.container.managementKeys.save(ManagementKey("mgmt-key-12345678", freshPem())) }
        api.transfer("fund", 10.0, typed = null)
        waitFor("the refusal") { state.value.betting.error != null }
        assertTrue(state.value.betting.error!!.endsWith("Tap Replace to enter the key again."))
        // The saved key stays: a refusal isn't proof it's wrong (Tj decides with Replace or Forget).
        assertNotNull(kotlinx.coroutines.runBlocking { app.container.managementKeys.load() })
    }
}
