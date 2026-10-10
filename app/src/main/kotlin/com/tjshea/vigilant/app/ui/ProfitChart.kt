package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.tjshea.vigilant.data.tracker.ProfitSeries
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

/**
 * The Stats tab's profit graph (Tj, 2026-10-10): running profit over time, with quick ranges like a stock chart (Today, Yesterday, 2 days, 3 days, This week, 7 days, 30 days, All time), pinch to zoom,
 * drag to scroll left and right, tap a point to read it, double-tap to go back to the range, and a full-screen view. The numbers above the line are what happened INSIDE the window being looked at.
 */
@Composable
fun ProfitChartCard(bets: List<TrackedBet>, modifier: Modifier = Modifier) {
    val now = rememberNow(60_000)
    var range by rememberSaveable { mutableStateOf(ProfitSeries.Range.ALL) }
    var full by rememberSaveable { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().testTag("profitChart"), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfitChartBody(bets, now, range, { range = it }, tall = false, onFull = { full = true })
        }
    }
    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize().testTag("profitChartFull"), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Profit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { full = false }, modifier = Modifier.testTag("profitChartClose")) { Text("Close") }
                    }
                    ProfitChartBody(bets, now, range, { range = it }, tall = true, onFull = null)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ProfitChartBody(bets: List<TrackedBet>, now: Long, range: ProfitSeries.Range, onRange: (ProfitSeries.Range) -> Unit, tall: Boolean, onFull: (() -> Unit)?) {
    val points = remember(bets) { ProfitSeries.points(bets) }
    val firstMs = points.firstOrNull()?.tMs ?: now
    val minT = firstMs.coerceAtMost(now - ProfitSeries.DAY_MS)
    val maxT = maxOf(now, points.lastOrNull()?.tMs ?: now)
    val zone = remember { ZoneId.systemDefault() }
    val preset = remember(range, now / 60_000L, firstMs) { range.bounds(now, firstMs, zone).let { (a, b) -> ChartView.clamp(ChartView.Window(a, b), minT, maxT) } }
    var startMs by remember(range, firstMs) { mutableLongStateOf(preset.start) }
    var endMs by remember(range, firstMs) { mutableLongStateOf(preset.end) }
    // The preset's end follows the clock while Tj has not touched the window.
    var touched by remember(range, firstMs) { mutableStateOf(false) }
    LaunchedEffect(preset, touched) { if (!touched) { startMs = preset.start; endMs = preset.end } }
    val view = ChartView.Window(startMs, endMs)
    var selected by remember(range, firstMs) { mutableStateOf<ProfitSeries.Point?>(null) }
    val inView = remember(points, startMs, endMs) { ProfitSeries.window(points, startMs, endMs) }
    val change = inView.profit

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Profit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onFull != null) TextButton(onClick = onFull, modifier = Modifier.testTag("profitFullScreen")) { Text("Full screen") }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProfitSeries.Range.entries.forEach { r ->
            FilterChip(selected = r == range && !touched, onClick = { touched = false; onRange(r); if (r == range) { startMs = preset.start; endMs = preset.end } }, label = { Text(r.label, maxLines = 1) }, modifier = Modifier.testTag("profitRange-${r.name}"))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LabeledValue("Profit in view", Format.signedMoney(change), valueColor = moneyColor(change), modifier = Modifier.testTag("profitInView"))
        LabeledValue("Profit %", inView.roi?.let { Format.evPercent(it) } ?: "—", valueColor = moneyColor(inView.roi ?: 0.0))
        LabeledValue("Staked", Format.money(inView.staked))
        LabeledValue("Bets", "${inView.bets}")
    }
    val sel = selected
    Text(
        if (sel != null) "${SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).format(Date(sel.tMs))} · profit so far ${Format.signedMoney(sel.cum)} · this bet ${Format.signedMoney(sel.profit)} on ${Format.money(sel.stake)}"
        else "Pinch to zoom, drag to scroll, tap a point to read it, double-tap to reset. Profit so far: ${Format.signedMoney(ProfitSeries.valueAt(points, endMs))}.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitChartReadout"),
    )
    if (points.size < 2) {
        Text("The graph needs two settled bets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    ProfitCanvas(
        points, view, minT, maxT, selected,
        modifier = Modifier.fillMaxWidth().then(if (tall) Modifier.weight(1f) else Modifier.height(220.dp)),
        onTransform = { focus, dx, zoom ->
            touched = true
            selected = null
            val w = ChartView.zoom(view, focus, zoom.toDouble(), minT, maxT).let { ChartView.pan(it, dx.toDouble(), minT, maxT) }
            startMs = w.start; endMs = w.end
        },
        onTap = { selected = it },
        onReset = { touched = false; startMs = preset.start; endMs = preset.end; selected = null },
    )
}

