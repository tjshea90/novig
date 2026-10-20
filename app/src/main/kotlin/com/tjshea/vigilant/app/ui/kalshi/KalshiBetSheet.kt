package com.tjshea.vigilant.app.ui.kalshi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.kalshi.KalshiHub
import com.tjshea.vigilant.app.kalshi.KalshiUi
import com.tjshea.vigilant.app.ui.AgreementLine
import com.tjshea.vigilant.app.ui.Banner
import com.tjshea.vigilant.app.ui.EvBadge
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.LabeledValue
import com.tjshea.vigilant.app.ui.SectionTitle
import com.tjshea.vigilant.app.ui.StarRating
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** The Bet sheet for a Kalshi bet: the numbers at Kalshi's price now (re-read on every amount typed), and one button that orders it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalshiBetSheet(hub: KalshiHub, ui: KalshiUi, o: Opportunity, now: Long, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val q = o.quote
    var stakeText by remember(o.key) { mutableStateOf(String.format(Locale.US, "%.2f", (o.suggestedStake ?: ui.config.scan.slipCustomStake).coerceAtMost(ui.config.scan.apiMaxStake))) }
    val stake = stakeText.toDoubleOrNull() ?: 0.0
    var plan by remember(o.key) { mutableStateOf<PlanResult?>(null) }
    var busy by remember(o.key) { mutableStateOf(false) }
    var outcome by remember(o.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(o.key, stake) {
        plan = null
        if (stake <= 0.0) return@LaunchedEffect
        delay(350)
        plan = hub.plan(o, stake)
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.selection, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    StarRating(o.rating, large = true)
                    Text("${o.marketLabel} · ${o.league.displayName} · ${Format.startTime(o.event.startsTs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(o.eventName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                q?.takeIf { !o.fairIsOld(now) }?.let { EvBadge(it.evPercent, large = true) }
            }
            AgreementLine(o.agreement, Modifier.padding(top = 8.dp))
            if (o.fairIsOld(now)) Banner("The other books' prices behind this are old: scan again before betting.")
            SectionTitle("Price at Kalshi")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Kalshi", q?.let { "${Format.american(it.cost)} · ${Format.percent(it.cost)}" } ?: "no offers")
                LabeledValue("Fair", o.fairProbability?.let { "${Format.american(it)} · ${Format.percent(it)}" } ?: "—")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Fee", q?.let { Format.percent(it.fee, 2) + " of \$1" } ?: "—")
                LabeledValue("Full Kelly", q?.let { Format.percent(it.kellyFraction) } ?: "—")
                LabeledValue("Offered", o.ladder.firstOrNull()?.let { "${it.contracts / 100} contracts" } ?: "—")
            }
            SectionTitle("Bet")
            OutlinedTextField(
                value = stakeText, onValueChange = { stakeText = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Dollars to spend") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("kalshiStake"),
            )
            when (val p = plan) {
                null -> if (stake > 0.0) Text("Reading Kalshi's price…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                is PlanResult.Refused -> Text(p.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("kalshiRefused"))
                is PlanResult.Ready -> {
                    val b = p.plan
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${b.contracts / 100} contracts at ${Format.percent(b.limitPrice)} (${Format.american(b.limitPrice)}): costs ${Format.money(b.expectedCost)} with the ${Format.money(b.feeDollars)} fee.", style = MaterialTheme.typography.bodyMedium)
                        Text("Wins ${Format.money(b.payout)} (profit ${Format.money(b.profitIfWon)}) · ${Format.evPercent(b.evPercent)} EV after the fee", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        b.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            outcome?.let { Text(it, modifier = Modifier.padding(top = 10.dp).testTag("kalshiOutcome"), fontWeight = FontWeight.SemiBold) }
            if (!ui.config.tradingEnabled) {
                Text("Orders are off: turn on \"Place orders on Kalshi\" in the Setup page to use this button.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            }
            val ready = plan as? PlanResult.Ready
            Button(
                onClick = {
                    val r = ready ?: return@Button
                    hub.placeAsync(o, stake, r.plan.limitPrice)
                    onDismiss()
                },
                enabled = ready != null && !busy && ui.config.tradingEnabled && outcome?.startsWith("Placed") != true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).testTag("kalshiPlace"),
            ) { Text(if (busy) "Placing…" else "Bet ${Format.money(stake)} on Kalshi") }
        }
    }
}
