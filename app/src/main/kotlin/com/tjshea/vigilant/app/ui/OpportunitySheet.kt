package com.tjshea.vigilant.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunitySheet(o: Opportunity, settings: ScanSettings, onDismiss: () -> Unit, onTrack: (Double) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        OpportunityDetail(o, settings, onTrack)
    }
}

@Composable
fun OpportunityDetail(o: Opportunity, settings: ScanSettings, onTrack: (Double) -> Unit) {
    val context = LocalContext.current
    val q = o.quote
    var stakeText by remember(o.key) { mutableStateOf(o.suggestedStake?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.selection, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${o.marketLabel} · ${o.league.displayName} · ${Format.startTime(o.event.startsTs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(o.eventName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            q?.let { EvBadge(it.evPercent, large = true) }
        }

        SectionTitle("Price")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LabeledValue("Novig", q?.let { "${Format.american(it.cost)} · ${Format.percent(it.cost)}" } ?: "no offers")
            LabeledValue("Fair", o.fairProbability?.let { "${Format.american(it)} · ${Format.percent(it)}" } ?: "—")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LabeledValue("Taker fee", q?.let { if (it.fee == 0.0) "none (pregame)" else Format.percent(it.fee, 2) } ?: "—")
            LabeledValue("Full Kelly", q?.let { Format.percent(it.kellyFraction) } ?: "—")
            LabeledValue("Novig width", o.novigWidth?.let { Format.percent(it) } ?: "—")
        }

        if (o.ladder.isNotEmpty()) {
            SectionTitle("Novig order book (you take)")
            o.ladder.take(6).forEach { level ->
                val ev = o.fairProbability?.let { EvMath.quote(it, level.price, o.market.fee!!, o.isLive).evPercent }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.american(level.price), Modifier.width(72.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text(Format.percent(level.price), Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${Format.contractsAsPayout(level.contracts)} payout",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ev?.let { Text(Format.evPercent(it), color = if (it >= 0) Edge.colors.positive else Edge.colors.negative, style = MaterialTheme.typography.labelMedium) }
                }
            }
            o.depth?.takeIf { it.contracts > 0 }?.let { d ->
                Text(
                    "${Format.money(d.dollarCost)} can be bet at +EV (expected profit ${Format.money(d.dollarEv)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Edge.colors.positive,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        o.fair?.let { fair ->
            SectionTitle("Fair odds: ${fair.sourceUsed.displayName} · ${fair.method.displayName}")
            if (fair.sourceUsed != fair.requestedSource) {
                Text(
                    "No sharp book quoted this line, so the market average is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Edge.colors.warning,
                )
            }
            val key = o.lineKey
            val idx = key?.let { k -> sideIndex(o, k.sides) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Book", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Odds", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No-vig", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hold", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            fair.perBook.sortedByDescending { it.isSharp }.forEach { b ->
                val used = b.book.bookTitle in fair.booksUsed
                val color = if (used) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.book.bookTitle + if (b.isSharp) "  ★" else "",
                        Modifier.weight(1f),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        idx?.let { Odds.formatAmerican(Odds.decimalToAmerican(b.book.decimalOdds[it])) } ?: "—",
                        Modifier.width(64.dp),
                        fontFamily = FontFamily.Monospace,
                        color = color,
                    )
                    Text(idx?.let { Format.percent(b.fairProbabilities[it]) } ?: "—", Modifier.width(64.dp), color = color, style = MaterialTheme.typography.bodySmall)
                    Text(Format.percent(b.hold), Modifier.width(52.dp), color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "★ sharp book · greyed rows weren't used for this line's fair price",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (q != null && o.fairProbability != null) {
            SectionTitle("Track this bet")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { stakeText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    label = { Text("Stake $") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { stakeText.toDoubleOrNull()?.takeIf { it > 0 }?.let(onTrack) }, enabled = (stakeText.toDoubleOrNull() ?: 0.0) > 0) {
                    Text("Track")
                }
            }
            o.suggestedStake?.let {
                Text(
                    "Suggested: ${Format.money(it)} (${Format.kellyLabel(settings.kellyMultiplier)} of a ${Format.money(settings.bankroll)} bankroll, capped at +EV liquidity).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://novig.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
        ) { Text("Open Novig") }
    }
}

/** Which column of the reference line this outcome is (its own side, or "Yes"'s side). */
private fun sideIndex(o: Opportunity, sides: List<Side>): Int? {
    val key = o.lineKey ?: return null
    val target = com.tjshea.vigilant.data.scanner.Planner.run { null } ?: run {
        // Recover this outcome's target from the fair probability: compare against each side.
        null
    }
    // Pricing stores the target implicitly; re-derive it by matching probabilities.
    val fair = o.fair ?: return null
    val p = o.fairProbability ?: return null
    val direct = fair.probabilities.indexOfFirst { kotlin.math.abs(it - p) < 1e-12 }
    return direct.takeIf { it >= 0 && it < key.sides.size }.also { target }
}
