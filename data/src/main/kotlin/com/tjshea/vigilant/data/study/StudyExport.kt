package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetLedger
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.ClosePlausibility
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
        "This is my Vigilant app's scan study: every bet its scanners found as +EV on Novig (CrazyNinjaOdds' whole list, including the bets my filters hide from the app), with the odds, EV, books, timing, closing line and result. " +
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
        /** The wide read in words: what CNO was asked, and what its last read held ([CnoWideState]); null when the wide read is off or hasn't run. */
        val wide: String? = null,
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
        /** Who found it: "c" (the app's CNO list), "w" (the wide read: every row CNO finds, hidden ones too), "v" (Vigilant's scan), any together ("c+w", "w", "c+v+w"). */
        val src: String,
        /**
         * Why the app's own CNO list would not have shown it at the first look: a [com.tjshea.vigilant.data.cno.CnoChecks.Reason] name (the app's screen under Tj's filters),
         * NOT_LISTED (the screen passes it but CNO's read under his filters didn't carry it); null: it showed it (or Vigilant listed it).
         */
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
        /** Tj placed it himself, as a taker (a bet): not a bid of Vigilant's that a taker filled (see [placedAs]). */
        val placedByTj: Boolean = false,
        /** How the Tracker holds this line, when it holds one: "bet" (a taker order) or "bid" (Vigilant's make order, filled). Null: not in the Tracker. */
        val placedAs: String? = null,
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
        /** Every column CNO printed for the row at its first wide look, by header, and the row's `data-*` attributes with an `@` (null: it was never in a wide read). */
        val cols: Map<String, String>? = null,
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
        val found = ClosingLine.closeOf(b, now)
        // A close that can't be this bet's (another game's: Tj's file, 2026-10-03, a Washington State -117 "closed" at +272, -50% CLV) is left out of every figure with
        // its reason, whatever the journal holds from before the matcher was fixed ([ClosePlausibility]; the journal is append-only and keeps the line).
        val implausible = found?.let { ClosePlausibility.reason(b, CloseLookup.Found(it.first, it.second)) }
        val closeOf = found.takeIf { implausible == null }
        val closeFair = closeOf?.first
        fun clvAt(american: Int?): Double? = if (closeFair == null || american == null) null else closeFair / (1.0 / Odds.americanToDecimal(american)) - 1.0
        val prices = listings.mapNotNull { it.second.o }
        // The best price is the longest odds (the lowest cost).
        val best = prices.maxByOrNull { Odds.americanToDecimal(it) }
        val last = listings.lastOrNull()?.second?.o
        // How long it stayed listed means in the app's lists (CNO's, Vigilant's): the wide read finds more than they carry.
        val ended = sb.sights.lastOrNull { Sight.isAppListing(it.second.k) || Sight.isAppGone(it.second.k) }
        val srcs = listings.map { it.second.k }.toSet()
        val src = listOfNotNull("c".takeIf { it in srcs }, "v".takeIf { it in srcs }, "w".takeIf { it in srcs }).joinToString("+").ifEmpty { if (b.source == "vigilant") "v" else "c" }
        val a = b.atBet
        return StudyRow(
            id = sb.id, firstSeenMs = b.createdAtMs, startsAtMs = b.startsTs, league = b.league, event = b.eventName, market = b.marketLabel, selection = b.selection,
            src = src, screen = sb.screen, american = b.american, cost = b.cost, ev = b.evPercentAtBet, fair = b.fairAtBet,
            status = b.status.name, profit = b.profit, gradeNote = b.gradeNote.takeIf { b.status == BetStatus.PENDING || b.gradeManual },
            closeFair = closeFair, closeAmerican = closeFair?.let { Odds.probabilityToAmerican(it.coerceIn(0.001, 0.999)) },
            closeVia = closeOf?.second?.let { if (it == ClosingLine.SOURCE_CAPTURED) b.closeVia ?: "read before the start" else it }, closeNote = implausible ?: b.closeNote.takeIf { closeFair == null },
            clv = if (closeOf == null) null else ClosingLine.clv(b, now), clvBest = clvAt(best), clvLast = clvAt(last), bestAmerican = best, lastAmerican = last,
            novigClose = b.novigClose,
            listedMin = ended?.let { (it.first - b.createdAtMs) / 60_000L }, lastListedMinToStart = ended?.let { (b.startsTs - it.first) / 60_000L },
            gone = ended?.second?.let { Sight.isAppGone(it.k) } == true,
            looks = sb.sights.size, placedByTj = own != null && !own.isBid, placedAs = own?.let { BetOrBid.of(it).word }, placedAmerican = own?.american,
            marketId = b.marketId.ifBlank { null }, outcomeId = b.outcomeId.ifBlank { null },
            kind = a?.kind?.ifBlank { null }, sport = a?.sport?.ifBlank { null }, minToStartFirst = a?.minutesToStart, cnoBooks = a?.cnoBooks, booksTwoSided = a?.twoSided,
            booksAgreeing = a?.agreeing, agreeShare = a?.let { x -> x.agreeing?.let { g -> x.twoSided?.takeIf { it > 0 }?.let { n -> (g.toDouble() / n).round(4) } } },
            available = a?.available, sharpVerdict = a?.sharpVerdict,
            cols = sb.cols, atBet = a?.copy(rules = null), vig = sb.vig?.copy(rules = null), cno = sb.cnoRec?.copy(rules = null),
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

        /** One game's share of [profit], [staked] and the closes, for the interval that counts games and not bets. */
        private class GameSum { var profit = 0.0; var staked = 0.0; var clvN = 0; var clvSum = 0.0 }
        private val games = HashMap<String, GameSum>()

        fun add(r: StudyRow) {
            val g = games.getOrPut("${r.league}|${r.event}|${r.startsAtMs}") { GameSum() }
            n++
            when (r.status) {
                BetStatus.WON.name -> { won++; staked += 1.0; profit += r.profit ?: 0.0; g.staked += 1.0; g.profit += r.profit ?: 0.0 }
                BetStatus.LOST.name -> { lost++; staked += 1.0; profit += r.profit ?: 0.0; g.staked += 1.0; g.profit += r.profit ?: 0.0 }
                BetStatus.PUSH.name, BetStatus.FMV.name -> pushed++
                BetStatus.VOID.name -> voided++
                else -> open++
            }
            r.clv?.let { clvN++; clvSum += it; if (it > 0) clvBeat++; g.clvN++; g.clvSum += it }
            r.ev?.let { evN++; evSum += it }
        }

        /**
         * The half-width of a 95% interval for a ratio of sums ([num] over [den] per game) that counts GAMES, not bets: bets of one game win and lose together (the
         * game results' variance was 1.5 where independent bets give 1.0, RESEARCH.md §81.2), so the interval of an independent-bets formula is too narrow. The
         * ratio estimator's robust variance, G/(G−1) · Σ(num_g − R·den_g)² / (Σden)²; null with fewer than [MIN_GAMES] games (it would say nothing).
         */
        private fun halfWidth(num: (GameSum) -> Double, den: (GameSum) -> Double): Pair<Double, Int>? {
            val gs = games.values.filter { den(it) > 0 }
            val d = gs.sumOf(den)
            if (gs.size < MIN_GAMES || d <= 0) return null
            val ratio = gs.sumOf(num) / d
            val variance = gs.size / (gs.size - 1.0) * gs.sumOf { (num(it) - ratio * den(it)).let { e -> e * e } } / (d * d)
            return 1.96 * Math.sqrt(variance) to gs.size
        }

        fun line(label: String): String {
            val roiBand = halfWidth({ it.profit }, { it.staked })?.let { (h, g) -> ", ±${"%.1f".format(Locale.US, h * 100)} points over $g games" } ?: ""
            val clvBand = halfWidth({ it.clvSum }, { it.clvN.toDouble() })?.let { (h, g) -> " ±${"%.2f".format(Locale.US, h * 100)} over $g games" } ?: ""
            val roi = if (staked > 0) "ROI ${pct(profit / staked)} (${"%+.1f".format(Locale.US, profit)}u on ${staked.toInt()}u$roiBand)" else "no result yet"
            val clv = if (clvN > 0) "CLV ${pct(clvSum / clvN)}$clvBand on $clvN closes, beat the close ${Math.round(100.0 * clvBeat / clvN)}%" else "no close yet"
            val ev = if (evN > 0) "EV listed ${pct(evSum / evN)}" else "no EV"
            // VOID is said when there is one (a voided bet is neither won nor lost, and it was in no count before: Tj's v0.70.1 file, 9 voids unprinted).
            return "$label · $n bets · $won-$lost-$pushed (W-L-P)${if (voided > 0) " · $voided void" else ""}${if (open > 0) " · $open open" else ""} · $roi · $clv · $ev"
        }

        private fun pct(v: Double) = String.format(Locale.US, "%+.2f%%", v * 100)
    }

    /** A study-specific split (beyond [BetLedger.Split]): [key] says which group a row is in; null: the row isn't part of this split. */
    private class Extra(val name: String, val key: (StudyRow, TimeZone) -> String?)

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
        Extra("Tj placed it") { r, _ -> if (r.placedByTj) "yes, as a bet" else if (r.placedAs == "bid") "no, but Vigilant's bid on it filled" else "no" },
        Extra("Price moved after the first look (best listed odds vs the first)") { r, _ ->
            val f = r.american
            val bst = r.bestAmerican
            if (f == null || bst == null) "?" else if (Odds.americanToDecimal(bst) / Odds.americanToDecimal(f) - 1.0 > 0.005) "got longer (better for the bettor)" else "same"
        },
        // Tj, 2026-10-04: "ensure that the study and diagnostic makes sense and it isn't feeding you illogical data". A pooled CLV mixes yardsticks that disagree (RESEARCH.md §81.2).
        Extra("Close source, for the bets that have one (never pool across them; Novig's trades split by how many trades are behind the close)") { r, _ ->
            if (r.clv == null) null else closeSource(r.closeVia).let { src -> novigTrades(r.closeVia)?.let { n -> "$src: ${tradesBand(n)}" } ?: src }
        },
        // Tj, 2026-10-05: "keep track of all betting information used with this Pinnacle only setting on so I can track how well bets do clv and EV and profit when only compared to Pinnacle".
        Extra("Pinnacle only (the mode: the fair is Pinnacle's devigged price alone, nothing else read)") { r, _ ->
            if (r.src != "v") null else if (r.atBet?.pinnacleOnly == true) "on: EV is against Pinnacle alone" else "off: Vigilant's usual fair"
        },
        Extra("Pinnacle only: how old Pinnacle's price was when the bet was first listed") { r, _ ->
            if (r.atBet?.pinnacleOnly != true) null else when (val age = r.atBet.pinnacleAgeSec) {
                null -> "not recorded"
                in 0..30 -> "30 s or under"
                in 31..60 -> "31 to 60 s"
                in 61..90 -> "61 to 90 s"
                else -> "over 90 s"
            }
        },
        Extra("Where it closed against its price (CLV)") { r, _ ->
            r.clv?.let { if (it > 0.05) "CLV over +5%" else if (it > 0.0) "CLV 0 to +5%" else if (it > -0.05) "CLV 0 to -5%" else "CLV under -5%" } ?: "no close"
        },
        // Tj, 2026-10-03: "consider if it is needed or smart to require that prop bets have at least one sharp prop book that agrees … Do option 1 and add the props split to the study".
        Extra("PROPS: what the sharp-ranked book said (the app's veto; the first of Kalshi, ProphetX, then FanDuel/Caesars, DraftKings on MLB, that prices both sides)") { r, _ ->
            if (isProp(r)) propSharp(r) else null
        },
        Extra("PROPS: the sharp book's own edge at Novig's price") { r, _ ->
            if (isProp(r)) propSharpEdge(r) else null
        },
        Extra("PROPS: are the exchanges (Kalshi, ProphetX) on the bet's page") { r, _ ->
            if (isProp(r)) propExchanges(r) else null
        },
    )

    private val EXCHANGES = setOf("Kalshi", "ProphetX")

    /** A close's source on its own, the way the summary and the splits count it: "Novig's last trades (7)" is "Novig's last trades" (the 7 trades are [novigTrades]). */
    internal fun closeSource(via: String?): String = via?.substringBefore(" ·")?.replace(Regex("""\s*\(\d+\)$"""), "") ?: "?"

    /** How many trades are behind a "Novig's last trades (N)" close; null for any other. */
    internal fun novigTrades(via: String?): Int? = via?.let { Regex("""^Novig's last trades \((\d+)\)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** One or two trades are a price, not a close: Tj's own bets' CLV against them ran -2.3% where 3-5 trades gave +0.9% (RESEARCH.md §81.2). */
    internal fun tradesBand(n: Int): String = when {
        n <= 2 -> "1-2 trades (noisy)"
        n <= 5 -> "3-5 trades"
        n <= 10 -> "6-10 trades"
        else -> "11 or more trades"
    }

    private fun isProp(r: StudyRow) = r.kind == BetKind.PROP.name

    /** Whether the bet's CNO game page was read (at its first look or by the green check after): only then does its record carry a real sharp verdict. */
    private fun pageRead(r: StudyRow): Boolean = r.atBet?.let { it.checkAtMs != null || it.twoSided != null || it.books.isNotEmpty() } == true

    /**
     * The sharp veto's verdict at the bet's first book check; null when no page was read (no verdict). Not the record's own field alone: a bet logged with no page
     * is judged against no books and says NO_SHARP, which is "nobody ranked prices both sides" only when there was a page to look at.
     */
    private fun verdictOf(r: StudyRow): String? = if (pageRead(r)) r.atBet?.sharpVerdict else null

    internal fun propSharp(r: StudyRow): String {
        val a = r.atBet
        val v = verdictOf(r) ?: return "no book page was read (no verdict)"
        if (v == SharpVeto.Verdict.NO_SHARP.name) return "no sharp-ranked book prices both sides (not vetoed)"
        val says = if (v == SharpVeto.Verdict.PASSED.name) "agrees" else "says no (vetoed)"
        return if (a?.sharpBook in EXCHANGES) "an exchange (Kalshi or ProphetX) $says" else "an originating book (FanDuel, Caesars or DraftKings) $says"
    }

    internal fun propSharpEdge(r: StudyRow): String? {
        val a = r.atBet?.takeIf { pageRead(r) } ?: return null
        val ev = a.sharpEv ?: return null
        return when {
            ev < 0.0 -> "sharp edge under 0%"
            ev < 0.01 -> "sharp edge 0 to 1%"
            ev < 0.02 -> "sharp edge 1 to 2%"
            ev < 0.04 -> "sharp edge 2 to 4%"
            else -> "sharp edge 4% or more"
        }
    }

    internal fun propExchanges(r: StudyRow): String {
        val books = r.atBet?.books.orEmpty()
        if (books.isEmpty()) return "no book page was read"
        val on = books.filter { it.book in EXCHANGES }
        return when {
            on.any { it.other != null } -> "an exchange prices both sides"
            on.isNotEmpty() -> "an exchange prices one side only"
            else -> "neither exchange is on the page"
        }
    }

    /** A rule for props that keeps or drops each bet by the sharp veto's verdict (null: no verdict, can't tell). Simulated over the log: the app's rules are unchanged. */
    private class WhatIf(val label: String, val keeps: (StudyRow) -> Boolean?)

    private val whatIfRules: List<WhatIf> = listOf(
        WhatIf("TODAY'S RULE: skip a prop only when a sharp-ranked book that prices both sides says it isn't +EV enough (no sharp book on the page: kept)") { r ->
            verdictOf(r)?.let { it != SharpVeto.Verdict.VETOED.name }
        },
        WhatIf("REQUIRE a sharp-ranked book (Kalshi, ProphetX, FanDuel, Caesars, DraftKings) to price both sides and agree") { r ->
            verdictOf(r)?.let { it == SharpVeto.Verdict.PASSED.name }
        },
        WhatIf("REQUIRE an exchange (Kalshi or ProphetX) to price both sides and agree") { r ->
            verdictOf(r)?.let { it == SharpVeto.Verdict.PASSED.name && r.atBet?.sharpBook in EXCHANGES }
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
     * Writes the whole file to [out]: the days in [journal] newest first, as many as fit under [DAYS_FACTOR] times [maxBytes] of journal ([tmp] holds the bets' lines
     * while the summary is added up). The bets' lines stop at [maxBytes] (the bets the app's lists hid, [StudyRow.screen] set, at most [HIDDEN_SHARE] of it, so
     * a day of wide-read finds can't push out the ones the app showed); every bet is in the summary all the same. [tracked]: Tj's own Tracker bets, to mark the ones he
     * placed. Returns the bets written.
     */
    fun write(
        out: java.io.Writer, journal: StudyJournal, tracked: List<TrackedBet>, meta: Meta, now: Long, tmp: File, maxBytes: Long = MAX_BYTES,
        /** Vigilant's own bids (Make orders, files/maker.json): every one posted in the last 14 days, for the BIDS section. */
        bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid> = emptyList(),
    ): Int {
        // A bet Tj took comes before a bid of Vigilant's that filled on the same line (Tj, 2026-10-07: a bid is not Tj's own bet).
        val ownIndex = tracked.filter { !it.isLock && it.createdAtMs < it.startsTs }.sortedBy { it.isBid }.groupBy { PlacedIndex.identity(it.eventName, it.marketLabel, it.selection) ?: "" }
            .filterKeys { it.isNotEmpty() }
        val all = journal.days().reversed()
        var bytes = 0L
        val days = ArrayList<LocalDate>()
        for (d in all) {
            val size = journal.file(d).length()
            if (days.isNotEmpty() && bytes + size > maxBytes * DAYS_FACTOR) break
            bytes += size
            days += d
        }
        val leftOut = all.size - days.size
        val overall = Agg()
        val shown = Agg()
        val hidden = Agg()
        val noOutliers = Agg()
        val splits = BetLedger.Split.entries.associateWith { LinkedHashMap<String, Agg>() }
        val extras = extraSplits.associate { it.name to LinkedHashMap<String, Agg>() }
        // Props only: for each what-if rule, the bets it would keep, drop and can't judge.
        val whatIf = whatIfRules.map { Triple(Agg(), Agg(), Agg()) }
        val closeReasons = HashMap<String, Int>()
        val closeVia = HashMap<String, Int>()
        var withCloseCount = 0
        // The bets a close can be asked of: started, or already holding one (the share of those that have one is the coverage; the whole file's count includes games not yet on).
        var closable = 0
        var rows = 0
        var cut = 0
        var written = 0L
        var hiddenWritten = 0L
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
                    val text = json.encodeToString(StudyRow.serializer(), row)
                    val hiddenOne = row.screen != null
                    if (written + text.length < maxBytes && !(hiddenOne && hiddenWritten + text.length > maxBytes * HIDDEN_SHARE)) {
                        lines.write(text)
                        lines.write("\n")
                        rows++
                        written += text.length + 1
                        if (hiddenOne) hiddenWritten += text.length + 1
                    } else {
                        cut++
                    }
                    overall.add(row)
                    (if (row.screen == null) shown else hidden).add(row)
                    if (!sb.bet.isOutlier) noOutliers.add(row)
                    for (split in BetLedger.Split.entries) splits.getValue(split).getOrPut(BetLedger.keyOf(sb.bet, split)) { Agg() }.add(row)
                    for (x in extraSplits) x.key(row, meta.zone)?.let { k -> extras.getValue(x.name).getOrPut(k) { Agg() }.add(row) }
                    if (isProp(row)) whatIfRules.forEachIndexed { i, rule ->
                        val (kept, dropped, unjudged) = whatIf[i]
                        when (rule.keeps(row)) { true -> kept; false -> dropped; null -> unjudged }.add(row)
                    }
                    if (sb.bet.startsTs < now || row.clv != null) closable++
                    if (row.clv != null) { withCloseCount++; closeVia.merge(closeSource(row.closeVia), 1, Int::plus) }
                    else if (sb.bet.startsTs < now && row.closeNote != null) {
                        // Each source's own reason, counted once per bet: a bet's note joins every source's ("Novig's file isn't out yet; ESPN keeps no line for props; no Novig outcome on
                        // record"), and cut at 90 characters the last ones, often the biggest, were lost (v0.70.1: 824 of 1,084 started bets had no Novig outcome id, never printed).
                        row.closeNote.split("; ").map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { closeReasons.merge(it.take(160), 1, Int::plus) }
                    }
                }
            }
        }

        val clock = SimpleDateFormat("MMM d, yyyy h:mm a z", Locale.US).apply { timeZone = meta.zone }
        out.appendLine("VIGILANT SCAN STUDY · version ${meta.versionName} · ${fileName(meta.versionName, now, meta.zone)}")
        out.appendLine()
        out.appendLine("== READ ME FIRST (for Claude) ==")
        readMe(meta, clock.format(Date(now)), rows, cut, firstDay, lastDay, days.size, leftOut, bytes).forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("== DATA DICTIONARY ==")
        DICTIONARY.forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("== HOW THIS DATA WAS COLLECTED, AND WHAT IT CAN'T SAY ==")
        CAVEATS.forEach { out.appendLine(it) }
        out.appendLine("Rules in force when this file was made (they decided which bets the scanners listed; they may have changed during the period): ${meta.rules}")
        out.appendLine("  Which part decides what: the app's CNO list (screen = none, src c) follows the 'CNO:' part (edge, odds, books, rows); the leading 'edge ≥', books and odds parts are the auto-bet's and Vigilant's own scan's (src v). Check atBet.preset and atBet.version: they changed during one evening.")
        out.appendLine()
        out.appendLine("== SUMMARY (added up on the phone; ROI is at the first-listed price with one unit a bet; CLV is against the close found, see closeVia; ± is a 95% interval that counts GAMES, not bets, so two groups whose intervals overlap are not shown to differ) ==")
        out.appendLine(overall.line("ALL BETS"))
        out.appendLine(shown.line("shown by the app's lists (screen = none)"))
        out.appendLine(hidden.line("hidden from the app's lists (screen set: the wide read's extra finds)"))
        out.appendLine(noOutliers.line("without outliers (EV listed over ±6%)"))
        out.appendLine("Closes found: $withCloseCount of $closable started bets (${if (closable > 0) Math.round(100.0 * withCloseCount / closable) else 0}%)" + (if (overall.n > closable) " · ${overall.n - closable} not started yet" else "") + (closeVia.takeIf { it.isNotEmpty() }?.entries?.sortedByDescending { it.value }?.joinToString(", ", " (", ")") { "${it.key} ${it.value}" } ?: ""))
        if (closeReasons.isNotEmpty()) {
            out.appendLine("Why started bets have no close yet, most common first (each source's own reason; a bet counts under every one that applies):")
            closeReasons.entries.sortedByDescending { it.value }.take(10).forEach { out.appendLine("    ×${it.value} ${it.key}") }
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
        if (whatIf.first().let { it.first.n + it.second.n + it.third.n } > 0) {
            out.appendLine()
            out.appendLine("== WHAT IF PROPS NEEDED A SHARP BOOK (props only; Tj asked 2026-10-03 whether to require one. Each rule is simulated over the logged props: the app's rules are unchanged. 'not judged' = no book page was read, so there is no sharp verdict) ==")
            whatIfRules.forEachIndexed { i, rule ->
                val (kept, dropped, unjudged) = whatIf[i]
                out.appendLine("-- ${rule.label} --")
                out.appendLine("   " + kept.line("KEPT"))
                out.appendLine("   " + dropped.line("DROPPED"))
                out.appendLine("   " + unjudged.line("NOT JUDGED"))
            }
        }
        betsAndBidsSection(out, tracked, now, meta.zone)
        bidSection(out, bids, tracked, now, meta.zone)
        out.appendLine()
        out.appendLine("== EVERY BET (JSON lines, newest first; $rows bets" + (if (cut > 0) ", $cut more left out of these lines but counted above" else "") + ") ==")
        out.appendLine("<<<JSONL")
        tmp.bufferedReader().use { it.copyTo(out) }
        out.appendLine(">>>")
        out.appendLine("== END OF FILE ==")
        tmp.delete()
        return overall.n
    }

    /**
     * The BIDS section (Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked in the scan/diagnosis feature and all information logged so I can see
     * how well my auto bids do"): Vigilant's make orders over the last 14 days, added up and split ([com.tjshea.vigilant.data.novig.trading.maker.BidReport.summary]), then every
     * FILLED bid as a JSON line ([BidReport.Row]) and the newest [MAX_UNFILLED_BID_ROWS] bids that never filled, so a reader can ask what separates the two.
     */
    private fun bidSection(out: java.io.Writer, bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>, tracked: List<TrackedBet>, now: Long, zone: TimeZone) {
        val rows = com.tjshea.vigilant.data.novig.trading.maker.BidReport.rows(bids, tracked, now)
        if (rows.isEmpty()) return
        out.appendLine()
        out.appendLine("== BIDS (Vigilant's make orders: standing offers under its fair price that takers fill; the last 14 days, ${rows.size} posted). A bid is judged by CLV and results like any bet, but ALSO by how fast it was taken: a bid taken in seconds is usually one the market was already walking away from (the fair on the next scan is under the price: evAtFill < 0, \"picked off\"). Compare evAtPost (the edge claimed) with evAtFill (the edge the next scan still saw) and clv (the close). Price band ~ the chance the side wins. ==")
        com.tjshea.vigilant.data.novig.trading.maker.BidReport.summary(rows, now).forEach { out.appendLine(it) }
        val ended = rows.filter { it.filled == 0L && it.why != null }.groupingBy { it.why!!.take(80) }.eachCount().entries.sortedByDescending { it.value }.take(8)
        if (ended.isNotEmpty()) {
            out.appendLine("-- bids that ended without a fill, by why --")
            ended.forEach { out.appendLine("    ×${it.value} ${it.key}") }
        }
        val filled = rows.filter { it.filled > 0 }.sortedByDescending { it.firstFillAtMs ?: it.postedAtMs }
        val unfilled = rows.filter { it.filled == 0L }.sortedByDescending { it.postedAtMs }.take(MAX_UNFILLED_BID_ROWS)
        out.appendLine("== EVERY FILLED BID (JSON lines, newest first; ${filled.size} bids) — fields: ${BID_FIELDS} ==")
        out.appendLine("<<<BIDJSONL")
        filled.forEach { out.appendLine(json.encodeToString(com.tjshea.vigilant.data.novig.trading.maker.BidReport.Row.serializer(), it)) }
        out.appendLine(">>>")
        out.appendLine("== THE NEWEST ${unfilled.size} BIDS THAT NEVER FILLED (JSON lines, same fields; for comparing with the filled ones) ==")
        out.appendLine("<<<UNFILLEDJSONL")
        unfilled.forEach { out.appendLine(json.encodeToString(com.tjshea.vigilant.data.novig.trading.maker.BidReport.Row.serializer(), it)) }
        out.appendLine(">>>")
    }

    /**
     * BETS AND BIDS APART (Tj, 2026-10-07): Tj's own Tracker records, the bets he took and the bids of Vigilant's that a taker filled, each with its EV, CLV and results.
     * Not the scan-listed bets above (those are every +EV bet a scan found, one unit each): these are money placed.
     */
    private fun betsAndBidsSection(out: java.io.Writer, tracked: List<TrackedBet>, now: Long, zone: TimeZone) {
        val lines = BetsAndBids.lines(tracked, now, zone.toZoneId())
        if (lines.isEmpty()) return
        out.appendLine()
        out.appendLine("== BETS AND BIDS APART (Tj's own Tracker records: BETS are taker orders he placed, BIDS are Vigilant's make orders that a taker filled. Their stakes are real; a bid's EV is the edge at its fair when it was posted. Judge them apart: a bid is exposed to being picked off, a bet is not. The BIDS section below has every bid posted, filled or not) ==")
        lines.forEach { out.appendLine(it) }
    }

    private const val MAX_UNFILLED_BID_ROWS = 300

    private const val BID_FIELDS =
        "id, auto, league, kind, market, selection, event, startsTs · as posted: postedAtMs, minToStartAtPost, price, american, contracts, fair (the one its margin is under: the lower of blend and sharpAtPost), " +
            "blend, sharpAtPost, evAtPost, margin, books, led (no bid as high on Novig's book), bestBid, offer, bookAgeSec, lifeMin, basis · ended: status, why, endedAtMs, restedMin · " +
            "fill: filled, paid, firstFillAtMs, fillDelaySec, minToStartAtFill, fairAtFill / sharpAtFill (the fair on the first scan after the fill), evAtFill, pickedOff · bet: betStatus, profit, stake, closeFair, clv, closeVia"

    // ---- the words -----------------------------------------------------------------------------------------------------

    private fun readMe(meta: Meta, madeAt: String, rows: Int, cut: Int, first: LocalDate?, last: LocalDate?, days: Int, leftOut: Int, bytes: Long): List<String> = listOf(
        "This file was made by the Vigilant app (Android, Kotlin + Compose; modules engine, data, app) on $madeAt, version ${meta.versionName} (code ${meta.versionCode}), on ${meta.device}.",
        "Vigilant finds +EV bets on the Novig sportsbook for Tj, who owns it; it is built across Claude sessions. Repo: github.com/tjshea90/novig (public), branch main.",
        "",
        "WHAT THIS IS: the scan study, a log of EVERY +EV bet Vigilant's scanners found on Novig — CrazyNinjaOdds (\"CNO\") and Vigilant's own scan — not only the ones Tj bet, and not only the ones the app showed him:",
        "besides the list the app shows (CNO read under Tj's filters), the study reads CNO a second time with its numeric filters opened right up (the WIDE read) and logs every row, flagged by why Tj's list would hide it.",
        "${rows + cut} bets from game days ${first ?: "?"} to ${last ?: "?"} ($days days, ${bytes / 1024} KB of log)" + (if (leftOut > 0) "; $leftOut older day(s) are on the phone but left out to keep this file a size Claude can read" else "") +
            (if (cut > 0) "; $cut bets (the hidden ones first) are counted in the SUMMARY but their lines were left out for the same reason." else "."),
        meta.wide?.let { "THE WIDE READ: $it" } ?: "THE WIDE READ: off, or it hasn't read yet: every bet here was in a list the app shows.",
        "Each bet was logged the moment a scan first found it, with everything the app knew then (and every column CNO printed for it, `cols`); watched while it stayed listed (every change of odds, EV or books, and when a scan dropped it); then, after its game,",
        "graded (won / lost / push) and given its closing line by the app's own grading and closing-line code (a futures bet is logged but never graded). Because it covers every bet found, it has none of the selection of Tj's own bets.",
        "THE QUESTION TJ ASKED OF THE HIDDEN ONES: the bets his filters hide (screen is set) are the ones he never sees. Compare them with the shown ones (SUMMARY and the splits): do any hidden kinds beat the close or profit, and do his filters (EV floor, odds cap, book count, one-sided, complete book, row limit) cost him edge or save him from traps?",
        "",
        "THE GOAL IS PROFIT: find which bets, bought when, beat the closing line (CLV) and make money. CLV is the leading indicator (it needs far fewer bets than results do); results confirm it slowly.",
        "Tj bets on Novig only, as a taker at the listed price (pregame Novig takers pay no fee) and, with the Bids tab, as a maker posting bids under its fair price.",
        "BETS vs BIDS: a BET is a taker order (Tj's tap, the Bet sheet, the auto-bet); a BID is a make order Vigilant posted under its fair price that a taker filled. They are different kinds of record with different risks, so read them apart: BETS AND BIDS APART (after the splits) gives each its EV, CLV and results from Tj's Tracker, the BIDS section has every bid posted (filled or not), and a scan-listed bet Tj holds as a bid is marked placedAs = \"bid\" (placedByTj is true only for his own bets).",
        "BIDS: when Vigilant has posted bids (make orders) there is a BIDS section after the splits with every bid added up and split, then every filled bid and the newest unfilled ones as JSON lines. Judge bids by CLV, by how fast they were taken, and by whether the fair on the next scan was still above the price they filled at (evAtFill): a fast fill is a symptom of a stale bid, not a success. Every bid names the choice that posted it (focus: ALL, QUICK_LIKELY or LOW_USAGE), the books its fair was made from (fairBooks) and how old their prices were when it was posted (fairAgeSec, fairNewestAgeSec); LOW_USAGE bids (props only, 2-3 sharp prop books, at least 2.5% under the fair, no longer than +130, a slow scan pace) are split out by books and by the age of the prices, so judge them apart from the other bids.",
        "PINNACLE ONLY: when Tj switches it on (Settings › Scanning), Vigilant's scan prices every Novig bet against Pinnacle's devigged two-sided price ALONE (no other book, the lowest of four devigs) and the auto-bet bets what beats it on a Pinnacle price read within seconds of the order.",
        "Those bets carry atBet.pinnacleOnly = true, atBet.pinnacleAgeSec (how old Pinnacle's price was) and Pinnacle's own two-sided price in atBet.books; two splits (\"Pinnacle only\") separate them. Judge them apart from every other bet: their EV, CLV and profit are against Pinnacle alone.",
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
        " 7. TJ'S OPEN QUESTION (2026-10-03): should a prop bet need a sharp prop book to agree it is +EV before the app lists or bets it? Today a prop's EV is CNO's consensus of 4+ books (mostly soft) and the sharp veto only skips it when a sharp-ranked book that prices both sides says no.",
        "    Answer it from the PROPS splits (what the sharp-ranked book said, its own edge, whether the exchanges are on the page) and the WHAT IF lines: for each rule, the props kept, dropped and not judged, with CLV (by closeVia), ROI and counts of games not bets, split by date. Say whether requiring one",
        "    raises CLV enough to pay for the bets it drops, which book's agreement matters (exchanges or the originating books), whether a higher edge for props with no sharp book is the better rule, and how many bets a day each choice costs. Only judged props (a book page was read: the top of each CNO scan) have a verdict: say how that selection could bias it.",
        " 8. Deliver: a ranked list of the strategies worth trying with the evidence, the exact app settings to change (presets in data/scanner/Presets.kt, the sharp veto in data/scanner/SharpVeto.kt, the trap guard, the auto-bet's rules, CNO's filters), and what",
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
        "src: who found it (c = the app's CNO list, i.e. CNO read under Tj's filters; w = the WIDE read, every row CNO finds with its filters opened, including the ones the app's list hides; v = Vigilant's scan; a bet found by several is one record: c+w, w, c+v+w …).",
        "screen: why the app's CNO list would not have shown it at its first look (null = it showed it): NOT_A_GAME (futures), MISMATCH (EV doesn't follow from its fair odds), ONE_WAY, BOOKS (under Tj's book count), ODDS (longer than his odds cap), SHORT_ODDS (shorter than his limit), TOO_GOOD (EV over 20%), EV (under his minimum),",
        "    and, only when Tj has picked which games the list looks at (CnoScope, v0.73.0; the header's rules line says what he picked): LEAGUE (a league he left out), KIND (a kind of bet he left out), LIVE (a game already under way, with hide live on), LIQUIDITY (fewer dollars available than his minimum), TEXT (not matching his words), PROPS (over his props-per-game cap: a group rule, so the single-row check cannot see it and it is logged NOT_LISTED),",
        "    NOT_LISTED (the app's screen passes it but CNO's read under his filters didn't carry it: its complete-book or both-sides rule, the row limit, or a filter in his Shared View link). Several reasons can apply; this is the first the app's screen found.",
        "cols: every column CNO printed for the row at its first wide look, by CNO's own header (EV%, Date, Sport, League, Event, Market, Bet Name, Odds with the dollars, Sportsbook, Fair Odds, Books, Extra …), plus the row's data-* attributes with an @. Text as CNO showed it; a column CNO adds appears without the app knowing it.",
        "american · cost · ev · fair: at the FIRST look — the Novig price (American; CNO's, or Novig's own live price when it had been read in the last minute), what a $1 payout costs at it (= implied probability; pregame Novig has no fee), the EV the list showed, and its fair probability (CNO's, or Vigilant's).",
        "status · profit · gradeNote: PENDING, WON, LOST, PUSH, VOID or FMV; profit is units at the first-listed price with a one-unit stake (null while open). gradeNote is the reason a result is missing.",
        "closeFair · closeAmerican · closeVia · closeNote: the closing line — the side's devigged fair probability at the start (and its odds), its source, and why none was found. Sources: \"ParlayAPI · Pinnacle close\" (the sharpest), \"ESPN\" (DraftKings / ESPN BET game lines only), \"Novig's last trades\" (the last half hour before the start), or \"Tracker · …\" (read before the start for a bet Tj also placed).",
        "clv · clvBest · clvLast: closeFair / cost − 1 at the first-listed price, at the best (longest) odds it was listed at, and at the last listed price. bestAmerican · lastAmerican: those prices. novigClose: Novig's own last price before the start when the Tracker read it.",
        "listedMin · lastListedMinToStart · gone: for the app's own lists (c and v; null for a bet only the wide read found): minutes from the first look to the last, how many minutes before the start the last look was, and whether a scan then dropped it (its edge fell under the filters, or CNO's row limit pushed it out).",
        "looks · placedByTj · placedAs · placedAmerican: number of looks; whether Tj also placed this bet himself as a taker (his own Tracker's), how the Tracker holds the line (placedAs: \"bet\" = a taker order, \"bid\" = Vigilant's make order that filled; placedByTj is false for a bid) and at what price. marketId · outcomeId: Novig's public ids when known.",
        "PROPS splits and the WHAT IF section use atBet.sharpVerdict (PASSED: a sharp-ranked book that prices both sides gives Novig's price the veto's bar or more; VETOED: it gives less; NO_SHARP: none of them prices both sides), atBet.sharpBook (Kalshi, ProphetX, FanDuel, Caesars, DraftKings … the first ranked book that did) and atBet.sharpEv (its own edge at Novig's price): all from the first book page read for the bet.",
        "kind · sport · minToStartFirst · cnoBooks · booksTwoSided · booksAgreeing · agreeShare · available · sharpVerdict: the most used fields of atBet at the top level. cnoBooks = books behind CNO's fair price (on every row); booksTwoSided = companies whose page prices both sides, booksAgreeing = those whose own fair says +EV at Novig's price, agreeShare = booksAgreeing / booksTwoSided (null when the book check wasn't made: only the top few bets get a page read).",
        "atBet: the app's record of the bet as first listed (data/.../tracker/AtBet.kt): league, sport, kind (PROP, MONEYLINE, SPREAD, TOTAL, TEAM_TOTAL, PERIOD, OTHER), minutesToStart, american, otherAmerican (the other side's price), available (Novig dollars at the price), ev, fair, cnoBooks (books behind CNO's fair), cnoOneWay, cnoListAgeSec,",
        "    and the app's own BOOK CHECK from CNO's game page when it was read (checkAtMs says when; a page is read for the top few bets, so many bets have none): twoSided (companies pricing both sides), oneSided, agreeing (those whose own fair says +EV at Novig's price), verdict (CONFIRMED / NOT_CONFIRMED …), checkFair, checkEv, books (every book's odds, other side, fair and the EV it gives Novig's price), dissent (books saying not +EV),",
        "    sharpVerdict / sharpBook / sharpEv (the sharpest book for the kind of bet — Pinnacle and Circa for game lines, Kalshi and ProphetX for props — and whether it VETOED the price), fullKelly (the Kelly share of bankroll this edge calls for), preset (the preset in force).",
        "vig / cno: the OTHER scanner's record when both listed the bet (atBet is the first lister's): vig = Vigilant's (its fair line's method, the books and sharp books behind it, how old their quotes were); cno = CNO's (its books, one-way flag, list age).",
        "s: every look, oldest first, each [minutesBeforeStart, kind, american, ev, fair, books, dollars, agreeing, companiesBothSides, checkEv, sharpVerdict]. kind: c = the app's CNO list carried it, w = the wide read found it (it finds the c rows too: both are logged), v = a Vigilant scan listed it, k = its book page was read (agreeing / companies / checkEv / sharp verdict are filled), xc / xw / xv = that read no longer lists it.",
        "    A look is logged when the bet is first seen, when odds / books change or EV moves 0.25 points (at most one a minute), at least every 5 minutes while it's listed, when it drops off, and for each book check that changed.",
    )

    private val CAVEATS: List<String> = listOf(
        "- SELECTION: the app's list is CNO read under Tj's filters (devig method, longest odds, fewest books, smallest EV, row limit, complete book, both sides — see the rules line below); the WIDE read opens all of those but his devig method, his book, and what his Shared View link scopes (sports, leagues, main lines, live, and any filter the app doesn't know: THE WIDE READ line says which fields were posted). So a bet the filters hid IS here, flagged by `screen`; one outside the link's scope, or past the wide read's row limit, is not.",
        "- The wide read runs at most every 30 seconds, and only while the app's CNO list is being read: a bet that appeared and vanished between two reads is missed. A bet \"dropped\" (gone) from the app's list may have lost its edge OR been pushed out by the row limit; look at its `w` looks to tell which (still there in the wide read = pushed out or filtered).",
        "- PROPS AND THE SHARP BOOK: a verdict exists only for bets whose CNO game page was read (the green check reads the best ~10 of the list, again every 4 minutes) and the bet's first read is the one used. Those are the app's best-EV rows, not a random sample, and hidden/wide-only props have none: compare inside the judged set, and say what the not-judged bets would change.",
        "- HIDDEN BETS: many are hidden for good reason (one-way devigs, one or two books, a price over the cap, futures, a stale line over 20% EV). The hidden group's ROI and CLV are real numbers like any other, but its CLV is mostly against the same closes: do not read a hidden kind as a strategy until it holds on both halves of the period.",
        "- FIRST-LISTED PRICE: ROI and clv assume the bet could be taken at the price at the first look, for a unit. Novig had only `available` dollars at that price, and many bets were listed for minutes: check listedMin and available before believing a rule's size.",
        "- CLOSES: the close sources differ (Pinnacle is sharper than ESPN's DraftKings line; Novig's last trades can lag). Compare CLV by close source before pooling (the split \"Close source\" does it; a Novig-trades close of 1-2 trades is a price, not a close: its split says so). Bets with no close are left out of every CLV figure; their reasons are in the summary.",
        "- RESULTS are noisy: at +3% EV a bet wins its edge once in thousands of bets. Trust CLV first; use ROI only to confirm over hundreds of games. Same-game and same-player bets are correlated: the effective sample is games, not rows.",
        "- Every bet is one unit at the first-listed price whatever Tj staked; the bets he placed himself are marked placedByTj. His own placed bets are a separate record (the diagnostics file's EVERY BET).",
        "- Overlap: one bet listed by both scanners is one row. Different books' sister sites are counted once in the book check (one company, one opinion).",
        "- The sample is one phone over the dates above, while the app and its rules were changing (the version and preset are in atBet). Say 'the data suggests', and ask Tj when a change needs a decision.",
    )

    /** The most bytes of bets' lines in one file (the share sheet and Claude's reading both have limits); every bet is still in the summary. */
    const val MAX_BYTES = 24L * 1024 * 1024

    /** Days are taken, newest first, while their journals total under this many times [MAX_BYTES] (a journal holds more than its lines in the file). */
    private const val DAYS_FACTOR = 3L

    /** The share of the lines' budget the bets the app's lists hid may take. */
    private const val HIDDEN_SHARE = 0.65

    private const val MAX_SPLIT_GROUPS = 25

    /** A ± needs this many games behind it: fewer say nothing ([Agg.halfWidth]). */
    private const val MIN_GAMES = 5
}
