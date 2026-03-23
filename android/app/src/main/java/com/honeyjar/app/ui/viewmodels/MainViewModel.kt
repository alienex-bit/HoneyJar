package com.honeyjar.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.*
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.data.entities.NotificationStatsEntity
import com.honeyjar.app.repositories.PriorityRepository
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.utils.BackupManager
import com.honeyjar.app.utils.TimeUtils
import java.util.Calendar

class MainViewModel(
    private val application: android.app.Application,
    private val repository: PriorityRepository,
    private val statsDao: StatsDao,
    private val notificationDao: NotificationDao
) : ViewModel() {
    val autoBackupFrequency: StateFlow<String> = SettingsRepository.getAutoBackupFrequency(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "off")
    val notifications: StateFlow<List<HoneyNotification>> =
        NotificationRepository.notifications
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notificationsHome: StateFlow<List<HoneyNotification>> =
        NotificationRepository.notificationsHome
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPriorityGroups: StateFlow<List<PriorityGroupEntity>> =
        repository.allPriorityGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allStats: StateFlow<List<NotificationStatsEntity>> =
        statsDao.getAllStats().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val priorityColors: StateFlow<Map<String, String>> = allPriorityGroups.map { groups ->
        groups.filter { it.isEnabled }.associate { it.key to it.colour }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Derived from Home flow (Unread + 24h) for high-performance Home screen counters
    val activeCount = notificationsHome.map { it.count { n -> !n.isResolved } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val snoozedCount = notificationsHome.map { it.count { n -> !n.isResolved && n.snoozeUntil > System.currentTimeMillis() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Global stats derived with a debounce to prevent O(N) lag on every new notification
    private val notificationsDebounced = NotificationRepository.notifications
        .debounce(1000L) // Aggregate bursts (e.g. during a restore or flood)

    val totalCount = notificationsDebounced.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val actionedCount = notificationsDebounced.map { it.count { n -> n.isResolved } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Bar chart: count per day for the last 7 days
    val barChartData: StateFlow<List<Int>> = notificationsDebounced.map { all ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val dayMs = 86_400_000L
        (6 downTo 0).map { daysAgo ->
            val dayStart = todayStart - (daysAgo * dayMs)
            all.count { it.postTime >= dayStart && it.postTime < dayStart + dayMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, List(7) { 0 })

    // Category breakdown: count per category key
    val categoryBreakdown: StateFlow<Map<String, Int>> = notificationsDebounced.map { all ->
        all.groupBy { it.priority.lowercase() }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Heatmap: [dayOfWeek 0=Mon][hourBucket 0-11] counts
    val heatmapData: StateFlow<Array<IntArray>> = notificationsDebounced.map { all ->
        val data = Array(7) { IntArray(12) { 0 } }
        val cal = Calendar.getInstance()
        all.forEach { n ->
            cal.timeInMillis = n.postTime
            val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val bucket = cal.get(Calendar.HOUR_OF_DAY) / 2
            data[day][bucket]++
        }
        data
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Array(7) { IntArray(12) { 0 } })

    // Response time stat (O(N) moved out of UI)
    val avgResponseMinutes: StateFlow<Long?> = notificationsDebounced.map { all ->
        all.filter { it.isResolved && it.resolvedAt > 0 && it.resolvedAt > it.postTime }
            .takeIf { it.isNotEmpty() }
            ?.map { (it.resolvedAt - it.postTime) / 60_000 }
            ?.average()
            ?.toLong()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updatePriorityColour(key: String, hexColor: String) {
        viewModelScope.launch {
            repository.updateColour(key, hexColor)
        }
    }
    
    fun updatePriorityEnabled(key: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabled(key, isEnabled)
        }
    }

    fun deletePriorityGroup(key: String) {
        viewModelScope.launch {
            repository.deleteByKey(key)
        }
    }
    
    fun insertPriorityGroup(group: PriorityGroupEntity) {
        viewModelScope.launch {
            repository.insert(group)
        }
    }

    suspend fun buildBackupJson(context: Context): String =
        BackupManager.buildBackupJson(context, statsDao, repository.dao)

    suspend fun restoreBackupJson(context: Context, json: String) =
        BackupManager.restoreFromJson(context, json, notificationDao, statsDao, repository.dao)

}

class MainViewModelFactory(
    private val application: android.app.Application,
    private val repository: PriorityRepository,
    private val statsDao: StatsDao,
    private val notificationDao: NotificationDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, statsDao, notificationDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
