package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.novig.NovigParlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

/**
 * What the OTHER makers charge, learned from Novig itself (MK12, Tj 2026-11-19: "figure out the best prices to offer the parlays at that still have a profitable edge but also get filled").
 *
 * Until now the maker fitted its price to the parlays that TRADED: about 10 priced trades an hour, and only the winning quote of each. `POST /rfq/v2/quote/unauthenticated` (docs.novig.com/affiliates/parlay-pricing,
 * keyless, 1 request a second per address) opens a real 3-second auction for any set of legs and returns the best price any maker offered, so one probe is one observation of the competition for the very RFQs
 * we can price, whether or not a user ever trades them. [RfqProbe] asks for a sample of the RFQs the maker priced, [RfqMarketModel] keeps what the market answered as a gap over OUR fair, per class of parlay,
 * and [RfqMarketModel.best] turns that demand curve into the quote that maximises the expected profit on what it ties up.
 */
class RfqMarketModel(private val keep: Int = 600, private val maxAgeMs: Long = 4 * 3_600_000L) {
    /** One probe's answer: the market's best price minus our fair (probability, 0.01 = one point of pot), null when no maker quoted at all. */
    private class Obs(val atMs: Long, val gap: Double?)

    private val byClass = HashMap<String, ArrayDeque<Obs>>()

    /** Records the answer under the class and under its coarser parent (the league left out), so a thin league falls back on what the whole kind of parlay shows. */
    @Synchronized fun observe(cls: String, fair: Double, market: Double?, atMs: Long) {
        for (k in listOf(cls, coarse(cls)).distinct()) {
            val q = byClass.getOrPut(k) { ArrayDeque() }
            q.addLast(Obs(atMs, market?.let { it - fair }))
            while (q.size > keep) q.removeFirst()
            while (q.isNotEmpty() && atMs - q.first().atMs > maxAgeMs) q.removeFirst()
        }
    }

    @Synchronized fun samples(cls: String): Int = byClass[cls]?.size ?: 0

    /** The share of the market's answers a quote [c] over our fair (probability) would have beaten: a strictly lower price wins; no quote from anyone is a win. Null with no observations. */
    @Synchronized fun winShare(cls: String, c: Double): Double? {
        val q = byClass[cls]?.takeIf { it.isNotEmpty() } ?: return null
        return q.count { it.gap == null || it.gap > c + 1e-9 }.toDouble() / q.size
    }

    /** Per class: observations, the share nobody quoted, and the quartiles of the gap among the answered ones (points of pot). */
    data class ClassStat(val cls: String, val n: Int, val noQuote: Double, val p25: Double?, val median: Double?, val p75: Double?)

    @Synchronized fun summary(): List<ClassStat> = byClass.entries.filter { it.value.isNotEmpty() }.map { (k, q) ->
        val g = q.mapNotNull { it.gap }.sorted()
        fun at(f: Double) = g.takeIf { it.isNotEmpty() }?.let { it[minOf(it.size - 1, (it.size * f).toInt())] * 100.0 }
        ClassStat(k, q.size, q.count { it.gap == null }.toDouble() / q.size, at(0.25), at(0.5), at(0.75))
    }.sortedByDescending { it.n }

