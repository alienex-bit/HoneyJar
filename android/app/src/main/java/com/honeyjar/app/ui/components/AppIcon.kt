package com.honeyjar.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.honeyjar.app.utils.AppIconCache

@Composable
fun AppIcon(
    packageName: String,
    category: String,
    size: Dp = 36.dp,
    tintColor: Color
) {
    val context = LocalContext.current
    val icon = remember(packageName) { AppIconCache.get(packageName, context) }

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val fallbackIcon = when (category.lowercase()) {
            "urgent"   -> Icons.Default.Warning
            "messages" -> Icons.Default.Chat
            "email"    -> Icons.Default.Email
            "calendar" -> Icons.Default.CalendarToday
            "delivery" -> Icons.Default.LocalShipping
            "updates"  -> Icons.Default.SystemUpdate
            "system"   -> Icons.Default.Settings
            else       -> Icons.Default.Notifications
        }
        Box(
            modifier = Modifier
                .size(size)
                .background(tintColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
