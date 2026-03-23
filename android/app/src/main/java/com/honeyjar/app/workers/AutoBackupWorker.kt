package com.honeyjar.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.honeyjar.app.data.database.HoneyJarDatabase
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.utils.BackupManager
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 30% Battery Guard
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()) else 100f
        
        if (pct < 30) {
            Log.w("AutoBackupWorker", "Battery too low for backup: ${pct.toInt()}% (Threshold: 30%)")
            return Result.retry()
        }

        return try {
            // Use standard background execution for now to avoid foreground service crashes
            // setForeground(getForegroundInfo()) 

            val db = HoneyJarDatabase.getDatabase(context)
            val json = BackupManager.buildBackupJson(context, db.statsDao(), db.priorityGroupDao())

            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val fileName = "honeyjar_backup_$date.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                java.io.File(dir, fileName).writeText(json)
            }

            SettingsRepository.setLastAutoBackupTime(context, System.currentTimeMillis())

            showSuccessNotification(fileName)
            Log.d("AutoBackupWorker", "Backup saved: $fileName")
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoBackupWorker", "Backup failed", e)
            showFailureNotification()
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("HoneyJar")
            .setContentText("Saving backup…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID_PROGRESS, notification)
    }

    private fun showSuccessNotification(fileName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Backup saved")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    private fun showFailureNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Backup failed")
            .setContentText("Could not save automatic backup. Check storage permissions.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Auto Backup", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "honeyjar_auto_backup"
        private const val NOTIF_ID = 9001
        private const val NOTIF_ID_PROGRESS = 9002
        private const val WORK_NAME = "honeyjar_auto_backup"

        fun schedule(context: Context, frequency: String) {
            val workManager = WorkManager.getInstance(context)

            if (frequency == "off") {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val (interval, unit) = when (frequency) {
                "daily"   -> 1L to TimeUnit.DAYS
                "weekly"  -> 7L to TimeUnit.DAYS
                "monthly" -> 30L to TimeUnit.DAYS
                else      -> { workManager.cancelUniqueWork(WORK_NAME); return }
            }

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(interval, unit)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        // Run a one-off backup immediately (used when pill is first selected)
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