    /**
     * The margin (probability points over our fair, as a fraction) that maximises the expected profit on the capital a quote ties up, or null while fewer than [RfqConfig.probeMinSamples] answers are in. At
     * a quote [c] over the fair the win share is [winShare]; a win earns c per dollar of pot, less an allowance for our fair's own error (half the per-leg points floor) and the cost of the collateral held
     * (collateral per dollar of pot is 1 - price, held [leadDays] + a quarter day, at [RfqConfig.minReturn] a day): net = c - allowance - cost. The score is win share x net, searched upward from [floorC].
     */
    fun best(cls: String, fair: Double, nLegs: Int, leadDays: Double, floorC: Double, cfg: RfqConfig): Double? {
        // the league's own answers when there are enough, else the kind of parlay across leagues
        val obs = synchronized(this) { byClass[cls]?.takeIf { it.size >= cfg.probeMinSamples } ?: byClass[coarse(cls)] }?.toList() ?: return null
        if (obs.size < cfg.probeMinSamples) return null
        val allowance = 0.5 * cfg.minPoints * max(1, nLegs - 1)
        val hold = max(1.0, leadDays + 0.25)
        var bestC: Double? = null
        var bestScore = 0.0
        var c = max(floorC, 0.0005)
        val top = floorC + 0.06
        while (c <= top + 1e-12) {
            val price = fair + c
            if (price >= 0.999) break
            val win = obs.count { it.gap == null || it.gap > c + 1e-9 }.toDouble() / obs.size
            val net = c - allowance - cfg.minReturn * hold * (1.0 - price)
            if (net > 0.0 && win * net > bestScore + 1e-12) { bestScore = win * net; bestC = c }
            c += 0.0005
        }
        return bestC
    }

    companion object {
        /**
         * The class a parlay belongs to for the market's answers: legs, kind of legs, the league and the size of our fair, as `legs|mix|league|band`. The league matters most: on 7,626 priced game-line parlays the
         * market paid a median 0.31 points over our fair in the NBA, 0.35 in the NHL, 0.45 in NCAAB, 1.27 in ATP tennis (a quote 1 point over won 13%, 4%, 26% and 61% of them).
         */
        fun classOf(legs: List<LegFair>, fair: Double): String {
            val n = legs.size.let { if (it >= 5) "5+" else it.toString() }
            val prop = legs.any { it.marketType.isBlank() || (it.marketType !in RfqPricer.GAME_LINES && !it.marketType.endsWith("_1H")) }
            val sameGame = legs.map { it.eventId }.toSet().size != legs.size
            val mix = when { prop && sameGame -> "psg"; prop -> "prop"; sameGame -> "sg"; else -> "gl" }
            val leagues = legs.map { it.league }.filter { it.isNotBlank() }.toSet()
            val league = when { leagues.size == 1 -> leagues.first(); leagues.isEmpty() -> "?"; else -> "multi" }
            val band = when { fair < 0.15 -> "lo"; fair < 0.40 -> "mid"; else -> "hi" }
            return "$n|$mix|$league|$band"
        }

        /** The same class across leagues: `legs|mix|*|band`. */
        fun coarse(cls: String): String = cls.split('|').let { if (it.size == 4) "${it[0]}|${it[1]}|*|${it[3]}" else cls }
    }
}

/**
 * Asks Novig what the market pays for a sample of the parlays the maker priced (MK12). At most one request per [gapMs] (the documented limit is 1 a second per address), several in flight at once because
 * each opens a 3-second auction. The sample is spread over the classes: the next probe is always the queued RFQ of the class with the fewest answers in [model]. A parlay set is asked at most once per
 * [dedupeMs] (the probe's own RFQ comes back on the maker's socket with the same legs: it must not start another). Stops for [Retry-After] on a 429 and for a minute on repeated failures.
 */
