package com.honeyjar.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "priority_groups")
data class PriorityGroupEntity(
    @PrimaryKey val key: String,
    val label: String,
    val colour: String,
    val isEnabled: Boolean = true,
    val position: Int,
    val soundUri: String = "off",         // "off" | "default" | "chime" | "alert" | custom URI
    val vibrationPattern: String = "off", // "off" | "short" | "double" | "long" | "urgent"
    val secondaryAlertEnabled: Boolean = true,
    val initialAlertDelayMs: Long = 300_000L,    // 5 min — delay before first alert after dismissal
    val secondaryAlertDelayMs: Long = 1_800_000L, // 30 min — repeat interval (0 = no repeat)
    val ignoreUntil: Long = 0L
)
