package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.kalshi.KalshiSigner
import com.tjshea.vigilant.data.novig.RateGate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

/** One order on Kalshi. [status]: `resting`, `canceled` or `executed` (Kalshi's words). Prices are dollars of the YES leg and the NO leg (they add to $1). */
data class KalshiOrder(
    val orderId: String,
    val clientOrderId: String?,
    val ticker: String,
    /** `yes` or `no`: the exposure the order buys (a buy of NO is `no`). */
    val outcomeSide: String,
    val status: String,
    val yesPrice: Double,
    val noPrice: Double,
    val initialCount: Double,
    val fillCount: Double,
    val remainingCount: Double,
    val takerFillCost: Double,
    val makerFillCost: Double,
    val takerFees: Double,
    val makerFees: Double,
    val createdMs: Long?,
    val expiresMs: Long?,
) {
    /** The price paid per contract of the side this order buys. */
    val price: Double get() = if (outcomeSide == "no") noPrice else yesPrice
    val terminal: Boolean get() = status != "resting"
    val fees: Double get() = takerFees + makerFees
    val cost: Double get() = takerFillCost + makerFillCost
}

/** One fill of an order: [count] contracts of [outcomeSide] at [price] dollars, [fee] dollars. */
data class KalshiFill(
    val fillId: String,
    val orderId: String,
    val ticker: String,
    val outcomeSide: String,
    val count: Double,
    val price: Double,
    val taker: Boolean,
    val fee: Double,
    val ts: Long?,
)

/** A held position: [position] contracts (positive = YES, negative = NO), what it cost, what is locked in. */
data class KalshiPosition(
    val ticker: String,
    val position: Double,
    val exposure: Double,
    val totalTraded: Double,
    val realizedPnl: Double,
    val feesPaid: Double,
)

/** A settled market: contracts held at the end and what they paid. */
data class KalshiSettlement(
    val ticker: String,
    val eventTicker: String,
    val result: String,
    val yesCount: Double,
    val yesCost: Double,
    val noCount: Double,
    val noCost: Double,
    /** Total revenue in dollars (Kalshi reports cents). */
    val revenue: Double,
    val fee: Double,
    val settledMs: Long?,
)

/** [shards]: the available balance on each exchange shard (Kalshi's `balance_breakdown`): an order can only spend the balance of the shard its market trades on. */
data class KalshiBalance(val cash: Double, val portfolioValue: Double, val shards: List<Pair<Int, Double>> = emptyList())

/**
 * The top of one market's book as Kalshi reports it: what a taker pays for YES is [yesAsk] and for NO is `1 - yesBid` (the NO ask), with [askSize] / [bidSize]
 * contracts at each. [status] `active` or `open` is tradable. A side nobody offers has a null price.
 */
data class KalshiTop(
    val ticker: String,
    val yesBid: Double?,
    val yesAsk: Double?,
    val bidSize: Double?,
    val askSize: Double?,
    val status: String,
) {
    val tradable: Boolean get() = status == "active" || status == "open"
    val noAsk: Double? get() = yesBid?.let { 1.0 - it }

    /** The price to buy [side] ("yes" / "no") now, null when nobody sells it. */
    fun priceFor(side: String): Double? = (if (side == "yes") yesAsk else noAsk)?.takeIf { it > 0.0 && it < 1.0 }

    /** Contracts offered at [priceFor] [side]. */
    fun sizeFor(side: String): Double? = if (side == "yes") askSize else bidSize
}

/** What an accepted order did right away. [filled] contracts matched at [averagePrice] with [averageFee] a contract; [remaining] rests (a GTC) or was cancelled (an IOC). */
data class KalshiPlaced(
    val orderId: String,
    val clientOrderId: String?,
    val filled: Double,
    val remaining: Double,
    val averagePrice: Double?,
    val averageFee: Double?,
)

/** A refusal or failure: [status] the HTTP code (0 = no answer), [code] Kalshi's own code, [detail] its words. */
class KalshiApiException(val status: Int, val code: String?, val detail: String?) :
    RuntimeException("Kalshi HTTP $status${code?.let { " $it" }.orEmpty()}${detail?.let { ": $it" }.orEmpty()}") {
    /** The refusal says the request cannot work until Tj changes something (a key without trading rights, a closed market, no money). */
    val isAuth: Boolean get() = status == 401 || status == 403
    val isRateLimit: Boolean get() = status == 429
}

