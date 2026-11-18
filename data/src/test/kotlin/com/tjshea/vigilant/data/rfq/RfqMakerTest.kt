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
    private fun leg(id: String, event: String, p: Double, books: Int = 5, ageMs: Long = 60_000L, startsInMs: Long = 3 * 3_600_000L, live: Boolean = false) =
        LegFair(id, event, "label $id", p, books, now - ageMs, now + startsInMs, live)

    private fun fairs(vararg l: LegFair) = LegFairs { id -> l.firstOrNull { it.outcomeId == id } }
    private fun created(vararg ids: String, minWager: Double = 10.0) = RfqProtocol.Created(now, "rfq-1", ids.toList(), now + 3_000L, minWager)
    private val cfg = RfqConfig(mode = RfqConfig.Mode.LIVE, margin = 0.08, maxStake = 12.0, maxCollateral = 45.0, maxOpenCollateral = 90.0)

    @Test fun twoLegsFromDifferentGamesArePricedAtTheFairProductPlusTheMargin() {
        val d = RfqPricer.decide(created("a", "b"), now, cfg, fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4)), 0.0) as RfqDecision.Quote
        assertEquals(0.2, d.fair, 1e-12)
        assertEquals("fair 0.20 x 1.08, rounded UP to a ten-thousandth", 0.216, d.price, 1e-12)
        assertEquals(0.016, d.edge, 1e-12)
        // collateral for a stake w is w (1-P)/P = w x 3.63: the $45 cap allows $12.39, the $12 stake cap binds
        assertEquals(12.0, d.maxWager, 1e-9)
    }

    @Test fun theCollateralCapsShrinkTheStakeAndAStakeUnderTheMinimumIsDeclined() {
        val f = fairs(leg("a", "e1", 0.5), leg("b", "e2", 0.4))
        val tight = cfg.copy(maxCollateral = 36.0)             // $36 / 3.63 = $9.9: under the $10 minimum
        assertTrue((RfqPricer.decide(created("a", "b"), now, tight, f, 0.0) as RfqDecision.Decline).reason.contains("under the minimum"))
        val used = RfqPricer.decide(created("a", "b"), now, cfg, f, 80.0)       // $10 of the $90 total left: $2.75 of stake
        assertTrue((used as RfqDecision.Decline).reason.contains("under the minimum"))
        assertTrue("the user's own minimum above $10 counts", (RfqPricer.decide(created("a", "b", minWager = 13.0), now, cfg, f, 0.0) as RfqDecision.Decline).reason.contains("minimum"))
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
        assertEquals("0.2160", q["price"]!!.jsonPrimitive.content); assertEquals("12.00", q["maxWager"]!!.jsonPrimitive.content)
        assertEquals(1L, r.maker.quotesSent)
        // $12 x 3.63 = $43.56 reserved: a second quote sees $46.44 of the $90 left and still fits; a third and fourth are cut back, then declined
        r.maker.onEvent(created("c", "d").copy(rfqId = "rfq-2"))
        r.maker.onEvent(created("a", "b").copy(rfqId = "rfq-3"))
        assertEquals("the third RFQ does not fit under the $90 total and is not quoted", 2, r.sent.size)
        assertTrue(r.maker.inFlight() > 80.0)
    }

    @Test fun aWonQuoteIsConfirmedOnlyIfItIsTheOneWeQuotedAndTheLastLookNeverSecondGuessesTheMarket() {
        val r = rig(RfqConfig.Mode.LIVE)
        r.maker.onEvent(created("a", "b"))
        r.sent.clear()
        r.legs = listOf(leg("a", "e1", 0.9), leg("b", "e2", 0.9))                       // the fair moved a lot: still confirmed
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-1", "q1", 12.0, 0.216, "rfq-1"))
        val c = json.parseToJsonElement(r.sent.single()).jsonObject["confirm"]!!.jsonObject
        assertEquals("true", c["confirmed"]!!.jsonPrimitive.content)
        assertEquals(1L, r.maker.confirmed)
        // a quote we did not make, or a stake above our maximum, is rejected
        r.sent.clear()
        r.maker.onEvent(RfqProtocol.QuoteAccepted(now, "rfq-X", "q9", 12.0, 0.3, null))
        assertEquals("false", json.parseToJsonElement(r.sent.single()).jsonObject["confirm"]!!.jsonObject["confirmed"]!!.jsonPrimitive.content)
    }

    @Test fun faultsStopTheQuotingAndTellTj() {
        val r = rig(RfqConfig.Mode.LIVE)
        r.maker.onEvent(RfqProtocol.Refused(now, 7, "MAKER_LOCKED", null))
        assertNotNull(r.maker.halted); assertTrue(r.told.single().contains("stopped quoting"))
        r.maker.onEvent(created("a", "b"))
        assertTrue("halted: priced and journaled, not quoted", r.sent.isEmpty() && r.rows.any { it.kind == "CREATED" })
        r.clock += 6 * 60_000L                                                          // the 5-minute lock has lifted
        r.legs = listOf(LegFair("a", "e1", "label a", 0.5, 5, r.clock - 60_000L, r.clock + 3 * 3_600_000L, false), LegFair("b", "e2", "label b", 0.4, 5, r.clock - 60_000L, r.clock + 3 * 3_600_000L, false))   // fresh fair at the later time
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
}
