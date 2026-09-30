package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.reference.ParlayVerdicts
import com.tjshea.vigilant.data.reference.Verdict
import com.tjshea.vigilant.data.reference.VerdictQuery
import com.tjshea.vigilant.engine.Odds

/**
 * A bet's "Second opinion" from ParlayAPI (Tj, 2026-09-30, PARLAY_API.md §6.4), kept by the bet's key while the app runs: being asked,
 * answered ([verdict], for [query]'s price), or why not ([error]).
 */
data class OpinionUi(
    val query: VerdictQuery,
    val asking: Boolean = false,
    val verdict: Verdict? = null,
    val error: String? = null,
    val atMs: Long? = null,
) {
    companion object {
        fun of(query: VerdictQuery, result: ParlayVerdicts.Result, now: Long): OpinionUi = when (result) {
            is ParlayVerdicts.Result.Answered -> OpinionUi(query, verdict = result.verdict, atMs = now)
            ParlayVerdicts.Result.Busy -> OpinionUi(query, error = "ParlayAPI's props board is busy: try again in a minute.", atMs = now)
            ParlayVerdicts.Result.Off -> OpinionUi(query, error = "Turn ParlayAPI on with a key in Settings to ask it.", atMs = now)
            is ParlayVerdicts.Result.Failed -> OpinionUi(query, error = result.message, atMs = now)
        }
    }
}

/** The test tag of a second opinion's answer. */
const val SECOND_OPINION = "secondOpinion"

/** The button's words: what it asks and what it costs. */
fun opinionButtonText(asked: Boolean): String = if (asked) "Ask ParlayAPI again (${ParlayVerdicts.COST} credits)" else "Second opinion (ParlayAPI, ${ParlayVerdicts.COST} credits)"

@Composable
private fun verdictColor(v: String): Color = when (v) {
    "BET", "LEAN" -> Edge.colors.positive
    "PASS" -> Edge.colors.negative
    "NO_DATA" -> Edge.colors.warning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun american(v: Int): String = Odds.formatAmerican(v)

/**
 * ParlayAPI's call on a bet, only when tapped (5 credits each: never automatic). Its verdict and fair price, and Vigilant's own EV at the
 * bet's price from that fair price (ParlayAPI's `edge_pct` is a probability-point gap, not EV: PARLAY_API.md §5). [onAsk] null: not
 * offered (ParlayAPI off or no key, or a bet /verdict can't grade).
 */
@Composable
fun SecondOpinion(opinion: OpinionUi?, onAsk: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onAsk == null && opinion == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opinion?.verdict?.let { VerdictCard(it, opinion.query) }
        opinion?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning) }
        if (onAsk != null) {
            OutlinedButton(onClick = onAsk, enabled = opinion?.asking != true, modifier = Modifier.fillMaxWidth()) {
                if (opinion?.asking == true) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Asking ParlayAPI…")
                } else {
                    Text(opinionButtonText(opinion?.verdict != null))
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(v: Verdict, q: VerdictQuery) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag(SECOND_OPINION)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ParlayAPI says ", style = MaterialTheme.typography.titleSmall)
                Text(v.verdict.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = verdictColor(v.verdict))
            }
            v.fairAmerican?.let { f ->
                Text(
                    "Fair ${american(f)}" + (v.fairProbability?.let { " (${Format.percent(it)})" } ?: "") + (v.fairSource?.let { " from ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Vigilant's own EV at the price shown, from their fair line.
            v.evAt(1.0 / Odds.americanToDecimal(q.price))?.let { ev ->
                Text(
                    "EV at ${american(q.price)}: ${Format.evPercent(ev)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ev >= 0) Edge.colors.positive else Edge.colors.negative,
                )
            }
            listOfNotNull(
                v.bestAmerican?.let { "best ${american(it)}" + (v.bestBook?.let { b -> " at ${b.replaceFirstChar { c -> c.uppercase() }}" } ?: "") },
                v.booksCompared?.let { "$it books compared" },
                v.confidence?.let { "confidence $it" },
                v.movementPp?.let { String.format(java.util.Locale.US, "%+.1f pts since open", it) },
            ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            v.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
