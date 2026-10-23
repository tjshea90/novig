package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BetRating
import com.tjshea.vigilant.data.scanner.ProfitProfile
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.sqrt

/**
 * Does the star rating sort bets by how good they are? (FX, Tj 2026-10-23: "Review the stars rating system for rating bets and make sure it is sound and based on good data.")
 *
 * Reads the engine's graded lines (`lines-<day>.csv.gz` from the `engine-dataset` Release, tools/lines_dataset.py's columns), re-rates every +EV line with TODAY's [BetRating] (the
 * shipped history points included) and prints, for the new stars, the stars each line was logged with, and every input of the rating, the closing-line value and the results with a
 * 95% interval that counts GAMES, not bets (lines of one game move together). Run by .github/workflows/review-digest.yml: `./gradlew :cloud:starcheck --args="<dir of lines csv> <out>"`.
 * Pure but for files; it orders nothing.
 */
object StarCheck {

    data class Line(
        val id: String, val day: String, val event: String, val league: String, val kind: String, val selection: String, val minToStart: Long?, val ev: Double,
        val books: Int, val agreeing: Int?, val sharp: Boolean?, val spread: Double?, val loggedStars: Double?, val status: String, val profit: Double?, val clv: Double?, val shown: Boolean,
    ) {
        val settled: Boolean get() = status == "WON" || status == "LOST" || status == "PUSH"
    }

    /** One RFC 4180 record per line (the dataset has no line breaks inside a field). */
    internal fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** The +EV lines of [text] (one CSV with its header), each once. */
    fun parse(text: Sequence<String>): List<Line> {
        val it = text.iterator()
        if (!it.hasNext()) return emptyList()
        val head = splitCsv(it.next()).withIndex().associate { (i, n) -> n.trim() to i }
        fun List<String>.f(name: String) = head[name]?.let { getOrNull(it) }?.trim().orEmpty()
        val out = ArrayList<Line>()
        while (it.hasNext()) {
            val raw = it.next()
            if (raw.isBlank()) continue
            val c = splitCsv(raw)
            val ev = c.f("ev").toDoubleOrNull() ?: continue
            if (ev <= 0.0) continue
            val two = c.f("books_two_sided").toIntOrNull()
            val sharp = when (c.f("sharp_agrees")) {
                "1" -> true
                "0" -> false
                else -> when (c.f("sharp_verdict")) { "PASSED" -> true; "VETOED" -> false; else -> null }
            }
            out += Line(
                id = c.f("id"), day = c.f("day"), event = c.f("event").ifEmpty { c.f("id") }, league = c.f("league"), kind = c.f("kind"), selection = c.f("selection"),
                minToStart = c.f("min_to_start").toDoubleOrNull()?.toLong(), ev = ev, books = two ?: (c.f("n_fair_books").toIntOrNull() ?: 0), agreeing = c.f("books_agreeing").toIntOrNull(),
                sharp = sharp, spread = c.f("fair_spread").toDoubleOrNull(), loggedStars = c.f("stars").toDoubleOrNull(), status = c.f("status"),
                profit = c.f("profit_units").toDoubleOrNull(), clv = c.f("clv").toDoubleOrNull(), shown = c.f("shown") == "1",
            )
        }
        return out
    }

    fun kindLabel(kind: String): String = BetKind.entries.firstOrNull { it.name == kind }?.label ?: BetKind.OTHER.label

    /** The history and lead points the scanners add today ([ProfitProfile.typePointsOf]), from the line's own lead time. */
    fun typePoints(l: Line, profile: ProfitProfile? = ProfitProfile.shipped): Double =
        (profile?.typePoints(l.league, kindLabel(l.kind), l.selection, l.minToStart) ?: 0.0) + BetRating.leadPoints(l.minToStart)

    fun rated(l: Line, profile: ProfitProfile? = ProfitProfile.shipped): BetRating.Rating =
        BetRating.rate(BetRating.Inputs(l.books, l.agreeing, l.ev, sharpAgrees = l.sharp == true, sharpAgainst = l.sharp == false, spread = l.spread, typePoints = typePoints(l, profile)))

    /** Mean and 95% half-width of [values], each game weighted once ([games]: the game of each value). */
    data class Stat(val n: Int, val games: Int, val mean: Double?, val half: Double?)

