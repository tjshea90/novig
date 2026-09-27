package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
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
) {
    val now = rememberNow(60_000)
    var view by rememberSaveable { mutableStateOf(initialView) }
    var period by rememberSaveable { mutableStateOf(TrackerPeriod.ALL) }
    var filter by rememberSaveable { mutableStateOf(BetFilter.OPEN) }
    var confirmDelete by remember { mutableStateOf<TrackedBet?>(null) }
    var editStake by remember { mutableStateOf<TrackedBet?>(null) }
    // Results of finished games from their final scores, each time the tab is opened (no network when nothing is due).
    LaunchedEffect(Unit) { onShown() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bet tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onCheckOdds, enabled = !state.checkingOdds) {
                        Text(if (state.checkingOdds) "Checking…" else "Check odds now")
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
                    val bets = inPeriod(state.bets, period, now)
                    if (bets.isEmpty()) {
                        item(key = "empty") { EmptyState("No bets ${if (period == TrackerPeriod.ALL) "tracked yet" else "in this period"}", EMPTY_HINT) }
                    } else {
                        item(key = "stats") { StatsCards(bets) }
                    }
                }
                TrackerView.BETS -> {
                    item(key = "filters") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BetFilter.entries.forEach { f ->
                                val n = filtered(state.bets, f).size
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ($n)") })
                            }
                        }
                    }
                    val shown = filtered(state.bets, filter).sortedByDescending { it.createdAtMs }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(if (state.bets.isEmpty()) "No bets tracked yet" else "No ${filter.label.lowercase()} bets", EMPTY_HINT)
                        }
                    }
                    items(shown, key = { it.id }) { bet ->
                        BetCard(bet, now, onSettle, onStake = { editStake = bet }, onDelete = { confirmDelete = bet })
                    }
                }
            }
        }
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
}

private const val EMPTY_HINT = "Tap ✓ on a bet in the widget or the CNO tab (or Track on a +EV card) and it's logged here, \$1 unless you change it."

private fun filtered(bets: List<TrackedBet>, f: BetFilter) = when (f) {
    BetFilter.OPEN -> bets.filter { it.status == BetStatus.PENDING }
    BetFilter.SETTLED -> bets.filter { it.status != BetStatus.PENDING }
    BetFilter.ALL -> bets
}

