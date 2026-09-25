package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The order books the websocket keeps current, applied exactly as NOVIG_API.md §6 describes:
 * a snapshot sets the book and its `seq`, each delta must carry `seq + 1`, a gap marks the book
 * stale until a fresh snapshot arrives. Pure logic, no socket, so every rule is unit-testable.
 */
class StreamBooks(private val clock: () -> Long = System::currentTimeMillis) {

    private class Order(val outcome: String, val priceMilli: Int, val qty: Long)

    private class Market(var seq: Long, val orders: LinkedHashMap<String, Order>, var stale: Boolean, var updatedAtMs: Long)

    private val markets = HashMap<String, Market>()

    /** Market IDs whose book has a gap and needs a `snapshot` request. */
    val needsSnapshot: Set<String> get() = synchronized(this) { markets.filterValues { it.stale }.keys.toSet() }

    /** Applies one market's `book` object from a snapshot message. */
    @Synchronized
    fun applySnapshot(marketId: String, book: JsonObject) {
        val seq = book["seq"]?.jsonPrimitive?.longOrNull ?: return
        val orders = LinkedHashMap<String, Order>()
        book["orders"]?.jsonObject?.forEach { (outcome, list) ->
            for (el in list.jsonArray) {
                val o = el.jsonObject
                val id = o["order"]?.jsonPrimitive?.content ?: o["orderId"]?.jsonPrimitive?.content ?: continue
                val price = o["price"]?.jsonPrimitive?.content?.let(NovigText::priceMilli) ?: continue
                val qty = o["qty"]?.jsonPrimitive?.longOrNull ?: continue
                orders[id] = Order(outcome, price, qty)
            }
        }
        markets[marketId] = Market(seq, orders, stale = false, updatedAtMs = clock())
    }

    /**
     * Applies one market's `book` delta. Returns false when it revealed a gap (the book is now
     * stale and a snapshot must be requested).
     */
    @Synchronized
    fun applyDelta(marketId: String, book: JsonObject): Boolean {
        val seq = book["seq"]?.jsonPrimitive?.longOrNull ?: return true
        val m = markets[marketId] ?: if (seq == 1L) {
            // A market that opened under an event subscription starts at seq 0 with no snapshot.
            Market(0, LinkedHashMap(), stale = false, updatedAtMs = clock()).also { markets[marketId] = it }
        } else {
            markets[marketId] = Market(seq, LinkedHashMap(), stale = true, updatedAtMs = clock())
            return false
        }
        if (m.stale) return false
        if (seq <= m.seq) return true // already covered by a newer snapshot
        if (seq != m.seq + 1) {
            m.stale = true
            return false
        }
        (book["deltas"] as? JsonArray)?.forEach { el ->
            val d = el.jsonObject
            val order = d["order"]?.jsonPrimitive?.content ?: return@forEach
            when (d["kind"]?.jsonPrimitive?.content) {
                "add" -> {
                    val outcome = d["outcome"]?.jsonPrimitive?.content ?: return@forEach
                    val price = d["price"]?.jsonPrimitive?.content?.let(NovigText::priceMilli) ?: return@forEach
                    val qty = d["qty"]?.jsonPrimitive?.longOrNull ?: return@forEach
                    m.orders[order] = Order(outcome, price, qty)
                }
                "remove" -> m.orders.remove(order)
            }
        }
        m.seq = seq
        m.updatedAtMs = clock()
        return true
    }

    @Synchronized
    fun forget(marketIds: Collection<String>) {
        marketIds.forEach { markets.remove(it) }
    }

    @Synchronized
    fun clear() = markets.clear()

    /** The current book, or null if unknown or stale. [NovigBook.fetchedAtMs] is its last change. */
    @Synchronized
    fun book(marketId: String): NovigBook? {
        val m = markets[marketId] ?: return null
        if (m.stale) return null
        val byOutcome = m.orders.values.groupBy { it.outcome }.mapValues { (_, orders) ->
            orders.groupBy { it.priceMilli }.map { (p, os) -> BidLevel(p, os.sumOf { it.qty }) }.sortedByDescending { it.priceMilli }
        }
        return NovigBook(marketId, m.seq, byOutcome, m.updatedAtMs)
    }

    @Synchronized
    fun knows(marketId: String): Boolean = markets[marketId]?.stale == false
}