    internal fun stat(pairs: List<Pair<String, Double>>): Stat {
        if (pairs.isEmpty()) return Stat(0, 0, null, null)
        val byGame = pairs.groupBy({ it.first }, { it.second }).values.map { it.average() }
        val m = byGame.average()
        val half = if (byGame.size >= 5) 1.96 * sqrt(byGame.sumOf { (it - m) * (it - m) } / (byGame.size - 1) / byGame.size) else null
        return Stat(pairs.size, byGame.size, m, half)
    }

    private fun pct(v: Double?) = v?.let { String.format(Locale.US, "%+.2f%%", it * 100) } ?: "-"
    private fun pm(v: Double?) = v?.let { String.format(Locale.US, "±%.2f", it * 100) } ?: "±?"

    private fun row(label: String, ls: List<Line>): String {
        val clv = stat(ls.filter { it.clv != null }.map { it.event to it.clv!! })
        val roi = stat(ls.filter { it.settled && it.profit != null }.map { it.event to it.profit!! })
        val settled = ls.filter { it.settled && it.status != "PUSH" }
        val win = if (settled.isEmpty()) "-" else "${Math.round(100.0 * settled.count { it.status == "WON" } / settled.size)}%"
        return String.format(Locale.US, "  %-26s %6d · CLV %5d bets %4d games %8s %7s · results %5d bets %4d games ROI %8s %7s · win %s",
            label.take(26), ls.size, clv.n, clv.games, pct(clv.mean), pm(clv.half), roi.n, roi.games, pct(roi.mean), pm(roi.half), win)
    }

    private fun table(title: String, ls: List<Line>, key: (Line) -> String?, order: Comparator<String> = naturalOrder()): List<String> =
        listOf("-- $title --") + ls.mapNotNull { l -> key(l)?.let { it to l } }.groupBy({ it.first }, { it.second }).toSortedMap(order).map { (k, v) -> row(k, v) }

