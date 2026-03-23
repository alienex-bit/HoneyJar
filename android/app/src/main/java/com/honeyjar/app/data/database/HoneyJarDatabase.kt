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
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.utils.NotificationCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PriorityGroupEntity::class, NotificationEntity::class, NotificationStatsEntity::class], version = 6, exportSchema = false)
abstract class HoneyJarDatabase : RoomDatabase() {
    abstract fun priorityGroupDao(): PriorityGroupDao
    abstract fun notificationDao(): NotificationDao
    abstract fun statsDao(): StatsDao

    companion object {
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

        @Volatile
        private var INSTANCE: HoneyJarDatabase? = null

        fun getDatabase(context: Context): HoneyJarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HoneyJarDatabase::class.java,
                    "honeyjar_database"
                )
                .addCallback(DatabaseCallback(context))
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
                    PriorityGroupEntity(NotificationCategories.URGENT, "Urgent", "#ef4444", true, 0),
                    PriorityGroupEntity(NotificationCategories.MESSAGES, "Messages", "#3b82f6", true, 1),
                    PriorityGroupEntity(NotificationCategories.CALENDAR, "Calendar", "#f59e0b", true, 2),
                    PriorityGroupEntity(NotificationCategories.EMAIL, "Email", "#a855f7", true, 3),
                    PriorityGroupEntity(NotificationCategories.UPDATES, "Updates", "#22c55e", true, 4),
                    PriorityGroupEntity(NotificationCategories.DELIVERY, "Delivery", "#fbbf24", true, 5),
                    PriorityGroupEntity(NotificationCategories.SYSTEM, "System", "#94a3b8", true, 6)
                )
                defaults.forEach { dao.insertPriorityGroup(it) }
            }
        }
    }
}
