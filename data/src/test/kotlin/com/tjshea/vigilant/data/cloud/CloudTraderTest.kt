package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ProfitProfile
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTraderTest {
    private val now = 1_800_000_000_000L
    private val start = now + 6 * 3_600_000L

    /** A +EV spread side: fair 0.53 against a price of 0.48 + fee, five books pricing both sides, a sharp one among them. */
    private fun row(id: String, ageMs: Long = 60_000L, live: Boolean = false, price: Double = 0.48, depth: Long = 50_000, starts: Long = start, event: String = "e-$id", at: Long = now): Opportunity {
        val ev = NovigEvent(event, "BASKETBALL", "NBA", if (live) NovigEvent.STATUS_LIVE else NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", starts)
        val market = NovigMarket("m-$id", event, "SPREAD", "OPEN", "Spread", starts, MarketFee.GAME, listOf(NovigOutcome("o-$id", "NYK -3.5", "TBD"), NovigOutcome("x-$id", "BOS +3.5", "TBD")), strike = -3.5)
        fun book(k: String, sharp: Boolean) = BookFair(BookPrices(k, k, listOf(1.78, 2.15), at - ageMs), listOf(0.54, 0.46), 0.03, sharp)
        val fair = FairLine(
            probabilities = listOf(0.53, 0.47), requestedSource = FairSource.BLEND, sourceUsed = FairSource.BLEND, method = DevigMethod.WORST_CASE,
            perBook = listOf(book("pinnacle", true), book("dk", false), book("fd", false), book("mgm", false), book("czr", false)),
            sharpBooksUsed = listOf("pinnacle"), averageBooksUsed = listOf("dk", "fd", "mgm", "czr"),
        )
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = ev, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = fair, fairProbability = 0.53, quote = EvQuote(0.53, price, 0.0075), ladder = listOf(TakeLevel(price, depth)), depth = null,
            suggestedStake = null, novigWidth = null, bookFetchedAtMs = at - 5_000, fairUpdatedMs = at - ageMs, refEvent = null,
            lineKey = LineKey("ref-$id", LineKind.SPREAD, -3.5, 0, null, null), target = OutcomeTarget.Is(Side.HOME), fairAsOfMs = at - ageMs,
        )
    }

    private fun settings(mode: CloudTradeMode = CloudTradeMode.PAPER, f: (ScanSettings) -> ScanSettings = { it }) =
        f(ScanSettings(bankroll = 1_000.0, gates = ApiGates(cloudTrading = mode)))

    private fun trader(port: CloudOrderPort? = null, unavailable: (Opportunity, Long) -> String? = { _, _ -> null }) = CloudTrader(unavailable, port) { now }

    private class FakePort(var cash: Double? = 1_000.0, var answer: (TradeEntry) -> CloudOrderPort.Placed) : CloudOrderPort {
        val sent = ArrayList<Pair<TradeEntry, Double>>()
        var cancelled = 0
        override val blocked: String? = null
        override suspend fun cash() = cash
        override suspend fun place(entry: TradeEntry, limitPrice: Double): CloudOrderPort.Placed { sent += entry to limitPrice; return answer(entry) }
        override suspend fun cancelResting() { cancelled++ }
        var held: List<com.tjshea.vigilant.data.novig.trading.NovigPosition>? = null
        override suspend fun positions() = held
        var reconciled: CloudOrderPort.Placed? = null
        override suspend fun reconcile(entry: TradeEntry) = reconciled
    }

    @Test
    fun `what the whole account holds in a game counts against the game cap, whoever placed it`() = runBlocking {
        val port = FakePort { e -> CloudOrderPort.Placed.Filled(e.contracts, e.avgPrice, 0.0, "o1") }
        port.held = listOf(com.tjshea.vigilant.data.novig.trading.NovigPosition("m-other", "o-other", 300, 150.0))
        val t = trader(port)
        t.describeOutcome = { id -> if (id == "o-other") CloudTrader.OutcomeInfo("e-a", "Boston Celtics @ New York Knicks", start, "Spread", "NYK -7.5") else null }
        val r = t.pass(listOf(row("a")), settings(CloudTradeMode.LIVE), now)
        assertEquals("nothing is ordered into a game the account already has \$150 in (cap 10% of the wallet)", 0, r.placed.size)
        assertTrue(r.line(), r.skipped.keys.any { it.contains("most at risk") })
        // the same pass without that position orders
        port.held = emptyList()
        assertEquals(1, trader(port).pass(listOf(row("a")), settings(CloudTradeMode.LIVE), now).placed.size)
    }

    @Test
    fun `an order Novig answers slowly holds ordering for a minute and a half, then is settled by lookup and never sent again`() = runBlocking {
        var clockMs = now
        val port = FakePort { CloudOrderPort.Placed.Unconfirmed("Novig took the order but had not finished it") }
        val t = CloudTrader({ _, _ -> null }, port) { clockMs }
        val first = t.pass(listOf(row("a")), settings(CloudTradeMode.LIVE), clockMs)
        assertEquals(1, first.placed.size)
        assertEquals("UNCONFIRMED", t.ledger.entries.single().status)
        // 30 s later: still held (the short hold), nothing more is sent
        clockMs = now + 30_000L
        assertTrue(t.pass(listOf(row("b")), settings(CloudTradeMode.LIVE), clockMs).stopped.isNotEmpty())
        assertEquals(1, port.sent.size)
        // two minutes later Novig says the order filled: the pass settles it and ordering is open again (a second line of the same team is the repeat guard's call); the first order was not sent again
        clockMs = now + 120_000L
        port.reconciled = CloudOrderPort.Placed.Filled(60, 0.48, 0.01, "oid-1")
        port.answer = { e -> CloudOrderPort.Placed.Filled(e.contracts, e.avgPrice, 0.0, "oid-2") }
        val later = t.pass(listOf(row("b")), settings(CloudTradeMode.LIVE), clockMs)
        val settled = t.ledger.entries.first()
        assertEquals("OPEN", settled.status)
        assertEquals(60L, settled.contracts)
        assertEquals("oid-1", settled.orderId)
        assertEquals("the first decision was never sent twice", 1, port.sent.count { it.first.id == settled.id })
        assertTrue("ordering is no longer held: ${later.line()}", later.stopped.isEmpty())
    }

    @Test
    fun `with trading off nothing is decided`() = runBlocking {
        val t = trader()
        val r = t.pass(listOf(row("a")), settings(CloudTradeMode.OFF), now)
        assertEquals(0, r.placed.size)
        assertTrue(t.ledger.entries.isEmpty())
    }

    @Test
    fun `paper trading records the order it would have sent, sized by Kelly, and sends nothing`() = runBlocking {
        val t = trader()
        val r = t.pass(listOf(row("a")), settings(), now)
        assertEquals(r.line(), 1, r.placed.size)
        val e = r.placed.single()
        assertEquals("PAPER", e.mode)
        assertEquals("m-a", e.marketId); assertEquals("o-a", e.outcomeId)
        assertTrue(e.cost > 0.0 && e.cost <= 50.0 + 1e-6)             // the 5% per-bet cap of $1000
        assertEquals(e.contracts * 0.48, e.avgPrice * e.contracts, 1e-6)
        assertTrue(e.evPercent > 0.0)
        assertEquals(1, t.ledger.entries.size)
        assertTrue(t.atRisk(now) > 0.0)
    }

    @Test
    fun `the same market is not ordered twice while the first is open, and its game cap holds`() = runBlocking {
        val t = trader()
        t.pass(listOf(row("a")), settings(), now)
        val again = t.pass(listOf(row("a", at = now + 120_000L)), settings(), now + 120_000L)
        assertEquals(0, again.placed.size)
        assertTrue(again.skipped.keys.joinToString(), again.skipped.keys.any { it.contains("already open") })
        assertEquals(1, t.ledger.entries.size)
    }

    @Test
    fun `the rules of the auto-bet keep a live game, old fair odds, an injured player and a game about to start out`() = runBlocking {
        val t = trader(unavailable = { o, _ -> if (o.key.contains("hurt")) "the player is out" else null })
        val r = t.pass(listOf(row("live", live = true), row("old", ageMs = 3 * 3_600_000L), row("hurt"), row("soon", starts = now + 30_000L)), settings(), now)
        assertEquals(r.line(), 0, r.placed.size)
        assertTrue(r.skipped.keys.any { it.contains("pregame") })
        assertTrue(r.skipped.keys.any { it.contains("too old") })
        assertTrue(r.skipped.keys.any { it.contains("player is out") })
    }

    @Test
    fun `a bet that is not plus EV at the price is not ordered`() = runBlocking {
        val r = trader().pass(listOf(row("a", price = 0.56)), settings(), now)
        assertEquals(0, r.placed.size)
    }

    @Test
    fun `the depth of the ladder limits what can be bought`() = runBlocking {
        val r = trader().pass(listOf(row("a", depth = 10)), settings(), now)
        val e = r.placed.single()
        assertEquals(10L, e.contracts)
    }

    @Test
    fun `live trading without a port orders nothing and says why`() = runBlocking {
        val r = trader().pass(listOf(row("a")), settings(CloudTradeMode.LIVE), now)
        assertEquals(0, r.placed.size)
        assertTrue(r.stopped.contains("trading key"))
    }

    @Test
    fun `live orders go through the port under the decision's client id and the fill the port reports is what is recorded`() = runBlocking {
        val port = FakePort { e -> CloudOrderPort.Placed.Filled(e.contracts - 1, 0.485, 0.01, "ord-1") }
        val t = trader(port)
        val r = t.pass(listOf(row("a")), settings(CloudTradeMode.LIVE), now)
        assertEquals(1, port.sent.size)
        val sent = port.sent.single().first
        assertEquals(CloudTrader.clientId(row("a"), now), sent.id)
        val e = r.placed.single()
        assertEquals(sent.contracts - 1, e.contracts)
        assertEquals("ord-1", e.orderId)
        assertEquals(0.485, e.avgPrice, 1e-9)
    }

    @Test
    fun `a missed order is not recorded and not retried at once, a stop ends ordering and is remembered`() = runBlocking {
        val port = FakePort { CloudOrderPort.Placed.Missed("price moved") }
        val t = trader(port)
        val s = settings(CloudTradeMode.LIVE)
        assertEquals(0, t.pass(listOf(row("a")), s, now).placed.size)
        assertEquals(0, t.pass(listOf(row("a", at = now + 30_000L)), s, now + 30_000L).placed.size)
        assertEquals(1, port.sent.size)
        port.answer = { CloudOrderPort.Placed.Stop("GEOLOCATION_NOT_ALLOWED") }
        val r = t.pass(listOf(row("b", at = now + 5 * 60_000L)), s, now + 5 * 60_000L)
        assertEquals("GEOLOCATION_NOT_ALLOWED", r.stopped)
        assertEquals("GEOLOCATION_NOT_ALLOWED", t.ledger.stopped)
        assertEquals(0, t.pass(listOf(row("c", at = now + 6 * 60_000L)), s, now + 6 * 60_000L).placed.size)
        assertEquals(2, port.sent.size)
    }

    @Test
    fun `an unreadable cash balance orders nothing`() = runBlocking {
        val port = FakePort(cash = null) { error("must not be called") }
        val r = trader(port).pass(listOf(row("a")), settings(CloudTradeMode.LIVE), now)
        assertEquals(0, r.placed.size)
        assertTrue(r.stopped.contains("cash"))
    }

    @Test
    fun `the daily loss stop ends the day's trading`() = runBlocking {
        val t = trader()
        t.load(TradeLedger(dayKey = java.time.Instant.ofEpochMilli(now).atZone(CloudTrader.ZONE).toLocalDate().toString(), dayStartEquity = 5_000.0))
        val r = t.pass(listOf(row("a")), settings(), now)
        assertEquals(0, r.placed.size)
        assertTrue(r.skipped.keys.joinToString(), r.skipped.keys.any { it.contains("loss stop") })
    }

    @Test
    fun `entries leave the money at risk three hours after the game starts and are settled by the grader`() = runBlocking {
        val t = trader()
        t.pass(listOf(row("a")), settings(), now)
        val e = t.ledger.entries.single()
        assertTrue(t.atRisk(now) > 0.0)
        assertEquals(0.0, t.atRisk(start + 4 * 3_600_000L), 0.0)
        t.settle(e.id, "WON", 12.0)
        assertEquals("WON", t.ledger.entries.single().status)
        assertEquals(0.0, t.atRisk(now), 0.0)
    }

    @Test
    fun `the ledger survives JSON, and old entries are dropped on load`() {
        val t = trader()
        runBlocking { t.pass(listOf(row("a")), settings(), now) }
        val text = TradeLedger.text(t.ledger)
        val back = TradeLedger.parse(text)!!
        assertEquals(t.ledger, back)
        val old = back.copy(entries = back.entries.map { it.copy(atMs = now - TradeLedger.KEEP_MS - 1) })
        val t2 = trader(); t2.load(old)
        assertTrue(t2.ledger.entries.isEmpty())
        assertFalse(TradeLedger.parse("not json") != null)
    }

    @Test
    fun `the client id is the same within a minute and changes with the rule version's minute`() {
        val a = CloudTrader.clientId(row("a"), now)
        assertEquals(a, CloudTrader.clientId(row("a"), now + 30_000L - (now % 60_000L)))
        assertTrue(a != CloudTrader.clientId(row("a"), now + 60_000L))
        assertNotNull(a)
    }

    @Test
    fun `the end of the window cancels what rests`() = runBlocking {
        val port = FakePort { error("none") }
        trader(port).windowEnded()
        assertEquals(1, port.cancelled)
    }

    @Test
    fun `a pass is journaled with its counts and the reasons bets were held back, so volume can be tuned from data`() {
        val r = CloudTrader.Report(com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE, looked = 31, passed = 1, skipped = mapOf("no Novig price read in the last 3 minutes" to 27), cash = 116.0)
        val row = r.row(1234L)
        org.junit.Assert.assertEquals(listOf("live", 31, 1, 0), listOf(row.mode, row.looked, row.passed, row.ordered))
        org.junit.Assert.assertEquals(27, row.skipped.getValue("no Novig price read in the last 3 minutes"))
        val text = kotlinx.serialization.json.Json.encodeToString(CloudTrader.PassRow.serializer(), row)
        org.junit.Assert.assertEquals(row, kotlinx.serialization.json.Json.decodeFromString(CloudTrader.PassRow.serializer(), text))
    }

    @Test
    fun `a type of bet that lost in Tj's history is never ordered, and only with Profit-proven on`() = runBlocking {
        val prop = row("prop").copy(league = Leagues.byNovigName("NFL")!!, marketLabel = "Player Receiving Yards", selection = "Juwan Johnson Over 39.5")
        val on = settings { it.copy(gates = it.gates.copy(profitProven = true)) }
        assertTrue(trader().profileBlock(prop, on, now)!!.startsWith("Not bet:"))
        assertEquals(null, trader().profileBlock(prop, settings { it.copy(gates = it.gates.copy(profitProven = false)) }, now))
        assertEquals(null, trader().profileBlock(row("spread"), on, now))
        val r = trader().pass(listOf(prop), on, now)
        assertEquals(r.line(), 0, r.placed.size)
        // The winners are not caught: totals (the kinds the profile favours or does not condemn) and team totals pass the type block, at any lead time.
        val pf = ProfitProfile.shipped!!
        for ((kind, sel) in listOf("Game totals" to "Over 220.5", "Game totals" to "Under 220.5", "Team totals" to "Boston Celtics Over 110.5", "Team totals" to "Boston Celtics Under 110.5")) {
            assertEquals("$kind $sel", null, pf.judge(ProfitProfile.Instrument.BET, "NBA", kind, sel, 0.03, 300, typeOnly = true).blocked)
        }
    }
}
