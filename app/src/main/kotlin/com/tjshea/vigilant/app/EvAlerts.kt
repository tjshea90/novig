package com.tjshea.vigilant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.alerts.EvAlert
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

    /** "4 of 5 books agree · found by CNO". Plus a word when the tap opens only the game. */
    fun detail(a: EvAlert): String =
        "${a.agreeing} of ${a.books} books agree · found by ${a.scanner}" + if (!a.exact) " · opens the game: the bet is under ${a.market}" else ""

    fun id(a: EvAlert): Int = ID_BASE + (abs(a.dedupeKey.hashCode()) % 1_000_000)

    /** What tapping opens: [a]'s link in Novig's app (never a browser when it's installed), else Novig itself. */
    fun intent(context: Context, a: EvAlert): Intent {
        // With the stake Settings asks for in the bet slip (Tj, 2026-09-28), a CNO link resolved late included.
        val link = com.tjshea.vigilant.data.novig.NovigLinks.withStake(a.link, a.stake)
        val installed = context.packageManager.getLaunchIntentForPackage(MiniWindow.NOVIG_PACKAGE) != null
        if (link == null || (link.startsWith("novigapp://") && !installed && AppBook.isNovig)) return AppBook.homeIntent(context)
        return Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .apply { if (installed && link.startsWith("novigapp://")) setPackage(MiniWindow.NOVIG_PACKAGE) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "+EV alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A new bet at or over your alert minimum that several books agree on. Tap to open it in ${AppBook.name}."
            },
        )
    }

    /** Posts [alerts]; returns how many went out (none when notifications aren't allowed). */
    fun post(context: Context, alerts: List<EvAlert>, now: Long = System.currentTimeMillis()): Int {
        if (!ScanService.canNotify(context)) return 0
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        var posted = 0
        for (a in alerts) {
            val id = id(a)
            val tap = PendingIntent.getActivity(context, id, intent(context, a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val body = text(a) + "\n" + detail(a)
            val timeout = a.startsAtMs?.let { minOf(SHOWN_FOR_MS, (it - now).coerceAtLeast(60_000L)) } ?: SHOWN_FOR_MS
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle(title(a))
                .setContentText(text(a))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
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
