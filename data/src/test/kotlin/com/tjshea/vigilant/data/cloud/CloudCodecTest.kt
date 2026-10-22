package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PositiveDepth
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCodecTest {
    private val start = 1_800_000_000_000L

    private fun row(id: String = "o1", withExtras: Boolean = true): Opportunity {
        val event = NovigEvent("e1", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", start)
        val market = NovigMarket("m1", "e1", "SPREAD", "OPEN", "Spread", start, MarketFee.GAME, listOf(NovigOutcome(id, "NYK -3.5", "TBD"), NovigOutcome("o2", "BOS +3.5", "TBD")), strike = -3.5)
        val pin = BookPrices("pinnacle", "Pinnacle", listOf(1.91, 1.95), start - 60_000)
        val fair = FairLine(
            probabilities = listOf(0.52, 0.48), requestedSource = FairSource.BLEND, sourceUsed = FairSource.SHARP, method = DevigMethod.WORST_CASE,
            perBook = listOf(BookFair(pin, listOf(0.52, 0.48), 0.04, true)), sharpBooksUsed = listOf("Pinnacle"), averageBooksUsed = listOf("DraftKings"),
            excluded = if (withExtras) listOf(BookPrices("odd", "Odd Book", listOf(3.0, 1.2), null)) else emptyList(),
        )
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = fair, fairProbability = 0.52, quote = EvQuote(0.52, 0.48, 0.0075),
            ladder = listOf(TakeLevel(0.48, 120), TakeLevel(0.49, 300)), depth = PositiveDepth(120, 59.1, 2.4, 0.49), suggestedStake = if (withExtras) 12.5 else null,
            novigWidth = 0.02, bestBid = 0.47, bidLevels = listOf(BidLevel(470, 90), BidLevel(465, 40)), bookFetchedAtMs = start - 5_000, fairUpdatedMs = start - 60_000,
            refEvent = null, lineKey = LineKey("ref-1", LineKind.SPREAD, -3.5, 0, null, null), target = OutcomeTarget.Is(Side.HOME), fairAsOfMs = start - 90_000,
        )
    }

    @Test
    fun `a row survives the trip through JSON text unchanged`() {
        for (withExtras in listOf(true, false)) {
            val r = row(withExtras = withExtras)
            val text = Json.encodeToString(JsonObject.serializer(), CloudCodec.encode(r))
            val back = CloudCodec.decode(Json.parseToJsonElement(text) as JsonObject)
            assertEquals(r, back)
            assertEquals(r.evPercent!!, back!!.evPercent!!, 0.0)
            assertEquals(r.key, back.key)
        }
    }

    @Test
    fun `a Kalshi row keeps each outcome's ticker so the phone can order it`() {
        val r = row()
        val ref = com.tjshea.vigilant.data.book.BookRef("KXNFLGAME-1", "KXNFLGAME-1-NYK|yes", "https://kalshi.com/m")
        val market = r.market.copy(outcomes = r.market.outcomes.mapIndexed { i, o -> o.copy(bookRef = if (i == 0) ref else ref.copy(outcomeId = "KXNFLGAME-1-NYK|no")) })
        val kr = r.copy(market = market, outcome = market.outcomes.first())
        val back = CloudCodec.decode(Json.parseToJsonElement(Json.encodeToString(JsonObject.serializer(), CloudCodec.encode(kr))) as JsonObject)!!
        assertEquals(kr, back)
        assertEquals(mapOf("o1" to com.tjshea.vigilant.data.kalshitrade.KalshiBook.Ref("KXNFLGAME-1-NYK", "yes"), "o2" to com.tjshea.vigilant.data.kalshitrade.KalshiBook.Ref("KXNFLGAME-1-NYK", "no")), com.tjshea.vigilant.data.kalshitrade.KalshiBook.refs(back.market))
    }

    @Test
    fun `a row with an unknown league or a missing part is skipped and counted, never fails the feed`() {
        val good = CloudCodec.encode(row())
        val unknownLeague = JsonObject(good + ("lg" to JsonPrimitive("CURLING")))
        val missing = JsonObject(good - "mk")
        val (rows, bad) = CloudCodec.decodeRows(JsonArray(listOf(good, unknownLeague, missing, JsonPrimitive(3), buildJsonObject { })))
        assertEquals(1, rows.size)
        assertEquals(4, bad)
    }

    @Test
    fun `fields a later version adds are ignored`() {
        val j = JsonObject(CloudCodec.encode(row()) + ("zz" to JsonPrimitive("future")))
        assertEquals(row(), CloudCodec.decode(j))
    }

    @Test
    fun `the rows become a scan result the feed reads`() {
        val rows = listOf(row("o1"), row("o3"))
        val r = CloudCodec.asScanResult(rows, ScanStats(1, 1, 1, 2, 2), start)
        assertEquals(2, r.opportunities.size)
        assertEquals(1, r.games.size)
        assertTrue(!r.partial)
        assertNull(r.waitingFor.firstOrNull())
    }
}
