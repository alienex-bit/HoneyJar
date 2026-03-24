package com.honeyjar.app.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.honeyjar.app.MainActivity
import com.honeyjar.app.R
import com.honeyjar.app.data.database.HoneyJarDatabase
import com.honeyjar.app.repositories.SettingsRepository
import kotlinx.coroutines.flow.first

class SecondaryAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val notifId = inputData.getString("notifId") ?: return Result.failure()
        val categoryKey = inputData.getString("categoryKey") ?: return Result.failure()

        val db = HoneyJarDatabase.getDatabase(applicationContext)
        val notif = db.notificationDao().getById(notifId) ?: return Result.success()
        if (notif.isResolved || notif.alertFiredAt > 0L) return Result.success()
        if (!SettingsRepository.isSecondaryAlertsEnabled(applicationContext).first()) return Result.success()
        val group = db.priorityGroupDao().getByKey(categoryKey) ?: return Result.success()
        if (!group.secondaryAlertEnabled) return Result.success()

        val deepLinkIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deepLink", "history?filter=$categoryKey")
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            notifId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "honeyjar_alert_$categoryKey"
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_status_bee)
            .setContentTitle("Unread: ${notif.title}")
            .setContentText("You haven't resolved this yet — tap to review")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify("alert_$notifId".hashCode(), notification)
            db.notificationDao().updateAlertFiredAt(notifId, System.currentTimeMillis())
        } catch (e: SecurityException) {
            // Notification permission revoked
        }

        return Result.success()
    }
}
