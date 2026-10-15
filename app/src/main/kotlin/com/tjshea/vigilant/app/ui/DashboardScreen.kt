package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cloud.CloudDashboard
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import com.tjshea.vigilant.data.keys.ProviderView
import com.tjshea.vigilant.data.keys.UsageMeter

/** The GitHub page the engine also writes (same words, for a computer screen). */
private const val DASHBOARD_URL = "https://github.com/tjshea90/novig/blob/cloud-feed/DASHBOARD.md"

/**
 * Settings › Engine dashboard (Tj, 2026-10-15): the GitHub engine at a glance, on a phone. Everything comes from what the phone already polls (the engine's `status.json` every few seconds and its
 * `usage.json` laid over this phone's own meters), so it is the same data as the GitHub page, nothing extra is asked of GitHub, and the last copy shows when the phone is offline.
 * [DashboardContent] takes plain values and no app objects, so it renders from sample data.
 */
@Composable
fun DashboardPage(state: UiState, onOpenCloudSettings: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(5_000)
    val meters = remember(state.usage, state.cloudUsage, state.settings.sgoPro, state.settings.oddsPapi, state.settings.unabated, state.settings.gates.useBoltOdds, state.settings.oddsPapiFree, now / 30_000L) {
        meterViews(state, now)
    }
    DashboardContent(state.cloudUi.status, state.cloudHistory, meters, now, state.settings.cloudScan, onOpenCloudSettings, modifier)
}

