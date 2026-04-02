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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.AIChatMessage
import com.honeyjar.app.ui.viewmodels.AiKeyState
import com.honeyjar.app.ui.viewmodels.MainViewModel
import java.util.Calendar

// ── Layout tokens ─────────────────────────────────────────────────────────────

private object Tokens {
    val BubbleMaxWidth = 300.dp
    val CornerLarge = 20.dp
    val CornerSmall = 4.dp
    val ContentPadding = 20.dp
    val SpacingMedium = 16.dp
    val DotSize = 5.dp
}

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun AIScreen(viewModel: MainViewModel) {
    val messages by viewModel.aiMessages.collectAsState()
    val isBusy by viewModel.isAIBusy.collectAsState()
    val keyState by viewModel.aiKeyState.collectAsState()
    val apiKey by viewModel.geminiApiKey.collectAsState()

    AIScreenContent(
        messages = messages,
        isBusy = isBusy,
        keyState = keyState,
        currentApiKey = apiKey,
        onSendMessage = { viewModel.sendAIPrompt(it) },
        onExecuteAction = { viewModel.executeAction(it) },
        onClearConversation = { viewModel.clearAiConversation() },
        viewModel = viewModel
    )
}

@Composable
private fun AIScreenContent(
    messages: List<AIChatMessage>,
    isBusy: Boolean,
    keyState: AiKeyState,
    currentApiKey: String,
    onSendMessage: (String) -> Unit,
    onExecuteAction: (String) -> Unit,
    onClearConversation: () -> Unit,
    viewModel: MainViewModel
) {
    val colors = LocalHoneyJarColors.current
    val listState = rememberLazyListState()
    var showKeyDialog by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        modifier = Modifier
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
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.ContentPadding, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AI Insights",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        "Powered by Gemini · your data stays on-device",
                        fontFamily = Outfit,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                // Menu: clear conversation + manage key
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.textSecondary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear conversation", fontFamily = Outfit) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick = { onClearConversation(); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (currentApiKey.isBlank()) "Add API key" else "Change API key", fontFamily = Outfit) },
                            leadingIcon = { Icon(Icons.Default.Key, null) },
                            onClick = { showKeyDialog = true; menuExpanded = false }
                        )
                    }
                }
            }

            // ── API key banner (shown when key is missing) ────────────────────
            AnimatedVisibility(visible = keyState == AiKeyState.MISSING) {
                ApiKeyBanner(onClick = { showKeyDialog = true })
            }

            // ── Contextual quick-chips ─────────────────────────────────────────
            if (keyState != AiKeyState.MISSING) {
                val chips = remember { getContextualChips() }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Tokens.ContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(chips) { chip ->
                        QuickChip(label = chip, onClick = { onSendMessage(chip) })
                    }
                }
                Spacer(Modifier.height(Tokens.SpacingMedium))
            }

            // ── Chat area ──────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = Tokens.ContentPadding,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingMedium)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onChipClick = { chip ->
                            // If the chip is an action tag execute it, otherwise send as prompt
                            if (chip.startsWith("ACTION:")) onExecuteAction(chip)
                            else onSendMessage(chip)
                        }
                    )
                }
                if (isBusy) {
                    item(key = "thinking") { ThinkingBubble() }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            ChatInput(
                onSend = onSendMessage,
                isBusy = isBusy,
                enabled = keyState != AiKeyState.MISSING
            )
        }
    }

    // ── API key dialog ────────────────────────────────────────────────────────
    if (showKeyDialog) {
        ApiKeyDialog(
            currentKey = currentApiKey,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                viewModel.saveGeminiApiKey(key)
                showKeyDialog = false
            }
        )
    }
}

// ── API key banner ─────────────────────────────────────────────────────────────

@Composable
private fun ApiKeyBanner(onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.ContentPadding, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "API key required",
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Tap to add your Google Gemini API key and unlock AI features",
                    fontFamily = Outfit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ── API key dialog ─────────────────────────────────────────────────────────────

@Composable
private fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text("Gemini API Key", fontFamily = Outfit, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your key is stored securely in DataStore on-device and is never included in backups or sent anywhere except the Gemini API.",
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("AIza...", fontFamily = Outfit) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (keyText.isNotBlank() && !keyText.startsWith("AIza")) {
                    Text(
                        "⚠ This doesn't look like a valid Google API key (typically starts with AIza)",
                        fontFamily = Outfit,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(keyText) },
                enabled = keyText.isNotBlank()
            ) {
                Text("Save", fontFamily = Outfit, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = Outfit)
            }
        }
    )
}

