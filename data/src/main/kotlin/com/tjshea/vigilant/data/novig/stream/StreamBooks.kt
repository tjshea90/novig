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

/** One change to a market's book as the push arrived, for a listener that replays books ([BookListener]; the scan never listens). */
data class BookChange(val kind: Kind, val outcome: String = "", val priceMilli: Int = 0, val qty: Long = 0L, val reason: String? = null) {
    enum class Kind {
        /** A snapshot is coming as ADDs: forget what was held. */
        CLEAR,
        ADD,

        /** [reason] is Novig's: `fill` or `cancel`. [outcome], [priceMilli] and [qty] are the removed order's own. */
        REMOVE,

        /** A gap in the sequence: the book is wrong until the next CLEAR. */
        STALE,
    }
}

/** Told of every change right after it is applied, with the phone's clock at arrival. Called on the socket's thread: keep it to handing the work on. */
fun interface BookListener {
    fun onChange(marketId: String, atMs: Long, changes: List<BookChange>)
}

/**
 * The order books the websocket keeps current, applied exactly as NOVIG_API.md §6 describes:
 * a snapshot sets the book and its `seq`, each delta must carry `seq + 1`, a gap marks the book
 * stale until a fresh snapshot arrives. Pure logic, no socket, so every rule is unit-testable.
 */
class StreamBooks(private val clock: () -> Long = System::currentTimeMillis) {

    /** Set once by whoever records the books (the burst recorder's own connection); null for a scan's. */
    @Volatile
    var listener: BookListener? = null

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
        listener?.let { l ->
            l.onChange(marketId, clock(), listOf(BookChange(BookChange.Kind.CLEAR)) + orders.values.map { BookChange(BookChange.Kind.ADD, it.outcome, it.priceMilli, it.qty) })
        }
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
            listener?.onChange(marketId, clock(), listOf(BookChange(BookChange.Kind.STALE)))
            return false
        }
        if (m.stale) return false
        if (seq <= m.seq) return true // already covered by a newer snapshot
        if (seq != m.seq + 1) {
            m.stale = true
            listener?.onChange(marketId, clock(), listOf(BookChange(BookChange.Kind.STALE)))
            return false
        }
        val changes = if (listener != null) ArrayList<BookChange>() else null
        (book["deltas"] as? JsonArray)?.forEach { el ->
            val d = el.jsonObject
            val order = d["order"]?.jsonPrimitive?.content ?: return@forEach
            when (d["kind"]?.jsonPrimitive?.content) {
                "add" -> {
                    val outcome = d["outcome"]?.jsonPrimitive?.content ?: return@forEach
                    val price = d["price"]?.jsonPrimitive?.content?.let(NovigText::priceMilli) ?: return@forEach
                    val qty = d["qty"]?.jsonPrimitive?.longOrNull ?: return@forEach
                    m.orders[order] = Order(outcome, price, qty)
                    changes?.add(BookChange(BookChange.Kind.ADD, outcome, price, qty))
                }
                "remove" -> m.orders.remove(order)?.let { o ->
                    changes?.add(BookChange(BookChange.Kind.REMOVE, o.outcome, o.priceMilli, o.qty, d["reason"]?.jsonPrimitive?.content))
                }
            }
        }
        m.seq = seq
        m.updatedAtMs = clock()
        if (changes != null && changes.isNotEmpty()) listener?.onChange(marketId, clock(), changes)
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
