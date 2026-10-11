package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import java.io.BufferedWriter
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The files Tj shares with Claude (Tj, 2026-10-11: "share every single bet and bid I ever made ... in a format that Claude can understand and analyze ... the goal is to analyze what bets and bids
 * beat the close, are profitable, and recommendations on app improvements or filters"; "careful about files that become too large and crash the app"). RESEARCH.md §132.
 *
 * What it writes, for the bets and bids since [fromMs] (all of them when null):
 *  - `…-README.md`: what the files are, a dictionary of every field, how CLV, EV and the star rating are defined, the headline numbers, the same table split every way ([BetDim]), the best and worst
 *    groups by closing-line value (single groups and pairs, with a t-statistic), the bids' fill story, data-quality counts and the questions to answer. Claude can answer most of the task from this alone.
 *  - `…-bets-p1.jsonl`, `-p2` …: one [BetHistory.Row] per line, oldest first, a new part every [partBytes] (about 4 MB, 6,000 bets), so no file is too big for a chat or for the phone.
 *  - `…-bids-posted.jsonl`: one [BetHistory.BidRow] per bid posted, filled or not.
 *
 * Memory: a bet is turned into its line, written and forgotten; nothing is built whole (the Tracker's own list is already in memory; the summary reads it with a handful of small maps). Pure
 * file work: no Android, so it is tested on the JVM.
 */
object BetHistoryExport {

    const val PART_BYTES = 4L * 1024 * 1024

    data class Result(val readme: File, val betParts: List<File>, val bidFile: File?, val bets: Int, val bids: Int, val bytes: Long) {
        val files: List<File> get() = listOf(readme) + betParts + listOfNotNull(bidFile)
    }

    /** A streaming writer that starts a new file when the current one passes [limit] bytes. */
    private class Parts(private val dir: File, private val stem: String, private val limit: Long) {
        val files = ArrayList<File>()
        private var out: BufferedWriter? = null
        private var bytes = 0L
        var total = 0L
            private set

        fun write(line: String) {
            val n = line.toByteArray(Charsets.UTF_8).size + 1L
            if (out == null || (bytes > 0 && bytes + n > limit)) next()
            out!!.write(line)
            out!!.write('\n'.code)
            bytes += n
            total += n
        }

        private fun next() {
            out?.close()
            val f = File(dir, "$stem-p${files.size + 1}.jsonl")
            files += f
            out = f.bufferedWriter(Charsets.UTF_8)
            bytes = 0
        }

        fun close() { out?.close(); out = null }
    }

    /**
     * Writes the files into [dir] (made if missing; the same names are replaced) under the name [stem] and returns them. [bidRows] are the bids posted, already as their JSON lines
     * ([BetHistory.line]), so the day files kept on the phone and the live ones are one stream.
     */
    fun write(
        dir: File,
        stem: String,
        bets: List<TrackedBet>,
        makerBids: List<MakerBid>,
        bidRows: Sequence<String>,
        now: Long,
        fromMs: Long? = null,
        rangeLabel: String = "all time",
        partBytes: Long = PART_BYTES,
    ): Result {
        dir.mkdirs()
        // Old parts of the same name must not linger (a shorter export has fewer parts).
        dir.listFiles { f -> f.name.startsWith("$stem-") }?.forEach { it.delete() }
        val inRange = bets.filter { fromMs == null || it.createdAtMs >= fromMs }.sortedBy { it.createdAtMs }
        val byBetId = HashMap<String, MakerBid>()
        makerBids.forEach { m -> m.betId?.let { byBetId[it] = m } }

        val parts = Parts(dir, "$stem-bets", partBytes)
        try {
            for (b in inRange) parts.write(BetHistory.line(BetHistory.row(b, byBetId[b.id], now)))
        } finally { parts.close() }

        var bidCount = 0
        var bidFile: File? = null
        val bidParts = Parts(dir, "$stem-bids-posted", partBytes * 4)
        try {
            for (line in bidRows) { bidParts.write(line); bidCount++ }
        } finally { bidParts.close() }
        bidFile = bidParts.files.firstOrNull()?.let { first ->
            if (bidParts.files.size == 1) File(dir, "$stem-bids-posted.jsonl").also { first.renameTo(it) } else first
        }

        val readme = File(dir, "$stem-README.md")
        readme.bufferedWriter(Charsets.UTF_8).use { w ->
            val bidList = bidFile?.let { f -> f.useLines { it.mapNotNull { l -> runCatching { BetHistory.json.decodeFromString<BetHistory.BidRow>(l) }.getOrNull() }.toList() } }.orEmpty()
            Readme.write(w, inRange, bidList, now, rangeLabel, parts.files.map { it.name }, bidFile?.name)
        }
        val files = parts.files + listOfNotNull(bidFile) + readme
        return Result(readme, parts.files.toList(), bidFile, inRange.size, bidCount, files.sumOf { it.length() })
    }

    /** The README and its summary tables. */
    internal object Readme {
        private fun pct(v: Double?) = v?.let { String.format(Locale.US, "%+.1f%%", it * 100) } ?: "n/a"
        private fun pct0(v: Double?) = v?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "n/a"
        private fun money(v: Double) = String.format(Locale.US, "%+.2f", v)

        /** One group's line: bets, record, ROI, EV when placed, CLV with its n and t-statistic, beat-the-close share, profit and expected profit. */
        fun groupLine(label: String, group: List<TrackedBet>, now: Long): String {
            val st = BetTracker.stats(group, now)
            val clvs = group.filter { !it.isOutlier && !it.isLock && it.status != BetStatus.VOID }.mapNotNull { ClosingLine.clv(it, now) }
            val t = tStat(clvs)
            return "- $label · ${st.bets} bets (${st.pending} open)" +
                (if (st.settled > 0) " · ${st.won}-${st.lost}${if (st.pushed > 0) "-${st.pushed}" else ""} (win ${pct0(st.winRate)})" else "") +
                " · ROI ${pct(st.roi)} · profit ${money(st.profit)} · EV when placed ${pct(st.averageEv)}" +
                (if (clvs.isNotEmpty()) " · CLV ${pct(st.averageClv)} on ${clvs.size} closed (beat close ${pct0(st.beatClosePercent)}${t?.let { String.format(Locale.US, ", t=%.1f", it) } ?: ""})" else " · CLV n/a (no true close yet)") +
                (if (st.settledWithEv > 0) " · expected ${money(st.expectedProfit)} vs actual ${money(st.profitWithEv)}" else "")
        }

        fun tStat(xs: List<Double>): Double? {
            if (xs.size < 3) return null
            val m = xs.average()
            val sd = sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
            return if (sd > 1e-12) m / (sd / sqrt(xs.size.toDouble())) else null
        }

        fun write(w: BufferedWriter, bets: List<TrackedBet>, bids: List<BetHistory.BidRow>, now: Long, range: String, betFiles: List<String>, bidFile: String?) {
            fun ln(s: String = "") { w.write(s); w.write("\n") }
            ln("# Vigilant bet history ($range)")
            ln("")
            ln("Generated ${BetHistory.iso(now)} by Vigilant (format ${BetHistory.FORMAT}). **${bets.size} bets and bid fills** (${bets.count { it.isBid }} are bid fills), **${bids.size} bids posted**" +
                (bets.minByOrNull { it.createdAtMs }?.let { ", from ${BetHistory.day(it.createdAtMs)} to ${BetHistory.day(bets.maxOf { b -> b.createdAtMs })}" } ?: "") + ".")
            ln("")
            ln("## Your task")
            ln("Tj bets on Novig (a US exchange) with a +EV scanner, **Vigilant**. The goal is PROFIT through positive EV that the CLOSING LINE confirms. From the files below find (1) which kinds of bets and bids beat the close and are")
            ln("profitable and which do not, (2) whether the app's own star rating, sharp-book check and agreement counts predict beating the close, (3) specific filters, thresholds and rule changes for the auto-bet")
            ln("and the auto-bid desk (name the setting or the code: Presets.kt, SharpVeto.kt, AutoBet.kt, MakerRules.kt, BetRating.kt), (4) data-quality problems in the logging itself. Judge groups by CLV first (it needs far fewer")
            ln("bets than profit to say anything): a group wants roughly 100+ bets with a true close before CLV means much and 1,000+ before ROI does. State the sample size and t-statistic behind every claim, say when the sample is too small, and")
            ln("do not recommend a change on a handful of bets. Bets and bids are different instruments (a bid can be picked off; a bet cannot): read them apart.")
            ln("")
            ln("## Files")
            betFiles.forEach { ln("- `$it`: bets and bid fills, one JSON object per line, oldest first") }
            bidFile?.let { ln("- `$it`: every bid posted (filled or not), one JSON object per line") }
            ln("- this README: dictionary, definitions, summary tables and automatic findings")
            ln("")
            dictionary(::ln)
            headline(::ln, bets, bids, now)
            splits(::ln, bets, now)
            findings(::ln, bets, now)
            bidSection(::ln, bids)
            quality(::ln, bets, now)
        }

        private fun dictionary(ln: (String) -> Unit) {
            ln("## Definitions")
            ln("- **Odds** are American. `cost` is the price paid per $1 of payout including Novig's taker fee (a pregame taker fee is 0; a bid pays no fee). EV, CLV, ROI, fair and win % are fractions (0.03 = +3%).")
            ln("- **EV** (`ev`): `fair / cost - 1` at the moment of placing, against the fair chance the app had then (devigged books). For a bid it is the edge at its fair when it was POSTED.")
            ln("- **Fair** (`fair`, `fairOdds`): the no-vig chance of the bet's side from the books (worst-case devig, lower of mean and median; sister sites count once; books with wrong-looking odds are left out).")
            ln("- **Close and CLV**: `closeFair` is the same side's no-vig chance at the START of the game; `closeOdds` is that as American odds (a fair price, not a book price: do not compare it with `odds` without the vig in mind - `clv` already does). `clv = closeFair / cost - 1`:")
            ln("  positive = the bet was cheaper than the market's final opinion. It exists ONLY for a TRUE close: a read in the last 15 minutes before the start, or a history source's price at the start (`closeSource`). A bet's own line at the time it was placed is never a close. `closeLeadSec` = seconds before the start the close was read. `novigCloseOdds` = Novig's own last price.")
            ln("- **Stars** (`stars`, 1-5 in halves): the app's rating of how much to TRUST the edge: companies pricing both sides (20 points), share agreeing +EV (20), size of the edge (25, falling above 12%), a sharp book agreeing (25), tightness of the books' fair chances (10); ceilings by company count and a sharp book against. `starsBasis` = recorded (rated live) or estimated (worked out afterwards from the record, notably every bid and every bet before v0.89.17).")
            ln("- **books / booksAgree**: companies (sister sites once) pricing BOTH sides behind the fair, and how many of those said +EV on their own. `sharp`: agree / against / none (no sharp book priced it); absent = not recorded.")
            ln("- **how**: auto (auto-bet or auto-bid) / manual / lock. **made**: bet (taker) / bid (maker fill). **leadMin**: minutes before the start when placed. **liquidity**: dollars Novig had at the price. **result**: won / lost / push / void / fmv / open; **profit** in dollars, **roi** = profit/stake.")
            ln("- `outlier: true` = an EV beyond +-" + String.format(Locale.US, "%.0f%%", BetTracker.OUTLIER_EV * 100) + " that the app leaves out of its averages (so do the tables here); `lockFor` = a bet that locks in another's profit (not a pick).")
            ln("- A field absent from a line is unknown for that bet (never 0 or empty). Times are America/New_York.")
            ln("")
        }

        private fun headline(ln: (String) -> Unit, bets: List<TrackedBet>, bids: List<BetHistory.BidRow>, now: Long) {
            ln("## Headline")
            ln(groupLine("All", bets, now))
            ln(groupLine("Bets (taker orders)", bets.filter { !it.isBid }, now))
            ln(groupLine("Bid fills", bets.filter { it.isBid }, now))
            ln("")
        }

        private val SPLIT_DIMS = listOf(
            BetDim.MADE, BetDim.HOW, BetDim.KIND, BetDim.SPORT, BetDim.STARS, BetDim.SHARP, BetDim.BOOKS, BetDim.AGREE, BetDim.EV, BetDim.LEAD,
            BetDim.LIQUIDITY, BetDim.ODDS, BetDim.SCANNER, BetDim.CLOSE_SOURCE,
        )

        private fun splits(ln: (String) -> Unit, bets: List<TrackedBet>, now: Long) {
            ln("## Splits (every dimension on its own)")
            for (dim in SPLIT_DIMS) {
                ln("### ${dim.label}")
                val groups = bets.groupBy { BetDims.valueOf(it, dim, now) }
                val ordered = groups.entries.sortedWith(compareBy({ BetDims.sortKey(dim, it.key) }, { -it.value.size }))
                ordered.forEach { (label, g) -> ln(groupLine(label, g, now)) }
                ln("")
            }
            // Bets and bids by week, to see a trend.
            ln("### By week placed")
            bets.groupBy { weekOf(it.createdAtMs) }.toSortedMap().forEach { (wk, g) -> ln(groupLine(wk, g, now)) }
            ln("")
        }

        private fun weekOf(ms: Long): String {
            val d = java.time.Instant.ofEpochMilli(ms).atZone(BetHistory.ET).toLocalDate()
            return "week of " + d.minusDays((d.dayOfWeek.value - 1).toLong())
        }

        /** Single groups and pairs of groups with enough closed bets, ranked by how far from zero their CLV is in standard errors. */
        private fun findings(ln: (String) -> Unit, bets: List<TrackedBet>, now: Long) {
            ln("## Automatic findings (closing-line value, strongest evidence first)")
            val usable = bets.filter { !it.isOutlier && !it.isLock && it.status != BetStatus.VOID && ClosingLine.clv(it, now) != null }
            if (usable.size < 30) {
                ln("Only ${usable.size} bets have a true close, too few for findings. Read the splits above with that in mind.")
                ln("")
                return
            }
            val dims = listOf(BetDim.MADE, BetDim.HOW, BetDim.KIND, BetDim.SPORT, BetDim.STARS, BetDim.SHARP, BetDim.BOOKS, BetDim.EV, BetDim.LEAD, BetDim.LIQUIDITY, BetDim.ODDS, BetDim.SCANNER)
            // Each closed bet's group on each dimension, once.
            val attr = usable.map { b -> dims.map { BetDims.valueOf(b, it, now) } }
            val clv = usable.map { ClosingLine.clv(it, now)!! }
            class Cell(val label: String, val ids: List<Int>)
            val cells = ArrayList<Cell>()
            for (i in dims.indices) attr.indices.groupBy { attr[it][i] }.forEach { (v, ids) -> cells += Cell("${dims[i].label} = $v", ids) }
            for (i in dims.indices) for (j in i + 1 until dims.size) {
                attr.indices.groupBy { attr[it][i] to attr[it][j] }.forEach { (v, ids) -> cells += Cell("${dims[i].label} = ${v.first} AND ${dims[j].label} = ${v.second}", ids) }
            }
            val scored = cells.filter { it.ids.size >= 25 && !it.label.contains(BetDims.UNKNOWN) && !it.label.contains(BetDims.UNRATED) }.mapNotNull { c ->
                val xs = c.ids.map { clv[it] }
                val t = tStat(xs) ?: return@mapNotNull null
                Triple(c, xs.average(), t)
            }
            fun show(title: String, list: List<Triple<Cell, Double, Double>>) {
                ln("### $title")
                if (list.isEmpty()) ln("(none with 25+ closed bets)")
                list.forEach { (c, m, t) ->
                    val group = c.ids.map { usable[it] }
                    val st = BetTracker.stats(group, now)
                    ln("- ${c.label}: CLV ${pct(m)} on ${c.ids.size} closed (t=${String.format(Locale.US, "%.1f", t)}, beat close ${pct0(st.beatClosePercent)}) · ROI ${pct(st.roi)} · profit ${money(st.profit)}")
                }
            }
            show("Beating the close", scored.filter { it.second > 0 }.sortedByDescending { it.third }.take(15))
            show("Giving the close away", scored.filter { it.second < 0 }.sortedBy { it.third }.take(15))
            ln("(t = mean CLV over its standard error: |t| of 2+ is hard to get by luck for one group, but this scans hundreds of groups, so expect a few at |t| of 2-3 by chance alone: trust the ones that are large AND make sense.)")
            ln("")
        }

        private fun bidSection(ln: (String) -> Unit, bids: List<BetHistory.BidRow>) {
            ln("## Bids posted (all, filled or not)")
            if (bids.isEmpty()) { ln("No bid history in this export (the file kept by the app holds the last days of bids; older days are in the daily copies in Downloads/Vigilant/bets)."); ln(""); return }
            fun line(label: String, g: List<BetHistory.BidRow>): String {
                val filled = g.count { it.filled > 0 }
                val full = g.count { it.fillPct >= 0.999 }
                val delays = g.mapNotNull { it.fillDelayMs }.sorted()
                val picked = g.mapNotNull { it.fillEv }
                return "- $label · ${g.size} posted · ${filled} filled (${pct0(filled.toDouble() / g.size)}), ${full} fully · \$${String.format(Locale.US, "%.2f", g.sumOf { it.paid })} paid" +
                    (delays.takeIf { it.isNotEmpty() }?.let { " · median time to first fill ${it[it.size / 2] / 1000}s" } ?: "") +
                    (picked.takeIf { it.isNotEmpty() }?.let { " · picked off (fair under price at the fill) ${pct0(it.count { v -> v < 0 }.toDouble() / it.size)} of ${it.size} judged, mean fill EV ${pct(it.average())}" } ?: "") +
                    " · mean EV at posting ${pct(g.map { it.ev }.average())}"
            }
            ln(line("All bids", bids))
            bids.groupBy { it.status }.entries.sortedByDescending { it.value.size }.forEach { (s, g) -> ln("- status $s: ${g.size}") }
            ln("### By kind")
            bids.groupBy { it.kind }.entries.sortedByDescending { it.value.size }.forEach { (k, g) -> ln(line(k, g)) }
            ln("### By time before the start when posted")
            bids.groupBy { BetLedger.leadBand(it.postedLeadMin ?: 0) }.entries.sortedBy { BetLedger.LEAD_ORDER.indexOf(it.key) }.forEach { (k, g) -> ln(line(k, g)) }
            ln("### By scanner / auto")
            bids.groupBy { "${it.scanner}${if (it.auto) " (auto)" else " (by hand)"}" }.entries.sortedByDescending { it.value.size }.forEach { (k, g) -> ln(line(k, g)) }
            ln("")
        }

        private fun quality(ln: (String) -> Unit, bets: List<TrackedBet>, now: Long) {
            ln("## Data quality")
            val n = bets.size.coerceAtLeast(1)
            fun row(label: String, c: Int) = ln("- $label: $c of ${bets.size} (${pct0(c.toDouble() / n)})")
            row("without the record as placed (older or imported bets)", bets.count { it.atBet == null })
            row("with a recorded star rating (rated live)", bets.count { BetDims.stars(it)?.second == false })
            row("with an estimated star rating", bets.count { BetDims.stars(it)?.second == true })
            row("with no star rating possible", bets.count { BetDims.stars(it) == null })
            row("started but with no true close", bets.count { now >= it.startsTs && !it.isLock && ClosingLine.clv(it, now) == null && it.status != BetStatus.VOID })
            row("left out of the averages as outliers", bets.count { it.isOutlier })
            row("open", bets.count { it.status == BetStatus.PENDING })
            val bySource = bets.mapNotNull { ClosingLine.closeOf(it, now)?.second }.groupingBy { ClosingLine.sourceLabel(it) }.eachCount()
            ln("- true closes by source: " + bySource.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" })
            ln("")
        }
    }

    /** Estimated size of an export of [bets] (about [BYTES_PER_BET] a line), for the share dialog. */
    fun estimate(betCount: Int, partBytes: Long = PART_BYTES): Pair<Long, Int> {
        val bytes = betCount * BYTES_PER_BET
        return bytes to ((bytes + partBytes - 1) / partBytes).toInt().coerceAtLeast(1)
    }

    const val BYTES_PER_BET = 800L

    @Suppress("unused")
    private fun absForImport() = abs(0)
}
