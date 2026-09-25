package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onToggleLeague: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onTrack: (Opportunity, Double) -> Unit,
) {
    var selected by remember { mutableStateOf<Opportunity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Positive EV", fontWeight = FontWeight.Bold)
                        StatusLine(state.status)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh everything") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.status.refreshing && state.result == null,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "leagues") {
                    LeagueChips(Leagues.ALL, state.settings.leagues, onToggleLeague, Modifier.padding(top = 4.dp))
                }
                item(key = "summary") { FeedSummary(state, onOpenSettings) }
                items(state.feed, key = { it.key }) { o ->
                    OpportunityCard(o, state.settings, Modifier.padding(horizontal = 12.dp).animateItem()) { selected = o }
                }
            }
        }
    }

    selected?.let { o ->
        // Show the freshest copy of the selected line: the loop may have re-priced it since the tap.
        val live = state.result?.opportunities?.firstOrNull { it.key == o.key } ?: o
        OpportunitySheet(live, state.settings, onDismiss = { selected = null }, onTrack = { stake -> onTrack(live, stake); selected = null })
    }
}

@Composable
private fun FeedSummary(state: UiState, onOpenSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.loaded && !state.status.hasOddsKey) {
            Banner(
                "Live Novig prices are on. Add a free The Odds API key to get fair odds and +EV.",
                action = "Add key",
                onAction = onOpenSettings,
            )
        }
        state.status.errors.take(2).forEach { Banner(it, color = Edge.colors.negative) }

        val result = state.result
        when {
            state.settings.leagues.isEmpty() ->
                EmptyState("Pick a league", "Choose one or more leagues above to start scanning Novig.")
            result == null ->
                EmptyState("Scanning Novig…", "Pulling the board and matching it to the sportsbooks.")
            !state.status.hasOddsKey -> EmptyState(
                "Fair odds need a key",
                "Novig's live prices for ${result.games.size} games are on the Games tab now. +EV needs fair " +
                    "odds from the sportsbooks: add a free The Odds API key in Settings.",
                action = "Open Settings",
                onAction = onOpenSettings,
            )
            state.feed.isEmpty() -> EmptyState(
                "No +EV right now",
                "${result.stats.outcomesWithFair} prices checked across ${result.stats.matchedEvents} games. " +
                    "Nothing at or above ${Format.percent(state.settings.minEvPercent)} EV. Prices refresh every " +
                    "${state.settings.novigRefreshSeconds}s while this screen is open.",
            )
            else -> Text(
                "${state.feed.size} bets at +${Format.percent(state.settings.minEvPercent)} EV or better · " +
                    "${result.stats.outcomesWithFair} prices checked · fair = ${fairSourceLabel(state.settings)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun fairSourceLabel(s: ScanSettings): String = when (s.fairSource) {
    FairSource.SHARP -> "sharp books"
    FairSource.MARKET_AVERAGE -> "market average"
    FairSource.BLEND -> "${(s.sharpWeight * 100).toInt()}% sharp blend"
} + ", ${s.devigMethod.displayName.lowercase()} devig"

@Composable
fun OpportunityCard(o: Opportunity, settings: ScanSettings, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val q = o.quote ?: return
    val fair = o.fairProbability ?: return
    val now = rememberNow(5_000)
    val refStale = o.fairUpdatedMs?.let { now - it > settings.staleReferenceMinutes * 60_000L } ?: false
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvBadge(q.evPercent)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${o.league.emoji} ${o.league.displayName} · ${if (o.isLive) "LIVE" else Format.startTime(o.event.startsTs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (o.isLive) Edge.colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (refStale) Text("stale", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.selection, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${o.marketLabel} · ${o.eventName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("NOVIG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(Format.american(q.cost), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("Fair", "${Format.american(fair)} · ${Format.percent(fair)}")
                LabeledValue("Price", Format.percent(q.cost, 1))
                o.suggestedStake?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
            }
            val depth = o.depth
            Text(
                buildString {
                    append(o.fair?.let { f -> f.booksUsed.take(3).joinToString(", ") + if (f.booksUsed.size > 3) " +${f.booksUsed.size - 3}" else "" } ?: "")
                    if (depth != null && depth.contracts > 0) append("  ·  ${Format.money(depth.dollarCost)} fillable at +EV")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
