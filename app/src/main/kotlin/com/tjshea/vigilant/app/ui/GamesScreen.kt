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
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.PricedGame

/**
 * OddsJam's "odds screen", for Novig: every game on the board, and inside a game every line with
 * Novig's live price next to the fair price. Works without an Odds API key (Novig column only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(state: UiState, onOpen: (Opportunity) -> Unit, onToggleLeague: (String) -> Unit) {
    var openEventId by rememberSaveable { mutableStateOf<String?>(null) }
    val games = state.result?.games.orEmpty()
    val open = games.firstOrNull { it.event.eventId == openEventId }

    if (open != null) {
        BackHandler { openEventId = null }
        GameDetail(open, state, onBack = { openEventId = null }, onOpen = onOpen)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Games", fontWeight = FontWeight.Bold)
                        StatusLine(state.status, streaming = state.novig.stream is com.tjshea.vigilant.data.novig.stream.StreamState.Live)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "leagues") {
                LeagueChips(com.tjshea.vigilant.data.scanner.Leagues.ALL, state.settings.leagues, onToggleLeague, Modifier.padding(top = 4.dp))
            }
            if (games.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (state.result == null) "Loading the board…" else "No games in the next ${state.settings.daysAhead} days",
                        "Games from the leagues you picked show up here with Novig's live prices.",
                    )
                }
            }
            items(games, key = { it.event.eventId }) { g -> GameRow(g, Modifier.padding(horizontal = 12.dp)) { openEventId = g.event.eventId } }
        }
    }
}

@Composable
private fun GameRow(g: PricedGame, modifier: Modifier, onClick: () -> Unit) {
    val best = g.outcomes.mapNotNull { it.evPercent }.maxOrNull()
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
                    if (g.refEvent == null) "Novig only · no sportsbook match yet" else "${g.outcomes.count { it.fairProbability != null }} prices vs fair",
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
private fun GameDetail(g: PricedGame, state: UiState, onBack: () -> Unit, onOpen: (Opportunity) -> Unit) {
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
                    Text("Novig", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Fair", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("EV", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            byMarket.forEach { outcomes ->
                item(key = outcomes.first().market.marketId) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(outcomes.first().marketLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        outcomes.forEach { o ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = o.quote != null) { onOpen(o) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(o.selection, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    o.quote?.let { Format.american(it.cost) } ?: o.ladder.firstOrNull()?.let { Format.american(it.price) } ?: "—",
                                    Modifier.width(64.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(o.fairProbability?.let { Format.american(it) } ?: "—", Modifier.width(64.dp), fontFamily = FontFamily.Monospace)
                                Text(
                                    o.evPercent?.let { Format.evPercent(it) } ?: "",
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

private fun order(type: String) = when (type) {
    "MONEY", "MONEYLINE_3_WAY_WIN", "MONEYLINE_3_WAY_DRAW" -> 0
    "SPREAD" -> 1
    else -> 2
}
