package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import com.tjshea.vigilant.data.cloud.LiveBoard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.LiveMaster
import com.tjshea.vigilant.app.MainViewModel
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.livebid.LiveBid
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The Live tab: a monitor and control board (Tj, 2026-10-18, TCL122-4). At the top the GitHub / Phone switch: GitHub (the default) runs the live lane and its sockets on the runner and this phone runs no live
 * betting of its own; Phone closes the runner's sockets, pauses its live lane and runs the same lane here. Under it the board for whichever runs live ([LiveBoard]): what it is doing, the big numbers, the
 * graphs, every socket; then the controls (they reach GitHub within seconds), and the games it watches. Pull down to read the engine again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    state: UiState,
    light: MainViewModel.LightUi,
    now: Long,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    onShown: () -> Unit,
    onOpenPinnodds: () -> Unit,
    onOpenLiveBids: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    val s = state.settings
    val shown by rememberUpdatedState(onShown)
    // The status lines under the switches ([UiState.tailTrade], [UiState.labStatus]) are asked for every second or two while this tab shows.
    LaunchedEffect(Unit) { while (true) { shown(); kotlinx.coroutines.delay(1_500) } }
    val clock = rememberNow(1_000)
    val tick = maxOf(now, clock)
    val board = remember(s, state.cloudUi.status, state.cloudUi.trades, light, state.bets, tick / 1_000L) {
        if (s.liveOnGitHub) LiveBoard.github(s, state.cloudUi.status, light.githubTrend, LiveBoard.githubToday(state.cloudUi.trades, tick), tick)
        else LiveBoard.phone(s, light.phoneStats, light.status.running, light.phoneSockets, light.phoneTrend, phoneToday(state, tick), tick)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        VigilantPullToRefresh(busy = state.cloudUi.refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp).testTag("liveScreen"),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            WhereSwitch(s, onUpdate)
            LiveBoardCard(board, tick)
            LiveMasterRow(s, onUpdate)

            SectionTitle("Controls")
            LiveNote(if (s.liveOnGitHub) "Changes reach GitHub within seconds (they ride the settings it reads every 3 s)." else "Changes apply on this phone at once.")
            TailLiveBlock(state, onUpdate)
            if (!s.liveOnGitHub) LightStatus(s, light, tick)

            SectionTitle("Sockets")
            LiveNote(if (s.liveOnGitHub) "On GitHub: the runner holds BoltOdds, Kalshi and SX Bet; this phone keeps only Novig's feed for the bids." else "On this phone: it holds the sockets; the runner reads those feeds by request for its pregame scan.")
            LiveSwitch("Novig market feed", "Novig's own live prices: what Live light, the scan and the bids read.", s.gates.novigSocket, "liveSocketNovig") { v -> onUpdate { it.copy(gates = it.gates.copy(novigSocket = v)) } }
            LiveSwitch("BoltOdds", "Soft-book odds pushed live: Live light's second opinion on a decided game.", s.gates.boltSocket, "liveSocketBolt") { v -> onUpdate { it.copy(gates = it.gates.copy(boltSocket = v)) } }
            LiveSwitch("Kalshi", "The exchange's real-time prices (needs your Kalshi API key).", s.gates.kalshiSocket, "liveSocketKalshi") { v -> onUpdate { it.copy(gates = it.gates.copy(kalshiSocket = v)) } }
            LiveSwitch("SX Bet", "The sharp exchange's live prices and scores (needs your SX Bet read-only key).", s.gates.sxSocket, "liveSocketSx") { v -> onUpdate { it.copy(gates = it.gates.copy(sxSocket = v)) } }
            if (!s.liveOnGitHub) LiveSwitch("Pinnodds", "Pinnacle's live push feed. Needs a Pinnodds subscription; leave it off if you have none.", s.gates.pinnSocket, "liveSocketPinn") { v -> onUpdate { it.copy(gates = it.gates.copy(pinnSocket = v)) } }

            SectionTitle("Live games in the feed")
            LiveSwitch(
                "Include live games", "Lists games already under way (with Novig's live fee priced in) in the +EV feed. Off = pregame only.",
                s.includeLive, "liveIncludeSwitch",
            ) { v -> onUpdate { it.copy(includeLive = v) } }

            if (!s.liveOnGitHub) {
                SectionTitle("Live taker and live bids (phone)")
                LiveNote("Priced from BoltOdds' soft-book average, SX Bet's exchange mid or a Pinnodds socket, whichever is picked as the live feed on their pages.")
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenPinnodds, modifier = Modifier.testTag("liveOpenPinnodds")) { Text("Live taker") }
                    OutlinedButton(onClick = onOpenLiveBids, modifier = Modifier.testTag("liveOpenLiveBids")) { Text("Live bids") }
                }
            }
        }
        }
    }
}

