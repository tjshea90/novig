package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.rfq.ParlayView
import com.tjshea.vigilant.data.rfq.RfqConfig
import com.tjshea.vigilant.data.rfq.RfqStatus
import java.util.Locale

/**
 * The Engine tab's Parlays view (MKV, Tj 2026-11-18: "I want it in vigilant"): the Novig parlay maker that runs on the GitHub engine, as a monitor (what it has seen, what the market pays, what it would
 * earn, in LIVE its quotes and collateral) and a control (OFF / SHADOW / LIVE and the caps, written to `rfq-maker.json` on `cloud-config`, which the engine reads within seconds). LIVE asks for a second
 * tap with what is at stake in words. The phone does none of the work.
 */
@Composable
fun ParlayMakerView(rfq: RfqStatus?, onSet: (RfqConfig, (String?) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val v = remember(rfq) { ParlayView.of(rfq) }
    val cfg = rfq?.config ?: RfqConfig()
    var note by remember { mutableStateOf<String?>(null) }
    var confirmLive by remember { mutableStateOf(false) }
    fun apply(c: RfqConfig) { note = "Saving…"; onSet(c) { why -> note = if (why == null) "Saved: the engine applies it within seconds." else "Not saved: $why" } }

    Column(modifier.testTag("parlayMaker"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Parlay maker")
        Text(v.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = toneColor(v.tone), modifier = Modifier.testTag("parlayHeadline"))
        Text(v.explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        v.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("parlayWarning")) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("parlayModes")) {
            RfqConfig.Mode.entries.forEach { m ->
                FilterChip(
                    selected = cfg.mode == m, enabled = rfq != null,
                    onClick = { if (m == cfg.mode) Unit else if (m == RfqConfig.Mode.LIVE) confirmLive = true else apply(cfg.copy(mode = m)) },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("parlayMode-${m.name}"),
                )
            }
        }
        if (rfq != null) PickingOptions(cfg, rfq, ::apply)
        v.tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { t -> TileCard(t, Modifier.weight(1f)) } }
        }
        Text(v.evidence, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("parlayEvidence"))
        if (rfq != null && rfq.declines.isNotEmpty()) Text("Not priced because: " + rfq.declines.joinToString("; "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (rfq != null) {
            SectionHeader("Limits")
            Text(v.rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper("Return asked on the collateral", "%.0f%%".format(Locale.US, cfg.minReturn * 100), "parlayReturn", { apply(cfg.copy(minReturn = (cfg.minReturn - 0.01).coerceAtLeast(0.01))) }, { apply(cfg.copy(minReturn = (cfg.minReturn + 0.01).coerceAtMost(0.2))) })
            Stepper("Most collateral in one parlay", "\$%.0f".format(Locale.US, cfg.maxCollateral), "parlayCollateral", { apply(cfg.copy(maxCollateral = (cfg.maxCollateral - 10.0).coerceAtLeast(20.0))) }, { apply(cfg.copy(maxCollateral = (cfg.maxCollateral + 10.0).coerceAtMost(250.0))) })
            Stepper("Most collateral in all parlays", "\$%.0f".format(Locale.US, cfg.maxOpenCollateral), "parlayOpen", { apply(cfg.copy(maxOpenCollateral = (cfg.maxOpenCollateral - 10.0).coerceAtLeast(20.0))) }, { apply(cfg.copy(maxOpenCollateral = (cfg.maxOpenCollateral + 10.0).coerceAtMost(500.0))) })
            Stepper("Skip parlays likelier to win than", "%.0f%%".format(Locale.US, cfg.maxFair * 100), "parlayMaxFair", { apply(cfg.copy(maxFair = (cfg.maxFair - 0.05).coerceAtLeast(0.30))) }, { apply(cfg.copy(maxFair = (cfg.maxFair + 0.05).coerceAtMost(0.85))) })
            Stepper("Skip any leg likelier than", "%.0f%%".format(Locale.US, cfg.maxLegFair * 100), "parlayMaxLegFair", { apply(cfg.copy(maxLegFair = (cfg.maxLegFair - 0.02).coerceAtLeast(0.70))) }, { apply(cfg.copy(maxLegFair = (cfg.maxLegFair + 0.02).coerceAtMost(0.99))) })
            Stepper("Most legs", "${cfg.maxLegs}", "parlayLegs", { apply(cfg.copy(maxLegs = (cfg.maxLegs - 1).coerceAtLeast(2))) }, { apply(cfg.copy(maxLegs = (cfg.maxLegs + 1).coerceAtMost(6))) })
        }
        note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("parlayNote")) }
        Text("Processing and Internet use are on the GitHub engine; this view only monitors and sets it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmLive) AlertDialog(
        onDismissRequest = { confirmLive = false },
        title = { Text("Start live quoting?") },
        text = { Text(ParlayView.liveWarning(rfq)) },
        confirmButton = { TextButton(onClick = { confirmLive = false; apply(cfg.copy(mode = RfqConfig.Mode.LIVE)) }, modifier = Modifier.testTag("parlayLiveConfirm")) { Text("Start live") } },
        dismissButton = { TextButton(onClick = { confirmLive = false }) { Text("Not yet") } },
    )
}

@Composable
private fun Stepper(label: String, value: String, tag: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, modifier = Modifier.testTag("$tag-minus")) { Text("−") }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("$tag-value"))
        OutlinedButton(onClick = onPlus, modifier = Modifier.testTag("$tag-plus")) { Text("+") }
    }
}


