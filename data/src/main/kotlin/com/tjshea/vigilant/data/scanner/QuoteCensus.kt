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
        return (if (ex.isEmpty()) "" else "quote census samples: $ex\n") + "quote census: " + snap.entries.groupBy({ it.key.substringBefore('|') }, { it.key.substringAfter('|') to it.value }).entries.sortedBy { ORDER.indexOf(it.key).let { i -> if (i < 0) 99 else i } }
            .joinToString(" · ") { (stage, v) -> "$stage " + v.sortedByDescending { it.second }.take(14).joinToString(",") { "${it.first} ${it.second}" } }
    }

    private const val SAMPLES = 6

    private val ORDER = listOf("sent", "tooOld", "atLine", "staleBesideFresh", "devigLeftOut", "priced", "crossSame", "crossDiff", "crossDiffBook", "crossDiffBp", "linesNow", "linesPlain")
}
