package com.honeyjar.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.ui.viewmodels.AIChatMessage
import java.util.Calendar

object AIScreenTokens {
    val BubbleMaxWidth = 280.dp
    val CornerLarge = 20.dp
    val CornerSmall = 4.dp
    val ChipSpacing = 8.dp
    val ContentPadding = 20.dp
    val SpacingMedium = 16.dp
    val DotSize = 5.dp
    val InputElevation = 8.dp
}

@Composable
fun AIScreen(viewModel: MainViewModel) {
    val messages by viewModel.aiMessages.collectAsState()
    val isBusy by viewModel.isAIBusy.collectAsState()
    
    AIScreenContent(
        messages = messages,
        isBusy = isBusy,
        onSendMessage = { viewModel.sendAIPrompt(it) }
    )
}

@Composable
fun AIScreenContent(
    messages: List<AIChatMessage>,
    isBusy: Boolean,
    onSendMessage: (String) -> Unit
) {
    val colors = LocalHoneyJarColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(horizontal = AIScreenTokens.ContentPadding, vertical = 24.dp)) {
                Text(
                    "AI Honey Insights",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = colors.textPrimary
                )
                Text(
                    "Proactive intelligence for your focus",
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            }

            // Quick Chips
            val chips = getContextualChips()
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AIScreenTokens.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(AIScreenTokens.ChipSpacing)
            ) {
                items(chips) { chip ->
                    SuggestionChip(
                        label = chip,
                        onClick = { onSendMessage(chip) }
                    )
                }
            }

            Spacer(Modifier.height(AIScreenTokens.SpacingMedium))

            // Chat Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AIScreenTokens.ContentPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(AIScreenTokens.SpacingMedium)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg, onChipClick = onSendMessage)
                }
                if (isBusy) {
                    item(key = "thinking") {
                        ThinkingBubble()
                    }
                }
            }

            // Input Area
            ChatInput(onSend = onSendMessage, isBusy = isBusy)
        }
    }
}

@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = Outfit
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubble(message: AIChatMessage, onChipClick: (String) -> Unit) {
    val colors = LocalHoneyJarColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primary else colors.itemBg
        val textColor = if (message.isUser) Color.White else colors.textPrimary
        val shape = if (message.isUser) {
            RoundedCornerShape(
                topStart = AIScreenTokens.CornerLarge, 
                topEnd = AIScreenTokens.CornerSmall, 
                bottomStart = AIScreenTokens.CornerLarge, 
                bottomEnd = AIScreenTokens.CornerLarge
            )
        } else {
            RoundedCornerShape(
                topStart = AIScreenTokens.CornerSmall, 
                topEnd = AIScreenTokens.CornerLarge, 
                bottomStart = AIScreenTokens.CornerLarge, 
                bottomEnd = AIScreenTokens.CornerLarge
            )
        }

        Surface(
            color = bubbleColor,
            shape = shape,
            border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder) else null,
            modifier = Modifier.widthIn(max = AIScreenTokens.BubbleMaxWidth)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(14.dp),
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 20.sp,
                fontFamily = Outfit
            )
        }

        if (message.followUpChips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.followUpChips.forEach { chip ->
                    FollowUpChip(chip, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

@Composable
fun FollowUpChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = Outfit
        )
    }
}

@Composable
fun ThinkingBubble() {
    val colors = LocalHoneyJarColors.current
    Surface(
        color = colors.itemBg,
        shape = RoundedCornerShape(
            topStart = AIScreenTokens.CornerSmall, 
            topEnd = AIScreenTokens.CornerLarge, 
            bottomStart = AIScreenTokens.CornerLarge, 
            bottomEnd = AIScreenTokens.CornerLarge
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier.width(64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val delay = index * 200
                val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(delay)
                    ),
                    label = "alpha"
                )
                Box(
                    Modifier
                        .size(AIScreenTokens.DotSize)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha), 
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit, isBusy: Boolean) {
    var text by remember { mutableStateOf("") }
    val colors = LocalHoneyJarColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = AIScreenTokens.InputElevation,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.textPrimary.copy(alpha = 0.05f))
                    .border(1.dp, colors.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontFamily = Outfit),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                "Ask HoneyJar...", 
                                color = colors.textSecondary.copy(alpha = 0.5f), 
                                fontSize = 14.sp,
                                fontFamily = Outfit
                            )
                        }
                        innerTextField()
                    }
                )
            }

            IconButton(
                onClick = { 
                    if (text.isNotBlank() && !isBusy) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank() && !isBusy,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.textPrimary.copy(alpha = 0.1f),
                    disabledContentColor = colors.textSecondary.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun getContextualChips(): List<String> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> listOf("What did I miss?", "Today's priorities", "Morning catch-up", "Summarise night")
        hour in 12..17 -> listOf("What's urgent?", "Which apps distract me?", "Afternoon summary", "Flow check")
        else -> listOf("Summarise today", "Distraction report", "Tomorrow's focus", "Evening calm")
    }
}
