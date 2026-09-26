package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.engine.Odds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Display formatting. Kept free of Compose so it's trivially testable. */
object Format {
    private val dayTime = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    private val date = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun evPercent(ev: Double): String = (if (ev >= 0) "+" else "−") + String.format(Locale.US, "%.2f%%", abs(ev) * 100)

    /** One decimal, rounded ("+7.2%" for 7.18%), for the mini window's narrow column. */
    fun evPercentShort(ev: Double): String = (if (ev >= 0) "+" else "−") + String.format(Locale.US, "%.1f%%", abs(ev) * 100)

    fun percent(p: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f%%", p * 100)

    fun american(probability: Double): String = Odds.formatAmerican(Odds.probabilityToAmerican(probability.coerceIn(0.001, 0.999)))

    fun money(dollars: Double): String = when {
        abs(dollars) >= 10_000 -> "$" + String.format(Locale.US, "%,d", dollars.roundToLong())
        else -> (if (dollars < 0) "−$" else "$") + String.format(Locale.US, "%,.2f", abs(dollars))
    }

    fun signedMoney(dollars: Double): String = (if (dollars >= 0) "+" else "") + money(dollars)

    fun startTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayTime.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun shortDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        date.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun age(sinceMs: Long?, nowMs: Long): String {
        if (sinceMs == null) return "never"
        val s = ((nowMs - sinceMs) / 1000).coerceAtLeast(0)
        return when {
            s < 5 -> "just now"
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            s < 86_400 -> "${s / 3600}h ago"
            else -> "${s / 86_400}d ago"
        }
    }

    fun kellyLabel(multiplier: Double): String = when (multiplier) {
        0.125 -> "⅛ Kelly"
        0.25 -> "¼ Kelly"
        0.5 -> "½ Kelly"
        1.0 -> "Full Kelly"
        else -> "${(multiplier * 100).toInt()}% Kelly"
    }

    /** A Novig contract pays $0.01, so N contracts is $N/100 of payout. */
    fun contractsAsPayout(contracts: Long): String = money(contracts / 100.0)
}
