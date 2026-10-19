package com.gmailchecker.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gmailchecker.app.data.AppSettings
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val UNIQUE_WORK_NAME = "gmail_keep_alive_periodic_work"

    fun syncSchedule(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.autoCheckEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val intervalDays = settings.intervalDays.coerceAtLeast(1).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
            intervalDays,
            TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