@Composable
private fun ProfitCanvas(
    points: List<ProfitSeries.Point>, view: ChartView.Window, minT: Long, maxT: Long, selected: ProfitSeries.Point?, modifier: Modifier,
    onTransform: (focus: Double, dxFraction: Float, zoom: Float) -> Unit, onTap: (ProfitSeries.Point?) -> Unit, onReset: () -> Unit,
) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val zone = remember { ZoneId.systemDefault() }
    val desc = "Profit graph from ${SimpleDateFormat("MMM d h:mm a", Locale.US).format(Date(view.start))} to ${SimpleDateFormat("MMM d h:mm a", Locale.US).format(Date(view.end))}"
    Box(
        modifier
            .semantics { contentDescription = desc }
            .testTag("profitCanvas")
            .pointerInput(view, minT, maxT) {
                // Pinch (two fingers) and a sideways drag (one finger) belong to the chart; an up-and-down drag is left to the list, so the Stats tab still scrolls over the graph.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var panning = false
                    var totalX = 0f
                    var totalY = 0f
                    val slop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid()
                            if (z != 1f || pan.x != 0f) {
                                onTransform((c.x / size.width).toDouble(), pan.x / size.width, z)
                                event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                            }
                            panning = true
                        } else if (pressed == 1) {
                            val ch = event.changes.first { it.pressed }
                            val d = ch.positionChange()
                            totalX += d.x; totalY += d.y
                            if (!panning && abs(totalX) > slop && abs(totalX) > abs(totalY)) panning = true
                            if (panning && d.x != 0f) { onTransform((ch.position.x / size.width).toDouble(), d.x / size.width, 1f); ch.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(points, view) {
                detectTapGestures(
                    onDoubleTap = { onReset() },
                    onTap = { pos ->
                        val t = view.start + (pos.x / size.width) * view.span
                        val i = ProfitSeries.firstAtOrAfter(points, t.toLong())
                        val near = listOfNotNull(points.getOrNull(i - 1), points.getOrNull(i)).minByOrNull { abs(it.tMs - t) }
                        onTap(near)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padL = 4f
            val padB = labelPx * 2.2f
            val padT = labelPx * 1.2f
            val plotH = h - padB - padT
            // Visible values: the curve inside the window plus its value at both edges, so the line runs to the edges.
            val i0 = ProfitSeries.firstAtOrAfter(points, view.start)
            val i1 = ProfitSeries.firstAtOrAfter(points, view.end + 1)
            val leftV = ProfitSeries.valueAt(points, view.start)
            val rightV = ProfitSeries.valueAt(points, view.end)
            var lo = minOf(leftV, rightV)
            var hi = maxOf(leftV, rightV)
            for (k in i0 until i1) { lo = minOf(lo, points[k].cum); hi = maxOf(hi, points[k].cum) }
            if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
            val pad = (hi - lo) * 0.08
            lo -= pad; hi += pad
            fun x(t: Long) = padL + ((t - view.start).toDouble() / view.span * (w - padL)).toFloat()
            fun y(v: Double) = padT + (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
            // Zero line, when zero is in view.
            if (lo < 0 && hi > 0) drawLine(axis, Offset(padL, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            // The line: from the value at the left edge, through every bet in view (thinned to about one point a pixel), to the value at the right edge.
            val path = Path()
            path.moveTo(x(view.start), y(leftV))
            val stride = ((i1 - i0) / (w / 1.5f)).toInt().coerceAtLeast(1)
            var k = i0
            while (k < i1) { path.lineTo(x(points[k].tMs), y(points[k].cum)); k += stride }
            if (i1 > i0) path.lineTo(x(points[i1 - 1].tMs), y(points[i1 - 1].cum))
            path.lineTo(x(view.end), y(rightV))
            drawPath(path, if (rightV >= leftV) up else down, style = Stroke(width = 2.dp.toPx()))
            selected?.takeIf { it.tMs in view.start..view.end }?.let { s ->
                drawLine(axis, Offset(x(s.tMs), padT), Offset(x(s.tMs), padT + plotH), strokeWidth = 1.dp.toPx())
                drawCircle(if (s.cum >= 0) up else down, 4.dp.toPx(), Offset(x(s.tMs), y(s.cum)))
            }
            // Labels: the top and bottom values, and times along the bottom.
            val paint = android.graphics.Paint().apply { color = label.toArgb(); textSize = labelPx; isAntiAlias = true }
            drawContext.canvas.nativeCanvas.drawText(Format.signedMoney(hi - pad), padL + 2f, padT + labelPx, paint)
            drawContext.canvas.nativeCanvas.drawText(Format.signedMoney(lo + pad), padL + 2f, padT + plotH - 2f, paint)
            val fmtDay = SimpleDateFormat("MMM d", Locale.US)
            val fmtHour = SimpleDateFormat("h a", Locale.US)
            val perTick = 5
            ChartView.ticks(view, perTick, zone).forEach { t ->
                val tx = x(t)
                drawLine(axis, Offset(tx, padT + plotH), Offset(tx, padT + plotH + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                val text = if (view.span > 2 * ProfitSeries.DAY_MS) fmtDay.format(Date(t)) else if (Instant.ofEpochMilli(t).atZone(zone).hour == 0) fmtDay.format(Date(t)) else fmtHour.format(Date(t))
                val tw = paint.measureText(text)
                drawContext.canvas.nativeCanvas.drawText(text, (tx - tw / 2).coerceIn(0f, w - tw), h - labelPx * 0.4f, paint)
            }
        }
    }
}
