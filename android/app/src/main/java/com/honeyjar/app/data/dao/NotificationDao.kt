package com.honeyjar.app.data.dao

import androidx.room.*
import com.honeyjar.app.data.entities.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE postTime >= :timestamp ORDER BY postTime DESC")
    fun getNotificationsSince(timestamp: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isResolved = 0 OR postTime >= :timestamp ORDER BY postTime DESC")
    fun getHomeNotifications(timestamp: Long): Flow<List<NotificationEntity>>

@Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isResolved = :isResolved, resolvedAt = :resolvedAtMs WHERE id = :id")
    suspend fun updateResolvedStatus(id: String, isResolved: Boolean, resolvedAtMs: Long)

    @Query("UPDATE notifications SET snoozeUntil = :snoozeUntil WHERE id = :id")
    suspend fun snoozeNotification(id: String, snoozeUntil: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("DELETE FROM notifications WHERE postTime < :timestamp")
    suspend fun deleteOldNotifications(timestamp: Long)

    @Query("UPDATE notifications SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: String)

    @Query("UPDATE notifications SET isResolved = 1, resolvedAt = :resolvedAtMs WHERE isResolved = 0")
    suspend fun resolveAllNotifications(resolvedAtMs: Long)

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NotificationEntity?

    @Query("UPDATE notifications SET isDismissedByUser = 1, dismissedAt = :at WHERE id = :id AND isResolved = 0")
    suspend fun markDismissedByUser(id: String, at: Long)

    @Query("UPDATE notifications SET alertFiredAt = :firedAt WHERE id = :id")
    suspend fun updateAlertFiredAt(id: String, firedAt: Long)
}