/**
 * How the maker picks parlays (MK17, Tj 2026-11-19: "put these into the app as options that I can change or turn on and off ... I want to use it tonight"): only parlays that finish on today's slate,
 * longshots only (a win chance up to a limit), and the wallet as the collateral limit. Each is a switch; the numbers have steppers; "Tonight" sets the three at once. These only narrow what the maker
 * accepts: nothing is quoted unless the mode is Live.
 */
@Composable
private fun PickingOptions(cfg: RfqConfig, rfq: RfqStatus, apply: (RfqConfig) -> Unit) {
    fun usd(x: Double?) = x?.let { "\$%.2f".format(Locale.US, it) } ?: "not read yet"
    val tonight = cfg.onlyToday && cfg.longshotsOnly && cfg.walletCollateral
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("pickingOptions")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Which parlays it accepts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Narrow what the maker quotes. Nothing is quoted unless the mode above is Live.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { apply(cfg.copy(onlyToday = true, longshotsOnly = true, longshotMaxChance = 0.25, walletCollateral = true, walletShare = 0.30)) },
                    enabled = !tonight, modifier = Modifier.weight(1f).testTag("optTonight"),
                ) { Text("Tonight: today, longshots, wallet", maxLines = 2) }
                OutlinedButton(
                    onClick = { apply(cfg.copy(onlyToday = false, longshotsOnly = false, walletCollateral = false)) },
                    enabled = cfg.onlyToday || cfg.longshotsOnly || cfg.walletCollateral, modifier = Modifier.testTag("optNormal"),
                ) { Text("All off") }
            }
            OptionRow("Only parlays that finish today", "The last leg must be a game on today's slate (5 am to 5 am Eastern).", cfg.onlyToday, "optToday") { apply(cfg.copy(onlyToday = it)) }
            OptionRow(
                "Longshots only", "Only parlays with at most ${"%.0f".format(Locale.US, cfg.longshotMaxChance * 100)}% chance to win by our odds: fewer of them, a bigger payout, more collateral each.",
                cfg.longshotsOnly, "optLongshots",
            ) { apply(cfg.copy(longshotsOnly = it)) }
            if (cfg.longshotsOnly) Stepper("Win chance at most", "%.0f%%".format(Locale.US, cfg.longshotMaxChance * 100), "optLongshotChance",
                { apply(cfg.copy(longshotMaxChance = (cfg.longshotMaxChance - 0.05).coerceAtLeast(0.10))) }, { apply(cfg.copy(longshotMaxChance = (cfg.longshotMaxChance + 0.05).coerceAtMost(0.40))) })
            OptionRow(
                "Use my wallet as the collateral limit", "It accepts parlays up to the cash in your wallet at any time, instead of the fixed dollar limits below: one parlay may lock up to " +
                    "${"%.0f".format(Locale.US, cfg.walletShare * 100)}% of the wallet.", cfg.walletCollateral, "optWallet",
            ) { apply(cfg.copy(walletCollateral = it)) }
            if (cfg.walletCollateral) {
                Stepper("One parlay locks at most", "%.0f%% of the wallet".format(Locale.US, cfg.walletShare * 100), "optWalletShare",
                    { apply(cfg.copy(walletShare = (cfg.walletShare - 0.05).coerceAtLeast(0.10))) }, { apply(cfg.copy(walletShare = (cfg.walletShare + 0.05).coerceAtMost(1.0))) })
                Text(
                    "Wallet now: ${usd(rfq.walletCash)} free, ${usd(rfq.walletTotal)} with the collateral already held. One parlay up to ${usd(rfq.walletParlayCap)}, one game's legs together up to ${usd(rfq.walletLegCap)}, " +
                        "quoting stops for six hours if a day's settled parlays lose ${usd(rfq.lossStopNow)}.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("optWalletNumbers"),
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, sub: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
