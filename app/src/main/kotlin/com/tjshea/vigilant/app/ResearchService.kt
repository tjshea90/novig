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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the research recorders (the paper lab, paper bids, live feed test, burst recorder) running with Vigilant in the background (Tj, 2026-10-10: "Is there any way to keep vigilant running even when
 * backgrounded ... Build it"). The recorders live in the app's scope; Android ends a backgrounded process after a few minutes, so this foreground service holds the process (and the CPU, with a partial
 * wake lock renewed every few seconds) exactly as long as research is on ([VigilantApp.researchOn]). One quiet notification offers Stop (research off) and STOP ALL. It places nothing: research is paper only.
 */
class ResearchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val container get() = (application as VigilantApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        runCatching { container.eventLog.info("SERVICE", "research service started") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, ongoing(), foregroundType())
        } catch (e: RuntimeException) {
            runCatching { container.eventLog.warn("SERVICE", "Android refused to start the research service in the foreground (${e.javaClass.simpleName}): reopen Vigilant") }
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                runCatching { container.settingsStore.update { it.copy(researchMode = false, altLab = false) } }
                stopNow()
            }
            ACTION_KILL -> container.appScope.launch { KillSwitch.engage(application, container, "the research notification's Stop all") }
        }
        if (watch == null) watch = scope.launch { follow() }
        return START_NOT_STICKY
    }

    private suspend fun follow() {
        while (scope.isActive) {
            val s = runCatching { container.currentSettings() }.getOrNull()
            if (s == null || !((s.researchMode || s.altLab) && !s.killed)) {
                stopNow()
                return
            }
            renewWakeLock()
            if (ScanService.canNotify(this)) runCatching { NotificationManagerCompat.from(this).notify(ONGOING_ID, ongoing()) }
            delay(NOTIFY_MS)
        }
    }

    private fun renewWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vigilant:research").also { it.setReferenceCounted(false); wakeLock = it }
        runCatching { lock.acquire(WAKE_LOCK_MS) }
        keepAwakeHeld = true
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        keepAwakeHeld = false
    }

    private fun stopNow() {
        runCatching { container.eventLog.info("SERVICE", "research service stopping (research is off, STOP ALL, or Stop)") }
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

    private fun ongoing(): Notification {
        val st = runCatching { container.lab.status.value }.getOrNull()
        val (posted, filled) = runCatching { container.bidLab.counts().let { it.third to it.second } }.getOrDefault(0L to 0)
        val text = "Paper lab: ${st?.games ?: 0} live game${if ((st?.games ?: 0) == 1) "" else "s"}, ${st?.cycles ?: 0} passes · paper bids: $posted up, $filled filled. Nothing is bet."
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle("Research running")
            .setContentText(text)
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
        const val ACTION_STOP = "com.tjshea.vigilant.RESEARCH_STOP"
        const val ACTION_KILL = "com.tjshea.vigilant.RESEARCH_KILL"
        private const val CHANNEL = "research"
        private const val ONGOING_ID = 7411
        private const val NOTIFY_MS = 5_000L
        private const val WAKE_LOCK_MS = 60_000L

        @Volatile
        var running = false
            private set

        @Volatile
        var keepAwakeHeld = false
            private set

        /** From Vigilant on screen (always allowed); refused quietly from the background. */
        fun start(context: Context) {
            val intent = Intent(context, ResearchService::class.java)
            runCatching { if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            if (running) runCatching { context.stopService(Intent(context, ResearchService::class.java)) }
        }

        private fun foregroundType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        private fun service(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, ResearchService::class.java).setAction(action),
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
                NotificationChannel(CHANNEL, "Research", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while research mode is on, so the paper lab and paper bids keep recording with ${AppBook.name} in the background."
                    setShowBadge(false)
                },
            )
        }
    }
}
