package com.honeyjar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.models.HoneyNotification
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import com.honeyjar.app.utils.ColorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualActionsSheet(
    notification: HoneyNotification,
    priorityGroups: List<PriorityGroupEntity>,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val priority = notification.priority.lowercase()
    val context = LocalContext.current
    val colors = LocalHoneyJarColors.current

    val appLabel = remember(notification.packageName) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(notification.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            Log.w("ContextualActions", "Failed to get app label for ${notification.packageName}", e)
            notification.packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }

    var showPriorityMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(Modifier.padding(24.dp).padding(bottom = 32.dp)) {
            when {
                showPriorityMenu -> {
                    Text("Move to...", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.textPrimary)
                    Spacer(Modifier.height(16.dp))
                    priorityGroups.filter { it.isEnabled }.sortedBy { it.position }.forEach { group ->
                        val isCurrent = group.key.lowercase() == priority
                        val groupColor = ColorUtils.parseHexColor(group.colour, Color(0xFF2A2A2A))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction("change_priority:${group.key.lowercase()}") }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).background(groupColor, CircleShape))
                            Spacer(Modifier.width(14.dp))
                            Text(group.label, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                 color = colors.textPrimary, modifier = Modifier.weight(1f))
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                     tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showPriorityMenu = false }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text("← Back", color = colors.textSecondary)
                    }
                }

                else -> {
                    Text(notification.title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = colors.textPrimary)
                    Text(appLabel, fontSize = 12.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(24.dp))

                    ActionItem(Icons.Default.OpenInNew, "Open $appLabel") { onAction("open_app") }
                    Divider(Modifier.padding(vertical = 8.dp), color = colors.glassBorder)
                    val isSnoozed = notification.snoozeUntil > System.currentTimeMillis()
                    
                    if (notification.isResolved) {
                        ActionItem(androidx.compose.material.icons.Icons.Default.MarkEmailUnread, "Mark Unread") { onAction("unresolve") }
                    } else {
                        ActionItem(Icons.Default.CheckCircle, "Mark Read") { onAction("resolve") }
                    }
                    Divider(Modifier.padding(vertical = 8.dp), color = colors.glassBorder)
                    if (isSnoozed) {
                        ActionItem(Icons.Default.NotificationsActive, "Unsnooze") { onAction("unsnooze") }
                    } else {
                        ActionItem(Icons.Default.Schedule, "Snooze for 1h") { onAction("snooze:3600000") }
                    }
                    Divider(Modifier.padding(vertical = 8.dp), color = colors.glassBorder)
                    ActionItem(Icons.Default.Label, "Change Priority") { showPriorityMenu = true }
                }
            }
        }
    }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
    }
}
