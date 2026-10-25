package com.tjshea.vigilant.data.scanner

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Where the fair odds' book quotes are lost, counted by feed (PX, Tj 2026-10-25: "many results only have 1 book behind the fair odds for alternate markets that are popular and available at many
 * books"): quotes a feed sent, quotes the scan's age limit left out, quotes at a priced line (atLine: after matching to Novig's games and lines), quotes [Freshness.freshFirst] left out beside fresher ones, quotes the devig left out (bad odds, one book a company), and quotes
 * that priced a line. The engine logs and clears it after every scan report. Pure counters.
 */
object QuoteCensus {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun add(stage: String, feed: String?, n: Int = 1) {
        if (n <= 0) return
        counts.getOrPut("$stage|${feed ?: "?"}") { AtomicLong() }.addAndGet(n.toLong())
    }

    fun snapshot(): Map<String, Long> = counts.mapValues { it.value.get() }

    /** A few worked examples per stage (the first [SAMPLES] of each reporting round), so a count can be checked against real quotes: which book, which game, which two prices. */
    private val samples = ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>>()

    fun sample(stage: String, text: String) {
        val l = samples.getOrPut(stage) { java.util.concurrent.CopyOnWriteArrayList() }
        if (l.size < SAMPLES) l.add(text)
    }

    /** One line: per stage, the feeds and their counts; then clears. */
    fun describeAndReset(): String {
        val snap = snapshot()
        counts.clear()
        val ex = samples.entries.sortedBy { it.key }.joinToString(" | ") { (k, v) -> "$k: " + v.joinToString("; ") }
        samples.clear()
        if (snap.isEmpty()) return "quote census: nothing counted"
        val stages = snap.entries.groupBy({ it.key.substringBefore('|') }, { it.key.substringAfter('|') to it.value })
        return (if (ex.isEmpty()) "" else "quote census samples: $ex\n") + "quote census: " + stages.entries.filter { it.key != BOOK_PRICED }.sortedBy { ORDER.indexOf(it.key).let { i -> if (i < 0) 99 else i } }
            .joinToString(" · ") { (stage, v) -> "$stage " + v.sortedByDescending { it.second }.take(14).joinToString(",") { "${it.first} ${it.second}" } } +
            outlierRates(stages[BOOK_OUT].orEmpty().toMap(), stages[BOOK_PRICED].orEmpty().toMap()).let { if (it.isEmpty()) "" else "\nbook outliers (out/(out+priced), books out 10+ times): $it" }
    }

    /**
     * PX11d (Tj 2026-10-25: "Do an audit to see if any book consistently posts outlier odds, and if so, consider it a mismatch on mapping"): each book's share of its lines that BadOdds left out, worst
     * first. A book out on a large share again and again is mismatched, not merely early.
     */
    internal fun outlierRates(out: Map<String, Long>, priced: Map<String, Long>, minOut: Long = 10, top: Int = 20): String =
        out.filter { it.value >= minOut }.map { (b, n) -> Triple(b, n, n.toDouble() / (n + (priced[b] ?: 0L))) }
            .sortedByDescending { it.third }.take(top)
            .joinToString(", ") { (b, n, r) -> "$b ${"%.1f".format(java.util.Locale.ROOT, r * 100)}% ($n of ${n + (priced[b] ?: 0L)})" }

    const val BOOK_OUT = "badOddsBook"
    const val BOOK_PRICED = "pricedBook"

    private const val SAMPLES = 6

    private val ORDER = listOf("sent", "tooOld", "atLine", "staleBesideFresh", "devigLeftOut", "priced", "crossSame", "crossDiff", "crossDiffBook", "crossDiffBp", "linesNow", "linesPlain", "badOdds", "pinnWin", "pinnFeeds", "pinnAge")
}
