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

    suspend fun updateSecondaryAlertEnabled(key: String, enabled: Boolean) {
        dao.updateSecondaryAlertEnabled(key, enabled)
    }

    suspend fun updateInitialAlertDelayMs(key: String, delayMs: Long) {
        dao.updateInitialAlertDelayMs(key, delayMs)
    }

    suspend fun updateSecondaryAlertDelayMs(key: String, delayMs: Long) {
        dao.updateSecondaryAlertDelayMs(key, delayMs)
    }

    /** Resets all built-in category colours back to their intended defaults. */
    suspend fun resetCategoryColours(): Int {
        val defaults = mapOf(
            "urgent"    to "#ef4444",
            "messages"  to "#3b82f6",
            "social"    to "#ec4899",
            "email"     to "#a855f7",
            "calendar"  to "#f59e0b",
            "calls"     to "#10b981",
            "weather"   to "#38bdf8",
            "travel"    to "#f97316",
            "finance"   to "#84cc16",
            "shopping"  to "#f43f5e",
            "media"     to "#8b5cf6",
            "security"  to "#f97316",
            "connected" to "#06b6d4",
            "updates"   to "#22c55e",
            "photos"    to "#f472b6",
            "system"    to "#94a3b8"
        )
        defaults.forEach { (key, colour) -> dao.updateColour(key, colour) }
        return defaults.size
    }

    suspend fun muteCategory(key: String, durationMs: Long) {
        val until = System.currentTimeMillis() + durationMs
        dao.updateIgnoreUntil(key, until)
    }

    suspend fun unmuteCategory(key: String) {
        dao.updateIgnoreUntil(key, 0L)
    }
}
