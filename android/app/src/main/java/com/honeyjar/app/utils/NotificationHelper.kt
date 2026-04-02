package com.honeyjar.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/**
 * Centralizes notification channel management to ensure consistency between
 * the foreground NotificationService and background SecondaryAlertWorker.
 */
object NotificationHelper {
    // Optimization: avoid re-creating channels if settings haven't changed
    private val channelSettingsCache = mutableMapOf<String, Pair<String, String>>()

    fun ensureAlertChannel(context: Context, categoryKey: String, soundUri: String, vibPattern: String): String {
        val channelId = "honeyjar_alert_$categoryKey"
        
        // Cache check to avoid redundant NM calls
        if (channelSettingsCache[categoryKey] == Pair(soundUri, vibPattern)) return channelId
        
        val nm = context.getSystemService(NotificationManager::class.java)
        
        // Android 8.0+ requires channels to be immutable. If sound/vibration changes, 
        // we must delete and recreate.
        nm.getNotificationChannel(channelId)?.let {
            nm.deleteNotificationChannel(channelId)
        }
        
        val name = "${categoryKey.lowercase().replaceFirstChar { it.uppercase() }} Alerts"
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(resolveSoundUri(context, soundUri), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build())
            resolveVibrationPattern(vibPattern)?.let {
                enableVibration(true)
                vibrationPattern = it
            }
        }
        nm.createNotificationChannel(channel)
        
        channelSettingsCache[categoryKey] = Pair(soundUri, vibPattern)
        return channelId
    }

    fun resolveSoundUri(context: Context, soundUri: String): Uri? = when (soundUri) {
        "off" -> null
        "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        "chime" -> Uri.parse("android.resource://${context.packageName}/raw/sound_chime")
        "alert" -> Uri.parse("android.resource://${context.packageName}/raw/sound_alert")
        else -> try { Uri.parse(soundUri) } catch (_: Exception) { null }
    }

    fun resolveVibrationPattern(pattern: String): LongArray? = when (pattern) {
        "off" -> null
        "short" -> longArrayOf(0, 100)
        "double" -> longArrayOf(0, 100, 100, 100)
        "long" -> longArrayOf(0, 500)
        "urgent" -> longArrayOf(0, 100, 50, 100, 50, 300)
        else -> null
    }
}
