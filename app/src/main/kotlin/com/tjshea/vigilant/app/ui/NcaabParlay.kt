package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.ncaab.NcaabText
import com.tjshea.vigilant.data.novig.NovigParlay
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.launch

/** The legs of the parlay being built on the college basketball tab (NC1). Novig prices a parlay for anyone and fills it only in its own app, so the app prices it and opens the slip there. */
@Stable
class ParlayState {
    val legs = mutableStateListOf<Opportunity>()
    fun has(o: Opportunity) = legs.any { it.outcome.outcomeId == o.outcome.outcomeId }
    fun toggle(o: Opportunity) {
        val i = legs.indexOfFirst { it.outcome.outcomeId == o.outcome.outcomeId }
        if (i >= 0) legs.removeAt(i) else if (legs.size < NovigParlay.MAX_LEGS) legs.add(o)
    }
}

val LocalParlay = staticCompositionLocalOf<ParlayState?> { null }

/** Asks Novig for a parlay's price (a keyless call): the view model's [com.tjshea.vigilant.app.MainViewModel.parlayQuote]. */
val LocalParlayQuote = staticCompositionLocalOf<(suspend (List<String>) -> NovigParlay.Result)?> { null }

internal fun legOf(o: Opportunity) = NovigParlay.Leg(
    o.outcome.outcomeId, o.event.eventId, o.kind, listOfNotNull(o.event.matchup?.home, o.event.matchup?.away),
)

/** The little link under a side that adds it to, or takes it out of, the parlay. */
@Composable
internal fun ParlayToggle(o: Opportunity) {
    val p = LocalParlay.current ?: return
    val on = p.has(o)
    Text(
        if (on) "✓ in the parlay (tap to remove)" else "＋ add to parlay",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { p.toggle(o) }.padding(vertical = 2.dp).testTag("ncaabParlayToggle"),
    )
}

@Composable
internal fun ParlayBar(modifier: Modifier = Modifier) {
    val p = LocalParlay.current ?: return
    if (p.legs.isEmpty()) return
    val quote = LocalParlayQuote.current
    val openNovig = LocalOpenNovig.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<NovigParlay.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    val ids = p.legs.map { it.outcome.outcomeId }
    // Other legs, another price: an old answer is never shown for a different parlay.
    LaunchedEffect(ids) { result = null }
    val problem = NovigParlay.problem(p.legs.map(::legOf))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = modifier.fillMaxWidth().testTag("ncaabParlayBar")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Parlay · ${p.legs.size} leg${if (p.legs.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            p.legs.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${o.selection} · ${o.event.description}", style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                    TextButton(onClick = { p.toggle(o) }) { Text("✕") }
                }
            }
            when {
                problem != null -> Text(problem, style = MaterialTheme.typography.labelMedium, color = LocalEdgeColors.current.warning, modifier = Modifier.testTag("ncaabParlayProblem"))
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && quote != null, onClick = { scope.launch { busy = true; result = quote?.invoke(ids); busy = false } }, modifier = Modifier.testTag("ncaabParlayPrice")) { Text(if (busy) "Asking Novig…" else "Price it at Novig") }
                        (result as? NovigParlay.Result.Priced)?.let {
                            OutlinedButton(onClick = { openNovig?.invoke(NovigParlay.deepLink(ids)) }, enabled = openNovig != null, modifier = Modifier.testTag("ncaabParlayOpen")) { Text("Open in Novig") }
                        }
                        TextButton(onClick = { p.legs.clear() }) { Text("Clear") }
                    }
                    result?.let { Text(describe(it, p.legs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ncaabParlayResult")) }
                    Text("Novig prices a parlay for anyone but fills it only in its own app: Open in Novig puts every leg on the slip and you place it there.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Novig's answer in words, set beside the legs taken one at a time and, when every leg has a book price, beside the books. */
internal fun describe(r: NovigParlay.Result, legs: List<Opportunity>): String = when (r) {
    is NovigParlay.Result.Priced -> {
        val q = r.quote
        val takes = legs.mapNotNull { it.ladder.firstOrNull()?.price }
        val fairs = legs.mapNotNull { it.fairProbability }
        buildString {
            append("Novig: ${NcaabText.cents(q.price)} per $1 — pays ${String.format(java.util.Locale.US, "%.2f", q.decimalOdds)}× (${Odds.formatAmerican(Odds.probabilityToAmerican(q.price.coerceIn(0.001, 0.999)))}), good for about ${q.lifetimeMs / 1000} s.")
            if (takes.size == legs.size) append(" The legs taken one by one would stack to ${NcaabText.cents(NovigParlay.stackedCost(takes))}.")
            if (fairs.size == legs.size) {
                val fair = fairs.fold(1.0) { a, b -> a * b }
                append(" Books' fair for all legs together ${NcaabText.pct(fair)}: ${Format.evPercentShort(fair / q.price - 1)} at Novig's price (legs treated as independent).")
            } else append(" No book price for ${legs.size - fairs.size} of ${legs.size} legs, so no EV against the books.")
        }
    }
    is NovigParlay.Result.Refused -> "Novig won't price this parlay: ${r.why}"
    NovigParlay.Result.NoPrice -> "Novig has no price for this parlay right now. Try again in a moment."
    is NovigParlay.Result.Limited -> "Novig asked for a pause${r.retryAfterS?.let { " (${it}s)" } ?: ""}. Try again shortly."
    is NovigParlay.Result.Failed -> "Couldn't reach Novig: ${r.why}"
}
