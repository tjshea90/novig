package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.tracker.SlateProfit
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.WalletProfit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/** The wallet's all-time figure as the profit card shows it ([WalletProfit.Money], null before a reading) and, when there is none, why. */
data class WalletMoneyUi(val money: WalletProfit.Money? = null, val note: String? = null)

/** The profit card's words, pure, for tests (Tj, 2026-10-19, PL: "should show 0 profit or loss ... so I know if I'm making or losing money"). */
object ProfitText {
    private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

    /** A profit, with no sign at zero ("$0.00", never "+$0.00"). */
    fun pl(x: Double): String = if (abs(x) < 0.005) "$0.00" else Format.signedMoney(x)

    fun pct(x: Double): String = if (abs(x) < 0.0005) "0.0%" else (if (x > 0) "+" else "") + String.format(Locale.US, "%.1f%%", x * 100)

    /** "Mon 1:05 PM", Eastern: a time on the chart always says its day (a slate's last result is often after midnight). */
    fun dayTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(SlateProfit.ET).format(dayTimeFmt)

    fun dayName(d: LocalDate): String = d.format(dayFmt)

    private fun bets(n: Int) = "$n open bet" + if (n == 1) "" else "s"

    /** The all-time figure's sum: what the wallet holds and what was put in; or, with no wallet figure, the settled bets it is taken from. */
    fun allTimeHow(book: SlateProfit.Book, w: WalletMoneyUi, now: Long): String {
        val m = w.money
        if (m != null) {
            val moves = if (m.takenOut > 0.005) " (${Format.money(m.putIn)} in, ${Format.money(m.takenOut)} out)" else ""
            return "Vigilant wallet ${Format.money(m.value)}: ${Format.money(m.cash)} cash + ${Format.money(m.openCost)} in ${bets(m.openBets)}, minus the ${Format.money(m.netIn)} put in$moves " +
                "(Novig's record of your transfers). Open bets count at what they cost until they settle. Read ${Format.age(m.atMs, now)}."
        }
        val why = w.note?.let { " $it" } ?: ""
        return if (book.settled == 0) "No settled bets yet.$why"
        else "From the ${book.settled} settled bets: ${Format.money(book.settledStaked)} staked, ${Format.money(book.returned)} paid back.$why"
    }

    /** Today's games in one line of numbers: $0.00 and why before a result, what is open after. */
    fun today(day: SlateProfit.Day): String = when {
        day.empty -> "No bets on today's games yet: \$0.00."
        day.settled == 0 -> "No results yet: \$0.00 until the first of today's games is graded. ${Format.money(day.openStaked)} is at stake on ${bets(day.open)}, not counted until they settle."
        else -> "${day.settled} settled: ${Format.money(day.settledStaked)} staked, ${Format.money(day.returned)} paid back = ${pl(day.profit)}." +
            if (day.open > 0) " ${day.open} still open (${Format.money(day.openStaked)} at stake, not counted yet)." else ""
    }

    /** The heading of today's view: whose games, and where they stand. */
    fun todayLine(day: SlateProfit.Day, now: Long): String = "${dayName(day.date)} games" + when {
        day.empty -> ""
        day.firstStartMs > now -> " · first game ${dayTime(day.firstStartMs)}"
        day.finished -> " · last result ${dayTime(day.endMs)}"
        else -> " · ${day.open} still open"
    }

    /** Several days in one line of numbers. */
    fun days(days: List<SlateProfit.Day>): String {
        val settled = days.sumOf { it.settled }
        val staked = days.sumOf { it.settledStaked }
        val profit = days.sumOf { it.profit }
        val open = days.sumOf { it.open }
        val base = if (settled == 0) "No results in these days yet: \$0.00." else "$settled settled: ${Format.money(staked)} staked, ${Format.money(staked + profit)} paid back = ${pl(profit)}."
        return base + if (open > 0) " ${bets(open)} on these days wait for results (${Format.money(days.sumOf { it.openStaked })} at stake, not counted yet)." else ""
    }

    /** The open bets on games after today. */
    fun later(d: SlateProfit.Day): String = "Also ${bets(d.open)} on later games (${Format.money(d.openStaked)} at stake, through ${dayName(d.date)}): each counts on its game's day."

