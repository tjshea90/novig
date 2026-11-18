package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Novig's RFQ socket (`GET /rfq/v3/ws`, docs `api/rfq/socket`, changelog 2026-11-12): the frames a parlay MAKER receives and sends (MK, Tj 2026-11-18: "I want to become a parlay maker").
 * Decimal amounts travel as JSON STRINGS both ways. Every message we send has a `nonce` (a whole number, new each time) and one method key; every event we get has `type` and `ts`.
 * The parser reads the documented examples exactly and ignores what it does not know ("Branch on `type`, because we may add new ones").
 */
object RfqProtocol {
    sealed interface Event { val ts: Long }
    /** An RFQ opened: answer before [expiresTs]; a quote must cover [minWager] (the user's minimum or $10). */
    data class Created(override val ts: Long, val rfqId: String, val outcomeIds: List<String>, val expiresTs: Long, val minWager: Double) : Event
    data class Closed(override val ts: Long, val rfqId: String) : Event
    data class QuoteCreated(override val ts: Long, val rfqId: String, val quoteId: String) : Event
    /** We won and must confirm within 1 second. */
    data class QuoteAccepted(override val ts: Long, val rfqId: String, val quoteId: String, val wager: Double, val price: Double, val clientId: String?) : Event
    data class QuoteExecuted(override val ts: Long, val rfqId: String, val quoteId: String, val wager: Double, val price: Double, val executedTs: Long) : Event
    data class QuoteUnfilled(override val ts: Long, val rfqId: String, val quoteId: String, val reason: String) : Event
    data class QuoteDropped(override val ts: Long, val rfqId: String, val quoteId: String?, val reason: String, val minWager: Double?, val requiredCollateral: Double?) : Event
    /** Any RFQ trade on Novig filled (never says which maker won). [offset] rises by one per execution: a jump means missed fills. */
    data class Executed(override val ts: Long, val offset: Long, val rfqId: String, val wager: Double, val price: Double) : Event
    /** Novig's answer to one of our messages: [kind] is the ack key (`subscribed`, `quoted`, `declined`, `confirmed`, `superseded`...). */
    data class Ack(override val ts: Long, val nonce: Long?, val kind: String, val rfqId: String?) : Event
    /** An error answering [nonce] (`MAKER_LOCKED`, `RFQ_CLOSED`, `CONFIRM_ALREADY_DECIDED`, `NO_CONFIRM_WINDOW`, `KYC_REQUIRED`...), or a frame with no nonce. */
    data class Refused(override val ts: Long, val nonce: Long?, val code: String, val message: String?) : Event
    data class Other(override val ts: Long, val type: String) : Event

    private val json = Json { ignoreUnknownKeys = true }
    private val ACKS = setOf("subscribed", "unsubscribed", "quoted", "superseded", "declined", "confirmed")

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.num(k: String) = text(k)?.toDoubleOrNull()
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull

    /** One text frame as an [Event], or null when it is not JSON or not an object. */
    fun parse(text: String, now: Long = System.currentTimeMillis()): Event? {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val ts = o.long("ts") ?: now
        val nonce = o.long("nonce")
        // An error carries `code` (or an `error` object with one), with or without a nonce.
        val err = (o["error"] as? JsonObject)
        val errCode = o.text("code") ?: err?.text("code")
        if (errCode != null && o.text("type") == null) return Refused(ts, nonce, errCode, o.text("message") ?: err?.text("message"))
        val type = o.text("type")
        if (type == null) {
            val key = ACKS.firstOrNull { o[it] != null } ?: return null
            val body = o[key]
            val rfqId = (body as? JsonObject)?.text("rfqId")
            return Ack(ts, nonce, key, rfqId)
        }
        fun id(k: String) = o.text(k) ?: return Other(ts, type)
        return when (type) {
            "rfq_created" -> Created(ts, id("rfqId"), (o["outcomeIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return Other(ts, type), o.long("expiresTs") ?: return Other(ts, type), o.num("minWager") ?: 10.0)
            "rfq_closed" -> Closed(ts, id("rfqId"))
            "quote_created" -> QuoteCreated(ts, id("rfqId"), id("quoteId"))
            "quote_accepted" -> QuoteAccepted(ts, id("rfqId"), id("quoteId"), o.num("wager") ?: return Other(ts, type), o.num("price") ?: return Other(ts, type), o.text("clientId"))
            "quote_executed" -> QuoteExecuted(ts, id("rfqId"), id("quoteId"), o.num("wager") ?: return Other(ts, type), o.num("price") ?: return Other(ts, type), o.long("executedTs") ?: ts)
            "quote_unfilled" -> QuoteUnfilled(ts, id("rfqId"), id("quoteId"), o.text("reason") ?: "")
            "quote_dropped" -> QuoteDropped(ts, id("rfqId"), o.text("quoteId"), o.text("reason") ?: "", o.num("minWager"), o.num("requiredCollateral"))
            "executed" -> Executed(ts, o.long("offset") ?: -1L, id("rfqId"), o.num("wager") ?: return Other(ts, type), o.num("price") ?: return Other(ts, type))
            else -> Other(ts, type)
        }
    }

    /** A decimal as the string Novig wants: [places] places, never an exponent, a dot whatever the phone's language. */
    fun dec(v: Double, places: Int): String = String.format(Locale.US, "%.${places}f", v)

    fun subscribe(nonce: Long, channels: List<String> = listOf("rfq", "quotes", "executions")): String =
        buildJsonObject { put("nonce", nonce); put("subscribe", buildJsonArray { channels.forEach { add(JsonPrimitive(it)) } }) }.toString()

    /** A quote: [price] is the probability the parlay wins (0.001 to 0.999), [maxWager] the largest stake it covers; one quote per RFQ, the first stands. */
    fun quote(nonce: Long, rfqId: String, price: Double, maxWager: Double, clientId: String? = null): String =
        buildJsonObject {
            put("nonce", nonce)
            put("quote", buildJsonObject {
                put("rfqId", rfqId); put("price", dec(price, 4)); put("maxWager", dec(maxWager, 2))
                if (clientId != null) put("clientId", clientId)
            })
        }.toString()

    fun decline(nonce: Long, rfqId: String): String = buildJsonObject { put("nonce", nonce); put("decline", buildJsonObject { put("rfqId", rfqId) }) }.toString()

    /** The last look: [confirmed] true fills the trade at the quoted price; false rejects it (and counts against the confirm rate). Answer within 1 second. */
    fun confirm(nonce: Long, rfqId: String, quoteId: String, confirmed: Boolean): String =
        buildJsonObject { put("nonce", nonce); put("confirm", buildJsonObject { put("rfqId", rfqId); put("quoteId", quoteId); put("confirmed", confirmed) }) }.toString()
}
