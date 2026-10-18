package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tjshea.vigilant.data.tracker.SlateProfit
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The window of time a chart shows, and how a pinch or a drag changes it. Pure, for tests (Tj, 2026-10-10: "pinch zoom in and zoom out to see different time periods, and scroll left to right").
 * A view is [start, end] in epoch milliseconds, always inside the data's [minT, maxT] and never narrower than [minSpan].
 */
object ChartView {
    data class Window(val start: Long, val end: Long) {
        val span: Long get() = end - start
    }

    const val MIN_SPAN_MS = 30L * 60_000L

    /** [w] kept inside [minT, maxT] at its own width (a window wider than the bounds becomes the bounds). */
    fun clamp(w: Window, minT: Long, maxT: Long, minSpan: Long = MIN_SPAN_MS): Window {
        val maxSpan = (maxT - minT).coerceAtLeast(minSpan)
        val span = w.span.coerceIn(minSpan, maxSpan)
        val start = w.start.coerceIn(minT, (maxT - span).coerceAtLeast(minT))
        return Window(start, start + span)
    }

    /** Zoomed by [factor] (above 1 = closer in) around the point at [focus] (0 = left edge, 1 = right edge) of the window: the time under the fingers stays under them. */
    fun zoom(w: Window, focus: Double, factor: Double, minT: Long, maxT: Long, minSpan: Long = MIN_SPAN_MS): Window {
        if (factor <= 0.0 || factor.isNaN()) return w
        val focusT = w.start + focus.coerceIn(0.0, 1.0) * w.span
        val maxSpan = (maxT - minT).coerceAtLeast(minSpan)
        val span = (w.span / factor).toLong().coerceIn(minSpan, maxSpan)
        val start = focusT - focus.coerceIn(0.0, 1.0) * span
        return clamp(Window(start.toLong(), start.toLong() + span), minT, maxT, minSpan)
    }

    /** Moved by a drag of [dxFraction] of the chart's width (to the right = back in time, as a finger drags the paper). */
    fun pan(w: Window, dxFraction: Double, minT: Long, maxT: Long, minSpan: Long = MIN_SPAN_MS): Window =
        clamp(Window(w.start - (dxFraction * w.span).toLong(), w.end - (dxFraction * w.span).toLong()), minT, maxT, minSpan)

    private val STEPS = longArrayOf(
        3_600_000L, 3 * 3_600_000L, 6 * 3_600_000L, 12 * 3_600_000L, 24 * 3_600_000L, 2 * 24 * 3_600_000L, 7 * 24 * 3_600_000L, 14 * 24 * 3_600_000L, 30 * 24 * 3_600_000L,
    )

    /** Tick times inside [w], at most [maxTicks], on round local times (hours, or midnights for a step of a day or more). */
    fun ticks(w: Window, maxTicks: Int, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val step = STEPS.firstOrNull { w.span / it <= maxTicks } ?: STEPS.last()
        val start = Instant.ofEpochMilli(w.start).atZone(zone)
        var t = (if (step >= STEPS[4]) start.toLocalDate().atStartOfDay(zone) else start.withMinute(0).withSecond(0).withNano(0)).toInstant().toEpochMilli()
        val out = ArrayList<Long>()
        while (t <= w.end) {
            if (t >= w.start) {
                val local = Instant.ofEpochMilli(t).atZone(zone)
                // Hour steps land on multiples of the step from local midnight; day steps on every [step / day] days since the epoch's Monday grid is not needed: each midnight in step.
                val onGrid = if (step < STEPS[4]) (local.hour % (step / 3_600_000L).toInt() == 0) else if (step == STEPS[4]) true else (local.toLocalDate().toEpochDay() % (step / STEPS[4]) == 0L)
                if (onGrid) out += t
            }
            t += if (step >= STEPS[4]) STEPS[4] else 3_600_000L
            if (out.size > maxTicks * 3) break
        }
        return out
    }
}

