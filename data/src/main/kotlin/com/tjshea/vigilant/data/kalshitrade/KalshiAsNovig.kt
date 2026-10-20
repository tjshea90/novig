package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.roundToLong

/** Which Kalshi ticker and side each outcome of the Kalshi scan is, learnt from the scan's markets ([KalshiBook.refs]); the adapter turns an outcome id into an order and back. */
class KalshiOutcomes {
    private val forward = ConcurrentHashMap<String, KalshiBook.Ref>()
    private val back = ConcurrentHashMap<String, String>()
    private val markets = ConcurrentHashMap<String, String>()

    fun learn(market: NovigMarket) {
        for ((outcomeId, ref) in KalshiBook.refs(market)) {
            forward[outcomeId] = ref
            back["${ref.ticker}|${ref.side}"] = outcomeId
            markets[ref.ticker] = market.marketId
        }
    }

    fun refOf(outcomeId: String): KalshiBook.Ref? = forward[outcomeId]
    fun outcomeOf(ticker: String, side: String): String? = back["$ticker|$side"]
    fun marketOf(ticker: String): String = markets[ticker] ?: "kalshi|$ticker"
}

/**
 * Kalshi spoken in Novig's tongue (KV4, Tj 2026-10-20: "consider both bets and bids ... all features to work for kalshi"): the bid desk ([com.tjshea.vigilant.data.novig.trading.maker.MakerDesk]),
 * its quoting, plan, guards and store are Novig's and are used UNCHANGED for Kalshi bids by handing the desk this client. It speaks the desk's few calls (post a post-only order with a lifetime, list
 * orders by status, fills, cancel) in Kalshi's: a resting limit order that is refused if it would take (`post_only`), expiring by itself (`expiration_time`), on Kalshi's one-cent grid (a price is floored to
 * the cent: never a higher price than the desk asked), counted in whole $1 contracts (the desk counts 1c ones: 100 of them are one).
 *
 * It extends [NovigTradingClient] only to be accepted where the desk takes one; every call the desk makes is overridden here, and the signer underneath points nowhere (a call that slipped through would
 * fail on the phone at once, never reach Novig).
 */
