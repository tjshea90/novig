package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.tracker.BetLedger
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Every bid, how fast it filled, whether the fair had moved under it, and what it paid (Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked
 * in the scan/diagnosis feature and all information logged so I can see how well my auto bids do"), pure. One [Row] per bid Vigilant posted: the bid as posted (price,
 * the fair its margin was taken from, the blend and the sharp book's own fair, the EV claimed, the book it was posted against, whether it led its side), what became of
 * it (rested how long, why it ended), its fill (how fast, the fair on the next scan, the EV then: a negative one was picked off) and the bet it became in the Tracker
 * (the close, CLV, result, profit). [summary] adds them up and splits them the ways that explain a fill: how fast, what kind, what price, with a sharp book or not, whether it
 * led, how early. Diagnostics prints [summary] and the newest fills ([fillLines]); the scan study carries [summary] and every filled bid's [Row] as a JSON line.
 * RESEARCH.md §88.3.
 */
object BidReport {

    @Serializable
    data class Row(
        val id: String,
        val auto: Boolean,
        val league: String,
        val kind: String,
        val market: String,
        val selection: String,
        val event: String,
        val startsTs: Long,
        // ---- as posted
        val postedAtMs: Long,
        val minToStartAtPost: Long,
        val price: Double,
        val american: Int,
        val contracts: Long,
        /** The fair its margin was taken from (the lower of the blend and the sharp book's), the blend, and the sharpest book's own fair. */
        val fair: Double,
        val blend: Double? = null,
        val sharpAtPost: Double? = null,
        /** The EV claimed at [fair] when posted, the margin asked for, and the books behind the fair. */
        val evAtPost: Double,
        val margin: Double,
        val books: Int,
        /** Novig's book when posted: no bid as high as ours (led), the best bid, the price to take it, and how old that book was. */
        val led: Boolean? = null,
        val bestBid: Double? = null,
        val offer: Double? = null,
        val bookAgeSec: Long? = null,
        /** How long it was set to rest at most (expiry minus posting), minutes. */
        val lifeMin: Long? = null,
        val basis: String? = null,
        /** Which "Which bids go up" choice posted it, the books its fair was made from, and how old the oldest and newest of their prices were then (seconds). */
        val focus: String? = null,
        val fairBooks: List<String> = emptyList(),
        val fairAgeSec: Int? = null,
        val fairNewestAgeSec: Int? = null,
        /** A small-market bid (Quick & likely's fill of the money popular bids leave idle). */
        val obscure: Boolean = false,
        // ---- what became of it
        val status: String,
        val why: String? = null,
        val endedAtMs: Long? = null,
        val restedMin: Double? = null,
        // ---- the fill
        val filled: Long = 0,
        val paid: Double? = null,
        val firstFillAtMs: Long? = null,
        val fillDelaySec: Long? = null,
        val minToStartAtFill: Long? = null,
        /** The fair on the first scan after the fill (blend, sharp), the EV the fill had against it, and whether that was under zero (picked off). */
        val fairAtFill: Double? = null,
        val sharpAtFill: Double? = null,
        val evAtFill: Double? = null,
        val pickedOff: Boolean? = null,
        // ---- the bet it became
        val betStatus: String? = null,
        val profit: Double? = null,
        val stake: Double? = null,
        val closeFair: Double? = null,
        val clv: Double? = null,
        val closeVia: String? = null,
    )

    /** [bids] that were posted (they have Novig's order id), as rows joined with the Tracker bets their fills became. */
    fun rows(bids: List<MakerBid>, tracked: List<TrackedBet>, now: Long, anchorSharp: Boolean = true): List<Row> {
        val byId = tracked.associateBy { it.id }
        return bids.filter { it.orderId != null }.sortedBy { it.postedAtMs }.map { b ->
            val bet = b.betId?.let { byId[it] }
            val ev = b.fillEv(anchorSharp)
            val fillAt = b.firstFillAtMs
            Row(
                id = b.clientId, auto = b.auto, league = b.league, kind = b.kind.name, market = b.marketLabel, selection = b.selection, event = b.eventName, startsTs = b.startsTs,
                postedAtMs = b.postedAtMs, minToStartAtPost = (b.startsTs - b.postedAtMs) / 60_000L, price = b.price,
                american = Odds.probabilityToAmerican(b.price.coerceIn(0.001, 0.999)), contracts = b.contracts,
                fair = b.fair, blend = b.blendFair, sharpAtPost = b.sharpFairAtPost, evAtPost = b.evAtFair, margin = b.margin, books = b.books,
                led = b.bestBidAtPost?.let { it < b.price - 1e-9 } ?: if (b.offerAtPost != null || b.bookAtMs != null) true else null,
                bestBid = b.bestBidAtPost, offer = b.offerAtPost, bookAgeSec = b.bookAtMs?.let { ((b.postedAtMs - it) / 1000L).coerceAtLeast(0L) },
                lifeMin = b.expiresAtMs?.let { ((it - b.postedAtMs) / 60_000L).coerceAtLeast(0L) },
                basis = b.fairBasis?.group,
                focus = b.focus, fairBooks = b.fairBooks, fairAgeSec = b.fairAgeSec, fairNewestAgeSec = b.fairNewestAgeSec, obscure = b.obscure,
                status = b.status.name, why = b.why.takeIf { b.status.ended }, endedAtMs = b.endedAtMs,
                restedMin = ((b.endedAtMs ?: now) - b.postedAtMs).coerceAtLeast(0L) / 60_000.0,
                filled = b.filled, paid = b.paid.takeIf { b.filled > 0 }, firstFillAtMs = fillAt, fillDelaySec = b.fillDelayMs?.let { it / 1000L },
                minToStartAtFill = fillAt?.let { (b.startsTs - it) / 60_000L },
                fairAtFill = b.fairAtFill, sharpAtFill = b.sharpFairAtFill, evAtFill = ev, pickedOff = ev?.let { it < 0.0 },
                betStatus = bet?.status?.name, profit = bet?.profit, stake = bet?.stake,
                closeFair = bet?.let { ClosingLine.closeFair(it, now) }, clv = bet?.let { ClosingLine.clv(it, now) }, closeVia = bet?.let { ClosingLine.closeOf(it, now)?.second },
            )
        }
    }

    /** One group's fills added up. */
    class Agg {
        var fills = 0
        var judged = 0
        var picked = 0
        private var evPost = 0.0
        private var evFill = 0.0
        private var clvN = 0
        private var clvSum = 0.0
        private var beat = 0
        private var settled = 0
        private var profit = 0.0
        private var staked = 0.0
        private var delaySum = 0L
        private var delayN = 0

        fun add(r: Row) {
            fills++
            evPost += r.evAtPost
            r.evAtFill?.let { judged++; evFill += it; if (it < 0.0) picked++ }
            r.clv?.let { clvN++; clvSum += it; if (it > 0.0) beat++ }
            if (r.profit != null && r.stake != null && r.betStatus != null && r.betStatus != BetStatus.PENDING.name && r.betStatus != BetStatus.VOID.name) { settled++; profit += r.profit; staked += r.stake }
            r.fillDelaySec?.let { delaySum += it; delayN++ }
        }

        fun line(label: String): String {
            val parts = ArrayList<String>()
            parts += "$fills fill${if (fills == 1) "" else "s"}"
            parts += "EV at post ${pct(evPost / fills)}"
            parts += if (judged > 0) "EV at fill ${pct(evFill / judged)} ($judged judged, ${Math.round(100.0 * picked / judged)}% picked off)" else "EV at fill: none judged yet"
            parts += if (clvN > 0) "CLV ${pct(clvSum / clvN)} ($clvN close${if (clvN == 1) "" else "s"}, ${Math.round(100.0 * beat / clvN)}% beat)" else "CLV: no close yet"
            if (settled > 0) parts += "results ${money(profit)} on ${String.format(Locale.US, "%.2f", staked)} staked ($settled settled)"
            if (delayN > 0) parts += "mean wait ${secs(delaySum / delayN)}"
            return "$label: " + parts.joinToString(" · ")
        }
    }

    /** The bids added up and split the ways that explain a fill. Empty when none was posted. */
    fun summary(rows: List<Row>, now: Long): List<String> {
        if (rows.isEmpty()) return emptyList()
        val filled = rows.filter { it.filled > 0 }
        val out = ArrayList<String>()
        val unfilled = rows.filter { it.filled == 0L && it.status != MakerStatus.RESTING.name && it.status != MakerStatus.SENT.name && it.status != MakerStatus.CANCELING.name }
        out += "bids: ${rows.size} posted · ${filled.size} filled (${pctOf(filled.size, rows.size)}) · ${rows.count { it.status == MakerStatus.RESTING.name || it.status == MakerStatus.SENT.name }} resting now · " +
            "${unfilled.size} ended without a fill · auto-make ${rows.count { it.auto }}, by hand ${rows.count { !it.auto }}"
        // Small-market bids are counted from the first one posted, before any fill ([rows] split by fill only once there are fills).
        if (rows.any { it.obscure }) out += "small-market bids (Quick & likely's fill of idle money): ${rows.count { it.obscure }} posted, ${rows.count { it.obscure && it.filled > 0 }} filled"
        if (filled.isEmpty()) return out
        val all = Agg().also { a -> filled.forEach(a::add) }
        out += all.line("ALL FILLS")
        val delays = filled.mapNotNull { it.fillDelaySec }.sorted()
        if (delays.isNotEmpty()) {
            out += "  how fast they were taken: median ${secs(delays[delays.size / 2])}, 25% within ${secs(delays[(delays.size - 1) / 4])}, 75% within ${secs(delays[(delays.size - 1) * 3 / 4])}; " +
                "${pctOf(delays.count { it < 120 }, delays.size)} within 2 minutes of posting (a bid taken that fast is usually one the market was already walking away from: compare EV at post with EV at fill)"
        }
        // Do the bids that fill differ from the ones that don't?
        val ledKnown = rows.filter { it.led != null }
        if (ledKnown.isNotEmpty()) {
            val f = ledKnown.filter { it.filled > 0 }
            val u = ledKnown.filter { it.filled == 0L }
            out += "  led their side when posted (no bid as high): filled ${pctOf(f.count { it.led == true }, f.size)} of ${f.size}, unfilled ${pctOf(u.count { it.led == true }, u.size)} of ${u.size}"
        }
        fun split(title: String, order: List<String>?, key: (Row) -> String?) {
            val groups = LinkedHashMap<String, Agg>()
            for (r in filled) key(r)?.let { groups.getOrPut(it) { Agg() }.add(r) }
            if (groups.isEmpty()) return
            out += "-- fills by $title --"
            val sorted = if (order != null) groups.entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } } else groups.entries.sortedByDescending { it.value.fills }
            sorted.forEach { out += "   " + it.value.line(it.key) }
        }
        split("how fast they were taken", DELAY_ORDER, ::delayBand)
        split("kind of market", null) { it.kind }
        split("bid price (about the chance the side wins)", PRICE_ORDER, ::priceBand)
        split("a sharp book behind the price", null) { if (it.sharpAtPost != null) "sharp book in the fair" else "no sharp book in the fair" }
        split("whether it led its side", null) { it.led?.let { l -> if (l) "led (no bid as high)" else "behind another bid" } }
        split("time to the start when posted", null) { BetLedger.leadBand(it.minToStartAtPost) }
        split("picked off or not (the fair on the next scan against the price filled at)", null) { it.pickedOff?.let { p -> if (p) "picked off (fair under the price)" else "still above the price" } }
        split("who posted it", null) { if (it.auto) "auto-make" else "by hand" }
        // Low API usage bids (RESEARCH.md §92): shown once any bid was posted by that mode (or by another one, so the two can be set side by side).
        if (rows.any { it.focus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE.name }) {
            split("which bids go up (Settings › Bids)", null) { focusLabel(it.focus) }
            split("the books behind the fair, low API usage bids", null) { r -> r.takeIf { it.focus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE.name && it.fairBooks.isNotEmpty() }?.fairBooks?.sorted()?.joinToString(" + ") }
            split("age of the oldest sharp price when posted, low API usage bids", AGE_ORDER) { r -> r.takeIf { it.focus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE.name }?.fairAgeSec?.let(::ageBand) }
        }
        // Small-market bids (Quick & likely fills the money popular bids leave idle with them, under strict safeguards): shown once any was posted, so Tj sees whether they pay.
        if (rows.any { it.obscure }) {
            out += "-- popular and small-market bids --"
            out += "   posted: ${rows.count { !it.obscure }} popular, ${rows.count { it.obscure }} small-market"
            split("popular or small market", null) { if (it.obscure) "small market (strict safeguards)" else "popular market" }
        }
        split("league", null) { it.league }
        return out
    }

    /** The newest [limit] fills as one line each, for Diagnostics: when, what, how fast, the price and fairs, the EV then and at the fill, the close and the result. */
    fun fillLines(rows: List<Row>, now: Long, limit: Int = 40, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<String> {
        val clock = java.text.SimpleDateFormat("MMM d h:mm a", Locale.US).apply { timeZone = zone }
        return rows.filter { it.filled > 0 }.sortedByDescending { it.firstFillAtMs ?: it.postedAtMs }.take(limit).map { r ->
            val parts = ArrayList<String>()
            parts += "${clock.format(java.util.Date(r.firstFillAtMs ?: r.postedAtMs))}"
            parts += "${r.selection} (${r.league} ${r.market})"
            parts += "${Odds.formatAmerican(r.american)} @ ${String.format(Locale.US, "%.3f", r.price)} x${r.filled}/${r.contracts}"
            parts += "taken ${r.fillDelaySec?.let(::secs) ?: "?"} after posting, ${r.minToStartAtFill?.let { "${it} min" } ?: "?"} before the start"
            parts += "fair ${prob(r.fair)}" + (r.blend?.takeIf { it != r.fair }?.let { " (blend ${prob(it)})" } ?: "") + (r.sharpAtPost?.let { " sharp ${prob(it)}" } ?: " no sharp") + " → EV ${pct(r.evAtPost)}"
            parts += if (r.fairAtFill != null) "next scan fair ${prob(r.fairAtFill)}" + (r.sharpAtFill?.let { " sharp ${prob(it)}" } ?: "") + " → EV ${pct(r.evAtFill ?: 0.0)}" + if (r.pickedOff == true) " PICKED OFF" else "" else "fair at fill: not judged"
            parts += when (r.led) { true -> "led"; false -> "behind"; null -> "book not recorded" } + (r.bookAgeSec?.let { ", book ${secs(it)} old" } ?: "")
            parts += if (r.clv != null) "CLV ${pct(r.clv)}${r.closeVia?.let { " ($it)" } ?: ""}" else "no close yet"
            r.betStatus?.takeIf { it != BetStatus.PENDING.name }?.let { parts += "$it ${money(r.profit ?: 0.0)}" }
            "  " + parts.joinToString(" · ")
        }
    }

    /** "Low API usage", "Quick & likely to win", "All bids"; a bid posted before the tag existed has none. */
    fun focusLabel(focus: String?): String? = focus?.let { f -> com.tjshea.vigilant.data.scanner.BidFocus.entries.firstOrNull { it.name == f }?.displayName ?: f }

    private val AGE_ORDER = listOf("under 1 min", "1 to 3 min", "3 to 5 min", "5 min or more")

    fun ageBand(sec: Int): String = when {
        sec < 60 -> "under 1 min"
        sec < 180 -> "1 to 3 min"
        sec < 300 -> "3 to 5 min"
        else -> "5 min or more"
    }

    private val DELAY_ORDER = listOf("under 30 s", "30 s to 2 min", "2 to 10 min", "10 to 60 min", "an hour or more")
    private val PRICE_ORDER = listOf("under 0.20", "0.20-0.30", "0.30-0.40", "0.40-0.50", "0.50-0.60", "0.60 and up")

    fun delayBand(r: Row): String? = r.fillDelaySec?.let {
        when {
            it < 30 -> "under 30 s"
            it < 120 -> "30 s to 2 min"
            it < 600 -> "2 to 10 min"
            it < 3600 -> "10 to 60 min"
            else -> "an hour or more"
        }
    }

    fun priceBand(r: Row): String = when {
        r.price < 0.20 -> "under 0.20"
        r.price < 0.30 -> "0.20-0.30"
        r.price < 0.40 -> "0.30-0.40"
        r.price < 0.50 -> "0.40-0.50"
        r.price < 0.60 -> "0.50-0.60"
        else -> "0.60 and up"
    }

    private fun pct(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun prob(v: Double) = String.format(Locale.US, "%.3f", v)
    private fun pctOf(n: Int, of: Int) = if (of == 0) "–" else "${Math.round(100.0 * n / of)}%"
    private fun money(v: Double) = String.format(Locale.US, "%+.2f", v)
    private fun secs(s: Long): String = when {
        s < 90 -> "${s}s"
        s < 5_400 -> "${Math.round(s / 60.0)} min"
        else -> String.format(Locale.US, "%.1f h", s / 3600.0)
    }
}