/** Today's live bets on this phone (the Live light / tail lane's own, Eastern day). */
private fun phoneToday(state: UiState, now: Long): LiveBoard.Today {
    val from = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("America/New_York")).toLocalDate().atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli()
    val live = state.bets.filter { it.source == com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_TAIL && it.createdAtMs >= from }
    return LiveBoard.Today(live.size, live.count { it.status == com.tjshea.vigilant.data.tracker.BetStatus.PENDING }, live.sumOf { it.profit ?: 0.0 })
}

/** Where live runs: GitHub or this phone (TCL122-4). Choosing Phone pauses GitHub's live lane and closes its sockets; choosing GitHub stops this phone's own live betting. */
@Composable
private fun WhereSwitch(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    Text("Run live on", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("liveWhere")) {
        LiveBoard.Where.entries.forEachIndexed { i, w ->
            val selected = (w == LiveBoard.Where.GITHUB) == s.liveOnGitHub
            androidx.compose.material3.SegmentedButton(
                selected = selected, onClick = { onUpdate { it.copy(tailLive = it.tailLive.copy(engine = w == LiveBoard.Where.GITHUB)) } },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, LiveBoard.Where.entries.size), modifier = Modifier.testTag("liveWhere-${w.name}"),
            ) { Text(w.label) }
        }
    }
    LiveNote(
        if (s.liveOnGitHub) "GitHub runs the live lane, its bets and bids and its sockets; this phone runs no live betting of its own and spends no mobile data on it."
        else "This phone runs the live lane, its bets and bids and its sockets on your mobile connection; GitHub's live lane is paused and its sockets are closed.",
    )
}

