package com.honeyjar.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.repositories.NotificationRepository
import com.honeyjar.app.ui.components.AppIcon
import com.honeyjar.app.ui.components.ContextualActionsSheet
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.ColorUtils
import com.honeyjar.app.utils.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    initialFilter: String? = null,
    initialStatus: String? = null
) {
    val bNotifications by viewModel.notifications.collectAsState()
    val priorityGroups by viewModel.allPriorityGroups.collectAsState()
    val priorityColors by viewModel.priorityColors.collectAsState()
    val colors = LocalHoneyJarColors.current

    var selectedCategory by remember { mutableStateOf<PriorityGroupEntity?>(null) }
    var readFilter by remember { mutableStateOf(initialStatus ?: "all") }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(priorityGroups, initialFilter) {
        if (selectedCategory == null && initialFilter != null) {
            selectedCategory = priorityGroups.find { it.key.lowercase() == initialFilter.lowercase() }
        }
    }

    val filteredNotifications = remember(bNotifications, selectedCategory, readFilter, searchQuery) {
        val now = System.currentTimeMillis()
        val query = searchQuery.trim().lowercase()

        bNotifications.filter { notif ->
            val matchesCategory = selectedCategory == null ||
                notif.priority.lowercase() == selectedCategory!!.key.lowercase()

            val matchesRead = when (readFilter) {
                "read"    -> notif.isResolved
                "unread"  -> !notif.isResolved && notif.snoozeUntil <= now
                "snoozed" -> !notif.isResolved && notif.snoozeUntil > now
                else      -> true
            }

            val matchesSearch = query.isEmpty() ||
                notif.title.lowercase().contains(query) ||
                notif.text.lowercase().contains(query) ||
                notif.packageName.lowercase().contains(query)

            matchesCategory && matchesRead && matchesSearch
        }.sortedByDescending { it.postTime }
    }

    val dayOfWeekFormatter = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dayMonthFormatter  = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val yearFormatter      = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    val groupedNotifications = remember(filteredNotifications) {
        val now            = System.currentTimeMillis()
        val todayStart     = TimeUtils.getDayStart(now)
        val yesterdayStart = todayStart - 86_400_000L
        val weekStart      = todayStart - 6 * 86_400_000L
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val yearStart = cal.timeInMillis

        filteredNotifications
            .groupBy { TimeUtils.getDayStart(it.postTime) }
            .entries
            .sortedByDescending { it.key }
            .map { (dayStart, notifs) ->
                val label = when {
                    dayStart >= todayStart     -> "Today"
                    dayStart >= yesterdayStart -> "Yesterday"
                    dayStart >= weekStart      -> dayOfWeekFormatter.format(Date(dayStart))
                    dayStart >= yearStart      -> dayMonthFormatter.format(Date(dayStart))
                    else                       -> yearFormatter.format(Date(dayStart))
                }
                label to notifs
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
    ) {

        // ── Header ────────────────────────────────────────────────────────
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "History",
                        fontWeight = FontWeight.Black,
                        fontSize = 31.sp,
                        fontFamily = PlayfairDisplay,
                        fontStyle = FontStyle.Italic,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(bottom = 4.dp)
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

                // ── Search bar (Now under title) ──────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.itemBg,
                    border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search notifications…",
                                    color = colors.textSecondary.copy(0.5f),
                                    fontSize = 14.sp,
                                    fontFamily = Outfit
                                )
                            },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = Outfit),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = ""; focusManager.clearFocus() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ── Read filter + Category chips ──────────────────────────
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Filter Row
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("all" to "All", "unread" to "Unread", "read" to "Read", "snoozed" to "Snoozed")
                                    .forEach { (key, label) ->
                                        val isActive = readFilter == key
                                        Surface(
                                            onClick = { readFilter = key },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            border = if (isActive) null else BorderStroke(1.dp, colors.glassBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                label,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else colors.textSecondary,
                                                fontFamily = Outfit,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                            }
                            
                            val hasUnread = remember(filteredNotifications) {
                                filteredNotifications.any { !it.isResolved }
                            }
                            
                            if (hasUnread) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        "Mark all read",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        fontFamily = Outfit,
                                        modifier = Modifier
                                            .clickable { 
                                                val unreadIds = filteredNotifications.filter { !it.isResolved }.map { it.id }
                                                NotificationRepository.resolveGroupNotifications(unreadIds)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = colors.glassBorder, thickness = 1.dp)

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

        // ── Notifications list ────────────────────────────────────────────
        if (priorityColors.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary.copy(0.5f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Reading the archives…", color = colors.textSecondary, fontSize = 14.sp, fontFamily = Outfit)
                }
            }
        } else if (filteredNotifications.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (searchQuery.isNotEmpty()) "🔍" else "📂", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\""
                        else "Nothing here yet",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = colors.textPrimary, fontFamily = Outfit
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Your history is as clean as a fresh pot of honey.",
                            color = colors.textSecondary, fontSize = 14.sp, fontFamily = Outfit
                        )
                    }
                }
            }
        } else {
            groupedNotifications.forEach { (dateLabel, notifs) ->
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

                items(notifs, key = { it.id }) { notification ->
                    var showActions by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    Spacer(Modifier.height(8.dp))

                    Box(Modifier.combinedClickable(onClick = {}, onLongClick = { showActions = true })) {
                        TimelineCard(notification, priorityColors)
                    }

                    if (showActions) {
                        ContextualActionsSheet(
                            notification = notification,
                            priorityGroups = priorityGroups,
                            onDismiss = { showActions = false },
                            onAction = { action ->
                                when {
                                    action == "open_app" -> { launchApp(context, notification.packageName); showActions = false }
                                    action == "resolve" -> { NotificationRepository.resolveNotification(notification.id); showActions = false }
                                    action == "unresolve" -> { NotificationRepository.unresolveNotification(notification.id); showActions = false }
                                    action.startsWith("change_priority:") -> {
                                        NotificationRepository.changePriority(notification.id, action.removePrefix("change_priority:"))
                                        showActions = false
                                    }
                                    action == "unsnooze" -> { NotificationRepository.unsnoozeNotification(notification.id); showActions = false }
                                    action.startsWith("snooze:") -> {
                                        NotificationRepository.snoozeNotification(notification.id, action.removePrefix("snooze:").toLongOrNull() ?: 3600000L)
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

// ── Category chips ────────────────────────────────────────────────────────────

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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { group ->
                    val isSelected = (group == null && selected == null) || (group != null && selected?.key == group.key)
                    val chipColor = if (group == null) MaterialTheme.colorScheme.primary
                                    else ColorUtils.parseHexColor(group.colour, MaterialTheme.colorScheme.primary)
                    FilterTab(
                        label = group?.label ?: "All",
                        emoji = categoryEmoji(group?.key ?: "all"),
                        isActive = isSelected,
                        categoryColor = chipColor,
                        modifier = Modifier.weight(1f),
                        onClick = { if (group == null) onSelect(null) else onSelect(if (isSelected) null else group) }
                    )
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun categoryEmoji(category: String) = when (category.lowercase()) {
    "urgent"    -> "🚨"
    "all"       -> "✨"
    "messages"  -> "💬"
    "social"    -> "🌐"
    "email"     -> "✉️"
    "calendar"  -> "📅"
    "calls"     -> "📞"
    "weather"   -> "🌦️"
    "travel"    -> "🚗"
    "finance"   -> "💰"
    "shopping"  -> "🛒"
    "media"     -> "🎬"
    "security"  -> "🔒"
    "connected" -> "🔗"
    "updates"   -> "🔄"
    "photos"    -> "📸"
    "system"    -> "⚙️"
    else        -> "🍯"
}

fun launchApp(context: android.content.Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

// ── TimelineCard ──────────────────────────────────────────────────────────────

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
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF22C55E).copy(0.08f), Color(0xFF22C55E).copy(0.08f)))
            else null
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(packageName = notification.packageName, category = notification.priority, size = 36.dp, tintColor = iconTint)
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            appLabel,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                        Text(TimeUtils.formatDuration(notification.postTime), fontSize = 10.sp, color = colors.textSecondary.copy(0.6f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notification.title,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = if (notification.isResolved) colors.textSecondary else colors.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (notification.text.isNotBlank()) {
                        Text(notification.text, fontSize = 13.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        val isSnoozed = notification.snoozeUntil > System.currentTimeMillis()
        if (notification.isResolved || isSnoozed) {
            val badgeText  = if (isSnoozed) "SNOOZED" else "READ"
            val badgeColor = if (isSnoozed) MaterialTheme.colorScheme.primary else Color(0xFF22C55E)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(badgeColor.copy(0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor, fontFamily = Outfit)
            }
        }
    }
}
