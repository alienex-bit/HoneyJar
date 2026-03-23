package com.honeyjar.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_stats",
    indices = [Index(value = ["dateStart", "category", "hourBucket"], unique = true)]
)
data class NotificationStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateStart: Long, // Start of the day in local time (Calendar.getInstance())
    val category: String,
    val hourBucket: Int, // 0-11 (2-hour buckets for heatmap)
    val count: Int = 1
)
