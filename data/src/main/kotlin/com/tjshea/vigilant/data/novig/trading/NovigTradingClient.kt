package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.net.URLEncoder

/** One order of the subaccount's ([status]: PENDING, OPEN, FILLED, CANCELED, REJECTED). [remaining]: contracts still resting. */
data class NovigOrder(
    val orderId: String,
    val clientId: String?,
    val marketId: String,
    val outcomeId: String,
    val price: Double,
    val qty: Long,
    val remaining: Long,
    val tif: String,
    val status: String,
    val createdTs: Long,
    /** When Novig cancels it by itself (an order placed with a `ttl`); null when it doesn't expire. */
    val expiresTs: Long? = null,
) {
    /**
     * No more can happen to it: filled, canceled or refused (the docs: "a partly filled order stays OPEN, track `remaining`, not the status", so
     * an open order with nothing left resting has filled all it will).
     */
    val terminal: Boolean get() = status == "FILLED" || status == "CANCELED" || status == "REJECTED" || (status == "OPEN" && remaining <= 0L)
}

/** One fill: [qty] contracts of 1¢ for [cost] dollars (`qty × price × 1¢`), plus a taker [fee] in dollars (none pregame). */
data class NovigFill(
    val fillId: String,
    val orderId: String,
    val clientId: String?,
    val marketId: String,
    val outcomeId: String,
    val qty: Long,
    val cost: Double,
    val taker: Boolean,
    val fee: Double,
    val ts: Long,
)

/** Contracts held in one outcome and what the wallet paid for them, net. */
data class NovigPosition(val marketId: String, val outcomeId: String, val qty: Long, val cost: Double)

/** One ledger row: [kind] FILL, FEE, MAKER_CREDIT, SETTLEMENT, TRANSFER_IN or TRANSFER_OUT; [amount] signed dollars; [ref] what it settles. */
data class LedgerRow(val transactionId: String, val kind: String, val amount: Double, val ref: String?, val ts: Long)

/**
 * The order/portfolio half of Novig's signed API for ONE subaccount (NOVIG_API.md §14-15), signed with that subaccount's `trading`
 * key to place and read, or its `trading::read` key to read. Every money and price field is a decimal string on the wire, parsed
 * exactly here; quantities are whole 1¢ contracts. A refusal is a [NovigApiException] with Novig's own code.
 */
open class NovigTradingClient(private val signer: NovigSignedClient, private val json: Json) {

    /** The subaccount's balance in dollars (`trading` key; a `trading::read` key can't read it). */
    open suspend fun balance(subaccountKeyId: String): Double {
        val body = signer.call("GET", "/v3/account/subaccounts/$subaccountKeyId/balance")
        return json.decodeFromString(BalanceDto.serializer(), body).balance.toDouble()
    }

