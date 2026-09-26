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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tjshea.vigilant.app.ScanStatus
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.FeedSort
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: UiState,
    onScan: () -> Unit,
    onToggleLeague: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onTrack: (Opportunity, Double) -> Unit,
    onSort: (FeedSort) -> Unit = {},
    /** Re-read these markets' Novig prices only (seconds, no fair-odds calls). */
    onRecheck: (Collection<String>) -> Unit = {},
) {
    var selected by remember { mutableStateOf<Opportunity?>(null) }
    // One coarse clock for every card's "stale" check, instead of a ticker per card.
    val now = rememberNow(15_000)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Positive EV", fontWeight = FontWeight.Bold)
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
        PullToRefreshBox(
            // The progress bar under the top bar shows the scan; a pull just starts one.
            isRefreshing = false,
            onRefresh = onScan,
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
                item(key = "summary") { FeedSummary(state, now, onScan, onOpenSettings, onSort) { onRecheck(feedMarketIds(state)) } }
                items(state.feed, key = { it.key }) { o ->
                    OpportunityCard(o, state.settings, now, Modifier.padding(horizontal = 12.dp).animateItem()) { selected = o }
                }
            }
        }
    }

    selected?.let { o ->
        // Show the freshest copy of the selected line: a scan may have re-priced it since the tap.
        val live = state.result?.opportunities?.firstOrNull { it.key == o.key } ?: o
        OpportunitySheet(
            live, state.settings,
            onDismiss = { selected = null },
            onTrack = { stake -> onTrack(live, stake); selected = null },
            onRecheck = { onRecheck(listOf(live.market.marketId)) }.takeIf { !state.status.scanning },
            rechecking = state.status.rechecking,
        )
    }
}

@Composable
private fun FeedSummary(
    state: UiState,
    now: Long,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onSort: (FeedSort) -> Unit,
    onRecheck: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UsageStrip(state)
        state.status.errors.take(3).forEach { Banner(it, color = Edge.colors.negative) }
        val old = if (state.status.scanning) emptyList() else state.feed.filter { it.priceIsOld(now) }
        if (old.isNotEmpty()) {
            // Novig prices age: an edge from 20 minutes ago may be gone. Say so before Tj bets it,
            // and offer the few-second recheck rather than a whole scan.
            val oldest = old.mapNotNull { it.bookFetchedAtMs }.minOrNull()
            Banner(
                (if (old.size == state.feed.size) "These prices are" else "${old.size} of these prices are") +
                    " up to ${Format.age(oldest, now).removeSuffix(" ago")} old. Recheck them (a few seconds) or scan again before betting.",
                action = if (state.status.rechecking) null else "Recheck",
                onAction = onRecheck,
            )
        }
        if (state.status.unscanned.isNotEmpty() && !state.status.scanning) {
            Banner(
                "${state.status.unscanned.joinToString(", ")} not scanned yet.",
                action = "Scan",
                onAction = onScan,
            )
        }

        val result = state.result
        val status = state.status
        when {
            state.settings.leagues.isEmpty() ->
                EmptyState("Pick a league", "Choose one or more leagues above, then tap Scan.")
            status.scanning && state.feed.isEmpty() -> EmptyState(
                "Scanning…",
                "Reading Novig's board" + sourceNames(state).let { if (it.isEmpty()) "" else " and fair odds from $it" } + ". " +
                    "Bets appear here as Novig's prices come in, likeliest edges first. " +
                    "You can switch apps: the scan keeps going and tells you when it's done.",
            )
            result == null -> EmptyState(
                "Tap Scan to find +EV bets",
                "Nothing is downloaded until you ask: tap Scan or pull down. " +
                    sourceNames(state).let {
                        if (it.isEmpty()) "No fair-odds source is on (Settings), so a scan shows Novig's prices only." else "Fair odds come from $it."
                    } +
                    if (state.pinnapiKeys.isEmpty() && state.settings.usePinnacle) " Add a free Pinnacle key in Settings for sharper lines." else "",
                action = "Scan now",
                onAction = onScan,
            )
            result.stats.matchedEvents == 0 && result.games.isNotEmpty() -> EmptyState(
                "No fair odds for these games",
                "Novig's prices for ${result.games.size} games are on the Games tab, but none of your fair-odds " +
                    "sources listed them this scan. Check the sources in Settings (a Pinnacle or Odds API key " +
                    "covers the most leagues).",
                action = "Fair odds settings",
                onAction = onOpenSettings,
            )
            state.feed.isEmpty() -> EmptyState(
                "No +EV right now",
                "${result.stats.outcomesWithFair} prices checked across ${result.stats.matchedEvents} games. " +
                    "Nothing at or above ${Format.percent(state.settings.minEvPercent)} EV. Scan again for fresh prices.",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.feed.size} bet${if (state.feed.size == 1) "" else "s"} ≥ +${Format.percent(state.settings.minEvPercent)} EV · " +
                            "${result.stats.outcomesWithFair} checked" + if (status.scanning) " so far" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FeedSort.entries.forEach { sort ->
                        val on = state.settings.feedSort == sort
                        TextButton(onClick = { onSort(sort) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(sort.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Fair: ${fairSourceLabel(state.settings)}" + sourceSummary(status).let { if (it.isEmpty()) "" else " · $it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!status.scanning) {
                        TextButton(onClick = onRecheck, enabled = !status.rechecking, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(if (status.rechecking) "Rechecking…" else "Recheck prices", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** "Pinnacle, Polymarket, Kalshi": the sources the next scan will use. */
private fun sourceNames(state: UiState): String {
    val s = state.settings
    return buildList {
        if (s.usePinnacle && state.pinnapiKeys.isNotEmpty()) add("Pinnacle")
        if (s.usePolymarket) add("Polymarket")
        if (s.useKalshi) add("Kalshi")
        if (s.useOddsApi && state.oddsApiKeys.isNotEmpty()) add("The Odds API")
    }.let { if (it.size <= 1) it.joinToString("") else it.dropLast(1).joinToString(", ") + " and " + it.last() }
}

/** "Pinnacle 12 · Polymarket 14 · Kalshi 9 games": who matched what on the last scan. */
fun sourceSummary(status: ScanStatus): String =
    status.sources.filter { it.matched > 0 }.joinToString(" · ") { "${it.name} ${it.matched}" }.let { if (it.isEmpty()) it else "$it games" }

/** The feed's markets, best first: what "Recheck" re-reads (the scanner reads at most 40). */
fun feedMarketIds(state: UiState): List<String> = state.feed.map { it.market.marketId }.distinct()

fun fairSourceLabel(s: ScanSettings): String = when (s.fairSource) {
    FairSource.SHARP -> "sharp books"
    FairSource.MARKET_AVERAGE -> "market average"
    FairSource.BLEND -> "${(s.sharpWeight * 100).toInt()}% sharp blend"
} + ", ${s.devigMethod.displayName.lowercase()} devig"

@Composable
fun OpportunityCard(o: Opportunity, settings: ScanSettings, now: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val q = o.quote ?: return
    val fair = o.fairProbability ?: return
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
                if (o.priceIsOld(now)) Text("old price ", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
                if (refStale) Text("stale fair", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
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