class RfqProbe(
    scope: CoroutineScope,
    private val model: RfqMarketModel,
    private val journal: (RfqRow) -> Unit,
    private val enabled: () -> Boolean,
    private val ask: suspend (List<String>) -> NovigParlay.Result,
    private val clock: () -> Long = System::currentTimeMillis,
    workers: Int = 4,
    private val gapMs: Long = 1_150L,
    private val dedupeMs: Long = 40_000L,
    private val perClassQueue: Int = 4,
    startDelayMs: Long = 2_000L,
) {
    class Item(val rfqId: String, val ids: List<String>, val cls: String, val fair: Double, val ourPrice: Double?, val nLegs: Int, val queuedMs: Long)

    private val queues = LinkedHashMap<String, ArrayDeque<Item>>()
    private val recent = HashMap<List<String>, Long>()
    private var nextSlotMs = 0L
    private var pausedUntilMs = 0L
    private var failures = 0

    @Volatile var sent = 0L; private set
    @Volatile var priced = 0L; private set
    @Volatile var noQuote = 0L; private set
    @Volatile var refused = 0L; private set
    @Volatile var limited = 0L; private set
    @Volatile var failed = 0L; private set

    /** Non-blocking: remember this priced RFQ as a candidate for a probe. */
    fun offer(item: Item) {
        if (!enabled()) return
        val key = item.ids.sorted()
        synchronized(this) {
            val now = clock()
            if (recent.size > 4_000) recent.entries.removeIf { now - it.value > dedupeMs }
            if (recent[key]?.let { now - it < dedupeMs } == true) return
            val q = queues.getOrPut(item.cls) { ArrayDeque() }
            if (q.any { it.ids.sorted() == key }) return
            q.addLast(item)
            while (q.size > perClassQueue) q.removeFirst()
        }
    }

    /** The queued RFQ of the least-sampled class, and the time to wait before asking (the 1-a-second spacing across workers), or null. */
    @Synchronized private fun take(): Pair<Item, Long>? {
        val now = clock()
        if (now < pausedUntilMs) return null
        // a queued RFQ that has waited longer than the price it was judged at lasts is stale for the comparison: the market and our fair both move
        for (q in queues.values) while (q.isNotEmpty() && now - q.first().queuedMs > 45_000L) q.removeFirst()
        val cls = queues.entries.filter { it.value.isNotEmpty() }.minByOrNull { model.samples(it.key) * 1_000_000L + it.value.first().queuedMs % 1_000_000L }?.key ?: return null
        val item = queues[cls]!!.removeFirst()
        val key = item.ids.sorted()
        if (recent[key]?.let { now - it < dedupeMs } == true) return null
        recent[key] = now
        val slot = max(now, nextSlotMs); nextSlotMs = slot + gapMs
        return item to (slot - now)
    }

    init {
        repeat(workers) { w ->
            scope.launch {
                delay(startDelayMs + 300L * w)
                while (isActive) {
                    val t = if (enabled()) take() else null
                    if (t == null) { delay(250L); continue }
                    val (item, wait) = t
                    if (wait > 0) delay(wait)
                    run(item)
                }
            }
        }
    }

    private suspend fun run(item: Item) {
        val t0 = clock()
        val res = ask(item.ids)
        val ms = clock() - t0
        sent++
        var status: String; var price: Double? = null
        when (res) {
            is NovigParlay.Result.Priced -> { priced++; failures = 0; status = "PRICED"; price = res.quote.price; model.observe(item.cls, item.fair, price, clock()) }
            is NovigParlay.Result.NoPrice -> { noQuote++; failures = 0; status = "NO_PRICE"; model.observe(item.cls, item.fair, null, clock()) }
            is NovigParlay.Result.Refused -> { refused++; status = "REFUSED" }
            is NovigParlay.Result.Limited -> { limited++; status = "LIMITED"; synchronized(this) { pausedUntilMs = clock() + (res.retryAfterS ?: 30) * 1_000L } }
            is NovigParlay.Result.Failed -> { failed++; status = "FAILED"; if (++failures >= 5) { failures = 0; synchronized(this) { pausedUntilMs = clock() + 60_000L } } }
        }
        runCatching {
            journal(RfqRow(clock(), "PROBE", item.rfqId, buildJsonObject {
                put("cls", item.cls); put("n", item.nLegs); put("fair", item.fair); put("st", status); put("ms", ms)
                price?.let { put("mkt", it) }; item.ourPrice?.let { put("ours", it) }
                // a sample of probes keeps the leg ids, so a later job can name and grade the legs (Novig's open catalog forgets an id once its market closes)
                if (item.rfqId.hashCode() and 15 == 0) put("ids", item.ids.joinToString(","))
            }.toString()))
        }
    }

    @Synchronized fun queued(): Int = queues.values.sumOf { it.size }

    fun line(): String = "probe: $sent sent, $priced priced, $noQuote nobody quoted, $refused refused, $limited limited, $failed failed"
}
