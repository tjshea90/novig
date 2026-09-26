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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.R
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.Odds

/**
 * CrazyNinjaOdds' +EV list for Tj's view (Tj, 2026-09-26; RESEARCH.md §18): CNO's rows, CNO's EV
 * and fair odds, kept current while this app or its mini window is on screen. Vigilant's own
 * scan stays on the +EV tab; the mini window can show both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnoScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Shrink to the mini window over other apps. Null hides the button (no picture-in-picture). */
    onMiniWindow: (() -> Unit)? = null,
) {
    val now = rememberNow(15_000)
    val cno = state.cno
    val on = state.settings.cnoEnabled
    // A list read for another link (Tj just changed it) isn't shown as if it were this one's.
    val snap = cno.snapshot?.takeIf { it.url == state.cnoUrl }
    var selected by remember { mutableStateOf<CnoRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CrazyNinjaOdds", fontWeight = FontWeight.Bold)
                        Text(
                            cnoStatus(state, snap, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cno.error != null) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (onMiniWindow != null) {
                        IconButton(onClick = onMiniWindow) {
                            Icon(painterResource(R.drawable.ic_mini_window), contentDescription = "Mini window over Novig", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (cno.refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh, enabled = on) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh CrazyNinjaOdds", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "summary") {
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                CnoView.describe(state.cnoUrl),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(if (state.settings.cnoViewUrl.isBlank()) "Use my filters" else "Change", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        cno.error?.let { Banner(if (snap != null) "$it. Showing the list from ${Format.age(snap.fetchedAtMs, now)}." else it, color = Edge.colors.negative) }
                        snap?.note?.let { Banner("CrazyNinjaOdds says: $it") }
                        when {
                            !on -> EmptyState(
                                "CrazyNinjaOdds is off",
                                "Turn it on in Settings to see CNO's +EV list here and in the mini window.",
                                action = "Settings",
                                onAction = onOpenSettings,
                            )
                            snap == null && cno.error == null -> EmptyState(
                                "Reading CrazyNinjaOdds…",
                                "Its +EV list for your view appears here in a few seconds, and stays current while Vigilant " +
                                    "or its mini window is on screen.",
                            )
                            snap != null && snap.rows.isEmpty() -> EmptyState(
                                "No +EV on CrazyNinjaOdds right now",
                                "Nothing in your view clears its filters. It's read again ${refreshLabel(state.settings)}.",
                            )
                        }
                    }
                }
                if (on && snap != null) {
                    items(snap.rows, key = { it.key }) { row ->
                        CnoCard(row, snap, state.settings, now, Modifier.padding(horizontal = 12.dp).animateItem()) { selected = row }
                    }
                    item(key = "credit") {
                        Text(
                            "From crazyninjaodds.com (free; donations keep it running). CNO's own EV and fair odds " +
                                (snap.evLabel?.let { "(${evMethodName(it)}) " } ?: "") +
                                "as of its last update. Novig's price may have moved since: check it in Novig before betting.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    selected?.let { row ->
        // The freshest copy of the tapped row: a refresh may have re-priced it since the tap.
        val live = snap?.rows?.firstOrNull { it.key == row.key } ?: row
        CnoSheet(live, snap, state.settings, state.cnoUrl, onDismiss = { selected = null })
    }
}

/** "Novig odds 40s old · every 1m", "Reading…", or the error. */
private fun cnoStatus(state: UiState, snap: CnoSnapshot?, now: Long): String = when {
    !state.settings.cnoEnabled -> "Off"
    state.cno.refreshing && snap == null -> "Reading…"
    state.cno.error != null && snap == null -> "Couldn't read it"
    snap == null -> "Not read yet"
    else -> "Odds ${Format.age(snap.dataAtMs, now).removeSuffix(" ago")} old · " +
        (if (state.cno.refreshing) "refreshing…" else refreshLabel(state.settings)) +
        if (state.cno.error != null) " · last read failed" else ""
}

fun refreshLabel(s: ScanSettings): String =
    if (s.cnoRefreshSeconds <= 0) "when you tap Refresh" else "every ${secondsLabel(s.cnoRefreshSeconds)}"

fun secondsLabel(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds s"

/** CNO's EV column label, in words ("LW-WC" → liquidity-weighted, worst case). */
fun evMethodName(label: String): String {
    val parts = label.uppercase().split('-')
    val weight = when (parts.getOrNull(0)) {
        "LW" -> "liquidity-weighted"
        "UW", "UMC" -> "unweighted"
        "C" -> "conservative"
        else -> parts.getOrNull(0)?.lowercase()
    }
    val method = when (parts.getOrNull(1)) {
        "WC" -> "worst case"
        "M" -> "multiplicative"
        "A", "AS" -> "additive/Shin"
        "P" -> "power"
        "MC" -> "market consensus"
        else -> parts.getOrNull(1)?.lowercase()
    }
    return listOfNotNull(weight, method).joinToString(", ").ifEmpty { label }
}

private fun sportEmoji(sport: String): String = when (sport.lowercase()) {
    "football" -> "🏈"
    "baseball" -> "⚾"
    "basketball" -> "🏀"
    "hockey" -> "🏒"
    "soccer" -> "⚽"
    else -> "🎯"
}

/** The Kelly stake for a CNO row: CNO's fair probability against the listed price, capped at what's available. */
fun cnoStake(row: CnoRow, s: ScanSettings): Double? {
    val fair = row.fairProbability ?: row.fairOdds?.let { Odds.impliedProbability(Odds.americanToDecimal(it)) } ?: return null
    val price = Odds.impliedProbability(Odds.americanToDecimal(row.odds))
    // Pregame Novig game markets charge the taker nothing (NOVIG_API.md §8).
    val stake = EvMath.suggestedStake(EvQuote(fair, price, 0.0), s.bankroll, s.kellyMultiplier, row.available)
    return stake.takeIf { it > 0 }
}

@Composable
private fun CnoCard(row: CnoRow, snap: CnoSnapshot, settings: ScanSettings, now: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val old = now - snap.dataAtMs > MiniWindow.CNO_OLD_MS
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvBadge(row.ev)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${sportEmoji(row.sport)} ${row.league.ifEmpty { row.sport }}" + (row.startsAtMs?.let { " · ${Format.startTime(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (old) Text("old price", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.bet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${row.market} · ${row.event}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.book.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(MiniWindow.american(row.odds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.fairOdds?.let { f -> LabeledValue("Fair", MiniWindow.american(f) + (row.fairProbability?.let { " · ${Format.percent(it)}" } ?: "")) }
                row.available?.let { LabeledValue("Available", Format.money(it)) }
                row.books?.let { LabeledValue("Books", it.toString()) }
                cnoStake(row, settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CnoSheet(row: CnoRow, snap: CnoSnapshot?, settings: ScanSettings, viewUrl: String, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = LocalUriHandler.current
    val openNovig = LocalOpenNovig.current
    val now = rememberNow(15_000)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvBadge(row.ev, large = true)
                Spacer(Modifier.width(12.dp))
                Text("CrazyNinjaOdds · ${row.book}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text(row.bet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${row.market} · ${row.event}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.startsAtMs?.let { Text("${row.league.ifEmpty { row.sport }} · ${Format.startTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue(row.book, MiniWindow.american(row.odds))
                row.fairOdds?.let { LabeledValue("Fair", MiniWindow.american(it)) }
                row.fairProbability?.let { LabeledValue("Fair %", Format.percent(it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.available?.let { LabeledValue("Available", Format.money(it)) }
                row.books?.let { LabeledValue("Books", it.toString()) }
                cnoStake(row, settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
            }
            Text(
                "CNO's numbers" + (snap?.let { " as of ${Format.age(it.dataAtMs, now)}" } ?: "") +
                    (snap?.evLabel?.let { ", ${evMethodName(it)} devig" } ?: "") +
                    ". The price and the dollars available were what Novig showed then: check them in Novig before betting." +
                    if (CnoView.includesLive(viewUrl)) " Live bets pay Novig's taker fee, which CNO's EV may not include." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (openNovig != null) Button(onClick = openNovig) { Text("Open Novig") }
                row.gameUrl?.let { url -> OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Every book on CNO") } }
            }
        }
    }
}
