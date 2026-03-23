package com.honeyjar.app.ui.screens

import android.content.Intent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import androidx.compose.ui.platform.LocalContext
import com.honeyjar.app.ui.components.AppIcon
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.models.HoneyNotification
import androidx.compose.foundation.combinedClickable
import com.honeyjar.app.ui.components.ContextualActionsSheet
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke

import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.TimeUtils
import com.honeyjar.app.utils.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, initialFilter: String? = null, initialStatus: String? = null) {
    val bNotifications by viewModel.notifications.collectAsState()
    val priorityGroups by viewModel.allPriorityGroups.collectAsState()
    val priorityColors by viewModel.priorityColors.collectAsState()
    val colors = LocalHoneyJarColors.current

    var selectedCategory by remember { mutableStateOf<PriorityGroupEntity?>(null) }
    var readFilter by remember { mutableStateOf(initialStatus ?: "all") } // "all", "unread", "read", "snoozed"
    var showReadFilterMenu by remember { mutableStateOf(false) }
    var timeWindow by remember { mutableStateOf("today") } // "today" or "history"
    var historyDays by remember { mutableStateOf(7) }
    var showHistoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(priorityGroups, initialFilter) {
        if (selectedCategory == null && initialFilter != null) {
            selectedCategory = priorityGroups.find { it.key.lowercase() == initialFilter.lowercase() }
        }
    }

    val filteredNotifications = remember(bNotifications, selectedCategory, readFilter, timeWindow, historyDays) {
        val now = System.currentTimeMillis()
        val todayStart = TimeUtils.getDayStart(now)
        val cutoff = when (timeWindow) {
            "today"   -> todayStart
            else      -> todayStart - (historyDays - 1) * 86_400_000L
        }
        val ceiling = if (timeWindow == "today") todayStart + 86_400_000L else Long.MAX_VALUE

        bNotifications.filter { notif ->
            val matchesWindow = notif.postTime >= cutoff && notif.postTime < ceiling
            val matchesCategory = selectedCategory == null || notif.priority.lowercase() == selectedCategory!!.key.lowercase()
            val matchesRead = when (readFilter) {
                "read"    -> notif.isResolved
                "unread"  -> !notif.isResolved && notif.snoozeUntil <= now
                "snoozed" -> !notif.isResolved && notif.snoozeUntil > now
                else      -> true
            }
            matchesWindow && matchesCategory && matchesRead
        }.sortedByDescending { it.postTime }
    }

    val dayOfWeekFormatter = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dayMonthFormatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val groupedNotifications = remember(filteredNotifications, timeWindow) {
        val now = System.currentTimeMillis()
        val todayStart     = TimeUtils.getDayStart(now)
        val yesterdayStart = todayStart - 86_400_000L
        val weekStart      = todayStart - 6 * 86_400_000L

        if (timeWindow == "today") {
            // Single flat group with no header needed — but reuse the same structure
            listOf("Today" to filteredNotifications)
        } else {
            filteredNotifications
                .groupBy { TimeUtils.getDayStart(it.postTime) }
                .entries
                .sortedByDescending { it.key }
                .map { (dayStart, notifs) ->
                    val label = when {
                        dayStart >= todayStart     -> "Today"
                        dayStart >= yesterdayStart -> "Yesterday"
                        dayStart >= weekStart      -> dayOfWeekFormatter.format(Date(dayStart))
                        else                       -> dayMonthFormatter.format(Date(dayStart))
                    }
                    label to notifs
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 2.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        // ── Dashboard header (scrolls with content) ──────────────────────
        item(key = "dashboard") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Title + count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "History",
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        fontFamily = PlayfairDisplay,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            "${filteredNotifications.size} notifications",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp, 
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Dashboard card: time pills + read filter + category chips
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: Today | History ▾ | All — equal width, white inactive border
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val todayActive = timeWindow == "today"
                            val historyActive = timeWindow == "history"
                            val readActive = readFilter != "all"

                            // Today pill
                            Surface(
                                onClick = { timeWindow = "today" },
                                shape = RoundedCornerShape(8.dp),
                                color = if (todayActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else colors.itemBg,
                                border = BorderStroke(if (todayActive) 2.dp else 1.dp, if (todayActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Today",
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    fontSize = 13.sp, fontFamily = Outfit,
                                    fontWeight = if (todayActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (todayActive) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            // History pill with dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { if (historyActive) showHistoryMenu = true else timeWindow = "history" },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (historyActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else colors.itemBg,
                                    border = BorderStroke(if (historyActive) 2.dp else 1.dp, if (historyActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            if (historyActive) "$historyDays days" else "History",
                                            fontSize = 13.sp, fontFamily = Outfit,
                                            fontWeight = if (historyActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (historyActive) MaterialTheme.colorScheme.primary else colors.textSecondary
                                        )
                                        if (historyActive) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                DropdownMenu(expanded = showHistoryMenu, onDismissRequest = { showHistoryMenu = false }) {
                                    listOf(3 to "Last 3 days", 7 to "Last 7 days", 14 to "Last 14 days", 30 to "Last 30 days").forEach { (days, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontFamily = Outfit) },
                                            onClick = { historyDays = days; timeWindow = "history"; showHistoryMenu = false },
                                            trailingIcon = if (historyDays == days && historyActive) ({
                                                Icon(Icons.Default.FilterList, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }) else null
                                        )
                                    }
                                }
                            }

                            // Read filter pill — equal weight
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { showReadFilterMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (readActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else colors.itemBg,
                                    border = BorderStroke(if (readActive) 2.dp else 1.dp, if (readActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.FilterList, contentDescription = null,
                                            tint = if (readActive) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                            modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            when (readFilter) { 
                                                "read" -> "Read"
                                                "unread" -> "Unread"
                                                "snoozed" -> "Snoozed"
                                                else -> "All" 
                                            },
                                            fontSize = 13.sp, fontFamily = Outfit,
                                            fontWeight = if (readActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (readActive) MaterialTheme.colorScheme.primary else colors.textSecondary
                                        )
                                    }
                                }
                                DropdownMenu(expanded = showReadFilterMenu, onDismissRequest = { showReadFilterMenu = false }) {
                                    listOf("all" to "All", "unread" to "Unread", "read" to "Read", "snoozed" to "Snoozed").forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontFamily = Outfit) },
                                            onClick = { readFilter = key; showReadFilterMenu = false },
                                            trailingIcon = if (readFilter == key) ({
                                                Icon(Icons.Default.FilterList, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }) else null
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = colors.glassBorder, thickness = 1.dp)

                        // Row 2: Category chips
                        HistoryCategoryChips(
                            priorityGroups = priorityGroups,
                            selected = selectedCategory,
                            onSelect = { selectedCategory = it }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
        // ─────────────────────────────────────────────────────────────────
            if (priorityColors.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Reading the archives...", color = colors.textSecondary, fontSize = 14.sp, fontFamily = Outfit)
                    }
                }
            } else if (filteredNotifications.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📂", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Nothing here yet", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.textPrimary, fontFamily = Outfit)
                        Text("Your history is as clean as a fresh pot of honey.", color = colors.textSecondary, fontSize = 14.sp, fontFamily = Outfit)
                    }
                }
            } else {
                groupedNotifications.forEach { (dateLabel, notifs) ->
                    // Suppress the "Today" date header — the dashboard card already makes it clear
                    if (!(timeWindow == "today" && dateLabel == "Today")) {
                        stickyHeader(key = "header_$dateLabel") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    dateLabel,
                                    fontFamily = Outfit,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    items(notifs, key = { it.id }) { notification ->
                        var showActions by remember { mutableStateOf(false) }
                        val context = LocalContext.current

                        Spacer(Modifier.height(8.dp))

                        Box(Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { showActions = true }
                        )) {
                            TimelineCard(notification, priorityColors)
                        }

                        if (showActions) {
                            ContextualActionsSheet(
                                notification = notification,
                                priorityGroups = priorityGroups,
                                onDismiss = { showActions = false },
                                onAction = { action ->
                                    when {
                                        action == "open_app" -> {
                                            val pkg = notification.packageName
                                            showActions = false
                                            launchApp(context, pkg)
                                        }
                                        action == "resolve" -> {
                                            NotificationRepository.resolveNotification(notification.id)
                                            showActions = false
                                        }
                                        action == "unresolve" -> {
                                            NotificationRepository.unresolveNotification(notification.id)
                                            showActions = false
                                        }
                                        action.startsWith("change_priority:") -> {
                                            val newPriority = action.removePrefix("change_priority:")
                                            NotificationRepository.changePriority(notification.id, newPriority)
                                            showActions = false
                                        }
                                        action == "unsnooze" -> {
                                            NotificationRepository.unsnoozeNotification(notification.id)
                                            showActions = false
                                        }
                                        action.startsWith("snooze:") -> {
                                            val duration = action.removePrefix("snooze:").toLongOrNull() ?: 3600000L
                                            NotificationRepository.snoozeNotification(notification.id, duration)
                                            showActions = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
}

@Composable
private fun HistoryCategoryChips(
    priorityGroups: List<PriorityGroupEntity>,
    selected: PriorityGroupEntity?,
    onSelect: (PriorityGroupEntity?) -> Unit
) {
    val colors = LocalHoneyJarColors.current
    val enabledGroups = remember(priorityGroups) {
        priorityGroups.filter { it.isEnabled }.sortedBy { it.position }
    }

    val allItems: List<PriorityGroupEntity?> = listOf(null) + enabledGroups
    val rows = allItems.chunked(4)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { group ->
                    val isSelected = group == null && selected == null || group != null && selected?.key == group.key
                    val chipColor = if (group == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        ColorUtils.parseHexColor(group.colour, MaterialTheme.colorScheme.primary)
                    }
                    
                    FilterTab(
                        label = group?.label ?: "All",
                        emoji = categoryEmoji(group?.key ?: "all"),
                        isActive = isSelected,
                        categoryColor = chipColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (group == null) onSelect(null)
                            else onSelect(if (isSelected) null else group)
                        }
                    )
                }
                // pad incomplete last row so chips stay equal width
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}




private fun categoryEmoji(category: String) = when (category.lowercase()) {
    "urgent", "all" -> "✨"
    "messages" -> "💬"
    "email"    -> "✉️"
    "delivery" -> "📦"
    "updates"  -> "🚀"
    "calendar" -> "📅"
    "system"   -> "⚙️"
    else       -> "🍯"
}

fun launchApp(context: android.content.Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun TimelineCard(notification: HoneyNotification, priorityColors: Map<String, String>) {
    val colors = LocalHoneyJarColors.current
    val context = LocalContext.current

    val appLabel = remember(notification.packageName) {
        AppLabelCache.get(notification.packageName, context)
    }

    val priorityColor = remember(notification.priority, priorityColors) {
        ColorUtils.parseHexColor(priorityColors[notification.priority.lowercase()] ?: "#2A2A2A", Color(0xFF2A2A2A))
    }

    val iconTint = if (notification.isResolved) Color(0xFF666666) else priorityColor

    Box(modifier = Modifier.fillMaxWidth()) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (notification.isResolved) Color(0xFF22C55E).copy(alpha = 0.4f) else priorityColor.copy(alpha = 0.8f),
            borderWidth = if (notification.isResolved) 2.dp else 6.dp,
            gradient = if (notification.isResolved)
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF22C55E).copy(alpha = 0.08f), Color(0xFF22C55E).copy(alpha = 0.08f)))
            else null
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(
                    packageName = notification.packageName,
                    category = notification.priority,
                    size = 36.dp,
                    tintColor = iconTint
                )
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(appLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Text(TimeUtils.formatDuration(notification.postTime), fontSize = 10.sp, color = colors.textSecondary.copy(0.6f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        notification.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (notification.isResolved) colors.textSecondary else colors.textPrimary
                    )
                    Text(
                        notification.text,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val isSnoozed = notification.snoozeUntil > System.currentTimeMillis()
        if (notification.isResolved || isSnoozed) {
            val badgeText = if (isSnoozed) "SNOOZED" else "READ"
            val badgeColor = if (isSnoozed) MaterialTheme.colorScheme.primary else Color(0xFF22C55E)
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    fontFamily = Outfit
                )
            }
        }
    }
}
