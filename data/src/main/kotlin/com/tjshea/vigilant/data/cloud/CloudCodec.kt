package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.LiveAges
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.PricedGame
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PositiveDepth
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A priced bet as it travels from the GitHub engine to the phone (Tj, 2026-10-14: the scans run on GitHub's runners; the phone only sets the settings and reads the results). A row carries
 * everything the Feed, the bet sheet and the Tracker read from an [Opportunity] except [Opportunity.refEvent] (the whole reference game with every book's every market: it is what makes a row heavy
 * and only the scanner's own re-matching reads it). Written by hand, not by generated serializers, so that [com.tjshea.vigilant.engine] stays free of a serialization plugin and an unknown or
 * missing field never fails a whole feed: a row that cannot be read is skipped and counted ([decodeRows]).
 */
object CloudCodec {

    fun encode(o: Opportunity): JsonObject = buildJsonObject {
        put("lg", o.league.novigName)
        put("ev", event(o.event))
        put("mk", market(o.market))
        put("oc", outcome(o.outcome))
        put("ml", o.marketLabel)
        put("kd", o.kind.name)
        put("sel", o.selection)
        o.fair?.let { put("fair", fairLine(it)) }
        o.fairProbability?.let { put("fp", it) }
        o.quote?.let { q -> put("q", buildJsonObject { put("p", q.fairProbability); put("pr", q.price); put("f", q.fee) }) }
        put("lad", buildJsonArray { o.ladder.forEach { add(buildJsonArray { add(JsonPrimitive(it.price)); add(JsonPrimitive(it.contracts)) }) } })
        o.depth?.let { d ->
            put("dp", buildJsonObject {
                put("c", d.contracts); put("d", d.dollarCost); put("e", d.dollarEv)
                d.worstPrice?.let { put("w", it) }
            })
        }
        o.suggestedStake?.let { put("ss", it) }
        o.novigWidth?.let { put("nw", it) }
        o.bestBid?.let { put("bb", it) }
        if (o.bidLevels.isNotEmpty()) put("bl", buildJsonArray { o.bidLevels.forEach { add(buildJsonArray { add(JsonPrimitive(it.priceMilli)); add(JsonPrimitive(it.contracts)) }) } })
        o.bookFetchedAtMs?.let { put("bf", it) }
        o.fairUpdatedMs?.let { put("fu", it) }
        o.lineKey?.let { k ->
            put("lk", buildJsonObject {
                put("r", k.refEventId); put("k", k.kind.name); k.line?.let { put("l", it) }; put("pe", k.period)
                k.subject?.let { put("s", it) }; k.stat?.let { put("st", it) }
            })
        }
        (o.target as? OutcomeTarget.Is)?.let { put("tg", it.side.name) }
        o.fairAsOfMs?.let { put("fa", it) }
        o.liveAges?.let { a ->
            put("la", buildJsonObject {
                put("a", a.pinnLineChangedMs); put("b", a.pinnFeedMs); a.novigMarketMs?.let { put("c", it) }; put("d", a.novigFeedMs)
            })
        }
    }

    /** The row, or null when it cannot be read (an unknown league, a missing field). */
    fun decode(j: JsonObject): Opportunity? = runCatching {
        val league = Leagues.byNovigName(j.str("lg")) ?: return null
        val q = j["q"]?.jsonObject
        Opportunity(
            league = league,
            event = event(j.obj("ev")),
            market = market(j.obj("mk")),
            outcome = outcome(j.obj("oc")),
            marketLabel = j.str("ml"),
            kind = LineKind.valueOf(j.str("kd")),
            selection = j.str("sel"),
            fair = j["fair"]?.takeIf { it !is JsonNull }?.jsonObject?.let(::fairLine),
            fairProbability = j.dbl("fp"),
            quote = q?.let { EvQuote(it.dbl("p")!!, it.dbl("pr")!!, it.dbl("f")!!) },
            ladder = j["lad"]?.jsonArray.orEmpty().map { val a = it.jsonArray; TakeLevel(a[0].num()!!, a[1].lng()!!) },
            depth = j["dp"]?.jsonObject?.let { PositiveDepth(it.lng("c")!!, it.dbl("d")!!, it.dbl("e")!!, it.dbl("w")) },
            suggestedStake = j.dbl("ss"),
            novigWidth = j.dbl("nw"),
            bestBid = j.dbl("bb"),
            bidLevels = j["bl"]?.jsonArray.orEmpty().map { val a = it.jsonArray; BidLevel(a[0].int()!!, a[1].lng()!!) },
            bookFetchedAtMs = j.lng("bf"),
            fairUpdatedMs = j.lng("fu"),
            refEvent = null,
            lineKey = j["lk"]?.jsonObject?.let { k ->
                LineKey(k.str("r"), LineKind.valueOf(k.str("k")), k.dbl("l"), k.int("pe") ?: 0, k.strOrNull("s"), k.strOrNull("st"))
            },
            target = j.strOrNull("tg")?.let { OutcomeTarget.Is(Side.valueOf(it)) },
            fairAsOfMs = j.lng("fa"),
            liveAges = j["la"]?.jsonObject?.let { LiveAges(it.lng("a")!!, it.lng("b")!!, it.lng("c"), it.lng("d")!!) },
        )
    }.getOrNull()

    /** Every row of [rows] that can be read, and how many could not. */
    fun decodeRows(rows: JsonArray): Pair<List<Opportunity>, Int> {
        var bad = 0
        val out = ArrayList<Opportunity>(rows.size)
        for (e in rows) {
            val o = (e as? JsonObject)?.let(::decode)
            if (o == null) bad++ else out += o
        }
        return out to bad
    }

    /** A scan result the phone's Feed, auto-bet and sheets read as they read their own: the cloud's rows, no games (the rows are the answer). */
    fun asScanResult(rows: List<Opportunity>, stats: ScanStats, computedAtMs: Long): ScanResult {
        val games = rows.groupBy { it.event.eventId }.map { (_, list) -> PricedGame(list.first().league, list.first().event, null, list) }
        return ScanResult(games = games, opportunities = rows, stats = stats, computedAtMs = computedAtMs)
    }

    // --- parts ---------------------------------------------------------------------------------------------------------------------------------------------

    internal fun event(e: NovigEvent) = buildJsonObject {
        put("id", e.eventId); put("sp", e.sport); put("lg", e.league); put("st", e.status); put("ds", e.description); put("ts", e.startsTs)
    }

    private fun event(j: JsonObject) = NovigEvent(j.str("id"), j.str("sp"), j.str("lg"), j.str("st"), j.str("ds"), j.lng("ts")!!)

    internal fun market(m: NovigMarket) = buildJsonObject {
        put("id", m.marketId); put("ev", m.eventId); put("ty", m.marketType); put("st", m.status); put("ds", m.description); put("ts", m.startsTs)
        m.fee?.let { f -> put("fee", buildJsonObject { put("c", f.coefficient); put("m", f.makerCredit); put("ch", f.charged.name) }) }
        put("oc", buildJsonArray { m.outcomes.forEach { add(outcome(it)) } })
        m.strike?.let { put("sk", it) }
    }

    internal fun market(j: JsonObject) = NovigMarket(
        marketId = j.str("id"), eventId = j.str("ev"), marketType = j.str("ty"), status = j.str("st"), description = j.str("ds"), startsTs = j.lng("ts")!!,
        fee = j["fee"]?.jsonObject?.let { MarketFee(it.dbl("c")!!, it.dbl("m")!!, FeeCharge.valueOf(it.str("ch"))) },
        outcomes = j["oc"]!!.jsonArray.map { outcome(it.jsonObject) },
        strike = j.dbl("sk"),
    )

    // The Kalshi ticker rides on `bookRef.outcomeId` ("TICKER|yes"): without it the phone cannot order a bet from the engine's Kalshi list (Tj, 2026-10-22: "its ticker is missing" on every Kalshi bet).
    private fun outcome(o: NovigOutcome) = buildJsonObject {
        put("id", o.outcomeId); put("n", o.name); put("st", o.status)
        o.bookRef?.let { r -> put("br", buildJsonObject { r.eventId?.let { put("e", it) }; r.outcomeId?.let { put("o", it) }; r.eventLink?.let { put("l", it) } }) }
    }

    private fun outcome(j: JsonObject) = NovigOutcome(
        j.str("id"), j.str("n"), j.str("st"),
        bookRef = (j["br"] as? JsonObject)?.let { b ->
            com.tjshea.vigilant.data.book.BookRef((b["e"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull, (b["o"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull, (b["l"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull)
        },
    )

    private fun fairLine(f: FairLine) = buildJsonObject {
        put("p", doubles(f.probabilities))
        put("rs", f.requestedSource.name); put("us", f.sourceUsed.name); put("m", f.method.name)
        put("pb", buildJsonArray { f.perBook.forEach { add(bookFair(it)) } })
        put("sh", strings(f.sharpBooksUsed)); put("av", strings(f.averageBooksUsed))
        if (f.excluded.isNotEmpty()) put("ex", buildJsonArray { f.excluded.forEach { add(book(it)) } })
    }

    private fun fairLine(j: JsonObject) = FairLine(
        probabilities = j["p"]!!.jsonArray.map { it.num()!! },
        requestedSource = FairSource.valueOf(j.str("rs")), sourceUsed = FairSource.valueOf(j.str("us")), method = DevigMethod.valueOf(j.str("m")),
        perBook = j["pb"]!!.jsonArray.map { bookFair(it.jsonObject) },
        sharpBooksUsed = j["sh"]!!.jsonArray.map { it.str() }, averageBooksUsed = j["av"]!!.jsonArray.map { it.str() },
        excluded = j["ex"]?.jsonArray.orEmpty().map { book(it.jsonObject) },
    )

    private fun bookFair(b: BookFair) = buildJsonObject {
        put("b", book(b.book)); put("fp", doubles(b.fairProbabilities)); put("h", b.hold); put("sh", b.isSharp)
    }

    private fun bookFair(j: JsonObject) = BookFair(book(j.obj("b")), j["fp"]!!.jsonArray.map { it.num()!! }, j.dbl("h")!!, j["sh"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false)

    private fun book(b: BookPrices) = buildJsonObject {
        put("k", b.bookKey); put("t", b.bookTitle); put("o", doubles(b.decimalOdds)); b.lastUpdateMs?.let { put("u", it) }
    }

    private fun book(j: JsonObject) = BookPrices(j.str("k"), j.str("t"), j["o"]!!.jsonArray.map { it.num()!! }, j.lng("u"))

    internal fun doubles(l: List<Double>) = buildJsonArray { l.forEach { add(JsonPrimitive(it)) } }
    internal fun strings(l: List<String>) = buildJsonArray { l.forEach { add(JsonPrimitive(it)) } }

    internal fun JsonObject.obj(k: String) = this[k]!!.jsonObject
    internal fun JsonObject.str(k: String) = (this[k] as JsonPrimitive).content
    internal fun JsonObject.strOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    internal fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
    internal fun JsonObject.lng(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    internal fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
    internal fun JsonElement.num() = (this as? JsonPrimitive)?.doubleOrNull
    internal fun JsonElement.lng() = (this as? JsonPrimitive)?.longOrNull
    internal fun JsonElement.int() = (this as? JsonPrimitive)?.intOrNull
    internal fun JsonElement.str() = (this as JsonPrimitive).content
}
