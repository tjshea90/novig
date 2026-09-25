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
import androidx.compose.material3.FilterChip
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
        // Selected leagues first, so what's active is always visible without scrolling.
        items(all.sortedByDescending { it.novigName in selected }, key = { it.novigName }) { league ->
            FilterChip(
                selected = league.novigName in selected,
                onClick = { onToggle(league.novigName) },
                label = { Text("${league.emoji} ${league.displayName}") },
            )
        }
    }
}

/** "Novig live · 8s ago   Fair odds 4m ago   488 credits" */
@Composable
fun StatusLine(status: ScanStatus, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val edge = Edge.colors
    val novigFresh = status.novigAtMs != null && now - status.novigAtMs < 60_000
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (novigFresh) edge.positive else edge.warning, CircleShape),
        )
        Text(
            buildString {
                append("Novig ")
                append(if (status.refreshing) "updating…" else Format.age(status.novigAtMs, now))
                if (status.hasOddsKey) {
                    append("  ·  Fair ")
                    append(Format.age(status.referenceAtMs, now))
                    status.creditsRemaining?.let { append("  ·  $it credits") }
                }
                status.backoffSeconds?.let { append("  ·  slowed ${it}s") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
