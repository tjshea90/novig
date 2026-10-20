package com.tjshea.vigilant.app.kalshi

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.R
import com.tjshea.vigilant.app.ScanService
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.util.Locale

/**
 * Notifications for what happens at Kalshi (Tj, 2026-10-20, CH32d): a bet placed, a bid filled, and an order that was not placed or timed out, the way the Novig side tells him.
 * A channel of its own, so the Novig notifications and their settings are untouched.
 */
object KalshiNotes {
    const val CHANNEL = "kalshi_orders"
    private var next = 6_100_000

    private fun ensureChannel(app: Application) {
        app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Kalshi orders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kalshi bets placed, bids filled, and orders that failed or timed out."
                enableVibration(true)
            },
        )
    }

    fun money(x: Double) = String.format(Locale.US, "$%.2f", x)

    fun placedTitle(bet: TrackedBet, source: String) =
        "$source $${String.format(Locale.US, "%.2f", bet.stake)}${bet.evPercentAtBet?.let { String.format(Locale.US, " · %+.1f%% EV", it * 100) }.orEmpty()} · ${bet.selection}"

    fun betText(bet: TrackedBet) = "${bet.marketLabel} · ${bet.eventName} · ${bet.contracts?.div(100)?.let { "$it contracts at " }.orEmpty()}${String.format(Locale.US, "%.0f", bet.price * 100)}¢"

    /** A bet placed at Kalshi by hand or by the auto-bet ([source] "Kalshi bet" / "Kalshi auto-bet"). */
    fun placed(app: Application, bet: TrackedBet, source: String) = post(app, placedTitle(bet, source), betText(bet))

    /** A resting bid that filled (a new fill or more of one). */
    fun bidFilled(app: Application, bet: TrackedBet) = post(app, "Kalshi bid filled ${money(bet.stake)} · ${bet.selection}", betText(bet))

    /** An order that did not go through (refused, not filled, failed, or an answer that never came). */
    fun failed(app: Application, title: String, text: String) = post(app, title, text)

    @SuppressLint("MissingPermission")
    private fun post(app: Application, title: String, text: String) {
        runCatching {
            if (!ScanService.canNotify(app)) return
            ensureChannel(app)
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_STATUS).setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(app).notify(next++, n)
        }
    }
}
