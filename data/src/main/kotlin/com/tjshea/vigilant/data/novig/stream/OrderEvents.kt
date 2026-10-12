package com.tjshea.vigilant.data.novig.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The subaccount's own orders as Novig's private stream tells them (NOVIG_API.md §26; docs.novig.com/api/streaming/private): `open` (the order rests), `fill` (a trade: the traded price, the size, how much
 * is left), `cancel` (with a reason when the engine names one: GO_LIVE, MARKET_CLOSED, SETTLED, NEUTRALIZED) and `reject` ("We accepted the order, then refused it. No HTTP status reports this refusal. An
 * unfilled IOC, FOK or PO order also arrives as reject"). The order's own events come in this order: its fills, then open, cancel or reject.
 *
 * What it is for: knowing the moment an order has ended, instead of asking for it every 400 ms ([com.tjshea.vigilant.data.novig.trading.ApiBetPlacer]). It is never the record: a bet is logged from the REST
 * fills (the fee is there, and this event carries none), and a gap in the stream's seq, a heartbeat that is ahead of it, or a dropped connection makes it say nothing until the next snapshot ([live]).
 * Pure logic with the stream's JSON in, so every rule is a unit test.
 */
class OrderEvents(private val clock: () -> Long = System::currentTimeMillis) {

    enum class End { FILLED, CANCELED, REJECTED }

    data class Fill(val price: Double, val qty: Long, val atMs: Long)

    /** One order as far as the stream has told it. [end] is null while it rests (or while nothing but fills has been heard). */
    data class State(
        val orderId: String,
        val clientId: String?,
        val marketId: String?,
        val outcomeId: String?,
        val tif: String?,
        val restingQty: Long?,
        val fills: List<Fill>,
        val remaining: Long?,
        val end: End?,
        val reason: String?,
        val atMs: Long,
    ) {
        val filledQty: Long get() = fills.sumOf { it.qty }
    }

    private val orders = object : LinkedHashMap<String, State>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State>) = size > MAX_ORDERS
    }
    private val waiters = HashMap<String, CompletableDeferred<State?>>()
    private var seq = 0L
    private var lastEventAtMs = 0L

    /** True once a snapshot of the orders is in on the current connection and no gap has been seen since: the stream can be taken at its word about "nothing yet". */
    @get:Synchronized
    var live: Boolean = false
        private set

    /** Events heard since the app opened (Diagnostics). */
    @get:Synchronized
    var events = 0L
        private set

    @get:Synchronized
    var gaps = 0
        private set

    /** Starts a connection afresh: nothing is known to be current until the snapshot of the next subscribe. Waiters are let go with no answer (they ask REST). */
    @Synchronized
    fun reset() {
        live = false
        seq = 0L
        val ws = waiters.values.toList()
        waiters.clear()
        ws.forEach { it.complete(null) }
    }

    @Synchronized
    fun state(orderId: String): State? = orders[orderId]

    /** The orders resting right now by the last snapshot and what has been heard since. */
    @Synchronized
    fun resting(): List<State> = orders.values.filter { it.end == null && (it.restingQty ?: 0L) > 0L && it.tif !in IMMEDIATE }

    /** When the last order event arrived (ms), or 0. */
    @Synchronized
    fun lastEventAt(): Long = lastEventAtMs

    /**
     * The end of [orderId] as the stream tells it, waiting up to [timeoutMs] for it; null when the stream is not live, says nothing in time, or loses its place meanwhile (the caller asks REST). An
     * order that already ended returns at once.
     */
    suspend fun awaitEnd(orderId: String, timeoutMs: Long): State? {
        val wait: CompletableDeferred<State?>
        synchronized(this) {
            orders[orderId]?.takeIf { it.end != null }?.let { return it }
            if (!live) return null
            wait = waiters.getOrPut(orderId) { CompletableDeferred() }
        }
        return withTimeoutOrNull(timeoutMs) { wait.await() }
    }

    /** Takes back the wait kept for [orderId] (the placer is done with it). */
    @Synchronized
    fun forget(orderId: String) {
        waiters.remove(orderId)
    }

    /**
     * The `orders` object of a snapshot (`{seq, open: [...]}`) or of a delta (`{seq, deltas: [...]}`). Returns false when it revealed a gap, which also stops [live] until the next snapshot.
     */
    @Synchronized
    fun apply(body: JsonObject): Boolean {
        val s = (body["seq"] as? JsonPrimitive)?.longOrNull ?: return true
        val open = body["open"] as? JsonArray
        val now = clock()
        if (open != null) {
            // A snapshot: the open orders replace what rests; ended orders already heard stay (they are facts).
            val ids = HashSet<String>()
            for (el in open) {
                val o = el as? JsonObject ?: continue
                val id = text(o, "orderId") ?: continue
                ids += id
                val prior = orders[id]
                orders[id] = State(
                    id, text(o, "clientId") ?: prior?.clientId, text(o, "marketId") ?: prior?.marketId, text(o, "outcomeId") ?: prior?.outcomeId, text(o, "tif") ?: prior?.tif,
                    num(o, "qty") ?: prior?.restingQty, prior?.fills.orEmpty(), num(o, "qty") ?: prior?.remaining, prior?.end, prior?.reason, now,
                )
            }
            // An order that rested before and is not in the snapshot ended while no one was listening: unknown how, so no end is invented for it.
            for ((id, st) in orders.entries.toList()) if (id !in ids && st.end == null && st.restingQty != null && st.fills.isEmpty()) orders.remove(id)
            seq = s
            live = true
            return true
        }
        val deltas = body["deltas"] as? JsonArray ?: return true
        if (!live) return true
        if (s <= seq) return true
        if (s != seq + 1) {
            gaps++
            live = false
            val ws = waiters.values.toList()
            waiters.clear()
            ws.forEach { it.complete(null) }
            return false
        }
        for (el in deltas) {
            val d = el as? JsonObject ?: continue
            val id = text(d, "orderId") ?: continue
            events++
            lastEventAtMs = now
            val prior = orders[id] ?: State(id, null, null, null, null, null, emptyList(), null, null, null, now)
            val next = when (text(d, "kind")) {
                "open" -> prior.copy(
                    clientId = text(d, "clientId") ?: prior.clientId, marketId = text(d, "marketId") ?: prior.marketId, outcomeId = text(d, "outcomeId") ?: prior.outcomeId,
                    tif = text(d, "tif") ?: prior.tif, restingQty = num(d, "qty") ?: prior.restingQty, remaining = num(d, "qty") ?: prior.remaining, atMs = now,
                )
                "fill" -> {
                    val price = text(d, "price")?.toDoubleOrNull()
                    val qty = num(d, "qty")
                    val remaining = num(d, "remaining")
                    val fills = if (price != null && qty != null) prior.fills + Fill(price, qty, now) else prior.fills
                    prior.copy(
                        clientId = text(d, "clientId") ?: prior.clientId, outcomeId = text(d, "outcomeId") ?: prior.outcomeId, fills = fills, remaining = remaining ?: prior.remaining,
                        end = if (remaining == 0L) End.FILLED else prior.end, atMs = now,
                    )
                }
                "cancel" -> prior.copy(end = if (prior.end == End.FILLED) End.FILLED else End.CANCELED, reason = text(d, "reason") ?: prior.reason, atMs = now)
                "reject" -> prior.copy(end = if (prior.end == End.FILLED) End.FILLED else End.REJECTED, reason = text(d, "reason") ?: prior.reason, atMs = now)
                else -> prior
            }
            orders[id] = next
            if (next.end != null) waiters.remove(id)?.complete(next)
        }
        seq = s
        return true
    }

    /** The stream's heartbeat (`{heartbeat: {orders: <seq>, ts}}`): a seq ahead of the last heard is a message lost. Returns false then. */
    @Synchronized
    fun heartbeat(ordersSeq: Long): Boolean {
        if (!live || ordersSeq <= seq) return true
        gaps++
        live = false
        val ws = waiters.values.toList()
        waiters.clear()
        ws.forEach { it.complete(null) }
        return false
    }

    private fun text(o: JsonObject, name: String): String? = (o[name] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
    private fun num(o: JsonObject, name: String): Long? = (o[name] as? JsonPrimitive)?.longOrNull

    companion object {
        const val MAX_ORDERS = 500
        private val IMMEDIATE = setOf("IOC", "FOK")
    }
}
