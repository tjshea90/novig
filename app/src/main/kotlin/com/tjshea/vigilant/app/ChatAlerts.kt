package com.tjshea.vigilant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.cloud.CloudChat

/** Claude's chat replies as notifications (CR, Tj 2026-10-19): one per reply, never again for the same one ([com.tjshea.vigilant.data.cloud.ChatSync] decides), and a tap opens the chat. */
object ChatAlerts {
    const val CHANNEL = "claude_chat"
    const val EXTRA_OPEN = "vigilant.open"
    const val OPEN_CHAT = "chat"
    private const val TAG = "chat"
    private const val REQUEST = 7720

    fun notify(app: Context, m: CloudChat.Msg) {
        if (!ScanService.canNotify(app)) return
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Chat with Claude", NotificationManager.IMPORTANCE_HIGH))
            val who = if (m.kind == "system") "GitHub" else "Claude" + (m.name?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            val body = m.text.ifBlank { m.files.joinToString(", ") { it.name } }
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle(who)
                .setContentText(body.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .withWallet(app)
                .setContentIntent(openChat(app))
                .build()
            NotificationManagerCompat.from(app).notify(TAG, m.id.hashCode(), n)
        }
    }

    /** The chat was opened: its notifications have done their job. */
    fun clear(app: Context) {
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.activeNotifications.filter { it.tag == TAG }.forEach { nm.cancel(TAG, it.id) }
        }
    }

    private fun openChat(app: Context): PendingIntent = PendingIntent.getActivity(
        app, REQUEST,
        Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_CHAT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
