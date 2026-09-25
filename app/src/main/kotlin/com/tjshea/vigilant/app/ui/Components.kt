package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.ScanStatus
import com.tjshea.vigilant.data.scanner.League
import kotlinx.coroutines.delay

/** The current time, ticking every [periodMs] while on screen. Only the caller recomposes. */
@Composable
fun rememberNow(periodMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** OddsJam's signature green EV pill. */
@Composable
fun EvBadge(ev: Double, modifier: Modifier = Modifier, large: Boolean = false) {
    val edge = Edge.colors
    val positive = ev >= 0
    Surface(
        modifier = modifier.semantics { contentDescription = "Expected value ${Format.evPercent(ev)}" },
        shape = RoundedCornerShape(8.dp),
        color = if (positive) edge.positiveContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            Format.evPercent(ev),
            modifier = Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 3.dp),
            color = if (positive) edge.positive else edge.negative,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun LeagueChips(all: List<League>, selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Always Tj's order (NFL, NCAAF, MLB, WNBA, NHL, then the rest), so chips never jump around.
        items(all, key = { it.novigName }) { league ->
            FilterChip(
                selected = league.novigName in selected,
                onClick = { onToggle(league.novigName) },
                label = { Text("${league.emoji} ${league.displayName}") },
            )
        }
    }
}

/** "Scanned 3m ago", or the scan's progress while one runs. What's left per API is the usage strip. */
@Composable
fun StatusLine(status: ScanStatus, modifier: Modifier = Modifier) {
    val now = rememberNow(15_000)
    val edge = Edge.colors
    val scanned = status.scannedAtMs
    val fresh = scanned != null && now - scanned < 2 * 60_000
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    when {
                        status.scanning -> MaterialTheme.colorScheme.primary
                        fresh -> edge.positive
                        scanned != null -> edge.warning
                        else -> MaterialTheme.colorScheme.outline
                    },
                    CircleShape,
                ),
        )
        Text(
            buildString {
                val p = status.progress
                when {
                    status.scanning && p != null && p.total > 0 -> append("${p.step} ${p.done}/${p.total}")
                    status.scanning -> append("Scanning…")
                    scanned == null -> append("Not scanned yet")
                    else -> append("Scanned ${Format.age(scanned, now)}")
                }
                status.backoffSeconds?.let { append("  ·  Novig slowed us ${it}s") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one button that fetches anything. Shows a spinner while a scan runs. */
@Composable
fun ScanButton(scanning: Boolean, enabled: Boolean, onScan: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onScan,
        enabled = enabled && !scanning,
        modifier = modifier.padding(end = 8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(if (scanning) "  Scanning" else "  Scan", fontWeight = FontWeight.SemiBold)
    }
}

/** A thin bar under the top bar while a scan runs, determinate once the step has a count. */
@Composable
fun ScanProgressBar(status: ScanStatus, modifier: Modifier = Modifier) {
    if (!status.scanning) return
    val p = status.progress
    if (p != null && p.total > 0) {
        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}

@Composable
fun Banner(text: String, modifier: Modifier = Modifier, color: Color = Edge.colors.warning, action: String? = null, onAction: () -> Unit = {}) {
    Surface(modifier.fillMaxWidth(), color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
    }
}
