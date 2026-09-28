package com.tjshea.vigilant.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the background auto-scan going with Vigilant closed (Tj, 2026-09-28: "run the app in the
 * background and it will continue scanning … even if the app is not open on the screen").
 *
 * A foreground service, alive exactly as long as auto-scan is on, with one quiet notification that
 * says so ("Auto-scan every 10 min · next at 3:42 PM") and offers Scan now and Stop. It holds no
 * wake lock between scans: each one is woken by an exact alarm ([AutoScanReceiver]; Android keeps
 * those on time even in Doze), holds a partial wake lock only while [AutoScanner.cycle] runs (capped
 * at [WAKE_LOCK_MAX_MS]), and arms the next alarm [ScanSettings.autoScanMinutes] after it started.
 * Type `specialUse`: a scan schedule the user sets has no fitting standard type, and `dataSync`
 * would be stopped after six hours a day on Android 15+.
 */
class AutoScanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var cycleJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifiedMs = 0L
    private var settings: ScanSettings? = null

    private val container get() = (application as VigilantApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, ongoing(container.autoScan.status.value), foregroundType())
        } catch (e: RuntimeException) {
            // Android refused (only allowed from a visible app, an exact alarm, boot, or an update):
            // Tj gets a note to reopen Vigilant, which starts it again.
            AutoScanReceiver.releaseBridge()
            notifyPaused(this)
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationManagerCompat.from(this).cancel(PAUSED_ID)
        if (watch == null) watch = scope.launch { follow() }
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                runCatching { container.settingsStore.update { it.copy(autoScan = AutoScanMode.OFF) } }
                stopNow()
            }
            ACTION_SCAN_NOW, ACTION_CYCLE -> runCycle()
            // Started (auto-scan just switched on, Vigilant opened, the phone booted): scan now
            // unless one ran recently, else keep the next alarm.
            else -> scope.launch {
                val s = container.currentSettings()
                val next = AutoScanClock.nextAtMs(container.autoScan.status.value.lastStartMs, s.autoScanMinutes, System.currentTimeMillis())
                if (next <= System.currentTimeMillis()) runCycle() else AutoScanAlarm.set(this@AutoScanService, next)
            }
        }
        return START_STICKY
    }

    /** Settings changes (off, paused, a new interval) and the cycle's progress, into the notification and the alarm. */
    private suspend fun follow() {
        combine(
            container.settingsStore.flow.filterNotNull().map { it.activeAutoScan to it.autoScanMinutes }.distinctUntilChanged(),
            container.autoScan.status,
            container.runner.state.map { it.progress }.distinctUntilChanged(),
        ) { (mode, minutes), status, _ -> Triple(mode, minutes, status) }
            .collect { (mode, minutes, status) ->
                if (mode == AutoScanMode.OFF) {
                    stopNow()
                    return@collect
                }
                val s = container.settingsStore.flow.value
                val changed = settings?.let { it.autoScanMinutes != minutes || it.activeAutoScan != mode } ?: false
                settings = s
                if (changed && !status.running) {
                    AutoScanAlarm.set(this, AutoScanClock.nextAtMs(status.lastStartMs, minutes, System.currentTimeMillis()))
                }
                updateOngoing(status)
            }
    }

    private fun runCycle() {
        if (cycleJob?.isActive == true || container.autoScan.running) {
            AutoScanReceiver.releaseBridge()
            return
        }
        acquireWakeLock()
        AutoScanReceiver.releaseBridge()
        cycleJob = scope.launch {
            val s = container.currentSettings()
            // The next one is armed first: a cycle cut short (killed, stuck) can't stop the schedule.
            AutoScanAlarm.set(this@AutoScanService, System.currentTimeMillis() + s.autoScanMinutes.coerceAtLeast(1) * 60_000L)
            try {
                // Off the main thread: book parsing and pricing.
                kotlinx.coroutines.withContext(Dispatchers.Default) { container.autoScan.cycle() }
            } finally {
                releaseWakeLock()
                updateOngoing(container.autoScan.status.value, force = true)
            }
        }
    }

    private fun stopNow() {
        AutoScanAlarm.cancel(this)
        cycleJob?.cancel()
        releaseWakeLock()
        AutoScanReceiver.releaseBridge()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vigilant:autoscan").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_MAX_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun updateOngoing(status: AutoScanner.Status, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifiedMs < 1_000) return
        lastNotifiedMs = now
        if (!ScanService.canNotify(this)) return
        runCatching { NotificationManagerCompat.from(this).notify(ONGOING_ID, ongoing(status)) }
    }

    private fun ongoing(status: AutoScanner.Status): Notification {
        val s = settings ?: container.settingsStore.flow.value ?: ScanSettings()
        val progress = container.runner.state.value.progress.takeIf { status.running && status.step == "Vigilant scan" }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(AutoScanText.title(s))
            .setContentText(AutoScanText.status(status, s, AutoScanAlarm.nextAtMs, System.currentTimeMillis(), progress))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(this))
            .addAction(0, "Scan now", service(this, ACTION_SCAN_NOW))
            .addAction(0, "Stop", service(this, ACTION_STOP))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "auto_scan"
        private const val ONGOING_ID = 3
        private const val PAUSED_ID = 4

        const val ACTION_CYCLE = "com.tjshea.vigilant.AUTO_SCAN_CYCLE"
        const val ACTION_SCAN_NOW = "com.tjshea.vigilant.AUTO_SCAN_NOW"
        const val ACTION_STOP = "com.tjshea.vigilant.AUTO_SCAN_STOP"

        /**
         * The CPU is held at most this long for one background scan (1,200 books on a slowed-down Novig). A scan with no
         * limit fits too: it stops reading Novig once its other books' odds would be too old (about 8 minutes in).
         */
        const val WAKE_LOCK_MAX_MS = 20 * 60_000L

        /** True while the service exists in this process. */
        @Volatile
        var running = false
            private set

        /**
         * From Vigilant on screen (always allowed), a boot, an update or an exact alarm. Already
         * running here: a plain start (an app with a foreground service may start its services).
         */
        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, AutoScanService::class.java).apply { this.action = action }
            runCatching { if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent) }.onFailure {
                AutoScanReceiver.releaseBridge()
                notifyPaused(context)
            }
        }

        fun stop(context: Context) {
            AutoScanAlarm.cancel(context)
            if (running) runCatching { context.stopService(Intent(context, AutoScanService::class.java)) }
        }

        private fun foregroundType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        private fun service(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, AutoScanService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Auto-scan", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while auto-scan is on, so it keeps scanning with ${AppBook.name} closed."
                    setShowBadge(false)
                },
            )
            EvAlerts.ensureChannel(context)
        }

        /** Android wouldn't let auto-scan start from the background: one note to reopen Vigilant. */
        fun notifyPaused(context: Context) {
            if (!ScanService.canNotify(context)) return
            ensureChannel(context)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("Auto-scan paused")
                .setContentText("Android stopped it in the background. Open Vigilant to start it again.")
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(PAUSED_ID, n) }
        }
    }
}