// ── Chat bubble ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubble(message: AIChatMessage, onChipClick: (String) -> Unit) {
    val colors = LocalHoneyJarColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primary else colors.itemBg
        val textColor = if (message.isUser) Color.White else colors.textPrimary
        val shape = if (message.isUser) {
            RoundedCornerShape(Tokens.CornerLarge, Tokens.CornerSmall, Tokens.CornerLarge, Tokens.CornerLarge)
        } else {
            RoundedCornerShape(Tokens.CornerSmall, Tokens.CornerLarge, Tokens.CornerLarge, Tokens.CornerLarge)
        }

        Surface(
            color = bubbleColor,
            shape = shape,
            border = if (!message.isUser)
                androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder) else null,
            modifier = Modifier.widthIn(max = Tokens.BubbleMaxWidth)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(14.dp),
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 21.sp,
                fontFamily = Outfit
            )
        }

        // Action / follow-up chips
        if (message.followUpChips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.followUpChips.forEach { chip ->
                    val isAction = message.pendingAction != null &&
                            message.followUpChips.indexOf(chip) == 0
                    FollowUpChip(
                        label = chip,
                        isAction = isAction,
                        onClick = {
                            if (isAction && message.pendingAction != null) {
                                onChipClick(message.pendingAction)
                            } else {
                                onChipClick(chip)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowUpChip(label: String, isAction: Boolean = false, onClick: () -> Unit) {
    val borderColor = if (isAction)
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val textColor = if (isAction)
        MaterialTheme.colorScheme.tertiary
    else
        MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isAction) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAction) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = Outfit
            )
        }
    }
}

// ── Quick chips (top of screen) ────────────────────────────────────────────────

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = Outfit,
            maxLines = 1
        )
    }
}

// ── Thinking bubble ────────────────────────────────────────────────────────────

@Composable
private fun ThinkingBubble() {
    val colors = LocalHoneyJarColors.current
    Surface(
        color = colors.itemBg,
        shape = RoundedCornerShape(Tokens.CornerSmall, Tokens.CornerLarge, Tokens.CornerLarge, Tokens.CornerLarge),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier.width(72.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 200)
                    ),
                    label = "alpha$index"
                )
                Box(
                    Modifier
                        .size(Tokens.DotSize)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
                )
            }
        }
    }
}

// ── Chat input ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInput(onSend: (String) -> Unit, isBusy: Boolean, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    val colors = LocalHoneyJarColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
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
                    .background(colors.textPrimary.copy(alpha = if (enabled) 0.05f else 0.02f))
                    .border(
                        1.dp,
                        colors.textPrimary.copy(alpha = if (enabled) 0.1f else 0.04f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (enabled) text = it },
                    textStyle = TextStyle(
                        color = colors.textPrimary.copy(alpha = if (enabled) 1f else 0.4f),
                        fontSize = 14.sp,
                        fontFamily = Outfit
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank() && !isBusy && enabled) {
                                onSend(text); text = ""
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                if (enabled) "Ask HoneyJar..." else "Add an API key to chat",
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
                    if (text.isNotBlank() && !isBusy && enabled) { onSend(text); text = "" }
                },
                enabled = text.isNotBlank() && !isBusy && enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.textPrimary.copy(alpha = 0.1f),
                    disabledContentColor = colors.textSecondary.copy(alpha = 0.3f)
                )
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Time-aware quick chips ─────────────────────────────────────────────────────

private fun getContextualChips(): List<String> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11  -> listOf("Morning catch-up", "What's urgent?", "What did I miss?", "Today's priorities")
        hour in 12..17 -> listOf("Which app distracts me most?", "Afternoon summary", "What's urgent?", "My response time")
        else           -> listOf("Summarise today", "Distraction report", "How was today?", "Evening wind-down")
    }
}
