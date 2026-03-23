package com.honeyjar.app.repositories

import com.honeyjar.app.data.dao.PriorityGroupDao
import com.honeyjar.app.data.entities.PriorityGroupEntity
import kotlinx.coroutines.flow.Flow

class PriorityRepository(val dao: PriorityGroupDao) {
    val allPriorityGroups: Flow<List<PriorityGroupEntity>> = dao.getAllPriorityGroups()

    suspend fun insert(group: PriorityGroupEntity) {
        dao.insertPriorityGroup(group)
    }

    suspend fun update(group: PriorityGroupEntity) {
        dao.updatePriorityGroup(group)
    }

    suspend fun delete(group: PriorityGroupEntity) {
        dao.deletePriorityGroup(group)
    }

    suspend fun updateColour(key: String, colour: String) {
        dao.updateColour(key, colour)
    }

    suspend fun updateEnabled(key: String, isEnabled: Boolean) {
        dao.updateEnabled(key, isEnabled)
    }

    suspend fun deleteByKey(key: String) {
        dao.deleteByKey(key)
    }

    suspend fun updateSoundUri(key: String, uri: String) {
        dao.updateSoundUri(key, uri)
    }

    suspend fun updateVibrationPattern(key: String, pattern: String) {
        dao.updateVibrationPattern(key, pattern)
    }
}
