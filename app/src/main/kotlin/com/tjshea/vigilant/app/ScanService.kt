package com.tjshea.vigilant.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tjshea.vigilant.data.scanner.ScanRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a scan alive while Tj is in another app (his request, 2026-09-25 ~18:05Z: "I will run the
 * scan then switch apps and let it scan in the background").
 *
 * Without it Android treats a backgrounded Vigilant as a cached app: it freezes the process within
 * seconds and cuts its network, so the scan stalls until he comes back. A foreground service (type
 * `dataSync`, started from the Scan tap while the app is on screen) keeps the process running, with
 * a progress notification, and a partial wake lock keeps the CPU up if the screen goes off.
 *
 * It lives exactly as long as one scan: it watches [com.tjshea.vigilant.data.scanner.ScanRunner],
 * and the moment the scan ends it releases the wake lock and stops itself. No polling, nothing on a
 * timer, nothing while idle. If Vigilant isn't on screen when the scan ends, it posts one "scan done"
 * notification with the count of +EV bets.
 */
class ScanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifiedMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as VigilantApp).container
        ensureChannels(this)
        // Always go foreground first: Android requires it within seconds of the start request,
        // even when the scan has already ended by the time this runs.
        ServiceCompat.startForeground(
            this, ONGOING_ID, progressNotification(container.runner.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        acquireWakeLock()
        if (watch == null) {
            watch = scope.launch {
                container.runner.state.collect { run ->
                    if (run.scanning) {
                        updateProgress(run)
                    } else {
                        finish(run, container.onScreen)
                    }
                }
            }
        }
        // A killed process takes the scan with it; there's nothing to resume, so don't restart.
        return START_NOT_STICKY
    }

    /** Android 15+: a `dataSync` service past its daily allowance must stop. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopNow()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun updateProgress(run: ScanRun) {
        // Notification updates are rate-limited by the system; twice a second is plenty.
        val now = System.currentTimeMillis()
        if (now - lastNotifiedMs < 500) return
        lastNotifiedMs = now
        notify(ONGOING_ID, progressNotification(run))
    }

    private fun finish(run: ScanRun, onScreen: Boolean) {
        if (!onScreen && run.finished > 0) notify(DONE_ID, doneNotification(run))
        stopNow()
    }

    private fun stopNow() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vigilant:scan").apply {
            setReferenceCounted(false)
            // A hard ceiling: even a scan stuck on a dead network can't hold the CPU past this.
            acquire(WAKE_LOCK_MAX_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notify(id: Int, notification: Notification) {
        if (!canNotify(this)) return
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(run: ScanRun): Notification {
        val p = run.progress
        val found = run.result?.let { r -> run.settings?.let { r.feed(it).size } } ?: 0
        return NotificationCompat.Builder(this, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Scanning Novig")
            .setContentText(ScanText.progress(p, found))
            .setProgress(p?.total ?: 0, p?.done ?: 0, p == null || p.total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun doneNotification(run: ScanRun): Notification {
        val settings = run.settings
        val feed = if (settings != null) run.result?.feed(settings).orEmpty() else emptyList()
        val (title, text) = ScanText.done(feed, settings?.minEvPercent ?: 0.0, run.report?.errors.orEmpty())
        return NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    companion object {
        private const val CHANNEL_SCAN = "scan"
        private const val CHANNEL_RESULTS = "scan_results"
        private const val ONGOING_ID = 1
        private const val DONE_ID = 2
        private const val WAKE_LOCK_MAX_MS = 10 * 60_000L

        /** Called from the Scan tap, while Vigilant is on screen (Android only lets it start then). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java)) }
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SCAN, "Scan in progress", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a scan runs, so it keeps going when you switch apps."
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULTS, "Scan results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "One note when a scan finishes while you're in another app."
                },
            )
        }
    }
}
