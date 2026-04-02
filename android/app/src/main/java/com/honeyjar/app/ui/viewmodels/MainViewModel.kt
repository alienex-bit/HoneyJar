package com.honeyjar.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.*
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.data.entities.NotificationStatsEntity
import com.honeyjar.app.repositories.PriorityRepository
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.repositories.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.BackupManager
import com.honeyjar.app.utils.TimeUtils
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

data class AppGuiltEntry(
    val packageName: String,
    val label: String,
    val count7Days: Int,
    val streak: Int
)

/**
 * Message type drives the visual treatment in the UI:
 * NORMAL  = standard bubble
 * INSIGHT = amber-tinted stat card (interesting data)
 * ACTION  = green confirmation (something was done)
 * WARNING = red-tinted (needs attention)
 */
enum class MessageType { NORMAL, INSIGHT, ACTION, WARNING }

data class AIChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val followUpChips: List<String> = emptyList(),
    val pendingAction: String? = null,
    val type: MessageType = MessageType.NORMAL
)

class MainViewModel(
    private val application: android.app.Application,
    private val repository: PriorityRepository,
    private val statsDao: StatsDao,
    private val notificationDao: NotificationDao
) : ViewModel() {

    // ── Settings ──────────────────────────────────────────────────────────────

    val secondaryAlertsEnabled: StateFlow<Boolean> = SettingsRepository.isSecondaryAlertsEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isSmartGrouping: StateFlow<Boolean> = SettingsRepository.isSmartGroupingEnabled(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoBackupFrequency: StateFlow<String> = SettingsRepository.getAutoBackupFrequency(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "off")

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

    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val activeCount: StateFlow<Int> = combine(notificationsHome, tickerFlow) { list, now ->
        list.count { n -> !n.isResolved && n.snoozeUntil <= now }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val snoozedCount: StateFlow<Int> = combine(notificationsHome, tickerFlow) { list, now ->
        list.count { n -> !n.isResolved && n.snoozeUntil > now }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val notificationsDebounced = NotificationRepository.notifications.debounce(1000L)

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
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, List(7) { 0 })

    val weeklyCount: StateFlow<Int> = barChartData.map { bars -> bars.sum() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val prevWeekCount: StateFlow<Int> = notificationsDebounced.map { all ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo     = todayStart - 7 * 86_400_000L
        val twoWeeksAgo = todayStart - 14 * 86_400_000L
        all.count { it.postTime >= twoWeeksAgo && it.postTime < weekAgo }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val categoryBreakdown: StateFlow<Map<String, Int>> = notificationsDebounced.map { all ->
        all.groupBy { it.priority.lowercase() }.mapValues { it.value.size }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Array(7) { IntArray(8) { 0 } })

    val appBreakdown: StateFlow<List<AppGuiltEntry>> = notificationsDebounced.map { all ->
        val dayMs = 86_400_000L
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        val weekAgo = todayStart - 6 * dayMs
        
        // Pre-calculate history sets to avoid repeating work in the loop
        val historySet = mutableSetOf<String>()
        all.forEach { historySet.add("${it.packageName}_${TimeUtils.getDayStart(it.postTime)}") }
        
        val recent = all.filter { it.postTime >= weekAgo }
        recent.groupBy { it.packageName }
            .map { (pkg, notifs) ->
                var streak = 0
                for (daysAgo in 0..365) {
                    val dayStart = todayStart - daysAgo * dayMs
                    if (historySet.contains("${pkg}_$dayStart")) streak++ else break
                }
                AppGuiltEntry(pkg, AppLabelCache.get(pkg, application), notifs.size, streak)
            }
            .sortedByDescending { it.count7Days }
            .take(10)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val avgResponseMinutes: StateFlow<Long?> = notificationsDebounced.map { all ->
        all.filter { it.isResolved && it.resolvedAt > 0 && it.resolvedAt > it.postTime }
            .takeIf { it.isNotEmpty() }
            ?.map { (it.resolvedAt - it.postTime) / 60_000 }
            ?.average()?.toLong()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── AI state ──────────────────────────────────────────────────────────────

    private val _aiMessages = MutableStateFlow<List<AIChatMessage>>(listOf(
        AIChatMessage(
            text = "Hi! I know your full notification history and can help you stay focused, spot patterns, or take action. What would you like to know?",
            isUser = false,
            type = MessageType.NORMAL,
            followUpChips = listOf("What did I miss?", "What's urgent?", "Summarise today", "Which app distracts me most?")
        )
    ))
    val aiMessages: StateFlow<List<AIChatMessage>> = _aiMessages.asStateFlow()

    private val _isAIBusy = MutableStateFlow(false)
    val isAIBusy: StateFlow<Boolean> = _isAIBusy.asStateFlow()

    // ── Send prompt ───────────────────────────────────────────────────────────

    fun sendAIPrompt(prompt: String) {
        viewModelScope.launch {
            _aiMessages.value += AIChatMessage(text = prompt, isUser = true)
            _isAIBusy.value = true
            kotlinx.coroutines.yield() // One frame so thinking bubble renders
            try {
                _aiMessages.value += buildResponse(prompt.trim())
            } finally {
                _isAIBusy.value = false
            }
        }
    }

    fun clearAiConversation() {
        _aiMessages.value = listOf(
            AIChatMessage(
                text = "Cleared. What would you like to know?",
                isUser = false,
                followUpChips = listOf("Summarise today", "What's urgent?", "Distraction report")
            )
        )
    }

    // ── Action execution ──────────────────────────────────────────────────────

    fun executeAction(action: String) {
        viewModelScope.launch {
            when {
                action.startsWith("ACTION:MUTE_CATEGORY:") -> {
                    val category = action.removePrefix("ACTION:MUTE_CATEGORY:")
                    muteCategory(category, 3_600_000L)
                    _aiMessages.value += AIChatMessage(
                        text = "Done — ${category.replaceFirstChar { it.uppercase() }} muted for 1 hour.",
                        isUser = false,
                        type = MessageType.ACTION,
                        followUpChips = listOf("Mute for 4 hours", "What else is noisy?", "Summarise today")
                    )
                }
                action.startsWith("ACTION:MUTE_CATEGORY_4H:") -> {
                    val category = action.removePrefix("ACTION:MUTE_CATEGORY_4H:")
                    muteCategory(category, 4 * 3_600_000L)
                    _aiMessages.value += AIChatMessage(
                        text = "Done — ${category.replaceFirstChar { it.uppercase() }} muted for 4 hours.",
                        isUser = false,
                        type = MessageType.ACTION,
                        followUpChips = listOf("What's still active?", "Summarise today")
                    )
                }
                action.startsWith("ACTION:RESOLVE_CATEGORY:") -> {
                    val category = action.removePrefix("ACTION:RESOLVE_CATEGORY:")
                    val all = notifications.value
                    val toResolve = if (category == "*") all else all.filter { it.priority == category }
                    val count = toResolve.count { !it.isResolved }
                    toResolve.filter { !it.isResolved }.forEach { NotificationRepository.resolveNotification(it.id) }
                    val label = if (category == "*") "all" else category
                    _aiMessages.value += AIChatMessage(
                        text = "Resolved $count $label notification${if (count != 1) "s" else ""}.",
                        isUser = false,
                        type = MessageType.ACTION,
                        followUpChips = listOf("Summarise today", "What's left?")
                    )
                }
                action.startsWith("ACTION:SNOOZE_CATEGORY:") -> {
                    val parts = action.removePrefix("ACTION:SNOOZE_CATEGORY:").split(":")
                    val category = parts.getOrNull(0) ?: return@launch
                    val durationMs = parts.getOrNull(1)?.toLongOrNull() ?: 3_600_000L
                    val all = notifications.value
                    val toSnooze = if (category == "*") all else all.filter { it.priority == category }
                    val count = toSnooze.count { !it.isResolved }
                    toSnooze.filter { !it.isResolved }
                        .forEach { NotificationRepository.snoozeNotification(it.id, durationMs) }
                    val mins = (durationMs / 60_000).toInt()
                    val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                    _aiMessages.value += AIChatMessage(
                        text = "Snoozed $count notification${if (count != 1) "s" else ""} for $label.",
                        isUser = false,
                        type = MessageType.ACTION,
                        followUpChips = listOf("What's still active?", "Summarise today")
                    )
                }
                else -> android.util.Log.w("HoneyJar-AI", "Unknown action: $action")
            }
        }
    }

    // ── Rules engine ──────────────────────────────────────────────────────────

    private suspend fun buildResponse(prompt: String): AIChatMessage {
        val p = prompt.lowercase()
        val now = System.currentTimeMillis()
        // Use the already-mapped HoneyNotification flow value — avoids DAO entity type mismatch
        val all = notifications.value
        val todayStart = TimeUtils.getDayStart(now)
        val today = all.filter { it.postTime >= todayStart }
        val unresolved = today.filter { !it.isResolved && it.snoozeUntil <= now }
        val snoozed = today.filter { !it.isResolved && it.snoozeUntil > now }
        val resolved = today.filter { it.isResolved }

        return when {
            p.hasAny("urgent", "priority", "important", "critical", "attention") ->
                respondUrgent(unresolved)

            p.hasAny("miss", "missed", "catch up", "catch-up", "what happened", "while i was") ->
                respondMissed(all, now)

            p.hasAny("summary", "summarise", "summarize", "today", "overview", "recap", "how was", "report") ->
                respondTodaySummary(today, unresolved, resolved, snoozed)

            p.hasAny("unresolved", "pending", "remaining", "left", "open", "active", "not resolved") ->
                respondUnresolved(unresolved, resolved)

            p.hasAny("distract", "noisy", "interrupt", "spam", "annoy", "worst app", "noisiest", "most notifications") ->
                respondDistractionReport()

            p.hasAny("response", "productivity", "how quick", "how fast", "action rate", "speed", "average time") ->
                respondResponseTime(today, resolved)

            p.hasAny("week", "weekly", "trend", "compare", "last week", "this week", "7 days") ->
                respondWeeklyTrend(all, now)

            p.hasAny("focus", "quiet", "do not disturb", "mute", "silence", "block", "stop") ->
                respondFocusHelp(today, unresolved)

            p.hasAny("snooze", "remind later", "remind me", "later", "not now") ->
                respondSnoozeOptions(unresolved)

            p.hasAny("clear", "resolve all", "mark all", "dismiss all", "done with", "finish") ->
                respondResolveAll(unresolved)

            p.hasAny("stat", "number", "count", "how many", "total", "all time") ->
                respondStats(today, unresolved, resolved, all)

            p.hasAny("evening", "wind down", "end of day", "night", "bed", "sleep", "done for the day") ->
                respondEveningWindDown(today, unresolved, resolved)

            p.hasAny("morning", "good morning", "start", "priorities for", "day ahead") ->
                respondMorningBriefing(all, today, unresolved, now)

            p.hasAny("message", "chat", "whatsapp", "text", "sms", "telegram", "signal") ->
                respondCategory("messages", today, unresolved)

            p.hasAny("email", "mail", "inbox", "outlook", "gmail") ->
                respondCategory("email", today, unresolved)

            p.hasAny("social", "instagram", "twitter", "facebook", "reddit", "tiktok") ->
                respondCategory("social", today, unresolved)

            p.hasAny("security", "vpn", "adguard", "virus", "threat", "surfshark") ->
                respondCategory("security", today, unresolved)

            p.hasAny("system", "android", "os", "phone system", "systemui") ->
                respondCategory("system", today, unresolved)

            p.hasAny("weather", "forecast", "rain", "wind", "temperature") ->
                respondCategory("weather", today, unresolved)

            p.hasAny("finance", "bank", "payment", "money", "revolut", "crypto") ->
                respondCategory("finance", today, unresolved)

            p.hasAny("media", "youtube", "spotify", "music", "video", "netflix") ->
                respondCategory("media", today, unresolved)

            p.hasAny("connected", "watch", "smartthings", "link to windows", "bluetooth") ->
                respondCategory("connected", today, unresolved)

            p.hasAny("photo", "camera", "screenshot", "gallery") ->
                respondCategory("photos", today, unresolved)

            p.hasAny("help", "what can you", "capabilities", "commands", "what do you do") ->
                respondHelp()

            else -> respondFallback(prompt, today, unresolved, all)
        }
    }

    // ── Intent responders ─────────────────────────────────────────────────────

    private fun respondUrgent(unresolved: List<HoneyNotification>): AIChatMessage {
        val urgent = unresolved.filter { it.priority == "urgent" }
        return if (urgent.isEmpty()) {
            AIChatMessage(
                text = "No urgent notifications right now. You have ${unresolved.size} unresolved total, but nothing flagged as urgent.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("What's unresolved?", "Summarise today", "Distraction report")
            )
        } else {
            val list = urgent.take(3).joinToString("\n") { "  • ${it.title}" }
            val more = if (urgent.size > 3) "\n  …and ${urgent.size - 3} more." else ""
            AIChatMessage(
                text = "⚠ ${urgent.size} urgent notification${if (urgent.size > 1) "s" else ""}:\n$list$more",
                isUser = false,
                type = MessageType.WARNING,
                followUpChips = listOf("Resolve all urgent", "Snooze urgent 1h", "What else is unresolved?"),
                pendingAction = "ACTION:RESOLVE_CATEGORY:urgent"
            )
        }
    }

    private fun respondMissed(all: List<HoneyNotification>, now: Long): AIChatMessage {
        val last4h = all.filter { it.postTime >= now - 4 * 3_600_000L && !it.isResolved }
        if (last4h.isEmpty()) {
            return AIChatMessage(
                text = "Nothing new in the last 4 hours — your jar is quiet.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("How about today overall?", "This week?")
            )
        }
        val byCategory = last4h.groupBy { it.priority }
            .entries.sortedByDescending { it.value.size }
        val breakdown = byCategory.take(3).joinToString(", ") { (cat, items) ->
            "${items.size} ${cat}"
        }
        val urgent = last4h.count { it.priority == "urgent" }
        val urgentLine = if (urgent > 0) " $urgent of them are urgent." else " None urgent."
        val topCat = byCategory.firstOrNull()?.key
        return AIChatMessage(
            text = "In the last 4 hours: ${last4h.size} unresolved — $breakdown.$urgentLine",
            isUser = false,
            type = if (urgent > 0) MessageType.WARNING else MessageType.INSIGHT,
            followUpChips = buildList {
                if (urgent > 0) add("Resolve urgent")
                add("Resolve all recent")
                if (topCat != null) add("Mute $topCat")
            },
            pendingAction = if (urgent > 0) "ACTION:RESOLVE_CATEGORY:urgent" else "ACTION:RESOLVE_CATEGORY:*"
        )
    }

    private fun respondTodaySummary(
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>,
        resolved: List<HoneyNotification>,
        snoozed: List<HoneyNotification>
    ): AIChatMessage {
        if (today.isEmpty()) {
            return AIChatMessage(
                text = "No notifications captured today yet. Enjoy the quiet!",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("This week?", "All time stats")
            )
        }
        val urgent = unresolved.count { it.priority == "urgent" }
        val topCat = today.groupBy { it.priority }.entries.maxByOrNull { it.value.size }
        val actionRate = if (today.isNotEmpty()) (resolved.size * 100 / today.size) else 0
        val weekly = weeklyCount.value
        val prev = prevWeekCount.value
        val trendStr = when {
            prev > 0 && weekly > prev * 1.2 -> " Busier than last week."
            prev > 0 && weekly < prev * 0.8 -> " Quieter than last week."
            else -> ""
        }

        val text = buildString {
            append("Today: ${today.size} notification${if (today.size != 1) "s" else ""}")
            if (unresolved.isNotEmpty()) append(", ${unresolved.size} unresolved")
            if (snoozed.isNotEmpty()) append(", ${snoozed.size} snoozed")
            if (resolved.isNotEmpty()) append(", ${resolved.size} resolved ($actionRate%)")
            append(".")
            if (urgent > 0) append("\n⚠ $urgent urgent still need attention.")
            if (topCat != null) append("\n${topCat.key.replaceFirstChar { it.uppercase() }} is busiest (${topCat.value.size}).")
            if (trendStr.isNotEmpty()) append(trendStr)
        }

        return AIChatMessage(
            text = text,
            isUser = false,
            type = if (urgent > 0) MessageType.WARNING else MessageType.INSIGHT,
            followUpChips = buildList {
                if (urgent > 0) add("Resolve urgent")
                if (unresolved.isNotEmpty()) add("Resolve all pending")
                add("Distraction report")
                if (size < 3) add("This week's trend")
            },
            pendingAction = if (urgent > 0) "ACTION:RESOLVE_CATEGORY:urgent" else null
        )
    }

    private fun respondUnresolved(
        unresolved: List<HoneyNotification>,
        resolved: List<HoneyNotification>
    ): AIChatMessage {
        if (unresolved.isEmpty()) {
            return AIChatMessage(
                text = "Nothing unresolved right now — your jar is empty. ${resolved.size} resolved today.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Summarise today", "This week's stats")
            )
        }
        val byCategory = unresolved.groupBy { it.priority }
            .entries.sortedByDescending { it.value.size }
        val breakdown = byCategory.take(4).joinToString("\n") { (cat, items) ->
            "  • ${cat.replaceFirstChar { it.uppercase() }}: ${items.size}"
        }
        val topCat = byCategory.firstOrNull()?.key ?: ""
        return AIChatMessage(
            text = "${unresolved.size} unresolved:\n$breakdown",
            isUser = false,
            type = MessageType.INSIGHT,
            followUpChips = listOf("Resolve all", "Mute $topCat", "Snooze all 1h"),
            pendingAction = "ACTION:RESOLVE_CATEGORY:*"
        )
    }

    private fun respondDistractionReport(): AIChatMessage {
        val apps = appBreakdown.value
        if (apps.isEmpty()) {
            return AIChatMessage(
                text = "Not enough data for a distraction report yet — keep using HoneyJar and I'll have patterns for you soon.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Summarise today")
            )
        }
        val top = apps.first()
        val top3 = apps.take(3).joinToString("\n") { "  • ${it.label}: ${it.count7Days} this week${if (it.streak >= 3) " 🔥 ${it.streak}d" else ""}" }
        val streakers = apps.filter { it.streak >= 5 }
        val streakNote = if (streakers.isNotEmpty())
            "\n${streakers.first().label} has interrupted you every day for ${streakers.first().streak} days." else ""

        val topCatToday = notificationsHome.value
            .groupBy { it.priority }.entries.maxByOrNull { it.value.size }?.key

        return AIChatMessage(
            text = "Top distractors this week:\n$top3$streakNote",
            isUser = false,
            type = MessageType.INSIGHT,
            followUpChips = buildList {
                add("Mute ${top.label.take(15)}")
                if (topCatToday != null) add("Mute $topCatToday for 1h")
                add("Response time stats")
            },
            pendingAction = topCatToday?.let { "ACTION:MUTE_CATEGORY:$it" }
        )
    }

    private fun respondResponseTime(
        today: List<HoneyNotification>,
        resolved: List<HoneyNotification>
    ): AIChatMessage {
        val avg = avgResponseMinutes.value
        val actionRate = if (today.isNotEmpty()) (resolved.size * 100 / today.size) else 0
        return if (avg == null) {
            AIChatMessage(
                text = "No resolved notifications yet, so no response time data. You've actioned ${resolved.size} of ${today.size} today ($actionRate%).",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Resolve some now", "Summarise today")
            )
        } else {
            val quality = when {
                avg < 5   -> "lightning fast ⚡"
                avg < 30  -> "quick 👍"
                avg < 120 -> "moderate"
                else      -> "slow — notifications may be piling up"
            }
            AIChatMessage(
                text = "Average response time: ${formatDuration(avg)} ($quality).\n$actionRate% action rate today (${resolved.size} of ${today.size} resolved).",
                isUser = false,
                type = MessageType.INSIGHT,
                followUpChips = listOf("This week's trend", "Resolve all pending", "Distraction report")
            )
        }
    }

    private fun respondWeeklyTrend(all: List<HoneyNotification>, now: Long): AIChatMessage {
        val dayMs = 86_400_000L
        val todayStart = TimeUtils.getDayStart(now)
        val days = (6 downTo 0).map { daysAgo ->
            val start = todayStart - daysAgo * dayMs
            val count = all.count { it.postTime >= start && it.postTime < start + dayMs }
            val name = SimpleDateFormat("EEE", Locale.getDefault())
                .format(java.util.Date(start))
            name to count
        }
        val weekly = weeklyCount.value
        val prev = prevWeekCount.value
        val busiest = days.maxByOrNull { it.second }
        val quietest = days.filter { it.second > 0 }.minByOrNull { it.second }
        val trendLine = when {
            prev == 0   -> "First week of data — nothing to compare yet."
            weekly > prev -> "↑ ${weekly - prev} more than last week ($prev)."
            weekly < prev -> "↓ ${prev - weekly} fewer than last week ($prev). Getting quieter!"
            else          -> "Same as last week ($prev)."
        }
        val text = buildString {
            append("This week: $weekly notifications. $trendLine")
            if (busiest != null && busiest.second > 0)
                append("\nBusiest: ${busiest.first} (${busiest.second}).")
            if (quietest != null && quietest.first != busiest?.first)
                append(" Quietest: ${quietest.first} (${quietest.second}).")
        }
        return AIChatMessage(
            text = text,
            isUser = false,
            type = MessageType.INSIGHT,
            followUpChips = listOf("Category breakdown", "Distraction report", "Response time")
        )
    }

    private fun respondFocusHelp(
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>
    ): AIChatMessage {
        val topCat = today.groupBy { it.priority }
            .entries.sortedByDescending { it.value.size }.firstOrNull()?.key
        val topApp = appBreakdown.value.firstOrNull()

        val text = buildString {
            append("To maximise focus:")
            if (topCat != null)
                append("\n  • Mute ${topCat.replaceFirstChar { it.uppercase() }} — your busiest category today (${today.count { it.priority == topCat }}).")
            if (topApp != null)
                append("\n  • ${topApp.label} is your noisiest app this week (${topApp.count7Days} pings).")
            if (unresolved.isNotEmpty())
                append("\n  • Clear your ${unresolved.size} unresolved items so they're not nagging you.")
        }

        return AIChatMessage(
            text = text,
            isUser = false,
            type = MessageType.NORMAL,
            followUpChips = buildList {
                if (topCat != null) add("Mute $topCat 1h")
                add("Resolve all pending")
                add("Snooze all 1h")
            },
            pendingAction = topCat?.let { "ACTION:MUTE_CATEGORY:$it" }
        )
    }

    private fun respondSnoozeOptions(unresolved: List<HoneyNotification>): AIChatMessage {
        return if (unresolved.isEmpty()) {
            AIChatMessage(
                text = "Nothing to snooze — no unresolved notifications right now.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Summarise today")
            )
        } else {
            AIChatMessage(
                text = "Snooze ${unresolved.size} unresolved notifications for how long?",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Snooze all 30min", "Snooze all 1h", "Snooze all 2h"),
                pendingAction = "ACTION:SNOOZE_CATEGORY:*:1800000"
            )
        }
    }

    private fun respondResolveAll(unresolved: List<HoneyNotification>): AIChatMessage {
        return if (unresolved.isEmpty()) {
            AIChatMessage(
                text = "Nothing to clear — no unresolved notifications right now.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Summarise today")
            )
        } else {
            AIChatMessage(
                text = "Resolve all ${unresolved.size} unresolved notifications?",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("Yes, resolve all", "Just urgent", "Cancel"),
                pendingAction = "ACTION:RESOLVE_CATEGORY:*"
            )
        }
    }

    private fun respondStats(
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>,
        resolved: List<HoneyNotification>,
        all: List<HoneyNotification>
    ): AIChatMessage {
        val weekly = weeklyCount.value
        val prev = prevWeekCount.value
        val trend = when {
            prev == 0   -> ""
            weekly > prev -> " ↑${weekly - prev} vs last week"
            weekly < prev -> " ↓${prev - weekly} vs last week"
            else          -> " (same as last week)"
        }
        return AIChatMessage(
            text = "Today: ${today.size} total, ${unresolved.size} unresolved, ${resolved.size} resolved.\nThis week: $weekly$trend.\nAll time: ${all.size} captured.",
            isUser = false,
            type = MessageType.INSIGHT,
            followUpChips = listOf("Category breakdown", "Weekly trend", "Response time")
        )
    }

    private fun respondEveningWindDown(
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>,
        resolved: List<HoneyNotification>
    ): AIChatMessage {
        val actionRate = if (today.isNotEmpty()) (resolved.size * 100 / today.size) else 0
        val avg = avgResponseMinutes.value
        val grade = when {
            actionRate >= 80 -> "Excellent day 🌟"
            actionRate >= 60 -> "Solid day 👍"
            actionRate >= 40 -> "Decent day"
            unresolved.isEmpty() -> "All clear 🎉"
            else -> "A few things left over"
        }
        val text = buildString {
            append("$grade. ${today.size} notifications today, ${resolved.size} resolved ($actionRate%).")
            if (unresolved.isNotEmpty()) {
                val urgent = unresolved.count { it.priority == "urgent" }
                append("\n${unresolved.size} still unresolved")
                if (urgent > 0) append(", including $urgent urgent")
                append(". Clear them before tomorrow?")
            } else {
                append("\nYou cleared everything — great work.")
            }
            if (avg != null) append("\nAvg response time: ${formatDuration(avg)}.")
        }
        return AIChatMessage(
            text = text,
            isUser = false,
            type = if (unresolved.isEmpty()) MessageType.INSIGHT else MessageType.NORMAL,
            followUpChips = buildList {
                if (unresolved.isNotEmpty()) add("Resolve all pending")
                add("This week's summary")
                add("Distraction report")
            },
            pendingAction = if (unresolved.isNotEmpty()) "ACTION:RESOLVE_CATEGORY:*" else null
        )
    }

    private fun respondMorningBriefing(
        all: List<HoneyNotification>,
        @Suppress("UNUSED_PARAMETER") today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>,
        now: Long
    ): AIChatMessage {
        val overnight = all.filter {
            it.postTime >= now - 8 * 3_600_000L && it.postTime < TimeUtils.getDayStart(now) && !it.isResolved
        }
        val urgent = unresolved.filter { it.priority == "urgent" }
        val text = buildString {
            if (overnight.isNotEmpty()) {
                val oUrgent = overnight.count { it.priority == "urgent" }
                append("Overnight: ${overnight.size} notification${if (overnight.size != 1) "s" else ""}")
                if (oUrgent > 0) append(", $oUrgent urgent") else append(", none urgent")
                append(".\n")
            } else {
                append("Quiet night — nothing came in overnight.\n")
            }
            if (unresolved.isNotEmpty()) append("${unresolved.size} unresolved heading into today.")
            else append("You're starting fresh — nothing unresolved.")
        }
        return AIChatMessage(
            text = text,
            isUser = false,
            type = if (urgent.isNotEmpty()) MessageType.WARNING else MessageType.INSIGHT,
            followUpChips = buildList {
                if (urgent.isNotEmpty()) add("Resolve urgent")
                if (overnight.isNotEmpty()) add("Resolve overnight")
                add("Today's priorities")
                if (size < 3) add("Distraction report")
            },
            pendingAction = if (urgent.isNotEmpty()) "ACTION:RESOLVE_CATEGORY:urgent" else null
        )
    }

    private fun respondCategory(
        category: String,
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>
    ): AIChatMessage {
        val catToday = today.filter { it.priority == category }
        val catUnresolved = unresolved.filter { it.priority == category }
        val catWeekly = categoryBreakdown.value[category] ?: 0
        val label = category.replaceFirstChar { it.uppercase() }

        if (catToday.isEmpty() && catWeekly == 0) {
            return AIChatMessage(
                text = "No $label notifications today or this week.",
                isUser = false,
                type = MessageType.NORMAL,
                followUpChips = listOf("What did I get today?", "Summarise today")
            )
        }
        return AIChatMessage(
            text = "$label: ${catToday.size} today (${catUnresolved.size} unresolved), $catWeekly this week.",
            isUser = false,
            type = MessageType.INSIGHT,
            followUpChips = buildList {
                if (catUnresolved.isNotEmpty()) add("Resolve all $category")
                add("Mute $category 1h")
                add("Summarise today")
            },
            pendingAction = if (catUnresolved.isNotEmpty()) "ACTION:RESOLVE_CATEGORY:$category" else null
        )
    }

    private fun respondHelp(): AIChatMessage {
        return AIChatMessage(
            text = "I can help with:\n• Summaries — today, this week, by category\n• Urgent & unresolved alerts\n• Distraction reports — noisiest apps and categories\n• Focus mode — mute noisy categories\n• Actions — resolve, snooze, or mute in one tap\n• Stats — response time, trends, counts\n• Morning briefings & evening wind-downs\n\nJust ask naturally.",
            isUser = false,
            type = MessageType.NORMAL,
            followUpChips = listOf("Summarise today", "What's urgent?", "Distraction report", "This week's trend")
        )
    }

    private fun respondFallback(
        prompt: String,
        today: List<HoneyNotification>,
        unresolved: List<HoneyNotification>,
        @Suppress("UNUSED_PARAMETER") all: List<HoneyNotification>
    ): AIChatMessage {
        // Try to match a category name in the prompt
        val categories = allPriorityGroups.value.filter { it.isEnabled }.map { it.key }
        val mentionedCat = categories.firstOrNull { cat -> prompt.lowercase().contains(cat) }
        if (mentionedCat != null) return respondCategory(mentionedCat, today, unresolved)

        // Try to match an app name
        val mentionedApp = appBreakdown.value.firstOrNull { entry ->
            prompt.lowercase().contains(entry.label.lowercase()) ||
            prompt.lowercase().contains(entry.packageName.substringAfterLast(".").lowercase())
        }
        if (mentionedApp != null) {
            return AIChatMessage(
                text = "${mentionedApp.label}: ${mentionedApp.count7Days} notifications this week${if (mentionedApp.streak >= 3) ", ${mentionedApp.streak}-day streak 🔥" else ""}.",
                isUser = false,
                type = MessageType.INSIGHT,
                followUpChips = listOf("Distraction report", "Summarise today")
            )
        }

        val urgent = unresolved.count { it.priority == "urgent" }
        return AIChatMessage(
            text = "I didn't quite catch that. Here's where things stand: ${unresolved.size} unresolved today${if (urgent > 0) ", $urgent urgent" else ""}. Try one of the options below.",
            isUser = false,
            type = MessageType.NORMAL,
            followUpChips = listOf("Summarise today", "What's urgent?", "Distraction report", "Help")
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True if the string contains any of the given keywords. */
    private fun String.hasAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }

    private fun formatDuration(minutes: Long): String = when {
        minutes < 60   -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
        else           -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    }

    // ── Category / group actions ──────────────────────────────────────────────

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

    suspend fun buildBackupJson(context: Context): String =
        BackupManager.buildBackupJson(context, statsDao, repository.dao)

    suspend fun restoreBackupJson(context: Context, json: String) =
        BackupManager.restoreFromJson(context, json, notificationDao, statsDao, repository.dao)

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