@Composable
private fun moneyColor(v: Double): Color = when {
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
private fun StatsCards(bets: List<TrackedBet>) {
    val stats = BetTracker.stats(bets)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stats.outliers > 0) {
            Text(
                "${stats.outliers} outlier bet${if (stats.outliers == 1) "" else "s"} (over ±${Format.percent(BetTracker.OUTLIER_EV, 0)} EV when bet) " +
                    "left out of every number here, so one odd bet can't skew them. ${if (stats.outliers == 1) "It's" else "They're"} still under Bets.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Profit", Format.signedMoney(stats.profit), valueColor = moneyColor(stats.profit))
                LabeledValue("Profit %", stats.roi?.let { Format.evPercent(it) } ?: "—", valueColor = moneyColor(stats.roi ?: 0.0))
                LabeledValue("Staked", Format.money(stats.staked))
            }
            ProfitLine(bets)
            Text(
                "Profit % is profit over money staked on settled bets (ROI): it runs with every result.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Win %", stats.winRate?.let { Format.percent(it, 1) } ?: "—")
                LabeledValue("Record", "${stats.won}-${stats.lost}" + if (stats.pushed > 0) "-${stats.pushed}" else "")
                LabeledValue("Open", "${stats.pending}")
            }
            Text(
                "Wins out of bets won or lost (pushes aside). Results come from each game's final score (ESPN, MLB) once it's over; tap one to fix it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("Expected", Format.signedMoney(stats.expectedProfit))
                LabeledValue("Avg EV", stats.averageEv?.let { Format.evPercent(it) } ?: "—")
                LabeledValue("Avg CLV", stats.averageClv?.let { Format.evPercent(it) } ?: "—", valueColor = moneyColor(stats.averageClv ?: 0.0))
            }
            Text(
                "CLV compares your price to the last fair line seen before the game started. Beating the close " +
                    "consistently is the best sign the edges are real" +
                    (stats.beatClosePercent?.let { " — you've beaten it on ${Format.percent(it, 0)} of bets." } ?: "."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatsCard {
            Text("By scanner", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf("CNO" to BetTracker.SOURCE_CNO, "Vigilant" to BetTracker.SOURCE_VIGILANT).forEach { (name, source) ->
                val s = BetTracker.stats(bets.filter { it.source == source })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${s.bets} bets · ${s.won}-${s.lost}", Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                    Text(Format.signedMoney(s.profit), Modifier.weight(1f), color = moneyColor(s.profit), style = MaterialTheme.typography.bodyMedium)
                    Text(s.roi?.let { Format.evPercentShort(it) } ?: "—", color = moneyColor(s.roi ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Running profit, one step per settled bet in the order they settled (outliers left out): green above zero, red below. */
@Composable
private fun ProfitLine(bets: List<TrackedBet>) {
    val settled = bets.filter { it.status != BetStatus.PENDING && it.status != BetStatus.VOID && !it.isOutlier }.sortedBy { it.settledAtMs ?: it.startsTs }
    if (settled.size < 2) return
    val points = settled.runningFold(0.0) { acc, b -> acc + (b.profit ?: 0.0) }
    val color = if (points.last() >= 0) Edge.colors.positive else Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Running profit over ${settled.size} settled bets" }) {
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

@Composable
private fun BetCard(bet: TrackedBet, now: Long, onSettle: (String, BetStatus) -> Unit, onStake: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bet.selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(bet.marketLabel, bet.league, Format.shortDate(bet.startsTs)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(bet.eventName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (bet.source == BetTracker.SOURCE_CNO) "CNO" else "Vigilant") +
                            (if (bet.book != "Novig") " · ${bet.book}" else "") +
                            (if (bet.imported) " · from an earlier ✓" else ""),
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
                LabeledValue("Stake ✎", Format.money(bet.stake), Modifier.clickable(onClickLabel = "Change the stake", onClick = onStake))
                LabeledValue("Price", bet.american?.let { Odds.formatAmerican(it) } ?: Format.american(bet.price))
                LabeledValue("EV", bet.evPercentAtBet?.let { Format.evPercent(it) } ?: "—")
                LabeledValue("CLV", bet.clvPercent?.let { Format.evPercent(it) } ?: "—")
                LabeledValue(
                    if (bet.status == BetStatus.PENDING) "To win" else "Result",
                    bet.profit?.let { Format.signedMoney(it) } ?: Format.money(bet.profitIfWon),
                    valueColor = bet.profit?.let { moneyColor(it) } ?: Color.Unspecified,
                )
            }
            if (bet.status == BetStatus.PENDING && bet.nowEv != null) {
                val ev = bet.nowEv!!
                Text(
                    "now ${Format.evPercentShort(ev)} EV",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ev >= 0) Edge.colors.positive else Edge.colors.negative,
                )
                Text(
                    listOfNotNull(
                        bet.nowFair?.let { "fair now ${Format.american(it)}" },
                        bet.nowBooks?.let { "$it book${if (it == 1) "" else "s"}" },
                        Format.age(bet.nowAtMs, now),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (bet.status == BetStatus.PENDING) {
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.WON) }, label = { Text("Won") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.LOST) }, label = { Text("Lost") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.PUSH) }, label = { Text("Push") })
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.VOID) }, label = { Text("Void") })
                } else {
                    val name = if (bet.status == BetStatus.FMV) "Fair value" else bet.status.name.lowercase().replaceFirstChar { it.uppercase() }
                    AssistChip(onClick = { onSettle(bet.id, BetStatus.PENDING) }, label = { Text("$name · undo") })
                    if (bet.settledBy == BetSettler.BY_SCORES || bet.settledBy == BetSettler.BY_NOVIG) {
                        Text("from the final score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${bet.selection} · what you bet on Novig", style = MaterialTheme.typography.bodySmall)
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
