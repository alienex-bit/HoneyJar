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
import com.honeyjar.app.utils.GeminiApiService
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.BackupManager
import com.honeyjar.app.utils.TimeUtils
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppGuiltEntry(
    val packageName: String,
    val label: String,
    val count7Days: Int,
    val streak: Int
)

/**
 * A single message in the AI chat, plus optional follow-up action chips.
 *
 * [pendingAction] is a machine-readable instruction that the AI screen can
 * execute on the user's behalf — e.g. snoozing a category or resolving all.
 * Format: "ACTION:<verb>:<arg>" — kept intentionally simple, no reflection.
 */
data class AIChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val followUpChips: List<String> = emptyList(),
    val pendingAction: String? = null   // e.g. "ACTION:MUTE_CATEGORY:social"
)

/** Possible states for the AI feature availability banner. */
enum class AiKeyState { UNKNOWN, MISSING, PRESENT }

class MainViewModel(
    private val application: android.app.Application,
    private val repository: PriorityRepository,
    private val statsDao: StatsDao,
    private val notificationDao: NotificationDao
) : ViewModel() {

    // ── Settings flows ────────────────────────────────────────────────────────

    val secondaryAlertsEnabled: StateFlow<Boolean> = SettingsRepository.isSecondaryAlertsEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isSmartGrouping: StateFlow<Boolean> = SettingsRepository.isSmartGroupingEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoBackupFrequency: StateFlow<String> = SettingsRepository.getAutoBackupFrequency(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "off")

    /** Exposed so the AI screen can show a "set API key" banner when empty. */
    val geminiApiKey: StateFlow<String> = SettingsRepository.getGeminiApiKey(application)
        .map { it.ifEmpty { com.honeyjar.app.BuildConfig.GEMINI_API_KEY } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aiKeyState: StateFlow<AiKeyState> = geminiApiKey.map { key ->
        when {
            key.isBlank() -> AiKeyState.MISSING
            else          -> AiKeyState.PRESENT
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiKeyState.UNKNOWN)

    // ── Notification flows ────────────────────────────────────────────────────

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

    // Ticker for time-sensitive snooze counts
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val activeCount: StateFlow<Int> = combine(notificationsHome, tickerFlow) { list, now ->
        list.count { n -> !n.isResolved && n.snoozeUntil <= now }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val snoozedCount: StateFlow<Int> = combine(notificationsHome, tickerFlow) { list, now ->
        list.count { n -> !n.isResolved && n.snoozeUntil > now }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val notificationsDebounced = NotificationRepository.notifications
        .debounce(1000L)

    val totalCount = notificationsDebounced.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val actionedCount = notificationsDebounced.map { it.count { n -> n.isResolved } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val barChartData: StateFlow<List<Int>> = notificationsDebounced.map { all ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val dayMs = 86_400_000L
        (6 downTo 0).map { daysAgo ->
            val dayStart = todayStart - (daysAgo * dayMs)
            all.count { it.postTime >= dayStart && it.postTime < dayStart + dayMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, List(7) { 0 })

    val weeklyCount: StateFlow<Int> = barChartData.map { bars -> bars.sum() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val prevWeekCount: StateFlow<Int> = notificationsDebounced.map { all ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo     = todayStart - 7 * 86_400_000L
        val twoWeeksAgo = todayStart - 14 * 86_400_000L
        all.count { it.postTime >= twoWeeksAgo && it.postTime < weekAgo }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val categoryBreakdown: StateFlow<Map<String, Int>> = notificationsDebounced.map { all ->
        all.groupBy { it.priority.lowercase() }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    val appBreakdown: StateFlow<List<AppGuiltEntry>> = notificationsDebounced.map { all ->
        val dayMs = 86_400_000L
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo = todayStart - 6 * dayMs
        val historySet = all.map { it.packageName to TimeUtils.getDayStart(it.postTime) }.toSet()
        val recent = all.filter { it.postTime >= weekAgo }
        recent.groupBy { it.packageName }
            .map { (pkg, notifs) ->
                var streak = 0
                for (daysAgo in 0..365) {
                    val dayStart = todayStart - daysAgo * dayMs
                    if (historySet.contains(pkg to dayStart)) streak++ else break
                }
                AppGuiltEntry(
                    packageName = pkg,
                    label = AppLabelCache.get(pkg, application),
                    count7Days = notifs.size,
                    streak = streak
                )
            }
            .sortedByDescending { it.count7Days }
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val avgResponseMinutes: StateFlow<Long?> = notificationsDebounced.map { all ->
        all.filter { it.isResolved && it.resolvedAt > 0 && it.resolvedAt > it.postTime }
            .takeIf { it.isNotEmpty() }
            ?.map { (it.resolvedAt - it.postTime) / 60_000 }
            ?.average()
            ?.toLong()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── AI Chat state ─────────────────────────────────────────────────────────

    private val _aiMessages = MutableStateFlow<List<AIChatMessage>>(listOf(
        AIChatMessage(
            text = "Hello! I'm your HoneyJar AI. I have full access to your notification history and can help you manage focus, spot patterns, or take actions on your behalf.",
            isUser = false,
            followUpChips = listOf("What did I miss?", "What's urgent?", "Summarise today", "Which app distracts me most?")
        )
    ))
    val aiMessages: StateFlow<List<AIChatMessage>> = _aiMessages.asStateFlow()

    private val _isAIBusy = MutableStateFlow(false)
    val isAIBusy: StateFlow<Boolean> = _isAIBusy.asStateFlow()

    /**
     * Conversation history sent to the API each turn (user + assistant alternating).
     * Does NOT include the initial greeting (that's UI-only).
     */
    private val apiHistory = mutableListOf<GeminiApiService.Message>()

    // ── AI: send prompt ───────────────────────────────────────────────────────

    fun sendAIPrompt(prompt: String) {
        viewModelScope.launch {
            val apiKey = geminiApiKey.value
            if (apiKey.isBlank()) {
                _aiMessages.value += AIChatMessage(
                    text = "No API key set. Please add your Google Gemini API key in Settings → AI Assistant.",
                    isUser = false
                )
                return@launch
            }

            _aiMessages.value += AIChatMessage(text = prompt, isUser = true)
            _isAIBusy.value = true

            try {
                val systemPrompt = buildSystemPrompt()
                apiHistory.add(GeminiApiService.Message("user", prompt))

                val result = GeminiApiService.sendMessage(apiKey, systemPrompt, apiHistory.toList())

                result.onSuccess { reply ->
                    apiHistory.add(GeminiApiService.Message("model", reply))

                    // Parse any action tags the model may have returned
                    val (cleanText, action) = extractAction(reply)
                    val chips = suggestFollowUpChips(reply, prompt)

                    _aiMessages.value += AIChatMessage(
                        text = cleanText,
                        isUser = false,
                        followUpChips = chips,
                        pendingAction = action
                    )

                    // Auto-execute safe read-only actions; prompt user for destructive ones
                    action?.let { executeActionIfSafe(it) }

                }.onFailure { error ->
                    // Don't add the failed user turn to history
                    apiHistory.removeLastOrNull()
                    _aiMessages.value += AIChatMessage(
                        text = "Sorry, I couldn't reach the AI right now. ${friendlyError(error.message)}",
                        isUser = false,
                        followUpChips = listOf("Try again")
                    )
                }

            } finally {
                _isAIBusy.value = false
            }
        }
    }

    // ── AI: clear conversation ────────────────────────────────────────────────

    fun clearAiConversation() {
        apiHistory.clear()
        _aiMessages.value = listOf(
            AIChatMessage(
                text = "Conversation cleared. How can I help?",
                isUser = false,
                followUpChips = listOf("What did I miss?", "Today's summary", "Distraction report")
            )
        )
    }

    // ── AI: execute action chips ──────────────────────────────────────────────

    /**
     * Called when the user taps a [pendingAction] chip in the UI.
     * Destructive actions (resolve all, mute category) are only executed
     * when explicitly confirmed via a chip tap — never automatically.
     */
    fun executeAction(action: String) {
        viewModelScope.launch {
            when {
                action.startsWith("ACTION:MUTE_CATEGORY:") -> {
                    val category = action.removePrefix("ACTION:MUTE_CATEGORY:")
                    muteCategory(category, 60 * 60 * 1000L) // 1 hour
                    _aiMessages.value += AIChatMessage(
                        text = "Done — $category notifications muted for 1 hour.",
                        isUser = false
                    )
                }
                action.startsWith("ACTION:RESOLVE_ALL:") -> {
                    val category = action.removePrefix("ACTION:RESOLVE_ALL:")
                    val all = notificationDao.getAllNotificationsOnce()
                    val toResolve = if (category == "*") all else all.filter { it.priority == category }
                    toResolve.filter { !it.isResolved }.forEach {
                        NotificationRepository.resolveNotification(it.id)
                    }
                    _aiMessages.value += AIChatMessage(
                        text = "Marked ${toResolve.size} notifications as resolved.",
                        isUser = false
                    )
                }
                action.startsWith("ACTION:SNOOZE_CATEGORY:") -> {
                    val parts = action.removePrefix("ACTION:SNOOZE_CATEGORY:").split(":")
                    val category = parts.getOrNull(0) ?: return@launch
                    val durationMs = parts.getOrNull(1)?.toLongOrNull() ?: 3_600_000L
                    val all = notificationDao.getAllNotificationsOnce()
                    all.filter { it.priority == category && !it.isResolved }.forEach {
                        NotificationRepository.snoozeNotification(it.id, durationMs)
                    }
                    val mins = durationMs / 60_000
                    _aiMessages.value += AIChatMessage(
                        text = "Snoozed all $category notifications for $mins minutes.",
                        isUser = false
                    )
                }
                else -> android.util.Log.w("HoneyJar-AI", "Unknown action: $action")
            }
        }
    }

    /** Safe read-only actions execute immediately without waiting for chip tap. */
    private fun executeActionIfSafe(action: String) {
        // Currently no auto-executed actions — all require explicit user confirmation via chip.
        // This hook exists for future read-only actions (e.g. navigate to History filtered view).
    }

    // ── AI: system prompt builder ─────────────────────────────────────────────

    /**
     * Builds a rich system prompt injecting the user's real notification data.
     * Sent fresh on every turn so the model always has up-to-date counts.
     */
    private suspend fun buildSystemPrompt(): String {
        val now = System.currentTimeMillis()
        val df = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
        val all = notificationDao.getAllNotificationsOnce()

        val todayStart = TimeUtils.getDayStart(now)
        val today = all.filter { it.postTime >= todayStart }
        val last4h = all.filter { it.postTime >= now - 4 * 3_600_000L }
        val unresolved = today.filter { !it.isResolved && it.snoozeUntil <= now }
        val snoozed = today.filter { !it.isResolved && it.snoozeUntil > now }

        val categoryBreakdown = today.groupBy { it.priority }
            .entries.sortedByDescending { it.value.size }
            .joinToString("\n") { (cat, items) ->
                val unresolvedInCat = items.count { !it.isResolved }
                "  - ${cat.replaceFirstChar { it.uppercase() }}: ${items.size} total, $unresolvedInCat unresolved"
            }

        val noisiest7Days = appBreakdown.value.take(3)
            .joinToString(", ") { "${it.label} (${it.count7Days})" }

        val urgentItems = unresolved.filter { it.priority == "urgent" }
            .take(5)
            .joinToString("\n") { "  • ${it.title} — ${it.text.take(60)}" }
            .ifEmpty { "  None" }

        val recentItems = last4h.take(8)
            .joinToString("\n") { n ->
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.postTime))
                val status = if (n.isResolved) "✓" else "●"
                "  $status [$time] ${n.priority.uppercase()}: ${n.title} — ${n.text.take(50)}"
            }
            .ifEmpty { "  None" }

        val avgResponse = avgResponseMinutes.value
        val responseStr = if (avgResponse != null) "${avgResponse}m avg response time" else "no response data yet"

        val categories = allPriorityGroups.value
            .filter { it.isEnabled }
            .joinToString(", ") { it.key }

        return """
You are HoneyJar AI, a smart, concise notification management assistant built into the HoneyJar app.
Current time: ${df.format(Date(now))}

## User's notification data
- Today total: ${today.size} notifications (${unresolved.size} unresolved, ${snoozed.size} snoozed)
- Last 4 hours: ${last4h.size} notifications
- This week: ${weeklyCount.value} (vs ${prevWeekCount.value} last week)
- $responseStr

## Today by category:
$categoryBreakdown

## Urgent unresolved:
$urgentItems

## Recent activity (last 4h):
$recentItems

## Noisiest apps this week: $noisiest7Days
## Active categories: $categories

## Your capabilities
You can perform actions by including ONE of these tags at the END of your reply, only when appropriate:
- [ACTION:MUTE_CATEGORY:<key>] — mutes a category for 1 hour
- [ACTION:RESOLVE_ALL:<key>] — marks all notifications in a category as resolved (* for all)
- [ACTION:SNOOZE_CATEGORY:<key>:<durationMs>] — snoozes a category (e.g. 3600000 = 1h)

## Instructions
- Be direct and concise — 2–4 sentences max unless asked for detail.
- Use specific numbers from the data above, not vague language.
- Suggest 2–3 relevant follow-up questions at the end of your reply as a plain bulleted list prefixed with "Suggestions:".
- Only suggest an action tag when the user has clearly requested it.
- Never make up notification data — use only what's provided above.
- If the data shows nothing interesting, say so honestly.
        """.trimIndent()
    }

    // ── AI: helpers ───────────────────────────────────────────────────────────

    /**
     * Strips any ACTION tag from the model's reply and returns it separately.
     * This keeps the displayed text clean while preserving the machine instruction.
     */
    private fun extractAction(reply: String): Pair<String, String?> {
        val actionRegex = Regex("""\[ACTION:[^\]]+\]""")
        val match = actionRegex.find(reply)
        val action = match?.value?.removeSurrounding("[", "]")
        val cleanText = reply.replace(actionRegex, "").trim()
        return cleanText to action
    }

    /**
     * Parses the "Suggestions:" section the model appends and turns them into chips.
     * Falls back to sensible defaults if the model doesn't include one.
     */
    private fun suggestFollowUpChips(reply: String, prompt: String): List<String> {
        val suggestionsBlock = reply.lines()
            .dropWhile { !it.startsWith("Suggestions:") }
            .drop(1)
            .takeWhile { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") }
            .map { it.trimStart('-', '•', '*', ' ') }
            .filter { it.isNotBlank() }
            .take(3)

        if (suggestionsBlock.isNotEmpty()) return suggestionsBlock

        // Fallback chips based on content keywords
        return when {
            "urgent" in reply.lowercase() -> listOf("Show urgent", "Resolve all urgent", "What else?")
            "mute" in reply.lowercase() || "distract" in reply.lowercase() -> listOf("Mute for 1 hour", "Show distractions", "Focus tips")
            "summary" in prompt.lowercase() || "today" in prompt.lowercase() -> listOf("What's urgent?", "Which app is noisiest?", "My response time")
            else -> listOf("Summarise today", "What's urgent?", "Distraction report")
        }
    }

    private fun friendlyError(message: String?): String = when {
        message == null -> "Please check your connection."
        "401" in message || "authentication" in message.lowercase() -> "Check your API key in Settings."
        "429" in message -> "Rate limit reached — try again in a moment."
        "timeout" in message.lowercase() -> "Request timed out — check your connection."
        else -> message.take(100) // Return the first 100 chars of the actual error
    }

    // ── API key management ────────────────────────────────────────────────────

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { SettingsRepository.setGeminiApiKey(application, key) }
    }

    // ── Category / priority group actions ────────────────────────────────────

    fun updatePriorityColour(key: String, hexColor: String) {
        viewModelScope.launch { repository.updateColour(key, hexColor) }
    }

    fun unmuteCategory(key: String) {
        viewModelScope.launch { repository.unmuteCategory(key) }
    }

    fun muteCategory(key: String, durationMs: Long) {
        viewModelScope.launch { repository.muteCategory(key, durationMs) }
    }

    fun updatePriorityEnabled(key: String, isEnabled: Boolean) {
        viewModelScope.launch { repository.updateEnabled(key, isEnabled) }
    }

    fun deletePriorityGroup(key: String) {
        viewModelScope.launch { repository.deleteByKey(key) }
    }

    fun insertPriorityGroup(group: PriorityGroupEntity) {
        viewModelScope.launch { repository.insert(group) }
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

    // ── Backup ────────────────────────────────────────────────────────────────

    suspend fun buildBackupJson(context: Context): String =
        BackupManager.buildBackupJson(context, statsDao, repository.dao)

    suspend fun restoreBackupJson(context: Context, json: String) =
        BackupManager.restoreFromJson(context, json, notificationDao, statsDao, repository.dao)

    // ── Maintenance ───────────────────────────────────────────────────────────

    suspend fun recategorizeAll(): Pair<Int, Int> = NotificationRepository.recategorizeAll()

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
