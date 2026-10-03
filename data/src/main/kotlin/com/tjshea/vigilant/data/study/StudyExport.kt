package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetLedger
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.PlacedIndex
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * The file Tj sends to Claude for the scan study (Tj, 2026-10-03: "a file for Claude that contains comprehensive info about all scanned bets … The file will
 * prompt Claude to do deep analysis on all of the bets and find profitable patterns … tell Claude this goal and tell Claude to be thorough and analyze all data
 * for patterns and find profitable bet strategies"). In the order a reader needs it: a read-me that says what the data is, the goal and the task; a data
 * dictionary; the caveats of how it was collected; summary tables computed on the phone (the same splits Diagnostics uses for the bets Tj placed, here over every
 * bet a scan listed); then one JSON line per bet ([StudyRow]) with its record as first listed, every look at it, its close and its result.
 *
 * Streams: a day at a time is folded from the journal and its lines go to a scratch file as the summary tables are added up, so memory holds one day however
 * many days are in the file. Never a key, token or account id: nothing of that kind is in the journal.
 */
object StudyExport {

    /** The message that goes with the file in the share sheet. */
    const val PROMPT =
        "This is my Vigilant app's scan study: every bet its scanners listed as +EV on Novig, with the odds, EV, books, timing, closing line and result. " +
            "Please read the READ ME FIRST section, then analyze ALL of the data thoroughly for patterns and find the bet strategies that beat the closing line " +
            "and are profitable. Tell me what you found, with the evidence, and what to change in the app."

    /** "vigilant-scan-study-v0.57.0-2026-10-03-1553.txt". */
    fun fileName(versionName: String, now: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "vigilant-scan-study-v$versionName-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).apply { timeZone = zone }.format(Date(now)) + ".txt"

    /** What the header says about the phone and the settings the log was made under. */
    data class Meta(
        val versionName: String,
        val versionCode: Int,
        val device: String,
        /** The CNO view's filters and the app's rules in force now ([com.tjshea.vigilant.data.scanner.PresetRules.summary]): what decided which bets were listed. */
        val rules: String,
        val zone: TimeZone = TimeZone.getDefault(),
    )

    /** One bet, as a JSON line. */
    @Serializable
    data class StudyRow(
        val id: String,
        val firstSeenMs: Long,
        val startsAtMs: Long,
        val league: String,
        val event: String,
        val market: String,
        val selection: String,
        /** Who listed it: "c" (CNO), "v" (Vigilant's scan), "c+v" (both). */
        val src: String,
        /** Why the app's own CNO screen would have hidden it ([com.tjshea.vigilant.data.cno.CnoChecks.Reason] name); null: it showed it (or Vigilant listed it). */
        val screen: String? = null,
        /** The price at the first look: American odds, what a $1 payout costs (no fee pregame), the EV the list showed, its fair probability. */
        val american: Int? = null,
        val cost: Double,
        val ev: Double? = null,
        val fair: Double? = null,
        /** PENDING, WON, LOST, PUSH, VOID or FMV; [profit] is units won at [american] with a one-unit stake (null while open). */
        val status: String,
        val profit: Double? = null,
        val gradeNote: String? = null,
        /** The close: the side's devigged fair probability at the start, its American odds, where it came from, and why there's none ([closeNote]). */
        val closeFair: Double? = null,
        val closeAmerican: Int? = null,
        val closeVia: String? = null,
        val closeNote: String? = null,
        /** CLV = closeFair / cost − 1 at the first look, at the best price it was listed at, and at the last one. */
        val clv: Double? = null,
        val clvBest: Double? = null,
        val clvLast: Double? = null,
        val bestAmerican: Int? = null,
        val lastAmerican: Int? = null,
        /** Novig's own last price before the start, when the Tracker had read it (a bet Tj also placed). */
        val novigClose: Double? = null,
        /** Minutes it stayed listed from the first look to the last, how long before the start that last look was, and whether a scan then dropped it. */
        val listedMin: Long? = null,
        val lastListedMinToStart: Long? = null,
        val gone: Boolean = false,
        val looks: Int,
        val placedByTj: Boolean = false,
        val placedAmerican: Int? = null,
        val marketId: String? = null,
        val outcomeId: String? = null,
        /**
         * The most used fields of [atBet], at the top level for convenience: kind and sport, minutes to the start at the first look, CNO's book count, the book check's
         * companies pricing both sides, how many agree and the share ([agreeShare] = [booksAgreeing] / [booksTwoSided]; null with no book check), dollars at the price,
         * and the sharp veto's verdict.
         */
        val kind: String? = null,
        val sport: String? = null,
        val minToStartFirst: Long? = null,
        val cnoBooks: Int? = null,
        val booksTwoSided: Int? = null,
        val booksAgreeing: Int? = null,
        val agreeShare: Double? = null,
        val available: Double? = null,
        val sharpVerdict: String? = null,
        /** The record as first listed (see AtBet.kt): the book check is the first page read, stamped [AtBet.checkAtMs]. */
        val atBet: AtBet? = null,
        /**
         * The other scanner's record of the bet when both listed it ([atBet] is the first lister's): Vigilant's (its fair line's method and books) or CNO's
         * (its books, one-way flag and list age).
         */
        val vig: AtBet? = null,
        val cno: AtBet? = null,
        /** Every look, newest last: [minutes before the start, kind, American odds, EV, fair, books, dollars, agreeing, companies both sides, check EV, sharp verdict]. */
        val s: JsonArray = JsonArray(emptyList()),
    )

    /** [sb] as its line: [own] is Tj's Tracker bet on the same line, when he placed one. */
    fun rowOf(sb: StudyBet, now: Long, own: TrackedBet?): StudyRow {
        val b = sb.bet
        val listings = sb.sights.filter { Sight.isListing(it.second.k) }
        val closeOf = ClosingLine.closeOf(b, now)
        val closeFair = closeOf?.first
        fun clvAt(american: Int?): Double? = if (closeFair == null || american == null) null else closeFair / (1.0 / Odds.americanToDecimal(american)) - 1.0
        val prices = listings.mapNotNull { it.second.o }
        // The best price is the longest odds (the lowest cost).
        val best = prices.maxByOrNull { Odds.americanToDecimal(it) }
        val last = listings.lastOrNull()?.second?.o
        val ended = sb.sights.lastOrNull { Sight.isListing(it.second.k) || Sight.isGone(it.second.k) }
        val srcs = listings.map { it.second.k }.toSet()
        val src = listOfNotNull("c".takeIf { it in srcs }, "v".takeIf { it in srcs }).joinToString("+").ifEmpty { if (b.source == "vigilant") "v" else "c" }
        val a = b.atBet
        return StudyRow(
            id = sb.id, firstSeenMs = b.createdAtMs, startsAtMs = b.startsTs, league = b.league, event = b.eventName, market = b.marketLabel, selection = b.selection,
            src = src, screen = sb.screen, american = b.american, cost = b.cost, ev = b.evPercentAtBet, fair = b.fairAtBet,
            status = b.status.name, profit = b.profit, gradeNote = b.gradeNote.takeIf { b.status == BetStatus.PENDING || b.gradeManual },
            closeFair = closeFair, closeAmerican = closeFair?.let { Odds.probabilityToAmerican(it.coerceIn(0.001, 0.999)) },
            closeVia = closeOf?.second?.let { if (it == ClosingLine.SOURCE_CAPTURED) b.closeVia ?: "read before the start" else it }, closeNote = b.closeNote.takeIf { closeFair == null },
            clv = ClosingLine.clv(b, now), clvBest = clvAt(best), clvLast = clvAt(last), bestAmerican = best, lastAmerican = last,
            novigClose = b.novigClose,
            listedMin = ended?.let { (it.first - b.createdAtMs) / 60_000L }, lastListedMinToStart = ended?.let { (b.startsTs - it.first) / 60_000L },
            gone = ended?.second?.let { Sight.isGone(it.k) } == true,
            looks = sb.sights.size, placedByTj = own != null, placedAmerican = own?.american,
            marketId = b.marketId.ifBlank { null }, outcomeId = b.outcomeId.ifBlank { null },
            kind = a?.kind?.ifBlank { null }, sport = a?.sport?.ifBlank { null }, minToStartFirst = a?.minutesToStart, cnoBooks = a?.cnoBooks, booksTwoSided = a?.twoSided,
            booksAgreeing = a?.agreeing, agreeShare = a?.let { x -> x.agreeing?.let { g -> x.twoSided?.takeIf { it > 0 }?.let { n -> (g.toDouble() / n).round(4) } } },
            available = a?.available, sharpVerdict = a?.sharpVerdict,
            atBet = a?.copy(rules = null), vig = sb.vig?.copy(rules = null), cno = sb.cnoRec?.copy(rules = null),
            s = JsonArray(
                sb.sights.map { (t, sg) ->
                    JsonArray(
                        listOf<Any?>((b.startsTs - t) / 60_000L, sg.k, sg.o, sg.ev?.round(4), sg.f?.round(4), sg.b, sg.a, sg.g, sg.n, sg.ce?.round(4), sg.sv)
                            .map { v -> if (v == null) JsonNull else if (v is String) JsonPrimitive(v) else JsonPrimitive(v as Number) },
                    )
                },
            ),
        )
    }

    private fun Double.round(places: Int): Double {
        var f = 1.0
        repeat(places) { f *= 10 }
        return Math.round(this * f) / f
    }

    private val json = kotlinx.serialization.json.Json { encodeDefaults = false; explicitNulls = false }

    // ---- the summary ------------------------------------------------------------------------------------------------

    /** One group's numbers, added up a bet at a time. */
    class Agg {
        var n = 0
        var won = 0
        var lost = 0
        var pushed = 0
        var voided = 0
        var open = 0
        var profit = 0.0
        var staked = 0.0
        var clvN = 0
        var clvSum = 0.0
        var clvBeat = 0
        var evN = 0
        var evSum = 0.0

        fun add(r: StudyRow) {
            n++
            when (r.status) {
                BetStatus.WON.name -> { won++; staked += 1.0; profit += r.profit ?: 0.0 }
                BetStatus.LOST.name -> { lost++; staked += 1.0; profit += r.profit ?: 0.0 }
                BetStatus.PUSH.name, BetStatus.FMV.name -> pushed++
                BetStatus.VOID.name -> voided++
                else -> open++
            }
            r.clv?.let { clvN++; clvSum += it; if (it > 0) clvBeat++ }
            r.ev?.let { evN++; evSum += it }
        }

        fun line(label: String): String {
            val roi = if (staked > 0) "ROI ${pct(profit / staked)} (${"%+.1f".format(Locale.US, profit)}u on ${staked.toInt()}u)" else "no result yet"
            val clv = if (clvN > 0) "CLV ${pct(clvSum / clvN)} on $clvN closes, beat the close ${Math.round(100.0 * clvBeat / clvN)}%" else "no close yet"
            val ev = if (evN > 0) "EV listed ${pct(evSum / evN)}" else "no EV"
            return "$label · $n bets · $won-$lost-$pushed (W-L-P)${if (open > 0) " · $open open" else ""} · $roi · $clv · $ev"
        }

        private fun pct(v: Double) = String.format(Locale.US, "%+.2f%%", v * 100)
    }

    /** A study-specific split (beyond [BetLedger.Split]): [key] says which group a row is in. */
    private class Extra(val name: String, val key: (StudyRow, TimeZone) -> String)

    private val extraSplits: List<Extra> = listOf(
        Extra("Who listed it (c = CNO, v = Vigilant's scan)") { r, _ -> r.src },
        Extra("Would the app's own CNO screen have shown it") { r, _ -> r.screen?.let { "hidden: $it" } ?: "shown" },
        Extra("League") { r, _ -> r.league.ifBlank { "?" } },
        Extra("Market") { r, _ -> r.market.ifBlank { "?" } },
        Extra("Odds when first listed") { r, _ -> oddsBand(r.american) },
        Extra("EV when first listed") { r, _ -> r.ev?.let(::evBand) ?: "?" },
        Extra("Books behind the fair (CNO's count)") { r, _ -> r.atBet?.cnoBooks?.let { if (it >= 8) "8 or more" else "$it" } ?: "?" },
        Extra("Share of two-sided books agreeing (the book check)") { r, _ ->
            val a = r.atBet
            val agreeing = a?.agreeing
            val twoSided = a?.twoSided
            if (agreeing == null || twoSided == null || twoSided == 0) "no check" else shareBand(agreeing.toDouble() / twoSided)
        },
        Extra("How long it stayed listed") { r, _ -> r.listedMin?.let(::listedBand) ?: "?" },
        Extra("Whether a scan dropped it before the start") { r, _ -> if (r.gone) "dropped off the list" else "still listed at its last look" },
        Extra("Hour of day first listed (Eastern)") { r, z -> hourOf(r.firstSeenMs, z) },
        Extra("Tj placed it") { r, _ -> if (r.placedByTj) "yes" else "no" },
        Extra("Price moved after the first look (best listed odds vs the first)") { r, _ ->
            val f = r.american
            val bst = r.bestAmerican
            if (f == null || bst == null) "?" else if (Odds.americanToDecimal(bst) / Odds.americanToDecimal(f) - 1.0 > 0.005) "got longer (better for the bettor)" else "same"
        },
        Extra("Where it closed against its price (CLV)") { r, _ ->
            r.clv?.let { if (it > 0.05) "CLV over +5%" else if (it > 0.0) "CLV 0 to +5%" else if (it > -0.05) "CLV 0 to -5%" else "CLV under -5%" } ?: "no close"
        },
    )

    private fun oddsBand(a: Int?): String = when {
        a == null -> "?"
        a < -300 -> "under -300"
        a < -150 -> "-300 to -151"
        a <= 100 && a > -150 -> "-150 to +100"
        a <= 200 -> "+101 to +200"
        a <= 400 -> "+201 to +400"
        else -> "over +400"
    }

    private fun evBand(ev: Double): String = when {
        ev < 0.015 -> "under 1.5%"
        ev < 0.025 -> "1.5% to 2.5%"
        ev < 0.04 -> "2.5% to 4%"
        ev < 0.06 -> "4% to 6%"
        ev < 0.10 -> "6% to 10%"
        else -> "10% or more"
    }

    private fun shareBand(share: Double): String = when {
        share >= 0.999 -> "every book (100%)"
        share >= 0.75 -> "75% to 99%"
        share >= 0.5 -> "50% to 74%"
        else -> "under 50%"
    }

    private fun listedBand(min: Long): String = when {
        min < 2 -> "under 2 min"
        min < 10 -> "2 to 10 min"
        min < 60 -> "10 to 60 min"
        min < 360 -> "1 to 6 h"
        else -> "6 h or more"
    }

    private fun hourOf(ms: Long, zone: TimeZone): String {
        val h = SimpleDateFormat("HH", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/New_York") }.format(Date(ms)).toInt()
        return when {
            h < 6 -> "00-05"
            h < 10 -> "06-09"
            h < 13 -> "10-12"
            h < 17 -> "13-16"
            h < 20 -> "17-19"
            else -> "20-23"
        }
    }

    // ---- the file -----------------------------------------------------------------------------------------------------

    /**
     * Writes the whole file to [out]: the days in [journal] newest first, as many as fit under [maxBytes] of journal ([tmp] holds the bets' lines while the
     * summary is added up). [tracked]: Tj's own Tracker bets, to mark the ones he placed. Returns the bets written.
     */
    fun write(out: java.io.Writer, journal: StudyJournal, tracked: List<TrackedBet>, meta: Meta, now: Long, tmp: File, maxBytes: Long = MAX_BYTES): Int {
        val ownIndex = tracked.filter { !it.isLock && it.createdAtMs < it.startsTs }.groupBy { PlacedIndex.identity(it.eventName, it.marketLabel, it.selection) ?: "" }
            .filterKeys { it.isNotEmpty() }
        val all = journal.days().reversed()
        var bytes = 0L
        val days = ArrayList<LocalDate>()
        for (d in all) {
            val size = journal.file(d).length()
            if (days.isNotEmpty() && bytes + size > maxBytes) break
            bytes += size
            days += d
        }
        val leftOut = all.size - days.size
        val overall = Agg()
        val noOutliers = Agg()
        val splits = BetLedger.Split.entries.associateWith { LinkedHashMap<String, Agg>() }
        val extras = extraSplits.associate { it.name to LinkedHashMap<String, Agg>() }
        val closeReasons = HashMap<String, Int>()
        val closeVia = HashMap<String, Int>()
        var withCloseCount = 0
        var rows = 0
        var firstDay: LocalDate? = null
        var lastDay: LocalDate? = null
        tmp.parentFile?.mkdirs()
        tmp.bufferedWriter().use { lines ->
            for (day in days) {
                val folded = journal.fold(day).values.sortedByDescending { it.bet.createdAtMs }
                if (folded.isEmpty()) continue
                firstDay = day.takeIf { firstDay == null || day < firstDay!! } ?: firstDay
                lastDay = day.takeIf { lastDay == null || day > lastDay!! } ?: lastDay
                for (sb in folded) {
                    val own = PlacedIndex.identity(sb.bet.eventName, sb.bet.marketLabel, sb.bet.selection)?.let { id ->
                        val tol = if (sb.bet.league.equals("MLB", true)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
                        ownIndex[id]?.firstOrNull { abs(it.startsTs - sb.bet.startsTs) <= tol }
                    }
                    val row = rowOf(sb, now, own)
                    lines.write(json.encodeToString(StudyRow.serializer(), row))
                    lines.write("\n")
                    rows++
                    overall.add(row)
                    if (!sb.bet.isOutlier) noOutliers.add(row)
                    for (split in BetLedger.Split.entries) splits.getValue(split).getOrPut(BetLedger.keyOf(sb.bet, split)) { Agg() }.add(row)
                    for (x in extraSplits) extras.getValue(x.name).getOrPut(x.key(row, meta.zone)) { Agg() }.add(row)
                    if (row.clv != null) { withCloseCount++; closeVia.merge(row.closeVia?.substringBefore(" ·") ?: "?", 1, Int::plus) }
                    else if (sb.bet.startsTs < now && row.closeNote != null) closeReasons.merge(row.closeNote.take(90), 1, Int::plus)
                }
            }
        }

        val clock = SimpleDateFormat("MMM d, yyyy h:mm a z", Locale.US).apply { timeZone = meta.zone }
        out.appendLine("VIGILANT SCAN STUDY · version ${meta.versionName} · ${fileName(meta.versionName, now, meta.zone)}")
        out.appendLine()
        out.appendLine("== READ ME FIRST (for Claude) ==")
        readMe(meta, clock.format(Date(now)), rows, firstDay, lastDay, days.size, leftOut, bytes).forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("== DATA DICTIONARY ==")
        DICTIONARY.forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("== HOW THIS DATA WAS COLLECTED, AND WHAT IT CAN'T SAY ==")
        CAVEATS.forEach { out.appendLine(it) }
        out.appendLine("Rules in force when this file was made (they decided which bets the scanners listed; they may have changed during the period): ${meta.rules}")
        out.appendLine()
        out.appendLine("== SUMMARY (added up on the phone; ROI is at the first-listed price with one unit a bet; CLV is against the close found, see closeVia) ==")
        out.appendLine(overall.line("ALL BETS"))
        out.appendLine(noOutliers.line("without outliers (EV listed over ±6%)"))
        out.appendLine("Closes found: $withCloseCount of $rows" + (closeVia.takeIf { it.isNotEmpty() }?.entries?.sortedByDescending { it.value }?.joinToString(", ", " (", ")") { "${it.key} ${it.value}" } ?: ""))
        if (closeReasons.isNotEmpty()) {
            out.appendLine("Why started bets have no close yet, most common first:")
            closeReasons.entries.sortedByDescending { it.value }.take(8).forEach { out.appendLine("    ×${it.value} ${it.key}") }
        }
        out.appendLine()
        out.appendLine("== SPLITS (each group: bets · W-L-P · ROI · CLV · EV listed; a group needs ~200+ bets with a close before its CLV says much) ==")
        for (split in BetLedger.Split.entries) {
            val groups = splits.getValue(split)
            if (groups.isEmpty()) continue
            out.appendLine("-- ${split.label} --")
            groups.entries.sortedByDescending { it.value.n }.forEach { out.appendLine("   " + it.value.line(it.key)) }
        }
        for (x in extraSplits) {
            val groups = extras.getValue(x.name)
            if (groups.isEmpty()) continue
            out.appendLine("-- ${x.name} --")
            groups.entries.sortedByDescending { it.value.n }.take(MAX_SPLIT_GROUPS).forEach { out.appendLine("   " + it.value.line(it.key)) }
        }
        out.appendLine()
        out.appendLine("== EVERY BET (JSON lines, newest first; $rows bets) ==")
        out.appendLine("<<<JSONL")
        tmp.bufferedReader().use { it.copyTo(out) }
        out.appendLine(">>>")
        out.appendLine("== END OF FILE ==")
        tmp.delete()
        return rows
    }

    // ---- the words -----------------------------------------------------------------------------------------------------

    private fun readMe(meta: Meta, madeAt: String, rows: Int, first: LocalDate?, last: LocalDate?, days: Int, leftOut: Int, bytes: Long): List<String> = listOf(
        "This file was made by the Vigilant app (Android, Kotlin + Compose; modules engine, data, app) on $madeAt, version ${meta.versionName} (code ${meta.versionCode}), on ${meta.device}.",
        "Vigilant finds +EV bets on the Novig sportsbook for Tj, who owns it; it is built across Claude sessions. Repo: github.com/tjshea90/novig (public), branch main.",
        "",
        "WHAT THIS IS: the scan study, a log of EVERY bet Vigilant's two scanners listed as +EV on Novig — CrazyNinjaOdds (\"CNO\") and Vigilant's own scan — not only the ones Tj bet.",
        "$rows bets from game days ${first ?: "?"} to ${last ?: "?"} ($days days, ${bytes / 1024} KB of log)" + if (leftOut > 0) "; $leftOut older day(s) are on the phone but left out to keep this file a size Claude can read." else ".",
        "Each bet was logged the moment a scan first listed it, with everything the app knew then; watched while it stayed listed (every change of odds, EV or books, and when a scan dropped it); then, after its game,",
        "graded (won / lost / push) and given its closing line by the app's own grading and closing-line code. Because it covers every listed bet, it has none of the selection of Tj's own bets.",
        "",
        "THE GOAL IS PROFIT: find which bets, bought when, beat the closing line (CLV) and make money. CLV is the leading indicator (it needs far fewer bets than results do); results confirm it slowly.",
        "Tj bets on Novig only, as a taker at the listed price (pregame Novig takers pay no fee) and, with the Bids tab, as a maker posting bids under its fair price.",
        "",
        "YOUR TASK — be thorough, and analyze ALL of the data for patterns and for profitable bet strategies:",
        " 1. Check the data first: counts by status and by close source, bets with no close and why, duplicates, odd values. Say what you can and can't trust. Parse the JSON lines with code (python/pandas); do not read them by eye.",
        " 2. Overall: the share of bets that beat the close, the average CLV, ROI at the first-listed price, and how both change with sample size. The EV the lists showed against the CLV the close says (is the edge real?).",
        " 3. Split by everything in the data dictionary, one at a time and then together: kind and market, league and sport, odds and EV bands, books behind the fair and the share of books agreeing, the sharp veto's verdict, whether the bet was",
        "    listed by CNO, Vigilant or both, dollars available, how long before the start it was first listed and how long it stayed listed, the hour of day, whether the price got better or worse after the first look, whether a scan dropped it.",
        " 4. Timing: from the looks (the `s` arrays) find WHEN in a bet's life the price is best and the close most often beaten — and whether a bet that stays listed a long time is a trap (the line moved against it) or a gift.",
        " 5. Traps: bets that look +EV on paper and lose to the close (the other side was a sharp's). Find the signs that separate them (a sharp book dissenting, a falling EV across the looks, few books, a long price, how long it stayed listed …).",
        " 6. Strategies: build candidate rules that pick bets (and the price and moment to take them), measure each out of sample (split by DATE, never at random; same-game bets are correlated, so count games, not bets), and report for every rule:",
        "    bets, games, CLV with a confidence interval, ROI, expected bets a day, and the stake it could carry (the `available` dollars). Prefer a few simple rules that hold on both halves of the period over many fitted ones; say how likely each is to be luck.",
        " 7. Deliver: a ranked list of the strategies worth trying with the evidence, the exact app settings to change (presets in data/scanner/Presets.kt, the sharp veto in data/scanner/SharpVeto.kt, the trap guard, the auto-bet's rules, CNO's filters), and what",
        "    more this log should record next time. If the data is too thin to say, say so and what number of bets would settle it.",
        "",
        "RULES THAT APPLY TO ANY CHANGE (details in CLAUDE.md and BRIEF.md)",
        "- Every task is for Vigilant (Novig) only; the Vigilant MGM module is frozen. Mobile data and phone storage are not constraints; API credits and rate limits are a budget.",
        "- Auto-bet places REAL money bets. Never loosen a safety limit because a finding suggests more bets; ask Tj when a change needs his decision.",
        "- Make changes the way the repo asks (CLAUDE.md): write the request into TASKS.md first, load the matching skill, test (mutation-check new tests), run `bash tools/test.sh`, ship with `bash ship.sh`, release via the workflow, send Tj the Release link.",
        "- The repo is public: never commit a key, token or account id. This file contains none.",
    )

    private val DICTIONARY: List<String> = listOf(
        "One JSON object per line under EVERY BET. Times are epoch milliseconds (UTC); \"days\" are Eastern. Odds are American. Probabilities are 0-1. EV and CLV are fractions (0.04 = 4%).",
        "id · firstSeenMs · startsAtMs · league · event · market · selection: the bet and when a scan first listed it / when its game starts.",
        "src: who listed it (c = CrazyNinjaOdds, v = Vigilant's scan, c+v = both; a bet both list is one record). screen: why the app's own CNO screen would have hidden it (null = it showed it): NOT_A_GAME, MISMATCH (EV doesn't follow from its fair odds), ONE_WAY, BOOKS, ODDS, TOO_GOOD (EV over 20%), EV.",
        "american · cost · ev · fair: at the FIRST look — the Novig price (American; CNO's, or Novig's own live price when it had been read in the last minute), what a $1 payout costs at it (= implied probability; pregame Novig has no fee), the EV the list showed, and its fair probability (CNO's, or Vigilant's).",
        "status · profit · gradeNote: PENDING, WON, LOST, PUSH, VOID or FMV; profit is units at the first-listed price with a one-unit stake (null while open). gradeNote is the reason a result is missing.",
        "closeFair · closeAmerican · closeVia · closeNote: the closing line — the side's devigged fair probability at the start (and its odds), its source, and why none was found. Sources: \"ParlayAPI · Pinnacle close\" (the sharpest), \"ESPN\" (DraftKings / ESPN BET game lines only), \"Novig's last trades\" (the last half hour before the start), or \"Tracker · …\" (read before the start for a bet Tj also placed).",
        "clv · clvBest · clvLast: closeFair / cost − 1 at the first-listed price, at the best (longest) odds it was listed at, and at the last listed price. bestAmerican · lastAmerican: those prices. novigClose: Novig's own last price before the start when the Tracker read it.",
        "listedMin · lastListedMinToStart · gone: minutes from the first look to the last, how many minutes before the start the last look was, and whether a scan then dropped it (its edge fell under the filters, or CNO's row limit pushed it out).",
        "looks · placedByTj · placedAmerican: number of looks; whether Tj also placed this bet (his own Tracker's) and at what price. marketId · outcomeId: Novig's public ids when known.",
        "kind · sport · minToStartFirst · cnoBooks · booksTwoSided · booksAgreeing · agreeShare · available · sharpVerdict: the most used fields of atBet at the top level. cnoBooks = books behind CNO's fair price (on every row); booksTwoSided = companies whose page prices both sides, booksAgreeing = those whose own fair says +EV at Novig's price, agreeShare = booksAgreeing / booksTwoSided (null when the book check wasn't made: only the top few bets get a page read).",
        "atBet: the app's record of the bet as first listed (data/.../tracker/AtBet.kt): league, sport, kind (PROP, MONEYLINE, SPREAD, TOTAL, TEAM_TOTAL, PERIOD, OTHER), minutesToStart, american, otherAmerican (the other side's price), available (Novig dollars at the price), ev, fair, cnoBooks (books behind CNO's fair), cnoOneWay, cnoListAgeSec,",
        "    and the app's own BOOK CHECK from CNO's game page when it was read (checkAtMs says when; a page is read for the top few bets, so many bets have none): twoSided (companies pricing both sides), oneSided, agreeing (those whose own fair says +EV at Novig's price), verdict (CONFIRMED / NOT_CONFIRMED …), checkFair, checkEv, books (every book's odds, other side, fair and the EV it gives Novig's price), dissent (books saying not +EV),",
        "    sharpVerdict / sharpBook / sharpEv (the sharpest book for the kind of bet — Pinnacle and Circa for game lines, Kalshi and ProphetX for props — and whether it VETOED the price), fullKelly (the Kelly share of bankroll this edge calls for), preset (the preset in force).",
        "vig / cno: the OTHER scanner's record when both listed the bet (atBet is the first lister's): vig = Vigilant's (its fair line's method, the books and sharp books behind it, how old their quotes were); cno = CNO's (its books, one-way flag, list age).",
        "s: every look, oldest first, each [minutesBeforeStart, kind, american, ev, fair, books, dollars, agreeing, companiesBothSides, checkEv, sharpVerdict]. kind: c = a CNO scan listed it, v = a Vigilant scan did, k = its book page was read (agreeing / companies / checkEv / sharp verdict are filled), xc / xv = that scan no longer lists it.",
        "    A look is logged when the bet is first seen, when odds / books change or EV moves 0.25 points (at most one a minute), at least every 5 minutes while it's listed, when it drops off, and for each book check that changed.",
    )

    private val CAVEATS: List<String> = listOf(
        "- SELECTION: only bets a scanner listed are here. CNO sends its best rows under Tj's filters (devig method, longest odds, fewest books, smallest EV, row limit — see the rules line below); a bet the filters hid is not in the data. A bet \"dropped\" (gone) may have lost its edge OR been pushed out by the row limit.",
        "- FIRST-LISTED PRICE: ROI and clv assume the bet could be taken at the price at the first look, for a unit. Novig had only `available` dollars at that price, and many bets were listed for minutes: check listedMin and available before believing a rule's size.",
        "- CLOSES: the close sources differ (Pinnacle is sharper than ESPN's DraftKings line; Novig's last trades can lag). Compare CLV by closeVia before pooling. Bets with no close are left out of every CLV figure; their reasons are in the summary.",
        "- RESULTS are noisy: at +3% EV a bet wins its edge once in thousands of bets. Trust CLV first; use ROI only to confirm over hundreds of games. Same-game and same-player bets are correlated: the effective sample is games, not rows.",
        "- Every bet is one unit at the first-listed price whatever Tj staked; the bets he placed himself are marked placedByTj. His own placed bets are a separate record (the diagnostics file's EVERY BET).",
        "- Overlap: one bet listed by both scanners is one row. Different books' sister sites are counted once in the book check (one company, one opinion).",
        "- The sample is one phone over the dates above, while the app and its rules were changing (the version and preset are in atBet). Say 'the data suggests', and ask Tj when a change needs a decision.",
    )

    /** The most journal bytes in one file (the share sheet and Claude's reading both have limits); older days stay on the phone. */
    const val MAX_BYTES = 24L * 1024 * 1024

    private const val MAX_SPLIT_GROUPS = 25
}
