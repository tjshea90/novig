package com.tjshea.vigilant.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException

/**
 * The true closing line of every bet (Tj, 2026-09-29: "make a system that finds the true closing odds for each of my bets"): an exact alarm
 * wakes Vigilant a few minutes before each open bet's start ([ClosingLine.nextAt]), whether it's open or not, and [run] reads the fair line
 * of every bet about to start. That read, the last before the start, is the bet's closing line ([ClosingLine]).
 *
 * What it reads: a CNO bet's game page on CrazyNinjaOdds (free), else Vigilant's own fair odds for the bet ([com.tjshea.vigilant.data.tracker
 * .OpenBetPricer]: one bets-only pass for everything starting then, only while Vigilant's scanner is on, so CNO only spends no API credits).
 * Nothing is read while scanning is paused.
 */
object ClosingCapture {
    /** [due] bets were about to start; [read] of them got their closing line. */
    data class Result(val due: Int, val read: Int)

    suspend fun run(c: AppContainer, now: Long = System.currentTimeMillis()): Result {
        c.ensureLoaded()
        val settings = c.currentSettings()
        val due = ClosingLine.due(c.tracker.all(), now)
        if (due.isEmpty()) return Result(0, 0)
        val ids = due.map { it.id }
        // Marked first: a read that fails waits ClosingLine.RETRY_MS before the next try, instead of the alarm firing again at once.
        c.tracker.markCloseTried(ids, now)
        if (settings.paused) return Result(due.size, 0)
        val read = HashSet<String>()
        val cnoIds = due.filter { it.gameUrl != null }.map { it.id }
        if (cnoIds.isNotEmpty()) read += attempt { c.recheck.captureClosing(cnoIds) }.orEmpty()
        val left = ids.filterNot { it in read }
        val pricer = c.betPricer?.takeIf { settings.vigilantOn }
        if (pricer != null && left.isNotEmpty()) {
            attempt { pricer.run(settings, left) }
            read += c.tracker.all().filter { it.id in left && (it.closingSeenAtMs ?: Long.MIN_VALUE) >= now }.map { it.id }
        }
        return Result(due.size, read.size)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** The alarm before each start: exact and allowed in Doze where Android lets Vigilant (`USE_EXACT_ALARM`), else the closest inexact one. */
object ClosingAlarm {
    const val ACTION = "com.tjshea.vigilant.CLOSING_CAPTURE"

    /** When the armed alarm goes off (this process's view), for Diagnostics. */
    @Volatile
    var nextAtMs: Long? = null
        private set

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, ClosingReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Arms the alarm for [bets]' next capture, or cancels it when none needs one. */
    fun schedule(context: Context, bets: List<TrackedBet>, now: Long = System.currentTimeMillis()) = set(context, ClosingLine.nextAt(bets, now))

    fun set(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            if (atMs == null) {
                am.cancel(pending(context))
            } else {
                val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context))
            }
            nextAtMs = atMs
        }
    }
}

/** The alarm going off: the capture runs as expedited work (it needs a network, and may take a minute), not in the receiver. */
class ClosingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClosingAlarm.ACTION) ClosingWorker.enqueue(context)
    }
}

class ClosingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? VigilantApp ?: return Result.success()
        val c = app.container
        try {
            ClosingCapture.run(c)
        } finally {
            // The next start (or a retry of a read that failed), whatever this one did.
            runCatching { ClosingAlarm.schedule(app, c.tracker.all()) }
        }
        return Result.success()
    }

    /** Expedited work before Android 12 runs as a foreground service: a quiet notification while it reads. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Closing lines", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Reading closing lines")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val NAME = "closing-line"
        private const val CHANNEL = "closing_lines"
        private const val NOTIFICATION_ID = 4107

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClosingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
