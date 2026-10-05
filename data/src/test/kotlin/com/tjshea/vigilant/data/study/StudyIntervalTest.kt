package com.tjshea.vigilant.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-05 ("Review the attached diagnostics and scan study … Am I beating the clv?"): the summary's ROI and CLV come with a ± that counts GAMES, not bets
 * (RESEARCH.md §82), so +6.95% against −0.70% on two days of NFL and NCAAF reads as the noise it is. Bets of one game win and lose together (§81.2).
 */
class StudyIntervalTest {

    private fun row(id: String, game: String, profit: Double?, clv: Double? = null) = StudyExport.StudyRow(
        id = id, firstSeenMs = 0, startsAtMs = 1_000, league = "NFL", event = game, market = "Moneyline", selection = "x", src = "c", cost = 0.5,
        status = when { profit == null -> "PENDING"; profit > 0 -> "WON"; else -> "LOST" }, profit = profit, clv = clv, looks = 1,
    )

    private fun line(rows: List<StudyExport.StudyRow>) = StudyExport.Agg().apply { rows.forEach(::add) }.line("X")

    @Test
    fun `the interval counts games, so two bets of one game are not two independent bets`() {
        // 5 games that each win both their bets and 5 that lose both: ROI 0 on 20 bets in 10 games.
        val together = (1..10).flatMap { g -> (1..2).map { b -> row("$g-$b", "G$g", if (g <= 5) 1.0 else -1.0) } }
        // The same 10 wins and 10 losses spread over 20 games.
        val apart = (1..20).map { g -> row("$g", "G$g", if (g <= 10) 1.0 else -1.0) }
        // Ratio estimator by game: 1.96 · sqrt(G/(G−1) · Σ(profit_g − ROI · staked_g)² / staked²).
        assertTrue(line(together), line(together).contains("ROI +0.00% (+0.0u on 20u, ±65.3 points over 10 games)"))
        assertTrue(line(apart), line(apart).contains("ROI +0.00% (+0.0u on 20u, ±45.0 points over 20 games)"))
    }

    @Test
    fun `CLV carries its own interval over the games that have a close`() {
        // 10 games, one close each, alternating +1% and +3%: mean +2%, ±1.96 · sqrt(10/9 · 10 · 0.01² / 10²) = ±0.65 points.
        val rows = (1..10).map { g -> row("$g", "G$g", 1.0, if (g % 2 == 0) 0.03 else 0.01) }
        assertTrue(line(rows), line(rows).contains("CLV +2.00% ±0.65 over 10 games on 10 closes"))
    }

    @Test
    fun `under five games there is no interval to print, and an open or unclosed bet adds none`() {
        val few = (1..4).map { g -> row("$g", "G$g", if (g % 2 == 0) 1.0 else -1.0, 0.02) }
        assertFalse(line(few), line(few).contains("±"))
        // Open bets (no profit) and bets with no close don't make a game count: only 3 games here have a result.
        val rows = (1..3).map { g -> row("$g", "G$g", 1.0) } + (4..10).map { g -> row("$g", "G$g", null) }
        assertFalse(line(rows), line(rows).contains("±"))
        assertTrue(line(rows), line(rows).contains("no close yet"))
    }
}
