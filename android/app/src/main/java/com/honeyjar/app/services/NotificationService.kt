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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.honeyjar.app.MainActivity
import com.honeyjar.app.R
import com.honeyjar.app.data.database.HoneyJarDatabase
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.utils.NotificationCategories
import com.honeyjar.app.utils.TimeUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.debounce

class NotificationService : NotificationListenerService() {
    private var isCaptureOngoing = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val STATUS_CHANNEL_ID = "honeyjar_status"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val ACTION_MARK_ALL_READ = "com.honeyjar.app.ACTION_MARK_ALL_READ"
        private const val ALERT_NOTIF_ID_BASE = 2000
    }

    // In-memory cache of priority group sound/vibration settings
    private var priorityGroupCache: Map<String, PriorityGroupEntity> = emptyMap()
    // Tracks channel settings to avoid redundant recreations: key → (soundUri, vibPattern)
    private val channelSettingsCache = mutableMapOf<String, Pair<String, String>>()

    private val markAllReadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MARK_ALL_READ)
                NotificationRepository.resolveAllNotifications()
        }
    }

    init {
        Log.d("HoneyJar-Alerts", "NotificationService Initialized (Class Loader)")
    }

    override fun onCreate() {
        super.onCreate()
        val database = HoneyJarDatabase.getDatabase(this)
        NotificationRepository.initialize(database.notificationDao(), database.statsDao())

        createStatusChannel()
        startForeground(STATUS_NOTIFICATION_ID, buildStatusNotification(emptyList()))

        val filter = IntentFilter(ACTION_MARK_ALL_READ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(markAllReadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(markAllReadReceiver, filter)
        }

        serviceScope.launch {
            try {
                SettingsRepository.isCaptureOngoingEnabled(this@NotificationService).collect {
                    isCaptureOngoing = it
                }
            } catch (e: Exception) {
                Log.e("HoneyJar-Service", "isCaptureOngoing collector failed", e)
            }
        }

        serviceScope.launch {
            try {
                HoneyJarDatabase.getDatabase(this@NotificationService)
                    .priorityGroupDao().getAllPriorityGroups().collect { groups ->
                        priorityGroupCache = groups.associateBy { it.key }
                    }
            } catch (e: Exception) {
                Log.e("HoneyJar-Service", "priorityGroup collector failed", e)
            }
        }

        // Live status notification updates (Optimized for Battery)
        serviceScope.launch {
            try {
                NotificationRepository.notificationsHome
                    .debounce(2000L)
                    .collect { today ->
                        val activeToday = today.filter { !it.isResolved }
                        updateStatusNotification(activeToday)
                    }
            } catch (e: Exception) {
                Log.e("HoneyJar-Service", "status notification collector failed", e)
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(markAllReadReceiver) } catch (_: Exception) { }
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("HoneyJar-Alerts", "Notification Listener Connected. Syncing active notifications.")

        val active = activeNotifications
        if (active.isNullOrEmpty()) {
            Log.d("HoneyJar-Alerts", "Listener connected — no active notifications to sync.")
        } else {
            active.forEach { sbn -> handleSbn(sbn) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d("HoneyJar-Alerts", "Notification Posted: ${sbn?.packageName}")
        sbn?.let { handleSbn(it) }
    }

    private fun handleSbn(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            // Skip our own status notification
            if (pkg == packageName) return

            Log.d("HoneyJar-Alerts", "handleSbn for $pkg")
            val extras = sbn.notification.extras

            // Robust Extraction
            val rawTitle = extras.getCharSequence("android.title")
            val convTitle = extras.getCharSequence("android.conversationTitle")
            val bigTitle = extras.getCharSequence("android.title.big")

            val rawText = extras.getCharSequence("android.text")
            val bigText = extras.getCharSequence("android.bigText")
            val infoText = extras.getCharSequence("android.infoText")
            val subText = extras.getCharSequence("android.subText")

            Log.d("HoneyJar-Debug", "Extraction for $pkg:")
            Log.d("HoneyJar-Debug", "  rawTitle: $rawTitle")
            Log.d("HoneyJar-Debug", "  convTitle: $convTitle")
            Log.d("HoneyJar-Debug", "  bigTitle: $bigTitle")
            Log.d("HoneyJar-Debug", "  rawText: $rawText")
            Log.d("HoneyJar-Debug", "  bigText: $bigText")
            Log.d("HoneyJar-Debug", "  subText: $subText")

            // 1. Resolve Title (Priority: standard -> conversation -> big -> package label)
            val initialTitle = rawTitle?.toString() ?: convTitle?.toString() ?: bigTitle?.toString()
            val title: String = if (!initialTitle.isNullOrBlank()) {
                initialTitle
            } else {
                try {
                    val pm = packageManager
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    "Notification"
                }
            }

            // 2. Resolve Text (Priority: big -> standard -> info -> sub)
            val text = bigText?.toString() ?: rawText?.toString() ?: infoText?.toString() ?: subText?.toString() ?: ""

            val time = sbn.postTime

            if (sbn.isOngoing && !isCaptureOngoing) {
                Log.d("HoneyJar-Alerts", "Skipping ongoing notification from $pkg (Setting is OFF)")
                return
            }

            val priority = categorize(pkg, title, text)

            val actions = sbn.notification.actions?.mapNotNull { it.title?.toString() } ?: emptyList()

            Log.d("HoneyJar-Alerts", "Processing: $title | Category: $priority | Actions: $actions | From: $pkg")

            val notifId = "${sbn.key}_$time"
            val honeyNotif = HoneyNotification(
                id = notifId,
                packageName = pkg,
                title = title,
                text = text,
                postTime = time,
                priority = priority,
                systemActions = actions
            )

            NotificationRepository.addNotification(honeyNotif)
            postAlertIfNeeded(priority, title, text)
            Log.d("HoneyJar-Alerts", "Captured and Stored successfully!")
        } catch (e: Exception) {
            Log.e("HoneyJar-Service", "Failed to process notification from ${sbn.packageName}", e)
        }
    }

    private fun categorize(pkg: String, title: String, text: String): String {
        val lowPackage = pkg.lowercase()
        val result = when {
            lowPackage.contains("whatsapp") || lowPackage.contains("messenger") ||
            lowPackage.contains("slack") || lowPackage.contains("sms") ||
            lowPackage.contains("messaging") || lowPackage.contains("mms") ||
            lowPackage.contains("message") -> NotificationCategories.MESSAGES

            title.lowercase().contains("payment") || title.lowercase().contains("critical") ||
            text.lowercase().contains("urgent") || text.lowercase().contains("down") -> NotificationCategories.URGENT

            lowPackage.contains("calendar") || title.lowercase().contains("appointment") ||
            title.lowercase().contains("meeting") -> NotificationCategories.CALENDAR

            lowPackage.contains("gmail") || lowPackage.contains("outlook") ||
            lowPackage.contains("mail") -> NotificationCategories.EMAIL

            lowPackage.contains("amazon") || title.lowercase().contains("delivery") ||
            text.lowercase().contains("parcel") -> NotificationCategories.DELIVERY

            lowPackage.contains("play") || title.lowercase().contains("update") -> NotificationCategories.UPDATES

            else -> NotificationCategories.SYSTEM
        }
        if (result == NotificationCategories.SYSTEM) {
            Log.d("HoneyJar-Categorize", "Uncategorised → system: pkg=$pkg")
        }
        return result
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { Log.d("HoneyJar-Alerts", "Notification dismissed: ${it.packageName}") }
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
        try {
            getSystemService(NotificationManager::class.java).notify(alertId, notification)
        } catch (e: Exception) {
            Log.w("HoneyJar-Alert", "Could not post alert for $categoryKey", e)
        }
    }

    private fun ensureAlertChannel(categoryKey: String, soundUri: String, vibPattern: String): String {
        val channelId = "honeyjar_alert_$categoryKey"
        if (channelSettingsCache[categoryKey] == Pair(soundUri, vibPattern)) return channelId
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(channelId)
        val resolvedSound = resolveSoundUri(soundUri)
        val resolvedVib = resolveVibrationPattern(vibPattern)
        val channel = NotificationChannel(
            channelId,
            "${categoryKey.replaceFirstChar { it.uppercase() }} Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            if (resolvedSound != null) {
                setSound(resolvedSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            } else {
                setSound(null, null)
            }
            if (resolvedVib != null) {
                enableVibration(true)
                vibrationPattern = resolvedVib
            } else {
                enableVibration(false)
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
        else -> try { Uri.parse(soundUri) } catch (e: Exception) { null }
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
            description = "Live notification count"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildStatusNotification(today: List<HoneyNotification>): android.app.Notification {
        val total = today.size
        val urgent = today.count { it.priority == NotificationCategories.URGENT }
        val contentText = if (urgent > 0) "$urgent urgent · tap to open" else if (total > 0) "$total captured today" else "Listening for notifications"

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markAllIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_MARK_ALL_READ).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("$total notifications today")

        val emojiMap = mapOf(
            "urgent" to "🚨", "messages" to "💬", "calendar" to "📅",
            "email" to "✉️", "updates" to "🔄", "delivery" to "📦", "system" to "⚙️"
        )
        today.groupBy { it.priority }.entries.sortedByDescending { it.value.size }.forEach { (cat, list) ->
            style.addLine("${emojiMap[cat] ?: "📌"} ${cat.replaceFirstChar { it.uppercase() }}: ${list.size}")
        }
        today.sortedByDescending { it.postTime }.take(3).forEach { style.addLine("• ${it.title}") }
        if (today.isEmpty()) style.addLine("No notifications yet today")

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status_bee)
            .setContentTitle("$total notifications collected today")
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(openIntent)
            .addAction(0, "Open app", openIntent)
            .addAction(0, "Mark all read", markAllIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateStatusNotification(today: List<HoneyNotification>) {
        try {
            NotificationManagerCompat.from(this).notify(STATUS_NOTIFICATION_ID, buildStatusNotification(today))
        } catch (e: SecurityException) {
            Log.w("HoneyJar-Status", "Cannot update status notification: ${e.message}")
        }
    }
}
