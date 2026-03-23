package com.honeyjar.app.data.dao

import androidx.room.*
import com.honeyjar.app.data.entities.PriorityGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriorityGroupDao {
    @Query("SELECT * FROM priority_groups ORDER BY position ASC")
    fun getAllPriorityGroups(): Flow<List<PriorityGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriorityGroup(group: PriorityGroupEntity)

    @Update
    suspend fun updatePriorityGroup(group: PriorityGroupEntity)

    @Delete
    suspend fun deletePriorityGroup(group: PriorityGroupEntity)
    
    @Query("UPDATE priority_groups SET colour = :colour WHERE `key` = :key")
    suspend fun updateColour(key: String, colour: String)
    
    @Query("UPDATE priority_groups SET isEnabled = :isEnabled WHERE `key` = :key")
    suspend fun updateEnabled(key: String, isEnabled: Boolean)

    @Query("DELETE FROM priority_groups WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
