package com.gmailchecker.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmailchecker.app.MainActivity
import com.gmailchecker.app.data.AccountStatus
import com.gmailchecker.app.data.AccountStore
import com.gmailchecker.app.data.GoogleApiClient
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = AccountStore.get(applicationContext)
        val settings = store.getSettings()
        val accounts = store.getAccounts()

        if (accounts.isEmpty() || settings.oauthClientId.isBlank()) {
            return Result.success()
        }

        val apiClient = GoogleApiClient()
        val now = System.currentTimeMillis()
        var successCount = 0
        val failures = mutableListOf<Exception>()

        for (account in accounts) {
            // On a retry, don't re-run accounts that already succeeded in the previous attempt.
            val succeededRecently = runAttemptCount > 0 &&
                account.status == AccountStatus.OK &&
                now - (account.lastCheckedEpochMs ?: 0L) < RECENT_SUCCESS_WINDOW_MS
            if (succeededRecently) {
                successCount++
                continue
            }

            try {
                val summary = apiClient.performKeepAlive(
                    email = account.email,
                    refreshToken = store.getRefreshToken(account.email),
                    settings = settings
                )
                store.recordSuccess(account.email, summary)
                successCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.recordFailure(account.email, e)
                failures += e
            }
        }

        if (failures.any { it is IOException } && runAttemptCount < MAX_RETRIES) {
            return Result.retry()
        }

        postNotification(successCount, failures.size)
        return Result.success()
    }

    private fun postNotification(successCount: Int, failureCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "gmail_keep_alive_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Gmail Keep-Alive Checks",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (failureCount == 0) {
            "Gmail Keep-Alive: $successCount account(s) active"
        } else {
            "Gmail Keep-Alive: $failureCount account(s) need attention"
        }

        val text = if (failureCount == 0) {
            "All $successCount Google account(s) checked successfully."
        } else {
            "$successCount succeeded, $failureCount failed. Tap to inspect."
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
    }

    private companion object {
        const val MAX_RETRIES = 3
        val RECENT_SUCCESS_WINDOW_MS = TimeUnit.HOURS.toMillis(12)
    }
}
