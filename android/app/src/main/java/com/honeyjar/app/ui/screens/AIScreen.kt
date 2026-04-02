package com.honeyjar.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.AIChatMessage
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.ui.viewmodels.MessageType
import java.util.Calendar

// ── Design tokens ─────────────────────────────────────────────────────────────

private object T {
    val CornerLarge = 20.dp
    val CornerSmall = 4.dp
    val Pad = 20.dp
    val BubbleMax = 310.dp
    val DotSize = 5.dp
}

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun AIScreen(viewModel: MainViewModel) {
    val messages by viewModel.aiMessages.collectAsState()
    val isBusy   by viewModel.isAIBusy.collectAsState()

    AIScreenContent(
        messages  = messages,
        isBusy    = isBusy,
        onSend    = { viewModel.sendAIPrompt(it) },
        onAction  = { viewModel.executeAction(it) },
        onClear   = { viewModel.clearAiConversation() }
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun AIScreenContent(
    messages : List<AIChatMessage>,
    isBusy   : Boolean,
    onSend   : (String) -> Unit,
    onAction : (String) -> Unit,
    onClear  : () -> Unit
) {
    val colors    = LocalHoneyJarColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.Pad, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI Insights",
                        fontFamily   = PlayfairDisplay,
                        fontWeight   = FontWeight.Black,
                        fontSize     = 32.sp,
                        color        = colors.textPrimary
                    )
                    Text(
                        "On-device · instant · no internet needed",
                        fontFamily = Outfit,
                        fontSize   = 12.sp,
                        color      = colors.textSecondary
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = colors.textSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text         = { Text("Clear conversation", fontFamily = Outfit) },
                            leadingIcon  = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick      = { onClear(); menuOpen = false }
                        )
                    }
                }
            }

            // Quick chips — time-aware
            val chips = remember { contextualChips() }
            LazyRow(
                contentPadding       = PaddingValues(horizontal = T.Pad),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips) { chip ->
                    QuickChip(chip) { onSend(chip) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Messages
            LazyColumn(
                state           = listState,
                modifier        = Modifier.weight(1f).fillMaxWidth(),
                contentPadding  = PaddingValues(horizontal = T.Pad, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    // Note: AnimatedVisibility inside keyed LazyColumn items causes
                    // a Compose SlotTable crash. Animation is applied via Modifier instead.
                    Bubble(msg) { chip ->
                        if (chip.startsWith("ACTION:")) onAction(chip)
                        else onSend(chip)
                    }
                }
                if (isBusy) {
                    item("thinking") { ThinkingBubble() }
                }
            }

            // Input
            ChatInput(onSend = onSend, isBusy = isBusy)
        }
    }
}

// ── Bubble ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(msg: AIChatMessage, onChipClick: (String) -> Unit) {
    val colors = LocalHoneyJarColors.current

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        if (msg.isUser) {
            // User bubble — solid primary
            Surface(
                color    = MaterialTheme.colorScheme.primary,
                shape    = RoundedCornerShape(T.CornerLarge, T.CornerSmall, T.CornerLarge, T.CornerLarge),
                modifier = Modifier.widthIn(max = T.BubbleMax)
            ) {
                Text(
                    msg.text,
                    modifier   = Modifier.padding(14.dp),
                    fontSize   = 14.sp,
                    color      = Color.White,
                    lineHeight = 21.sp,
                    fontFamily = Outfit
                )
            }
        } else {
            // Assistant bubble — style driven by MessageType
            val (bg, border, icon, iconTint) = bubbleStyle(msg.type)
            Surface(
                color    = bg,
                shape    = RoundedCornerShape(T.CornerSmall, T.CornerLarge, T.CornerLarge, T.CornerLarge),
                border   = androidx.compose.foundation.BorderStroke(1.dp, border),
                modifier = Modifier.widthIn(max = T.BubbleMax)
            ) {
                Column(Modifier.padding(14.dp)) {
                    // Type icon badge for non-normal messages
                    if (icon != null) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier              = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
                            Text(
                                typeBadgeLabel(msg.type),
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = iconTint,
                                fontFamily = Outfit
                            )
                        }
                    }
                    Text(
                        msg.text,
                        fontSize   = 14.sp,
                        color      = colors.textPrimary,
                        lineHeight = 21.sp,
                        fontFamily = Outfit
                    )
                }
            }

            // Follow-up chips
            if (msg.followUpChips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    msg.followUpChips.forEachIndexed { index, chip ->
                        // First chip is the primary action if pendingAction is set
                        val isAction = msg.pendingAction != null && index == 0 &&
                                       (chip.startsWith("Yes") || chip.startsWith("Resolve") ||
                                        chip.startsWith("Mute") || chip.startsWith("Snooze"))
                        FollowUpChip(chip, isAction) {
                            if (isAction && msg.pendingAction != null) onChipClick(msg.pendingAction)
                            else onChipClick(chip)
                        }
                    }
                }
            }
        }
    }
}

