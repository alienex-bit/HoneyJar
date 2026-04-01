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
import com.honeyjar.app.utils.AppCategoryResolver
import com.honeyjar.app.utils.NotificationCategories
import com.honeyjar.app.utils.TimeUtils
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.workers.SecondaryAlertWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.debounce
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
    private lateinit var appCategoryDao: com.honeyjar.app.data.dao.AppCategoryDao

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
        notificationDao = database.notificationDao()
        appCategoryDao = database.appCategoryDao()
        NotificationRepository.initialize(notificationDao, database.statsDao())

        // Prewarm resolver: evicts stale Play Store entries from cache
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

    // Thin non-suspend launcher — called from onNotificationPosted / onListenerConnected
    // which are both on the main thread and cannot suspend directly.
    private fun handleSbn(sbn: StatusBarNotification) {
        serviceScope.launch { handleSbnSuspend(sbn) }
    }

    private suspend fun handleSbnSuspend(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            if (pkg == packageName) return

            Log.d("HoneyJar-Alerts", "handleSbn for $pkg")
            val extras = sbn.notification.extras

            val rawTitle = extras.getCharSequence("android.title")
            val convTitle = extras.getCharSequence("android.conversationTitle")
            val bigTitle = extras.getCharSequence("android.title.big")
            val rawText = extras.getCharSequence("android.text")
            val bigText = extras.getCharSequence("android.bigText")
            val infoText = extras.getCharSequence("android.infoText")
            val subText = extras.getCharSequence("android.subText")

            Log.d("HoneyJar-Debug", "Extraction for $pkg: rawTitle=$rawTitle rawText=$rawText")

            val initialTitle = rawTitle?.toString() ?: convTitle?.toString() ?: bigTitle?.toString()
            val title: String = if (!initialTitle.isNullOrBlank()) {
                initialTitle
            } else {
                try {
                    val pm = packageManager
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) { "Notification" }
            }

            val text = bigText?.toString() ?: rawText?.toString() ?: infoText?.toString() ?: subText?.toString() ?: ""
            val time = sbn.postTime

            if (sbn.isOngoing && !isCaptureOngoing) {
                Log.d("HoneyJar-Alerts", "Skipping ongoing notification from $pkg (Setting is OFF)")
                return
            }

            // categorize() is now suspend — falls through to PackageManager / Play Store
            // for any package not in the static lookup table
            val priority = categorize(pkg, title, text)

            val actions = sbn.notification.actions?.mapNotNull { it.title?.toString() } ?: emptyList()
            Log.d("HoneyJar-Alerts", "Processing: $title | Category: $priority | From: $pkg")

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
            Log.d("HoneyJar-Alerts", "Captured and Stored successfully!")
        } catch (e: Exception) {
            Log.e("HoneyJar-Service", "Failed to process notification from ${sbn.packageName}", e)
        }
    }

    private suspend fun categorize(pkg: String, title: String, text: String): String {
        // Stage 1: exact package lookup (static table — no I/O)
        val staticResult = PACKAGE_CATEGORY_MAP[pkg]
            ?: categorizeBySubstring(pkg, title, text)

        if (staticResult != NotificationCategories.SYSTEM) return staticResult

        // Stage 2: dynamic resolver — PackageManager then Play Store scrape
        // Only runs when the static table misses (i.e. new/unknown package)
        return AppCategoryResolver.resolve(pkg, this, appCategoryDao)
    }

    private fun categorizeBySubstring(pkg: String, title: String, text: String): String {
        val p = pkg.lowercase()
        val t = title.lowercase()
        val b = text.lowercase()

        return when {
            // Messages — explicit messaging apps only
            p.contains("whatsapp") || p.contains("telegram") || p.contains("signal") ||
            p.contains(".sms") || p.contains(".mms") || p.contains(".messaging") ||
            p.contains(".messages") -> NotificationCategories.MESSAGES

            // Social — broader comms/community
            p.contains("twitter") || p.contains("instagram") || p.contains("facebook") ||
            p.contains("reddit") || p.contains("linkedin") || p.contains("discord") ||
            p.contains("tiktok") || p.contains("snapchat") || p.contains("pinterest") ||
            p.contains("threads") || p.contains("mastodon") -> NotificationCategories.SOCIAL

            // Email
            p.contains("gmail") || p.contains("outlook") || p.contains(".mail") ||
            p.contains("mailbox") || p.contains("easilydo") -> NotificationCategories.EMAIL

            // Calendar
            p.contains("calendar") || p.contains("bizcal") ||
            t.contains("appointment") || t.contains("meeting") ||
            t.contains("reminder") -> NotificationCategories.CALENDAR

            // Finance
            p.contains("revolut") || p.contains("monzo") || p.contains("starling") ||
            p.contains("barclays") || p.contains("lloyds") || p.contains("hsbc") ||
            p.contains("paypal") || p.contains("cashapp") || p.contains("wallet") ||
            p.contains("safepal") || p.contains("coinbase") || p.contains("binance") ||
            p.contains("clearscore") || p.contains("experian") ||
            t.contains("payment") || t.contains("transaction") || t.contains("transfer") -> NotificationCategories.FINANCE

            // Shopping / delivery
            p.contains("amazon") || p.contains("ebay") || p.contains("etsy") ||
            p.contains("wayfair") || p.contains("asos") || p.contains("next.android") ||
            p.contains("asda") || p.contains("tesco") || p.contains("dominos") ||
            p.contains("deliveroo") || p.contains("justeat") || p.contains("trainline") ||
            p.contains("pal.train") || p.contains("bluelightcard") || p.contains("dreamcar") ||
            p.contains("telescope") || p.contains("giveaway") ||
            t.contains("delivery") || t.contains("dispatched") || t.contains("your order") ||
            b.contains("parcel") || b.contains("tracking") -> NotificationCategories.SHOPPING

            // Travel / transport
            p.contains("uber") || p.contains("lyft") || p.contains("bolt.") ||
            p.contains("waze") || p.contains("maps") || p.contains("citymapper") ||
            p.contains("trainpal") || p.contains("gearhead") || p.contains("autoproject") -> NotificationCategories.TRAVEL

            // Weather
            p.contains("weather") || p.contains("windy") || p.contains("lightning") ||
            p.contains("accuweather") || p.contains("bbc.mobile.weather") ||
            p.contains("daemonapp") -> NotificationCategories.WEATHER

            // Media / entertainment
            p.contains("youtube") || p.contains("spotify") || p.contains("netflix") ||
            p.contains("disney") || p.contains("primevideo") || p.contains("itvhub") ||
            p.contains("bbciplayer") || p.contains("soundcloud") || p.contains("mixcloud") ||
            p.contains("audible") || p.contains("capcut") || p.contains("lvoverseas") ||
            p.contains("magazines") || p.contains("patreon") ||
            p.contains("game") || p.contains("games") || p.contains("gaming") -> NotificationCategories.MEDIA

            // Device / system maintenance
            p.contains("vending") || p.contains("samsungapps") || p.contains("packageinstaller") ||
            p.contains("wssyncmldm") || p.contains("fota") || p.contains("appmanager") ||
            p.contains("oneconnect") || p.contains("smartthings") || p.contains("eero") ||
            p.contains("surfshark") || p.contains("vpn") || p.contains("adguard") ||
            p.contains("firefox") || p.contains("chrome") || p.contains("browser") ||
            p.contains("systemui") || p.contains("providers.downloads") ||
            p.contains("bixby") || p.contains("scloud") || p.contains("smartcapture") ||
            p.contains("devicesecurity") || p.contains("gms") || p == "android" -> NotificationCategories.DEVICE

            // Urgent — only explicit security/critical signals
            t.contains("critical") || t.contains("security alert") || t.contains("fraud") ||
            t.contains("unauthorised") || t.contains("unauthorized") ||
            b.contains("critical") || b.contains("security alert") -> NotificationCategories.URGENT

            else -> NotificationCategories.SYSTEM
        }
    }

    companion object {
        // Exact-match package lookup — takes priority over substring matching.
        // Add new packages here as they appear in logs (HoneyJar-Categorize tag).
        private val PACKAGE_CATEGORY_MAP = mapOf(
            // ── Messages ──────────────────────────────────────────────────────
            "com.whatsapp"                              to NotificationCategories.MESSAGES,
            "com.whatsapp.w4b"                         to NotificationCategories.MESSAGES,
            "org.telegram.messenger"                   to NotificationCategories.MESSAGES,
            "tw.nekomimi.nekogram"                     to NotificationCategories.MESSAGES,
            "org.thoughtcrime.securesms"               to NotificationCategories.MESSAGES, // Signal
            "com.google.android.apps.messaging"        to NotificationCategories.MESSAGES,
            "com.samsung.android.messaging"            to NotificationCategories.MESSAGES,

            // ── Social ────────────────────────────────────────────────────────
            "com.twitter.android"                      to NotificationCategories.SOCIAL,
            "com.facebook.orca"                        to NotificationCategories.SOCIAL,  // Messenger
            "com.facebook.katana"                      to NotificationCategories.SOCIAL,
            "com.instagram.android"                    to NotificationCategories.SOCIAL,
            "com.instagram.barcelona"                  to NotificationCategories.SOCIAL,  // Threads
            "com.reddit.frontpage"                     to NotificationCategories.SOCIAL,
            "com.linkedin.android"                     to NotificationCategories.SOCIAL,
            "com.discord"                              to NotificationCategories.SOCIAL,
            "com.zhiliaoapp.musically"                 to NotificationCategories.SOCIAL,  // TikTok
            "com.spond.spond"                          to NotificationCategories.SOCIAL,
            "com.qohlo.ca"                             to NotificationCategories.CALLS,   // call log app

            // ── Calls ─────────────────────────────────────────────────────────
            "com.hb.dialer.free"                       to NotificationCategories.CALLS,
            "com.samsung.android.dialer"               to NotificationCategories.CALLS,
            "com.google.android.dialer"                to NotificationCategories.CALLS,

            // ── Email ─────────────────────────────────────────────────────────
            "com.google.android.gm"                    to NotificationCategories.EMAIL,
            "com.microsoft.office.outlook"             to NotificationCategories.EMAIL,
            "com.easilydo.mail"                        to NotificationCategories.EMAIL,
            "com.samsung.android.email.provider"       to NotificationCategories.EMAIL,

            // ── Calendar ──────────────────────────────────────────────────────
            "com.samsung.android.calendar"             to NotificationCategories.CALENDAR,
            "com.google.android.calendar"              to NotificationCategories.CALENDAR,
            "com.appgenix.bizcal.pro"                  to NotificationCategories.CALENDAR,

            // ── Finance ───────────────────────────────────────────────────────
            "com.revolut.revolut"                      to NotificationCategories.FINANCE,
            "io.safepal.wallet"                        to NotificationCategories.FINANCE,
            "com.google.android.apps.walletnfcrel"     to NotificationCategories.FINANCE,
            "com.clearscore.mobile"                    to NotificationCategories.FINANCE,
            "com.fumbgames.bitcoinminor"               to NotificationCategories.FINANCE,
            "io.voodoo.paper2"                         to NotificationCategories.FINANCE,
            "com.blockchainvault"                      to NotificationCategories.FINANCE,
            "com.zypto"                                to NotificationCategories.FINANCE,

            // ── Shopping ──────────────────────────────────────────────────────
            "com.amazon.mShop.android.shopping"        to NotificationCategories.SHOPPING,
            "com.amazon.dee.app"                       to NotificationCategories.SHOPPING, // Alexa
            "com.amazon.avod.thirdpartyclient"         to NotificationCategories.SHOPPING, // Prime Video
            "uk.co.next.android"                       to NotificationCategories.SHOPPING,
            "com.asda.rewards"                         to NotificationCategories.SHOPPING,
            "com.wayfair.wayfair"                      to NotificationCategories.SHOPPING,
            "uk.co.dominos.android"                    to NotificationCategories.SHOPPING,
            "uk.co.dreamcargiveaways"                  to NotificationCategories.SHOPPING,
            "com.bluelightcard.user"                   to NotificationCategories.SHOPPING,
            "tv.telescope.onepercentclub.uk"           to NotificationCategories.SHOPPING,
            "com.pal.train"                            to NotificationCategories.SHOPPING,
            "net.tsapps.appsales"                      to NotificationCategories.SHOPPING,

            // ── Travel ────────────────────────────────────────────────────────
            "com.ubercab"                              to NotificationCategories.TRAVEL,
            "com.ubercab.eats"                         to NotificationCategories.TRAVEL,
            "com.waze"                                 to NotificationCategories.TRAVEL,
            "com.google.android.apps.maps"             to NotificationCategories.TRAVEL,
            "com.google.android.projection.gearhead"   to NotificationCategories.TRAVEL, // Android Auto
            "com.wetherspoon.orderandpay"              to NotificationCategories.TRAVEL,

            // ── Weather ───────────────────────────────────────────────────────
            "com.devexpert.weather"                    to NotificationCategories.WEATHER,
            "com.windyty.android"                      to NotificationCategories.WEATHER,
            "com.jrustonapps.mylightningtrackerpro"    to NotificationCategories.WEATHER,
            "com.accuweather.android"                  to NotificationCategories.WEATHER,
            "com.sec.android.daemonapp"                to NotificationCategories.WEATHER, // Samsung weather widget
            "com.appmind.radios.gb"                    to NotificationCategories.MEDIA,   // radio

            // ── Media ─────────────────────────────────────────────────────────
            "app.revanced.android.youtube"             to NotificationCategories.MEDIA,
            "com.google.android.youtube"               to NotificationCategories.MEDIA,
            "com.google.android.apps.youtube.music"    to NotificationCategories.MEDIA,
            "app.rvx.android.apps.youtube.music"       to NotificationCategories.MEDIA,
            "com.spotify.music"                        to NotificationCategories.MEDIA,
            "com.google.android.apps.magazines"        to NotificationCategories.MEDIA,   // Google News
            "com.lemon.lvoverseas"                     to NotificationCategories.MEDIA,   // CapCut
            "com.mixcloud.player"                      to NotificationCategories.MEDIA,
            "com.patreon.android"                      to NotificationCategories.MEDIA,
            "com.moonactive.jellybusters"              to NotificationCategories.MEDIA,   // game
            "com.hyperup.holepeople"                   to NotificationCategories.MEDIA,   // game
            "com.pocketchamps.game"                    to NotificationCategories.MEDIA,
            "com.pc.sand.loop"                         to NotificationCategories.MEDIA,
            "com.funcamerastudio.videomaker"           to NotificationCategories.MEDIA,
            "com.backdrops.wallpapers"                 to NotificationCategories.MEDIA,
            "com.adobe.reader"                         to NotificationCategories.MEDIA,
            "com.samsung.storyservice"                 to NotificationCategories.MEDIA,

            // ── Device ────────────────────────────────────────────────────────
            "com.adguard.android"                      to NotificationCategories.DEVICE,
            "com.android.systemui"                     to NotificationCategories.DEVICE,
            "android"                                  to NotificationCategories.DEVICE,
            "com.microsoft.appmanager"                 to NotificationCategories.DEVICE,  // Link to Windows
            "com.wssyncmldm"                           to NotificationCategories.DEVICE,  // Samsung FOTA
            "com.android.vending"                      to NotificationCategories.DEVICE,  // Play Store
            "com.sec.android.app.samsungapps"          to NotificationCategories.DEVICE,  // Galaxy Store
            "com.google.android.gms"                   to NotificationCategories.DEVICE,
            "com.android.providers.downloads"          to NotificationCategories.DEVICE,
            "com.samsung.android.oneconnect"           to NotificationCategories.DEVICE,  // SmartThings
            "com.samsung.android.sm.devicesecurity"    to NotificationCategories.DEVICE,
            "com.samsung.android.bixby.wakeup"         to NotificationCategories.DEVICE,
            "com.samsung.android.app.smartcapture"     to NotificationCategories.DEVICE,
            "com.samsung.android.scloud"               to NotificationCategories.DEVICE,
            "com.samsung.android.voc"                  to NotificationCategories.DEVICE,
            "com.samsung.wearable.watch6plugin"        to NotificationCategories.DEVICE,
            "com.samsung.android.forest"               to NotificationCategories.DEVICE,
            "com.sec.android.app.clockpackage"         to NotificationCategories.DEVICE,
            "com.google.android.googlequicksearchbox"  to NotificationCategories.DEVICE,
            "com.google.android.apps.photos"           to NotificationCategories.DEVICE,
            "com.sec.android.app.camera"               to NotificationCategories.DEVICE,
            "com.sec.android.app.shealth"              to NotificationCategories.DEVICE,  // Samsung Health → device for now
            "org.mozilla.firefox"                      to NotificationCategories.DEVICE,
            "com.surfshark.vpnclient.android"          to NotificationCategories.DEVICE,
            "com.eero.android"                         to NotificationCategories.DEVICE,
            "com.google.android.packageinstaller"      to NotificationCategories.DEVICE,
            "com.microsoft.skydrive"                   to NotificationCategories.DEVICE,
            "org.zwanoo.android.speedtest"             to NotificationCategories.DEVICE,
            "xda.dante.shm.mod.companion"              to NotificationCategories.DEVICE,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null || sbn.packageName == packageName) return
        Log.d("HoneyJar-Alerts", "Notification removed: ${sbn.packageName} reason=$reason")
        // reason 2 = REASON_LISTENER_CANCEL (single swipe), 9 = REASON_LISTENER_CANCEL_DELETED (clear all)
        if (reason != 2 && reason != 9) return
        val notifId = "${sbn.key}_${sbn.postTime}"
        serviceScope.launch {
            try {
                val globalEnabled = SettingsRepository.isSecondaryAlertsEnabled(this@NotificationService).first()
                if (!globalEnabled) return@launch
                val notif = notificationDao.getById(notifId) ?: return@launch
                if (notif.isResolved || notif.alertFiredAt > 0L) return@launch
                val group = priorityGroupCache[notif.priority] ?: return@launch
                if (!group.secondaryAlertEnabled) return@launch
                notificationDao.markDismissedByUser(notifId, System.currentTimeMillis())
                scheduleSecondaryAlert(notifId, notif.priority, group.initialAlertDelayMs)
                Log.d("HoneyJar-Alerts", "Scheduled secondary alert for $notifId in ${group.initialAlertDelayMs}ms")
            } catch (e: Exception) {
                Log.e("HoneyJar-Service", "Failed to handle dismissal for $notifId", e)
            }
        }
    }

    private fun scheduleSecondaryAlert(notifId: String, categoryKey: String, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<SecondaryAlertWorker>()
            .setInputData(workDataOf("notifId" to notifId, "categoryKey" to categoryKey))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "alert_$notifId", ExistingWorkPolicy.KEEP, request
        )
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
            "urgent"   to "🚨", "messages" to "💬", "social"   to "🌐",
            "email"    to "✉️", "calendar" to "📅", "calls"    to "📞",
            "weather"  to "🌦️", "travel"  to "🚗", "finance"  to "💰",
            "shopping" to "🛒", "media"    to "🎬", "device"   to "📱",
            "system"   to "⚙️"
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
