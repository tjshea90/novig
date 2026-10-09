package com.tjshea.vigilant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.alerts.EvAlert
import com.tjshea.vigilant.engine.Odds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The +EV push alerts (Tj, 2026-09-28: "sends me an android push notification and I can click on the
 * notification and it will open the exact bet in novig immediately, just like the cno widget already
 * does"). One notification per bet, on a high-importance channel (it pops up over whatever is on
 * screen); tapping it opens the bet's link in Novig's app, as a widget tap does ([MainActivity]'s
 * `launchNovig`): the bet slip itself, or the game when only that is known.
 */
object EvAlerts {
    const val CHANNEL = "ev_alerts"

    /** The quiet channel for "Tracked ✓" (it replaces the alert in place: no second pop-up). */
    const val DONE_CHANNEL = "ev_alerts_done"

    /** The notification's "✓ Placed" button, and the confirmation's "Undo". */
    const val ACTION_PLACED = "com.tjshea.vigilant.action.ALERT_PLACED"
    const val ACTION_UNDO = "com.tjshea.vigilant.action.ALERT_UNDO"
    const val EXTRA_ALERT = "alert"

    /** How long "Tracked ✓ · Undo" stays before it takes itself down. */
    const val DONE_SHOWN_FOR_MS = 60_000L

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Alert ids live above the scan and auto-scan notifications' own. */
    private const val ID_BASE = 1_000

    /** An alert whose game hasn't started is taken down after this: its price has likely moved. */
    const val SHOWN_FOR_MS = 20 * 60_000L

    /** "+4.1% EV · Justin Jefferson Under 69.5". */
    fun title(a: EvAlert): String = "+${String.format(Locale.US, "%.1f", a.ev * 100)}% EV · ${a.bet}"

    /** "+117 on Novig · Player Receiving Yards · Vikings @ Buccaneers, 8:20 PM". */
    fun text(a: EvAlert, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val odds = if (a.american > 0) "+${a.american}" else "${a.american}"
        val start = a.startsAtMs?.let { ", " + SimpleDateFormat("EEE h:mm a", Locale.US).apply { timeZone = zone }.format(Date(it)) }.orEmpty()
        return "$odds on ${AppBook.name} · ${a.market} · ${a.event}$start"
    }

    /** "4 of 5 books agree · found by CNO". */
    fun detail(a: EvAlert): String = "${a.agreeing} of ${a.books} books agree · found by ${a.scanner}"

    fun id(a: EvAlert): Int = ID_BASE + (abs(a.dedupeKey.hashCode()) % 1_000_000)

    /**
     * What tapping any of Vigilant's alerts opens: Vigilant itself, full screen (out of the mini window too), never the bet in Novig (Tj,
     * 2026-09-30: "when I click on anything in the push notifications for vigilant, instead of opening the bet, it opens the vigilant app in
     * full screen"). The alert is taken down by the tap (setAutoCancel).
     */
    fun openVigilant(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "✓ Placed" or "✓ Placed $5": the amount the bet is tracked at (Settings' bet-slip amount; $1 when there is none). */
    fun placedLabel(a: EvAlert): String = "✓ Placed" + (a.stake?.let { " $" + com.tjshea.vigilant.data.novig.NovigLinks.amountText(it) } ?: "")

    /** [a] carried in a notification button's intent (the button opens no screen: [AlertActionReceiver] does the work). */
    fun actionIntent(context: Context, a: EvAlert, action: String): Intent =
        Intent(context, AlertActionReceiver::class.java).setAction(action).putExtra(EXTRA_ALERT, json.encodeToString(EvAlert.serializer(), a))

    /** The alert a button's [intent] carries; null when it's missing or unreadable. */
    fun alertOf(intent: Intent): EvAlert? =
        intent.getStringExtra(EXTRA_ALERT)?.let { runCatching { json.decodeFromString(EvAlert.serializer(), it) }.getOrNull() }

    private fun broadcast(context: Context, a: EvAlert, action: String): PendingIntent =
        // A request code per alert and button: PendingIntents that differ only in extras are one and the same.
        PendingIntent.getBroadcast(
            context, id(a) * 2 + if (action == ACTION_UNDO) 1 else 0, actionIntent(context, a, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * What the button did (Tj, 2026-09-29: "a fast way for me to mark a bet as placed … from a push notification"):
     * "✓ Placed" tracks the bet at the alert's price and stake and hides it from every list, like the widget's ✓, and
     * the alert turns into a quiet "Tracked ✓ · Undo"; "Undo" puts it all back. Works with Vigilant closed.
     */
    suspend fun handle(context: Context, container: AppContainer, action: String?, a: EvAlert, now: Long = System.currentTimeMillis()) {
        val nm = NotificationManagerCompat.from(context)
        when (action) {
            ACTION_PLACED -> {
                val bet = com.tjshea.vigilant.data.alerts.AlertPlacement.place(a, a.stake, container.tracker, container.placed, now)
                if (bet != null) postDone(context, a, "Tracked ✓ ${a.bet}", "${com.tjshea.vigilant.app.ui.Format.money(bet.stake)} at ${Odds.formatAmerican(a.american)} · in the Tracker (change the stake or the price you got there)", undo = true)
                else postDone(context, a, "Couldn't track ${a.bet}", "Vigilant couldn't save the bet (is the phone's storage full?). Track it from the +EV or CNO tab.", undo = false)
            }
            ACTION_UNDO -> {
                com.tjshea.vigilant.data.alerts.AlertPlacement.undo(a, container.tracker, container.placed)
                runCatching { nm.cancel(id(a)) }
            }
        }
    }

    private fun postDone(context: Context, a: EvAlert, title: String, text: String, undo: Boolean) {
        if (!ScanService.canNotify(context) || !NotifyGate.allow()) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(context)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openVigilant(context, id(a)))
            .setTimeoutAfter(DONE_SHOWN_FOR_MS)
            .apply { if (undo) addAction(0, "Undo", broadcast(context, a, ACTION_UNDO)) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id(a), n) }
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Tracked from an alert", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A quiet confirmation that a bet was tracked from a +EV alert's ✓ Placed button, with Undo."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "+EV alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A new bet at or over your alert minimum that several books agree on. Tap to open Vigilant; ✓ Placed tracks it."
            },
        )
    }

    /** Posts [alerts]; returns how many went out (none when notifications aren't allowed). */
    fun post(context: Context, alerts: List<EvAlert>, now: Long = System.currentTimeMillis()): Int {
        if (!ScanService.canNotify(context) || !NotifyGate.allow()) return 0
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        var posted = 0
        for (a in alerts) {
            val id = id(a)
            val tap = openVigilant(context, id)
            val body = text(a) + "\n" + detail(a)
            val timeout = a.startsAtMs?.let { minOf(SHOWN_FOR_MS, (it - now).coerceAtLeast(60_000L)) } ?: SHOWN_FOR_MS
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .withWallet(context)
                .setContentTitle(title(a))
                .setContentText(text(a))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Tapping opens Vigilant full screen and takes the alert down (Tj, 2026-09-30); ✓ Placed works from the alert before that tap.
                .setAutoCancel(true)
                .addAction(0, placedLabel(a), broadcast(context, a, ACTION_PLACED))
                .setTimeoutAfter(timeout)
                .setWhen(now)
                .setShowWhen(true)
                .setContentIntent(tap)
                .build()
            if (runCatching { nm.notify(id, n) }.isSuccess) posted++
        }
        return posted
    }
}