    /**
     * Places one order. [price] is a probability on Novig's grid ([com.tjshea.vigilant.engine.PriceGrid]); [tif] `IOC` fills what it can at
     * that price or better and cancels the rest. The reply only says the order was queued: read it back ([order]) for what came of it.
     * [clientId] is echoed on the order and its fills and is what finds it again after a lost answer (Novig never checks it for uniqueness).
     * It must be a UUID: Novig parses it as one (Tj's first real order, 2026-09-29: "clientId: UUID parsing failed ... found `v`" for
     * "vigilant-<uuid>"), so [newClientId] makes them and anything else is refused here, before a request is sent.
     * [ttlMs]: how long the order may rest before Novig cancels it (NOVIG_API.md §17: "Required for `GTT`, optional for `PO`, forbidden otherwise");
     * a maker bid is `PO` (post only: refused rather than taking) with one.
     */
    open suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long? = null): String {
        require(isUuid(clientId)) { "clientId must be a UUID (Novig parses it as one): $clientId" }
        require(ttlMs == null || (tif == "PO" || tif == "GTT") && ttlMs > 0) { "ttl is only for PO and GTT orders, and positive: $tif $ttlMs" }
        require(tif != "GTT" || ttlMs != null) { "a GTT order needs a ttl" }
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                buildMap {
                    put("outcomeId", JsonPrimitive(outcomeId))
                    put("price", JsonPrimitive(priceText(price)))
                    put("qty", JsonPrimitive(qty))
                    put("tif", JsonPrimitive(tif))
                    ttlMs?.let { put("ttl", JsonPrimitive(it)) }
                    put("clientId", JsonPrimitive(clientId))
                },
            ),
        )
        return json.decodeFromString(AcceptedDto.serializer(), signer.call("POST", "/v3/orders", body = body)).orderId
    }

    /**
     * Asks Novig to cancel one resting order (`DELETE /v3/orders/{id}`, the `cancel` bucket). The answer only says the cancel was queued, with the
     * order's status at that moment ([NovigOrder.status]: `FILLED` means it was too late); null when Novig has no such order (404: it ended and left).
     */
    open suspend fun cancelOrder(orderId: String): String? = try {
        json.decodeFromString(CancelDto.serializer(), signer.call("DELETE", "/v3/orders/$orderId")).status
    } catch (e: NovigApiException) {
        if (e.status == 404) null else throw e
    }

    /**
     * Cancels every resting order of the subaccount in one market, one event, or (both null) everywhere (`DELETE /v3/orders`): how many cancels
     * Novig queued.
     */
    open suspend fun cancelOrders(marketId: String? = null, eventId: String? = null): Int {
        val query = listOfNotNull(eventId?.let { "event=" + percent(it) }, marketId?.let { "market=" + percent(it) }).joinToString("&").ifEmpty { null }
        return json.decodeFromString(CancelAllDto.serializer(), signer.call("DELETE", "/v3/orders", query)).canceled
    }

    /** The order, or null while Novig answers 404 (it can, right after the 201). */
    open suspend fun order(orderId: String): NovigOrder? = try {
        json.decodeFromString(OrderDto.serializer(), signer.call("GET", "/v3/orders/$orderId")).toDomain()
    } catch (e: NovigApiException) {
        if (e.status == 404) null else throw e
    }

    /**
     * The orders in [status] (`PENDING`, `OPEN`, `FILLED`, `CANCELED` or `REJECTED`), every page up to [MAX_ORDER_ROWS] (which end the first page is isn't
     * promised), only those on [outcomeId] when it's given.
     */
    open suspend fun orders(status: String, limit: Int = 500, outcomeId: String? = null): List<NovigOrder> {
        val out = ArrayList<NovigOrder>()
        var cursor: String? = null
        do {
            val query = buildString {
                append("limit=").append(limit.coerceIn(1, 5000)).append("&status=").append(status)
                outcomeId?.let { append("&outcome=").append(percent(it)) }
                cursor?.let { append("&after=").append(percent(it)) }
            }
            val page = json.decodeFromString(OrderPageDto.serializer(), signer.call("GET", "/v3/orders", query))
            out += page.items.map { it.toDomain() }
            cursor = page.next?.takeIf { it.isNotBlank() }
        } while (cursor != null && out.size < MAX_ORDER_ROWS)
        return out
    }

    /** Fills of one [orderId], or every fill (up to [limit]) when null. */
    open suspend fun fills(orderId: String? = null, limit: Int = 500): List<NovigFill> {
        val out = ArrayList<NovigFill>()
        var cursor: String? = null
        // Every page, up to [MAX_FILL_ROWS]: the docs don't say which end of the history the first page is.
        do {
            val query = buildString {
                append("limit=").append(minOf(limit, 500))
                if (orderId != null) append("&order=").append(percent(orderId))
                cursor?.let { append("&after=").append(percent(it)) }
            }
            val page = json.decodeFromString(FillPageDto.serializer(), signer.call("GET", "/v3/portfolio/fills", query))
            out += page.items.map { it.toDomain() }
            cursor = page.next?.takeIf { it.isNotBlank() }
        } while (cursor != null && out.size < MAX_FILL_ROWS)
        return out
    }

    /** The subaccount's nonzero positions, or the ones in [marketId]. */
    open suspend fun positions(marketId: String? = null): List<NovigPosition> {
        val query = marketId?.let { "market=" + URLEncoder.encode(it, "UTF-8") }
        return json.decodeFromString(ListSerializer(PositionDto.serializer()), signer.call("GET", "/v3/portfolio/positions", query)).map { it.toDomain() }
    }

    /**
     * Ledger rows of [kind] (every kind when null) for [subaccountKeyId] whose event was scheduled in ([startsAfterMs], [startsBeforeMs]) (both
     * exclusive; the docs filter these by the event's scheduled start), paged until [maxRows].
     */
    open suspend fun ledger(subaccountKeyId: String, kind: String?, startsAfterMs: Long, startsBeforeMs: Long, maxRows: Int = 1_000): List<LedgerRow> {
        val out = ArrayList<LedgerRow>()
        var cursor: String? = null
        do {
            val query = buildString {
                kind?.let { append("kind=").append(it).append('&') }
                append("limit=").append(minOf(500, maxRows))
                append("&startsAfter=").append(startsAfterMs)
                append("&startsBefore=").append(startsBeforeMs)
                cursor?.let { append("&after=").append(percent(it)) }
            }
            val page = json.decodeFromString(TransactionPageDto.serializer(), signer.call("GET", "/v3/account/subaccounts/$subaccountKeyId/transactions", query))
            out += page.items.map { it.toDomain() }
            cursor = page.next?.takeIf { it.isNotBlank() }
        } while (cursor != null && out.size < maxRows)
        return out
    }

    private fun percent(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        /** The most fills one call reads (8 + 1 per 50 rows of the `history` bucket: 2,000 rows cost 48 tokens of 512). */
        const val MAX_FILL_ROWS = 2_000

        /** The most orders one listing reads. */
        const val MAX_ORDER_ROWS = 2_000

        /** A grid price as Novig writes it: three decimals ("0.455", "0.050"). */
        fun priceText(price: Double): String = BigDecimal.valueOf(price).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()

        /** An id for an order or a transfer: a plain UUID, the only shape Novig's `clientId` / `clientTransferId` accepts. */
        fun newClientId(): String = java.util.UUID.randomUUID().toString()

        private val UUID_SHAPE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isUuid(id: String): Boolean = UUID_SHAPE.matches(id)
    }
}

