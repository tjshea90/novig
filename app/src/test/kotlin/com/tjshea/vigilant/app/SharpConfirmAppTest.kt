package com.tjshea.vigilant.app

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.SharpConfirmText
import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.keys.ApiProvider
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
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import com.tjshea.vigilant.data.diag.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tj, 2026-10-02: "require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … for the cno scanner and auto bet feature". The
 * auto-bet and the alerts on a real container with a fake Novig and fake Pinnacle feeds: what the check is asked, when, and what a no does.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SharpConfirmAppTest {

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

    // ---- the same fake Novig as AutoBettorTest: fills at the plan's price ---------------------------------------------

    private class FakeNovig(val orders: AtomicInteger = AtomicInteger()) :
        NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }), Json { ignoreUnknownKeys = true }) {
        var last: Triple<String, Double, Long>? = null
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            orders.incrementAndGet()
            last = Triple(outcomeId, price, qty)
            return "order-${orders.get()}"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "", last!!.first, last!!.second, last!!.third, 0, "IOC", "FILLED", 1)
        override suspend fun fills(orderId: String?, limit: Int) = last?.let { (outcome, price, qty) ->
            listOf(NovigFill("f-$orderId", orderId ?: "o", null, "mkt", outcome, qty, qty * price * 0.01, true, 0.0, 1_790_400_000_000L))
        }.orEmpty()
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = emptyList<NovigOrder>()
    }

    private val market = NovigMarket("mkt", "ev", "TOTAL", "OPEN", "Player Receiving Yards", now + 86_400_000L, MarketFee.GAME, listOf(NovigOutcome("out-jj", "Under 69.5", "TBD"), NovigOutcome("out-other", "Over 69.5", "TBD")))
    private fun book() = NovigBook("mkt", 1, mapOf("out-other" to listOf(BidLevel(539, 5_000)), "out-jj" to listOf(BidLevel(400, 50))), now)
    private fun targetOf(row: CnoRow) = BetTarget(
        market = market, outcomeId = "out-jj", league = row.league, eventName = row.event, startsTs = row.startsAtMs!!, marketLabel = row.market, selection = row.bet,
        fair = CnoChecks.fairProbability(row)!!, fairAsOfMs = now - 20_000L, source = BetTracker.SOURCE_CNO, placedKey = MiniWindow.cnoKey(row), book = row.book, gameUrl = row.gameUrl, betUrl = row.betUrl,
    )

    private class Asked(val rules: SharpConfirm.Rules, val bet: SharpBooks.Bet, val view: CnoBooksView?, val odds: Int, val live: Boolean)

    private fun bettor(novig: FakeNovig, asked: MutableList<Asked>, answer: (Asked) -> SharpConfirm.Result): AutoBettor {
        val placer = ApiBetPlacer(novig, app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)
        return AutoBettor(
            app, app.container, clock = { now }, placer = { placer }, wallet = { 25.0 }, resolve = { targetOf(it) },
            sharp = { rules, bet, view, odds, live, _ -> Asked(rules, bet, view, odds, live).also { asked += it }.let(answer) },
        )
    }

    private fun settings(f: (ScanSettings) -> ScanSettings = { it }) = f(
        ScanSettings(
            autoBet = true, autoScan = AutoScanMode.CNO, autoBetBooks = 3, autoBetMinEv = 0.025, autoBetTwoSided = 2, autoBetStake = AutoBetStake.ONE_DOLLAR, autoBetMaxStake = 10.0,
            apiMaxPerDay = 50.0, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, trapEarlyHours = 0,
        ),
    )

    private fun state(s: ScanSettings = settings()): UiState {
        val base = SampleCno.withBooks(SampleCno.state(SampleScan.state().copy(settings = s.copy(cnoLivePrices = true))))
        return base.copy(novigLive = mapOf(jefferson.key to LivePrice(117, 88.0, 0.0584, now - 5_000, "mkt", "out-jj"))).indexed(now)
    }

    private val yes = SharpConfirm.Result(SharpConfirm.Verdict.CONFIRMED, detail = "Pinnacle +3.3% (devigged, 40 sec old, via PinnWire)")
    private fun no(reason: String = "Pinnacle's own devigged price doesn't show it +EV at Novig's price") = SharpConfirm.Result(SharpConfirm.Verdict.NOT_CONFIRMED, reason = reason, detail = "Pinnacle -1.0%")

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    // ---- the auto-bet ------------------------------------------------------------------------------------------------

    @Test
    fun `a bet the sharp book confirms is placed, and its pop-up names the sharp book`() = runBlocking {
        val novig = FakeNovig()
        val asked = ArrayList<Asked>()
        val report = bettor(novig, asked) { yes }.run(settings(), state())
        assertEquals(1, report.placed.size)
        assertEquals(1, novig.orders.get())
        // Asked once, with the bet as CNO names it, Novig's price now, pregame, and CNO's game page for it (its free first look).
        val q = asked.single()
        assertEquals(SharpBooks.Bet("NFL", "Minnesota Vikings @ Tampa Bay Buccaneers", jefferson.startsAtMs, "Player Receiving Yards", "Justin Jefferson Under 69.5"), q.bet)
        assertEquals(117, q.odds)
        assertFalse(q.live)
        assertEquals(setOf("PN"), q.rules.codes)
        assertTrue(q.view!!.prices.any { it.code == "PN" })
        val text = notifications().single { it.extras.getString("android.title")!!.startsWith("Auto-bet") }.extras.getString("android.text")!!
        assertTrue(text, text.contains("3 of 3 books agree") && text.contains(" · sharp: Pinnacle +3.3% (devigged, 40 sec old, via PinnWire)"))
    }

    @Test
    fun `a bet the sharp book doesn't confirm is not placed, and the report says why in one general sentence`() = runBlocking {
        val novig = FakeNovig()
        val asked = ArrayList<Asked>()
        val report = bettor(novig, asked) { no() }.run(settings(), state())
        assertEquals(0, report.placed.size)
        assertEquals(0, novig.orders.get())
        assertEquals(1, report.skipped["Pinnacle's own devigged price doesn't show it +EV at Novig's price"])
        // Every verdict that isn't a yes skips: no price, stale, nothing to ask.
        for (v in listOf(SharpConfirm.Verdict.STALE, SharpConfirm.Verdict.NO_QUOTE, SharpConfirm.Verdict.UNAVAILABLE)) {
            val r = bettor(novig, ArrayList()) { SharpConfirm.Result(v, reason = "because $v") }.run(settings(), state())
            assertEquals(v.name, mapOf("because $v" to 1), r.skipped.filterKeys { it.startsWith("because") })
            assertEquals(0, r.placed.size)
        }
        assertEquals(0, novig.orders.get())
    }

    @Test
    fun `Settings counts what the sharp check said about each bet, so no volume says why`() = runBlocking {
        // Tj, 2026-10-02 16:05Z: "I'm getting no volume so far on auto bet with the option for each bet to be verified positive EV by a sharp book. Is this
        // working correctly? Is it getting sharp book pricing?" The tally is each bet's latest verdict since the app started.
        val b = bettor(FakeNovig(), ArrayList()) { SharpConfirm.Result(SharpConfirm.Verdict.NO_QUOTE, reason = "no Pinnacle price for both sides of this exact bet") }
        assertNull(AutoBettor.sharpLine(b.status.value, "Pinnacle"))
        b.run(settings(), state())
        assertEquals(mapOf("confirm.NO_QUOTE" to 1), b.status.value.sharp)
        assertEquals(
            "Sharp check since Vigilant started: 1 bet asked about, 0 confirmed, 1 with no Pinnacle price for that exact line.",
            AutoBettor.sharpLine(b.status.value, "Pinnacle"),
        )
        // The same bet asked again counts once, at its newest verdict.
        b.run(settings(), state())
        assertEquals(mapOf("confirm.NO_QUOTE" to 1), b.status.value.sharp)
        val all = AutoBettor.Status(sharp = SharpConfirm.Verdict.entries.associate { "confirm.${it.name}" to 2 })
        assertEquals(
            "Sharp check since Vigilant started: 10 bets asked about, 2 confirmed, 2 with no Pinnacle price for that exact line, 2 that Pinnacle's own price " +
                "doesn't show +EV (enough), 2 with Pinnacle's price too old, 2 when no feed could be asked.",
            AutoBettor.sharpLine(all, "Pinnacle"),
        )
    }

    @Test
    fun `a check that throws is a skip, never a bet`() = runBlocking {
        val novig = FakeNovig()
        val report = bettor(novig, ArrayList()) { error("feed exploded") }.run(settings(), state())
        assertEquals(0, report.placed.size)
        assertEquals(0, novig.orders.get())
        assertTrue(report.skipped.keys.toString(), report.skipped.keys.any { it.startsWith("couldn't get a fresh Pinnacle price (feed exploded)") })
    }

    @Test
    fun `switched off, the check is never asked, and it is asked last so a bet that fails another criterion costs no feed call`() = runBlocking {
        val novig = FakeNovig()
        val asked = ArrayList<Asked>()
        val off = settings { it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF) }
        val offReport = bettor(novig, asked) { error("must not be asked") }.run(off, state(off))
        assertEquals(offReport.skipped.toString(), 1, offReport.placed.size)
        assertTrue(asked.isEmpty())
        // Books agreeing 5 (the bet has 3), longest odds under +117: each fails first, so the sharp check is never reached.
        for (strict in listOf(settings { it.copy(autoBetBooks = 5) }, settings { it.copy(autoBetMaxOdds = 110) })) {
            val r = bettor(FakeNovig(), asked) { yes }.run(strict, state(strict))
            assertEquals(0, r.placed.size)
        }
        assertTrue("never asked for a bet that already failed: $asked", asked.isEmpty())
        // The alerts' switch is another switch: it doesn't make the auto-bet ask.
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        val alertsOnly = settings { it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF, sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) }
        val alertsReport = bettor(FakeNovig(), asked) { error("must not be asked") }.run(alertsOnly, state(alertsOnly))
        assertEquals(alertsReport.skipped.toString(), 1, alertsReport.placed.size)
    }

    @Test
    fun `the real check with a Pinnacle quote that shows +EV places the bet, an old one or a no doesn't, and CNO's page vetoes for free`() = runBlocking {
        val pick = com.tjshea.vigilant.data.tracker.BetGrader.pickOf("Player Receiving Yards", "Justin Jefferson Under 69.5") as com.tjshea.vigilant.data.tracker.BetGrader.Pick.Prop
        var calls = 0
        fun feed(over: Double, under: Double, ageMs: Long) = object : ReferenceSource {
            override val id = "pinnacle"
            override val displayName = "PinnWire"
            override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
                calls++
                val market = RefBookMarket(
                    "pinnacle", "Pinnacle", LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, over, 69.5), RefQuote(Side.UNDER, under, 69.5)), now - ageMs, subject = "Justin Jefferson", stat = pick.stat,
                )
                return RefSnapshot("americanfootball_nfl", listOf(RefEvent("g", "americanfootball_nfl", jefferson.startsAtMs!!, "Tampa Bay Buccaneers", "Minnesota Vikings", listOf(market))), now - 1_000L, provider = id)
            }
        }
        fun bettorWith(feed: ReferenceSource): AutoBettor {
            val sharp = SharpBooks(sources = { listOf(feed) }, settings = { ScanSettings() }, clock = { now })
            val placer = ApiBetPlacer(FakeNovig(), app.container.tracker, books = { book() }, limits = { BetLimits(10.0, 50.0, 0.01) }, clock = { now }, pause = { }, lock = app.container.orderLock)
            return AutoBettor(app, app.container, clock = { now }, placer = { placer }, wallet = { 25.0 }, resolve = { targetOf(it) },
                sharp = { rules, bet, view, odds, live, at -> SharpGate.check(sharp, rules, bet, view, odds, live, at) })
        }
        // Pinnacle: Under 2.10 / Over 1.90: fair about 47.5%, +3% at Novig's +117 (a fresh quote).
        val ok = bettorWith(feed(over = 1.90, under = 2.10, ageMs = 30_000L)).run(settings { it.copy(sharpConfirmViaCno = false) }, state())
        assertEquals(ok.skipped.toString(), 1, ok.placed.size)
        assertEquals(1, calls)
        // The same quote 4 minutes old: past the 3-minute limit, so not bet.
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        val stale = bettorWith(feed(over = 1.90, under = 2.10, ageMs = 240_000L)).run(settings(), state())
        assertEquals(0, stale.placed.size)
        assertEquals(stale.skipped.toString(), 1, stale.skipped["Pinnacle's price for it is older than 3 min (or has no time)"])
        // CNO's own page has Pinnacle at +100/−122 (fair about 47.6%): +EV at Novig's +117. Make CNO's Pinnacle say no (Under +140, Over −170: fair about 40%):
        // the gate vetoes it before any feed is asked.
        val sharp = SharpBooks(sources = { listOf(feed(over = 1.90, under = 2.10, ageMs = 30_000L)) }, settings = { ScanSettings() }, clock = { now })
        val bet = SharpBooks.Bet("NFL", "Minnesota Vikings @ Tampa Bay Buccaneers", jefferson.startsAtMs, "Player Receiving Yards", "Justin Jefferson Under 69.5")
        val noView = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "PN") CnoBookPrice("PN", 140, null, -170, null) else it }) }
        val rules = SharpConfirm.rules(settings(), autoBet = true)!!
        calls = 0
        val veto = SharpGate.check(sharp, rules, bet, noView, 117, false, now)
        assertEquals(SharpConfirm.Verdict.NOT_CONFIRMED, veto.verdict)
        assertEquals("a CNO veto costs no feed call", 0, calls)
        // CNO's page saying yes does not confirm on its own (a feed with the quote's own time is asked) unless Settings let the page confirm.
        val yesView = SampleCno.jeffersonBooks()
        assertEquals(SharpConfirm.Verdict.CONFIRMED, SharpGate.check(sharp, rules, bet, yesView, 117, false, now).verdict)
        assertEquals("the feed was asked", 1, calls)
        calls = 0
        val viaCno = rules.copy(viaCno = true)
        val byPage = SharpGate.check(SharpBooks(sources = { listOf(feed(over = 1.90, under = 2.10, ageMs = 30_000L)) }, settings = { ScanSettings() }, clock = { now }), viaCno, bet, yesView, 117, false, now)
        assertEquals(SharpConfirm.Verdict.CONFIRMED, byPage.verdict)
        assertEquals("CNO's page confirmed it for free", 0, calls)
        assertEquals("CNO's page", byPage.judged.single().quote.via)
        // A page that is too old to count falls through to the feed.
        val oldView = yesView.copy(fetchedAtMs = now - 400_000L)
        assertEquals(SharpConfirm.Verdict.CONFIRMED, SharpGate.check(SharpBooks(sources = { listOf(feed(over = 1.90, under = 2.10, ageMs = 30_000L)) }, settings = { ScanSettings() }, clock = { now }), viaCno, bet, oldView, 117, false, now).verdict)
        assertEquals(1, calls)
    }

    // ---- the alerts ---------------------------------------------------------------------------------------------------

    @Test
    fun `an alert is sent only if its bet is confirmed, one already sent isn't asked again, and a bet with no verdict or a failed check is dropped`() = runBlocking {
        val s = settings { it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF, sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, alertMinEv = 0.03) }
        val st = state(s)
        val items = AlertPicks.cnoChecked(st, s.alertMinEv, now)
        val alerts = AlertPicks.cno(st, s.alertMinEv, now)
        assertTrue(alerts.isNotEmpty())
        val asked = ArrayList<String>()
        // Everything confirmed: all stay.
        val kept = SharpGate.confirmedAlerts(alerts, unseen = alerts, items = items) { asked += it.pick.row.bet; yes }
        assertEquals(alerts.map { it.key }, kept.map { it.key })
        // None confirmed: none stay.
        assertTrue(SharpGate.confirmedAlerts(alerts, unseen = alerts, items = items) { no() }.isEmpty())
        // Already sent (not in unseen): kept without asking.
        asked.clear()
        assertEquals(alerts.size, SharpGate.confirmedAlerts(alerts, unseen = emptyList(), items = items) { asked += it.pick.row.bet; no() }.size)
        assertTrue(asked.isEmpty())
        // A check that throws drops that alert only; an alert with no candidate behind it is dropped.
        val first = alerts.first().key
        val some = SharpGate.confirmedAlerts(alerts, unseen = alerts, items = items) { if (MiniWindow.cnoKey(it.pick.row) == first) error("boom") else yes }
        assertEquals(alerts.map { it.key }.filter { it != first }, some.map { it.key })
        assertTrue(SharpGate.confirmedAlerts(alerts, unseen = alerts, items = emptyList()) { yes }.isEmpty())
    }

    @Test
    fun `the veto drops an alert only when the sharpest book for its kind says no`() = runBlocking {
        // Tj, 2026-10-02 17:01Z: the veto, for CNO's push alerts too (their default).
        val s = settings { it.copy(sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.VETO, alertMinEv = 0.03) }
        val st = state(s)
        val items = AlertPicks.cnoChecked(st, s.alertMinEv, now)
        val alerts = AlertPicks.cno(st, s.alertMinEv, now)
        assertTrue(alerts.isNotEmpty())
        val verdicts = ArrayList<com.tjshea.vigilant.data.scanner.SharpVeto.Verdict>()
        // The sample's books: Kalshi (the sharpest for props) says +EV: every alert stays.
        assertEquals(alerts.map { it.key }, SharpGate.unvetoedAlerts(alerts, items, { st.booksAt(it.pick.row.key, now)?.view }, 0.0) { verdicts += it.verdict }.map { it.key })
        assertTrue(verdicts.toString(), verdicts.isNotEmpty() && verdicts.all { it == com.tjshea.vigilant.data.scanner.SharpVeto.Verdict.PASSED })
        // Kalshi says no: that alert goes; Pinnacle saying no wouldn't (not a prop sharp).
        val kalshiNo = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "KI") com.tjshea.vigilant.data.cno.CnoBookPrice("KI", 105, 106.0, -135, 13_662.0) else it }) }
        val jj = MiniWindow.cnoKey(jefferson)
        assertFalse(SharpGate.unvetoedAlerts(alerts, items, { if (MiniWindow.cnoKey(it.pick.row) == jj) kalshiNo else st.booksAt(it.pick.row.key, now)?.view }, 0.0).any { it.key == jj })
        val pinnacleNo = SampleCno.jeffersonBooks().let { v -> v.copy(prices = v.prices.map { if (it.code == "PN") com.tjshea.vigilant.data.cno.CnoBookPrice("PN", 105, null, -135, null) else it }) }
        assertTrue(SharpGate.unvetoedAlerts(alerts, items, { if (MiniWindow.cnoKey(it.pick.row) == jj) pinnacleNo else st.booksAt(it.pick.row.key, now)?.view }, 0.0).any { it.key == jj })
        // No page, no veto.
        assertEquals(alerts.size, SharpGate.unvetoedAlerts(alerts, items, { null }, 0.0).size)
    }

    // ---- Settings words ------------------------------------------------------------------------------------------------

    @Test
    fun `Settings names the feeds that can confirm, and says plainly when none is on`() {
        val s = ScanSettings(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM)
        fun keys(vararg on: ApiProvider) = { p: ApiProvider -> if (p in on) 1 else 0 }
        assertEquals(emptyList<String>(), SharpConfirmText.feedsOn(s, keys()))
        assertEquals(listOf("PinnWire / pinnapi", "ParlayAPI"), SharpConfirmText.feedsOn(s, keys(ApiProvider.PINNWIRE, ApiProvider.PARLAY)))
        assertEquals(listOf("PinnWire / pinnapi"), SharpConfirmText.feedsOn(s, keys(ApiProvider.PINNAPI)))
        // Switched off in Settings: the key alone doesn't make it a feed.
        assertEquals(emptyList<String>(), SharpConfirmText.feedsOn(s.copy(usePinnacle = false), keys(ApiProvider.PINNWIRE)))
        assertEquals(listOf("PropLine", "The Odds API"), SharpConfirmText.feedsOn(s.copy(usePropLine = true, useOddsApi = true), keys(ApiProvider.PROPLINE, ApiProvider.THE_ODDS_API)))
        assertTrue(SharpConfirmText.feedsNote(s, emptyList()).startsWith("No Pinnacle feed is on with a key"))
        assertTrue(SharpConfirmText.feedsNote(s, emptyList()).contains("the auto-bet skips every bet and no CNO alert is sent"))
        assertEquals("No Pinnacle feed is on with a key: only CNO's page can confirm.", SharpConfirmText.feedsNote(s.copy(sharpConfirmViaCno = true), emptyList()))
        assertTrue(SharpConfirmText.feedsNote(s, listOf("PinnWire / pinnapi", "ParlayAPI")).startsWith("Asked in this order, and the first that has the bet answers: PinnWire / pinnapi, ParlayAPI."))
        assertNull(SharpConfirmText.confirmNote(ScanSettings()))
        assertEquals(
            "For the auto-bet and CNO's push alerts: Pinnacle's own price, at most 3 min old, must show any +EV at Novig's price now.",
            SharpConfirmText.confirmNote(ScanSettings(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM)),
        )
        assertEquals("For the auto-bet: Pinnacle or Circa's own price, at most 1 min old, must show +2% at Novig's price now.",
            SharpConfirmText.confirmNote(ScanSettings(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, sharpConfirmBooks = com.tjshea.vigilant.data.scanner.SharpBookChoice.PINNACLE_CIRCA, sharpConfirmMaxAgeSeconds = 60, sharpConfirmMinEv = 0.02)))
        assertTrue(SharpConfirmText.viaCnoNote(ScanSettings()).startsWith("Off: only a Pinnacle feed"))
        assertTrue(SharpConfirmText.viaCnoNote(ScanSettings(sharpConfirmViaCno = true)).startsWith("On: Pinnacle's column on CNO's game page can confirm a bet too"))
    }

    /** A background cycle needs CNO and a clock to run for real: this pins that its alerts go through the sharp check, only when Settings switch it on. */
    @Test
    fun `the background cycle's CNO alerts go through the sharp check only when the alerts switch is on`() {
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        val alerts = src.substringAfter("private suspend fun cnoAlerts(s: ScanSettings): List<EvAlert> {").substringBefore("private suspend fun vigilantScan")
        assertTrue(alerts, alerts.contains("val rules = com.tjshea.vigilant.data.scanner.SharpConfirm.rules(s, autoBet = false) ?: return alerts"))
        // The veto (the default) runs first and returns: a confirmation is only asked in that mode.
        assertTrue(alerts, alerts.contains("if (s.sharpAlerts == com.tjshea.vigilant.data.scanner.SharpMode.VETO) {\n            return SharpGate.unvetoedAlerts("))
        assertTrue(alerts.indexOf("SharpGate.unvetoedAlerts(") < alerts.indexOf("SharpConfirm.rules"))
        assertTrue(alerts, alerts.contains("return SharpGate.confirmedAlerts(alerts, c.alertLog.unseen(alerts), AlertPicks.cnoChecked(state, s.alertMinEv, now))"))
        // Judged by the books first, then by the sharp one: the sharp check only sees alerts the books already confirmed.
        assertTrue(alerts.indexOf("AlertPicks.cno(state") < alerts.indexOf("SharpConfirm.rules"))
        // Vigilant's own alerts are not asked: its fair odds already come from the sharp books.
        assertFalse(src.substringAfter("private suspend fun vigilantScan").substringBefore("private suspend fun send").contains("SharpGate"))
        // And the auto-bet's check comes after every other criterion, before the stake.
        val bettor = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoBettor.kt").readText()
        assertTrue(bettor.indexOf("AutoBet.judge(rules, item.shown.ev") < bettor.indexOf("?: sharpReason(item, settings, state, now)"))
        assertTrue(bettor.indexOf("?: sharpReason(item, settings, state, now)") < bettor.indexOf("AutoBet.stake(rules, item.shown.row"))
    }

    // ---- the flight recorder sees every run (Tj, 2026-10-02) ---------------------------------------------------------------

    @Test
    fun `a run is written to the flight recorder with the funnel in counters, why bets weren't placed, and an event for each bet placed`() = runBlocking {
        val log = app.container.eventLog
        val before = log.counters()
        bettor(FakeNovig(), ArrayList()) { yes }.run(settings(), state())
        var c = log.counters()
        fun delta(k: String) = (c[k] ?: 0L) - (before[k] ?: 0L)
        assertEquals(1L, delta("autobet.runs"))
        assertEquals(1L, delta("autobet.looked"))
        assertEquals(1L, delta("autobet.passed"))
        assertEquals(1L, delta("autobet.placed"))
        assertEquals(1L, delta("sharp.autobet.CONFIRMED"))
        assertTrue(log.events().any { it.cat == "AUTOBET" && it.msg.startsWith("placed Justin Jefferson Under 69.5 \$1.00 at +117") })
        // A no: counted by its reason, nothing placed.
        app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
        bettor(FakeNovig(), ArrayList()) { no() }.run(settings(), state())
        c = log.counters()
        assertEquals(1L, delta("sharp.autobet.NOT_CONFIRMED"))
        assertEquals(1L, delta("autobet.skip.Pinnacle's own devigged price doesn't show it +EV at Novig's price"))
        assertEquals(1L, delta("autobet.placed"))
        // An unavailable check is a warning event too.
        bettor(FakeNovig(), ArrayList()) { SharpConfirm.Result(SharpConfirm.Verdict.UNAVAILABLE, reason = "couldn't get a fresh Pinnacle price (credits held back)") }.run(settings(), state())
        assertTrue(log.events().any { it.cat == "SHARP" && it.msg.contains("credits held back") })
    }

    @Test
    fun `what a run counts, and what it says when it stopped or couldn't start`() = runBlocking {
        val log = app.container.eventLog
        val b = bettor(FakeNovig(), ArrayList()) { yes }
        val before = log.counters()
        val eventsBefore = log.events().size
        fun delta(k: String) = (log.counters()[k] ?: 0L) - (before[k] ?: 0L)
        // A run that looked at nothing is not a run in the funnel (a cycle every 5 s would swamp the numbers), and "off" is not news.
        b.record(AutoBettor.Report(), "Auto-bet is off")
        b.record(AutoBettor.Report(), null)
        assertEquals(0L, delta("autobet.runs"))
        assertEquals(eventsBefore, log.events().size)
        // A run that looked: counted, with each skip reason by how many it skipped for it.
        b.record(AutoBettor.Report(looked = 5, passed = 2, skipped = mapOf("its edge is under your minimum" to 3, "tried a moment ago" to 1)), null)
        assertEquals(1L, delta("autobet.runs"))
        assertEquals(5L, delta("autobet.looked"))
        assertEquals(2L, delta("autobet.passed"))
        assertEquals(3L, delta("autobet.skip.its edge is under your minimum"))
        assertEquals(1L, delta("autobet.skip.tried a moment ago"))
        // A stop is a warning with its reason, but "placed ..." is how a good run ends and isn't one.
        b.record(AutoBettor.Report(looked = 1, stopped = "Novig refused the bet (HTTP 429)"), null)
        b.record(AutoBettor.Report(looked = 1, stopped = "placed 3 bets"), null)
        val warns = log.events().drop(eventsBefore).filter { it.cat == "AUTOBET" }
        assertEquals(warns.toString(), listOf("stopped: Novig refused the bet (HTTP 429)"), warns.map { it.msg })
        assertEquals(Level.WARN, warns.single().level)
        // A reason it can't place bets at all is a warning, too.
        b.record(AutoBettor.Report(halted = true), "stopped: a bet failed (Settings › Betting › Resume auto-bet)")
        assertTrue(log.events().last().msg, log.events().last().msg == "can't place bets: stopped: a bet failed (Settings › Betting › Resume auto-bet)")
    }
}
