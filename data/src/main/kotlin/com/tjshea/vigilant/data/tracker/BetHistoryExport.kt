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
 *  - `…-bets-p1.jsonl`, `-p2` …: one [BetHistory.Row] per line, oldest first, a new part every [partBytes] (about 4 MB, 4,000 bets), so no file is too big for a chat or for the phone.
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
            Readme.write(w, inRange, bidFile, bidCount, now, rangeLabel, parts.files.map { it.name }, bidFile?.name)
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
            val ind = group.filter { !it.isOutlier && !it.isLock && it.status != BetStatus.VOID && ClosingLine.closeOf(it, now)?.second?.let(ClosingLine::independent) == true }.mapNotNull { ClosingLine.clv(it, now) }
            return "- $label · ${st.bets} bets (${st.pending} open)" +
                (if (st.settled > 0) " · ${st.won}-${st.lost}${if (st.pushed > 0) "-${st.pushed}" else ""} (win ${pct0(st.winRate)})" else "") +
                " · ROI ${pct(st.roi)} · profit ${money(st.profit)} · EV when placed ${pct(st.averageEv)}" +
                (if (clvs.isNotEmpty()) " · CLV ${pct(st.averageClv)} on ${clvs.size} closed (beat close ${pct0(st.beatClosePercent)}${t?.let { String.format(Locale.US, ", t=%.1f", it) } ?: ""})" else " · CLV n/a (no true close yet)") +
                (if (ind.isNotEmpty()) " · INDEPENDENT-close CLV ${pct(ind.average())} on ${ind.size} (beat ${pct0(ind.count { it > 0 }.toDouble() / ind.size)})" else if (clvs.isNotEmpty()) " · no independent close" else "") +
                (if (st.settledWithEv > 0) " · expected ${money(st.expectedProfit)} vs actual ${money(st.profitWithEv)}" else "")
        }

        fun tStat(xs: List<Double>): Double? {
            if (xs.size < 3) return null
            val m = xs.average()
            val sd = sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
            return if (sd > 1e-12) m / (sd / sqrt(xs.size.toDouble())) else null
        }

        fun write(w: BufferedWriter, bets: List<TrackedBet>, bidFileOnDisk: File?, bidCount: Int, now: Long, range: String, betFiles: List<String>, bidFile: String?) {
            fun ln(s: String = "") { w.write(s); w.write("\n") }
            ln("# Vigilant bet history ($range)")
            ln("")
            ln("Generated ${BetHistory.iso(now)} by Vigilant (format ${BetHistory.FORMAT}). **${bets.size} bets and bid fills** (${bets.count { it.isBid }} are bid fills), **$bidCount bids posted**" +
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
            headline(::ln, bets, now)
            splits(::ln, bets, now)
            findings(::ln, bets, now)
            bidSection(::ln, bidFileOnDisk)
            quality(::ln, bets, now)
        }

        private fun dictionary(ln: (String) -> Unit) {
            ln("## Definitions")
            ln("- **Odds** are American. `cost` is the price paid per $1 of payout including Novig's taker fee (a pregame taker fee is 0; a bid pays no fee). EV, CLV, ROI, fair and win % are fractions (0.03 = +3%).")
            ln("- **EV** (`ev`): `fair / cost - 1` at the moment of placing, against the fair chance the app had then (devigged books). For a bid it is the edge at its fair when it was POSTED.")
            ln("- **Fair** (`fair`, `fairOdds`): the no-vig chance of the bet's side from the books (worst-case devig, lower of mean and median; sister sites count once; books with wrong-looking odds are left out).")
            ln("- **Close and CLV**: `closeFair` is the same side's no-vig chance at the START of the game; `closeOdds` is that as American odds (a fair price, not a book price: do not compare it with `odds` without the vig in mind - `clv` already does). `clv = closeFair / cost - 1`:")
            ln("  positive = the bet was cheaper than the market's final opinion. It exists ONLY for a TRUE close: a read of the market in the last 15 minutes before the start made at least 60 s after the bet (a read seconds after the bet is its own line, not a close), or a history source's price at the start. `closeSource` = captured or the history source.")
            ln("  `closeBasis` says what the close is made of: `read:cno` (CrazyNinjaOdds' consensus, the SAME kind of consensus that found most CNO bets), `read:vigilant` (Vigilant's own fair line), `read:both`, `history:pinnacle` (Pinnacle's price at the start, via ParlayAPI/SGO/OddsPapi), `history:espn`, `history:novig-trades` (the last 3+ trades on Novig in the last 15 minutes, the bettor's own fills included).")
            ln("  `closeIndependent` = true only for history from another source than the one that found the bet (a sharp book's price at the start, or ESPN's): CLV against `read:cno` is partly circular (the bet and its close come from one consensus), so **test every CLV claim on `closeIndependent: true` bets too**. When both exist the sharp book's price at the start is the close (the app asks for it even when a read before the start exists); `startsLoggedMs`/`startsNote` appear when a game started early or late and the start was corrected. For a read, `closeReadMs`/`closeLeadSec` say when it was taken and how many seconds before the start, `closeReadAfterBetSec` how long after the bet; a history close has no read time (it is the price at the start).")
            ln("  Closes found after the game are devigged by the same conservative WORST-CASE method as the app's own fair (the lowest fair chance of four methods; `AtBet`'s `fairMethod` says which method priced each bet: bets before v0.89.19 used POWER); before v0.89.18 history closes were multiplicative, which flatters longshots by 5-13% (so very old history closes on dogs are biased high). `novigCloseOdds`/`novigCloseAgeSec` = Novig's own last price when it was read in the last 15 minutes before the start.")
            ln("- **Stars** (`stars`, 1-5 in halves): the app's rating of how much to TRUST the edge: companies pricing both sides (20 points), share agreeing +EV (25), size of the edge (30, falling above 12%), a sharp book agreeing (15), tightness of the books' fair chances (10), plus half the history's type-of-bet and lead points (weights SR, v1.0.67; before it 20/20/25/25/10 and full type points); ceilings by company count and a sharp book against. `starsBasis` = recorded (rated live) or estimated (worked out afterwards from the record, notably every bid and every bet before v0.89.17).")
            ln("- **books / booksAgree**: companies (sister sites once) pricing BOTH sides behind the fair, and how many of those said +EV on their own. `sharp`: agree / against / none (no sharp book priced it); absent = not recorded.")
            ln("- **how**: auto (auto-bet or auto-bid) / manual / lock. **made**: bet (taker) / bid (maker fill). **leadMin**: minutes before the start when placed. **liquidity**: dollars Novig had at the price. **result**: won / lost / push / void / fmv / open; **profit** in dollars, **roi** = profit/stake.")
            ln("- `outlier: true` = an EV beyond +-" + String.format(Locale.US, "%.0f%%", BetTracker.OUTLIER_EV * 100) + " that the app leaves out of its averages (so do the tables here); `lockFor` = a bet that locks in another's profit (not a pick).")
            ln("- A field absent from a line is unknown for that bet (never 0 or empty). Times are America/New_York.")
            ln("")
        }

        private fun headline(ln: (String) -> Unit, bets: List<TrackedBet>, now: Long) {
            ln("## Headline")
            ln("**Read the INDEPENDENT-close CLV first**: a close read from the market before the start is, for a CrazyNinjaOdds bet, the same consensus that found it (partly circular); the independent figure (a sharp book's or ESPN's price at the start) cannot be.")
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

        /** Counts, sum and sum of squares of CLV for one cell: enough for its mean and t-statistic without keeping its bets. */
        private class Acc { var n = 0; var sum = 0.0; var sq = 0.0 }

        /**
         * Single groups and pairs of groups with enough closed bets, ranked by how far from zero their CLV is in standard errors. One pass over the closed bets with a small accumulator per cell
         * (never a list of bets per cell: a year of hundreds of bets a day would be tens of millions of boxed numbers); only the few cells that are shown are looked at again for their ROI.
         */
        private fun findings(ln: (String) -> Unit, bets: List<TrackedBet>, now: Long) {
            ln("## Automatic findings (closing-line value, strongest evidence first)")
            val usable = bets.filter { !it.isOutlier && !it.isLock && it.status != BetStatus.VOID && ClosingLine.clv(it, now) != null }
            if (usable.size < 30) {
                ln("Only ${usable.size} bets have a true close, too few for findings. Read the splits above with that in mind.")
                ln("")
                return
            }
            val dims = listOf(BetDim.MADE, BetDim.HOW, BetDim.KIND, BetDim.SPORT, BetDim.STARS, BetDim.SHARP, BetDim.BOOKS, BetDim.EV, BetDim.LEAD, BetDim.LIQUIDITY, BetDim.ODDS, BetDim.SCANNER)
            // Each dimension's group names as small integers, so a cell is a pair of ints.
            val names = dims.map { ArrayList<String>() }
            val codeOf = dims.map { HashMap<String, Int>() }
            fun code(d: Int, v: String): Int = codeOf[d].getOrPut(v) { names[d].add(v); names[d].size - 1 }
            val attr = Array(dims.size) { IntArray(usable.size) }
            val clv = DoubleArray(usable.size)
            usable.forEachIndexed { i, b ->
                clv[i] = ClosingLine.clv(b, now)!!
                for (d in dims.indices) attr[d][i] = code(d, BetDims.valueOf(b, dims[d], now))
            }
            // key: dimension i, group a, and (for a pair) dimension j, group c; -1 = none.
            data class Key(val i: Int, val a: Int, val j: Int, val c: Int)
            val cells = HashMap<Key, Acc>()
            for (n in usable.indices) {
                for (i in dims.indices) {
                    cells.getOrPut(Key(i, attr[i][n], -1, -1)) { Acc() }.also { it.n++; it.sum += clv[n]; it.sq += clv[n] * clv[n] }
                    for (j in i + 1 until dims.size) cells.getOrPut(Key(i, attr[i][n], j, attr[j][n])) { Acc() }.also { it.n++; it.sum += clv[n]; it.sq += clv[n] * clv[n] }
                }
            }
            fun label(k: Key): String = "${dims[k.i].label} = ${names[k.i][k.a]}" + (if (k.j >= 0) " AND ${dims[k.j].label} = ${names[k.j][k.c]}" else "")
            class Scored(val key: Key, val n: Int, val mean: Double, val t: Double)
            val scored = cells.entries.mapNotNull { (k, a) ->
                if (a.n < 25) return@mapNotNull null
                val lab = label(k)
                if (lab.contains(BetDims.UNKNOWN) || lab.contains(BetDims.UNRATED)) return@mapNotNull null
                val mean = a.sum / a.n
                val sd = sqrt(((a.sq - a.n * mean * mean) / (a.n - 1)).coerceAtLeast(0.0))
                if (sd < 1e-12) null else Scored(k, a.n, mean, mean / (sd / sqrt(a.n.toDouble())))
            }
            fun show(title: String, list: List<Scored>) {
                ln("### $title")
                if (list.isEmpty()) ln("(none with 25+ closed bets)")
                list.forEach { c ->
                    // Only the shown cells are looked at again, for their ROI and profit.
                    val group = usable.filterIndexed { n, _ -> attr[c.key.i][n] == c.key.a && (c.key.j < 0 || attr[c.key.j][n] == c.key.c) }
                    val st = BetTracker.stats(group, now)
                    ln("- ${label(c.key)}: CLV ${pct(c.mean)} on ${c.n} closed (t=${String.format(Locale.US, "%.1f", c.t)}, beat close ${pct0(st.beatClosePercent)}) · ROI ${pct(st.roi)} · profit ${money(st.profit)}")
                }
            }
            show("Beating the close", scored.filter { it.mean > 0 }.sortedByDescending { it.t }.take(15))
            show("Giving the close away", scored.filter { it.mean < 0 }.sortedBy { it.t }.take(15))
            ln("(t = mean CLV over its standard error: |t| of 2+ is hard to get by luck for one group, but this scans ${cells.size} groups, so expect some at |t| of 2-3 by chance alone: trust the ones that are large AND make sense, and that hold on `closeIndependent: true` bets.)")
            ln("")
        }

        /** Totals for a group of bids; the delays kept are capped so a year of bids can't fill memory. */
        private class BidAcc {
            var n = 0; var filled = 0; var full = 0; var paid = 0.0; var evSum = 0.0; var judged = 0; var picked = 0; var fillEvSum = 0.0
            val delays = ArrayList<Long>()
            fun add(r: BetHistory.BidRow) {
                n++
                if (r.filled > 0) filled++
                if (r.fillPct >= 0.999) full++
                paid += r.paid
                evSum += r.ev
                r.fillDelayMs?.let { if (delays.size < 20_000) delays.add(it) }
                r.fillEv?.let { judged++; fillEvSum += it; if (it < 0) picked++ }
            }

            fun line(label: String): String {
                val d = delays.sorted()
                return "- $label · $n posted · $filled filled (${pct0(filled.toDouble() / n)}), $full fully · \$${String.format(Locale.US, "%.2f", paid)} paid" +
                    (d.takeIf { it.isNotEmpty() }?.let { " · median time to first fill ${it[it.size / 2] / 1000}s" } ?: "") +
                    (if (judged > 0) " · picked off (fair under price at the fill) ${pct0(picked.toDouble() / judged)} of $judged judged, mean fill EV ${pct(fillEvSum / judged)}" else "") +
                    " · mean EV at posting ${pct(evSum / n)}"
            }
        }

        /** Reads the bids file one line at a time into small accumulators: the whole history is never in memory. */
        private fun bidSection(ln: (String) -> Unit, file: File?) {
            ln("## Bids posted (all, filled or not)")
            val all = BidAcc()
            val status = HashMap<String, Int>()
            val kind = HashMap<String, BidAcc>()
            val lead = HashMap<String, BidAcc>()
            val who = HashMap<String, BidAcc>()
            file?.useLines { lines ->
                for (l in lines) {
                    val r = runCatching { BetHistory.json.decodeFromString<BetHistory.BidRow>(l) }.getOrNull() ?: continue
                    all.add(r)
                    status.merge(r.status, 1, Int::plus)
                    kind.getOrPut(r.kind) { BidAcc() }.add(r)
                    lead.getOrPut(BetLedger.leadBand(r.postedLeadMin ?: 0)) { BidAcc() }.add(r)
                    who.getOrPut("${r.scanner}${if (r.auto) " (auto)" else " (by hand)"}") { BidAcc() }.add(r)
                }
            }
            if (all.n == 0) { ln("No bid history in this export (the app keeps each day's bids once they end; a bid posted before v0.89.18 is only here if it was still on the desk)."); ln(""); return }
            ln(all.line("All bids"))
            status.entries.sortedByDescending { it.value }.forEach { (s, n) -> ln("- status $s: $n") }
            ln("### By kind")
            kind.entries.sortedByDescending { it.value.n }.forEach { (k, g) -> ln(g.line(k)) }
            ln("### By time before the start when posted")
            lead.entries.sortedBy { BetLedger.LEAD_ORDER.indexOf(it.key) }.forEach { (k, g) -> ln(g.line(k)) }
            ln("### By scanner / auto")
            who.entries.sortedByDescending { it.value.n }.forEach { (k, g) -> ln(g.line(k)) }
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
            val byBasis = bets.filter { it.status != BetStatus.VOID }.mapNotNull { b -> ClosingLine.closeOf(b, now)?.second?.let { BetHistory.closeBasis(b, it) } }.groupingBy { it }.eachCount()
            ln("- true closes by basis: " + byBasis.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" })
            ln("")
        }
    }

    /** Estimated size of an export of [bets] (about [BYTES_PER_BET] a line), for the share dialog. */
    fun estimate(betCount: Int, partBytes: Long = PART_BYTES): Pair<Long, Int> {
        val bytes = betCount * BYTES_PER_BET
        return bytes to ((bytes + partBytes - 1) / partBytes).toInt().coerceAtLeast(1)
    }

    const val BYTES_PER_BET = 1_200L

    @Suppress("unused")
    private fun absForImport() = abs(0)
}