class KalshiAsNovig(
    private val kalshi: KalshiTradingClient,
    private val outcomes: KalshiOutcomes,
    private val clock: () -> Long = System::currentTimeMillis,
) : NovigTradingClient(
    NovigSignedClient(OkHttpClient(), Json, object : NovigSigningKey {
        override val keyId = "kalshi-adapter"
        override val algorithm = NovigKeyAlgorithm.entries.first()
        override fun sign(message: ByteArray): ByteArray = throw IllegalStateException("the Kalshi adapter never signs for Novig")
    }, "http://127.0.0.1:9"),
    Json,
) {
    private val expiry = ConcurrentHashMap<String, Long>()

    override val batchOrders: Boolean get() = false

    override suspend fun balance(subaccountKeyId: String): Double = kalshi.balance().cash

    override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
        val ref = outcomes.refOf(outcomeId) ?: throw NovigApiException(400, "UNKNOWN_OUTCOME", "No Kalshi ticker is known for $outcomeId")
        // Kalshi sells whole $1 contracts: a bid the desk sized at half a contract or more is one contract (Tj, 2026-10-20: "0 kalshi bids", every one was "under one contract"); less is refused.
        // Tj, 2026-10-20 (later): "a bid may be placed at the minimum contract on kalshi even if the amount is larger than Kelly": any positive size is at least one contract.
        val count = if (qty in 1L..99L) 1L else qty / 100L
        if (count < 1L) throw NovigApiException(400, "ORDER_TOO_SMALL", "Under one Kalshi contract")
        val cents = floor(price * 100.0 + 1e-9) / 100.0
        if (cents < 0.01 || cents > 0.99) throw NovigApiException(400, "PRICE_OUT_OF_RANGE", "Kalshi prices run 1c to 99c")
        val post = tif == "PO"
        val expiresAt = ttlMs?.let { clock() + it }
        try {
            val placed = kalshi.buy(
                ref.ticker, ref.side, cents, count, clientId,
                tif = if (tif == "IOC") KalshiTradingClient.IOC else if (tif == "FOK") KalshiTradingClient.FOK else KalshiTradingClient.GTC,
                postOnly = post, expirationMs = expiresAt,
            )
            expiresAt?.let { expiry[placed.orderId] = it }
            return placed.orderId
        } catch (e: KalshiApiException) {
            throw asNovig(e)
        }
    }

    override suspend fun cancelOrder(orderId: String): String? = try {
        if (kalshi.cancel(orderId)) "CANCELED" else null
    } catch (e: KalshiApiException) {
        throw asNovig(e)
    }

    override suspend fun cancelOrders(marketId: String?, eventId: String?): Int {
        // Every resting order on Kalshi is this app's (the desk or an arbitrage leg's rests nothing); cancelled one by one so a market filter is honoured.
        var n = 0
        for (o in kalshi.orders(status = "resting")) {
            if (marketId != null && outcomes.marketOf(o.ticker) != marketId) continue
            try { if (kalshi.cancel(o.orderId, o.ticker)) n++ } catch (e: KalshiApiException) { if (!e.isRateLimit) throw asNovig(e) }
        }
        return n
    }

    override suspend fun order(orderId: String): NovigOrder? = try {
        kalshi.order(orderId)?.let(::toNovig)
    } catch (e: KalshiApiException) {
        throw asNovig(e)
    }

    override suspend fun orders(status: String, limit: Int, outcomeId: String?): List<NovigOrder> {
        val want = when (status) {
            "OPEN" -> "resting"
            "FILLED" -> "executed"
            "CANCELED" -> "canceled"
            else -> return emptyList()
        }
        val ref = outcomeId?.let { outcomes.refOf(it) }
        if (outcomeId != null && ref == null) return emptyList()
        return try {
            kalshi.orders(status = want, ticker = ref?.ticker, limit = limit.coerceIn(1, 1000)).filter { o -> ref == null || o.outcomeSide == ref.side }.map(::toNovig)
        } catch (e: KalshiApiException) {
            throw asNovig(e)
        }
    }

    override suspend fun fills(orderId: String?, limit: Int): List<NovigFill> = try {
        kalshi.fills(orderId = orderId, limit = limit).map(::toNovig)
    } catch (e: KalshiApiException) {
        throw asNovig(e)
    }

    override suspend fun fillsStartingAfter(startsAfterMs: Long, limit: Int): List<NovigFill> = try {
        // Kalshi's fills have no "game starts after": the fills since a day before the first start in question.
        kalshi.fills(sinceMs = startsAfterMs - 24 * 3_600_000L, limit = limit).map(::toNovig)
    } catch (e: KalshiApiException) {
        throw asNovig(e)
    }

    override suspend fun positions(marketId: String?) = emptyList<com.tjshea.vigilant.data.novig.trading.NovigPosition>()

    private fun toNovig(o: KalshiOrder): NovigOrder {
        val outcome = outcomes.outcomeOf(o.ticker, o.outcomeSide) ?: "${o.ticker}|${o.outcomeSide}"
        val qty = (o.initialCount * 100.0).roundToLong()
        val rest = (o.remainingCount * 100.0).roundToLong()
        return NovigOrder(
            orderId = o.orderId, clientId = o.clientOrderId, marketId = outcomes.marketOf(o.ticker), outcomeId = outcome, price = o.price, qty = qty, remaining = if (o.status == "resting") rest else 0L,
            tif = "PO", status = when (o.status) { "resting" -> "OPEN"; "executed" -> "FILLED"; "canceled" -> "CANCELED"; else -> o.status.uppercase() },
            createdTs = o.createdMs ?: clock(), expiresTs = o.expiresMs ?: expiry[o.orderId],
        )
    }

    private fun toNovig(f: KalshiFill): NovigFill = NovigFill(
        fillId = f.fillId, orderId = f.orderId, clientId = null, marketId = outcomes.marketOf(f.ticker),
        outcomeId = outcomes.outcomeOf(f.ticker, f.outcomeSide) ?: "${f.ticker}|${f.outcomeSide}",
        qty = (f.count * 100.0).roundToLong(), cost = f.count * f.price, taker = f.taker, fee = f.fee, ts = f.ts ?: clock(),
    )

    private fun asNovig(e: KalshiApiException): NovigApiException = when {
        e.status == 429 -> NovigApiException(429, "RATE_LIMITED", e.detail)
        e.isAuth -> NovigApiException(e.status, "KEY_REFUSED", e.detail)
        e.status == 400 || e.status == 409 || e.status == 422 -> NovigApiException(400, e.code?.uppercase() ?: "REFUSED", e.detail)
        else -> NovigApiException(e.status, e.code, e.detail)
    }
}
