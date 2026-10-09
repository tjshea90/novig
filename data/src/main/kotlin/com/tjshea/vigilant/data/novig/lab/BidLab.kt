package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.engine.PriceGrid
import kotlinx.serialization.Serializable
import java.util.Locale

/** One side of one Novig market with a fair price, as the bid lab sees it: pregame lines come from Vigilant's scan, live ones from Pinnacle's price on the Pinnodds feed. */
data class LabLine(
    val outcomeId: String,
    val marketId: String,
    val eventId: String,
    val event: String,
    val league: String,
    val kind: String,
    val selection: String,
    val startsTs: Long,
    val live: Boolean,
    val fair: Double,
    /** Novig's price to take this side now (a post-only bid must be under it); null = nothing on offer. */
    val offer: Double?,
    val bestBid: Double?,
    val books: Int,
    val fairAgeSec: Int = 0,
)

/** One paper bid recipe: how far under the fair, how long it rests, and whether it is pulled when the fair moves against it. */
data class BidVariant(val margin: Double, val ttlMs: Long, val guard: Boolean, val live: Boolean) {
    val name: String get() = (if (live) "live" else "pre") + "-m" + String.format(Locale.US, "%.0f", margin * 100) + "-t" + (if (ttlMs >= 60_000L) "${ttlMs / 60_000L}m" else "${ttlMs / 1000L}s") + (if (guard) "-guard" else "")
}

/** A paper bid when it went up: [price] is what it bid, [fair] what Vigilant (or Pinnacle) thought the side was worth then. */
@Serializable
data class BidLabBid(
    val id: String,
    val atMs: Long,
    val variant: String,
    val live: Boolean,
    val outcomeId: String,
    val marketId: String,
    val eventId: String,
    val event: String,
    val league: String,
    val kind: String,
    val selection: String,
    val startsTs: Long,
    val price: Double,
    val fair: Double,
    val offer: Double,
    val bestBid: Double? = null,
    val books: Int = 0,
    val expiresMs: Long,
)

/** What happened to a paper bid afterwards: a FILL (a trade went through its price: [value] is the trade's price, [strict] when strictly through it), a CANCEL (the guard), a CLOSE ([value] = the last fair seen before the start), a GRADE (WIN/LOSS in [text]). */
@Serializable
data class BidLabEvent(val id: String, val atMs: Long, val type: String, val value: Double? = null, val strict: Boolean = false, val text: String? = null)

/**
 * The paper bid lab (Tj, 2026-10-09: "Your goal is to find any way to profit ... spend a fair amount of research on make bids. So far my make bids have been more profitable than take bids"; RESEARCH.md §122).
 * Vigilant's real bids filled 90 times in two weeks; this runs MANY bid recipes ([BidVariant]: margins, how long a bid rests, a cancel-when-the-fair-moves guard) on EVERY line the bid desk looks at, side by
 * side, against Novig's real trade tape, so a day of it is worth months of real bids. A paper bid is filled when a trade on its outcome prints at or under its price after it went up (the queue ahead of
 * it is unknown: [BidLabEvent.strict] marks a trade strictly through the price, which certainly filled it). A fill is followed to the last fair seen before the start (its CLV) and to the settled result.
 * **It places nothing and is given no way to**: only the lines it is handed, the public trade tape and the public market.
 */
