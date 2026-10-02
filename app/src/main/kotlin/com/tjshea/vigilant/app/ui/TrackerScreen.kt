package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.CheckOddsStats
import androidx.compose.material3.Switch
import com.tjshea.vigilant.data.tracker.ClvStats
import com.tjshea.vigilant.data.tracker.ClvPeriod
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.TrackerBreakdown
import com.tjshea.vigilant.engine.Odds
import java.time.Instant
import java.time.ZoneId

/** The Tracker's two halves (Tj, 2026-09-27: "make a stats section"). */
enum class TrackerView(val label: String) { STATS("Stats"), BETS("Bets") }

/** How far back the stats look, by when each bet was placed. */
enum class TrackerPeriod(val label: String) { ALL("All"), DAYS_30("30 days"), DAYS_7("7 days"), TODAY("Today") }

enum class BetFilter(val label: String) { OPEN("Open"), SETTLED("Settled"), ALL("All") }

/** [bets] placed within [period] of [nowMs] ("Today": since local midnight). */
fun inPeriod(bets: List<TrackedBet>, period: TrackerPeriod, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<TrackedBet> {
    val from = when (period) {
        TrackerPeriod.ALL -> return bets
        TrackerPeriod.DAYS_30 -> nowMs - 30L * 86_400_000L
        TrackerPeriod.DAYS_7 -> nowMs - 7L * 86_400_000L
        TrackerPeriod.TODAY -> Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
    return bets.filter { it.createdAtMs >= from }
}

/** What each bet's buttons do (the Tracker's own state is in [UiState]; these are the ViewModel's and the Activity's). */
data class BetActions(
    /** Replace: Novig's bet slip on this exact bet, with Settings' amount filled in. */
    val onReplace: (TrackedBet) -> Unit = {},
    /** Re-read one bet's books (id, quiet: no word when it can't be read). */
    val onReread: (String, Boolean) -> Unit = { _, _ -> },
    /** "Grade now": every open bet against the final scores again. */
    val onGrade: () -> Unit = {},
    /** Turn auto-grading back on for a bet whose result was undone. */
    val onRegrade: (String) -> Unit = {},
    /** Correct the American odds a bet was filled at. */
    val onPrice: (String, Int) -> Unit = { _, _ -> },
    /** Betting through the API is set up: add what Novig filled that the Tracker doesn't have. */
    val onSync: () -> Unit = {},
    /** Lock in a bet's profit (market id, the profit Tj confirmed): RESEARCH.md §67. */
    val onLock: (String, Double) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TrackerScreen(
    state: UiState,
    onSettle: (String, BetStatus) -> Unit,
    onDelete: (String) -> Unit,
    onStake: (String, Double) -> Unit = { _, _ -> },
    onCheckOdds: () -> Unit = {},
    onShown: () -> Unit = {},
    initialView: TrackerView = TrackerView.STATS,
    actions: BetActions = BetActions(),
    /** The "Novig only" filter switched (Tj, 2026-10-02 ~18:50Z), and its own read of Novig's prices (no other book). */
    onNovigOnly: (Boolean) -> Unit = {},
    onCheckNovig: () -> Unit = {},
) {
    val now = rememberNow(60_000)
    var view by rememberSaveable { mutableStateOf(initialView) }
    var period by rememberSaveable { mutableStateOf(TrackerPeriod.ALL) }
    var filter by rememberSaveable { mutableStateOf(BetFilter.OPEN) }
    // How the bets are ordered and which scanner's are listed (Tj, 2026-09-29): kept through rotation and the app being recreated.
    var sort by rememberSaveable { mutableStateOf(BetSort.DEFAULT) }
    var sortReversed by rememberSaveable { mutableStateOf(false) }
    var scanner by rememberSaveable { mutableStateOf(ScannerFilter.ALL) }
    var breakdownBy by rememberSaveable { mutableStateOf(TrackerBreakdown.By.LEAGUE) }
    // The closing-line card's own period and outlier switch (Tj, 2026-09-29).
    var clvPeriod by rememberSaveable { mutableStateOf(ClvPeriod.ALL) }
    var clvHideOutliers by rememberSaveable { mutableStateOf(false) }
    // The open bet's sheet, by id, so a recheck that updates the bet updates the sheet, and deleting closes it.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<TrackedBet?>(null) }
    var editStake by remember { mutableStateOf<TrackedBet?>(null) }
    var editPrice by remember { mutableStateOf<TrackedBet?>(null) }
    // Results of finished games from their final scores, each time the tab is opened (no network when nothing is due).
    LaunchedEffect(Unit) { onShown() }

    // "Novig only": every open bet's EV and every closing line from Novig's own prices ([NovigNow.view]); the rest of the screen is unchanged.
    val novigOnly = state.settings.trackerNovigOnly
    val bets = remember(state.bets, novigOnly) { if (novigOnly) com.tjshea.vigilant.data.tracker.NovigNow.view(state.bets) else state.bets }
    val minute = now / 60_000L
    val scoped = remember(bets, scanner) { TrackerSort.inScanner(bets, scanner) }
    val counts = remember(scoped) { BetFilter.entries.associateWith { f -> filtered(scoped, f).size } }
    val scannerCounts = remember(bets, filter) { ScannerFilter.entries.associateWith { s -> TrackerSort.inScanner(filtered(bets, filter), s).size } }
    val shown = remember(scoped, filter, minute, sort, sortReversed) {
        TrackerSort.sorted(filtered(scoped, filter), sort, sortReversed) { ordered(it, filter, now) }
    }
    val periodBets = remember(bets, period, minute) { inPeriod(bets, period, now) }
    // The "Check odds now" counter: open bets re-read since the last check began (0 again at each new one), live as batches are saved; a game
    // that starts drops out within the minute.
    val checkStart = state.checkStartedAtMs
    val checkStats = remember(bets, checkStart, minute) { checkStart?.let { CheckOddsStats.of(bets, it, now) } }
    val openBet = openId?.let { id -> bets.firstOrNull { it.id == id } }
    LaunchedEffect(openId, openBet == null) { if (openId != null && openBet == null) openId = null }
    // A different list (tab, filter, sort, scanner, period) starts at its top, with the pinned tabs and filters just above it; the first
    // composition, and one restored after a rotation, keep their place.
    val listState = rememberLazyListState()
    var shownKey by remember { mutableStateOf<List<Any>?>(null) }
    val listKey = listOf(view, period, filter, sort, sortReversed, scanner)
    LaunchedEffect(listKey) {
        if (shownKey != null && shownKey != listKey) listState.scrollToItem(0)
        shownKey = listKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bet tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    if (novigOnly) {
                        TextButton(onClick = onCheckNovig, enabled = !state.readingNovig, modifier = Modifier.testTag("checkNovig")) {
                            Text(if (state.readingNovig) "Reading Novig…" else "Check Novig now")
                        }
                    } else TextButton(onClick = onCheckOdds, enabled = !state.checkingOdds) {
                        val progress = state.checkProgress
                        Text(
                            when {
                                progress != null && progress.second > 0 -> "Checking ${progress.first}/${progress.second}…"
                                state.checkingOdds -> "Checking…"
                                else -> "Check odds now"
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Check odds now holds the focus (Tj, 2026-10-01): what waits for it, said where Tj is looking.
            if (state.checkingOdds) {
                item(key = "checkFocus") {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("checkFocusBanner"),
                    ) {
                        Text(
                            "Checking your open bets' odds, EV and closing lines. The CNO scanner, background auto-scan (auto-bet with it), scans and the widget's refresh are paused until this finishes.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            // The tabs and filters stay pinned to the top while the bets scroll under them (Tj, 2026-09-29: "When I scroll down through the
            // long list of my active bets, I still want to have the filters at the top without having to scroll all the way back up").
            stickyHeader(key = "nav") {
                StickyBar {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        TrackerView.entries.forEachIndexed { i, v ->
                            SegmentedButton(
                                selected = view == v,
                                onClick = { view = v },
                                shape = SegmentedButtonDefaults.itemShape(i, TrackerView.entries.size),
                            ) { Text(v.label, maxLines = 1) }
                        }
                    }
                    // One row, so the pinned bar stays compact; what it counts is said just below, in the list.
                    if (checkStats != null) CheckOddsCounter(checkStats)
                    if (AppBook.isNovig) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = novigOnly,
                                onClick = { onNovigOnly(!novigOnly) },
                                label = { Text("Novig only") },
                                modifier = Modifier.testTag("novigOnlyChip"),
                            )
                            if (novigOnly) {
                                Text(
                                    TrackerText.novigOnlyNote(state.bets, now),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).testTag("novigOnlyNote"),
                                )
                            }
                        }
                    }
                    when (view) {
                        TrackerView.STATS -> Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrackerPeriod.entries.forEach { p ->
                                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                            }
                        }
                        TrackerView.BETS -> Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BetFilter.entries.forEach { f ->
                                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} (${counts[f] ?: 0})") })
                                }
                            }
                            // Sort and scanner are menus here, so the bar stays two chip rows tall while it is pinned (wrapped chips took a third of the screen).
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MenuChip(TrackerSort.barLabel(sort, sortReversed, defaultLabel(filter)), active = sort != BetSort.DEFAULT, modifier = Modifier.weight(1.3f)) { close ->
                                    // Tap a sort to choose it, tap it again to turn it round (newest / oldest first, best / worst EV first).
                                    BetSort.entries.forEach { s ->
                                        MenuChoice(
                                            TrackerSort.chipLabel(s, sort, sortReversed, defaultLabel(filter)), selected = sort == s,
                                            onClick = {
                                                if (sort == s && s != BetSort.DEFAULT) sortReversed = !sortReversed else { sort = s; sortReversed = false }
                                                close()
                                            },
                                        )
                                    }
                                }
                                MenuChip("Scanner: ${scanner.short}", active = scanner != ScannerFilter.ALL, modifier = Modifier.weight(1f)) { close ->
                                    ScannerFilter.entries.forEach { s ->
                                        MenuChoice("${s.label} (${scannerCounts[s] ?: 0})", selected = scanner == s, onClick = { scanner = s; close() })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (checkStats != null && checkStart != null) {
                item(key = "checkCaption") {
                    Text(
                        TrackerText.checkCaption(checkStats, state.checkingOdds, state.checkProgress, checkStart, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).testTag("checkCaption"),
                    )
                }
            }
            when (view) {
                TrackerView.STATS -> {
                    if (periodBets.isEmpty()) {
                        item(key = "empty") { EmptyState("No bets ${if (period == TrackerPeriod.ALL) "tracked yet" else "in this period"}", EMPTY_HINT) }
                    } else {
                        item(key = "stats") {
                            StatsCards(periodBets, breakdownBy, onBreakdown = { breakdownBy = it }) {
                                ClosingLineCard(bets, now, clvPeriod, { clvPeriod = it }, clvHideOutliers, { clvHideOutliers = it })
                            }
                        }
                    }
                }
                TrackerView.BETS -> {
                    if (filter == BetFilter.OPEN && shown.isNotEmpty()) {
                        item(key = "summary") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(TrackerText.openSummary(shown, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // The bets whose game started come first: results to grade, or a tap to give.
                                if (shown.any { now >= it.startsTs }) {
                                    TextButton(onClick = actions.onGrade, enabled = !state.gradingBets, contentPadding = PaddingValues(0.dp)) {
                                        Text(if (state.gradingBets) "Grading…" else "Grade now: read the final scores again")
                                    }
                                }
                                if (state.betting.enabled) {
                                    TextButton(onClick = actions.onSync, contentPadding = PaddingValues(0.dp)) { Text("Sync with Novig's fills") }
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(if (bets.isEmpty()) "No bets tracked yet" else "No ${filter.label.lowercase()} bets", EMPTY_HINT)
                        }
                    }
                    items(shown, key = { it.id }) { bet ->
                        BetCard(
                            bet, now,
                            replacing = state.replacingBet == bet.id,
                            onOpen = { openId = bet.id },
                            onReplace = { actions.onReplace(bet) },
                            onSettle = onSettle,
                            onRegrade = { actions.onRegrade(bet.id) },
                            onStake = { editStake = bet },
                            onDelete = { confirmDelete = bet },
                            injury = state.injuries[com.tjshea.vigilant.data.reference.InjuryTags.betKey(bet)],
                            move = state.lineMoves[com.tjshea.vigilant.data.reference.InjuryTags.betKey(bet)],
                            lock = if (bet.viaApi && bet.status == BetStatus.PENDING) state.locks[bet.marketId] else null,
                        )
                    }
                }
            }
        }
    }

    openBet?.let { bet ->
        BetSheet(
            bet, now, state.settings,
            rereading = state.rereadingBet == bet.id,
            grading = state.gradingBets,
            replacing = state.replacingBet == bet.id,
            actions = actions,
            onSettle = { onSettle(bet.id, it) },
            onStake = { editStake = bet },
            onPrice = { editPrice = bet },
            onDelete = { confirmDelete = bet },
            onDismiss = { openId = null },
            injury = state.injuries[com.tjshea.vigilant.data.reference.InjuryTags.betKey(bet)],
            lock = if (bet.viaApi) state.locks[bet.marketId] else null,
            locking = state.locking == bet.marketId,
        )
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
    editStake?.let { bet -> StakeDialog(bet, onSave = { onStake(bet.id, it); editStake = null }, onDismiss = { editStake = null }) }
    editPrice?.let { bet -> PriceDialog(bet, onSave = { actions.onPrice(bet.id, it); editPrice = null }, onDismiss = { editPrice = null }) }
}

private const val EMPTY_HINT = "Tap ✓ on a bet in the widget or the CNO tab (or Track on a +EV card, or ✓ Placed on a +EV alert) and it's logged here, \$1 unless you change it."

private fun filtered(bets: List<TrackedBet>, f: BetFilter) = when (f) {
    BetFilter.OPEN -> bets.filter { it.status == BetStatus.PENDING }
    BetFilter.SETTLED -> bets.filter { it.status != BetStatus.PENDING }
    BetFilter.ALL -> bets
}

/** A chip that opens a menu of choices ([items] gets a function that closes it): "Sort: Current EV", "Scanner: CNO". */
@Composable
private fun MenuChip(label: String, active: Boolean, modifier: Modifier = Modifier, items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

/** One choice in a [MenuChip]'s menu, ticked when it is the one in force. */
@Composable
private fun MenuChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = "selected", Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/** What the default order is called on each list. */
internal fun defaultLabel(f: BetFilter): String = when (f) {
    BetFilter.OPEN -> "Needs a look"
    BetFilter.SETTLED -> "Latest result"
    BetFilter.ALL -> "Latest placed"
}

/** Open bets: the ones that need a look first ([TrackerText.openOrder]); settled: latest result first; all: latest placed first. */
private fun ordered(bets: List<TrackedBet>, f: BetFilter, now: Long): List<TrackedBet> = when (f) {
    BetFilter.OPEN -> TrackerText.openOrder(bets, now)
    BetFilter.SETTLED -> bets.sortedByDescending { it.settledAtMs ?: it.createdAtMs }
    BetFilter.ALL -> bets.sortedByDescending { it.createdAtMs }
}

@Composable
internal fun moneyColor(v: Double): Color = when {
    v > 0 -> Edge.colors.positive
    v < 0 -> Edge.colors.negative
    else -> Color.Unspecified
}

@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatsCards(
    bets: List<TrackedBet>,
    by: TrackerBreakdown.By,
    onBreakdown: (TrackerBreakdown.By) -> Unit,
    /** The closing-line card's own filters, and every bet (it runs over all time, whatever the period at the top). */
    clv: @Composable () -> Unit = {},
) {
    val now = rememberNow(60_000)
    val stats = remember(bets, now / 60_000L) { BetTracker.stats(bets, now) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stats.outliers > 0) {
            Caption(
                "${stats.outliers} outlier bet${if (stats.outliers == 1) "" else "s"} (over ±${Format.percent(BetTracker.OUTLIER_EV, 0)} EV when bet) " +
                    "left out of every number here, so one odd bet can't skew them. ${if (stats.outliers == 1) "It's" else "They're"} still under Bets.",
            )
        }
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Profit", Format.signedMoney(stats.profit), valueColor = moneyColor(stats.profit))
                LabeledValue("Profit %", stats.roi?.let { Format.evPercent(it) } ?: "—", valueColor = moneyColor(stats.roi ?: 0.0))
                LabeledValue("Staked", Format.money(stats.staked))
            }
            ProfitLine(bets)
            Caption("Profit % is profit over money staked on settled bets (ROI): it runs with every result.")
            if (stats.outliers > 0) Caption("With the outliers counted too, your bankroll's result is ${Format.signedMoney(stats.profitAll)}.")
        }
        clv()
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Win %", stats.winRate?.let { Format.percent(it, 1) } ?: "—")
                LabeledValue("Record", "${stats.won}-${stats.lost}" + if (stats.pushed > 0) "-${stats.pushed}" else "")
                LabeledValue("Open", "${stats.pending}")
            }
            Caption(
                "Wins out of bets won or lost (pushes aside" + (if (stats.voided > 0) "; ${stats.voided} voided" else "") + "). " +
                    "Results come from each game's final score (ESPN, MLB) once it's over; tap a bet to see how it was graded, or to fix it.",
            )
        }
        if (stats.pending > 0) {
            StatsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LabeledValue("At risk", Format.money(stats.openStaked))
                    LabeledValue("Pays if all win", Format.money(stats.openToWin))
                    LabeledValue("Expected", Format.signedMoney(stats.openExpected), valueColor = moneyColor(stats.openExpected))
                }
                Caption("The ${stats.pending} open bet${if (stats.pending == 1) "" else "s"}: money on them, what they'd pay, and what their edges say they're worth (not in Profit yet).")
            }
        }
        StatsCard {
            CardTitle("Are the edges real?")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Expected", Format.signedMoney(stats.expectedProfit))
                LabeledValue("Actual", Format.signedMoney(stats.profitWithEv), valueColor = moneyColor(stats.profitWithEv))
                LabeledValue("Difference", Format.signedMoney(stats.vsExpected), valueColor = moneyColor(stats.vsExpected))
            }
            Text(TrackerText.luckMessage(stats), style = MaterialTheme.typography.bodySmall)
            stats.averageEv?.let { Text("Average EV when bet: ${Format.evPercent(it)} (these ${stats.bets} bets, outliers aside).", style = MaterialTheme.typography.bodySmall) }
            Caption("Both numbers count the same ${stats.settledWithEv} won and lost bet${if (stats.settledWithEv == 1) "" else "s"}: what their EVs promised, and what they actually paid. Bets with no EV on record, pushes and voids aren't in either.")
        }
        BreakdownCard(bets, by, onBreakdown)
    }
}

/** The same numbers split by scanner, league, kind of market, edge size or price ([TrackerBreakdown]): where the edge is real. */
@Composable
private fun BreakdownCard(bets: List<TrackedBet>, by: TrackerBreakdown.By, onBy: (TrackerBreakdown.By) -> Unit) {
    val rows = remember(bets, by) { TrackerBreakdown.of(bets, by) }
    StatsCard {
        CardTitle("Where it's working")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TrackerBreakdown.By.entries.forEach { b -> FilterChip(selected = by == b, onClick = { onBy(b) }, label = { Text(b.label) }) }
        }
        rows.forEach { r ->
            val s = r.stats
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(Format.signedMoney(s.profit), Modifier.weight(1f), color = moneyColor(s.profit), style = MaterialTheme.typography.bodyMedium)
                    Text(s.roi?.let { Format.evPercentShort(it) } ?: "—", Modifier.weight(0.8f), color = moneyColor(s.roi ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                }
                Caption(
                    "${s.bets} bet${if (s.bets == 1) "" else "s"} · ${s.won}-${s.lost}" + (if (s.pushed > 0) "-${s.pushed}" else "") +
                        (if (s.pending > 0) " · ${s.pending} open" else "") +
                        (s.averageEv?.let { " · avg EV ${Format.evPercentShort(it)}" } ?: "") +
                        (s.averageClv?.let { " · CLV ${Format.evPercentShort(it)}" } ?: ""), // true closes only (BetTracker.stats)
                )
            }
        }
        Caption("Profit and profit % (ROI) per group. A group of a few bets says little: look at the big ones, and at CLV, which needs far fewer bets than profit.")
    }
}

