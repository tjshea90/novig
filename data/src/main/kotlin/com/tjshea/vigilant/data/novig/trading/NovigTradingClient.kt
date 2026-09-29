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
) {
    /** No more can happen to it: filled, canceled or refused. */
    val terminal: Boolean get() = status == "FILLED" || status == "CANCELED" || status == "REJECTED"
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
     */
    open suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String): String {
        val body = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "outcomeId" to JsonPrimitive(outcomeId),
                    "price" to JsonPrimitive(priceText(price)),
                    "qty" to JsonPrimitive(qty),
                    "tif" to JsonPrimitive(tif),
                    "clientId" to JsonPrimitive(clientId),
                ),
            ),
        )
        return json.decodeFromString(AcceptedDto.serializer(), signer.call("POST", "/v3/orders", body = body)).orderId
    }

    /** The order, or null while Novig answers 404 (it can, right after the 201). */
    open suspend fun order(orderId: String): NovigOrder? = try {
        json.decodeFromString(OrderDto.serializer(), signer.call("GET", "/v3/orders/$orderId")).toDomain()
    } catch (e: NovigApiException) {
        if (e.status == 404) null else throw e
    }

    /** Up to [limit] orders in [status] (newest first is not promised: the whole page is returned). */
    open suspend fun orders(status: String, limit: Int = 100): List<NovigOrder> =
        json.decodeFromString(OrderPageDto.serializer(), signer.call("GET", "/v3/orders", "limit=$limit&status=$status")).items.map { it.toDomain() }

    /** Fills of one [orderId], or every fill (up to [limit]) when null. */
    open suspend fun fills(orderId: String? = null, limit: Int = 500): List<NovigFill> {
        val query = buildString {
            append("limit=").append(limit)
            if (orderId != null) append("&order=").append(URLEncoder.encode(orderId, "UTF-8"))
        }
        // Query parameters are signed sorted by name: limit, order.
        return json.decodeFromString(FillPageDto.serializer(), signer.call("GET", "/v3/portfolio/fills", query)).items.map { it.toDomain() }
    }

    /** The subaccount's nonzero positions, or the ones in [marketId]. */
    open suspend fun positions(marketId: String? = null): List<NovigPosition> {
        val query = marketId?.let { "market=" + URLEncoder.encode(it, "UTF-8") }
        return json.decodeFromString(ListSerializer(PositionDto.serializer()), signer.call("GET", "/v3/portfolio/positions", query)).map { it.toDomain() }
    }

    /**
     * Ledger rows of [kind] for [subaccountKeyId] whose event was scheduled in ([startsAfterMs], [startsBeforeMs]) (both exclusive;
     * the docs filter these by the event's scheduled start), paged until [maxRows].
     */
    open suspend fun ledger(subaccountKeyId: String, kind: String, startsAfterMs: Long, startsBeforeMs: Long, maxRows: Int = 1_000): List<LedgerRow> {
        val out = ArrayList<LedgerRow>()
        var cursor: String? = null
        do {
            val query = buildString {
                append("kind=").append(kind)
                append("&limit=").append(minOf(500, maxRows))
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
        /** A grid price as Novig writes it: three decimals ("0.455", "0.050"). */
        fun priceText(price: Double): String = BigDecimal.valueOf(price).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()
    }
}

@Serializable
private data class BalanceDto(val balance: String)

@Serializable
private data class AcceptedDto(val orderId: String, val clientId: String? = null)

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
) {
    fun toDomain() = NovigOrder(orderId, clientId, marketId, outcomeId, price.toDouble(), qty, remaining, tif, status, createdTs)
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