/**
 * The order/portfolio half of Kalshi's signed API (KALSHI_API.md, TRADING; docs.kalshi.com, read 2026-10-20). Only this class places or cancels a Kalshi order, and only
 * the Kalshi side of the app uses it: nothing in the Novig code knows it exists. Orders go to the V2 route (`POST /portfolio/events/orders`, `side` bid/ask of the YES leg:
 * buying NO at p is selling YES at 1 - p); if Kalshi answers 404 or 405 there (the route moved), the legacy route (`POST /portfolio/orders`, yes/no and buy/sell) is used for the
 * life of the client. Prices go as dollar strings, counts as whole contracts, every order carries a [clientOrderId] (a UUID) that finds it again after a lost answer.
 * Two bugs this shape rules out: a retried order (an unanswered POST is looked up by client id, never sent again) and a price that is not the one decided on (the price is the ticker's side price, formatted once).
 */
open class KalshiTradingClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val signer: () -> KalshiSigner?,
    private val baseUrl: String = DEFAULT_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Orders (and cancels) are paced under the account's write budget (10 tokens each; 100 a second at the lowest tier). */
    private val writeGate: RateGate = RateGate(ratePerSecond = 5.0, burst = 5, minRate = 1.0, maxRate = 8.0),
    private val readGate: RateGate = RateGate(ratePerSecond = 8.0, burst = 8, minRate = 1.0, maxRate = 12.0),
    /** The account's subaccount number (0 = primary); null leaves the parameter out. */
    private val subaccount: Int? = null,
) {
    @Volatile private var legacy = false

    open val ready: Boolean get() = signer() != null

    private fun signed(): KalshiSigner = signer() ?: throw KalshiApiException(0, "no_key", "No Kalshi key is saved")

    private suspend fun call(method: String, path: String, query: Map<String, String> = emptyMap(), body: JsonObject? = null, write: Boolean = false): JsonObject {
        val s = signed()
        val gate = if (write) writeGate else readGate
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        gate.acquire()
        val builder = Request.Builder().url(url)
        s.headers(method, url, clock()).forEach { (h, v) -> builder.header(h, v) }
        builder.header("Accept", "application/json")
        val requestBody = (body?.toString() ?: "").toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (body == null) builder.delete() else builder.delete(requestBody)
            else -> builder.method(method, requestBody)
        }
        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: java.io.IOException) {
            throw KalshiApiException(0, "network", e.message)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 429) {
                val wait = r.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000) ?: 2_000L
                gate.pause(clock() + wait)
                gate.slowDown()
            }
            if (!r.isSuccessful) {
                val err = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val nested = err?.get("error") as? JsonObject
                throw KalshiApiException(
                    r.code,
                    nested?.str("code") ?: err?.str("code"),
                    nested?.str("message") ?: err?.str("message") ?: nested?.str("details") ?: err?.str("details") ?: text.take(160).ifBlank { null },
                )
            }
            if (text.isBlank()) return JsonObject(emptyMap())
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun sub(q: MutableMap<String, String>): MutableMap<String, String> = q.also { m -> subaccount?.let { m["subaccount"] = it.toString() } }

    /** When each market's shard was last funded, so a refusal that persists is not answered with transfer after transfer. */
    private val fundedAt = HashMap<String, Long>()

    /** What the last shard move did, in words (shown on the Kalshi page and in a refusal). */
    @Volatile var lastFunding: String? = null
        private set

    /**
     * Moves the cash held on the other exchange shards to the shard [ticker] trades on (`POST /portfolio/intra_exchange_instance_transfer`, event contract to event contract, centicents, a client transfer id),
     * then waits a few seconds for it to arrive. Null = the money is there now and the order may be sent again; else why not (said in words).
     */
    suspend fun fundShardFor(ticker: String): String? {
        val now = clock()
        synchronized(fundedAt) { fundedAt[ticker]?.takeIf { now - it < 60_000L }?.let { return "cash was moved for this market a moment ago and is still not enough" }; fundedAt[ticker] = now }
        val shard = (call("GET", "/markets/$ticker")["market"] as? JsonObject)?.num("exchange_index")?.toInt() ?: 0
        val before = balance()
        val here = before.shards.firstOrNull { it.first == shard }?.second ?: 0.0
        val sources = before.shards.filter { it.first != shard && it.second >= 0.01 }
        if (sources.isEmpty()) return "no other exchange shard holds cash (cash by shard: ${before.shards.joinToString { "#${it.first} $${"%.2f".format(Locale.US, it.second)}" }.ifEmpty { "not reported" }}); fund the account on Kalshi"
        for ((idx, dollars) in sources) {
            val body = buildJsonObject {
                put("source", "event_contract"); put("destination", "event_contract")
                put("source_exchange_shard", idx); put("destination_exchange_shard", shard)
                put("amount", Math.round(dollars * 10_000.0))
                put("client_transfer_id", newClientId())
            }
            call("POST", "/portfolio/intra_exchange_instance_transfer", body = body, write = true)
        }
        lastFunding = "moved ${sources.joinToString { "$${"%.2f".format(Locale.US, it.second)} from shard #${it.first}" }} to shard #$shard (the shard of $ticker)"
        repeat(10) {
            kotlinx.coroutines.delay(1_000L)
            val h = balance().shards.firstOrNull { it.first == shard }?.second ?: 0.0
            if (h > here + 0.005) return null
        }
        return "the transfer was accepted but the cash had not arrived after 10 seconds"
    }

    /** The last order this client sent, in words (ticker, side, count, price, time in force): shown beside a refusal so Kalshi's answer can be checked against what was asked. */
    @Volatile var lastOrder: String? = null
        private set

    /** Cash and the value of the positions, in dollars. */
    open suspend fun balance(): KalshiBalance {
        val o = call("GET", "/portfolio/balance", sub(HashMap()))
        val cash = o.dollars("balance_dollars") ?: (o.num("balance")?.div(100.0)) ?: 0.0
        val pv = o.dollars("portfolio_value_dollars") ?: (o.num("portfolio_value")?.div(100.0)) ?: 0.0
        val shards = (o["balance_breakdown"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { e ->
            val b = e as? JsonObject ?: return@mapNotNull null
            val idx = b.num("exchange_index")?.toInt() ?: return@mapNotNull null
            idx to (b.dollars("balance") ?: 0.0)
        }.orEmpty()
        return KalshiBalance(cash, pv, shards)
    }

    /** The old route's order (`POST /portfolio/orders`, an explicit action and side): the same client id, so a lost answer is found the same way. */
    private suspend fun buyLegacy(ticker: String, outcomeSide: String, price: Double, count: Long, clientOrderId: String, tif: String, postOnly: Boolean, expirationMs: Long?): KalshiPlaced {
        val old = buildJsonObject {
            put("ticker", ticker)
            put("client_order_id", clientOrderId)
            put("action", "buy")
            put("side", outcomeSide)
            put("type", "limit")
            put("count", count)
            put(if (outcomeSide == "yes") "yes_price_dollars" else "no_price_dollars", dollars(price))
            put("time_in_force", when (tif) { IOC -> "immediate_or_cancel"; FOK -> "fill_or_kill"; else -> "good_till_canceled" })
            if (postOnly) put("post_only", true)
            if (expirationMs != null && tif == GTC) put("expiration_ts", expirationMs / 1000L)
            put("self_trade_prevention_type", "taker_at_cross")
            subaccount?.let { put("subaccount", it) }
        }
        val o = call("POST", "/portfolio/orders", body = old, write = true)
        val order = (o["order"] as? JsonObject)?.let(::order)
        return KalshiPlaced(
            order?.orderId ?: o.str("order_id") ?: throw KalshiApiException(200, "no_order_id", "Kalshi's reply names no order"),
            order?.clientOrderId ?: clientOrderId,
            order?.fillCount ?: 0.0,
            order?.remainingCount ?: 0.0,
            order?.takeIf { it.fillCount > 0.0 }?.let { it.cost / it.fillCount },
            order?.takeIf { it.fillCount > 0.0 }?.let { it.fees / it.fillCount },
        )
    }

    /**
     * Buys [count] whole contracts of [outcomeSide] ("yes" or "no") on [ticker] at [price] dollars (that side's price), or better.
     * [tif]: [IOC] takes what it can now and cancels the rest; [GTC] rests (with [postOnly] it is refused rather than taking, which is a bid); [FOK] all or nothing.
     * [expirationMs] limits how long a GTC rests.
     */
    open suspend fun buy(
        ticker: String,
        outcomeSide: String,
        price: Double,
        count: Long,
        clientOrderId: String,
        tif: String = IOC,
        postOnly: Boolean = false,
        expirationMs: Long? = null,
    ): KalshiPlaced {
        require(outcomeSide == "yes" || outcomeSide == "no") { "side must be yes or no: $outcomeSide" }
        lastOrder = "$ticker ${outcomeSide.uppercase()} x$count @ ${dollars(price)} $tif"
        require(count >= 1) { "count must be at least 1" }
        require(price > 0.0 && price < 1.0) { "price must be inside (0, 1): $price" }
        require(isUuid(clientOrderId)) { "client order id must be a UUID: $clientOrderId" }
        require(tif == IOC || tif == GTC || tif == FOK) { "unknown time in force $tif" }
        require(!postOnly || tif == GTC) { "post only needs a resting (GTC) order" }
        if (!legacy) {
            val v2 = buildJsonObject {
                put("ticker", ticker)
                put("client_order_id", clientOrderId)
                // The YES leg: buying YES is a bid at its price; buying NO at p is an ask on YES at 1 - p.
                put("side", if (outcomeSide == "yes") "bid" else "ask")
                put("count", count.toString() + ".00")
                put("price", dollars(if (outcomeSide == "yes") price else 1.0 - price))
                put("time_in_force", tif)
                if (postOnly) put("post_only", true)
                put("self_trade_prevention_type", "taker_at_cross")
                if (expirationMs != null && tif == GTC) put("expiration_time", expirationMs / 1000L)
                subaccount?.let { put("subaccount", it) }
            }
            try {
                return try {
                    placed(call("POST", "/portfolio/events/orders", body = v2, write = true), clientOrderId, price)
                } catch (e: KalshiApiException) {
                    // insufficient_balance with cash in the account (Tj, 2026-10-23; docs.kalshi.com exchange_sharding): an order spends the balance of ITS market's exchange shard. The cash on the other shards is
                    // moved there (inside the same account, never out of it), once a minute at most, and the order sent again: a refused order placed nothing.
                    if (e.status == 400 && e.code?.contains("insufficient", true) == true && fundShardFor(ticker) == null) {
                        placed(call("POST", "/portfolio/events/orders", body = v2, write = true), clientOrderId, price)
                    } else throw e
                }
            } catch (e: KalshiApiException) {
                // The route is gone (a retired path answers 404 or 405): the old one, from now on. Anything else is Kalshi's answer to this order.
                if (e.status != 404 && e.status != 405) throw e
                legacy = true
            }
        }
        return buyLegacy(ticker, outcomeSide, price, count, clientOrderId, tif, postOnly, expirationMs)
    }

    private fun placed(o: JsonObject, clientOrderId: String, intended: Double): KalshiPlaced = KalshiPlaced(
        orderId = o.str("order_id") ?: throw KalshiApiException(200, "no_order_id", "Kalshi's reply names no order"),
        clientOrderId = o.str("client_order_id") ?: clientOrderId,
        filled = o.count("fill_count") ?: 0.0,
        remaining = o.count("remaining_count") ?: 0.0,
        averagePrice = o.dollars("average_fill_price"),
        averageFee = o.dollars("average_fee_paid"),
    )

    /** The order as Kalshi holds it now. */
    open suspend fun order(orderId: String): KalshiOrder? = try {
        (call("GET", "/portfolio/orders/$orderId", sub(HashMap()))["order"] as? JsonObject)?.let(::order)
    } catch (e: KalshiApiException) {
        if (e.status == 404) null else throw e
    }

    /** Orders by status (`resting`, `canceled`, `executed`; null = all), newest page first. */
    open suspend fun orders(status: String? = null, ticker: String? = null, limit: Int = 200): List<KalshiOrder> {
        val q = sub(HashMap())
        status?.let { q["status"] = it }
        ticker?.let { q["ticker"] = it }
        q["limit"] = limit.toString()
        val arr = call("GET", "/portfolio/orders", q)["orders"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.let(::order) }
    }

    /** The order a lost answer may have left: found by its client id among the open and the recent orders on [ticker]. */
    open suspend fun findByClientId(clientOrderId: String, ticker: String): KalshiOrder? =
        orders(ticker = ticker, limit = 200).firstOrNull { it.clientOrderId == clientOrderId }

    /** Cancels a resting order. True when Kalshi cancelled it, false when it was already gone (filled or cancelled). */
    open suspend fun cancel(orderId: String, ticker: String? = null): Boolean = try {
        val q = sub(HashMap())
        ticker?.let { q["market_ticker"] = it }
        if (!legacy) {
            try {
                call("DELETE", "/portfolio/events/orders/$orderId", q, write = true)
            } catch (e: KalshiApiException) {
                if (e.status == 404 && e.code?.contains("order", true) != true) call("DELETE", "/portfolio/orders/$orderId", sub(HashMap()), write = true) else throw e
            }
        } else {
            call("DELETE", "/portfolio/orders/$orderId", sub(HashMap()), write = true)
        }
        true
    } catch (e: KalshiApiException) {
        if (e.status == 404) false else throw e
    }

    /** Fills since [sinceMs], newest first (up to [limit]). */
    open suspend fun fills(sinceMs: Long? = null, ticker: String? = null, orderId: String? = null, limit: Int = 500): List<KalshiFill> {
        val q = sub(HashMap())
        sinceMs?.let { q["min_ts"] = (it / 1000L).toString() }
        ticker?.let { q["ticker"] = it }
        orderId?.let { q["order_id"] = it }
        q["limit"] = limit.coerceIn(1, 1000).toString()
        val arr = call("GET", "/portfolio/fills", q)["fills"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.let(::fill) }
    }

    /** Positions not yet settled (and, with [settled], the settled ones). */
    open suspend fun positions(settled: Boolean = false): List<KalshiPosition> {
        val q = sub(HashMap())
        q["limit"] = "1000"
        q["count_filter"] = "position"
        if (settled) q["settlement_status"] = "all"
        val arr = call("GET", "/portfolio/positions", q)["market_positions"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            KalshiPosition(
                ticker = o.str("ticker") ?: return@mapNotNull null,
                position = o.count("position_fp") ?: o.num("position") ?: 0.0,
                exposure = o.dollars("market_exposure_dollars") ?: 0.0,
                totalTraded = o.dollars("total_traded_dollars") ?: 0.0,
                realizedPnl = o.dollars("realized_pnl_dollars") ?: 0.0,
                feesPaid = o.dollars("fees_paid_dollars") ?: 0.0,
            )
        }
    }

    /** Markets Kalshi has settled for this account since [sinceMs]. */
    open suspend fun settlements(sinceMs: Long? = null, limit: Int = 500): List<KalshiSettlement> {
        val q = sub(HashMap())
        sinceMs?.let { q["min_ts"] = (it / 1000L).toString() }
        q["limit"] = limit.coerceIn(1, 1000).toString()
        val arr = call("GET", "/portfolio/settlements", q)["settlements"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            KalshiSettlement(
                ticker = o.str("ticker") ?: return@mapNotNull null,
                eventTicker = o.str("event_ticker").orEmpty(),
                result = o.str("market_result").orEmpty(),
                yesCount = o.count("yes_count_fp") ?: 0.0,
                yesCost = o.dollars("yes_total_cost_dollars") ?: 0.0,
                noCount = o.count("no_count_fp") ?: 0.0,
                noCost = o.dollars("no_total_cost_dollars") ?: 0.0,
                revenue = (o.num("revenue") ?: 0.0) / 100.0,
                fee = o.dollars("fee_cost") ?: 0.0,
                settledMs = o.str("settled_time")?.let(::parseTime),
            )
        }
    }

    /** The top of the book of each of [tickers] (up to 100 a request, signed), by ticker. A ticker Kalshi does not return is missing from the map. */
    open suspend fun tops(tickers: Collection<String>): Map<String, KalshiTop> {
        val out = HashMap<String, KalshiTop>()
        for (chunk in tickers.distinct().chunked(100)) {
            val arr = call("GET", "/markets", mapOf("tickers" to chunk.joinToString(","), "limit" to "100"))["markets"] as? JsonArray ?: continue
            for (e in arr) {
                val o = e as? JsonObject ?: continue
                val t = o.str("ticker") ?: continue
                out[t] = KalshiTop(t, o.dollars("yes_bid_dollars"), o.dollars("yes_ask_dollars"), o.count("yes_bid_size_fp"), o.count("yes_ask_size_fp"), o.str("status").orEmpty())
            }
        }
        return out
    }

    /** Whether the key can reach the account at all (a balance read): the Test key button. */
    open suspend fun ping(): Result<KalshiBalance> = runCatching { balance() }

    private fun order(o: JsonObject): KalshiOrder? {
        val id = o.str("order_id") ?: return null
        return KalshiOrder(
            orderId = id,
            clientOrderId = o.str("client_order_id"),
            ticker = o.str("ticker") ?: return null,
            outcomeSide = o.str("outcome_side") ?: o.str("side") ?: "yes",
            status = o.str("status").orEmpty(),
            yesPrice = o.dollars("yes_price_dollars") ?: 0.0,
            noPrice = o.dollars("no_price_dollars") ?: 0.0,
            initialCount = o.count("initial_count_fp") ?: 0.0,
            fillCount = o.count("fill_count_fp") ?: 0.0,
            remainingCount = o.count("remaining_count_fp") ?: 0.0,
            takerFillCost = o.dollars("taker_fill_cost_dollars") ?: 0.0,
            makerFillCost = o.dollars("maker_fill_cost_dollars") ?: 0.0,
            takerFees = o.dollars("taker_fees_dollars") ?: 0.0,
            makerFees = o.dollars("maker_fees_dollars") ?: 0.0,
            createdMs = o.str("created_time")?.let(::parseTime),
            expiresMs = o.str("expiration_time")?.let(::parseTime),
        )
    }

    private fun fill(o: JsonObject): KalshiFill? {
        val side = o.str("outcome_side") ?: o.str("side") ?: return null
        return KalshiFill(
            fillId = o.str("fill_id") ?: o.str("trade_id") ?: return null,
            orderId = o.str("order_id").orEmpty(),
            ticker = o.str("ticker") ?: o.str("market_ticker") ?: return null,
            outcomeSide = side,
            count = o.count("count_fp") ?: o.num("count") ?: 0.0,
            price = (if (side == "no") o.dollars("no_price_dollars") else o.dollars("yes_price_dollars")) ?: 0.0,
            taker = o["is_taker"]?.let { (it as? JsonPrimitive)?.contentOrNull == "true" } ?: false,
            fee = o.dollars("fee_cost") ?: 0.0,
            ts = o.str("created_time")?.let(::parseTime) ?: o.num("ts")?.toLong()?.times(1000L),
        )
    }

    companion object {
        const val DEFAULT_URL = "https://external-api.kalshi.com/trade-api/v2"
        const val IOC = "immediate_or_cancel"
        const val GTC = "good_till_canceled"
        const val FOK = "fill_or_kill"
        private val JSON = "application/json".toMediaType()

        /** A dollar price as Kalshi takes it: four decimals, a point (never a comma, whatever the phone's language). */
        fun dollars(v: Double): String = String.format(Locale.US, "%.4f", v)

        fun isUuid(s: String): Boolean = runCatching { java.util.UUID.fromString(s) }.isSuccess

        fun newClientId(): String = java.util.UUID.randomUUID().toString()

        internal fun parseTime(s: String): Long? = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
        private fun JsonObject.num(key: String): Double? = str(key)?.toDoubleOrNull()
        private fun JsonObject.dollars(key: String): Double? = num(key)
        private fun JsonObject.count(key: String): Double? = num(key)
    }
}
