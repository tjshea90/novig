package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.scanner.ScanResult

/**
 * Where the Kalshi scan loses lines, in counts (Tj, 2026-10-21, CH55: "I'm not seeing a lot on kalshi ... are there matching problems or simply not much opportunity"): per league the Kalshi
 * games it read, how many matched a fair-odds game, how many lines had a fair price, how many were +EV after Kalshi's fee. [verdict] names the biggest loss in words. Pure; reads a finished scan.
 */
object KalshiFunnel {
    data class Row(val league: String, val games: Int, val matched: Int, val lines: Int, val withFair: Int, val positiveEv: Int)

    data class Funnel(val rows: List<Row>, val listed: Int) {
        val games get() = rows.sumOf { it.games }
        val matched get() = rows.sumOf { it.matched }
        val lines get() = rows.sumOf { it.lines }
        val withFair get() = rows.sumOf { it.withFair }
        val positiveEv get() = rows.sumOf { it.positiveEv }
    }

    fun of(result: ScanResult, listed: Int): Funnel = Funnel(
        result.games.groupBy { it.league.displayName }.map { (name, gs) ->
            val outs = gs.flatMap { it.outcomes }
            Row(name, gs.size, gs.count { it.refEvent != null }, outs.size, outs.count { it.fair != null }, outs.count { (it.evPercent ?: -1.0) > 0.0 })
        }.sortedByDescending { it.games },
        listed,
    )

    /** One line each for the Kalshi tab and Diagnostics. */
    fun lines(f: Funnel): List<String> = buildList {
        add("Kalshi games read ${f.games} → matched to a fair-odds game ${f.matched} → lines priced ${f.lines} → with a fair price ${f.withFair} → +EV after Kalshi's fee ${f.positiveEv} → listed ${f.listed}")
        f.rows.forEach { add("${it.league}: ${it.games} games, ${it.matched} matched, ${it.lines} lines, ${it.withFair} with fair, ${it.positiveEv} +EV") }
        add(verdict(f))
    }

    fun verdict(f: Funnel): String = when {
        f.games == 0 -> "Kalshi returned no games for the leagues picked: nothing to match (check the leagues and the Kalshi key/socket)."
        f.matched * 2 < f.games -> "Matching is the loss: only ${f.matched} of ${f.games} Kalshi games found a fair-odds game (same teams and start time)."
        f.lines > 0 && f.withFair * 2 < f.lines -> "Fair prices are the loss: ${f.withFair} of ${f.lines} lines had one (few books carry those markets)."
        f.positiveEv == 0 -> "Matched and priced, but none is +EV after Kalshi's fee: not much opportunity right now."
        f.listed < f.positiveEv -> "${f.positiveEv} are +EV but ${f.listed} are listed: the list's filters (edge, stars, books agreeing) are holding the rest."
        else -> "Matching and pricing look healthy; ${f.positiveEv} +EV lines."
    }
}
