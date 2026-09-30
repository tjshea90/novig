package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.reference.ParlayAccount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The test tag of ParlayAPI's credits-a-day chart. */
const val USAGE_CHART = "parlayUsageChart"

/** [history]'s days laid out one slot a day over its window, ending today (ParlayAPI's days are UTC): a day with no calls is 0. */
fun usageSlots(history: ParlayAccount.History, now: Long): List<ParlayAccount.Day> {
    val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
    val byDay = history.days.associateBy { it.day }
    val n = history.windowDays.coerceIn(1, 90)
    return (n - 1 downTo 0).map { back ->
        val d = today.minusDays(back.toLong()).toString()
        byDay[d] ?: ParlayAccount.Day(d, 0, 0)
    }
}

private val DAY_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun dayLabel(day: String): String = runCatching { LocalDate.parse(day).format(DAY_LABEL) }.getOrDefault(day)

/**
 * Under ParlayAPI's meter (Tj, 2026-09-30, PARLAY_API.md §6.2): credits spent each day of the last 30 as bars (one series: no legend, the
 * title names it), a tap on a bar says that day's credits and calls, and the endpoints that spent the most, in words, as the table view.
 */
@Composable
fun ParlayUsageChart(history: ParlayAccount.History, now: Long, modifier: Modifier = Modifier) {
    val slots = remember(history, now / 3_600_000L) { usageSlots(history, now) }
    val peak = slots.maxOfOrNull { it.credits } ?: 0
    // The day read out under the bars: the tapped one, else today.
    var picked by rememberSaveable(history.readAtMs) { mutableStateOf<String?>(null) }
    val shown = slots.firstOrNull { it.day == picked } ?: slots.last()
    val bar = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val peakDay = slots.filter { it.credits == peak && peak > 0 }.lastOrNull()
    Column(modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("Credits a day · last ${slots.size} days", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${history.total} in all", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag(USAGE_CHART)
                .semantics {
                    contentDescription = "ParlayAPI credits a day, last ${slots.size} days: ${history.total} in all" +
                        (peakDay?.let { ", most ${it.credits} on ${dayLabel(it.day)}" } ?: "") + ", today ${slots.last().credits}"
                }
                .pointerInput(slots) {
                    detectTapGestures { p ->
                        val i = (p.x / (size.width.toFloat() / slots.size)).toInt().coerceIn(0, slots.lastIndex)
                        picked = slots[i].day
                    }
                },
        ) {
            val slot = size.width / slots.size
            // A 2px gap between bars, thin bars, the data end rounded, anchored to the baseline.
            val gap = 2.dp.toPx()
            val w = (slot - gap).coerceAtLeast(1f)
            val base = size.height - 1.dp.toPx()
            val r = minOf(2.dp.toPx(), w / 2)
            slots.forEachIndexed { i, d ->
                if (d.credits <= 0 || peak <= 0) return@forEachIndexed
                val h = (base * d.credits / peak).coerceAtLeast(2.dp.toPx())
                val alpha = if (picked == null || d.day == shown.day) 1f else 0.45f
                drawRoundRect(bar.copy(alpha = alpha), Offset(i * slot + gap / 2, base - h), Size(w, h + r), CornerRadius(r, r))
            }
            // The baseline over the rounded feet: recessive.
            drawLine(axis, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        }
        Row {
            Text(dayLabel(slots.first().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (shown.day == slots.last().day) "Today" else dayLabel(shown.day)) +
                ": ${shown.credits} credit${if (shown.credits == 1) "" else "s"} · ${shown.requests} call${if (shown.requests == 1) "" else "s"}" +
                (peakDay?.takeIf { it.day != shown.day }?.let { " · most: ${it.credits} on ${dayLabel(it.day)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        if (history.top.isNotEmpty()) {
            Text("Where they went", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            history.top.take(TOP_SHOWN).forEach { e ->
                Row {
                    Text(ParlayAccount.endpointName(e.endpoint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${e.credits} cr · ${e.requests} call${if (e.requests == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Endpoints listed under the chart (ParlayAPI's answer has its top ten). */
private const val TOP_SHOWN = 6
