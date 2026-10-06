package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.BurstText
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The real-money burst trader's settings (Tj, 2026-10-06: "make it good enough so that if it is proven I can just turn it on"; RESEARCH.md §95). The switch cannot go ON until the recorder's proof
 * says so ([UiState.burstProofReason] null once read), and going on asks first, in Tj's own numbers. The running gate ([com.tjshea.vigilant.app.VigilantApp.burstGate]) checks the same proof
 * again at every window, so a saved "on" with a lapsed proof places nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BurstTraderSettings(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val unlocked = s.burstRecorder && state.burstProofRead && state.burstProofReason == null
    var confirming by remember { mutableStateOf(false) }
    Text(BurstText.TRADE_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    s.burstTradeHalted?.let { why ->
        Column(Modifier.padding(vertical = 4.dp).testTag("burstTradeHalted")) {
            Text("Stopped: $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onUpdate { it.copy(burstTradeHalted = null) } }, modifier = Modifier.testTag("burstTradeResume")) { Text(BurstText.TRADE_RESUME) }
        }
    }
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = s.burstTrade, role = Role.Switch,
            onValueChange = { v -> if (!v) onUpdate { it.copy(burstTrade = false) } else if (unlocked) confirming = true },
        ).padding(vertical = 6.dp).testTag("burstTradeSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(BurstText.TRADE_TITLE, style = MaterialTheme.typography.bodyMedium)
            Text(BurstText.TRADE_SUB, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = s.burstTrade, onCheckedChange = null, enabled = unlocked || s.burstTrade)
    }
    Text(
        BurstText.tradeLine(if (state.burstProofRead) state.burstProofReason else "checking the recorder's proof…", state.burstTrade, s, state.burstProvedLeagues),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp).testTag("burstTradeNote"),
    )
    if (unlocked || s.burstTrade) {
        DollarChips("A leg's stake", ScanSettings.BURST_TRADE_STAKE_CHOICES, s.burstTradeStake, "burstTradeStake") { v -> onUpdate { it.copy(burstTradeStake = v) } }
        DollarChips("Most spent on one game", ScanSettings.BURST_TRADE_MAX_GAME_CHOICES, s.burstTradeMaxGame, "burstTradeGame") { v -> onUpdate { it.copy(burstTradeMaxGame = v) } }
        DollarChips("Most spent in a day", ScanSettings.BURST_TRADE_MAX_DAY_CHOICES, s.burstTradeMaxDay, "burstTradeDay") { v -> onUpdate { it.copy(burstTradeMaxDay = v) } }
        DollarChips("Halt when legs held alone cost", ScanSettings.BURST_TRADE_HALT_LOSS_CHOICES, s.burstTradeHaltLoss, "burstTradeHalt") { v -> onUpdate { it.copy(burstTradeHaltLoss = v) } }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(BurstText.TRADE_CONFIRM_TITLE) },
            text = { Text(BurstText.tradeConfirm(s, state.burstProvedLeagues)) },
            confirmButton = { TextButton(onClick = { confirming = false; onUpdate { it.copy(burstTrade = true, burstTradeHalted = null) } }, modifier = Modifier.testTag("burstTradeConfirm")) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.testTag("burstTradeCancel")) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DollarChips(title: String, choices: List<Double>, selected: Double, tag: String, onPick: (Double) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { d ->
            FilterChip(selected = d == selected, onClick = { onPick(d) }, label = { Text("$" + d.toInt()) }, modifier = Modifier.testTag("$tag-${d.toInt()}"))
        }
    }
}