class BidLab(
    private val trades: suspend (marketId: String) -> List<TrapGuard.Trade>,
    private val market: suspend (marketId: String) -> NovigMarket?,
    private val bidJournal: DayJournal<BidLabBid>,
    private val eventJournal: DayJournal<BidLabEvent>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val variants: List<BidVariant> = VARIANTS,
) {
    private class Active(val bid: BidLabBid, val variant: BidVariant) {
        var lastFair: Double = bid.fair
        var filledAtMs: Long? = null
        var closed = false
    }

    private val lock = Any()
    private val active = LinkedHashMap<String, Active>()          // id -> bid not yet over, or filled and not yet graded
    private val byKey = HashMap<String, String>()                  // variant|outcome -> id of its resting bid
    private val lastFairOf = HashMap<String, Double>()             // outcomeId -> newest fair seen
    private var seq = 0L

    @Volatile var posted = 0L; private set
    @Volatile var filled = 0L; private set
    @Volatile var lastObservedMs = 0L; private set

    /** A pass of lines: updates the fair of every bid up, pulls the guarded ones the fair has left, closes the filled ones whose game started, and puts up a bid per recipe on each line that has none. */
    fun observe(lines: List<LabLine>, now: Long) {
        lastObservedMs = now
        val out = ArrayList<BidLabBid>()
        val events = ArrayList<BidLabEvent>()
        synchronized(lock) {
            for (l in lines) lastFairOf[l.outcomeId] = l.fair
            val it = active.values.iterator()
            while (it.hasNext()) {
                val a = it.next()
                lastFairOf[a.bid.outcomeId]?.let { a.lastFair = it }
                if (a.filledAtMs != null) {
                    if (now > a.bid.startsTs + FORGET_MS) { byKey.remove(key(a.variant, a.bid.outcomeId)); it.remove(); continue }
                    if (!a.closed && now >= a.bid.startsTs) { a.closed = true; events += BidLabEvent(a.bid.id, now, "CLOSE", a.lastFair) }
                    continue
                }
                if (now >= a.bid.expiresMs) { byKey.remove(key(a.variant, a.bid.outcomeId)); it.remove(); continue }
                if (a.variant.guard && a.lastFair < a.bid.price * GUARD) {
                    events += BidLabEvent(a.bid.id, now, "CANCEL", a.lastFair)
                    byKey.remove(key(a.variant, a.bid.outcomeId)); it.remove()
                }
            }
            for (l in lines) {
                val offer = l.offer ?: continue
                for (v in variants) {
                    if (v.live != l.live || key(v, l.outcomeId) in byKey) continue
                    if (active.size >= MAX_ACTIVE) continue
                    if (l.startsTs - now < MIN_TO_START_MS && !l.live) continue
                    val p = PriceGrid.floor(l.fair / (1.0 + v.margin)) ?: continue
                    if (p < MIN_PRICE || p > MAX_PRICE || p >= offer - TICK) continue
                    val end = minOf(now + v.ttlMs, if (l.live) Long.MAX_VALUE else l.startsTs - STOP_MS)
                    if (end <= now) continue
                    val bid = BidLabBid("${now.toString(36)}-${(seq++).toString(36)}", now, v.name, l.live, l.outcomeId, l.marketId, l.eventId, l.event, l.league, l.kind, l.selection, l.startsTs, p, l.fair, offer, l.bestBid, l.books, end)
                    active[bid.id] = Active(bid, v)
                    byKey[key(v, l.outcomeId)] = bid.id
                    out += bid
                }
            }
            posted += out.size
        }
        runCatching { bidJournal.appendAll(out) }
        runCatching { eventJournal.appendAll(events) }
    }

    private fun key(v: BidVariant, outcomeId: String) = v.name + "|" + outcomeId

    /**
     * Picks the paper bids back up from the journals after a stop or restart (Tj, 2026-10-09: "if I intermittently turn on and off both research mode switches, does the app delete the data in between"): the
     * journals were never touched, but the bids still resting and the fills waiting for their close and result lived in memory only. A bid that was neither filled nor over is put back up; a fill that
     * has no GRADE yet goes back to waiting for its close and result. Call once before [observe]; a second call adds nothing already known.
     */
    fun restore(bids: List<BidLabBid>, events: List<BidLabEvent>, now: Long) {
        val byId = events.groupBy { it.id }
        val named = variants.associateBy { it.name }
        synchronized(lock) {
            for (b in bids) {
                if (b.id in active) continue
                val v = named[b.variant] ?: continue
                val evs = byId[b.id].orEmpty()
                if (evs.any { it.type == "CANCEL" || it.type == "GRADE" }) continue
                val fill = evs.firstOrNull { it.type == "FILL" }
                val a = Active(b, v)
                if (fill != null) {
                    if (now > b.startsTs + FORGET_MS) continue
                    a.filledAtMs = fill.atMs
                    evs.lastOrNull { it.type == "CLOSE" }?.let { a.closed = true; it.value?.let { f -> a.lastFair = f } }
                } else if (now >= b.expiresMs) continue
                active[b.id] = a
                byKey[key(v, b.outcomeId)] = b.id
            }
            posted = maxOf(posted, bids.size.toLong())
            filled = maxOf(filled, events.count { it.type == "FILL" }.toLong())
            seq = maxOf(seq, bids.size.toLong())
        }
    }

    /**
     * Reads the trade tape of up to [maxMarkets] markets that have bids up (the most bids first) and fills the bids a trade went through; then grades the filled bids whose game is over by reading the
     * market's own settled status. Public reads only; safe to call from one coroutine at a time.
     */
    suspend fun poll(now: Long, maxMarkets: Int = MAX_MARKETS) {
        val todo: List<String>
        synchronized(lock) { todo = active.values.filter { it.filledAtMs == null }.groupBy { it.bid.marketId }.entries.sortedByDescending { it.value.size }.take(maxMarkets).map { it.key } }
        for (m in todo) {
            val tape = runCatching { trades(m) }.getOrNull() ?: continue
            val events = ArrayList<BidLabEvent>()
            synchronized(lock) {
                for (a in active.values) {
                    if (a.bid.marketId != m || a.filledAtMs != null) continue
                    val t = tape.filter { it.outcomeId == a.bid.outcomeId && it.atMs > a.bid.atMs + LATENCY_MS && it.atMs <= minOf(a.bid.expiresMs, now) && it.price <= a.bid.price + 1e-9 }.minByOrNull { it.atMs } ?: continue
                    a.filledAtMs = t.atMs   // the side stays held (no second bid on it) until the fill is graded, as the real desk holds a filled side
                    filled++
                    events += BidLabEvent(a.bid.id, t.atMs, "FILL", t.price, strict = t.price < a.bid.price - 1e-9)
                }
            }
            runCatching { eventJournal.appendAll(events) }
        }
        val toGrade: List<Active>
        synchronized(lock) { toGrade = active.values.filter { it.filledAtMs != null && now > it.bid.startsTs + GRADE_AFTER_MS }.take(MAX_GRADE) }
        for (a in toGrade) {
            val mk = runCatching { market(a.bid.marketId) }.getOrNull() ?: continue
            val status = mk.outcomes.firstOrNull { it.outcomeId == a.bid.outcomeId }?.status?.trim().orEmpty()
            if (status.isEmpty() || status.equals("TBD", true)) continue
            eventJournal.append(BidLabEvent(a.bid.id, now, "GRADE", text = status))
            synchronized(lock) { active.remove(a.bid.id); byKey.remove(key(a.variant, a.bid.outcomeId)) }
        }
    }

    /** What is in flight, for the status line. */
    fun counts(): Triple<Int, Int, Long> = synchronized(lock) { Triple(active.values.count { it.filledAtMs == null }, active.values.count { it.filledAtMs != null }, posted) }

    companion object {
        /** A bid is pulled when the fair has fallen below this x its price (its edge under 1%). */
        const val GUARD = 1.01
        const val MIN_PRICE = 0.10
        const val MAX_PRICE = 0.65
        const val TICK = 0.0025
        const val STOP_MS = 5 * 60_000L
        const val MIN_TO_START_MS = 6 * 60_000L
        const val LATENCY_MS = 300L
        const val GRADE_AFTER_MS = 3 * 3_600_000L
        const val MAX_MARKETS = 25
        const val MAX_GRADE = 20

        /** Paper bids in flight at most (memory and the journal stay small however many lines the desk reads). */
        const val MAX_ACTIVE = 4000

        /** A filled bid whose market never settles for the lab is forgotten this long after the start. */
        const val FORGET_MS = 48 * 3_600_000L

        /**
         * Pregame: how far under the fair (2-6%), how long a bid rests (30 min as the app's default, 2 h to see what waiting buys), with and without the guard. Live: 1-4% under Pinnacle's fair, resting two
         * minutes (a live fair goes old in seconds; the paper lab reads about every 20 s, so its guard acts a pass later than a real one would).
         */
        val VARIANTS: List<BidVariant> =
            listOf(0.02, 0.03, 0.04, 0.06).flatMap { m -> listOf(false, true).map { g -> BidVariant(m, 30 * 60_000L, g, live = false) } } +
                listOf(0.03, 0.04).map { m -> BidVariant(m, 120 * 60_000L, true, live = false) } +
                listOf(0.01, 0.02, 0.03, 0.04).flatMap { m -> listOf(false, true).map { g -> BidVariant(m, 120_000L, g, live = true) } }
    }
}

/** A pregame [com.tjshea.vigilant.data.novig.trading.maker.MakerLine] as a paper-bid line, or null when it has no usable fair (none, old, the player is out, fewer than two books) or nothing on offer. */
fun labLineOf(l: com.tjshea.vigilant.data.novig.trading.maker.MakerLine, now: Long): LabLine? {
    val fair = l.fair ?: return null
    if (l.fairOld || l.live || l.unavailable != null || l.books < 2) return null
    val offer = l.offer ?: return null
    return LabLine(l.outcomeId, l.market.marketId, l.market.eventId, l.eventName, l.league, l.kind.name, l.selection, l.startsTs, false, fair, offer, l.bestBid, l.books, l.fairAsOfMs?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0)
}
