package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * The channel a market is subscribed on (NOVIG_API.md §26): [BOOK] is every resting order (the full ladder of each outcome, 16 tokens a market); [BBO] is only each outcome's best resting bid and its
 * size (8 tokens), which is all a taker's price needs. A [BBO] market's book is [NovigBook.partial].
 */
enum class BookChannel(val wire: String, val weight: Int) {
    BOOK("book", 16),
    BBO("bbo", 8),
}

/**
 * What the `lifecycle` channel (included in `book` and `bbo`) last said about a market: its [status] (OPEN, CLOSED, SETTLED; null until one is known), the last [transition] (OPEN, CLOSE, GRADE, START,
 * END, GOLIVE, UNLIVE) and whether the market is [live] (the last GOLIVE/UNLIVE seen; null if neither has been). `GOLIVE` voids every resting order and turns taker fees on.
 */
data class MarketLifecycle(val status: String?, val transition: String?, val live: Boolean?, val atMs: Long) {
    val open: Boolean get() = status == "OPEN"
    val closed: Boolean get() = status == "CLOSED" || status == "SETTLED"
}

/**
 * The order books the websocket keeps current, applied exactly as NOVIG_API.md §6 describes:
 * a snapshot sets the book and its `seq`, each delta must carry `seq + 1`, a gap marks the book
 * stale until a fresh snapshot arrives. Pure logic, no socket, so every rule is unit-testable.
 *
 * Novig's prose docs and its OpenAPI document word the same messages differently (an order is `order` or `orderId`, an outcome `outcome` or `outcomeId`; the OpenAPI adds an `update` delta
 * that lowers a resting order's size; a lifecycle delta is a plain transition or a `{kind, status}` object), and no real answer settles which a live server sends (NOVIG_API.md §26), so every
 * reader here takes both.
 */
class StreamBooks(private val clock: () -> Long = System::currentTimeMillis) {

    /** Set once by whoever records the books (the burst recorder's own connection); null for a scan's. */
    @Volatile
    var listener: BookListener? = null

    private val extras = java.util.concurrent.CopyOnWriteArrayList<BookListener>()
    private val fan = BookListener { id, at, ch -> listener?.onChange(id, at, ch); extras.forEach { it.onChange(id, at, ch) } }
    private fun sink(): BookListener? = if (listener != null || extras.isNotEmpty()) fan else null

    /** More listeners beside [listener]: the live engine's view of the app's one stream (it must not open a second connection, the key's `stream` bucket is shared). */
    fun addListener(l: BookListener) { extras += l }
    fun removeListener(l: BookListener) { extras -= l }

    private class Order(val outcome: String, val priceMilli: Int, val qty: Long)

    private class Level(val priceMilli: Int, val qty: Long)

    private class Market(
        var seq: Long,
        val channel: BookChannel,
        val orders: LinkedHashMap<String, Order>,
        /** [BookChannel.BBO] only: each outcome's best resting bid. */
        val top: LinkedHashMap<String, Level>,
        var stale: Boolean,
        var updatedAtMs: Long,
    )

    private class Life(var seq: Long, var status: String?, var transition: String?, var live: Boolean?, var atMs: Long)

    private val markets = HashMap<String, Market>()
    private val lives = HashMap<String, Life>()

    /** Market IDs whose book has a gap and needs a `snapshot` request. */
    val needsSnapshot: Set<String> get() = synchronized(this) { markets.filterValues { it.stale }.keys.toSet() }

    /** The channel [marketId]'s held state came in on, or null when nothing is held. */
    @Synchronized
    fun channelOf(marketId: String): BookChannel? = markets[marketId]?.channel

    /** What the lifecycle channel last said about [marketId], or null if it has said nothing. */
    @Synchronized
    fun lifecycle(marketId: String): MarketLifecycle? = lives[marketId]?.let { MarketLifecycle(it.status, it.transition, it.live, it.atMs) }

