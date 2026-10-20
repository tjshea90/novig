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

private enum class KPage(val label: String) { ARB("Arb"), BIDS("Bids"), BETS("Bets"), AUTO("Auto"), SETTINGS("Setup") }

/**
 * The Kalshi tab (KV, Tj 2026-10-20): arbitrage (its +EV list is in the +EV tab beside Novig's since CH33) between the two
 * venues, bids, the bets placed there, the auto-bet and its setup. Nothing here changes a Novig setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalshiScreen(hub: KalshiHub, onOpenKeySettings: () -> Unit) {
    val ui by hub.ui.collectAsState()
    var page by rememberSaveable { mutableStateOf(KPage.ARB.name) }
    val current = KPage.entries.firstOrNull { it.name == page } ?: KPage.ARB
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
                KPage.ARB -> ArbPage(hub, ui)
                KPage.BIDS -> KalshiBidsPage(hub, ui)
                KPage.BETS -> BetsPage(ui, now)
                KPage.AUTO -> AutoPage(hub, ui)
                KPage.SETTINGS -> SetupPage(hub, ui, onOpenKeySettings)
            }
        }
    }
}
