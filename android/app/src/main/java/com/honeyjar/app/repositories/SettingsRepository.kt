package com.honeyjar.app.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsRepository {
    private val SMART_GROUPING = booleanPreferencesKey("smart_grouping")
    private val PRIORITY_FILTERING = booleanPreferencesKey("priority_filtering")
    private val FOCUS_MODE = booleanPreferencesKey("focus_mode")
    private val CAPTURE_ONGOING = booleanPreferencesKey("capture_ongoing")
    private val PRO_ENABLED = booleanPreferencesKey("pro_enabled")
    private val HAS_COMPLETED_ONBOARDING_INTRO = booleanPreferencesKey("has_completed_onboarding_intro")
    private val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency") // "off", "daily", "weekly", "monthly"
    private val LAST_AUTO_BACKUP_TIME = longPreferencesKey("last_auto_backup_time")
    fun isSmartGroupingEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[SMART_GROUPING] ?: true }
    fun isPriorityFilteringEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[PRIORITY_FILTERING] ?: true }
    fun isFocusModeEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[FOCUS_MODE] ?: false }
    fun isCaptureOngoingEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[CAPTURE_ONGOING] ?: false }
    fun isProEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[PRO_ENABLED] ?: false }
    fun hasCompletedOnboardingIntro(context: Context): Flow<Boolean> = context.dataStore.data.map { it[HAS_COMPLETED_ONBOARDING_INTRO] ?: false }
    fun getAutoBackupFrequency(context: Context): Flow<String> = context.dataStore.data.map { it[AUTO_BACKUP_FREQUENCY] ?: "off" }
    fun getLastAutoBackupTime(context: Context): Flow<Long> = context.dataStore.data.map { it[LAST_AUTO_BACKUP_TIME] ?: 0L }

    suspend fun setSmartGrouping(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[SMART_GROUPING] = enabled }
    }

    suspend fun setPriorityFiltering(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[PRIORITY_FILTERING] = enabled }
    }

    suspend fun setFocusMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[FOCUS_MODE] = enabled }
    }

    suspend fun setCaptureOngoing(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[CAPTURE_ONGOING] = enabled }
    }

    suspend fun setProEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[PRO_ENABLED] = enabled }
    }

    suspend fun setHasCompletedOnboardingIntro(context: Context, completed: Boolean) {
        context.dataStore.edit { it[HAS_COMPLETED_ONBOARDING_INTRO] = completed }
    }

    suspend fun setAutoBackupFrequency(context: Context, frequency: String) {
        context.dataStore.edit { it[AUTO_BACKUP_FREQUENCY] = frequency }
    }

    suspend fun setLastAutoBackupTime(context: Context, time: Long) {
        context.dataStore.edit { it[LAST_AUTO_BACKUP_TIME] = time }
    }
}
