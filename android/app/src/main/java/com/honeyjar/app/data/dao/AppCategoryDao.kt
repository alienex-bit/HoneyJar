package com.honeyjar.app.data.dao

import androidx.room.*
import com.honeyjar.app.data.entities.AppCategoryEntity

@Dao
interface AppCategoryDao {

    @Query("SELECT * FROM app_category_cache WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AppCategoryEntity)

    /** Wipe stale Play Store entries older than [beforeMs] so they get re-fetched eventually. */
    @Query("DELETE FROM app_category_cache WHERE source = 'playstore' AND fetchedAt < :beforeMs")
    suspend fun evictStalePlayStore(beforeMs: Long)

    @Query("SELECT COUNT(*) FROM app_category_cache")
    suspend fun count(): Int
}
