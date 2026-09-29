package com.tjshea.vigilant.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.PricedGame

/**
 * OddsJam's "odds screen", for Novig: every game on the board, and inside a game every line with
 * Novig's price from the last scan next to the fair price. Pull down (or Scan) to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(state: UiState, onOpen: (Opportunity) -> Unit, onToggleLeague: (String) -> Unit, onScan: () -> Unit = {}) {
    var openEventId by rememberSaveable { mutableStateOf<String?>(null) }
    // Fair prices and EVs show only while the other books' prices are current (RESEARCH.md §24).
    val now = rememberNow(15_000)
    val games = state.gamesAt(now)
    // An open game stays open even if it just left the start-time window.
    val open = state.result?.games.orEmpty().firstOrNull { it.event.eventId == openEventId }

    if (open != null) {
        BackHandler { openEventId = null }
        GameDetail(open, state, now, onBack = { openEventId = null }, onOpen = onOpen)
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Games", fontWeight = FontWeight.Bold)
                            StatusLine(state.status)
                        }
                    },
                    actions = { ScanButton(state.status.scanning, state.loaded && state.settings.leagues.isNotEmpty(), onScan) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                ScanProgressBar(state.status)
            }
        },
    ) { padding ->
        VigilantPullToRefresh(
            // The progress bar under the top bar shows the scan; the pull's arrow lets go once it starts.
            busy = state.status.scanning,
            onRefresh = onScan,
            modifier = Modifier.padding(padding).fillMaxSize(),
            holdWhileBusy = false,
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Pinned while the games scroll under it (Tj, 2026-09-29).
                stickyHeader(key = "leagues") {
                    StickyBar { LeagueChips(com.tjshea.vigilant.data.scanner.Leagues.ALL, state.settings.leagues, onToggleLeague, Modifier.padding(vertical = 4.dp)) }
                }
                if (games.isEmpty()) {
                    item(key = "empty") {
                        when {
                            state.result == null && state.status.scanning -> EmptyState("Scanning…", "Reading ${AppBook.name}'s board.")
                            state.result == null -> EmptyState(
                                "Tap Scan to load the board",
                                "Games from the leagues you picked show up here with ${AppBook.name}'s prices and the fair line.",
                                action = "Scan now",
                                onAction = onScan,
                            )
                            state.settings.startsWithinHours > 0 && state.result.games.isNotEmpty() -> EmptyState(
                                "No games in the next ${state.settings.startsWithinHours} hours",
                                "Games later than that are hidden by the start-time filter (+EV tab or Settings).",
                            )
                            else -> EmptyState(
                                "No games in the next ${state.settings.daysAhead} days",
                                "Games from the leagues you picked show up here with ${AppBook.name}'s prices and the fair line.",
                            )
                        }
                    }
                }
                items(games, key = { it.event.eventId }) { g -> GameRow(g, now, Modifier.padding(horizontal = 12.dp)) { openEventId = g.event.eventId } }
            }
        }
    }
}

@Composable
private fun GameRow(g: PricedGame, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val best = g.outcomes.filterNot { it.fairIsOld(now) }.mapNotNull { it.evPercent }.maxOrNull()
    val ml = g.outcomes.filter { it.market.marketType == "MONEY" }
    Card(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${g.league.emoji} ${g.league.displayName} · ${if (g.event.isLive) "LIVE" else Format.startTime(g.event.startsTs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(g.event.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (g.refEvent == null) "${AppBook.name} only · no fair odds for this game" else "${g.outcomes.count { it.fairProbability != null }} prices vs fair",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (g.refEvent == null) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                ml.forEach { o ->
                    Text(
                        "${o.outcome.name} ${o.quote?.let { Format.american(it.cost) } ?: o.ladder.firstOrNull()?.let { Format.american(it.price) } ?: "—"}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (best != null && best > 0) EvBadge(best, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetail(g: PricedGame, state: UiState, now: Long, onBack: () -> Unit, onOpen: (Opportunity) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(g.event.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("${g.league.displayName} · ${Format.startTime(g.event.startsTs)}", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val byMarket = g.outcomes.groupBy { it.market.marketId }.values
            .sortedWith(compareBy({ order(it.first().market.marketType) }, { it.first().lineKey?.line ?: 0.0 }))
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Selection", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(AppBook.name, Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Fair", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("EV", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            byMarket.forEach { outcomes ->
                item(key = outcomes.first().market.marketId) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(outcomes.first().marketLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        outcomes.forEach { o ->
                            // Past a few minutes the other books' prices behind it aren't current: Novig's price only.
                            val old = o.fairIsOld(now)
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = o.quote != null && !old) { onOpen(o) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(o.selection, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    o.quote?.let { Format.american(it.cost) } ?: o.ladder.firstOrNull()?.let { Format.american(it.price) } ?: "—",
                                    Modifier.width(64.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(if (old) "old" else o.fairProbability?.let { Format.american(it) } ?: "—", Modifier.width(64.dp), fontFamily = FontFamily.Monospace)
                                Text(
                                    if (old) "" else o.evPercent?.let { Format.evPercent(it) } ?: "",
                                    Modifier.width(72.dp),
                                    color = when {
                                        (o.evPercent ?: 0.0) >= state.settings.minEvPercent -> Edge.colors.positive
                                        (o.evPercent ?: 0.0) < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Game-detail order: main lines, then 1st half / F5 / 1st set, 1st inning, team totals, then player props. */
private fun order(type: String) = when (type) {
    "MONEY" -> 0
    "SPREAD" -> 1
    "TOTAL" -> 2
    "SPREAD_1H" -> 3
    "TOTAL_1H" -> 4
    "FIRST_SET_MONEYLINE" -> 4
    "FIRST_INNING_TOTAL" -> 5
    "TEAM_TOTAL", "PLAYER_GAMES_WON" -> 6
    else -> 7
}
