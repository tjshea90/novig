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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    @Volatile private var lastNotifiedMs = 0L

    /** The progress notification's "N found", worked out again only when the scan has a new result ([FoundCount]). */
    private val found = FoundCount { r, s -> shown(r.feed(s), s).size }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as VigilantApp).container
        ensureChannels(this)
        // Always go foreground first: Android requires it within seconds of the start request,
        // even when the scan has already ended by the time this runs. If Android refuses (it only
        // allows it from a visible app), stop cleanly: the scan still runs while Vigilant is open.
        try {
            ServiceCompat.startForeground(
                // No count yet: counting is the work kept off the main thread (below).
                this, ONGOING_ID, progressNotification(container.runner.state.value, found = 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        if (watch == null) {
            watch = scope.launch {
                // Progress while the scan runs; the first state that isn't scanning ends the watch. Built OFF the main thread (Tj, 2026-10-02:
                // "When I scan with vigilant scanner, the entire app becomes laggy still"): twice a second it screened every priced side
                // (8,660 in his scan) and rebuilt the placed-bets index from all his tracked bets, on the main thread, whatever tab was open.
                val ended = container.runner.state
                    .onEach { if (it.scanning) updateProgress(it) }
                    .flowOn(Dispatchers.Default)
                    .first { !it.scanning }
                watch = null
                val onScreen = container.onScreen
                // The wallet's balance as of now on the result's notification (Tj, 2026-10-02 21:51Z): one read unless the last is under 30 s old.
                runCatching { container.wallet.fresh() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                val done = withContext(Dispatchers.Default) { doneNotificationFor(ended, onScreen) }
                finish(ended, done)
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
        notify(ONGOING_ID, progressNotification(run, found.of(run)))
    }

    /** The "scan done" note to post when the scan ended with Vigilant off screen, else null (worked out off the main thread). */
    private fun doneNotificationFor(run: ScanRun, onScreen: Boolean): Notification? {
        val container = (application as VigilantApp).container
        // Not when Tj switched to CNO only mid-scan (the widget and app no longer show these bets), or paused it
        // (the scan was stopped, not done).
        val vigilantShown = container.settingsStore.flow.value?.let { it.vigilantOn && !it.paused } ?: true
        return if (!onScreen && run.finished > 0 && vigilantShown) doneNotification(run) else null
    }

    private fun finish(run: ScanRun, done: Notification?) {
        val container = (application as VigilantApp).container
        if (done != null) {
            notify(DONE_ID, done)
            // His scan, left running in the background: its new +EV bets alert like a background scan's (Tj, 2026-09-28).
            val settings = run.settings
            if (settings != null && settings.alertMinEv > 0.0) {
                container.appScope.launch { runCatching { container.autoScan.afterScan(run.result, container.currentSettings()) } }
            }
        }
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
        if (id == DONE_ID && !NotifyGate.allow()) return
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(run: ScanRun, found: Int): Notification {
        val p = run.progress
        return NotificationCompat.Builder(this, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .withWallet(this)
            .setContentTitle("Scanning ${AppBook.name}")
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

    /** The bets Tj already has, as the app's lists hide them. */
    private fun placedIndex(): com.tjshea.vigilant.data.tracker.PlacedIndex {
        val c = (application as VigilantApp).container
        return com.tjshea.vigilant.data.tracker.PlacedIndex.of(c.placed.flow.value?.bets.orEmpty(), c.tracker.flow.value.orEmpty(), System.currentTimeMillis())
    }

    /** [feed] as the app's lists show it: without bets Tj already has, and only games in his start-time window. */
    private fun shown(
        feed: List<com.tjshea.vigilant.data.scanner.Opportunity>,
        settings: com.tjshea.vigilant.data.scanner.ScanSettings,
    ): List<com.tjshea.vigilant.data.scanner.Opportunity> {
        val now = System.currentTimeMillis()
        return placedIndex().visible(feed).filter { settings.startsInWindow(it.event.startsTs, now) }
    }

    private fun doneNotification(run: ScanRun): Notification {
        val settings = run.settings
        // Bets Tj already placed (from either scanner) aren't news (Tj, 2026-09-27).
        val feed = if (settings != null) shown(run.result?.feed(settings).orEmpty(), settings) else emptyList()
        val (title, text) = ScanText.done(feed, settings?.minEvPercent ?: 0.0, run.report?.errors.orEmpty())
        return NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .withWallet(this)
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
        /** Up to 1,200 Novig prices a scan since v0.18.0: about four minutes, longer when Novig slows it down. */
        private const val WAKE_LOCK_MAX_MS = 20 * 60_000L

        /** Called from the Scan tap, while Vigilant is on screen (Android only lets it start then). */
        fun start(context: Context) {
            // The last scan's "done" note is out of date the moment a new scan starts.
            cancelDone(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java)) }
        }

        /**
         * Takes down the "scan done" notification: its bets are no longer what Vigilant shows (a new
         * process, CNO only picked, or a new scan).
         */
        fun cancelDone(context: Context) {
            runCatching { NotificationManagerCompat.from(context).cancel(DONE_ID) }
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun ensureChannels(context: Context) {
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

/**
 * The scan notification's "N found" (Tj's own bets and games outside his start-time window left out, as the lists do): [count] screens the whole
 * result, so it runs once per new result (a partial comes every ~2 s on a big scan), not on every progress tick (Tj, 2026-10-02: the app lagged
 * while a scan ran).
 */
internal class FoundCount(private val count: (com.tjshea.vigilant.data.scanner.ScanResult, com.tjshea.vigilant.data.scanner.ScanSettings) -> Int) {
    private var result: com.tjshea.vigilant.data.scanner.ScanResult? = null
    private var settings: com.tjshea.vigilant.data.scanner.ScanSettings? = null
    private var last = 0

    @Synchronized
    fun of(run: ScanRun): Int {
        val r = run.result ?: return 0
        val s = run.settings ?: return 0
        if (r !== result || s != settings) {
            last = count(r, s)
            result = r
            settings = s
        }
        return last
    }
}
