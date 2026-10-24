package com.tjshea.vigilant.data.scanner

/**
 * Whether each book's lines were matched to the right Novig market (Tj, 2026-10-24: "make sure sx bet and the other apis are properly matching lines and props").
 * A line wrongly matched (another player, another point, the sides swapped) shows as a price far from every other book's on the same outcome, so for each book
 * this compares its own devigged probability for the outcome with the median of the OTHER books' and counts the gaps over [GAP]. A healthy book has a few (news
 * moves one book first); a book with a high share is mismatching. Pure; the engine puts [text] in its scan note.
 */
object MatchHealth {
    /** A gap this big (probability points) between one book and the median of the others is not a price difference, it is a different line. */
    const val GAP = 0.15
    const val MIN_OTHERS = 2

    class Row(val book: String, var compared: Int = 0, var off: Int = 0, val examples: ArrayList<String> = ArrayList())

    fun of(opps: Collection<Opportunity>): List<Row> {
        val rows = LinkedHashMap<String, Row>()
        for (o in opps) {
            val idx = o.referenceIndex ?: continue
            val per = o.fair?.perBook ?: continue
            if (per.size < MIN_OTHERS + 1) continue
            val probs = per.map { bf -> bf.fairProbabilities.getOrNull(idx) }
            for (i in per.indices) {
                val mine = probs[i] ?: continue
                val others = probs.filterIndexed { j, p -> j != i && p != null }.map { it!! }.sorted()
                if (others.size < MIN_OTHERS) continue
                val median = if (others.size % 2 == 1) others[others.size / 2] else (others[others.size / 2 - 1] + others[others.size / 2]) / 2
                val r = rows.getOrPut(per[i].book.bookTitle) { Row(per[i].book.bookTitle) }
                r.compared++
                if (kotlin.math.abs(mine - median) > GAP) {
                    r.off++
                    if (r.examples.size < 2) r.examples += "${o.event.description.take(40)} ${o.selection.take(30)} (${o.marketLabel.take(24)}) ${"%.0f".format(mine * 100)}% vs ${"%.0f".format(median * 100)}%"
                }
            }
        }
        return rows.values.filter { it.compared >= 20 }.sortedByDescending { it.off.toDouble() / it.compared }
    }

    /** The worst few books as a note line, then how many lines the exchanges priced at all (SX Bet, Kalshi, Polymarket: Tj asked why SX never shows), or empty when nothing was compared. */
    fun text(opps: Collection<Opportunity>, top: Int = 5): String {
        val all = of(opps)
        val worst = all.filter { it.off > 0 }.take(top)
        val cover = all.filter { r -> EXCHANGES.any { r.book.contains(it, ignoreCase = true) } }
        val lines = ArrayList<String>()
        if (worst.isNotEmpty()) lines += "match check (book far from the others' median, share of lines): " + worst.joinToString(" · ") { "${it.book} ${it.off}/${it.compared}" + (it.examples.firstOrNull()?.let { e -> " e.g. $e" } ?: "") }
        if (cover.isNotEmpty()) lines += "exchange lines compared: " + cover.joinToString(" · ") { "${it.book} ${it.compared}" }
        return lines.joinToString("; ")
    }

    private val EXCHANGES = listOf("sx", "kalshi", "polymarket")
}