    fun periodLabel(period: TrackerPeriod, first: LocalDate?): String = when (period) {
        TrackerPeriod.TODAY -> "Today"
        TrackerPeriod.DAYS_7 -> "Last 7 days"
        TrackerPeriod.DAYS_30 -> "Last 30 days"
        TrackerPeriod.ALL -> first?.let { "Since ${it.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}" } ?: "All time"
    }
}

/**
 * The Tracker's profit card (Tj, 2026-10-19, PL: "Think of the most logical way to make a chart which shows daily profit/loss and total all time profit/loss that is logical and shows an accurate number so
 * I know if I'm making or losing money on my bets"):
 *  - on top, ALL-TIME profit in real money: the Vigilant wallet now (cash + open bets at cost) minus what was put in, from Novig's transfer record ([WalletProfit]); with no wallet reading, the settled
 *    bets' total, saying so;
 *  - below, the Tracker's own period (its Today / 7 days / 30 days / All chips): Today is today's games, $0.00 until the first result, as a running line from the first game; a longer period is a running
 *    total and a bar per day, then the days one by one;
 *  - a bet that has not settled is money at stake, never a loss: it is counted on its game's day once it settles.
 */
@Composable
fun ProfitChartCard(bets: List<TrackedBet>, period: TrackerPeriod, wallet: WalletMoneyUi = WalletMoneyUi(), modifier: Modifier = Modifier) {
    val now = rememberNow(60_000)
    val book = remember(bets, now / 60_000L) { SlateProfit.book(bets, now) }
    Card(modifier.fillMaxWidth().testTag("profitChart"), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AllTime(book, wallet, now)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when (period) {
                TrackerPeriod.TODAY -> TodayView(bets, book, now)
                else -> DaysView(SlateProfit.lastDays(book, now, period.days), ProfitText.periodLabel(period, book.days.firstOrNull()?.date))
            }
            SlateProfit.later(book, now)?.let { Text(ProfitText.later(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitLater")) }
            Text(
                "A day is the day of its games (Eastern, 5 am to 5 am, so a late game stays on the night it began). Its profit is what its settled bets paid back minus what they cost; the days count the bets " +
                    "shown here, while the all-time figure is the wallet's real money (fees and maker credits included).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllTime(book: SlateProfit.Book, wallet: WalletMoneyUi, now: Long) {
    val m = wallet.money
    val profit = m?.profit ?: book.profit
    val pct = if (m != null) m.pct else book.pct
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("All-time profit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(ProfitText.pl(profit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = moneyColor(profit), modifier = Modifier.testTag("profitAllTime"))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(pct?.let(ProfitText::pct) ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = moneyColor(profit))
            Text(if (m != null) "on ${Format.money(m.netIn)} put in" else "on ${Format.money(book.settledStaked)} staked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(ProfitText.allTimeHow(book, wallet, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitAllTimeHow"))
    if (m != null && book.settled > 0) {
        Text("The settled bets shown here: ${ProfitText.pl(book.profit)} on ${Format.money(book.settledStaked)} staked.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Today's games: their numbers, and their running profit from the first game to the last result (or now). */
@Composable
private fun TodayView(bets: List<TrackedBet>, book: SlateProfit.Book, now: Long) {
    val day = SlateProfit.day(book, SlateProfit.today(now))
    val points = remember(bets, day.date, now / 60_000L) { SlateProfit.curve(bets, day.date) }
    Text(ProfitText.todayLine(day, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitDayLine"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledValue("Today", ProfitText.pl(day.profit), valueColor = moneyColor(day.profit), modifier = Modifier.weight(1.2f).testTag("profitInView"))
        LabeledValue("Return on staked", day.changePct?.let(ProfitText::pct) ?: "—", valueColor = moneyColor(day.profit), modifier = Modifier.weight(1.2f))
        LabeledValue("Record", "${day.won}-${day.lost}", modifier = Modifier.weight(0.8f))
    }
    Text(ProfitText.today(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitChartReadout"))
    if (points.isEmpty()) return
    var selected by remember(day.date) { mutableStateOf<SlateProfit.Point?>(null) }
    selected?.let { p ->
        Text("${ProfitText.dayTime(p.tMs)} · ${p.label} ${ProfitText.pl(p.profit)} on ${Format.money(p.stake)} · today so far ${ProfitText.pl(p.cum)}", style = MaterialTheme.typography.labelSmall)
    }
    val start = minOf(day.firstStartMs, points.first().tMs)
    val end = if (day.finished) maxOf(day.endMs, points.last().tMs) else maxOf(now, points.last().tMs)
    DayLine(points, start, end, endsNow = !day.finished, selected = selected, onTap = { selected = it }, modifier = Modifier.fillMaxWidth().height(200.dp))
}

/** Several days: the running total, a bar per day, then the days one by one. */
@Composable
private fun DaysView(days: List<SlateProfit.Day>, label: String) {
    if (days.isEmpty()) return
    val profit = days.sumOf { it.profit }
    val staked = days.sumOf { it.settledStaked }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledValue(label, ProfitText.pl(profit), valueColor = moneyColor(profit), modifier = Modifier.weight(1.2f).testTag("profitInView"))
        LabeledValue("Return on staked", if (staked > 0) ProfitText.pct(profit / staked) else "—", valueColor = moneyColor(profit), modifier = Modifier.weight(1.2f))
        LabeledValue("Up / down days", "${days.count { it.profit > 0.005 }} / ${days.count { it.profit < -0.005 }}", modifier = Modifier.weight(1f))
    }
    Text(ProfitText.days(days), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profitChartReadout"))
    if (days.none { it.settled > 0 }) return
    val running = remember(days) { days.runningFold(0.0) { acc, d -> acc + d.profit }.drop(1) }
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    selected?.let { i ->
        val d = days[i]
        Text(
            "${ProfitText.dayName(d.date)}: ${ProfitText.pl(d.profit)}" + (d.changePct?.let { " (${ProfitText.pct(it)})" } ?: "") + " · staked ${Format.money(d.settledStaked)} · ${d.won}-${d.lost}" +
                (if (d.open > 0) " · ${d.open} open" else "") + " · running total ${ProfitText.pl(running[i])}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Text("Running total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    RunningLine(days, running, selected, { selected = it }, Modifier.fillMaxWidth().height(150.dp))
    Text("Each day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    DayBars(days, selected, { selected = it }, Modifier.fillMaxWidth().height(150.dp))
    Text("Day by day", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    val listed = days.filter { !it.empty }.asReversed()
    listed.take(MAX_LIST_DAYS).forEach { d ->
        Row(Modifier.fillMaxWidth().testTag("profitDay-${d.date}"), verticalAlignment = Alignment.CenterVertically) {
            Text(ProfitText.dayName(d.date), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(ProfitText.pl(d.profit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = moneyColor(d.profit), modifier = Modifier.padding(end = 8.dp))
            Text(d.changePct?.let(ProfitText::pct) ?: "—", style = MaterialTheme.typography.labelSmall, color = moneyColor(d.profit), modifier = Modifier.padding(end = 8.dp))
            Text(Format.money(d.settledStaked) + " staked" + if (d.open > 0) " · ${d.open} open" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (listed.size > MAX_LIST_DAYS) Text("…and ${listed.size - MAX_LIST_DAYS} earlier days.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val MAX_LIST_DAYS = 30

/** Today's running profit: flat from 0 at the first game, one step per moment results came in, with a labelled zero and the day named on both times. */
@Composable
private fun DayLine(points: List<SlateProfit.Point>, startMs: Long, endMs: Long, endsNow: Boolean, selected: SlateProfit.Point?, onTap: (SlateProfit.Point?) -> Unit, modifier: Modifier) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val span = (endMs - startMs).coerceAtLeast(60_000L)
    val a = ProfitText.dayTime(startMs)
    val b = if (endsNow) "now" else ProfitText.dayTime(endMs)
    Box(
        modifier.testTag("profitCanvas").semantics { contentDescription = "Today's profit, from $a to $b" }
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
            nc.drawText("\$0", 4f, y(0.0) - 4f, paint)
            val last = ProfitText.pl(prev)
            nc.drawText(last, (w - paint.measureText(last) - 4f).coerceAtLeast(0f), (y(prev) - 6f).coerceAtLeast(labelPx), paint)
            nc.drawText(a, 0f, h - labelPx * 0.4f, paint)
            nc.drawText(b, w - paint.measureText(b), h - labelPx * 0.4f, paint)
        }
    }
}

/** The running total over the days, one point at the end of each day (above the bar of that day), from $0 at the start of the first. */
@Composable
private fun RunningLine(days: List<SlateProfit.Day>, running: List<Double>, selected: Int?, onTap: (Int?) -> Unit, modifier: Modifier) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        modifier.testTag("profitRunning").semantics { contentDescription = "Running total over ${days.size} days: ${ProfitText.pl(running.last())}" }
            .pointerInput(days) { detectTapGestures { pos -> tap((pos.x / size.width * days.size).toInt().coerceIn(0, days.size - 1)) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val padB = labelPx * 2.2f; val padT = labelPx * 1.2f
            val plotH = h - padB - padT
            var lo = minOf(0.0, running.min()); var hi = maxOf(0.0, running.max())
            if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
            val pad = (hi - lo) * 0.1; lo -= pad; hi += pad
            fun y(v: Double) = padT + (plotH * (1 - (v - lo) / (hi - lo))).toFloat()
            val slot = w / days.size
            drawLine(axis, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            val path = Path(); path.moveTo(0f, y(0.0))
            running.forEachIndexed { i, v -> path.lineTo(slot * i + slot / 2, y(v)) }
            val end = running.last()
            drawPath(path, if (end >= 0) up else down, style = Stroke(width = 2.dp.toPx()))
            drawCircle(if (end >= 0) up else down, 3.5.dp.toPx(), Offset(slot * (days.size - 1) + slot / 2, y(end)))
            selected?.let { i -> drawLine(axis, Offset(slot * i + slot / 2, padT), Offset(slot * i + slot / 2, padT + plotH), strokeWidth = 1.dp.toPx()); drawCircle(if (running[i] >= 0) up else down, 4.dp.toPx(), Offset(slot * i + slot / 2, y(running[i]))) }
            val paint = android.graphics.Paint().apply { color = label.toArgb(); textSize = labelPx; isAntiAlias = true }
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("\$0", 4f, y(0.0) - 4f, paint)
            val last = ProfitText.pl(end)
            nc.drawText(last, (w - paint.measureText(last) - 4f).coerceAtLeast(0f), (y(end) - 6f).coerceAtLeast(labelPx), paint)
            val fmt = DateTimeFormatter.ofPattern("EEE M/d", Locale.US)
            val first = days.first().date.format(fmt); val lastDay = days.last().date.format(fmt)
            nc.drawText(first, 0f, h - labelPx * 0.4f, paint)
            nc.drawText(lastDay, w - paint.measureText(lastDay), h - labelPx * 0.4f, paint)
        }
    }
}

/** One bar per day around a labelled zero (a day with no result has none); the tapped one is outlined. */
@Composable
private fun DayBars(days: List<SlateProfit.Day>, selected: Int?, onTap: (Int?) -> Unit, modifier: Modifier) {
    val up = Edge.colors.positive
    val down = Edge.colors.negative
    val axis = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val labelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        modifier.testTag("profitBars").semantics { contentDescription = "Profit of each of the last ${days.size} days" }
            .pointerInput(days) { detectTapGestures { pos -> tap((pos.x / size.width * days.size).toInt().coerceIn(0, days.size - 1)) } },
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
                if (d.settled == 0) return@forEachIndexed
                val cx = slot * i + slot / 2
                val top = y(maxOf(d.profit, 0.0)); val bottom = y(minOf(d.profit, 0.0))
                drawRect(if (d.profit >= 0) up else down, Offset(cx - bar / 2, top), androidx.compose.ui.geometry.Size(bar, (bottom - top).coerceAtLeast(1.5f)))
                if (i == selected) drawRect(ink, Offset(cx - bar / 2, top), androidx.compose.ui.geometry.Size(bar, (bottom - top).coerceAtLeast(1.5f)), style = Stroke(1.5.dp.toPx()))
            }
            drawLine(axis, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx())
            val paint = android.graphics.Paint().apply { color = label.toArgb(); textSize = labelPx; isAntiAlias = true }
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText(Format.signedMoney(hi), 2f, padT, paint)
            if (lo < 0) nc.drawText(Format.signedMoney(lo), 2f, padT + plotH, paint)
            val fmt = DateTimeFormatter.ofPattern("M/d", Locale.US)
            val every = ((days.size + 6) / 7).coerceAtLeast(1)
            days.forEachIndexed { i, d ->
                if (i % every != 0 && i != days.size - 1) return@forEachIndexed
                val t = d.date.format(fmt); val tw = paint.measureText(t)
                nc.drawText(t, (slot * i + slot / 2 - tw / 2).coerceIn(0f, w - tw), h - labelPx * 0.4f, paint)
            }
        }
    }
}
