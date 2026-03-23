package com.honeyjar.app.repositories

import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.data.entities.NotificationEntity
import com.honeyjar.app.models.HoneyNotification
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.honeyjar.app.utils.TimeUtils
import org.json.JSONArray
import java.util.Calendar

object NotificationRepository {
    private val daoFlow = MutableStateFlow<NotificationDao?>(null)
    @Volatile private var dao: NotificationDao? = null
    @Volatile private var statsDao: StatsDao? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize(notificationDao: NotificationDao, statsDao: StatsDao) {
        this.dao = notificationDao
        this.statsDao = statsDao
        this.daoFlow.value = notificationDao
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications: Flow<List<HoneyNotification>> = daoFlow.flatMapLatest { currentDao ->
        currentDao?.getAllNotifications()?.map { entities ->
            entities.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notificationsHome: Flow<List<HoneyNotification>> = daoFlow.flatMapLatest { currentDao ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        currentDao?.getHomeNotifications(todayStart)?.map { entities ->
            entities.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    fun addNotification(notification: HoneyNotification) {
        scope.launch {
            try {
                val currentDao = dao
                if (currentDao == null) {
                    Log.e("HoneyJar-Alerts", "NotificationDao is NULL! Initialization failed?")
                    return@launch
                }
                currentDao.insertNotification(notification.toEntity())
                incrementStats(notification.priority, notification.postTime)
                Log.d("HoneyJar-Alerts", "Successfully stored notification: ${notification.title}")
            } catch (e: Exception) {
                Log.e("HoneyJar-Alerts", "CRITICAL FAILURE in addNotification: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun incrementStats(priority: String, timestamp: Long) {
        val dayStart = TimeUtils.getDayStart(timestamp)
        val bucket = getHourBucket(timestamp)
        statsDao?.let { it.upsertStat(dayStart, priority, bucket) }
    }

    private fun getHourBucket(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(Calendar.HOUR_OF_DAY) / 2
    }

    fun resolveNotification(id: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao?.updateResolvedStatus(id, true, now)
        }
    }

    fun unresolveNotification(id: String) {
        scope.launch {
            dao?.updateResolvedStatus(id, false, 0L)
        }
    }

    fun snoozeNotification(id: String, durationMillis: Long) {
        scope.launch {
            val snoozeUntil = System.currentTimeMillis() + durationMillis
            dao?.snoozeNotification(id, snoozeUntil)
        }
    }

    fun unsnoozeNotification(id: String) {
        scope.launch {
            dao?.snoozeNotification(id, 0L)
        }
    }

    fun changePriority(id: String, newPriority: String) {
        scope.launch {
            dao?.updatePriority(id, newPriority.lowercase())
        }
    }

    fun deleteNotification(id: String) {
        scope.launch {
            dao?.deleteNotificationById(id)
        }
    }

    fun clearAllNotifications() {
        scope.launch {
            dao?.deleteAllNotifications()
        }
    }

    fun resolveAllNotifications() {
        scope.launch {
            dao?.resolveAllNotifications(System.currentTimeMillis())
        }
    }

    fun purgeOldNotifications(autopurgeDays: Int) {
        if (autopurgeDays <= 0) return
        scope.launch {
            val threshold = System.currentTimeMillis() - (autopurgeDays.toLong() * 24 * 60 * 60 * 1000)
            dao?.deleteOldNotifications(threshold)
            Log.d("HoneyJar-Purge", "Purged notifications older than $autopurgeDays days (Threshold: $threshold)")
        }
    }

    private fun NotificationEntity.toModel(): HoneyNotification {
        return HoneyNotification(
            id = id,
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime,
            priority = priority,
            isResolved = isResolved,
            isGrouped = isGrouped,
            snoozeUntil = snoozeUntil,
            resolvedAt = resolvedAt,
            systemActions = systemActionsJson?.let { json ->
                try {
                    val array = JSONArray(json)
                    List(array.length()) { array.getString(it) }
                } catch (e: Exception) { emptyList() }
            } ?: emptyList()
        )
    }

    private fun HoneyNotification.toEntity(): NotificationEntity {
        return NotificationEntity(
            id = id,
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime,
            priority = priority,
            isResolved = isResolved,
            isGrouped = isGrouped,
            iv = null,
            encryptedData = null,
            snoozeUntil = snoozeUntil,
            resolvedAt = resolvedAt,
            systemActionsJson = if (systemActions.isNotEmpty()) JSONArray(systemActions).toString() else null
        )
    }
}