@Serializable
private data class BalanceDto(val balance: String)

@Serializable
private data class AcceptedDto(val orderId: String, val clientId: String? = null)

@Serializable
private data class CancelDto(val orderId: String = "", val status: String = "")

@Serializable
private data class CancelAllDto(val canceled: Int = 0)

@Serializable
private data class OrderDto(
    val orderId: String,
    val clientId: String? = null,
    val marketId: String = "",
    val outcomeId: String = "",
    val price: String = "0",
    val qty: Long = 0,
    val remaining: Long = 0,
    val tif: String = "",
    val status: String = "",
    val createdTs: Long = 0,
    val expiresTs: Long? = null,
) {
    fun toDomain() = NovigOrder(orderId, clientId, marketId, outcomeId, price.toDouble(), qty, remaining, tif, status, createdTs, expiresTs)
}

@Serializable
private data class OrderPageDto(val items: List<OrderDto> = emptyList(), val next: String? = null)

@Serializable
private data class FillDto(
    val fillId: String,
    val orderId: String,
    val clientId: String? = null,
    val marketId: String = "",
    val outcomeId: String = "",
    val qty: Long = 0,
    val cost: String = "0",
    val taker: Boolean = false,
    val fee: String? = null,
    val ts: Long = 0,
) {
    fun toDomain() = NovigFill(fillId, orderId, clientId, marketId, outcomeId, qty, cost.toDouble(), taker, fee?.toDoubleOrNull() ?: 0.0, ts)
}

@Serializable
private data class FillPageDto(val items: List<FillDto> = emptyList(), val next: String? = null)

@Serializable
private data class PositionDto(val marketId: String, val outcomeId: String, val qty: Long = 0, val cost: String = "0") {
    fun toDomain() = NovigPosition(marketId, outcomeId, qty, cost.toDouble())
}

@Serializable
private data class TransactionDto(val transactionId: String = "", val kind: String = "", val amount: String = "0", val ref: String? = null, val ts: Long = 0) {
    fun toDomain() = LedgerRow(transactionId, kind, amount.toDouble(), ref, ts)
}

@Serializable
private data class TransactionPageDto(val items: List<TransactionDto> = emptyList(), val next: String? = null)