    /** Applies one market's `book` object from a snapshot message. */
    @Synchronized
    fun applySnapshot(marketId: String, book: JsonObject) {
        val seq = book["seq"]?.jsonPrimitive?.longOrNull ?: return
        val orders = LinkedHashMap<String, Order>()
        book["orders"]?.jsonObject?.forEach { (outcome, list) ->
            for (el in list.jsonArray) {
                val o = el.jsonObject
                val id = text(o, "order", "orderId") ?: continue
                val price = o["price"]?.jsonPrimitive?.content?.let(NovigText::priceMilli) ?: continue
                val qty = o["qty"]?.jsonPrimitive?.longOrNull ?: continue
                orders[id] = Order(outcome, price, qty)
            }
        }
        markets[marketId] = Market(seq, BookChannel.BOOK, orders, LinkedHashMap(), stale = false, updatedAtMs = clock())
        sink()?.let { l ->
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
        val held = markets[marketId]
        // A `book` delta that was already on its way when the market moved to `bbo` says nothing about the best bids held now.
        if (held != null && held.channel != BookChannel.BOOK) return true
        val m = held ?: if (seq == 1L) {
            // A market that opened under an event subscription starts at seq 0 with no snapshot.
            Market(0, BookChannel.BOOK, LinkedHashMap(), LinkedHashMap(), stale = false, updatedAtMs = clock()).also { markets[marketId] = it }
        } else {
            markets[marketId] = Market(seq, BookChannel.BOOK, LinkedHashMap(), LinkedHashMap(), stale = true, updatedAtMs = clock())
            sink()?.onChange(marketId, clock(), listOf(BookChange(BookChange.Kind.STALE)))
            return false
        }
        if (m.stale) return false
        if (seq <= m.seq) return true // already covered by a newer snapshot
        if (seq != m.seq + 1) {
            m.stale = true
            sink()?.onChange(marketId, clock(), listOf(BookChange(BookChange.Kind.STALE)))
            return false
        }
        val changes = if (sink() != null) ArrayList<BookChange>() else null
        (book["deltas"] as? JsonArray)?.forEach { el ->
            val d = el.jsonObject
            val order = text(d, "order", "orderId") ?: return@forEach
            when (d["kind"]?.jsonPrimitive?.content) {
                "add" -> {
                    val outcome = text(d, "outcome", "outcomeId") ?: return@forEach
                    val price = d["price"]?.jsonPrimitive?.content?.let(NovigText::priceMilli) ?: return@forEach
                    val qty = d["qty"]?.jsonPrimitive?.longOrNull ?: return@forEach
                    m.orders[order] = Order(outcome, price, qty)
                    changes?.add(BookChange(BookChange.Kind.ADD, outcome, price, qty))
                }
                "remove" -> m.orders.remove(order)?.let { o ->
                    changes?.add(BookChange(BookChange.Kind.REMOVE, o.outcome, o.priceMilli, o.qty, d["reason"]?.jsonPrimitive?.content))
                }
                // The OpenAPI's partial fill: the order stays at its price and queue place with a smaller size. Told to a listener as the prose's remove (a fill) and add of the rest.
                "update" -> {
                    val old = m.orders[order]
                    val remaining = d["remaining"]?.jsonPrimitive?.longOrNull
                    if (old != null && remaining != null) {
                        if (remaining <= 0L) {
                            m.orders.remove(order)
                            changes?.add(BookChange(BookChange.Kind.REMOVE, old.outcome, old.priceMilli, old.qty, "fill"))
                        } else {
                            m.orders[order] = Order(old.outcome, old.priceMilli, remaining)
                            changes?.add(BookChange(BookChange.Kind.REMOVE, old.outcome, old.priceMilli, old.qty, "fill"))
                            changes?.add(BookChange(BookChange.Kind.ADD, old.outcome, old.priceMilli, remaining))
                        }
                    }
                }
            }
        }
        m.seq = seq
        m.updatedAtMs = clock()
        if (changes != null && changes.isNotEmpty()) sink()?.onChange(marketId, clock(), changes)
        return true
    }

    /**
     * Applies one market's `bbo` object from a snapshot message: `{seq, bbo: {<outcome>: {price, qty}}}`, an outcome with no resting bid absent (OpenAPI `BboSnapshot`). The market is then held as
     * [BookChannel.BBO]: its book has only the best level of each outcome. False when the object could not be read (no `seq`).
     */
    @Synchronized
    fun applyBboSnapshot(marketId: String, bbo: JsonObject): Boolean {
        val seq = bbo["seq"]?.jsonPrimitive?.longOrNull ?: return false
        val top = LinkedHashMap<String, Level>()
        (bbo["bbo"] as? JsonObject)?.forEach { (outcome, level) -> level(level)?.let { top[outcome] = it } }
        markets[marketId] = Market(seq, BookChannel.BBO, LinkedHashMap(), top, stale = false, updatedAtMs = clock())
        return true
    }

    /**
     * Applies one market's `bbo` delta: `{seq, deltas: [{outcomeId, bbo: {price, qty} | null}]}`, a null or missing `bbo` meaning the outcome's last resting bid left the book. Returns false when
     * it revealed a gap (the state is stale and a snapshot must be requested), like [applyDelta].
     */
    @Synchronized
    fun applyBboDelta(marketId: String, bbo: JsonObject): Boolean {
        val seq = bbo["seq"]?.jsonPrimitive?.longOrNull ?: return true
        val held = markets[marketId]
        // A `bbo` delta that was already on its way when the market moved to `book` is not the book's business.
        if (held != null && held.channel != BookChannel.BBO) return true
        val m = held ?: if (seq == 1L) {
            Market(0, BookChannel.BBO, LinkedHashMap(), LinkedHashMap(), stale = false, updatedAtMs = clock()).also { markets[marketId] = it }
        } else {
            markets[marketId] = Market(seq, BookChannel.BBO, LinkedHashMap(), LinkedHashMap(), stale = true, updatedAtMs = clock())
            return false
        }
        if (m.stale) return false
        if (seq <= m.seq) return true
        if (seq != m.seq + 1) {
            m.stale = true
            return false
        }
        (bbo["deltas"] as? JsonArray)?.forEach { el ->
            val d = el as? JsonObject ?: return@forEach
            val outcome = text(d, "outcomeId", "outcome") ?: return@forEach
            val level = level(d["bbo"])
            if (level == null) m.top.remove(outcome) else m.top[outcome] = level
        }
        m.seq = seq
        m.updatedAtMs = clock()
        return true
    }

    /** Applies one market's `lifecycle` object from a snapshot: `{seq, status}`. */
    @Synchronized
    fun applyLifecycleSnapshot(marketId: String, life: JsonObject) {
        val seq = life["seq"]?.jsonPrimitive?.longOrNull ?: return
        val status = life["status"]?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val prior = lives[marketId]
        lives[marketId] = Life(seq, status ?: prior?.status, prior?.transition, prior?.live, clock())
    }

    /**
     * Applies one market's `lifecycle` delta: `{seq, deltas: ["GOLIVE"]}` (the prose) or `{seq, deltas: [{kind, status}]}` (the OpenAPI). A skipped seq makes the market's state stale, which asks for a
     * snapshot (the reply carries the lifecycle again). Returns false then.
     */
    @Synchronized
    fun applyLifecycleDelta(marketId: String, life: JsonObject): Boolean {
        val seq = life["seq"]?.jsonPrimitive?.longOrNull ?: return true
        val cur = lives[marketId]
        if (cur != null && seq <= cur.seq) return true
        val gap = cur != null && seq != cur.seq + 1
        val now = clock()
        val l = cur ?: Life(seq, null, null, null, now).also { lives[marketId] = it }
        (life["deltas"] as? JsonArray)?.forEach { el ->
            val kind: String?
            var status: String? = null
            if (el is JsonObject) {
                kind = text(el, "kind", "transition")
                status = el["status"]?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            } else {
                kind = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            val k = kind?.uppercase() ?: return@forEach
            l.transition = k
            // A transition's own status is what Novig names; the prose's plain transitions imply it for the three that change it.
            l.status = status ?: when (k) { "OPEN" -> "OPEN"; "CLOSE" -> "CLOSED"; "GRADE" -> "SETTLED"; else -> l.status }
            when (k) { "GOLIVE" -> l.live = true; "UNLIVE" -> l.live = false }
        }
        l.seq = seq
        l.atMs = now
        if (gap) {
            // The status in hand may have missed a CLOSE: unknown until the snapshot says.
            l.status = null
            markets[marketId]?.stale = true
            return false
        }
        return true
    }

    @Synchronized
    fun forget(marketIds: Collection<String>) {
        marketIds.forEach { markets.remove(it); lives.remove(it) }
    }

    @Synchronized
    fun clear() {
        markets.clear()
        lives.clear()
    }

    /** The current book, or null if unknown or stale. [NovigBook.fetchedAtMs] is its last change. A `bbo` market's book is [NovigBook.partial]: the best level of each outcome. */
    @Synchronized
    fun book(marketId: String): NovigBook? {
        val m = markets[marketId] ?: return null
        if (m.stale) return null
        if (m.channel == BookChannel.BBO) {
            return NovigBook(marketId, m.seq, m.top.mapValues { (_, l) -> listOf(BidLevel(l.priceMilli, l.qty)) }, m.updatedAtMs, partial = true)
        }
        val byOutcome = m.orders.values.groupBy { it.outcome }.mapValues { (_, orders) ->
            orders.groupBy { it.priceMilli }.map { (p, os) -> BidLevel(p, os.sumOf { it.qty }) }.sortedByDescending { it.priceMilli }
        }
        return NovigBook(marketId, m.seq, byOutcome, m.updatedAtMs)
    }

    @Synchronized
    fun knows(marketId: String): Boolean = markets[marketId]?.stale == false

    private fun text(o: JsonObject, vararg names: String): String? {
        for (n in names) (o[n] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { return it }
        return null
    }

    /** `{price, qty}` as a level, or null for a missing, null or unreadable one (and for no size: a level with nothing resting is no bid). */
    private fun level(el: JsonElement?): Level? {
        val o = el as? JsonObject ?: return null
        val price = (o["price"] as? JsonPrimitive)?.content?.let(NovigText::priceMilli) ?: return null
        val qty = (o["qty"] as? JsonPrimitive)?.longOrNull ?: return null
        return if (qty > 0L) Level(price, qty) else null
    }
}
