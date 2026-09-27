package com.tjshea.vigilant.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The Tracker's "background scores system" (Tj, 2026-09-27): every few hours, with a network,
 * whether Vigilant is open or not, open bets whose games are over are settled from their final
 * scores ([com.tjshea.vigilant.data.tracker.BetSettler]: ESPN, MLB's Stats API). Android batches it with other apps' work, so it
 * costs no wake-ups of its own; with no bet due it reads one small file and ends, no network.
 */
class SettleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? VigilantApp ?: return Result.success()
        val report = runCatching { app.container.settler.run() }.getOrNull()
        // A score feed out of reach: the next 3-hourly run tries again, no retry storm.
        return if (report == null) Result.failure() else Result.success()
    }

    companion object {
        private const val NAME = "settle-bets"
        private const val EVERY_HOURS = 3L

        /** Once is enough (kept across restarts); later calls keep the schedule already set. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SettleWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
