package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.ScanUiState
import com.tjshea.vigilant.data.scanner.Sport
import com.tjshea.vigilant.engine.EvOpportunity
import com.tjshea.vigilant.engine.FeeResult
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunitiesScreen(
    uiState: ScanUiState,
    availableSports: List<Sport>,
    selectedSports: Set<Sport>,
    onToggleSport: (Sport) -> Unit,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vigilant") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRescan) {
                Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SportPicker(availableSports, selectedSports, onToggleSport)
            if (uiState is ScanUiState.Loaded && (!uiState.novigIsLive || !uiState.referenceIsLive)) {
                SampleDataBanner(uiState.novigIsLive, uiState.referenceIsLive)
            }
            PullToRefreshBox(
                isRefreshing = uiState is ScanUiState.Loading,
                onRefresh = onRescan,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (uiState) {
                    is ScanUiState.Idle -> IdleState(selectedSports.isEmpty())
                    is ScanUiState.Loading -> LoadingState()
                    is ScanUiState.Error -> ErrorState(uiState.message)
                    is ScanUiState.Loaded -> OpportunityList(uiState.opportunities)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportPicker(availableSports: List<Sport>, selectedSports: Set<Sport>, onToggleSport: (Sport) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(availableSports) { sport ->
            FilterChip(
                selected = sport in selectedSports,
                onClick = { onToggleSport(sport) },
                label = { Text(sport.displayName) },
            )
        }
    }
}

@Composable
private fun SampleDataBanner(novigIsLive: Boolean, referenceIsLive: Boolean) {
    val missing = buildList {
        if (!novigIsLive) add("Novig (add a SharpAPI key)")
        if (!referenceIsLive) add("reference (add a The Odds API key)")
    }
    Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SAMPLE DATA — ${missing.joinToString(" and ")} still on sample data. Add keys via ⚙ Settings.",
            modifier = Modifier.padding(8.dp),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scan failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OpportunityList(opportunities: List<EvOpportunity>) {
    if (opportunities.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("No matched markets found.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(opportunities) { opportunity -> OpportunityCard(opportunity) }
    }
}

@Composable
private fun OpportunityCard(opportunity: EvOpportunity) {
    val positive = opportunity.isPositiveEv
    val accentColor = when {
        positive -> Color(0xFF22D3A5)
        opportunity.netEv != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(opportunity.eventName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${opportunity.marketDescription} — ${opportunity.outcomeLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Novig: ${formatAmerican(opportunity.novigPrice)}")
                Text("Fair: ${formatPercent(opportunity.fairProbability)}")
                Text(netEvLabel(opportunity), color = accentColor, fontWeight = FontWeight.Bold)
            }

            Text(
                "Devig: ${opportunity.devigMethod} · Ref: ${opportunity.referenceBooks.joinToString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val fee = opportunity.fee
            if (fee is FeeResult.Unknown) {
                Text(
                    "Fee unknown — not counted as +EV: ${fee.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun netEvLabel(opportunity: EvOpportunity): String {
    val netEv = opportunity.netEv ?: return "fee unknown"
    val roi = netEv / opportunity.novigPrice
    return "${if (roi >= 0) "+" else ""}${formatPercent(roi)} net"
}

private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)

private fun formatAmerican(novigPrice: Double): String {
    val american = Odds.decimalToAmerican(Odds.novigPriceToDecimalOdds(novigPrice))
    return if (american > 0) "+$american" else american.toString()
}