// ── Bubble styling ────────────────────────────────────────────────────────────

private data class BubbleStyle(
    val bg       : Color,
    val border   : Color,
    val icon     : ImageVector?,
    val iconTint : Color
)

@Composable
private fun bubbleStyle(type: MessageType): BubbleStyle {
    val colors = LocalHoneyJarColors.current
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        MessageType.INSIGHT -> BubbleStyle(
            bg       = scheme.primaryContainer.copy(alpha = 0.25f),
            border   = scheme.primary.copy(alpha = 0.3f),
            icon     = Icons.Default.Insights,
            iconTint = scheme.primary
        )
        MessageType.WARNING -> BubbleStyle(
            bg       = scheme.errorContainer.copy(alpha = 0.25f),
            border   = scheme.error.copy(alpha = 0.4f),
            icon     = Icons.Default.Warning,
            iconTint = scheme.error
        )
        MessageType.ACTION -> BubbleStyle(
            bg       = Color(0xFF1B5E20).copy(alpha = 0.15f),
            border   = Color(0xFF4CAF50).copy(alpha = 0.4f),
            icon     = Icons.Default.CheckCircle,
            iconTint = Color(0xFF4CAF50)
        )
        MessageType.NORMAL -> BubbleStyle(
            bg       = colors.itemBg,
            border   = colors.glassBorder,
            icon     = null,
            iconTint = Color.Unspecified
        )
    }
}

private fun typeBadgeLabel(type: MessageType) = when (type) {
    MessageType.INSIGHT -> "INSIGHT"
    MessageType.WARNING -> "ATTENTION"
    MessageType.ACTION  -> "DONE"
    MessageType.NORMAL  -> ""
}

// ── Chips ─────────────────────────────────────────────────────────────────────

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(20.dp),
        color   = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border  = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
            fontFamily = Outfit,
            maxLines   = 1
        )
    }
}

@Composable
private fun FollowUpChip(label: String, isAction: Boolean, onClick: () -> Unit) {
    val borderColor = if (isAction) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                      else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val textColor   = if (isAction) MaterialTheme.colorScheme.tertiary
                      else MaterialTheme.colorScheme.primary
    val bgColor     = if (isAction) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                      else Color.Transparent

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = bgColor,
        border  = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAction) {
                Icon(Icons.Default.Bolt, null, tint = textColor, modifier = Modifier.size(11.dp))
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = Outfit)
        }
    }
}

// ── Thinking bubble ───────────────────────────────────────────────────────────

@Composable
private fun ThinkingBubble() {
    val colors = LocalHoneyJarColors.current
    Surface(
        color    = colors.itemBg,
        shape    = RoundedCornerShape(T.CornerSmall, T.CornerLarge, T.CornerLarge, T.CornerLarge),
        border   = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier.width(72.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val t = rememberInfiniteTransition(label = "d$i")
                val a by t.animateFloat(
                    0.2f, 1f,
                    infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse, StartOffset(i * 200)),
                    label = "a$i"
                )
                Box(Modifier.size(T.DotSize).background(MaterialTheme.colorScheme.primary.copy(alpha = a), CircleShape))
            }
        }
    }
}

// ── Chat input ────────────────────────────────────────────────────────────────

@Composable
private fun ChatInput(onSend: (String) -> Unit, isBusy: Boolean) {
    var text   by remember { mutableStateOf("") }
    val colors = LocalHoneyJarColors.current

    Surface(
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier       = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        border         = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.textPrimary.copy(alpha = 0.05f))
                    .border(1.dp, colors.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            ) {
                BasicTextField(
                    value         = text,
                    onValueChange = { text = it },
                    textStyle     = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontFamily = Outfit),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction      = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (text.isNotBlank() && !isBusy) { onSend(text); text = "" } }
                    ),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Ask anything...", color = colors.textSecondary.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = Outfit)
                        inner()
                    }
                )
            }

            IconButton(
                onClick  = { if (text.isNotBlank() && !isBusy) { onSend(text); text = "" } },
                enabled  = text.isNotBlank() && !isBusy,
                colors   = IconButtonDefaults.iconButtonColors(
                    containerColor        = MaterialTheme.colorScheme.primary,
                    contentColor          = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.textPrimary.copy(alpha = 0.1f),
                    disabledContentColor  = colors.textSecondary.copy(alpha = 0.3f)
                )
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Time-aware quick chips ────────────────────────────────────────────────────

private fun contextualChips(): List<String> {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h in 5..11  -> listOf("Morning briefing", "What's urgent?", "What did I miss?", "Today's priorities")
        h in 12..17 -> listOf("Afternoon summary", "Which app distracts me most?", "What's unresolved?", "Focus help")
        else        -> listOf("Evening wind-down", "Summarise today", "Distraction report", "Resolve all pending")
    }
}
