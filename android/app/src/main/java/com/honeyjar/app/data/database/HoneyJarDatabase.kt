package com.honeyjar.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.honeyjar.app.data.dao.PriorityGroupDao
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.data.entities.NotificationEntity
import com.honeyjar.app.data.entities.NotificationStatsEntity
import com.honeyjar.app.data.dao.AppCategoryDao
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.data.entities.AppCategoryEntity
import com.honeyjar.app.utils.NotificationCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PriorityGroupEntity::class, NotificationEntity::class, NotificationStatsEntity::class, AppCategoryEntity::class], version = 12, exportSchema = false)
abstract class HoneyJarDatabase : RoomDatabase() {
    abstract fun priorityGroupDao(): PriorityGroupDao
    abstract fun notificationDao(): NotificationDao
    abstract fun statsDao(): StatsDao
    abstract fun appCategoryDao(): AppCategoryDao

    companion object {
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN ignoreUntil INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure system group exists — was missing from migration 8→9
                db.execSQL("""
                    INSERT OR IGNORE INTO priority_groups
                    (key, label, colour, isEnabled, position, soundUri, vibrationPattern,
                     secondaryAlertEnabled, initialAlertDelayMs, secondaryAlertDelayMs)
                    VALUES ('system', 'System', '#94a3b8', 1, 13, 'off', 'off', 1, 300000, 1800000)
                """.trimIndent())
                // New device sub-categories
                val newGroups = listOf(
                    Triple("security",  "Security",  "#f97316"),  // orange
                    Triple("connected", "Connected", "#06b6d4"),  // cyan
                    Triple("updates",   "Updates",   "#22c55e"),  // green
                    Triple("photos",    "Photos",    "#f472b6"),  // pink
                )
                newGroups.forEachIndexed { i, (key, label, colour) ->
                    db.execSQL("""
                        INSERT OR IGNORE INTO priority_groups
                        (key, label, colour, isEnabled, position, soundUri, vibrationPattern,
                         secondaryAlertEnabled, initialAlertDelayMs, secondaryAlertDelayMs)
                        VALUES ('$key', '$label', '$colour', 1, ${14 + i},
                                'off', 'off', 1, 300000, 1800000)
                    """.trimIndent())
                }
                // Retire the old catch-all device group
                db.execSQL("UPDATE priority_groups SET isEnabled = 0 WHERE key = 'device'")
            }
        }

        @Volatile
        private var INSTANCE: HoneyJarDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN systemActionsJson TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateStart INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        hourBucket INTEGER NOT NULL,
                        count INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove any duplicate rows introduced before the unique index existed,
                // keeping only the row with the highest count for each (dateStart, category, hourBucket) tuple.
                db.execSQL("""
                    DELETE FROM notification_stats
                    WHERE id NOT IN (
                        SELECT id FROM notification_stats
                        GROUP BY dateStart, category, hourBucket
                        HAVING id = MAX(id)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_notification_stats_dateStart_category_hourBucket
                    ON notification_stats (dateStart, category, hourBucket)
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN soundUri TEXT NOT NULL DEFAULT 'off'")
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN vibrationPattern TEXT NOT NULL DEFAULT 'off'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN isDismissedByUser INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN dismissedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN alertFiredAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN secondaryAlertEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN initialAlertDelayMs INTEGER NOT NULL DEFAULT 300000")
                db.execSQL("ALTER TABLE priority_groups ADD COLUMN secondaryAlertDelayMs INTEGER NOT NULL DEFAULT 1800000")
            }
        }

        // Migration 8→9: expand categories from 7 to 13.
        // Inserts new groups only if not already present (safe to re-run).
        // Retired: updates (#4), delivery (#5) — remapped into device/shopping/travel.
        // New: social, calls, weather, travel, finance, shopping, media, device.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val newGroups = listOf(
                    Triple("social",   "Social",   "#ec4899"),  // pink
                    Triple("calls",    "Calls",    "#10b981"),  // emerald
                    Triple("weather",  "Weather",  "#38bdf8"),  // sky blue
                    Triple("travel",   "Travel",   "#f97316"),  // orange
                    Triple("finance",  "Finance",  "#84cc16"),  // lime
                    Triple("shopping", "Shopping", "#f43f5e"),  // rose
                    Triple("media",    "Media",    "#8b5cf6"),  // violet
                    Triple("device",   "Device",   "#64748b"),  // slate
                )
                // Retire old categories by disabling them so existing data isn't orphaned
                db.execSQL("UPDATE priority_groups SET isEnabled = 0 WHERE key IN ('updates', 'delivery')")

                newGroups.forEachIndexed { i, (key, label, colour) ->
                    db.execSQL("""
                        INSERT OR IGNORE INTO priority_groups
                        (key, label, colour, isEnabled, position, soundUri, vibrationPattern,
                         secondaryAlertEnabled, initialAlertDelayMs, secondaryAlertDelayMs)
                        VALUES ('$key', '$label', '$colour', 1, ${10 + i},
                                'off', 'off', 1, 300000, 1800000)
                    """.trimIndent())
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_category_cache (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        category TEXT NOT NULL,
                        source TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): HoneyJarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HoneyJarDatabase::class.java,
                    "honeyjar_database"
                )
                .addCallback(DatabaseCallback(context))
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.priorityGroupDao())
                    }
                }
            }

            suspend fun populateDatabase(dao: PriorityGroupDao) {
                val defaults = listOf(
                    PriorityGroupEntity(NotificationCategories.URGENT,    "Urgent",    "#ef4444", true,  0),
                    PriorityGroupEntity(NotificationCategories.MESSAGES,  "Messages",  "#3b82f6", true,  1),
                    PriorityGroupEntity(NotificationCategories.SOCIAL,    "Social",    "#ec4899", true,  2),
                    PriorityGroupEntity(NotificationCategories.EMAIL,     "Email",     "#a855f7", true,  3),
                    PriorityGroupEntity(NotificationCategories.CALENDAR,  "Calendar",  "#f59e0b", true,  4),
                    PriorityGroupEntity(NotificationCategories.CALLS,     "Calls",     "#10b981", true,  5),
                    PriorityGroupEntity(NotificationCategories.WEATHER,   "Weather",   "#38bdf8", true,  6),
                    PriorityGroupEntity(NotificationCategories.TRAVEL,    "Travel",    "#f97316", true,  7),
                    PriorityGroupEntity(NotificationCategories.FINANCE,   "Finance",   "#84cc16", true,  8),
                    PriorityGroupEntity(NotificationCategories.SHOPPING,  "Shopping",  "#f43f5e", true,  9),
                    PriorityGroupEntity(NotificationCategories.MEDIA,     "Media",     "#8b5cf6", true, 10),
                    PriorityGroupEntity(NotificationCategories.SECURITY,  "Security",  "#f97316", true, 11),
                    PriorityGroupEntity(NotificationCategories.CONNECTED, "Connected", "#06b6d4", true, 12),
                    PriorityGroupEntity(NotificationCategories.UPDATES,   "Updates",   "#22c55e", true, 13),
                    PriorityGroupEntity(NotificationCategories.PHOTOS,    "Photos",    "#f472b6", true, 14),
                    PriorityGroupEntity(NotificationCategories.SYSTEM,    "System",    "#94a3b8", true, 15),
                )
                defaults.forEach { dao.insertPriorityGroup(it) }
            }
        }
    }
}