@Composable
fun DashboardContent(
    status: CloudStatus?,
    history: List<DashboardModel.Point>,
    meters: List<ProviderView>,
    now: Long,
    cloudOn: Boolean,
    onOpenCloudSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    Column(modifier.testTag("dashboard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeadlineCard(status, now, cloudOn)
        val tiles = remember(status, now / 5_000L) { DashboardModel.tiles(status, now) }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { t -> TileCard(t, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        status?.takeIf { it.novigRoute.isNotBlank() }?.let { InfoLine("Novig", it.novigRoute) }
        status?.takeIf { it.tradeMode.isNotBlank() && it.tradeNote.isNotBlank() }?.let { InfoLine("Trading", it.tradeNote, if (it.tradeStopped.isNotBlank()) Tone.WARN else Tone.NEUTRAL) }
        status?.takeIf { it.tradeStopped.isNotBlank() }?.let { InfoLine("Stopped", it.tradeStopped, Tone.WARN) }

        SectionHeader("APIs")
        val health = remember(status) { DashboardModel.apiHealth(status) }
        val cards = remember(meters, health) {
            meters.filter { it.keys.isNotEmpty() || !it.policy.keyed || health.containsKey(it.policy.id) }.sortedBy { severity(health[it.policy.id]) }
        }
        if (cards.isEmpty()) Text("No API has answered yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        cards.forEach { v -> ApiCard(v, health[v.policy.id], DashboardModel.apiDetail(status, v.policy.id), now) }
        if (health.isEmpty() && status != null) {
            Text("Which APIs worked on the last scan appears after the engine's next scan (it has to finish one on the new version).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionHeader("Trends")
        if (history.size < 2) {
            Text("The graphs fill in as the engine finishes scans (one about every 5 minutes).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Trend("Scan time", history.map { it.scanS.toFloat() }, "s", Edge.colors.warning)
            Trend("+EV lines priced", history.map { it.posEv.toFloat() }, "", Edge.colors.positive)
            Trend("Fair odds age (median)", history.map { it.fairAgeS.toFloat() }, "s", Edge.colors.negative)
            Trend("Wallet", history.map { it.cash.toFloat() }, "$", Edge.colors.positive, money = true)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { runCatching { uri.openUri(DASHBOARD_URL) } }, modifier = Modifier.weight(1f).testTag("dashboardOpenGitHub")) { Text("Open on GitHub", maxLines = 1) }
            OutlinedButton(onClick = onOpenCloudSettings, modifier = Modifier.weight(1f).testTag("dashboardOpenCloud")) { Text("Cloud settings", maxLines = 1) }
        }
        Text(
            if (status == null) "Synced from the GitHub engine once the cloud scan is on and a token is saved."
            else "Synced from the GitHub engine · its last word ${DashboardModel.ago(now - status.heartbeatMs)} · run ${status.runId.takeLast(6)}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun severity(h: CloudDashboard.Health?): Int = when (h) {
    CloudDashboard.Health.FAILING -> 0
    CloudDashboard.Health.SILENT -> 1
    CloudDashboard.Health.HELD_BACK -> 2
    CloudDashboard.Health.STANDING_BY -> 3
    CloudDashboard.Health.WORKING -> 4
    else -> 5
}

@Composable
private fun toneColor(t: Tone): Color = when (t) {
    Tone.GOOD -> Edge.colors.positive
    Tone.WARN -> Edge.colors.warning
    Tone.BAD -> Edge.colors.negative
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun HeadlineCard(status: CloudStatus?, now: Long, cloudOn: Boolean) {
    val h = remember(status, now / 5_000L) { DashboardModel.headline(status, now) }
    val progress = remember(status) { DashboardModel.scanProgress(status) }
    val c = toneColor(h.tone)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("dashboardHeadline")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).background(c, CircleShape))
                Text(h.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(h.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress?.let { LinearProgressIndicator(progress = { it }, color = c, modifier = Modifier.fillMaxWidth()) }
            if (!cloudOn) Text("The cloud scan is switched off in Settings › Cloud scan: this shows the engine's last word.", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
        }
    }
}

@Composable
private fun TileCard(t: DashboardModel.Tile, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = if (t.tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurface else toneColor(t.tone), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.sub.isNotBlank()) Text(t.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoLine(label: String, text: String, tone: Tone = Tone.NEUTRAL) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurfaceVariant else toneColor(tone))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

/** One API: health chip, name, what the last scan got from it, and its usage meter (the same ledger as Settings › APIs, with the engine's calls in it). */
@Composable
private fun ApiCard(v: ProviderView, health: CloudDashboard.Health?, detail: String, now: Long) {
    val tone = when (health) {
        CloudDashboard.Health.WORKING -> Tone.GOOD
        CloudDashboard.Health.FAILING -> Tone.BAD
        CloudDashboard.Health.SILENT, CloudDashboard.Health.HELD_BACK -> Tone.WARN
        else -> Tone.NEUTRAL
    }
    val c = toneColor(tone)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("dashboardApi-${v.policy.id}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(c, CircleShape))
                Text(v.policy.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(health?.label?.substringAfter(' ') ?: if (v.policy.keyed && v.keys.isEmpty()) "no key" else "—", style = MaterialTheme.typography.labelMedium, color = c)
            }
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val allowance = v.totalAllowance
            val left = v.totalLeft
            if (allowance != null && left != null && allowance > 0) {
                UsageBar(used = (allowance - left).coerceAtLeast(0), left = left, allowance = allowance)
                Text(
                    "$left of $allowance ${v.policy.unit} left" + (v.nextReset?.let { " · resets ${UsageMeter.whenText(it, now)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (v.policy.keyed && v.keys.isNotEmpty()) {
                Text("${v.keys.sumOf { it.used }} requests counted (no allowance published)", style = MaterialTheme.typography.labelMedium)
            }
            val active = v.keys.firstOrNull { it.state == com.tjshea.vigilant.data.keys.KeyState.ACTIVE }
            if (v.keys.size > 1) Text("${v.keys.size} keys" + (active?.let { " · key ${it.index + 1} in use" } ?: "") + (v.keys.count { it.state == com.tjshea.vigilant.data.keys.KeyState.SPENT }.takeIf { it > 0 }?.let { " · $it spent" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (v.callsToday > 0) {
                Text(
                    "${v.callsToday} request${if (v.callsToday == 1) "" else "s"} today" + if (v.throttledToday > 0) " · ${v.throttledToday} slowed down" else "",
                    style = MaterialTheme.typography.labelSmall, color = if (v.throttledToday > 0) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small line graph of one number over the engine's last scans: the latest value large, the range under it. Unknown points (negative) are skipped. */
@Composable
private fun Trend(title: String, values: List<Float>, unit: String, color: Color, money: Boolean = false) {
    val known = remember(values) { values.filter { it >= 0f } }
    if (known.size < 2) return
    val last = known.last()
    val lo = known.min()
    val hi = known.max()
    fun f(x: Float) = if (money) String.format(java.util.Locale.US, "$%.2f", x) else if (x >= 100f) "${x.toInt()}$unit" else String.format(java.util.Locale.US, "%.0f$unit", x)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("dashboardTrend-$title")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(f(last), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Sparkline(known, color, Modifier.fillMaxWidth().height(48.dp))
            Text("low ${f(lo)} · high ${f(hi)} · last ${known.size} scans", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun x(i: Int) = pad + w * i / (values.size - 1).coerceAtLeast(1)
        fun y(v: Float) = pad + h * (1f - (v - lo) / span)
        drawLine(guide, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 1.dp.toPx())
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(values.size - 1), y(values.last())))
    }
}
