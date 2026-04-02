package com.honeyjar.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.honeyjar.app.data.entities.NotificationStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Query("SELECT * FROM notification_stats ORDER BY dateStart DESC LIMIT 500")
    fun getAllStats(): Flow<List<NotificationStatsEntity>>

    @Query("""
        INSERT INTO notification_stats (dateStart, category, hourBucket, count)
        VALUES (:dateStart, :category, :hourBucket, 1)
        ON CONFLICT(dateStart, category, hourBucket)
        DO UPDATE SET count = count + 1
    """)
    suspend fun upsertStat(dateStart: Long, category: String, hourBucket: Int)

    @Query("DELETE FROM notification_stats")
    suspend fun deleteAllStats()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(stat: NotificationStatsEntity)
}
