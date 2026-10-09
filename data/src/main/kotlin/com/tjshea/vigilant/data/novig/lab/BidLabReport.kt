package com.tjshea.vigilant.data.novig.lab

import java.util.Locale

/** The bid lab's tables (Diagnostics and the research file): per recipe, then slices of the recipe that did best. Pure over the two journals. */
object BidLabReport {
    private fun pct(x: Double?) = if (x == null) "n/a" else String.format(Locale.US, "%+.1f%%", 100 * x)

    private class Row(val bid: BidLabBid, val fill: BidLabEvent?, val close: Double?, val grade: String?, val cancelled: Boolean)

    private fun rows(bids: List<BidLabBid>, events: List<BidLabEvent>): List<Row> {
        val by = events.groupBy { it.id }
        return bids.map { b ->
            val ev = by[b.id].orEmpty()
            Row(b, ev.firstOrNull { it.type == "FILL" }, ev.lastOrNull { it.type == "CLOSE" }?.value, ev.lastOrNull { it.type == "GRADE" }?.text, ev.any { it.type == "CANCEL" })
        }
    }

    private fun profit(r: Row): Double? = when (r.grade?.trim()?.uppercase(Locale.US)) {
        null -> null
        "WIN" -> 1.0 / r.bid.price - 1.0
        "LOSS" -> -1.0
        "PUSH" -> 0.0
        else -> r.grade.toDoubleOrNull()?.let { it / r.bid.price - 1.0 }
    }

    private fun stats(rs: List<Row>): String {
        val fills = rs.filter { it.fill != null }
        val strict = fills.count { it.fill!!.strict }
        val evPost = rs.map { it.bid.fair / it.bid.price - 1.0 }.average()
        val clvs = fills.mapNotNull { r -> r.close?.let { it / r.bid.price - 1.0 } }
        val profits = fills.mapNotNull(::profit)
        val bidHours = rs.sumOf { (minOf(it.fill?.atMs ?: it.bid.expiresMs, it.bid.expiresMs) - it.bid.atMs).coerceAtLeast(0L) } / 3_600_000.0
        return "${rs.size} posted, ${fills.size} filled (${String.format(Locale.US, "%.1f", 100.0 * fills.size / rs.size.coerceAtLeast(1))}%, $strict strictly through, " +
            "${String.format(Locale.US, "%.2f", if (bidHours > 0) fills.size / bidHours else 0.0)}/bid-hour) · EV at post ${pct(evPost)} · CLV ${pct(clvs.average().takeIf { clvs.isNotEmpty() })} on ${clvs.size}" +
            " · ${profits.count { it > 0 }}-${profits.count { it < 0 }} graded, ROI ${pct(profits.average().takeIf { profits.isNotEmpty() })}"
    }

    fun lines(bids: List<BidLabBid>, events: List<BidLabEvent>): List<String> {
        if (bids.isEmpty()) return listOf("no paper bids yet (it needs lines: pregame ones come from the Bids passes, live ones from the paper lab)")
        val all = rows(bids, events)
        val out = ArrayList<String>()
        out += "PAPER BIDS by recipe (m = margin under the fair, t = how long it rests, guard = pulled when the fair moves against it; a fill = a trade went through its price after it went up, queue ignored):"
        val byVariant = all.groupBy { it.bid.variant }
        for ((name, rs) in byVariant.entries.sortedBy { it.key }) out += "  $name: ${stats(rs)}"
        // Slices of the recipe with the most fills (at least 10), where the pattern would show.
        val best = byVariant.entries.filter { e -> e.value.count { it.fill != null } >= 10 }.maxByOrNull { e -> e.value.count { it.fill != null } }
        if (best != null) {
            out += "  slices of ${best.key} (the recipe with the most fills):"
            fun slice(title: String, key: (Row) -> String) {
                for ((k, rs) in best.value.groupBy(key).entries.sortedBy { it.key }) out += "    $title $k: ${stats(rs)}"
            }
            slice("kind") { it.bid.kind }
            slice("side") { if (it.bid.selection.contains("Under", true)) "Under" else if (it.bid.selection.contains("Over", true)) "Over" else "other" }
            slice("hours to start") { r ->
                val h = (r.bid.startsTs - r.bid.atMs) / 3_600_000.0
                when { r.bid.live -> "live"; h < 1 -> "<1h"; h < 3 -> "1-3h"; h < 6 -> "3-6h"; h < 12 -> "6-12h"; h < 24 -> "12-24h"; else -> "24h+" }
            }
            slice("league") { it.bid.league }
            slice("books behind the fair") { if (it.bid.books >= 5) "5+" else it.bid.books.toString() }
        }
        return out
    }
}
