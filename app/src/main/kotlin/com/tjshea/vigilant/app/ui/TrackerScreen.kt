package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
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
)

@OptIn(ExperimentalMaterial3Api::class)
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
    // The open bet's sheet, by id, so a recheck that updates the bet updates the sheet, and deleting closes it.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<TrackedBet?>(null) }
    var editStake by remember { mutableStateOf<TrackedBet?>(null) }
    var editPrice by remember { mutableStateOf<TrackedBet?>(null) }
    // Results of finished games from their final scores, each time the tab is opened (no network when nothing is due).
    LaunchedEffect(Unit) { onShown() }

    val bets = state.bets
    val minute = now / 60_000L
    val scoped = remember(bets, scanner) { TrackerSort.inScanner(bets, scanner) }
    val counts = remember(scoped) { BetFilter.entries.associateWith { f -> filtered(scoped, f).size } }
    val scannerCounts = remember(bets, filter) { ScannerFilter.entries.associateWith { s -> TrackerSort.inScanner(filtered(bets, filter), s).size } }
    val shown = remember(scoped, filter, minute, sort, sortReversed) {
        TrackerSort.sorted(filtered(scoped, filter), sort, sortReversed) { ordered(it, filter, now) }
    }
    val periodBets = remember(bets, period, minute) { inPeriod(bets, period, now) }
    val openBet = openId?.let { id -> bets.firstOrNull { it.id == id } }
    LaunchedEffect(openId, openBet == null) { if (openId != null && openBet == null) openId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bet tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onCheckOdds, enabled = !state.checkingOdds) {
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
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "switch") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TrackerView.entries.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = view == v,
                            onClick = { view = v },
                            shape = SegmentedButtonDefaults.itemShape(i, TrackerView.entries.size),
                        ) { Text(v.label, maxLines = 1) }
                    }
                }
            }
            when (view) {
                TrackerView.STATS -> {
                    item(key = "periods") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TrackerPeriod.entries.forEach { p ->
                                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                            }
                        }
                    }
                    if (periodBets.isEmpty()) {
                        item(key = "empty") { EmptyState("No bets ${if (period == TrackerPeriod.ALL) "tracked yet" else "in this period"}", EMPTY_HINT) }
                    } else {
                        item(key = "stats") { StatsCards(periodBets, breakdownBy, onBreakdown = { breakdownBy = it }) }
                    }
                }
                TrackerView.BETS -> {
                    item(key = "filters") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BetFilter.entries.forEach { f ->
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} (${counts[f] ?: 0})") })
                            }
                        }
                    }
                    item(key = "sort") {
                        // Tap a sort to choose it, tap it again to turn it round (newest / oldest first, best / worst EV first).
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ChipCaption("Sort")
                            BetSort.entries.forEach { s ->
                                FilterChip(
                                    selected = sort == s,
                                    onClick = {
                                        if (sort == s && s != BetSort.DEFAULT) sortReversed = !sortReversed else { sort = s; sortReversed = false }
                                    },
                                    label = { Text(TrackerSort.chipLabel(s, sort, sortReversed, defaultLabel(filter)), maxLines = 1) },
                                )
                            }
                        }
                    }
                    item(key = "scanner") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ChipCaption("Scanner")
                            ScannerFilter.entries.forEach { s ->
                                FilterChip(selected = scanner == s, onClick = { scanner = s }, label = { Text("${s.label} (${scannerCounts[s] ?: 0})", maxLines = 1) })
                            }
                        }
                    }
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
private fun StatsCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
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
private fun StatsCards(bets: List<TrackedBet>, by: TrackerBreakdown.By, onBreakdown: (TrackerBreakdown.By) -> Unit) {
    val stats = remember(bets) { BetTracker.stats(bets) }
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
            Caption("Both numbers count the same ${stats.settledWithEv} won and lost bet${if (stats.settledWithEv == 1) "" else "s"}: what their EVs promised, and what they actually paid. Bets with no EV on record, pushes and voids aren't in either.")
        }
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Avg EV", stats.averageEv?.let { Format.evPercent(it) } ?: "—")
                LabeledValue("Avg CLV", stats.averageClv?.let { Format.evPercent(it) } ?: "—", valueColor = moneyColor(stats.averageClv ?: 0.0))
                LabeledValue("Beat the close", stats.beatClosePercent?.let { Format.percent(it, 0) } ?: "—")
            }
            Caption(
                "CLV compares your price to the last fair line seen before the game started. Beating the close consistently is the best " +
                    "sign the edges are real, and it shows up far sooner than profit does.",
            )
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
                        (s.averageClv?.let { " · CLV ${Format.evPercentShort(it)}" } ?: ""),
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
                    Text(bet.selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(bet.marketLabel, bet.league, if (open) TrackerText.startsIn(bet.startsTs, now) else Format.shortDate(bet.startsTs)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(bet.eventName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (bet.source == BetTracker.SOURCE_CNO) "CNO" else "Vigilant") +
                            " · placed ${Format.placedAt(bet.createdAtMs)}" +
                            (if (bet.book != AppBook.name) " · ${bet.book}" else "") +
                            (if (bet.viaApi) (if (bet.imported) " · found in Novig's fills" else " · placed through Novig's API") else "") +
                            (if (bet.imported && !bet.viaApi) " · from an earlier ✓" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                LabeledValue("EV", bet.evPercentAtBet?.let { Format.evPercent(it) } ?: "—")
                LabeledValue("CLV", bet.clvPercent?.let { Format.evPercent(it) } ?: "—")
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