/** Running profit, one step per settled bet in the order the games were played (outliers left out): green above zero, red below. */
@Composable
private fun ProfitLine(bets: List<TrackedBet>) {
    val points = remember(bets) {
        val settled = bets.filter { it.status != BetStatus.PENDING && it.status != BetStatus.VOID && !it.isOutlier }.sortedBy { it.startsTs }
        if (settled.size < 2) emptyList() else settled.runningFold(0.0) { acc, b -> acc + (b.profit ?: 0.0) }
    }
    if (points.isEmpty()) return
    val color = if (points.last() >= 0) Edge.colors.positive else Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Running profit over ${points.size - 1} settled bets" }) {
        val lo = minOf(0.0, points.min())
        val hi = maxOf(0.0, points.max())
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - lo) / span)).toFloat()
        val dx = size.width / (points.size - 1)
        drawLine(axis, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * dx, y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BetCard(
    bet: TrackedBet,
    now: Long,
    replacing: Boolean,
    onOpen: () -> Unit,
    onReplace: () -> Unit,
    onSettle: (String, BetStatus) -> Unit,
    onRegrade: () -> Unit,
    onStake: () -> Unit,
    onDelete: () -> Unit,
    /** An open prop bet's player may not play (PARLAY_API.md §6.1): a tag after the pick. */
    injury: com.tjshea.vigilant.data.reference.Injury? = null,
    /** An open team bet's game moved at Pinnacle (PARLAY_API.md §6.3). */
    move: com.tjshea.vigilant.data.reference.LineMove? = null,
    /** The lock on this bet's market, when it was placed through the API (RESEARCH.md §67). */
    lock: com.tjshea.vigilant.app.LockView? = null,
) {
    val open = bet.status == BetStatus.PENDING
    val awaiting = TrackerText.awaiting(bet, now)
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bet.selection, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (open) injury?.let { Spacer(Modifier.width(6.dp)); InjuryTag(it) }
                    }
                    Text(
                        listOf(bet.marketLabel, bet.league, if (open) TrackerText.startsIn(bet.startsTs, now) else Format.shortDate(bet.startsTs)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(bet.eventName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (open) move?.let { LineMoveNote(it) }
                    Text(
                        TrackerSort.scannerOf(bet).short +
                            " · placed ${Format.placedAt(bet.createdAtMs)}" +
                            (if (bet.book != AppBook.name) " · ${bet.book}" else "") +
                            (if (bet.viaApi) (if (bet.imported) " · found in Novig's fills" else if (bet.auto) " · auto-bet through Novig's API" else " · placed through Novig's API") else "") +
                            (if (bet.imported && !bet.viaApi) " · from an earlier ✓" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LockText.badge(lock)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Edge.colors.positive, modifier = Modifier.testTag("lockBadge"))
                    }
                    if (bet.isLock) {
                        Text("🔒 A lock: the other side of an earlier bet (not in the record, EV or CLV)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (bet.isOutlier) {
                        Text(
                            "Outlier (over ±${Format.percent(BetTracker.OUTLIER_EV, 0)} EV): not in stats",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete bet") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (bet.viaApi) LabeledValue("Stake", Format.money(bet.stake))
                else LabeledValue("Stake ✎", Format.money(bet.stake), Modifier.clickable(onClickLabel = "Change the stake", onClick = onStake))
                LabeledValue("Price", bet.american?.let { Odds.formatAmerican(it) } ?: Format.american(bet.price))
                LabeledValue("EV at bet", bet.evPercentAtBet?.let { Format.evPercent(it) } ?: "—")
                // The true CLV once the game has started with a close read just before it; until then the "now" line below is the one to watch.
                val clv = ClosingLine.clv(bet, now)
                LabeledValue("CLV", clv?.let { Format.evPercent(it) } ?: "—", valueColor = clv?.let { moneyColor(it) } ?: Color.Unspecified)
                LabeledValue(
                    if (open) "To win" else "Result",
                    bet.profit?.let { Format.signedMoney(it) } ?: Format.money(bet.profitIfWon),
                    valueColor = bet.profit?.let { moneyColor(it) } ?: Color.Unspecified,
                )
            }
            if (open) {
                // The EV the fair odds now (devigged) give the price this bet was placed at; "now" only while the read is young.
                TrackerText.nowLine(bet, now)?.let { line ->
                    val ev = bet.nowEv!!
                    Text(
                        line.headline,
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            line.stale -> MaterialTheme.colorScheme.onSurfaceVariant
                            ev >= 0 -> Edge.colors.positive
                            else -> Edge.colors.negative
                        },
                    )
                    Text(line.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open) {
                TrackerText.oddsNote(bet, now)?.let { note ->
                    Text(
                        note,
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            awaiting?.let { st ->
                Text(
                    st.text,
                    Modifier.padding(top = 6.dp, end = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (st.tone == TrackerText.Tone.ATTENTION) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (open) {
                    FilledTonalButton(
                        onClick = onReplace,
                        enabled = !replacing,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(34.dp),
                    ) { Text(if (replacing) "Opening…" else "Replace") }
                    if (bet.autoGradeOff) TextButton(onClick = onRegrade) { Text("Grade automatically") }
                    // A game that hasn't started has no result to give.
                    if (now >= bet.startsTs) {
                        AssistChip(onClick = { onSettle(bet.id, BetStatus.WON) }, label = { Text("Won") })
                        AssistChip(onClick = { onSettle(bet.id, BetStatus.LOST) }, label = { Text("Lost") })
                        AssistChip(onClick = { onSettle(bet.id, BetStatus.PUSH) }, label = { Text("Push") })
                        AssistChip(onClick = { onSettle(bet.id, BetStatus.VOID) }, label = { Text("Void") })
                    }
                } else {
                    val name = if (bet.status == BetStatus.FMV) "Fair value" else bet.status.name.lowercase().replaceFirstChar { it.uppercase() }
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.PENDING) }, label = { Text("$name · undo") })
                    if (bet.settledBy == BetSettler.BY_SCORES || bet.settledBy == BetSettler.BY_NOVIG) {
                        Text(
                            bet.gradeNote ?: "from the final score",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StakeDialog(bet: TrackedBet, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", bet.stake)) }
    val value = text.replace(",", "").removePrefix("$").trim().toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1_000_000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stake") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${bet.selection} · what you bet on ${bet.book.ifBlank { AppBook.name }}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    prefix = { Text("$") },
                    isError = value == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** American odds typed as "+150", "-110" or "−110" (the app's own minus sign); null when it isn't a price (inside ±100, or not a number). */
internal fun parseAmerican(text: String): Int? =
    text.trim().replace("−", "-").removePrefix("+").toIntOrNull()?.takeIf { it >= 100 || it <= -100 }

/** The price a bet was really filled at (an alert's or a ✓'s price can differ): profit and EV follow. */
@Composable
private fun PriceDialog(bet: TrackedBet, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(bet.american?.let { Odds.formatAmerican(it) } ?: "") }
    val value = parseAmerican(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price you got") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${bet.selection}: the American odds you were actually filled at on ${bet.book.ifBlank { AppBook.name }}. Profit, EV and CLV follow.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = value == null,
                    supportingText = { if (value == null) Text("Odds like +150 or −110") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The "Check odds now" counter, pinned at the top of the Tracker (Tj, 2026-09-29): how many open bets are +EV and −EV right now at the price
 * they were placed, the share that's +EV, and their average EV with the ones over ±5% left out ([CheckOddsStats]). It starts at 0 with each new
 * check and counts up as the refreshed odds are saved. One row: [TrackerText.checkCaption] says what it counts, below the pinned bar.
 */
@Composable
fun CheckOddsCounter(stats: CheckOddsStats, modifier: Modifier = Modifier) {
    val said = TrackerText.checkCounts(stats) + " · " + TrackerText.checkAverage(stats)
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = said }.testTag("checkCounter"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CounterValue(stats.positive.toString(), "+EV", Edge.colors.positive)
        CounterValue(stats.negative.toString(), "−EV", Edge.colors.negative)
        CounterValue(stats.positiveShare?.let { Format.percent(it, 0) } ?: "–", "+EV", MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        val avg = stats.averageEv
        CounterValue(
            avg?.let(Format::evPercentShort) ?: "–", "avg EV",
            when {
                avg == null -> MaterialTheme.colorScheme.onSurface
                avg >= 0 -> Edge.colors.positive
                else -> Edge.colors.negative
            },
        )
    }
}

@Composable
private fun CounterValue(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(" $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/**
 * Closing line value (Tj, 2026-09-29: "the percentage of my bets that beat closing line value ... the average percentage that my bets beat the
 * closing line ... Keep this stat line running forever, it does not reset ... a filter system ... (all time, today, yesterday, last 3 days, last
 * week), and an option to remove outliers (bets over 5% different than closing line value)"). Over every bet ever tracked ([ClvStats]), with
 * its own period chips (by when each bet was placed) and outlier switch; only true closes count ([ClosingLine]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClosingLineCard(
    allBets: List<TrackedBet>,
    now: Long,
    period: ClvPeriod,
    onPeriod: (ClvPeriod) -> Unit,
    hideOutliers: Boolean,
    onHideOutliers: (Boolean) -> Unit,
) {
    val s = remember(allBets, now / 60_000L, period, hideOutliers) { ClvStats.of(allBets, now, period, hideOutliers) }
    StatsCard(Modifier.testTag("clvCard")) {
        CardTitle("Closing line value")
        // Wrapped, so all five periods are in sight.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClvPeriod.entries.forEach { p ->
                FilterChip(selected = period == p, onClick = { onPeriod(p) }, label = { Text(p.label) }, modifier = Modifier.testTag("clvPeriod-${p.name}"))
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Hide outliers") { onHideOutliers(!hideOutliers) }.testTag("clvOutliers"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hide outliers (over ±${Format.percent(ClosingLine.OUTLIER_CLV, 0)} from the close)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hideOutliers, onCheckedChange = onHideOutliers)
        }
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = TrackerText.clvLine(s) }.testTag("clvValues"),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LabeledValue("Beat the close", s.beatShare?.let { "${Format.percent(it, 0)} (${s.beat}/${s.closed})" } ?: "—")
            LabeledValue("Avg vs close", s.averageClv?.let { Format.evPercent(it) } ?: "—", valueColor = s.averageClv?.let { moneyColor(it) } ?: Color.Unspecified)
            LabeledValue("Avg EV at bet", s.averageEvAtBet?.let { Format.evPercent(it) } ?: "—")
        }
        Caption(TrackerText.clvCounts(s))
        Caption(
            "The close is the devigged fair line read in the last ${ClosingLine.TRUE_CLOSE_MS / 60_000} minutes before the start: Vigilant reads it about " +
                "${ClosingLine.LEAD_MS / 60_000} minutes before each of your games and again about 2 minutes before, even when it's closed (the later read wins). " +
                "A bet is never \"closed\" at the price you bet it at, however late you placed it. If the phone was off, the real close is found afterwards, " +
                "hours or days later: Pinnacle's closing lines from ParlayAPI first (with a key: game lines and props, the sharpest close), ESPN's " +
                "closing odds (moneylines, spreads, totals) right after the start, and Novig's own last trades before the start (every market, props " +
                "too) the next morning. \"Avg vs close\" is how much better your odds were than the closing odds, " +
                "averaged over the bets. Beating the close is the best early sign your edges are real. This card has its own period; the one at the " +
                "top doesn't change it.",
        )
    }
}
