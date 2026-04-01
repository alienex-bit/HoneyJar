package com.honeyjar.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent cache of package → HoneyJar category mappings resolved at runtime.
 *
 * source:
 *   "device"    – derived from ApplicationInfo.category (PackageManager, on-device, instant)
 *   "playstore" – scraped from Play Store HTML (network, one-time per unknown package)
 *   "manual"    – set by the static lookup table in NotificationService (never expires)
 */
@Entity(tableName = "app_category_cache")
data class AppCategoryEntity(
    @PrimaryKey val packageName: String,
    val category: String,          // one of NotificationCategories.*
    val source: String,            // "device" | "playstore" | "manual"
    val fetchedAt: Long = System.currentTimeMillis()
)
