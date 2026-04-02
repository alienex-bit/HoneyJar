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
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.BackupManager
import com.honeyjar.app.utils.TimeUtils
import java.util.Calendar

data class AppGuiltEntry(
    val packageName: String,
    val label: String,
    val count7Days: Int,
    val streak: Int  // consecutive days ending today with ≥1 notification
)

data class AIChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val followUpChips: List<String> = emptyList()
)

class MainViewModel(
    private val application: android.app.Application,
    private val repository: PriorityRepository,
    private val statsDao: StatsDao,
    private val notificationDao: NotificationDao
) : ViewModel() {
    val secondaryAlertsEnabled: StateFlow<Boolean> = SettingsRepository.isSecondaryAlertsEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isSmartGrouping: StateFlow<Boolean> = SettingsRepository.isSmartGroupingEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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

    // This week vs previous week totals for trend display
    // weeklyCount derived from barChartData so it's always consistent with the bar chart
    val weeklyCount: StateFlow<Int> = barChartData.map { bars -> bars.sum() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val prevWeekCount: StateFlow<Int> = notificationsDebounced.map { all ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo     = todayStart - 7 * 86_400_000L
        val twoWeeksAgo = todayStart - 14 * 86_400_000L
        all.count { it.postTime >= twoWeeksAgo && it.postTime < weekAgo }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Category breakdown: count per category key
    val categoryBreakdown: StateFlow<Map<String, Int>> = notificationsDebounced.map { all ->
        all.groupBy { it.priority.lowercase() }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Heatmap: [dayOfWeek 0=Mon][hourBucket 0-7] counts (3-hour buckets)
    val heatmapData: StateFlow<Array<IntArray>> = notificationsDebounced.map { all ->
        val data = Array(7) { IntArray(8) { 0 } }
        val cal = Calendar.getInstance()
        all.forEach { n ->
            cal.timeInMillis = n.postTime
            val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val bucket = cal.get(Calendar.HOUR_OF_DAY) / 3
            data[day][bucket]++
        }
        data
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Array(7) { IntArray(8) { 0 } })

    // App Guilt Score: top 10 noisiest apps over the last 7 days with consecutive-day streaks
    val appBreakdown: StateFlow<List<AppGuiltEntry>> = notificationsDebounced.map { all ->
        val dayMs = 86_400_000L
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo = todayStart - 6 * dayMs
        val recent = all.filter { it.postTime >= weekAgo }
        recent.groupBy { it.packageName }
            .map { (pkg, notifs) ->
                val count = notifs.size
                var streak = 0
                for (daysAgo in 0..365) {
                    val dayStart = todayStart - daysAgo * dayMs
                    if (all.any { it.packageName == pkg && it.postTime >= dayStart && it.postTime < dayStart + dayMs }) {
                        streak++
                    } else break
                }
                AppGuiltEntry(
                    packageName = pkg,
                    label = AppLabelCache.get(pkg, application),
                    count7Days = count,
                    streak = streak
                )
            }
            .sortedByDescending { it.count7Days }
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Response time stat (O(N) moved out of UI)
    val avgResponseMinutes: StateFlow<Long?> = notificationsDebounced.map { all ->
        all.filter { it.isResolved && it.resolvedAt > 0 && it.resolvedAt > it.postTime }
            .takeIf { it.isNotEmpty() }
            ?.map { (it.resolvedAt - it.postTime) / 60_000 }
            ?.average()
            ?.toLong()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _aiMessages = MutableStateFlow<List<AIChatMessage>>(listOf(
        AIChatMessage(
            text = "Hello! I'm your HoneyJar Assistant. How can I help you manage your focus today?", 
            isUser = false, 
            followUpChips = listOf("What did I miss?", "What's urgent?", "Summarise today")
        )
    ))
    val aiMessages: StateFlow<List<AIChatMessage>> = _aiMessages.asStateFlow()

    private val _isAIBusy = MutableStateFlow(false)
    val isAIBusy: StateFlow<Boolean> = _isAIBusy.asStateFlow()

    fun sendAIPrompt(prompt: String) {
        viewModelScope.launch {
            _aiMessages.value += AIChatMessage(text = prompt, isUser = true)
            _isAIBusy.value = true
            
            // Artificial delay to simulate "thinking"
            kotlinx.coroutines.delay(1000)
            
            // Gather real data context pulse! pulse! pulse! pulse (truncated)
            val allNotifs = notificationDao.getAllNotificationsOnce()
            val now = System.currentTimeMillis()
            val last4h = allNotifs.filter { it.postTime >= (now - 4 * 3600_000L) }
            val urgentCount = last4h.count { it.priority.lowercase() == "urgent" && !it.isResolved }
            val categories = last4h.groupBy { it.priority.lowercase() }.mapValues { it.value.size }
            val topCategory = categories.maxByOrNull { it.value }?.key ?: "none"
            
            val response = when {
                prompt.contains("missed", ignoreCase = true) || prompt.contains("summary", ignoreCase = true) -> {
                    val summary = if (last4h.isEmpty()) {
                        "Your notification honey jar is empty for the last 4 hours. It's been very quiet!"
                    } else {
                        "In the last 4 hours, you've received ${last4h.size} notifications. " +
                        (if (topCategory != "none") "Most are from '$topCategory' (${categories[topCategory]}). " else "") +
                        (if (urgentCount > 0) "Critically, $urgentCount 'Urgent' alerts need your attention." else "Nothing urgent was missed.")
                    }
                    AIChatMessage(text = summary, isUser = false, followUpChips = listOf("Show urgent", "Mute $topCategory", "Details"))
                }
                prompt.contains("urgent", ignoreCase = true) -> {
                    if (urgentCount > 0) {
                        AIChatMessage(text = "You have $urgentCount urgent alerts waiting. I recommend checking those first to clear your mind.", isUser = false, followUpChips = listOf("Show Urgent", "Clear All"))
                    } else {
                        AIChatMessage(text = "Great news: You have 0 urgent notifications right now. Everything else is low priority.", isUser = false, followUpChips = listOf("Summarise morning", "Focus advice"))
                    }
                }
                prompt.contains("distract", ignoreCase = true) || prompt.contains("interrupted", ignoreCase = true) -> {
                    val noisiest = last4h.groupBy { it.packageName }.maxByOrNull { it.value.size }
                    if (noisiest != null) {
                        val label = AppLabelCache.get(noisiest.key, application)
                        AIChatMessage(text = "$label has sent ${noisiest.value.size} pings in the last 4 hours. This is your most disruptive app recently.", isUser = false, followUpChips = listOf("Mute $label", "Ignore Category"))
                    } else {
                        AIChatMessage(text = "No apps have been particularly noisy lately. You're doing great at maintaining focus!", isUser = false, followUpChips = listOf("Show patterns", "Zen score"))
                    }
                }
                else -> AIChatMessage(
                    text = "I've analyzed your notification history. You've actioned ${allNotifs.count { it.isResolved }} notifications so far. Most of your time is spent in '${allNotifs.groupBy { it.priority }.maxByOrNull { it.value.size }?.key ?: "Unknown"}' types.",
                    isUser = false,
                    followUpChips = listOf("Summarise today", "Best focus advice")
                )
            }
            
            _aiMessages.value += response
            _isAIBusy.value = false
        }
    }

    fun updatePriorityColour(key: String, hexColor: String) {
        viewModelScope.launch {
            repository.updateColour(key, hexColor)
        }
    }
    
    fun unmuteCategory(key: String) {
        viewModelScope.launch { repository.unmuteCategory(key) }
    }

    fun muteCategory(key: String, durationMs: Long) {
        viewModelScope.launch { repository.muteCategory(key, durationMs) }
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

    fun updateSoundUri(key: String, uri: String) {
        viewModelScope.launch { repository.updateSoundUri(key, uri) }
    }

    fun updateVibrationPattern(key: String, pattern: String) {
        viewModelScope.launch { repository.updateVibrationPattern(key, pattern) }
    }

    fun setSecondaryAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setSecondaryAlertsEnabled(application, enabled) }
    }

    fun updateSecondaryAlertEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch { repository.updateSecondaryAlertEnabled(key, enabled) }
    }

    fun updateInitialAlertDelayMs(key: String, delayMs: Long) {
        viewModelScope.launch { repository.updateInitialAlertDelayMs(key, delayMs) }
    }

    fun updateSecondaryAlertDelayMs(key: String, delayMs: Long) {
        viewModelScope.launch { repository.updateSecondaryAlertDelayMs(key, delayMs) }
    }

    suspend fun buildBackupJson(context: Context): String =
        BackupManager.buildBackupJson(context, statsDao, repository.dao)

    suspend fun restoreBackupJson(context: Context, json: String) =
        BackupManager.restoreFromJson(context, json, notificationDao, statsDao, repository.dao)

    /** Re-runs the static categoriser over all historical notifications. Returns (total, updated). */
    suspend fun recategorizeAll(): Pair<Int, Int> = NotificationRepository.recategorizeAll()

    /** Resets all built-in category colours to their defaults. Returns count reset. */
    suspend fun resetCategoryColours(): Int = repository.resetCategoryColours()

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
