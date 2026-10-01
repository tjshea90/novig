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
import com.tjshea.vigilant.data.scanner.KeepAwake
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
 * says so ("Auto-scan every 10 min · next at 3:42 PM") and offers Scan now and Stop. Type `specialUse`: a scan schedule the user sets
 * has no fitting standard type, and `dataSync` would be stopped after six hours a day on Android 15+.
 *
 * Two ways to keep time (RESEARCH.md §59; Tj, 2026-10-02: "keep it alive robustly … even if the phone is idle and the screen is turned off and locked"):
 *
 *  - **Keep awake** ([KeepAwake.active]: the switch is on and cycles are under 9 minutes apart, the default): the service holds a partial wake lock
 *    the whole time (the screen stays off), so the CPU runs and, the service being a foreground one, so does its network even in Doze; a loop in
 *    the service starts each cycle ([loop]). The alarm is only a safety net a few intervals off ([armWatchdog]), moved on by every cycle: it goes
 *    off only if the loop stalls, and starts the cycles and the loop again.
 *  - **Alarm only** (the switch off, or cycles 9 minutes or more apart): no wake lock between scans. Each one is woken by an exact alarm
 *    ([AutoScanReceiver]), holds a partial wake lock only while [AutoScanner.cycle] runs (capped at [WAKE_LOCK_MAX_MS]), and arms the next alarm
 *    [ScanSettings.autoScanSeconds] after it started (and again when it ends, if it outlasted that). In Doze Android lets such an alarm go off
 *    about once every 9 minutes whatever the interval, which is why faster intervals use the first way.
 */
class AutoScanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var cycleJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifiedMs = 0L
    private var settings: ScanSettings? = null

    /** Keep awake: the loop that starts each cycle, the lock that keeps the CPU on between them, when that lock ends, and what the safety alarm is armed for. */
    private var loopJob: Job? = null
    private var keepAwakeLock: PowerManager.WakeLock? = null
    private var keepAwakeUntilMs: Long? = null
    private var watchdogAtMs: Long? = null

    /** When the loop starts the next cycle, for the notification. */
    @Volatile
    private var loopNextAtMs: Long? = null

    /** What [follow] last saw of the settings that decide how the schedule is kept. */
    private var plan: Plan? = null

    private data class Plan(val mode: AutoScanMode, val seconds: Int, val hold: Boolean)

    /** The service is going away (Stop, auto-scan off): a cycle ending now must not arm another alarm. */
    private var stopping = false

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
            ACTION_SCAN_NOW -> runCycle(forceVigilant = true)
            ACTION_CYCLE -> scope.launch {
                val s = container.currentSettings()
                val last = container.autoScan.status.value.lastStartMs
                val now = System.currentTimeMillis()
                // The safety alarm of a loop that is running fine (a cycle moves it on, so this is rare, and after Vigilant is swiped out of
                // the recent apps): nothing to do but arm it again. Any other alarm, or a stalled loop, runs a cycle (and the loop starts again).
                if (KeepAwake.active(s) && loopJob?.isActive == true && last != null && now - last < KeepAwake.watchdogDelayMs(s.autoScanSeconds)) {
                    AutoScanReceiver.releaseBridge()
                    armWatchdog(s.autoScanSeconds, force = true)
                } else {
                    runCycle()
                }
            }
            // Started (auto-scan just switched on, Vigilant opened, the phone booted): scan now
            // unless one ran recently, else keep the next alarm. Keeping awake, the loop does this itself.
            else -> scope.launch {
                val s = container.currentSettings()
                if (KeepAwake.active(s)) return@launch
                val next = AutoScanClock.nextAtMs(container.autoScan.status.value.lastStartMs, s.autoScanSeconds, System.currentTimeMillis())
                if (next <= System.currentTimeMillis()) runCycle() else AutoScanAlarm.set(this@AutoScanService, next)
            }
        }
        return START_STICKY
    }

    /** Settings changes (off, paused, a new interval, keep awake) and the cycle's progress, into the notification, the alarm and the loop. */
    private suspend fun follow() {
        combine(
            container.settingsStore.flow.filterNotNull().map { Plan(it.activeAutoScan, it.autoScanSeconds, KeepAwake.active(it)) }.distinctUntilChanged(),
            container.autoScan.status,
            container.runner.state.map { it.progress }.distinctUntilChanged(),
        ) { next, status, _ -> next to status }
            .collect { (next, status) ->
                if (next.mode == AutoScanMode.OFF) {
                    stopNow()
                    return@collect
                }
                val before = plan
                plan = next
                settings = container.settingsStore.flow.value
                // Keeping awake starts (or restarts at another interval) the loop; not keeping awake ends it and lets the CPU sleep between scans.
                if (before?.hold != next.hold || before.seconds != next.seconds) {
                    if (next.hold) {
                        restartLoop()
                    } else {
                        stopLoop()
                        releaseKeepAwake()
                    }
                }
                if (before != null && before != next && !status.running) {
                    armAlarm(next.hold, next.seconds, AutoScanClock.nextAtMs(status.lastStartMs, next.seconds, System.currentTimeMillis()))
                }
                updateOngoing(status)
            }
    }

    /**
     * Keep awake: starts each cycle when its time comes. The CPU is held awake (renewed before its timeout), the safety alarm is moved on, a cycle that
     * another trigger (Scan now, the alarm) started is waited for, and a failure here is written to Recent problems and tried again, never a crash.
     */
    private suspend fun loop() {
        while (true) {
            try {
                if (!step()) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { container.problems.add("Keep-awake loop", "${e.javaClass.simpleName}: ${e.message}") }
                delay(LOOP_RETRY_MS)
            }
        }
    }

    /** One wait and one cycle of the [loop]; false when keeping awake is no longer wanted. */
    private suspend fun step(): Boolean {
        val s = container.currentSettings()
        if (!KeepAwake.active(s)) return false
        maintainKeepAwake(s.autoScanSeconds)
        if (container.autoScan.running) {
            // Not a spin: a cycle that has the lock but hasn't yet said it is running (it reads the settings first).
            delay(RUNNING_POLL_MS)
            container.autoScan.status.first { !it.running }
            return true
        }
        val now = System.currentTimeMillis()
        // Worked out once per wait: from a cycle just started it is always in the future ([AutoScanClock.minGapMs]).
        val due = AutoScanClock.nextAtMs(container.autoScan.status.value.lastStartMs, s.autoScanSeconds, now)
        loopNextAtMs = due
        while (true) {
            val left = due - System.currentTimeMillis()
            if (left <= 0) break
            delay(minOf(left, LOOP_SLICE_MS))
            maintainKeepAwake(s.autoScanSeconds)
        }
        val before = container.autoScan.status.value.lastStartMs
        runCycle()
        cycleJob?.join()
        // No cycle started (Check odds now holds the focus, or another trigger got there first): not again this instant.
        if (container.autoScan.status.value.lastStartMs == before) delay(AutoScanClock.minGapMs(s.autoScanSeconds))
        return true
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch { loop() }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        loopNextAtMs = null
    }

    /** The CPU held awake and the safety alarm kept ahead of the loop. */
    private fun maintainKeepAwake(seconds: Int) {
        holdKeepAwake()
        armWatchdog(seconds)
    }

    /**
     * The alarm behind the schedule: keeping awake, a safety net a few intervals away that the loop keeps moving ([armWatchdog]); else the exact time of
     * the next cycle.
     */
    private fun armAlarm(hold: Boolean, seconds: Int, nextCycleAtMs: Long) {
        if (hold) armWatchdog(seconds, force = true) else AutoScanAlarm.set(this, nextCycleAtMs)
    }

    /** Not announced as the next scan in the notification ([AutoScanAlarm.set]): it is not one. */
    private fun armWatchdog(seconds: Int, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && !KeepAwake.rearmDue(watchdogAtMs, now, seconds)) return
        val at = KeepAwake.watchdogAtMs(now, seconds)
        AutoScanAlarm.set(this, at, announce = false)
        watchdogAtMs = at
    }

    private fun runCycle(forceVigilant: Boolean = false) {
        if (cycleJob?.isActive == true || container.autoScan.running) {
            AutoScanReceiver.releaseBridge()
            return
        }
        acquireWakeLock()
        AutoScanReceiver.releaseBridge()
        cycleJob = scope.launch {
            val s = container.currentSettings()
            // The next one is armed first: a cycle cut short (killed, stuck) can't stop the schedule.
            armAlarm(KeepAwake.active(s), s.autoScanSeconds, System.currentTimeMillis() + s.autoScanSeconds.coerceAtLeast(1) * 1_000L)
            try {
                // Off the main thread: book parsing and pricing.
                kotlinx.coroutines.withContext(Dispatchers.Default) { container.autoScan.cycle(forceVigilant) }
            } finally {
                releaseWakeLock()
                // A cycle longer than its interval (a 15 s one with a slow CNO page, any one with Vigilant's scan) had its next alarm go off while
                // it ran, and that one was dropped ([runCycle]'s guard): the next is armed from here, so the schedule never lapses.
                // The interval as it is now: Tj may have picked another while the cycle ran.
                if (!stopping) {
                    val now = container.settingsStore.flow.value ?: s
                    armAlarm(KeepAwake.active(now), now.autoScanSeconds, AutoScanClock.nextAtMs(container.autoScan.status.value.lastStartMs, now.autoScanSeconds, System.currentTimeMillis()))
                }
                updateOngoing(container.autoScan.status.value, force = true)
            }
        }
    }

    private fun stopNow() {
        stopping = true
        AutoScanAlarm.cancel(this)
        stopLoop()
        cycleJob?.cancel()
        releaseWakeLock()
        releaseKeepAwake()
        AutoScanReceiver.releaseBridge()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        running = false
        releaseWakeLock()
        releaseKeepAwake()
        scope.cancel()
        // A deliberate stop (Stop, auto-scan off): the hours before the next start are not a late cycle ([CycleLog]). A killed process never gets here.
        container.appScope.launch(Dispatchers.IO) { runCatching { container.cycleLog.stopped() } }
        super.onDestroy()
    }

    /**
     * Vigilant swiped out of the recent apps. The service goes on, but some phones then end the process: an alarm a moment from now starts it again
     * if so (and is a no-op if not: [ACTION_CYCLE]).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!stopping && plan?.let { it.mode != AutoScanMode.OFF } == true) AutoScanAlarm.set(this, System.currentTimeMillis() + TASK_REMOVED_RESTART_MS, announce = false)
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

    /** Keep awake: the CPU stays on with the screen off. Held with a timeout and renewed ([KeepAwake.lockRenewDue]), so a dead service can't hold it for ever. */
    private fun holdKeepAwake() {
        val now = System.currentTimeMillis()
        val lock = keepAwakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vigilant:keepawake").apply { setReferenceCounted(false) }.also { keepAwakeLock = it }
        if (lock.isHeld && !KeepAwake.lockRenewDue(keepAwakeUntilMs, now)) return
        lock.acquire(KeepAwake.LOCK_TIMEOUT_MS)
        keepAwakeUntilMs = now + KeepAwake.LOCK_TIMEOUT_MS
        keepAwakeHeld = true
    }

    /** For tests: the keep-awake lock itself is held (not just the flag Diagnostics reads). */
    internal val keepAwakeIsHeld: Boolean get() = keepAwakeLock?.isHeld == true

    private fun releaseKeepAwake() {
        keepAwakeLock?.let { if (it.isHeld) it.release() }
        keepAwakeLock = null
        keepAwakeUntilMs = null
        keepAwakeHeld = false
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
            .setContentText(AutoScanText.status(status, s, if (loopJob?.isActive == true) loopNextAtMs else AutoScanAlarm.nextAtMs, System.currentTimeMillis(), progress))
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

        /** The loop's longest sleep: it wakes this often to renew the CPU lock and move the safety alarm on. */
        private const val LOOP_SLICE_MS = 30_000L

        /** A cycle running when the loop looks: it waits this, then for the cycle to end. */
        private const val RUNNING_POLL_MS = 250L

        /** After a failure in the loop. */
        private const val LOOP_RETRY_MS = 10_000L

        /** An alarm this long after Vigilant is swiped away ([onTaskRemoved]). */
        private const val TASK_REMOVED_RESTART_MS = 3_000L

        /** True while the service exists in this process. */
        @Volatile
        var running = false
            private set

        /** True while the service holds the CPU awake between scans (Diagnostics). */
        @Volatile
        var keepAwakeHeld = false
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
    /** What runs, not what was picked: the scanner choice can put half of it to sleep ([ScanSettings.autoScansCno], [ScanSettings.autoScansVigilant]). */
    fun title(s: ScanSettings): String {
        val what = when {
            s.autoScansCno && s.autoScansVigilant -> "CNO + Vigilant"
            s.autoScansVigilant -> "Vigilant"
            s.autoScansCno -> "CNO"
            else -> s.autoScan.displayName
        }
        return "Auto-scan: $what every ${ScanSettings.intervalLabel(s.autoScanSeconds)}"
    }

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
        if (status.pausedForCheck) return "Paused while Check odds now runs (auto-bet too)"
        // Seconds only when scans are under a minute apart: "Next at 3:42:15 PM".
        val clock = SimpleDateFormat(if (s.autoScanSeconds < 60) "h:mm:ss a" else "h:mm a", Locale.US).apply { timeZone = zone }
        val next = nextAtMs?.takeIf { it > now }?.let { "Next at ${clock.format(Date(it))}" } ?: "Next scan soon"
        val alerts = if (s.alertMinEv <= 0.0) "alerts off" else "alerts at ${Math.round(s.alertMinEv * 100)}%+"
        val last = when {
            status.lastEndMs == null -> null
            status.lastError != null && status.lastFound == 0 -> "last: ${status.lastError}"
            status.lastFound == 0 -> "last found nothing to alert"
            else -> "last found ${status.lastFound} (${status.lastAlerts} new)"
        }
        return listOfNotNull(next, last, alerts, "stays awake".takeIf { KeepAwake.active(s) }).joinToString(" · ")
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

    /** [announce]: it is the next scan (the notification says so); false for the keep-awake safety alarm and the restart after a swipe, which are not. */
    fun set(context: Context, atMs: Long, announce: Boolean = true) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
            nextAtMs = if (announce) atMs else null
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
                        // Alarms don't survive a reboot: the closing capture's is armed again (and after an update, for good measure).
                        runCatching { ClosingAlarm.schedule(app, app.container.tracker.all()) }
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
