package com.honeyjar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.utils.TimeUtils
import com.honeyjar.app.utils.ColorUtils
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import com.honeyjar.app.ui.components.AppIcon
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.viewmodels.MainViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import android.content.Context
import androidx.compose.foundation.BorderStroke
import com.honeyjar.app.utils.AppLabelCache
import com.honeyjar.app.utils.NotificationCategories
import com.honeyjar.app.ui.components.ContextualActionsSheet
import android.content.Intent
import com.honeyjar.app.repositories.NotificationRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule

@Composable
fun HomeScreen(
    viewModel: MainViewModel, 
    onNavigateToHistory: (filter: String?, status: String?) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsState()
    val isSmartGrouping by viewModel.isSmartGrouping.collectAsState()

    val bNotifications by viewModel.notificationsHome.collectAsState()
    val priorityColors by viewModel.priorityColors.collectAsState()
    val context = LocalContext.current
    val colors = LocalHoneyJarColors.current

    val allPriorityGroups by viewModel.allPriorityGroups.collectAsState()

    val activeNotifications = remember(bNotifications, allPriorityGroups) {
        val now = System.currentTimeMillis()
        bNotifications.filter { notif ->
            val isSnoozed = notif.snoozeUntil > now
            val group = allPriorityGroups.find { it.key.lowercase() == notif.priority.lowercase() }
            val isIgnored = group != null && group.ignoreUntil > now
            val isDisabled = group != null && !group.isEnabled
            
            !notif.isResolved && !isSnoozed && !isIgnored && !isDisabled
        }
    }

    val todayTotalNotifications = remember(bNotifications) {
        bNotifications.filter { it.snoozeUntil < System.currentTimeMillis() }
    }

    val heroPriorityColors = remember(todayTotalNotifications, priorityColors) {
        resolvePriorityColors(todayTotalNotifications, priorityColors)
    }

    val categories = remember(heroPriorityColors) {
        heroPriorityColors.keys.toList()
    }

    val snoozedNotifications = remember(bNotifications) {
        bNotifications.filter { !it.isResolved && it.snoozeUntil > System.currentTimeMillis() }
    }
    val snoozedCount = snoozedNotifications.size

    val digestGroups = remember(activeNotifications, heroPriorityColors, selectedCategory) {
        val groups = buildDigestGroups(activeNotifications, heroPriorityColors, context)
        if (selectedCategory == null) groups
        else groups.filter { it.category == selectedCategory }
    }

    // Ungrouped: flat list of individual notifications sorted by time, filtered by category
    val flatNotifications = remember(activeNotifications, heroPriorityColors, selectedCategory) {
        val filtered = if (selectedCategory == null) activeNotifications
                       else activeNotifications.filter { it.priority.lowercase() == selectedCategory }
        filtered.sortedByDescending { it.postTime }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            HeaderSection(autoBackupFrequency, onNavigateToSettings)
        }
        item {
            HeroCard(todayTotalNotifications, bNotifications, heroPriorityColors, snoozedCount = snoozedCount, onReviewClick = { onNavigateToHistory("urgent", null) })
        }
        if (snoozedCount > 0) {
            item {
                SnoozedBox(snoozedCount, onClick = { onNavigateToHistory(null, "snoozed") })
            }
        }
        item {
            CategoryFilterTabs(
                categories = categories,
                priorityColors = heroPriorityColors,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's digest", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = colors.textPrimary)
                    if (activeNotifications.isNotEmpty()) {
                        Text(
                            "Mark all read",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            fontFamily = Outfit,
                            modifier = Modifier
                                .clickable { NotificationRepository.resolveAllNotifications() }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                    }
                }
                Text(
                    if (isSmartGrouping) "Grouped" else "All",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (isSmartGrouping) {
            items(digestGroups) { group ->
                var showActions by remember { mutableStateOf(false) }
                val localContext = LocalContext.current
                val priorityGroups by viewModel.allPriorityGroups.collectAsState()

                DigestCard(
                    group = group,
                    onTap = { showActions = true },
                    onResolve = {
                        NotificationRepository.resolveGroupNotifications(group.notifications.map { it.id })
                    }
                )

                if (showActions) {
                    ContextualActionsSheet(
                        notification = group.notifications.first(),
                        priorityGroups = priorityGroups,
                        onDismiss = { showActions = false },
                        onAction = { action ->
                            when {
                                action == "open_app" -> {
                                    launchApp(localContext, group.packageName)
                                    showActions = false
                                }
                                action == "resolve" -> {
                                    NotificationRepository.resolveGroupNotifications(group.notifications.map { it.id })
                                    showActions = false
                                }
                                action.startsWith("change_priority:") -> {
                                    val newPriority = action.removePrefix("change_priority:")
                                    group.notifications.forEach { NotificationRepository.changePriority(it.id, newPriority) }
                                    showActions = false
                                }
                                action.startsWith("snooze:") -> {
                                    val duration = action.removePrefix("snooze:").toLongOrNull() ?: 3600000L
                                    group.notifications.forEach { NotificationRepository.snoozeNotification(it.id, duration) }
                                    showActions = false
                                }
                                action.startsWith("ignore_category:") -> {
                                    val duration = action.removePrefix("ignore_category:").toLongOrNull() ?: 0L
                                    viewModel.muteCategory(group.category, duration)
                                    showActions = false
                                }
                            }
                        }
                    )
                }
            }
        } else {
            items(flatNotifications) { notif ->
                var showActions by remember { mutableStateOf(false) }
                val localContext = LocalContext.current
                val priorityGroups by viewModel.allPriorityGroups.collectAsState()
                val color = ColorUtils.parseHexColor(
                    heroPriorityColors[notif.priority.lowercase()] ?: "#94a3b8"
                )

                SingleNotifCard(
                    notification = notif,
                    color = color,
                    onLongPress = { showActions = true }
                )

                if (showActions) {
                    ContextualActionsSheet(
                        notification = notif,
                        priorityGroups = priorityGroups,
                        onDismiss = { showActions = false },
                        onAction = { action ->
                            when {
                                action == "open_app" -> {
                                    launchApp(context, notif.packageName)
                                    showActions = false
                                }
                                action == "resolve" -> {
                                    NotificationRepository.resolveNotification(notif.id)
                                    showActions = false
                                }
                                action == "unresolve" -> {
                                    NotificationRepository.unresolveNotification(notif.id)
                                    showActions = false
                                }
                                action.startsWith("change_priority:") -> {
                                    val newPriority = action.removePrefix("change_priority:")
                                    NotificationRepository.changePriority(notif.id, newPriority)
                                    showActions = false
                                }
                                action.startsWith("snooze:") -> {
                                    val duration = action.removePrefix("snooze:").toLongOrNull() ?: 3600000L
                                    NotificationRepository.snoozeNotification(notif.id, duration)
                                    showActions = false
                                }
                                action.startsWith("ignore_category:") -> {
                                    val duration = action.removePrefix("ignore_category:").toLongOrNull() ?: 0L
                                    viewModel.muteCategory(notif.priority.lowercase(), duration)
                                    showActions = false
                                }
                            }
                        }
                    )
                }
            }
        }

        // Action Center: Muted & Disabled Categories
        val mutedGroups = allPriorityGroups.filter { it.ignoreUntil > System.currentTimeMillis() }
        val disabledGroups = allPriorityGroups.filter { !it.isEnabled }
        
        if (mutedGroups.isNotEmpty() || disabledGroups.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("Action center", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = colors.textPrimary)
                Text("Management for muted or disabled streams", fontSize = 11.sp, color = colors.textSecondary)
                Spacer(Modifier.height(12.dp))
            }
            
            items(mutedGroups) { group ->
                ManagementRow(
                    label = group.label,
                    status = "Muted",
                    color = ColorUtils.parseHexColor(group.colour, Color.Gray),
                    onAction = { viewModel.unmuteCategory(group.key) },
                    actionLabel = "Unmute"
                )
            }
            
            items(disabledGroups) { group ->
                ManagementRow(
                    label = group.label,
                    status = "Disabled",
                    color = ColorUtils.parseHexColor(group.colour, Color.Gray).copy(alpha = 0.4f),
                    onAction = { viewModel.updatePriorityEnabled(group.key, true) },
                    actionLabel = "Enable"
                )
            }
        }
    }
}

@Composable
fun ManagementRow(
    label: String,
    status: String,
    color: Color,
    onAction: () -> Unit,
    actionLabel: String
) {
    val colors = LocalHoneyJarColors.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary)
                    Text(status, fontSize = 11.sp, color = colors.textSecondary)
                }
            }
            Text(
                actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction() }.padding(8.dp)
            )
        }
    }
}

