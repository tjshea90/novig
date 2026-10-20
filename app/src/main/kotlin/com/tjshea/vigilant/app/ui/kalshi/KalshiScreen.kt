package com.tjshea.vigilant.app.ui.kalshi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.kalshi.KalshiHub
import com.tjshea.vigilant.app.ui.EmptyState
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.LeagueChips
import com.tjshea.vigilant.app.ui.OpportunityCard
import com.tjshea.vigilant.app.ui.ScanButton
import com.tjshea.vigilant.app.ui.rememberNow
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity

private enum class KPage(val label: String) { EV("+EV"), ARB("Arb"), BIDS("Bids"), BETS("Bets"), AUTO("Auto"), SETTINGS("Setup") }

/**
 * The Kalshi tab (KV, Tj 2026-10-20): Kalshi's own +EV list (the same scan, rules and fair odds as the Novig side, priced at Kalshi's ask with Kalshi's fee), arbitrage between the two
 * venues, bids, the bets placed there, the auto-bet and its setup. Nothing here changes a Novig setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalshiScreen(hub: KalshiHub, onOpenKeySettings: () -> Unit) {
    val ui by hub.ui.collectAsState()
    var page by rememberSaveable { mutableStateOf(KPage.EV.name) }
    val current = KPage.entries.firstOrNull { it.name == page } ?: KPage.EV
    val now = rememberNow(5_000)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); hub.messageShown() } }
    LaunchedEffect(Unit) { hub.refreshBalance(); hub.findArbs() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Kalshi", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    ui.scanning -> ui.progress ?: "Scanning"
                                    ui.scannedAtMs != null -> "Scanned ${Format.age(ui.scannedAtMs, now)}" + (ui.balance?.let { " · ${Format.money(it)} cash" } ?: "")
                                    else -> ui.balance?.let { "${Format.money(it)} cash" } ?: "Not scanned yet"
                                },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = { ScanButton(ui.scanning, ui.config.scan.leagues.isNotEmpty(), hub::scanNow, Modifier.testTag("kalshiScan")) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    KPage.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = p == current, onClick = { page = p.name }, shape = SegmentedButtonDefaults.itemShape(i, KPage.entries.size),
                            modifier = Modifier.testTag("kalshiPage-${p.name}"),
                        ) { Text(p.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                KPage.EV -> EvPage(hub, ui, now, onOpenKeySettings)
                KPage.ARB -> ArbPage(hub, ui)
                KPage.BIDS -> KalshiBidsPage(hub, ui)
                KPage.BETS -> BetsPage(ui, now)
                KPage.AUTO -> AutoPage(hub, ui)
                KPage.SETTINGS -> SetupPage(hub, ui, onOpenKeySettings)
            }
        }
    }
}

@Composable
private fun EvPage(hub: KalshiHub, ui: com.tjshea.vigilant.app.kalshi.KalshiUi, now: Long, onOpenKeySettings: () -> Unit) {
    var selected by remember { mutableStateOf<Opportunity?>(null) }
    val leagues = remember { Leagues.ALL.filter { it.kalshiSeries.isNotEmpty() } }
    LeagueChips(
        leagues, ui.config.scan.leagues,
        onToggle = { n -> hub.update { c -> c.copy(scan = c.scan.copy(leagues = if (n in c.scan.leagues) c.scan.leagues - n else c.scan.leagues + n)) } },
        modifier = Modifier.padding(vertical = 6.dp),
    )
    if (!ui.hasKey) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text("No Kalshi key saved: the list works from public prices, but orders and your balance need your Key ID and private key.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenKeySettings) { Text("Add the Kalshi key") }
            }
        }
    }
    ui.errors.firstOrNull()?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
    }
    ui.autoNote?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
    }
    if (ui.feed.isEmpty()) {
        EmptyState(
            if (ui.scannedAtMs == null) "Scan Kalshi" else "No +EV bets at Kalshi right now",
            if (ui.scannedAtMs == null) "Tap Scan: Kalshi's prices are compared with the same sharp books Novig's are." else "Kalshi's prices were compared with the other books' fair odds; none is above ${Format.percent(ui.config.scan.minEvPercent)}.",
            modifier = Modifier.padding(16.dp),
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("kalshiFeed")) {
            items(ui.feed, key = { it.key }) { o ->
                OpportunityCard(o, ui.config.scan, now, onOpen = false, onClick = { selected = o })
            }
        }
    }
    selected?.let { o -> KalshiBetSheet(hub, ui, o, now, onDismiss = { selected = null }) }
}