/** The profit card's views (Tj, 2026-10-18, TCL122-6): one slate (today's, yesterday's) or the last few slates day by day. A "day" is a slate of games ([SlateProfit]), never midnight to midnight. */
enum class SlateRange(val label: String, val days: Int) {
    TODAY("Today", 1), YESTERDAY("Yesterday", 1), WEEK("7 days", 7), MONTH("30 days", 30), ALL("All", Int.MAX_VALUE);
}

/**
 * The Tracker's profit card (Tj, 2026-10-18, TCL122-6: "make the profit/loss graph easier to understand. make an option to see the graph for today only ... make sure the profit/loss math is sound"):
 *  - the balance, from ONE fixed start (the money staked on the first slate logged), and the change since;
 *  - Today / Yesterday: that slate alone, from its first game to its last result, as a running line from 0, with the change over the balance it started from;
 *  - 7 days / 30 days / All: one bar per slate (green up, red down) and the change over the balance before the first of them;
 *  - a day-by-day list under it. Tap the line or a bar to read it.
 */
@Composable
fun ProfitChartCard(bets: List<TrackedBet>, modifier: Modifier = Modifier) {
    val now = rememberNow(60_000)
    var range by rememberSaveable { mutableStateOf(SlateRange.TODAY) }
    val book = remember(bets, now / 60_000L) { SlateProfit.book(bets, now) }
    Card(modifier.fillMaxWidth().testTag("profitChart"), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfitCardBody(bets, book, now, range) { range = it }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfitCardBody(bets: List<TrackedBet>, book: SlateProfit.Book, now: Long, range: SlateRange, onRange: (SlateRange) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Profit and loss", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Balance ${Format.money(book.balance)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("profitBalance"))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.signedMoney(book.profit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = moneyColor(book.profit))
            Text(book.pct?.let { pctText(it) + " all time" } ?: "all time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        if (book.days.isEmpty()) "No bets yet."
        else "Started at ${Format.money(book.start)}: the money staked on the first day logged (${dayName(book.days.first().date)}). It never changes, so staking more never looks like a deposit.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SlateRange.entries.forEach { r ->
            FilterChip(selected = r == range, onClick = { onRange(r) }, label = { Text(r.label, maxLines = 1) }, modifier = Modifier.testTag("profitRange-${r.name}"))
        }
    }
    if (book.days.isEmpty()) return
    when (range) {
        SlateRange.TODAY, SlateRange.YESTERDAY -> {
            val cur = SlateProfit.current(book, now)
            val day = if (range == SlateRange.TODAY) cur else SlateProfit.previous(book, cur)
            if (day == null) { Text("No slate before this one yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
            SlateView(bets, day, now)
        }
        else -> {
            val days = SlateProfit.lastDays(book, now, range.days)
            DaysView(days)
        }
    }
}

/** One slate: its numbers, its running line from its first game to its last result, and what it did to the balance. */
@Composable
private fun SlateView(bets: List<TrackedBet>, day: SlateProfit.Day, now: Long) {
    val points = remember(bets, day.date) { SlateProfit.curve(bets, day.date) }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("America/New_York") } }
    Text(
        "${dayName(day.date)} slate · first game ${fmt.format(Date(day.firstStartMs))}" + if (day.finished) " · last result ${fmt.format(Date(day.endMs))}" else " · ${day.open} still open",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitSlateLine"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledValue("This slate", Format.signedMoney(day.profit), valueColor = moneyColor(day.profit), modifier = Modifier.weight(1.2f).testTag("profitInView"))
        LabeledValue("vs the day before", day.changePct?.let { pctText(it) } ?: "—", valueColor = moneyColor(day.profit), modifier = Modifier.weight(1.2f))
        LabeledValue("Record", "${day.won}-${day.lost}", modifier = Modifier.weight(0.8f))
    }
    Text(
        "Started the slate at ${Format.money(day.startBalance)}, " + (if (day.finished) "ended at " else "now at ") + "${Format.money(day.endBalance)}. ${day.settled} settled on ${Format.money(day.staked)} staked.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitChartReadout"),
    )
    if (points.isEmpty()) {
        Text("No results in yet: the line starts with the first one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var selected by remember(day.date) { mutableStateOf<SlateProfit.Point?>(null) }
    selected?.let { p -> Text("${fmt.format(Date(p.tMs))} · ${p.label} ${Format.signedMoney(p.profit)} on ${Format.money(p.stake)} · slate so far ${Format.signedMoney(p.cum)}", style = MaterialTheme.typography.labelSmall) }
    SlateLine(points, day.firstStartMs, maxOf(day.endMs, points.last().tMs), selected, { selected = it }, Modifier.fillMaxWidth().height(200.dp))
}

/** Several slates: one bar each, then the list. */
@Composable
private fun DaysView(days: List<SlateProfit.Day>) {
    if (days.isEmpty()) return
    val profit = days.sumOf { it.profit }
    val from = days.first().startBalance
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledValue("${days.size} slate${if (days.size == 1) "" else "s"}", Format.signedMoney(profit), valueColor = moneyColor(profit), modifier = Modifier.weight(1.2f).testTag("profitInView"))
        LabeledValue("vs the start of it", if (from > 0) pctText(profit / from) else "—", valueColor = moneyColor(profit), modifier = Modifier.weight(1.2f))
        LabeledValue("Up / down days", "${days.count { it.profit > 0.005 }} / ${days.count { it.profit < -0.005 }}", modifier = Modifier.weight(1f))
    }
    Text("From ${Format.money(from)} to ${Format.money(days.last().endBalance)}. Each bar is one slate of games (first game to last result).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitChartReadout"))
    var selected by remember(days) { mutableStateOf<SlateProfit.Day?>(null) }
    selected?.let { d -> Text("${dayName(d.date)}: ${Format.signedMoney(d.profit)} (${d.changePct?.let { pctText(it) } ?: "—"}) · balance ${Format.money(d.endBalance)} · ${d.won}-${d.lost}" + if (d.open > 0) " · ${d.open} open" else "", style = MaterialTheme.typography.labelSmall) }
    DayBars(days, selected, { selected = it }, Modifier.fillMaxWidth().height(180.dp))
    Text("Day by day", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    days.asReversed().take(MAX_LIST_DAYS).forEach { d ->
        Row(Modifier.fillMaxWidth().testTag("profitDay-${d.date}"), verticalAlignment = Alignment.CenterVertically) {
            Text(dayName(d.date), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(Format.signedMoney(d.profit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = moneyColor(d.profit), modifier = Modifier.padding(end = 8.dp))
            Text(d.changePct?.let { pctText(it) } ?: "—", style = MaterialTheme.typography.labelSmall, color = moneyColor(d.profit), modifier = Modifier.padding(end = 8.dp))
            Text(Format.money(d.endBalance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (days.size > MAX_LIST_DAYS) Text("…and ${days.size - MAX_LIST_DAYS} earlier slates.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val MAX_LIST_DAYS = 30

private fun pctText(x: Double) = (if (x >= 0) "+" else "") + String.format(Locale.US, "%.1f%%", x * 100)

private fun dayName(d: java.time.LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d", Locale.US))

/** The slate's running profit: a line from 0 at its first game, one step per result, with a labelled zero (where the slate started). */
@Composable
private fun SlateLine(points: List<SlateProfit.Point>, startMs: Long, endMs: Long, selected: SlateProfit.Point?, onTap: (SlateProfit.Point?) -> Unit, modifier: Modifier) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val span = (endMs - startMs).coerceAtLeast(60_000L)
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("America/New_York") } }
    Box(
        modifier.testTag("profitCanvas").semantics { contentDescription = "This slate's profit, from ${fmt.format(Date(startMs))} to ${fmt.format(Date(endMs))}" }
            .pointerInput(points, startMs, span) {
                detectTapGestures { pos ->
                    val t = startMs + (pos.x / size.width) * span
                    tap(points.minByOrNull { abs(it.tMs - t) })
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val padB = labelPx * 2.2f; val padT = labelPx * 1.2f
            val plotH = h - padB - padT
            var lo = minOf(0.0, points.minOf { it.cum }); var hi = maxOf(0.0, points.maxOf { it.cum })
            if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
            val pad = (hi - lo) * 0.1; lo -= pad; hi += pad
            fun x(t: Long) = ((t - startMs).toDouble() / span * w).toFloat().coerceIn(0f, w)
            fun y(v: Double) = padT + (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
            drawLine(axis, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            // A step line: flat until a result comes in, then its jump (a bet's money moves when it settles, not before).
            val path = Path(); path.moveTo(0f, y(0.0))
            var prev = 0.0
            for (p in points) { path.lineTo(x(p.tMs), y(prev)); path.lineTo(x(p.tMs), y(p.cum)); prev = p.cum }
            path.lineTo(w, y(prev))
            drawPath(path, if (prev >= 0) up else down, style = Stroke(width = 2.dp.toPx()))
            drawCircle(if (prev >= 0) up else down, 3.5.dp.toPx(), Offset(x(points.last().tMs), y(prev)))
            selected?.let { s -> drawLine(axis, Offset(x(s.tMs), padT), Offset(x(s.tMs), padT + plotH), strokeWidth = 1.dp.toPx()); drawCircle(if (s.cum >= 0) up else down, 4.dp.toPx(), Offset(x(s.tMs), y(s.cum))) }
            val paint = android.graphics.Paint().apply { color = label.toArgb(); textSize = labelPx; isAntiAlias = true }
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("0 (slate start)", 4f, y(0.0) - 4f, paint)
            nc.drawText(Format.signedMoney(prev), (w - paint.measureText(Format.signedMoney(prev)) - 4f).coerceAtLeast(0f), (y(prev) - 6f).coerceAtLeast(labelPx), paint)
            val a = fmt.format(Date(startMs)); val b = fmt.format(Date(endMs))
            nc.drawText(a, 0f, h - labelPx * 0.4f, paint)
            nc.drawText(b, w - paint.measureText(b), h - labelPx * 0.4f, paint)
        }
    }
}

/** One bar per slate around a labelled zero; the tapped one is outlined. */
@Composable
private fun DayBars(days: List<SlateProfit.Day>, selected: SlateProfit.Day?, onTap: (SlateProfit.Day?) -> Unit, modifier: Modifier) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        modifier.testTag("profitBars").semantics { contentDescription = "Profit of each of the last ${days.size} slates" }
            .pointerInput(days) { detectTapGestures { pos -> tap(days.getOrNull((pos.x / size.width * days.size).toInt().coerceIn(0, days.size - 1))) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val padB = labelPx * 2.2f; val padT = labelPx * 1.2f
            val plotH = h - padB - padT
            var lo = minOf(0.0, days.minOf { it.profit }); var hi = maxOf(0.0, days.maxOf { it.profit })
            if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
            fun y(v: Double) = padT + (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
            val slot = w / days.size
            val bar = (slot * 0.7f).coerceAtMost(28.dp.toPx())
            days.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2
                val top = y(maxOf(d.profit, 0.0)); val bottom = y(minOf(d.profit, 0.0))
                drawRect(if (d.profit >= 0) up else down, Offset(cx - bar / 2, top), androidx.compose.ui.geometry.Size(bar, (bottom - top).coerceAtLeast(1.5f)))
                if (d == selected) drawRect(ink, Offset(cx - bar / 2, top), androidx.compose.ui.geometry.Size(bar, (bottom - top).coerceAtLeast(1.5f)), style = Stroke(1.5.dp.toPx()))
            }
            drawLine(axis, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx())
            val paint = android.graphics.Paint().apply { color = label.toArgb(); textSize = labelPx; isAntiAlias = true }
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText(Format.signedMoney(hi), 2f, padT, paint)
            if (lo < 0) nc.drawText(Format.signedMoney(lo), 2f, padT + plotH, paint)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d", Locale.US)
            val every = ((days.size + 6) / 7).coerceAtLeast(1)
            days.forEachIndexed { i, d ->
                if (i % every != 0 && i != days.size - 1) return@forEachIndexed
                val t = d.date.format(fmt); val tw = paint.measureText(t)
                nc.drawText(t, (slot * i + slot / 2 - tw / 2).coerceIn(0f, w - tw), h - labelPx * 0.4f, paint)
            }
        }
    }
}