@Composable
fun HeaderSection(autoBackupFrequency: String, onBackupClick: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val colors = LocalHoneyJarColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "HoneyJar",
            fontWeight = FontWeight.Black,
            fontSize = 31.sp,
            fontFamily = PlayfairDisplay,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary
        )
        Box(Modifier.padding(top = 12.dp)) { // Visual offset increased to 12dp to match the large italic font center
            AutoBackupStatusPill(autoBackupFrequency, onBackupClick)
        }
    }
}

@Composable
fun AutoBackupStatusPill(frequency: String, onClick: () -> Unit) {
    val isOff = frequency == "off"
    val color = if (isOff) Color(0xFFEF4444) else Color(0xFF22C55E) // Red vs Green
    val text = if (isOff) "Auto Backup Off" else "Auto Backup On"
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = Outfit)
        }
    }
}

@Composable
fun HeroCard(displayNotifications: List<HoneyNotification>, allNotifications: List<HoneyNotification>, priorityColors: Map<String, String>, snoozedCount: Int, onReviewClick: () -> Unit = {}) {
    val totalCount = displayNotifications.size
    val activeCount = allNotifications.filter { it.priority.lowercase() == "urgent" && !it.isResolved }.size
    val colors = LocalHoneyJarColors.current

    GlassCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radial Ring
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val arcs = buildArcs(displayNotifications, priorityColors)
                    val ringTrack = colors.textPrimary.copy(alpha = 0.07f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = ringTrack, radius = size.minDimension / 2.5f, style = Stroke(width = 16f))
                        arcs.forEach { arc ->
                            drawArc(
                                color = arc.color,
                                startAngle = arc.startAngle - 90f,
                                sweepAngle = (arc.sweepAngle - 2f).coerceAtLeast(0.1f),
                                useCenter = false,
                                style = Stroke(width = 16f, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val countText = if (totalCount >= 10000) "${totalCount / 1000}k+" else totalCount.toString()
                        val countFontSize = when {
                            totalCount >= 10000 -> 16.sp
                            totalCount >= 1000  -> 18.sp
                            else                -> 23.sp
                        }
                        Text(countText, fontSize = countFontSize, fontWeight = FontWeight.Black, color = colors.textPrimary)
                        Text("today", fontSize = 9.sp, color = colors.textPrimary.copy(0.38f))
                    }
                }

                // Category Bars
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    // Show top active categories, ensuring we don't skip the requested core ones
                    val allCats = priorityColors.keys.toList()
                    val displayCats = allCats.filter { cat -> 
                        displayNotifications.any { it.priority.lowercase() == cat } 
                    }.take(8).toMutableList()
                    
                    // Always try to include important filters if they exist in schema and have data
                    val important = listOf("urgent", "messages", "email", "calendar", "calls", "social", "finance", "shopping")
                    important.forEach { imp ->
                        if (allCats.contains(imp) && !displayCats.contains(imp) && displayNotifications.any { it.priority.lowercase() == imp }) {
                            if (displayCats.size >= 8) displayCats.removeAt(displayCats.size - 1)
                            displayCats.add(imp)
                        }
                    }

                    displayCats.forEach { category ->
                        val hex = priorityColors[category] ?: "#94a3b8"
                        val count = displayNotifications.count { it.priority.lowercase() == category }
                        val color = ColorUtils.parseHexColor(hex)
                        val progress = if (totalCount > 0) count.toFloat() / totalCount else 0f

                        Row(modifier = Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(6.dp).background(color, CircleShape))
                            Text(
                                category.replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.width(60.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Box(Modifier.weight(1f).height(3.dp).background(colors.textPrimary.copy(0.07f), RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
                            }
                            Text(
                                count.toString(),
                                fontSize = 9.sp,
                                color = colors.textPrimary.copy(0.35f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.widthIn(min = 20.dp),
                                maxLines = 1
                            )
                        }
                    }
                    
                    // Snoozed Row
                    if (snoozedCount > 0) {
                        Row(modifier = Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Text(
                                "Snoozed",
                                fontSize = 9.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.width(60.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Box(Modifier.weight(1f).height(3.dp).background(colors.textPrimary.copy(0.07f), RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth((snoozedCount.toFloat() / totalCount.coerceAtLeast(1)).coerceAtMost(1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                            }
                            Text(
                                snoozedCount.toString(),
                                fontSize = 9.sp,
                                color = colors.textPrimary.copy(0.35f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.widthIn(min = 20.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.textPrimary.copy(0.07f), thickness = 1.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val footerText = if (activeCount > 0) "Action required on $activeCount alerts" else "No urgent alerts at this moment"
                val alertRed = Color(0xFFFF1744) // Constant Alert Red
                val staticGrey = Color(0xFFA0A0A0) // Constant Grey
                
                val footerColor = if (activeCount > 0) alertRed else staticGrey
                val actionColor = alertRed // Persistent Red for both states
                
                Text(
                    footerText, 
                    fontSize = 10.sp, 
                    color = footerColor,
                    fontWeight = if (activeCount > 0) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = Outfit
                )
                Text(
                    "Review →", 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = actionColor, 
                    fontFamily = Outfit,
                    modifier = Modifier.clickable { onReviewClick() }.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

private fun categoryEmoji(category: String) = when (category.lowercase()) {
    "urgent"    -> "🚨"
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

@Composable
fun SnoozedBox(count: Int, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$count hidden in honey",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontFamily = Outfit
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "Review snoozed →",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = Outfit
            )
        }
    }
}

@Composable
fun CategoryFilterTabs(categories: List<String>, priorityColors: Map<String, String> = emptyMap(), selected: String?, onSelect: (String?) -> Unit) {
    val allItems = listOf(null) + categories
    val rows = allItems.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cat ->
                    val catColor = cat?.let { key ->
                        priorityColors[key]?.let { hex ->
                            ColorUtils.parseHexColor(hex, Color.Transparent).takeIf { it != Color.Transparent }
                        }
                    }
                    FilterTab(
                        label = cat?.replaceFirstChar { it.uppercase() } ?: "All",
                        emoji = cat?.let { categoryEmoji(it) } ?: "✨",
                        isActive = cat == selected,
                        categoryColor = catColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(cat) }
                    )
                }
                // Fill remaining slots in last row
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FilterTab(label: String, emoji: String, isActive: Boolean, categoryColor: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    val borderColor = when {
        isActive && categoryColor != null -> categoryColor
        isActive -> MaterialTheme.colorScheme.primary
        categoryColor != null -> categoryColor.copy(alpha = 0.4f)
        else -> colors.glassBorder
    }
    val bgColor = when {
        isActive && categoryColor != null -> categoryColor.copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.primary.copy(0.15f)
        else -> colors.itemBg
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isActive) 4.dp else 2.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isActive && categoryColor != null -> categoryColor
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> colors.textSecondary
                },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DigestCard(
    group: DigestGroup,
    onTap: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onResolve: () -> Unit
) {
    val colors = LocalHoneyJarColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        borderColor = group.color.copy(alpha = 0.8f),
        borderWidth = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(
                packageName = group.packageName,
                category = group.category,
                size = 36.dp,
                tintColor = group.color
            )

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.appLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        fontFamily = Outfit,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    Text(
                        TimeUtils.formatDuration(group.latestNotification.postTime),
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(0.6f),
                        fontFamily = Outfit,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    group.latestNotification.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    fontFamily = Outfit,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    group.category.replaceFirstChar { it.uppercase() },
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    fontFamily = Outfit,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier.background(group.color.copy(0.15f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    group.notifications.size.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = group.color,
                    fontFamily = Outfit
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleNotifCard(
    notification: HoneyNotification,
    color: Color,
    onLongPress: () -> Unit = {}
) {
    val colors = LocalHoneyJarColors.current
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        borderColor = color.copy(alpha = 0.6f),
        borderWidth = 3.dp  // thinner than DigestCard — less visual weight per item
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(
                packageName = notification.packageName,
                category = notification.priority,
                size = 32.dp,
                tintColor = color
            )

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        AppLabelCache.get(notification.packageName, context),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        fontFamily = Outfit,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    Text(
                        TimeUtils.formatDuration(notification.postTime),
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(0.6f),
                        fontFamily = Outfit,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    notification.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    fontFamily = Outfit,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (notification.text.isNotBlank()) {
                    Text(
                        notification.text,
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(0.8f),
                        fontFamily = Outfit,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Category colour dot — replaces the count badge
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

data class CategoryArc(val category: String, val color: Color, val startAngle: Float, val sweepAngle: Float)
data class DigestGroup(
    val category: String,
    val packageName: String,
    val appLabel: String,
    val color: Color,
    val notifications: List<HoneyNotification>
) {
    val latestNotification get() = notifications.first()
}

private fun buildArcs(notifications: List<HoneyNotification>, priorityColors: Map<String, String>): List<CategoryArc> {
    val total = notifications.size.coerceAtLeast(1)
    var currentAngle = 0f
    return priorityColors.entries.mapNotNull { (key, hex) ->
        val count = notifications.count { it.priority.lowercase() == key }
        if (count == 0) return@mapNotNull null
        val sweep = (count.toFloat() / total) * 360f
        val color = ColorUtils.parseHexColor(hex)
        val arc = CategoryArc(key, color, currentAngle, sweep)
        currentAngle += sweep
        arc
    }
}

private fun resolvePriorityColors(
    notifications: List<HoneyNotification>,
    configuredPriorityColors: Map<String, String>
): Map<String, String> {
    if (configuredPriorityColors.isNotEmpty()) {
        val grouped = notifications.groupBy { it.priority.lowercase() }
        val orderedKeys = configuredPriorityColors.keys.filter { grouped.containsKey(it.lowercase()) }
        if (orderedKeys.isNotEmpty()) {
            return orderedKeys.associateWith { configuredPriorityColors[it] ?: defaultColorForCategory(it) }
        }

        return configuredPriorityColors
    }

    val liveCategories = notifications
        .map { it.priority.lowercase() }
        .distinct()
        .take(6)

    if (liveCategories.isNotEmpty()) {
        return liveCategories.associateWith { category -> defaultColorForCategory(category) }
    }

    return linkedMapOf(
        NotificationCategories.URGENT    to defaultColorForCategory(NotificationCategories.URGENT),
        NotificationCategories.MESSAGES  to defaultColorForCategory(NotificationCategories.MESSAGES),
        NotificationCategories.SOCIAL    to defaultColorForCategory(NotificationCategories.SOCIAL),
        NotificationCategories.EMAIL     to defaultColorForCategory(NotificationCategories.EMAIL),
        NotificationCategories.CALENDAR  to defaultColorForCategory(NotificationCategories.CALENDAR),
        NotificationCategories.CALLS     to defaultColorForCategory(NotificationCategories.CALLS),
        NotificationCategories.WEATHER   to defaultColorForCategory(NotificationCategories.WEATHER),
        NotificationCategories.TRAVEL    to defaultColorForCategory(NotificationCategories.TRAVEL),
        NotificationCategories.FINANCE   to defaultColorForCategory(NotificationCategories.FINANCE),
        NotificationCategories.SHOPPING  to defaultColorForCategory(NotificationCategories.SHOPPING),
        NotificationCategories.MEDIA     to defaultColorForCategory(NotificationCategories.MEDIA),
        NotificationCategories.SECURITY  to defaultColorForCategory(NotificationCategories.SECURITY),
        NotificationCategories.CONNECTED to defaultColorForCategory(NotificationCategories.CONNECTED),
        NotificationCategories.UPDATES   to defaultColorForCategory(NotificationCategories.UPDATES),
        NotificationCategories.PHOTOS    to defaultColorForCategory(NotificationCategories.PHOTOS),
        NotificationCategories.SYSTEM    to defaultColorForCategory(NotificationCategories.SYSTEM)
    )
}

private fun defaultColorForCategory(category: String): String {
    return when (category.lowercase()) {
        NotificationCategories.URGENT    -> "#ef4444"
        NotificationCategories.MESSAGES  -> "#3b82f6"
        NotificationCategories.SOCIAL    -> "#ec4899"
        NotificationCategories.EMAIL     -> "#a855f7"
        NotificationCategories.CALENDAR  -> "#f59e0b"
        NotificationCategories.CALLS     -> "#10b981"
        NotificationCategories.WEATHER   -> "#38bdf8"
        NotificationCategories.TRAVEL    -> "#f97316"
        NotificationCategories.FINANCE   -> "#84cc16"
        NotificationCategories.SHOPPING  -> "#f43f5e"
        NotificationCategories.MEDIA     -> "#8b5cf6"
        NotificationCategories.SECURITY  -> "#f97316"
        NotificationCategories.CONNECTED -> "#06b6d4"
        NotificationCategories.UPDATES   -> "#22c55e"
        NotificationCategories.PHOTOS    -> "#f472b6"
        NotificationCategories.SYSTEM    -> "#94a3b8"
        else -> "#94a3b8"
    }
}

private fun buildDigestGroups(notifications: List<HoneyNotification>, priorityColors: Map<String, String>, context: Context): List<DigestGroup> {
    val groupedByCategory = notifications.groupBy { it.priority.lowercase() }
    
    return groupedByCategory.flatMap { (category, catNotifs) ->
        val color = ColorUtils.parseHexColor(priorityColors[category] ?: defaultColorForCategory(category))
        
        // Group by package within each category
        catNotifs.groupBy { it.packageName }.map { (pkg, pkgNotifs) ->
            DigestGroup(
                category = category,
                packageName = pkg,
                appLabel = AppLabelCache.get(pkg, context),
                color = color,
                notifications = pkgNotifs.sortedByDescending { it.postTime }
            )
        }
    }.sortedByDescending { it.latestNotification.postTime }
}

private fun buildSummaryTitle(group: List<HoneyNotification>): String {
    val category = group.first().priority.lowercase()
    return when (category) {
        "messages" -> {
            val names = group.map { it.title.substringBefore(":").trim() }.distinct().take(2)
            when {
                names.size == 1 -> if (group.size > 1) "${names[0]} (+${group.size - 1})" else names[0]
                names.size > 1 -> if (group.size > 2) "${names[0]}, ${names[1]} +${group.size - 2}" else "${names[0]}, ${names[1]}"
                else -> "${group.size} messages"
            }
        }
        "email" -> {
            val subjects = group.map { it.title.take(40) }.distinct().take(2)
            if (group.size > 2) "${subjects.joinToString(", ")} +${group.size - 2}" else subjects.joinToString(", ")
        }
        else -> {
            val latest = group.maxByOrNull { it.postTime } ?: group.first()
            latest.title.trim().ifBlank { latest.text.take(60).trim() }
        }
    }
}

private fun buildSourceApps(group: List<HoneyNotification>, context: Context): String {
    return group.map { it.packageName }.distinct().take(2)
        .joinToString(", ") { AppLabelCache.get(it, context) }
}