    /** Least-squares slope of y on x with its t (bets as the unit: an upper bound on the evidence). */
    internal fun slope(xy: List<Pair<Double, Double>>): Pair<Double, Double>? {
        if (xy.size < 10) return null
        val mx = xy.sumOf { it.first } / xy.size
        val my = xy.sumOf { it.second } / xy.size
        val sxx = xy.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx == 0.0) return null
        val b = xy.sumOf { (it.first - mx) * (it.second - my) } / sxx
        val a = my - b * mx
        val s2 = xy.sumOf { val e = it.second - a - b * it.first; e * e } / (xy.size - 2)
        return b to b / sqrt(s2 / sxx)
    }

    fun report(lines: List<Line>, profile: ProfitProfile? = ProfitProfile.shipped): String {
        val ls = lines.distinctBy { it.id }
        val stars = ls.associateWith { rated(it, profile).stars }
        val days = ls.map { it.day }.filter { it.isNotEmpty() }.distinct().sorted()
        val out = ArrayList<String>()
        out += "STAR RATING CHECK (cloud/StarCheckMain.kt) · ${ls.size} +EV lines the engine saw, game days ${days.firstOrNull() ?: "?"} to ${days.lastOrNull() ?: "?"} (${days.size} days) · " +
            "${ls.count { it.clv != null }} with a close · ${ls.count { it.settled }} settled"
        out += "Each line re-rated with the rating the app ships now (history points from profit_profile.json as of ${profile?.asOf ?: "?"}, lead points, the small-book cap). CLV = close_fair / cost - 1 (positive = beat the close);"
        out += "ROI per \$1 at the first price. ± is a 95% interval over GAMES (a game's lines averaged first). A sound rating gives more CLV and more ROI as the stars rise; intervals that overlap do not differ."
        out += "The history points were fitted to Tj's own bets (out of sample here); the lead points and the small-book cap were fitted to these lines (in sample)."
        val halfOrder = Comparator<String> { a, b -> (a.toDoubleOrNull() ?: 0.0).compareTo(b.toDoubleOrNull() ?: 0.0) }
        out += table("Stars now (re-rated)", ls, { String.format(Locale.US, "%.1f", stars.getValue(it)) }, halfOrder)
        out += table("Stars now, coarse", ls, { val s = stars.getValue(it); when { s < 2.0 -> "1-1.5"; s < 3.0 -> "2-2.5"; s < 4.0 -> "3-3.5"; else -> "4-5" } })
        out += table("Stars now, the lines the list showed", ls.filter { it.shown }, { val s = stars.getValue(it); when { s < 2.0 -> "1-1.5"; s < 3.0 -> "2-2.5"; s < 4.0 -> "3-3.5"; else -> "4-5" } })
        out += table("Stars as logged (the rating of the day)", ls, { it.loggedStars?.let { s -> String.format(Locale.US, "%.1f", s) } ?: "not logged" }, halfOrder)
        val firstHalf = days.take(days.size / 2).toSet()
        out += table("Stars now, by period (stability)", ls, { val s = stars.getValue(it); (if (it.day in firstHalf) "early " else "late ") + when { s < 2.5 -> "<2.5"; s < 3.5 -> "2.5-3"; else -> "3.5+" } })
        slope(ls.filter { it.clv != null }.map { stars.getValue(it) to it.clv!! })?.let { (b, t) -> out += String.format(Locale.US, "  CLV per star: %+.2f points (t %.1f, bets as the unit)", b * 100, t) }
        slope(ls.filter { it.settled && it.profit != null }.map { stars.getValue(it) to it.profit!! })?.let { (b, t) -> out += String.format(Locale.US, "  ROI per star: %+.2f points (t %.1f, bets as the unit)", b * 100, t) }
        out += "== Each input of the rating on its own =="
        out += table("Companies pricing both sides", ls, { when (it.books) { 0 -> "0"; 1 -> "1"; 2 -> "2"; 3 -> "3"; 4 -> "4"; in 5..7 -> "5-7"; else -> "8+" } })
        out += table("Share of them saying +EV", ls, { l -> l.agreeing?.takeIf { l.books > 0 }?.let { val s = it.toDouble() / l.books; when { s <= 0.5 -> "<=50%"; s < 0.75 -> "50-75%"; s < 1.0 -> "75-99%"; else -> "100%" } } ?: "unknown" })
        out += table("Edge at the price", ls, { when { it.ev < 0.02 -> "0-2%"; it.ev < 0.04 -> "2-4%"; it.ev < 0.06 -> "4-6%"; it.ev <= 0.12 -> "6-12%"; else -> "12%+" } })
        out += table("Sharp book", ls, { when (it.sharp) { true -> "agrees"; false -> "against"; null -> "none priced it" } })
        out += table("Books' fair spread (sd)", ls, { l -> l.spread?.let { when { it <= 0.01 -> "<=1 pt"; it <= 0.03 -> "1-3 pts"; it <= 0.05 -> "3-5 pts"; else -> ">5 pts" } } ?: "unknown" })
        out += table("Lead time", ls, { l -> l.minToStart?.let { when { it < 60 -> "<1h"; it <= 360 -> "1-6h"; it <= 1440 -> "6-24h"; else -> ">24h" } } ?: "unknown" })
        out += table("History + lead points", ls, { val p = typePoints(it, profile); when { p <= -5 -> "<=-5"; p < -0.5 -> "-5..-0.5"; p <= 0.5 -> "about 0"; p < 5 -> "0.5..5"; else -> ">=5" } })
        out += table("Kind", ls, { kindLabel(it.kind) })
        out += table("Kind and side", ls, { "${kindLabel(it.kind)} ${ProfitProfile.sideOf(it.selection)}" })
        out += table("League", ls, { it.league.ifEmpty { "?" } })
        return out.joinToString("\n") + "\n"
    }

    /** Every `lines-*.csv(.gz)` under [dir]. */
    fun read(dir: File): List<Line> = dir.walkTopDown().filter { it.isFile && it.name.startsWith("lines-") && (it.name.endsWith(".csv.gz") || it.name.endsWith(".csv")) }.sortedBy { it.name }
        .flatMap { f ->
            val stream = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream().buffered()) else f.inputStream().buffered()
            stream.bufferedReader().useLines { parse(it) }
        }.toList()
}

fun main(args: Array<String>) {
    if (args.size < 2) { System.err.println("usage: starcheck <dir of lines-*.csv.gz> <output file>"); return }
    val text = StarCheck.report(StarCheck.read(File(args[0])))
    File(args[1]).apply { parentFile?.mkdirs() }.writeText(text)
    println("star check: ${text.lines().size} lines -> ${args[1]}")
}
