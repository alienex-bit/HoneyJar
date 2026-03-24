package com.honeyjar.app.utils

import android.content.Context
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.dao.PriorityGroupDao
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.data.entities.NotificationEntity
import com.honeyjar.app.data.entities.NotificationStatsEntity
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.data.ThemePrefs
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.ui.theme.HoneyJarThemeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    suspend fun buildBackupJson(
        context: Context,
        statsDao: StatsDao,
        priorityGroupDao: PriorityGroupDao
    ): String {
        // Use the model flow (decrypts on the fly) so backup always contains readable content
        val notifications = withTimeout(5_000) { NotificationRepository.notifications.first() }
        val groups = withTimeout(5_000) { priorityGroupDao.getAllPriorityGroups().first() }
        val stats = withTimeout(5_000) { statsDao.getAllStats().first() }

        val settingsObj = JSONObject().apply {
            put("isSmartGrouping", SettingsRepository.isSmartGroupingEnabled(context).first())
            put("isPriorityFiltering", SettingsRepository.isPriorityFilteringEnabled(context).first())
            put("isFocusMode", SettingsRepository.isFocusModeEnabled(context).first())
            put("isCaptureOngoing", SettingsRepository.isCaptureOngoingEnabled(context).first())
            put("isProEnabled", SettingsRepository.isProEnabled(context).first())
            put("autoBackupFrequency", SettingsRepository.getAutoBackupFrequency(context).first())
            put("hasCompletedOnboarding", SettingsRepository.hasCompletedOnboardingIntro(context).first())
            put("lastAutoBackupTime", SettingsRepository.getLastAutoBackupTime(context).first())
            put("secondaryAlertsEnabled", SettingsRepository.isSecondaryAlertsEnabled(context).first())
        }

        val groupsArray = JSONArray().apply {
            groups.forEach { g ->
                put(JSONObject().apply {
                    put("key", g.key)
                    put("label", g.label)
                    put("colour", g.colour)
                    put("isEnabled", g.isEnabled)
                    put("position", g.position)
                    put("soundUri", g.soundUri)
                    put("vibrationPattern", g.vibrationPattern)
                    put("secondaryAlertEnabled", g.secondaryAlertEnabled)
                    put("initialAlertDelayMs", g.initialAlertDelayMs)
                    put("secondaryAlertDelayMs", g.secondaryAlertDelayMs)
                })
            }
        }

        val notifArray = JSONArray().apply {
            notifications.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("packageName", n.packageName)
                    put("title", n.title)
                    put("text", n.text)
                    put("postTime", n.postTime)
                    put("priority", n.priority)
                    put("isResolved", n.isResolved)
                    put("isGrouped", n.isGrouped)
                    put("snoozeUntil", n.snoozeUntil)
                    put("resolvedAt", n.resolvedAt)
                    put("systemActionsJson", if (n.systemActions.isNotEmpty()) org.json.JSONArray(n.systemActions).toString() else JSONObject.NULL)
                })
            }
        }

        val statsArray = JSONArray().apply {
            stats.forEach { s ->
                put(JSONObject().apply {
                    put("dateStart", s.dateStart)
                    put("category", s.category)
                    put("hourBucket", s.hourBucket)
                    put("count", s.count)
                })
            }
        }

        return JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("theme", ThemePrefs.theme.value.name)
            put("settings", settingsObj)
            put("priorityGroups", groupsArray)
            put("notifications", notifArray)
            put("stats", statsArray)
        }.toString(2)
    }

    suspend fun restoreFromJson(
        context: Context,
        json: String,
        notificationDao: NotificationDao,
        statsDao: StatsDao,
        priorityGroupDao: PriorityGroupDao
    ) {
        val root = JSONObject(json)
        check(root.getInt("version") == 1) { "Unsupported backup version" }

        // Settings
        val s = root.getJSONObject("settings")
        SettingsRepository.setSmartGrouping(context, s.optBoolean("isSmartGrouping", true))
        SettingsRepository.setPriorityFiltering(context, s.optBoolean("isPriorityFiltering", true))
        SettingsRepository.setFocusMode(context, s.optBoolean("isFocusMode", false))
        SettingsRepository.setCaptureOngoing(context, s.optBoolean("isCaptureOngoing", false))
        SettingsRepository.setProEnabled(context, s.optBoolean("isProEnabled", false))
        SettingsRepository.setAutoBackupFrequency(context, s.optString("autoBackupFrequency", "off"))
        if (s.optBoolean("hasCompletedOnboarding", false)) {
            SettingsRepository.setHasCompletedOnboardingIntro(context, true)
        }
        SettingsRepository.setLastAutoBackupTime(context, s.optLong("lastAutoBackupTime", 0L))
        SettingsRepository.setSecondaryAlertsEnabled(context, s.optBoolean("secondaryAlertsEnabled", true))

        // Theme
        val themeName = root.optString("theme", HoneyJarThemeType.DarkHoney.name)
        val theme = try { HoneyJarThemeType.valueOf(themeName) } catch (e: Exception) { HoneyJarThemeType.DarkHoney }
        ThemePrefs.setTheme(context, theme)

        // Priority groups
        val groups = root.getJSONArray("priorityGroups")
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            priorityGroupDao.insertPriorityGroup(PriorityGroupEntity(
                key = g.getString("key"),
                label = g.getString("label"),
                colour = g.getString("colour"),
                isEnabled = g.optBoolean("isEnabled", true),
                position = g.optInt("position", i),
                soundUri = g.optString("soundUri", "off"),
                vibrationPattern = g.optString("vibrationPattern", "off"),
                secondaryAlertEnabled = g.optBoolean("secondaryAlertEnabled", true),
                initialAlertDelayMs = g.optLong("initialAlertDelayMs", 300_000L),
                secondaryAlertDelayMs = g.optLong("secondaryAlertDelayMs", 1_800_000L)
            ))
        }

        // Notifications — IGNORE conflict so existing records are never overwritten
        val notifs = root.getJSONArray("notifications")
        for (i in 0 until notifs.length()) {
            val n = notifs.getJSONObject(i)
            notificationDao.insertNotification(NotificationEntity(
                id = n.getString("id"),
                packageName = n.getString("packageName"),
                title = n.getString("title"),
                text = n.getString("text"),
                postTime = n.getLong("postTime"),
                priority = n.getString("priority"),
                isResolved = n.optBoolean("isResolved", false),
                isGrouped = n.optBoolean("isGrouped", false),
                snoozeUntil = n.optLong("snoozeUntil", 0L),
                resolvedAt = n.optLong("resolvedAt", 0L),
                systemActionsJson = if (n.isNull("systemActionsJson")) null else n.getString("systemActionsJson")
            ))
        }

        // Stats — REPLACE merges counts from backup with existing rows
        val statsArr = root.getJSONArray("stats")
        for (i in 0 until statsArr.length()) {
            val s2 = statsArr.getJSONObject(i)
            statsDao.insertStat(NotificationStatsEntity(
                dateStart = s2.getLong("dateStart"),
                category = s2.getString("category"),
                hourBucket = s2.getInt("hourBucket"),
                count = s2.getInt("count")
            ))
        }
    }
}
