package com.honeyjar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.honeyjar.app.ui.theme.LocalHoneyJarColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalHoneyJarColors.current.glassBorder,
    borderWidth: androidx.compose.ui.unit.Dp = 2.dp,
    gradient: Brush? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = if (gradient == null) LocalHoneyJarColors.current.itemBg else Color.Transparent
    ) {
        Box(
            modifier = if (gradient != null) Modifier.background(gradient) else Modifier
        ) {
            content()
        }
    }
}
