package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.R
import com.tjshea.vigilant.app.ScanStatus
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.FeedSort
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    state: UiState,
    onScan: () -> Unit,
    onToggleLeague: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onTrack: (Opportunity, Double) -> Unit,
    onSort: (FeedSort) -> Unit = {},
    /** Show only games starting within this many hours (0 = any). */
    onStartsWithin: (Int) -> Unit = {},
    /** Re-read these markets' Novig prices only (seconds, no fair-odds calls). */
    onRecheck: (Collection<String>) -> Unit = {},
    /** Shrink to the mini window over other apps. Null hides the button (no picture-in-picture). */
    onMiniWindow: (() -> Unit)? = null,
    /** Pause every scan (true) or resume (false). */
    onPause: (Boolean) -> Unit = {},
    /** ✕: the bet leaves this list (and the widget) for good, through refreshes and rescans, as on the CNO tab. */
    onHide: (Opportunity) -> Unit = {},
    /** Undo, or "Put back" in the removed list: the bet (by key) shows again. */
    onUnhide: (String) -> Unit = {},
    /** ParlayAPI's own picks at Novig (PARLAY_API.md §6.5): shown while ParlayAPI is on with a key. Null: no section. */
    parlay: ParlayPickActions? = null,
) {
    var selected by remember { mutableStateOf<Opportunity?>(null) }
    // One coarse clock for every card's "stale" check, instead of a ticker per card.
    val now = rememberNow(15_000)
    // Vigilant's own bets Tj removed with ✕ (CNO's are listed on its tab), to put back.
    val removed = state.placed.filter { it.hidden && !it.key.startsWith("cno:") }
    var showRemoved by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun undoable(text: String, key: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onUnhide(key)
        }
    }
    val hide: (Opportunity) -> Unit = { o ->
        onHide(o)
        undoable("Removed: ${o.selection}. Hidden here and in the widget.", o.key)
    }
    // ParlayAPI's ✓ and ✕ offer Undo the way CNO's do.
    val parlayActions = parlay?.let { a ->
        ParlayPickActions(
            onScan = a.onScan, onRecheck = a.onRecheck, onOpen = a.onOpen, opening = a.opening,
            onPlaced = { item -> a.onPlaced(item); undoable("Placed: ${item.title}. Logged in the Tracker.", item.key) },
            onHide = { item -> a.onHide(item); undoable("Removed: ${item.title}.", item.key) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Positive EV", fontWeight = FontWeight.Bold)
                            StatusLine(state.status)
                        }
                    },
                    actions = {
                        if (onMiniWindow != null) {
                            IconButton(onClick = onMiniWindow) {
                                Icon(painterResource(R.drawable.ic_mini_window), contentDescription = "Mini window over ${AppBook.name}", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        PauseButton(state.settings.paused, onPause)
                        ScanButton(state.status.scanning, state.loaded && state.settings.leagues.isNotEmpty() && !state.settings.paused, onScan)
                    },
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
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // The league chips and the start-time window stay pinned while the bets scroll under them (Tj, 2026-09-29).
                stickyHeader(key = "leagues") {
                    StickyBar {
                        LeagueChips(Leagues.ALL, state.settings.leagues, onToggleLeague, Modifier.padding(vertical = 4.dp))
                        // Tj, 2026-09-27: "only show games that start within the next 24 hours or 12 hours or 48 hours".
                        if (AppBook.isNovig && state.result != null && state.settings.leagues.isNotEmpty()) {
                            Box(Modifier.padding(horizontal = 12.dp)) { StartsWithinRow(state.settings.startsWithinHours, onStartsWithin) }
                        }
                    }
                }
                // Only EVs whose other books' prices are still current (RESEARCH.md §24).
                val shown = state.feedAt(now)
                if (state.settings.paused) item(key = "paused") { PausedBanner({ onPause(false) }, Modifier.padding(horizontal = 12.dp)) }
                item(key = "summary") { FeedSummary(state, shown, now, onScan, onOpenSettings, onSort, onStartsWithin) { onRecheck(feedMarketIds(state, now)) } }
                if (removed.isNotEmpty() && state.settings.leagues.isNotEmpty()) {
                    item(key = "removed") {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            SetAsideList(removed, "you removed", "✕", "Put back", showRemoved, { showRemoved = !showRemoved }, onUnhide)
                        }
                    }
                }
                // ParlayAPI's picks, above Vigilant's own: read only on a tap (10 credits a league), each re-priced at Novig.
                if (parlayActions != null && state.canAskParlay && state.settings.leagues.isNotEmpty()) {
                    val picks = state.parlayShown(now)
                    item(key = "parlayHeader") { ParlayPicksHeader(state, picks.size, now, parlayActions, Modifier.padding(horizontal = 12.dp)) }
                    items(picks, key = { "parlay/" + it.key }) { p ->
                        ParlayPickCard(
                            p, state.settings, now, Modifier.padding(horizontal = 12.dp).animateItem(),
                            injury = state.injuries[p.key], actions = parlayActions,
                        )
                    }
                }
                items(shown, key = { it.key }) { o ->
                    OpportunityCard(o, state.settings, now, Modifier.padding(horizontal = 12.dp).animateItem(), onHide = { hide(o) }, injury = state.injuries[o.key], move = state.lineMoves[o.key]) { selected = o }
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
            injury = state.injuries[live.key],
        )
    }
}

@Composable
private fun FeedSummary(
    state: UiState,
    shown: List<Opportunity>,
    now: Long,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onSort: (FeedSort) -> Unit,
    onStartsWithin: (Int) -> Unit,
    onRecheck: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UsageStrip(state)
        state.status.errors.take(3).forEach { Banner(it, color = Edge.colors.negative) }
        val old = if (state.status.scanning) emptyList() else shown.filter { it.priceIsOld(now) }
        if (old.isNotEmpty()) {
            // Novig prices age: an edge from 20 minutes ago may be gone. Say so before Tj bets it,
            // and offer the few-second recheck rather than a whole scan.
            val oldest = old.mapNotNull { it.bookFetchedAtMs }.minOrNull()
            Banner(
                (if (old.size == shown.size) "These prices are" else "${old.size} of these prices are") +
                    " up to ${Format.age(oldest, now).removeSuffix(" ago")} old. Recheck them (a few seconds) or scan again before betting.",
                action = if (state.status.rechecking) null else "Recheck",
                onAction = onRecheck,
            )
        }
        // "Starts within" widened since the last scan, which read only its window: the new games need a scan.
        val scannedWindow = state.status.scannedWindowHours
        if (!state.status.scanning && state.result != null && scannedWindow != null && state.settings.scanWindowHours > scannedWindow) {
            Banner(
                "The last scan read games starting in ${windowLabel(scannedWindow)}. Scan to add the rest of ${windowLabel(state.settings.scanWindowHours)}.",
                action = "Scan",
                onAction = onScan,
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
        // The feed's bets, current enough to compare, but all outside the start-time window.
        val allLater = state.settings.startsWithinHours > 0 &&
            state.feed.any { !it.fairIsOld(now) } && state.feed.none { !it.fairIsOld(now) && state.settings.startsInWindow(it.event.startsTs, now) }
        when {
            state.settings.leagues.isEmpty() ->
                EmptyState("Pick a league", "Choose one or more leagues above, then tap Scan.")
            status.scanning && state.feed.isEmpty() -> EmptyState(
                "Scanning…",
                "Reading ${AppBook.name}'s board" + sourceNames(state).let { if (it.isEmpty()) "" else " and fair odds from $it" } + ". " +
                    "Bets appear here as ${AppBook.name}'s prices come in, likeliest edges first. " +
                    "You can switch apps: the scan keeps going and tells you when it's done.",
            )
            // Vigilant MGM reads BetMGM's own odds through these feeds: with neither, there's nothing to price.
            result == null && !AppBook.isNovig && state.proplineKeys.isEmpty() && state.oddsApiKeys.isEmpty() -> EmptyState(
                "Add a PropLine key",
                "Vigilant MGM reads ${AppBook.name}'s odds in the same requests as the other books': PropLine (free key, " +
                    "1,000 requests a day) or The Odds API. Add a key in Settings, then tap Scan.",
                action = "Settings",
                onAction = onOpenSettings,
            )
            result == null -> EmptyState(
                "Tap Scan to find +EV bets",
                "Nothing is downloaded until you ask: tap Scan or pull down. " +
                    sourceNames(state).let {
                        if (it.isEmpty()) "No fair-odds source is on (Settings), so a scan shows ${AppBook.name}'s prices only." else "Fair odds come from $it."
                    } +
                    if (state.pinnapiKeys.isEmpty() && state.pinnwireKeys.isEmpty() && state.settings.usePinnacle) " Add a free Pinnacle key in Settings for sharper lines." else "",
                action = "Scan now",
                onAction = onScan,
            )
            result.stats.matchedEvents == 0 && result.games.isNotEmpty() -> EmptyState(
                "No fair odds for these games",
                "${AppBook.name}'s prices for ${result.games.size} games are on the Games tab, but none of your fair-odds " +
                    "sources listed them this scan. Check the sources in Settings (a Pinnacle or PropLine key " +
                    "covers the most leagues).",
                action = "Fair odds settings",
                onAction = onOpenSettings,
            )
            allLater -> EmptyState(
                "Nothing starting in the next ${state.settings.startsWithinHours} hours",
                "Every +EV bet this scan found is on a game starting later. Widen the window to see them.",
                action = "Show any time",
                onAction = { onStartsWithin(0) },
            )
            state.feed.isNotEmpty() && shown.isEmpty() -> EmptyState(
                "Odds too old to compare",
                "The other books' prices behind these bets are too old to compare (${Freshness.LIMIT_TEXT}), so they're " +
                    "hidden: they could show +EV that isn't there any more. Scan for current odds.",
                action = "Scan now",
                onAction = onScan,
            )
            state.feed.isEmpty() -> EmptyState(
                "No +EV right now",
                "${result.stats.outcomesWithFair} prices checked across ${result.stats.matchedEvents} games " +
                    "starting in ${windowLabel(state.settings.scanWindowHours)}. " +
                    "Nothing at or above ${Format.percent(state.settings.minEvPercent)} EV. Scan again for fresh prices." +
                    laterGamesText(result.stats.laterGames, state.settings),
                action = if (result.stats.laterGames > 0) (if (state.settings.scanWindowHours < state.settings.daysAhead * 24) "Any time" else "Days ahead") else null,
                onAction = { if (state.settings.scanWindowHours < state.settings.daysAhead * 24) onStartsWithin(0) else onOpenSettings() },
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${shown.size} bet${if (shown.size == 1) "" else "s"} ≥ +${Format.percent(state.settings.minEvPercent)} EV · " +
                            "${result.stats.outcomesWithFair} checked" + if (status.scanning) " so far" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FeedSort.entries.forEach { sort ->
                        val on = state.settings.feedSort == sort
                        // Which one is picked is said to TalkBack too, not only drawn in bold.
                        TextButton(onClick = { onSort(sort) }, modifier = Modifier.semantics { selected = on }, contentPadding = PaddingValues(horizontal = 8.dp)) {
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
                agedOutText(state, now)?.let { note ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(note, style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning, modifier = Modifier.weight(1f))
                        if (!status.scanning) {
                            TextButton(onClick = onScan, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Scan", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Pinnacle, Polymarket, Kalshi and PropLine (The Odds API as backup)": the sources the next scan will
 * use, a backup named as one (RESEARCH.md §23).
 */
internal fun sourceNames(state: UiState): String {
    val s = state.settings
    val propLine = s.usePropLine && state.proplineKeys.isNotEmpty()
    val oddsApi = s.useOddsApi && state.oddsApiKeys.isNotEmpty()
    return buildList {
        if (s.usePinnacle && (state.pinnapiKeys.isNotEmpty() || state.pinnwireKeys.isNotEmpty())) add("Pinnacle")
        if (s.usePolymarket) add("Polymarket")
        if (s.useKalshi) add("Kalshi")
        if (propLine) add("PropLine")
        if (s.useParlay && state.parlayKeys.isNotEmpty()) add("ParlayAPI")
        if (oddsApi && !propLine) add("The Odds API")
    }.let { if (it.size <= 1) it.joinToString("") else it.dropLast(1).joinToString(", ") + " and " + it.last() } +
        if (oddsApi && propLine) " (The Odds API as backup)" else ""
}

/**
 * "Pinnacle 12 · Polymarket 14 · Kalshi 9 games": who matched what on the last scan, then any backup
 * API that wasn't needed ("The Odds API on standby": PropLine gave its books, RESEARCH.md §23).
 */
fun sourceSummary(status: ScanStatus): String {
    val matched = status.sources.filter { it.matched > 0 }.joinToString(" · ") { "${it.name} ${it.matched}" }.let { if (it.isEmpty()) it else "$it games" }
    val standby = status.sources.filter { it.standingBy > 0 && it.fetched == 0 && it.matched == 0 }.joinToString(" · ") { "${it.name} on standby" }
    return listOf(matched, standby).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** The feed's markets, best first: what "Recheck" re-reads (the scanner reads at most 40). */
/**
 * The markets a Recheck re-reads: the bets shown at [now] ([UiState.feedAt]), not the whole scan. A bet
 * hidden by the start-time window or by old odds would spend Novig requests (and the recheck's cap) on
 * lines Tj can't see, and one with old odds would turn the recheck into a full scan (full test, 2026-09-27).
 */
fun feedMarketIds(state: UiState, now: Long = System.currentTimeMillis()): List<String> = state.feedAt(now).map { it.market.marketId }.distinct()

fun fairSourceLabel(s: ScanSettings): String = when (s.fairSource) {
    FairSource.SHARP -> "sharp books"
    FairSource.MARKET_AVERAGE -> "market average"
    FairSource.BLEND -> "${(s.sharpWeight * 100).toInt()}% sharp blend"
} + ", ${s.devigMethod.displayName.lowercase()} devig"

@Composable
fun OpportunityCard(
    o: Opportunity,
    settings: ScanSettings,
    now: Long,
    modifier: Modifier = Modifier,
    /** Show the one-tap "Open in Novig" button (the +EV tab; off where a card is only a preview). */
    onOpen: Boolean = true,
    /** ✕ at the top right, as on CNO's cards: removes the bet for good. Null: no ✕ (a preview). */
    onHide: (() -> Unit)? = null,
    /** The player's injury report when he may not play (a prop bet): a tag after the pick. */
    injury: com.tjshea.vigilant.data.reference.Injury? = null,
    /** A team bet's game moved at Pinnacle (PARLAY_API.md §6.3): toward this side or against it. */
    move: com.tjshea.vigilant.data.reference.LineMove? = null,
    onClick: () -> Unit,
) {
    val q = o.quote ?: return
    val fair = o.fairProbability ?: return
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
                // How old its other books' prices are, once past a few minutes (they leave the feed at 5, or 10 for a
                // game more than 3 hours away: Freshness.maxAgeMs).
                o.fairAsOfMs?.let { now - it }?.takeIf { it > FAIR_AGING_MS }?.let { age ->
                    Text("odds ${age / 60_000} min old", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
                }
                // Top right, where CNO's cards have theirs.
                if (onHide != null) {
                    IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${o.selection} from the list", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            o.selection, Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        injury?.let { Spacer(Modifier.width(6.dp)); InjuryTag(it) }
                    }
                    Text(
                        "${o.marketLabel} · ${o.eventName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    move?.let { LineMoveNote(it) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(AppBook.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(Format.american(q.cost), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("Fair", "${Format.american(fair)} · ${Format.percent(fair)}")
                LabeledValue("Price", Format.percent(q.cost, 1))
                o.suggestedStake?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
            }
            val depth = o.depth
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(o.fair?.let { f -> f.booksUsed.take(3).joinToString(", ") + if (f.booksUsed.size > 3) " +${f.booksUsed.size - 3}" else "" } ?: "")
                        if (AppBook.exchange && depth != null && depth.contracts > 0) append("\n${Format.money(depth.dollarCost)} fillable at +EV")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // One tap to the bet slip, as the widget does (Tj, 2026-09-28); tapping the card still opens its details.
                if (onOpen) {
                    Spacer(Modifier.width(8.dp))
                    // Betting through Novig's API (Tj, 2026-09-29): only when it's set up in Settings.
                    ApiBetButton { it.betOpportunity(o) }
                    Spacer(Modifier.width(6.dp))
                    OpenBetButton(o, settings)
                }
            }
        }
    }
}

/** A card says how old its odds are past this: the other books' prices are on their way to [Freshness.maxAgeMs]. */
private const val FAIR_AGING_MS = 3 * 60_000L

/** "Starts within: Any time · 12h · 24h · 48h": the start-time window every list obeys (the +EV and CNO tabs). */
@Composable
internal fun StartsWithinRow(hours: Int, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Starts within",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ScanSettings.STARTS_WITHIN_CHOICES.forEach { h ->
            val on = hours == h
            TextButton(onClick = { onPick(h) }, modifier = Modifier.semantics { selected = on }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(startsWithinLabel(h), style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "7 days", "day" for 1. */
internal fun daysLabel(days: Int): String = if (days == 1) "day" else "$days days"

/**
 * What a scan left out because it starts past "Days ahead" (Tj, 2026-09-28: "There are way more than 7 total
 * games"): said, with where to change it. Empty when nothing was left out.
 */
internal fun laterGamesText(later: Int, settings: ScanSettings = ScanSettings()): String = when {
    later <= 0 -> ""
    // "Starts within" bounded the scan (Tj, 2026-09-28: "stop the scan when all the markets are finished scanning for the selected time period").
    settings.scanWindowHours < settings.daysAhead.coerceAtLeast(1) * 24 ->
        " $later more game${if (later == 1) "" else "s"} on Novig start later than that; widen Starts within and scan again to add them."
    else -> " $later more game${if (later == 1) "" else "s"} on Novig start later than that; raise Days ahead in Settings to scan them."
}

/**
 * Why bets left the list with no scan (Tj, 2026-09-28: "they quickly disappeared"): the other books' prices
 * behind them passed the 5-minute limit (RESEARCH.md §24). Said, instead of the list silently shrinking.
 * Null when none did.
 */
internal fun agedOutText(state: UiState, now: Long): String? {
    val aged = state.feed.count { it.fairIsOld(now) && state.settings.startsInWindow(it.event.startsTs, now) }
    if (aged == 0) return null
    return "$aged bet${if (aged == 1) "" else "s"} hidden: the other books' odds behind ${if (aged == 1) "it" else "them"} " +
        "are too old (${com.tjshea.vigilant.data.scanner.Freshness.LIMIT_TEXT}). Scan for current odds."
}
