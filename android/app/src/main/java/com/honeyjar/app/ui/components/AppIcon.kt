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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.honeyjar.app.utils.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(
    packageName: String,
    category: String,
    size: Dp = 36.dp,
    tintColor: Color
) {
    val context = LocalContext.current

    // Load icon off the composition thread so the card layout is stable on first frame.
    // AppIconCache.get() draws a bitmap synchronously via PackageManager — doing it on
    // the main thread causes cards to measure at the wrong size and then snap.
    val icon = produceState<ImageBitmap?>(initialValue = AppIconCache.peek(packageName), key1 = packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.get(packageName, context)
            }
        }
    }.value

    // Always occupy the fixed size so the parent Row never changes height mid-render.
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
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
}