/** The ongoing notification's words (pure, for tests). */
object AutoScanText {
    fun title(s: ScanSettings): String = "Auto-scan: ${s.autoScan.displayName} every ${s.autoScanMinutes} min"

    fun status(
        status: AutoScanner.Status,
        s: ScanSettings,
        nextAtMs: Long?,
        now: Long,
        progress: com.tjshea.vigilant.data.scanner.ScanProgress? = null,
        zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    ): String {
        if (status.running) {
            val p = progress?.takeIf { it.total > 0 }?.let { " ${it.done}/${it.total}" }.orEmpty()
            return "${status.step ?: "Scanning"}$p…"
        }
        val clock = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = zone }
        val next = nextAtMs?.takeIf { it > now }?.let { "Next at ${clock.format(Date(it))}" } ?: "Next scan soon"
        val alerts = if (s.alertMinEv <= 0.0) "alerts off" else "alerts at ${Math.round(s.alertMinEv * 100)}%+"
        val last = when {
            status.lastEndMs == null -> null
            status.lastError != null && status.lastFound == 0 -> "last: ${status.lastError}"
            status.lastFound == 0 -> "last found nothing to alert"
            else -> "last found ${status.lastFound} (${status.lastAlerts} new)"
        }
        return listOfNotNull(next, last, alerts).joinToString(" · ")
    }
}

/**
 * The alarm that wakes each background scan: exact and allowed in Doze when Android lets Vigilant set
 * exact alarms (`USE_EXACT_ALARM`), else the closest inexact equivalent.
 */
object AutoScanAlarm {
    /** When the armed alarm goes off, for the notification (this process's view). */
    @Volatile
    var nextAtMs: Long? = null
        private set

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, AutoScanReceiver::class.java).setAction(AutoScanService.ACTION_CYCLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun set(context: Context, atMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
            nextAtMs = atMs
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
        nextAtMs = null
    }
}

/**
 * The auto-scan alarm, and restarts after a reboot or an update (Tj installs new versions from GitHub).
 * The alarm's wake-up lasts only while this runs, so it holds a short wake lock of its own until the
 * service takes over with the scan's.
 */
class AutoScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutoScanService.ACTION_CYCLE -> {
                holdBridge(context)
                AutoScanService.start(context, AutoScanService.ACTION_CYCLE)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                val app = context.applicationContext as? VigilantApp ?: return pending.finish()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (app.container.currentSettings().activeAutoScan != AutoScanMode.OFF) AutoScanService.start(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private var bridge: PowerManager.WakeLock? = null

        @Synchronized
        private fun holdBridge(context: Context) {
            if (bridge?.isHeld == true) return
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            bridge = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vigilant:autoscan-alarm").apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }
        }

        @Synchronized
        fun releaseBridge() {
            bridge?.let { if (it.isHeld) it.release() }
            bridge = null
        }
    }
}
