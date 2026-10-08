package com.tjshea.vigilant.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the Pinnodds live engine running with the screen off and Vigilant in the background (Tj, 2026-10-08: "the betting must be fast to react to the odds movement"). The engine
 * itself ([com.tjshea.vigilant.data.pinnodds.PinnLiveRunner]) lives in the app's scope; Android freezes a backgrounded process and its sockets, so this foreground service holds the
 * process and the CPU awake exactly as long as Settings › Pinnodds live is on (a partial wake lock, renewed every few seconds, so a stalled service cannot hold the CPU for long).
 * One quiet notification says what it is doing and offers Stop (the feed off) and STOP ALL.
 */
class LiveFeedService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val container get() = (application as VigilantApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        runCatching { container.eventLog.info("SERVICE", "live feed service started") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, ongoing(), foregroundType())
        } catch (e: RuntimeException) {
            // Android refuses a foreground start from the background (only from a visible app, an alarm, boot or an update): the engine still runs while the app lives.
            runCatching { container.eventLog.warn("SERVICE", "Android refused to start the live feed in the foreground (${e.javaClass.simpleName}): reopen Vigilant") }
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                runCatching { container.settingsStore.update { it.copy(pinnLive = false) } }
                stopNow()
            }
            ACTION_KILL -> container.appScope.launch { KillSwitch.engage(application, container, "the live feed notification's Stop all") }
        }
        if (watch == null) watch = scope.launch { follow() }
        return START_NOT_STICKY
    }

    /** Every few seconds: renew the CPU lock, refresh the notification, and stop when the switch is off or STOP ALL is on. */
    private suspend fun follow() {
        while (scope.isActive) {
            val s = runCatching { container.currentSettings() }.getOrNull()
            if (s == null || !s.pinnLive || s.killed) {
                stopNow()
                return
            }
            renewWakeLock()
            if (ScanService.canNotify(this)) runCatching { NotificationManagerCompat.from(this).notify(ONGOING_ID, ongoing(s)) }
            delay(NOTIFY_MS)
        }
    }

    private fun renewWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vigilant:livefeed").also { it.setReferenceCounted(false); wakeLock = it }
        runCatching { lock.acquire(WAKE_LOCK_MS) }
        keepAwakeHeld = true
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        keepAwakeHeld = false
    }

    private fun stopNow() {
        runCatching { container.eventLog.info("SERVICE", "live feed service stopping (the feed is off, STOP ALL, or Stop)") }
        watch?.cancel()
        watch = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        watch?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun ongoing(settings: ScanSettings? = null): Notification {
        val s = settings ?: container.settingsStore.flow.value ?: ScanSettings()
        val keys = container.keyStore.current(com.tjshea.vigilant.data.keys.ApiProvider.PINNODDS).size
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle("Pinnodds live · " + if (s.pinnLiveBet && s.pinnLiveHalted == null) "real bets" else if (s.pinnLiveHalted != null) "stopped" else "paper")
            .setContentText(PinnText.statusLine(container.pinnRunner.status.value, container.pinnTrader.status.value, s, keys))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(this))
            .addAction(0, "Stop", service(this, ACTION_STOP))
            .addAction(0, "STOP ALL", service(this, ACTION_KILL))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.tjshea.vigilant.LIVE_FEED_STOP"
        const val ACTION_KILL = "com.tjshea.vigilant.LIVE_FEED_KILL"
        private const val CHANNEL = "live_feed"
        private const val ONGOING_ID = 7410
        private const val NOTIFY_MS = 5_000L

        /** The CPU lock is taken for this long and renewed every [NOTIFY_MS]: a stalled service lets go within a minute. */
        private const val WAKE_LOCK_MS = 60_000L

        @Volatile
        var running = false
            private set

        @Volatile
        var keepAwakeHeld = false
            private set

        /** From Vigilant on screen (always allowed). Already running here: a plain start. */
        fun start(context: Context) {
            val intent = Intent(context, LiveFeedService::class.java)
            runCatching { if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            if (running) runCatching { context.stopService(Intent(context, LiveFeedService::class.java)) }
        }

        private fun foregroundType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        private fun service(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, LiveFeedService::class.java).setAction(action),
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
                NotificationChannel(CHANNEL, "Pinnodds live", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the Pinnodds live feed is on, so it keeps watching Pinnacle's prices with ${AppBook.name} in the background."
                    setShowBadge(false)
                },
            )
        }
    }
}
