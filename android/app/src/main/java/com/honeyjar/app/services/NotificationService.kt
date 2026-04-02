package com.honeyjar.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.honeyjar.app.MainActivity
import com.honeyjar.app.R
import com.honeyjar.app.data.dao.AppCategoryDao
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.database.HoneyJarDatabase
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.utils.AppCategoryResolver
import com.honeyjar.app.utils.NotificationCategories
import com.honeyjar.app.workers.SecondaryAlertWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NotificationService : NotificationListenerService() {
    private var isCaptureOngoing = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val STATUS_CHANNEL_ID = "honeyjar_status"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val ACTION_MARK_ALL_READ = "com.honeyjar.app.ACTION_MARK_ALL_READ"
        private const val ALERT_NOTIF_ID_BASE = 2000
    }

    private lateinit var notificationDao: NotificationDao
    private lateinit var appCategoryDao: AppCategoryDao

    private var priorityGroupCache: Map<String, PriorityGroupEntity> = emptyMap()
    private val channelSettingsCache = ConcurrentHashMap<String, Pair<String, String>>()

    private val markAllReadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MARK_ALL_READ)
                NotificationRepository.resolveAllNotifications()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = HoneyJarDatabase.getDatabase(this)
        notificationDao = database.notificationDao()
        appCategoryDao = database.appCategoryDao()
        NotificationRepository.initialize(notificationDao, database.statsDao(), applicationContext)

        serviceScope.launch { AppCategoryResolver.prewarm(appCategoryDao) }

        createStatusChannel()
        startForeground(STATUS_NOTIFICATION_ID, buildStatusNotification(emptyList()))

        val filter = IntentFilter(ACTION_MARK_ALL_READ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(markAllReadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(markAllReadReceiver, filter)
        }

        serviceScope.launch {
            SettingsRepository.isCaptureOngoingEnabled(this@NotificationService).collect { isCaptureOngoing = it }
        }

        serviceScope.launch {
            HoneyJarDatabase.getDatabase(this@NotificationService)
                .priorityGroupDao().getAllPriorityGroups().collect { groups ->
                    priorityGroupCache = groups.associateBy { it.key }
                }
        }

        serviceScope.launch {
            NotificationRepository.notificationsHome
                .debounce(2000L)
                .collect { today ->
                    updateStatusNotification(today)
                }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(markAllReadReceiver) } catch (_: Exception) { }
        NotificationRepository.shutdown()
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications?.forEach { handleSbn(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { handleSbn(it) }
    }

    private fun handleSbn(sbn: StatusBarNotification) {
        serviceScope.launch { handleSbnSuspend(sbn) }
    }

    private suspend fun handleSbnSuspend(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            if (pkg == packageName) return

            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString()
                ?: extras.getCharSequence("android.conversationTitle")?.toString() ?: "Notification"
            val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text") ?: "").toString()
            val time = sbn.postTime

            if (sbn.isOngoing && !isCaptureOngoing) return

            val priority = AppCategoryResolver.categorizeStatic(pkg, title, text)
                ?: AppCategoryResolver.resolve(pkg, this@NotificationService, appCategoryDao)

            val actions = sbn.notification.actions?.mapNotNull { it.title?.toString() } ?: emptyList()
            val honeyNotif = HoneyNotification(
                id = "${sbn.key}_$time",
                packageName = pkg,
                title = title,
                text = text,
                postTime = time,
                priority = priority,
                systemActions = actions
            )

            NotificationRepository.addNotification(honeyNotif)
            postAlertIfNeeded(priority, title, text)
        } catch (_: Exception) {}
    }

    private fun postAlertIfNeeded(categoryKey: String, title: String, text: String) {
        val group = priorityGroupCache[categoryKey] ?: return
        if (group.soundUri == "off" && group.vibrationPattern == "off") return
        val channelId = ensureAlertChannel(categoryKey, group.soundUri, group.vibrationPattern)
        val alertId = ALERT_NOTIF_ID_BASE + (categoryKey.hashCode() and 0x7FFFFFFF)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_status_bee)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(alertId, notification)
    }

    private fun ensureAlertChannel(categoryKey: String, soundUri: String, vibPattern: String): String {
        val channelId = "honeyjar_alert_$categoryKey"
        if (channelSettingsCache[categoryKey] == Pair(soundUri, vibPattern)) return channelId
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(channelId)
        val channel = NotificationChannel(channelId, "${categoryKey.replaceFirstChar { it.uppercase() }} Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(resolveSoundUri(soundUri), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            resolveVibrationPattern(vibPattern)?.let {
                enableVibration(true)
                vibrationPattern = it
            }
        }
        nm.createNotificationChannel(channel)
        channelSettingsCache[categoryKey] = Pair(soundUri, vibPattern)
        return channelId
    }

    private fun resolveSoundUri(soundUri: String): Uri? = when (soundUri) {
        "off" -> null
        "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        "chime" -> Uri.parse("android.resource://$packageName/raw/sound_chime")
        "alert" -> Uri.parse("android.resource://$packageName/raw/sound_alert")
        else -> try { Uri.parse(soundUri) } catch (_: Exception) { null }
    }

    private fun resolveVibrationPattern(pattern: String): LongArray? = when (pattern) {
        "off" -> null
        "short" -> longArrayOf(0, 100)
        "double" -> longArrayOf(0, 100, 100, 100)
        "long" -> longArrayOf(0, 500)
        "urgent" -> longArrayOf(0, 100, 50, 100, 50, 300)
        else -> null
    }

    private fun createStatusChannel() {
        val ch = NotificationChannel(STATUS_CHANNEL_ID, "HoneyJar Status", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildStatusNotification(today: List<HoneyNotification>): android.app.Notification {
        val total = today.size
        val urgent = today.count { it.priority == NotificationCategories.URGENT }
        val contentText = if (urgent > 0) "$urgent urgent · tap to open" else if (total > 0) "$total captured today" else "Listening for notifications"

        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val markAllIntent = PendingIntent.getBroadcast(this, 1, Intent(ACTION_MARK_ALL_READ).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)

        val style = NotificationCompat.InboxStyle().setBigContentTitle("$total notifications today")
        today.groupBy { it.priority }.forEach { (cat, list) -> style.addLine("${cat.replaceFirstChar { it.uppercase() }}: ${list.size}") }
        
        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status_bee)
            .setContentTitle("$total captured today")
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(openIntent)
            .addAction(0, "Mark all read", markAllIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateStatusNotification(today: List<HoneyNotification>) {
        NotificationManagerCompat.from(this).notify(STATUS_NOTIFICATION_ID, buildStatusNotification(today))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null || sbn.packageName == packageName || (reason != 2 && reason != 9)) return
        val notifId = "${sbn.key}_${sbn.postTime}"
        serviceScope.launch {
            val notif = notificationDao.getById(notifId) ?: return@launch
            if (!notif.isResolved && notif.alertFiredAt == 0L) {
                priorityGroupCache[notif.priority]?.takeIf { it.secondaryAlertEnabled }?.let { group ->
                    notificationDao.markDismissedByUser(notifId, System.currentTimeMillis())
                    val request = OneTimeWorkRequestBuilder<SecondaryAlertWorker>()
                        .setInputData(workDataOf("notifId" to notifId, "categoryKey" to notif.priority))
                        .setInitialDelay(group.initialAlertDelayMs, TimeUnit.MILLISECONDS)
                        .build()
                    WorkManager.getInstance(this@NotificationService).enqueueUniqueWork("alert_$notifId", ExistingWorkPolicy.KEEP, request)
                }
            }
        }
    }
}