/** The board: headline, big numbers, graphs, sockets, games watched. */
@Composable
private fun LiveBoardCard(b: LiveBoard.Board, now: Long) {
    val tone = when (b.tone) {
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.GOOD -> Edge.colors.positive
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.WARN -> Edge.colors.warning
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.BAD -> Edge.colors.negative
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("liveDashboard")) {
        Column(Modifier.padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp).background(tone, androidx.compose.foundation.shape.CircleShape))
                Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("liveBoardTitle"))
            }
            Text(b.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            b.tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { t -> LiveTile(t, Modifier.weight(1f)) }
                    if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
            val pushes = remember(b.trend) { com.tjshea.vigilant.data.cloud.LiveTrend.perMinute(b.trend) { it.pushes } }
            val judged = remember(b.trend) { com.tjshea.vigilant.data.cloud.LiveTrend.perMinute(b.trend) { it.judgments } }
            if (pushes.size >= 2) {
                LiveSpark("Novig pushes a minute", pushes, Edge.colors.positive)
                LiveSpark("Judgments a minute", judged, Edge.colors.warning)
            } else Text("The graphs fill in while the lane runs and this tab is open (one reading a second).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sockets (${b.where.label})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (b.sockets.isEmpty()) Text("No word on the sockets yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.sockets.forEach { x ->
                val c = when (LiveBoard.socketTone(x)) {
                    com.tjshea.vigilant.data.cloud.DashboardModel.Tone.GOOD -> Edge.colors.positive
                    com.tjshea.vigilant.data.cloud.DashboardModel.Tone.BAD -> Edge.colors.negative
                    else -> MaterialTheme.colorScheme.outline
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("liveSocket-${x.name}")) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(c, androidx.compose.foundation.shape.CircleShape))
                    Text(x.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(LiveBoard.socketWord(x, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (b.gameLines.isNotEmpty()) {
                Text("Games it is watching", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                b.gameLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun LiveTile(t: com.tjshea.vigilant.data.cloud.DashboardModel.Tile, modifier: Modifier) {
    val c = when (t.tone) {
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.GOOD -> Edge.colors.positive
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.BAD -> Edge.colors.negative
        com.tjshea.vigilant.data.cloud.DashboardModel.Tone.WARN -> Edge.colors.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = modifier.testTag("liveTile-${t.label}")) {
        Column(Modifier.padding(10.dp)) {
            Text(t.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = c, maxLines = 1)
            if (t.sub.isNotBlank()) Text(t.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** A small real-time line: the newest value large, the range under it, a dot on the newest point. */
@Composable
private fun LiveSpark(title: String, values: List<Float>, color: androidx.compose.ui.graphics.Color) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().testTag("liveSpark-$title")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(String.format(java.util.Locale.US, "%.0f", values.last()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val hi = values.max().coerceAtLeast(1f)
            val pad = 3.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            fun x(i: Int) = pad + w * i / (values.size - 1).coerceAtLeast(1)
            fun y(v: Float) = pad + h * (1f - v / hi)
            drawLine(guide, androidx.compose.ui.geometry.Offset(pad, size.height - pad), androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad), strokeWidth = 1.dp.toPx())
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(color, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(values.size - 1), y(values.last())))
        }
        Text("peak ${String.format(java.util.Locale.US, "%.0f", values.max())} · the last ${values.size} readings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What is running now, in one or two lines. */
internal fun summary(state: UiState, light: MainViewModel.LightUi, s: ScanSettings, masterOn: Boolean): String {
    if (!masterOn) return "Everything live is OFF (the switch above). Nothing live is read or sent."
    val l = light.status
    val parts = ArrayList<String>()
    parts += if (s.tailLive.on) {
        "Live light ${if (s.tailLive.bet) "REAL money" else "paper"}" + (if (l.running) ": ${l.games} games watched, ${l.offers} bets offered" else ": waiting for the Novig live feed or a live game") + (if (s.tailLive.bids) ", bids on" else "")
    } else "Live light off"
    parts += if (s.includeLive) "live games in the feed" else "feed is pregame only"
    val sockets = listOf(s.gates.novigSocket, s.gates.boltSocket, s.gates.kalshiSocket, s.gates.pinnSocket).count { it }
    parts += "$sockets of 4 sockets on"
    return parts.joinToString(" · ")
}

/** Live light's games and bids (what the Bids tab showed before): the lane's counts, one line per game, the bids up, the last that ended. */
@Composable
internal fun LightStatus(s: ScanSettings, light: MainViewModel.LightUi, now: Long) {
    val t = s.tailLive
    val l = light.status
    if (!(t.on && t.light)) return
    Text("What it is doing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    if (!l.running) {
        LiveNote("Starting: it needs the Novig key's live feed and a live game on a picked league.")
        return
    }
    LiveNote("${l.games} live games watched (${l.withScore} with a score) · ${l.pushes} pushes from Novig · ${l.scans} judgments (the last ${l.lastScanTookMs} ms after its push) · ${l.offers} bets offered to the bettor" + (if (t.bids) " · ${l.bidWants} bids asked for" else ""))
    l.gameLines.values.forEach { LiveNote(it) }
    if (!t.bids) return
    val up = light.bids.filter { it.active }
    val ended = light.bids.filter { !it.active }.takeLast(5).reversed()
    Text("Live light bids up (${up.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp).testTag("lightBidsTitle"))
    if (up.isEmpty()) LiveNote("None up right now: a bid goes up only on a side the score and clock call at least 85% decided, 7% under its fair and under what Novig offers. Most live sides are not that clear.")
    up.forEach { LightBidRow(it, now) }
    if (ended.isNotEmpty()) {
        Text("Last ended", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        ended.forEach { LightBidRow(it, now) }
    }
}

@Composable
private fun LightBidRow(b: LiveBid, now: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Text(b.selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${b.marketLabel} · ${b.eventName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (b.real) "REAL" else "PAPER"} · bid ${"%.1f".format(java.util.Locale.US, b.price * 100)}¢ · model ${"%.1f".format(java.util.Locale.US, b.fair * 100)}% · EV ${"%+.1f".format(java.util.Locale.US, b.ev * 100)}% · ${b.contracts} contracts",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                b.status.label + (if (b.filled > 0) " · ${b.filled} filled" else "") + " · ${Format.age(b.postedAtMs, now)}" + (b.why?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun LiveSwitch(title: String, sub: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
