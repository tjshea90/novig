package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MK (Tj, 2026-11-18: "I want to become a parlay maker"): Novig's RFQ socket frames exactly as docs.novig.com/api/rfq/socket gives them, the maker's pricing rule, and the shadow/live behaviour
 * (SHADOW sends nothing; LIVE quotes within the caps, keeps what it quoted, and stops on the faults).
 */
class RfqMakerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val now = 1_790_500_000_000L

    // ---- the documented frames ----
    @Test fun theDocumentedEventsParse() {
        val c = RfqProtocol.parse("""{"ts":1762790400123,"type":"rfq_created","rfqId":"r1","outcomeIds":["o1","o2"],"expiresTs":1762790403123,"minWager":"15"}""") as RfqProtocol.Created
        assertEquals(listOf("o1", "o2"), c.outcomeIds); assertEquals(15.0, c.minWager, 0.0); assertEquals(1762790403123L, c.expiresTs)
        val a = RfqProtocol.parse("""{"ts":1,"type":"quote_accepted","rfqId":"r1","quoteId":"q1","wager":"50","price":"0.45","clientId":"c1"}""") as RfqProtocol.QuoteAccepted
        assertEquals(50.0, a.wager, 0.0); assertEquals(0.45, a.price, 1e-12); assertEquals("c1", a.clientId)
        val x = RfqProtocol.parse("""{"ts":1,"type":"executed","offset":12345,"rfqId":"r1","wager":"50","price":"0.45"}""") as RfqProtocol.Executed
        assertEquals(12345L, x.offset); assertEquals(0.45, x.price, 1e-12)
        assertTrue(RfqProtocol.parse("""{"ts":1,"type":"quote_dropped","rfqId":"r1","quoteId":"q1","reason":"below_min_wager","minWager":"15"}""") is RfqProtocol.QuoteDropped)
        assertTrue(RfqProtocol.parse("""{"ts":1,"type":"quote_unfilled","rfqId":"r1","quoteId":"q1","reason":"maker_insolvent"}""") is RfqProtocol.QuoteUnfilled)
        assertEquals("subscribed", (RfqProtocol.parse("""{"nonce":1,"subscribed":["quotes","rfq"]}""") as RfqProtocol.Ack).kind)
        assertEquals("quoted", (RfqProtocol.parse("""{"nonce":3,"quoted":{"rfqId":"r1"}}""") as RfqProtocol.Ack).kind)
        assertTrue("a type we do not know is not an error", RfqProtocol.parse("""{"ts":1,"type":"something_new"}""") is RfqProtocol.Other)
        assertNull(RfqProtocol.parse("not json")); assertNull(RfqProtocol.parse("[1]"))
        assertEquals("MAKER_LOCKED", (RfqProtocol.parse("""{"nonce":9,"error":{"code":"MAKER_LOCKED","message":"locked"}}""") as RfqProtocol.Refused).code)
        assertEquals("RFQ_CLOSED", (RfqProtocol.parse("""{"nonce":9,"code":"RFQ_CLOSED"}""") as RfqProtocol.Refused).code)
    }

    @Test fun theMessagesWeSendCarryStringDecimalsAndANonce() {
        val q = json.parseToJsonElement(RfqProtocol.quote(3, "r1", 0.4512, 250.0, "c1")).jsonObject
        assertEquals("3", q["nonce"]!!.jsonPrimitive.content)
        val body = q["quote"]!!.jsonObject
        assertEquals("0.4512", body["price"]!!.jsonPrimitive.content); assertTrue(body["price"]!!.jsonPrimitive.isString)
        assertEquals("250.00", body["maxWager"]!!.jsonPrimitive.content); assertEquals("c1", body["clientId"]!!.jsonPrimitive.content)
        assertEquals(listOf("rfq", "quotes"), json.parseToJsonElement(RfqProtocol.subscribe(1, listOf("rfq", "quotes"))).jsonObject["subscribe"]!!.jsonArray.map { it.jsonPrimitive.content })
        val c = json.parseToJsonElement(RfqProtocol.confirm(5, "r1", "q1", true)).jsonObject["confirm"]!!.jsonObject
        assertEquals("true", c["confirmed"]!!.jsonPrimitive.content); assertEquals("q1", c["quoteId"]!!.jsonPrimitive.content)
        assertEquals("r1", json.parseToJsonElement(RfqProtocol.decline(4, "r1")).jsonObject["decline"]!!.jsonObject["rfqId"]!!.jsonPrimitive.content)
    }

    // ---- the pricing rule ----
    private fun leg(id: String, event: String, p: Double, books: Int = 5, ageMs: Long = 60_000L, startsInMs: Long = 3 * 3_600_000L, live: Boolean = false, type: String = "MONEY") =
        LegFair(id, event, "label $id", p, books, now - ageMs, now + startsInMs, live, type)

    private fun fairs(vararg l: LegFair) = LegFairs { id -> l.firstOrNull { it.outcomeId == id } }
    private fun created(vararg ids: String, minWager: Double = 10.0) = RfqProtocol.Created(now, "rfq-1", ids.toList(), now + 3_000L, minWager)
    private val cfg = RfqConfig(mode = RfqConfig.Mode.LIVE, margin = 0.08, minReturn = 0.0, minPoints = 0.0, maxLegs = 3, maxStake = 12.0, maxCollateral = 45.0, maxOpenCollateral = 90.0)   // the old flat-margin rule; the new terms are tested apart

    @Test fun twoLegsFromDifferentGamesArePricedAtTheFairProductPlusTheMargin() {
        val d = RfqPricer.decide(created("a", "b"), now, cfg, fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4)), 0.0) as RfqDecision.Quote
        assertEquals(0.2, d.fair, 1e-12)
        assertEquals("fair 0.20 x 1.08, rounded UP to a ten-thousandth", 0.216, d.price, 1e-12)
        assertEquals(0.016, d.edge, 1e-12)
        // the quote covers the stake the RFQ asked for ($10 here), not the largest the caps allow
        assertEquals(10.0, d.maxWager, 1e-9)
    }

    @Test fun theCollateralCapsShrinkTheStakeAndAStakeUnderTheMinimumIsDeclined() {
        val f = fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4))
        val tight = cfg.copy(maxCollateral = 36.0)             // $36 / 3.63 = $9.9: under the $10 minimum
        assertTrue((RfqPricer.decide(created("a", "b"), now, tight, f, 0.0) as RfqDecision.Decline).reason.contains("asked"))
        val used = RfqPricer.decide(created("a", "b"), now, cfg, f, 80.0)       // $10 of the $90 total left: $2.75 of stake
        assertTrue((used as RfqDecision.Decline).reason.contains("asked"))
        assertTrue("the user's own minimum above $10 counts", (RfqPricer.decide(created("a", "b", minWager = 13.0), now, cfg, f, 0.0) as RfqDecision.Decline).reason.contains("asked"))
    }

    @Test fun everythingItCannotPriceIsDeclinedWithAReason() {
        fun why(c: RfqProtocol.Created, vararg l: LegFair, conf: RfqConfig = cfg) = (RfqPricer.decide(c, now, conf, fairs(*l), 0.0) as RfqDecision.Decline).reason
        assertTrue(why(created("a", "zzz"), leg("a", "e1", 0.5)).contains("no fair line"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5), leg("b", "e1", 0.4)).contains("one game"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5, live = true), leg("b", "e2", 0.4)).contains("in play"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5, startsInMs = 5 * 60_000L), leg("b", "e2", 0.4)).contains("starts within"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5, startsInMs = 9 * 86_400_000L), leg("b", "e2", 0.4)).contains("days away"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5, books = 2), leg("b", "e2", 0.4)).contains("2 companies"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.5, ageMs = 20 * 60_000L), leg("b", "e2", 0.4)).contains("old"))
        assertTrue(why(created("a", "b"), leg("a", "e1", 0.1), leg("b", "e2", 0.1)).contains("outside"))
        assertTrue(why(created("a"), leg("a", "e1", 0.5)).contains("1 legs"))
        assertTrue(why(created("a", "b", "c", "d"), leg("a", "e1", 0.9), leg("b", "e2", 0.9), leg("c", "e3", 0.9), leg("d", "e4", 0.9)).contains("4 legs"))
    }

    // ---- the maker: shadow and live ----
    private class Rig(var cfg: RfqConfig, var legs: List<LegFair>, var collateral: Double = 0.0, var clock: Long = 1_790_500_000_000L) {
        val sent = ArrayList<String>(); val rows = ArrayList<RfqRow>(); val told = ArrayList<String>()
        val maker = RfqMaker({ cfg }, { id -> legs.firstOrNull { it.outcomeId == id } }, { collateral }, { sent += it; true }, { rows += it }, { t, _ -> told += t }, { clock })
    }

    private fun rig(mode: RfqConfig.Mode) = Rig(cfg.copy(mode = mode), listOf(leg("a", "e1", 0.5), leg("b", "e2", 0.4)))

    @Test fun shadowPricesAndJournalsButSendsNothingAndComparesWithTheMarket() {
        val r = rig(RfqConfig.Mode.SHADOW)
        r.maker.onEvent(created("a", "b"))
        assertTrue("SHADOW never sends a frame", r.sent.isEmpty())
        val row = r.rows.single { it.kind == "CREATED" }
        assertTrue(row.data.contains("\"decision\":\"quote\"") && row.data.contains("\"price\":0.216"))
        // the market traded this RFQ at 0.23: our 0.216 would have won, and the market paid 3 points over our fair
        r.maker.onEvent(RfqProtocol.Executed(now, 10, "rfq-1", 40.0, 0.23))
        assertEquals(1L, r.maker.executedSeen)
        assertTrue(r.rows.last().data.contains("\"wouldWin\":true"))
        val s = r.maker.status(true)
        assertTrue(s, s.contains("SHADOW") && s.contains("median +3.00 points") && s.contains("would have won 1"))
        // a trade at 0.20 (below our price) would not have been won
        r.maker.onEvent(created("a", "b").copy(rfqId = "rfq-2"))
        r.maker.onEvent(RfqProtocol.Executed(now, 11, "rfq-2", 25.0, 0.205))
        assertTrue(r.rows.last().data.contains("\"wouldWin\":false"))
    }

    @Test fun offDoesNothing() {
        val r = rig(RfqConfig.Mode.OFF)
        r.maker.onEvent(created("a", "b"))
        assertTrue(r.rows.isEmpty() && r.sent.isEmpty()); assertEquals("RFQ maker: off", r.maker.status(true))
    }

    @Test fun liveQuotesWithinTheCapsAndReservesTheCollateralSoTwoQuotesCannotOvershoot() {
        val r = rig(RfqConfig.Mode.LIVE)
        r.legs = r.legs + listOf(leg("c", "e3", 0.5), leg("d", "e4", 0.4))
        r.maker.onEvent(created("a", "b"))
        val q = json.parseToJsonElement(r.sent.single()).jsonObject["quote"]!!.jsonObject
        assertEquals("0.2160", q["price"]!!.jsonPrimitive.content); assertEquals("10.00", q["maxWager"]!!.jsonPrimitive.content)
        assertEquals(1L, r.maker.quotesSent)
        // $10 x 3.63 = $36.30 reserved each: the second still fits under the $90 total, the third has $17 of room, under the $10 stake's $36 of collateral
        r.maker.onEvent(created("c", "d").copy(rfqId = "rfq-2"))
        r.maker.onEvent(created("a", "b").copy(rfqId = "rfq-3"))
        assertEquals("the third RFQ does not fit under the $90 total and is not quoted", 2, r.sent.size)
        assertTrue(r.maker.inFlight() > 70.0)
    }

    @Test fun aWonQuoteIsConfirmedOnlyIfItIsTheOneWeQuotedAndTheLastLookNeverSecondGuessesTheMarket() {
        val r = rig(RfqConfig.Mode.LIVE)
        r.maker.onEvent(created("a", "b"))
        r.maker.onEvent(created("a", "b").copy(rfqId = "rfq-7"))
        r.sent.clear()
        r.legs = listOf(leg("a", "e1", 0.9), leg("b", "e2", 0.9))                       // the fair moved a lot: still confirmed
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-1", "q1", 10.0, 0.216, "rfq-1"))
        val c = json.parseToJsonElement(r.sent.single()).jsonObject["confirm"]!!.jsonObject
        assertEquals("true", c["confirmed"]!!.jsonPrimitive.content)
        assertEquals(1L, r.maker.confirmed)
        // Novig echoing our 0.216 rounded differently (0.2163) is still our quote: confirmed, not rejected
        r.sent.clear()
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-7", "q7", 10.0, 0.2163, "rfq-7"))
        assertEquals("true", json.parseToJsonElement(r.sent.single()).jsonObject["confirm"]!!.jsonObject["confirmed"]!!.jsonPrimitive.content)
        r.sent.clear()
        // a quote this connection did not make is left alone (another job of the same maker may own it: our "no" would beat its "yes")
        r.sent.clear()
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-X", "q9", 12.0, 0.3, null))
        assertTrue(r.sent.isEmpty())
        // our own quote won at a stake above our maximum (it cannot happen on Novig's side, but the rule is ours): rejected
        r.maker.onEvent(created("a", "b").copy(rfqId = "rfq-5"))
        r.sent.clear()
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-5", "q5", 99.0, 0.216, "rfq-5"))
        assertEquals("false", json.parseToJsonElement(r.sent.single()).jsonObject["confirm"]!!.jsonObject["confirmed"]!!.jsonPrimitive.content)
    }

    @Test fun faultsStopTheQuotingAndTellTj() {
        val r = rig(RfqConfig.Mode.LIVE)
        r.maker.onEvent(RfqProtocol.Refused(now, 7, "MAKER_LOCKED", null))
        assertNotNull(r.maker.halted); assertTrue(r.told.single().contains("stopped quoting"))
        r.maker.onEvent(created("a", "b"))
        assertTrue("halted: priced and journaled, not quoted", r.sent.isEmpty() && r.rows.any { it.kind == "CREATED" })
        r.clock += 6 * 60_000L                                                          // the 5-minute lock has lifted
        r.legs = listOf(LegFair("a", "e1", "label a", 0.5, 5, r.clock - 60_000L, r.clock + 3 * 3_600_000L, false, "MONEY"), LegFair("b", "e2", "label b", 0.4, 5, r.clock - 60_000L, r.clock + 3 * 3_600_000L, false, "SPREAD"))   // fresh fair at the later time
        r.maker.onEvent(RfqProtocol.Created(r.clock, "rfq-9", listOf("a", "b"), r.clock + 3_000L, 10.0))
        assertNull(r.maker.halted); assertEquals(1, r.sent.size)
        r.maker.onEvent(RfqProtocol.QuoteUnfilled(now, "rfq-9", "q", "maker_insolvent"))
        assertNotNull("an insolvent collateral stops it for 30 minutes", r.maker.halted)
        assertFalse(r.maker.status(true).isBlank())
    }

    @Test fun theConfigFileParsesAndDefaultsToOff() {
        assertEquals(RfqConfig.Mode.OFF, RfqConfig().mode)
        val c = RfqConfig.parse("""{"mode":"SHADOW","margin":0.05,"future":1}""")!!
        assertEquals(RfqConfig.Mode.SHADOW, c.mode); assertEquals(0.05, c.margin, 0.0); assertEquals(12.0, c.maxStake, 0.0)
        assertNull(RfqConfig.parse("")); assertNull(RfqConfig.parse("{broken"))
    }

    // ---- the flow is ~60 RFQs a second (first real run, 2026-11-18): the journal samples what cannot be priced, the tally counts all of it ----
    @Test fun theJournalKeepsWhatItCanPriceAndASampleOfTheRestWhileTheTallyCountsEverything() {
        val rows = ArrayList<RfqRow>(); var clock = now
        val legs = listOf(leg("a", "e1", 0.5), leg("b", "e2", 0.4))
        val maker = RfqMaker({ cfg.copy(mode = RfqConfig.Mode.SHADOW) }, { id -> legs.firstOrNull { it.outcomeId == id } }, { 0.0 }, { true }, { rows += it }, { _, _ -> }, { clock },
            legInfo = { id -> if (id.startsWith("s")) "Player Points Over 22.5" else null }, sampleEvery = 10)
        repeat(100) { i -> maker.onEvent(RfqProtocol.Created(clock, "x$i", listOf(if (i % 2 == 0) "s$i" else "u$i", "b"), clock + 3_000L, 10.0)) }
        maker.onEvent(created("a", "b"))                                          // priceable: always kept
        val created = rows.filter { it.kind == "CREATED" }
        assertTrue("about one in ten of the unpriceable, plus the priceable one (${created.size})", created.size in 3..25 && created.any { it.rfqId == "rfq-1" })
        // trades of RFQs we could not price keep their legs for later study, with no fair
        maker.onEvent(RfqProtocol.Executed(clock, 1, "x3", 20.0, 0.31))
        val ex = rows.last { it.kind == "EXECUTED" }
        assertTrue(ex.data, ex.data.contains("\"outcomeIds\"") && ex.data.contains("\"nLegs\":2") && ex.data.contains("unscanned"))
        // five minutes later the tally row says what the flow was
        clock += RfqMaker.TALLY_MS + 1
        maker.onEvent(RfqProtocol.Closed(clock, "x1"))
        val tally = json.parseToJsonElement(rows.single { it.kind == "TALLY" }.data).jsonObject
        assertEquals(101L, tally["created"]!!.jsonPrimitive.content.toLong())
        val classes = tally["legClasses"]!!.jsonObject
        assertEquals(50L, classes["scanned"]!!.jsonPrimitive.content.toLong()); assertEquals(50L, classes["unscanned"]!!.jsonPrimitive.content.toLong())
        assertTrue(tally["scannedKinds"]!!.jsonObject.keys.single() == "Player Points Over #")
        assertEquals(1L, tally["executed"]!!.jsonPrimitive.content.toLong())
    }

    @Test fun aTradeIsMatchedToItsRfqEvenWhenManyOthersArrivedInBetween() {
        val rows = ArrayList<RfqRow>(); val clock = now
        val maker = RfqMaker({ cfg.copy(mode = RfqConfig.Mode.SHADOW) }, { id -> if (id == "a") leg("a", "e1", 0.5) else if (id == "b") leg("b", "e2", 0.4) else null }, { 0.0 }, { true }, { rows += it }, { _, _ -> }, { clock })
        maker.onEvent(created("a", "b"))
        repeat(3_000) { i -> maker.onEvent(RfqProtocol.Created(clock, "n$i", listOf("p$i", "q$i"), clock + 3_000L, 10.0)) }       // 5 seconds of the real flow
        maker.onEvent(RfqProtocol.Executed(clock, 1, "rfq-1", 40.0, 0.23))
        assertTrue("the old 600-entry window had forgotten it", rows.last { it.kind == "EXECUTED" }.data.contains("\"seenHere\":true"))
    }

    // ---- pricing by what the quote ties up (first shadow day, 188 priced trades: a relative margin tied $22,000 of collateral for $50 of EV, a 4% return on collateral $2,700 for $82) ----
    private val roc = RfqConfig(mode = RfqConfig.Mode.LIVE, margin = 0.02, minReturn = 0.04, minPoints = 0.01, maxLegs = 5)

    @Test fun theQuoteAsksAReturnOnTheCollateralAndMoreForLongerLockUpsAndMoreLegs() {
        val h = 3_600_000L
        // fair 0.40, a game in 3 h: (0.40 + 0.04)/1.04 = 0.4231 (2.3 points); the 2% relative floor (0.408) and the 1-point floor (0.41) are lower
        assertEquals(0.4231, RfqPricer.quotePrice(0.40, 2, 3 * h, roc), 1e-9)
        // the same parlay two days out ties the collateral up 2.25 days: r = 9%, (0.40 + 0.09)/1.09 = 0.44954, rounded up to 0.4496
        assertEquals(0.4496, RfqPricer.quotePrice(0.40, 2, 48 * h, roc), 1e-9)
        // a favourite pair at 0.9: the 2% relative floor binds (0.918, 1.8 points); the return term gives 0.9039 and the 1-point floor 0.91
        assertEquals(0.918, RfqPricer.quotePrice(0.90, 2, 3 * h, roc), 1e-9)
        // five legs of a 0.5 parlay: the points floor (4 x 1 point = 0.54) beats the return term (0.5192, 1.9 points)
        assertEquals(0.54, RfqPricer.quotePrice(0.50, 5, 3 * h, roc), 1e-9)
        assertEquals("with no return and no points asked only the relative floor is left", 0.2040, RfqPricer.quotePrice(0.20, 2, 3 * h, roc.copy(minReturn = 0.0, minPoints = 0.0)), 1e-9)
    }

    @Test fun theQuoteCoversTheStakeTheRfqAskedForAndLongshotsThatTieUpTooMuchAreDeclined() {
        val f = fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4))                                  // fair 0.20, price (0.20 + 0.04)/1.04 = 0.2308
        val d = RfqPricer.decide(created("a", "b", minWager = 10.0), now, roc, f, 0.0) as RfqDecision.Quote
        assertEquals(0.2308, d.price, 1e-9); assertEquals(10.0, d.maxWager, 1e-9)
        // $10 x (1 - 0.2308)/0.2308 = $33.3 of collateral: a $25 per-parlay cap cannot cover the stake asked for
        val tight = (RfqPricer.decide(created("a", "b"), now, roc.copy(maxCollateral = 25.0), f, 0.0) as RfqDecision.Decline).reason
        assertTrue(tight, tight.contains("asked"))
        // a stake the RFQ asks for above our stake cap is declined, never cut down
        assertTrue((RfqPricer.decide(created("a", "b", minWager = 25.0), now, roc, f, 0.0) as RfqDecision.Decline).reason.contains("asked"))
        // five legs at 0.8 each (fair 0.328): quoted now that the leg limit is 5; six legs are not
        val five = fairs(*(1..5).map { leg("l$it", "e$it", 0.8) }.toTypedArray())
        assertTrue(RfqPricer.decide(created("l1", "l2", "l3", "l4", "l5"), now, roc, five, 0.0) is RfqDecision.Quote)
        assertTrue((RfqPricer.decide(created("l1", "l2", "l3", "l4", "l5", "l6"), now, roc, five, 0.0) as RfqDecision.Decline).reason.contains("6 legs"))
    }

    // ---- game lines only (the first shadow day: 0.94 points over our fair on game lines, +6.0 points / +38% on parlays with a player prop: our prop fair is the thing in doubt) ----
    @Test fun aParlayWithAPlayerPropIsDeclinedWithNoFairSoItNeverCountsAsEvidenceOfWhatTheMarketPays() {
        val f = fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4, type = "POINTS"))
        val d = RfqPricer.decide(created("a", "b"), now, roc, f, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("not a game line")); assertNull("no fair: the trade is not compared with a fair we do not trust", d.fair)
        val on = RfqPricer.decide(created("a", "b"), now, roc.copy(allowProps = true), f, 0.0)
        assertTrue("allowProps lets the study see them", on is RfqDecision.Quote)
        val unknown = fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4, type = ""))
        assertTrue("a leg whose market type is not known is not a game line", RfqPricer.decide(created("a", "b"), now, roc, unknown, 0.0) is RfqDecision.Decline)
        val halves = fairs(leg("a", "e1", 0.5, type = "SPREAD_1H"), leg("b", "e2", 0.4, type = "TEAM_TOTAL"))
        assertTrue(RfqPricer.decide(created("a", "b"), now, roc, halves, 0.0) is RfqDecision.Quote)
    }
}
