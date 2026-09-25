package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(state: UiState, onSettle: (String, BetStatus) -> Unit, onDelete: (String) -> Unit) {
    val stats = BetTracker.stats(state.bets)
    var confirmDelete by remember { mutableStateOf<TrackedBet?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bet tracker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "stats") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabeledValue("Profit", Format.signedMoney(stats.profit), valueColor = moneyColor(stats.profit))
                            LabeledValue("ROI", stats.roi?.let { Format.evPercent(it) } ?: "—")
                            LabeledValue("Expected", Format.signedMoney(stats.expectedProfit))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabeledValue("Bets", "${stats.bets} (${stats.pending} open)")
                            LabeledValue("Avg EV", stats.averageEv?.let { Format.evPercent(it) } ?: "—")
                            LabeledValue("Avg CLV", stats.averageClv?.let { Format.evPercent(it) } ?: "—")
                        }
                        Text(
                            "CLV compares your price to the last fair line seen before kickoff. Beating the close " +
                                "consistently is the best sign the edges are real" +
                                (stats.beatClosePercent?.let { " — you've beaten it on ${Format.percent(it, 0)} of bets." } ?: "."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.bets.isEmpty()) {
                item(key = "empty") { EmptyState("No bets tracked yet", "Open any +EV card and tap Track to log the bet you placed on Novig.") }
            }
            items(state.bets.sortedByDescending { it.createdAtMs }, key = { it.id }) { bet ->
                BetCard(bet, onSettle, onDelete = { confirmDelete = bet })
            }
        }
    }

    confirmDelete?.let { bet ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this bet?") },
            text = { Text("${bet.selection} · ${Format.money(bet.stake)}. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(bet.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun moneyColor(v: Double): Color = when {
    v > 0 -> Edge.colors.positive
    v < 0 -> Edge.colors.negative
    else -> Color.Unspecified
}

@Composable
private fun BetCard(bet: TrackedBet, onSettle: (String, BetStatus) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bet.selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${bet.marketLabel} · ${bet.league} · ${Format.shortDate(bet.startsTs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(bet.eventName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete bet") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Stake", Format.money(bet.stake))
                LabeledValue("Price", Format.american(bet.cost))
                LabeledValue("EV", Format.evPercent(bet.evPercentAtBet))
                LabeledValue("CLV", bet.clvPercent?.let { Format.evPercent(it) } ?: "—")
                LabeledValue(
                    if (bet.status == BetStatus.PENDING) "To win" else "Result",
                    bet.profit?.let { Format.signedMoney(it) } ?: Format.money(bet.profitIfWon),
                    valueColor = bet.profit?.let { moneyColor(it) } ?: Color.Unspecified,
                )
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bet.status == BetStatus.PENDING) {
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.WON) }, label = { Text("Won") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.LOST) }, label = { Text("Lost") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.PUSH) }, label = { Text("Push") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.VOID) }, label = { Text("Void") })
                } else {
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.PENDING) }, label = { Text("${bet.status.name.lowercase().replaceFirstChar { it.uppercase() }} · undo") })
                }
            }
        }
    }
}
