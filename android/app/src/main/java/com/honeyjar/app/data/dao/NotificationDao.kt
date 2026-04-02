package com.honeyjar.app.data.dao

import androidx.room.*
import com.honeyjar.app.data.entities.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    abstract suspend fun getAllNotificationsOnce(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE postTime >= :timestamp ORDER BY postTime DESC")
    abstract fun getNotificationsSince(timestamp: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE postTime >= :timestamp ORDER BY postTime DESC")
    abstract fun getHomeNotifications(timestamp: Long): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isResolved = :isResolved, resolvedAt = :resolvedAtMs WHERE id = :id")
    abstract suspend fun updateResolvedStatus(id: String, isResolved: Boolean, resolvedAtMs: Long)

    /** Non-suspend version for use inside Transaction blocks. */
    @Query("UPDATE notifications SET isResolved = :isResolved, resolvedAt = :resolvedAtMs WHERE id = :id")
    abstract fun updateResolvedStatusSync(id: String, isResolved: Boolean, resolvedAtMs: Long)

    @Query("UPDATE notifications SET snoozeUntil = :snoozeUntil WHERE id = :id")
    abstract suspend fun snoozeNotification(id: String, snoozeUntil: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    abstract suspend fun deleteNotificationById(id: String)

    @Query("DELETE FROM notifications")
    abstract suspend fun deleteAllNotifications()


    @Query("UPDATE notifications SET priority = :priority WHERE id = :id")
    abstract suspend fun updatePriority(id: String, priority: String)

    /** Non-suspend version for use inside Transaction blocks. */
    @Query("UPDATE notifications SET priority = :priority WHERE id = :id")
    abstract fun updatePrioritySync(id: String, priority: String)

    @Transaction
    open fun runInTransaction(block: () -> Unit) {
        block()
    }

    @Query("UPDATE notifications SET isResolved = 1, resolvedAt = :resolvedAtMs WHERE isResolved = 0")
    abstract suspend fun resolveAllNotifications(resolvedAtMs: Long)

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): NotificationEntity?

    @Query("UPDATE notifications SET isDismissedByUser = 1, dismissedAt = :at WHERE id = :id AND isResolved = 0")
    abstract suspend fun markDismissedByUser(id: String, at: Long)

    @Query("UPDATE notifications SET alertFiredAt = :firedAt WHERE id = :id")
    abstract suspend fun updateAlertFiredAt(id: String, firedAt: Long)
}
