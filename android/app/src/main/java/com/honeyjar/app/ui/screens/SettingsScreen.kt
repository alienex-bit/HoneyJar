package com.honeyjar.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.repositories.SettingsRepository
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.HoneyJarThemeType
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.utils.NotificationCategories
import android.widget.Toast
import com.honeyjar.app.workers.AutoBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(currentTheme: HoneyJarThemeType, onThemeChange: (HoneyJarThemeType) -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val priorityGroups by viewModel.allPriorityGroups.collectAsState()
    
    val isSmartGrouping by SettingsRepository.isSmartGroupingEnabled(context).collectAsState(true)
    val isCaptureOngoing by SettingsRepository.isCaptureOngoingEnabled(context).collectAsState(false)
    val isProEnabled by SettingsRepository.isProEnabled(context).collectAsState(false)
    val isSecondaryAlerts by viewModel.secondaryAlertsEnabled.collectAsState()

    var showColorPickerFor by remember { mutableStateOf<PriorityGroupEntity?>(null) }
    var showSoundProfileFor by remember { mutableStateOf<PriorityGroupEntity?>(null) }
    var showAddCustomGroup by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Settings", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 31.sp, 
                    fontFamily = PlayfairDisplay,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    ),
                )
            }

            item {
                ProBannerHighFi(isProEnabled) {
                    scope.launch { SettingsRepository.setProEnabled(context, true) }
                }
            }

            item {
                SettingsGroup("APPEARANCE") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        ThemeSelectorGridHighFi(currentTheme, onThemeChange)
                    }
                }
            }
            
            item {
                SettingsGroup("NOTIFICATIONS") {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        SettingsToggleItem(Icons.Default.Notifications, "Smart grouping", "Bundle similar notifications", isSmartGrouping) { 
                            scope.launch { SettingsRepository.setSmartGrouping(context, it) }
                        }
                        SettingsToggleItem(Icons.Default.NotificationsActive, "Capture ongoing alerts", "Persistent notifications e.g. VPN, AdGuard, Android Auto", isCaptureOngoing) {
                            scope.launch { SettingsRepository.setCaptureOngoing(context, it) }
                        }
                        SettingsToggleItem(Icons.Default.Alarm, "Secondary alerts", "Re-alert for dismissed unresolved notifications", isSecondaryAlerts) {
                            viewModel.setSecondaryAlertsEnabled(it)
                        }
                    }
                }
            }

            item {
                SettingsGroup("PERMISSIONS") {
                    PermissionsSection()
                }
            }

            item {
                SettingsGroup("PRIORITY GROUPS & COLOURS") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        priorityGroups.forEach { group ->
                            val isSystemGroup = group.key in setOf(
                                NotificationCategories.URGENT,    NotificationCategories.MESSAGES,
                                NotificationCategories.SOCIAL,    NotificationCategories.EMAIL,
                                NotificationCategories.CALENDAR,  NotificationCategories.CALLS,
                                NotificationCategories.WEATHER,   NotificationCategories.TRAVEL,
                                NotificationCategories.FINANCE,   NotificationCategories.SHOPPING,
                                NotificationCategories.MEDIA,     NotificationCategories.SECURITY,
                                NotificationCategories.CONNECTED, NotificationCategories.UPDATES,
                                NotificationCategories.PHOTOS,    NotificationCategories.SYSTEM
                            )
                            PriorityRow(
                                group = group,
                                isDeletable = !isSystemGroup,
                                onColorClick = { showColorPickerFor = group },
                                onSoundClick = { showSoundProfileFor = group },
                                onEnabledChange = { viewModel.updatePriorityEnabled(group.key, it) },
                                onDelete = { viewModel.deletePriorityGroup(group.key) }
                            )
                        }
                        Text(
                            "+ Add custom group", 
                            color = MaterialTheme.colorScheme.primary, 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp).clickable { showAddCustomGroup = true }
                        )
                    }
                }
            }

            // TODO: re-enable SECURITY section once HIDE_CONTENT key is wired in SettingsRepository

            item {
                SettingsGroup("BACKUP & RESTORE") {
                    BackupSection(viewModel)
                }
                SettingsGroup("MAINTENANCE") {
                    MaintenanceSection(viewModel)
                }
            }
        }

        // Overlays
        showColorPickerFor?.let { group ->
            ColorPickerOverlay(
                group = group,
                onColorSelected = { hex ->
                    viewModel.updatePriorityColour(group.key, hex)
                    showColorPickerFor = null
                },
                onDismiss = { showColorPickerFor = null }
            )
        }

        showSoundProfileFor?.let { group ->
            SoundProfileSheet(
                group = group,
                onSoundSelected = { uri -> viewModel.updateSoundUri(group.key, uri) },
                onVibrationSelected = { pattern -> viewModel.updateVibrationPattern(group.key, pattern) },
                onSecondaryAlertEnabledChanged = { viewModel.updateSecondaryAlertEnabled(group.key, it) },
                onInitialDelayChanged = { viewModel.updateInitialAlertDelayMs(group.key, it) },
                onRepeatDelayChanged = { viewModel.updateSecondaryAlertDelayMs(group.key, it) },
                onDismiss = { showSoundProfileFor = null }
            )
        }
        
        if (showAddCustomGroup) {
            AddCustomGroupOverlay(
                onAdd = { label, color ->
                    viewModel.insertPriorityGroup(PriorityGroupEntity(
                        key = label.lowercase().replace(" ", "_"),
                        label = label,
                        colour = color,
                        isEnabled = true,
                        position = priorityGroups.size
                    ))
                    showAddCustomGroup = false
                },
                onDismiss = { showAddCustomGroup = false }
            )
        }

    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            fontFamily = Outfit,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        content()
    }
}

@Composable
fun ProBannerHighFi(isPro: Boolean, onUpgrade: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        gradient = colors.heroGradient
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isPro) "🍯" else "👑", fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (isPro) "HoneyJar Pro" else "Upgrade to Pro", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = colors.textPrimary)
                    Text(if (isPro) "You have unlocked all features!" else "Unlimited history, cloud backup & more", fontSize = 12.sp, color = colors.textPrimary.copy(0.7f))
                }
            }
            if (!isPro) {
                Surface(
                    color = Color(0xFFF59E0B),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onUpgrade() }
                ) {
                    Text(
                        "£4.99", 
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontFamily = PlayfairDisplay,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Text("ACTIVE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF22C55E))
            }
        }
    }
}

@Composable
fun ThemeSelectorGridHighFi(currentTheme: HoneyJarThemeType, onThemeChange: (HoneyJarThemeType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppearancePill(Modifier.weight(1f), "Dark Amber",    Color(0xFFFFB300), currentTheme == HoneyJarThemeType.DarkHoney)    { onThemeChange(HoneyJarThemeType.DarkHoney) }
            AppearancePill(Modifier.weight(1f), "Dark Obsidian", Color(0xFFA57EFF), currentTheme == HoneyJarThemeType.Midnight)      { onThemeChange(HoneyJarThemeType.Midnight) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppearancePill(Modifier.weight(1f), "Light Cream",   Color(0xFF8B6700), currentTheme == HoneyJarThemeType.LightCream)   { onThemeChange(HoneyJarThemeType.LightCream) }
            AppearancePill(Modifier.weight(1f), "Light Minimal", Color(0xFF0062D6), currentTheme == HoneyJarThemeType.LightMinimal) { onThemeChange(HoneyJarThemeType.LightMinimal) }
        }
    }
}

@Composable
fun AppearancePill(modifier: Modifier, name: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    // Border and background use the pill's own colour, not the current theme accent
    val borderColor = if (isSelected) color else colors.glassBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.12f) else colors.itemBg,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(color, CircleShape)
            )
            Text(
                name,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.7f),
                fontFamily = Outfit,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsToggleItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalHoneyJarColors.current
    
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(colors.itemBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        Switch(
            checked = checked, 
            onCheckedChange = { onCheckedChange(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFFF59E0B),
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
fun SettingsArrowItem(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val colors = LocalHoneyJarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(colors.itemBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        if (onClick != null) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary.copy(0.4f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else colors.itemBg,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = colors.textPrimary)
        }
    }
}

@Composable
fun PriorityRow(
    group: PriorityGroupEntity,
    isDeletable: Boolean,
    onColorClick: () -> Unit,
    onSoundClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalHoneyJarColors.current
    val groupColor = try { Color(android.graphics.Color.parseColor(group.colour)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
    val soundActive = group.soundUri != "off" || group.vibrationPattern != "off"
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onColorClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 28x28dp colour swatch (8dp radius)
        Box(
            Modifier
                .size(28.dp)
                .background(groupColor, RoundedCornerShape(8.dp))
                .clickable { onColorClick() }
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(group.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
            val hint = when (group.key) {
                "urgent"    -> "Fraud alerts, security warnings"
                "messages"  -> "WhatsApp, Telegram, Signal, SMS"
                "social"    -> "Twitter, Instagram, Reddit, LinkedIn"
                "email"     -> "Gmail, Outlook, EasilyDo Mail"
                "calendar"  -> "Samsung Calendar, Google Calendar"
                "calls"     -> "Dialler, call logs"
                "weather"   -> "Weather Pro, Windy, Lightning Tracker"
                "travel"    -> "Uber, Waze, Android Auto, Maps"
                "finance"   -> "Revolut, SafePal, Google Wallet"
                "shopping"  -> "Amazon, Next, Blue Light Card"
                "media"     -> "YouTube, CapCut, Google News, games"
                "security"  -> "AdGuard, Surfshark, Device Security"
                "connected" -> "Link to Windows, Galaxy Watch, SmartThings"
                "updates"   -> "Play Store, Galaxy Store, FOTA, Firefox"
                "photos"    -> "Camera, Google Photos, Samsung Cloud"
                "system"    -> "SystemUI, charging, alarms, OS noise"
                else -> "Custom apps group"
            }
            Text(hint, fontSize = 11.sp, color = colors.textSecondary)
        }

        // Sound & vibration icon — lit up when active
        IconButton(onClick = onSoundClick) {
            Icon(
                if (soundActive) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = "Sound profile",
                tint = if (soundActive) MaterialTheme.colorScheme.primary else colors.textSecondary.copy(0.4f),
                modifier = Modifier.size(18.dp)
            )
        }

        if (isDeletable) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.textSecondary.copy(0.4f), modifier = Modifier.size(18.dp))
            }
        }

        Switch(
            checked = group.isEnabled,
            onCheckedChange = { onEnabledChange(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = groupColor)
        )
    }
}

@Composable
fun PermissionsSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationAccess by remember { mutableStateOf(false) }
    var batteryOptimisation by remember { mutableStateOf(false) }
    var storageAccess by remember { mutableStateOf(false) }

    fun refresh() {
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        batteryOptimisation = pm.isIgnoringBatteryOptimizations(context.packageName)
        storageAccess = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refresh() }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        PermissionRow(
            icon = Icons.Default.Notifications,
            title = "Notification Access",
            description = "Required to capture alerts",
            isGranted = notificationAccess
        ) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermissionRow(
            icon = Icons.Default.BatteryFull,
            title = "Battery Optimisation",
            description = "Keeps capture running in background",
            isGranted = batteryOptimisation
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
        PermissionRow(
            icon = Icons.Default.Storage,
            title = "Storage Access",
            description = "For saving and restoring backups",
            isGranted = storageAccess
        ) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                storageLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHoneyJarColors.current
    val statusColor = if (isGranted) Color(0xFF22C55E) else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(colors.itemBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
            Text(description, fontSize = 12.sp, color = colors.textSecondary)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                if (isGranted) "Granted" else "Required",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

@Composable
fun BackupSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalHoneyJarColors.current
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val autoBackupFrequency by SettingsRepository.getAutoBackupFrequency(context).collectAsState("off")
    val lastBackupTime by SettingsRepository.getLastAutoBackupTime(context).collectAsState(0L)
    val freqOptions = listOf("Off" to "off", "Daily" to "daily", "Weekly" to "weekly", "Monthly" to "monthly")

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = viewModel.buildBackupJson(context)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false; pendingRestoreUri = null },
            title = { Text("Restore from backup?", fontFamily = PlayfairDisplay) },
            text = { Text("Settings and theme will be overwritten. Your existing notifications and priority groups will be kept — the backup will only add missing items.", fontFamily = Outfit, fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    val uri = pendingRestoreUri ?: return@Button
                    pendingRestoreUri = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                                ?: error("Could not read file")
                            viewModel.restoreBackupJson(context, json)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Restore complete", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Restore", fontFamily = Outfit) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false; pendingRestoreUri = null }) {
                    Text("Cancel", fontFamily = Outfit)
                }
            }
        )
    }

    Column {
        SettingsArrowItem(
            icon = Icons.Default.SaveAlt,
            title = "Export backup",
            subtitle = "Save a copy to Downloads",
            onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                exportLauncher.launch("honeyjar_backup_$date.json")
            }
        )
        SettingsArrowItem(
            icon = Icons.Default.FolderOpen,
            title = "Restore from backup",
            subtitle = "Merges backup into current data",
            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
        )

        Divider(Modifier.padding(vertical = 8.dp), color = colors.glassBorder)

        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Auto backup", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary, fontFamily = Outfit)
            }
            Text("Automatically saves a backup to Downloads. A backup also runs immediately when you select a frequency.", fontSize = 13.sp, color = colors.textSecondary, fontFamily = Outfit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                freqOptions.forEach { (label, key) ->
                    val isSelected = autoBackupFrequency == key
                    Surface(
                        onClick = {
                            scope.launch {
                                try {
                                    SettingsRepository.setAutoBackupFrequency(context, key)
                                    if (key != "off") {
                                        AutoBackupWorker.schedule(context, key)
                                        AutoBackupWorker.runNow(context)
                                    } else {
                                        AutoBackupWorker.schedule(context, "off")
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.e("HJ-FATAL", "Exception in backup trigger", t)
                                    Toast.makeText(context, "Failed: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else colors.itemBg,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary,
                                fontFamily = Outfit
                            )
                        }
                    }
                }
            }
            if (lastBackupTime > 0L) {
                val lastBackupText = remember(lastBackupTime) {
                    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastBackupTime))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                    Text("Last backup: $lastBackupText", fontSize = 12.sp, color = colors.textSecondary, fontFamily = Outfit)
                }
            }
        }
    }
}

@Composable
fun MaintenanceSection(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val colors = LocalHoneyJarColors.current
    var isRunning by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    var isColoursRunning by remember { mutableStateOf(false) }
    var coloursResultText by remember { mutableStateOf<String?>(null) }
    var showColoursConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Recategorise button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Recategorise history",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontFamily = Outfit
                )
                Text(
                    resultText ?: "Re-run categoriser over all stored notifications",
                    fontSize = 11.sp,
                    color = if (resultText != null) MaterialTheme.colorScheme.primary
                            else colors.textSecondary,
                    fontFamily = Outfit
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = !isRunning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Run", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
                }
            }
        }

        // Reset colour alignment button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Reset category colours",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontFamily = Outfit
                )
                Text(
                    coloursResultText ?: "Restore all category colours to their defaults",
                    fontSize = 11.sp,
                    color = if (coloursResultText != null) MaterialTheme.colorScheme.primary
                            else colors.textSecondary,
                    fontFamily = Outfit
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { showColoursConfirm = true },
                enabled = !isColoursRunning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isColoursRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Run", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Outfit)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Recategorise history?", fontFamily = PlayfairDisplay) },
            text = {
                Text(
                    "This will re-run the categoriser over all your stored notifications and update any that are miscategorised. It won't delete anything.",
                    fontFamily = Outfit,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    isRunning = true
                    resultText = null
                    scope.launch {
                        val (total, updated) = viewModel.recategorizeAll()
                        isRunning = false
                        resultText = if (updated == 0) "Already up to date ($total checked)"
                                     else "Done — $updated of $total updated"
                    }
                }) {
                    Text("Recategorise", fontFamily = Outfit, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", fontFamily = Outfit)
                }
            }
        )
    }

    if (showColoursConfirm) {
        AlertDialog(
            onDismissRequest = { showColoursConfirm = false },
            title = { Text("Reset category colours?", fontFamily = PlayfairDisplay) },
            text = {
                Text(
                    "This will restore all 16 built-in category colours to their defaults. Any colours you've customised will be overwritten. Custom categories are not affected.",
                    fontFamily = Outfit,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showColoursConfirm = false
                    isColoursRunning = true
                    coloursResultText = null
                    scope.launch {
                        val count = viewModel.resetCategoryColours()
                        isColoursRunning = false
                        coloursResultText = "Done — $count colours reset to defaults"
                    }
                }) {
                    Text("Reset colours", fontFamily = Outfit, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showColoursConfirm = false }) {
                    Text("Cancel", fontFamily = Outfit)
                }
            }
        )
    }
}

@Composable
fun SoundProfileSheet(
    group: PriorityGroupEntity,
    onSoundSelected: (String) -> Unit,
    onVibrationSelected: (String) -> Unit,
    onSecondaryAlertEnabledChanged: (Boolean) -> Unit,
    onInitialDelayChanged: (Long) -> Unit,
    onRepeatDelayChanged: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalHoneyJarColors.current
    val scope = rememberCoroutineScope()

    var currentSound by remember { mutableStateOf(group.soundUri) }
    var currentVibration by remember { mutableStateOf(group.vibrationPattern) }
    var secondaryEnabled by remember { mutableStateOf(group.secondaryAlertEnabled) }
    var initialDelay by remember { mutableStateOf(group.initialAlertDelayMs) }
    var repeatDelay by remember { mutableStateOf(group.secondaryAlertDelayMs) }

    val soundOptions = listOf("off" to "Off", "default" to "Default", "chime" to "Chime", "alert" to "Alert", "custom" to "Custom")
    val vibOptions = listOf("off" to "Off", "short" to "Short", "double" to "Double", "long" to "Long", "urgent" to "Urgent")

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                currentSound = uri.toString()
                onSoundSelected(uri.toString())
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Sound & Vibration", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = PlayfairDisplay, color = colors.textPrimary)
                Text(group.label, fontSize = 14.sp, color = colors.textSecondary, fontFamily = Outfit)
                Spacer(Modifier.height(24.dp))

                // Sound row
                Text("Sound", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontFamily = Outfit)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    soundOptions.forEach { (key, label) ->
                        val isSelected = if (key == "custom") currentSound !in listOf("off", "default", "chime", "alert") else currentSound == key
                        Surface(
                            onClick = {
                                if (key == "custom") {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Sound")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    }
                                    ringtoneLauncher.launch(intent)
                                } else {
                                    currentSound = key
                                    onSoundSelected(key)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else colors.itemBg,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary, fontFamily = Outfit)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Vibration row
                Text("Vibration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontFamily = Outfit)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vibOptions.forEach { (key, label) ->
                        val isSelected = currentVibration == key
                        Surface(
                            onClick = { currentVibration = key; onVibrationSelected(key) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else colors.itemBg,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary, fontFamily = Outfit)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Reminders section
                Divider(color = colors.glassBorder)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Reminders", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontFamily = Outfit)
                        Text("Alert if dismissed without resolving", fontSize = 11.sp, color = colors.textSecondary.copy(0.6f), fontFamily = Outfit)
                    }
                    Switch(
                        checked = secondaryEnabled,
                        onCheckedChange = { secondaryEnabled = it; onSecondaryAlertEnabledChanged(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFF59E0B), checkedThumbColor = Color.White)
                    )
                }

                if (secondaryEnabled) {
                    Spacer(Modifier.height(12.dp))
                    val initialOptions = listOf(
                        "5 min" to 300_000L, "15 min" to 900_000L, "30 min" to 1_800_000L,
                        "1 hr" to 3_600_000L, "2 hr" to 7_200_000L
                    )
                    Text("First reminder after", fontSize = 12.sp, color = colors.textSecondary, fontFamily = Outfit)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        initialOptions.forEach { (label, ms) ->
                            val isSelected = initialDelay == ms
                            Surface(
                                onClick = { initialDelay = ms; onInitialDelayChanged(ms) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else colors.itemBg,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary, fontFamily = Outfit)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val repeatOptions = listOf(
                        "Off" to 0L, "30 min" to 1_800_000L, "1 hr" to 3_600_000L,
                        "2 hr" to 7_200_000L, "4 hr" to 14_400_000L
                    )
                    Text("Repeat reminder", fontSize = 12.sp, color = colors.textSecondary, fontFamily = Outfit)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeatOptions.forEach { (label, ms) ->
                            val isSelected = repeatDelay == ms
                            Surface(
                                onClick = { repeatDelay = ms; onRepeatDelayChanged(ms) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else colors.itemBg,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary, fontFamily = Outfit)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                // Test button
                Button(
                    onClick = {
                        scope.launch {
                            if (currentSound != "off") {
                                try {
                                    val uri = when (currentSound) {
                                        "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                        "chime" -> Uri.parse("android.resource://${context.packageName}/raw/sound_chime")
                                        "alert" -> Uri.parse("android.resource://${context.packageName}/raw/sound_alert")
                                        else -> Uri.parse(currentSound)
                                    }
                                    RingtoneManager.getRingtone(context, uri)?.play()
                                } catch (_: Exception) {}
                            }
                            if (currentVibration != "off") {
                                val pattern = when (currentVibration) {
                                    "short" -> longArrayOf(0, 100)
                                    "double" -> longArrayOf(0, 100, 100, 100)
                                    "long" -> longArrayOf(0, 500)
                                    "urgent" -> longArrayOf(0, 100, 50, 100, 50, 300)
                                    else -> null
                                }
                                pattern?.let {
                                    val v = context.getSystemService(Vibrator::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        v?.vibrate(VibrationEffect.createWaveform(it, -1))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        v?.vibrate(it, -1)
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test", fontWeight = FontWeight.Bold, fontFamily = Outfit)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, fontFamily = Outfit)
                }
            }
        }
    }
}

@Composable
fun ColorPickerOverlay(group: PriorityGroupEntity, onColorSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable(enabled = false) { },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(android.graphics.Color.parseColor(group.colour)).copy(alpha = 0.6f))
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Choose colour", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = PlayfairDisplay, color = colors.textPrimary)
                Text("Editing: ${group.label}", fontSize = 13.sp, color = colors.textSecondary, fontFamily = Outfit)

                Spacer(Modifier.height(20.dp))

                // Show currently selected colour
                val currentHex = group.colour
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(
                                try { Color(android.graphics.Color.parseColor(currentHex)) }
                                catch (_: Exception) { MaterialTheme.colorScheme.primary },
                                CircleShape
                            )
                    )
                    Text(
                        "Current: $currentHex",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        fontFamily = Outfit
                    )
                }

                // 30 swatches covering all 16 category defaults + extras, in 6-column grid
                val swatches = listOf(
                    // Reds / pinks
                    "#ef4444", "#f43f5e", "#ec4899", "#f472b6", "#d946ef", "#a855f7",
                    // Purples / blues
                    "#8b5cf6", "#6366f1", "#3b82f6", "#0062d6", "#06b6d4", "#38bdf8",
                    // Greens
                    "#10b981", "#22c55e", "#84cc16", "#a3e635", "#eab308", "#f59e0b",
                    // Oranges / reds
                    "#f97316", "#fb923c", "#f97316", "#dc2626", "#b91c1c", "#7f1d1d",
                    // Neutrals / slate
                    "#94a3b8", "#64748b", "#475569", "#334155", "#1e293b", "#0f172a"
                )
                val cols = 6

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    swatches.chunked(cols).forEach { rowSwatches ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowSwatches.forEach { hex ->
                                val isSelected = hex.equals(currentHex, ignoreCase = true)
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .background(
                                            try { Color(android.graphics.Color.parseColor(hex)) }
                                            catch (_: Exception) { Color.Gray },
                                            CircleShape
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { onColorSelected(hex) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.glassBorder)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontFamily = Outfit)
                }
            }
        }
    }
}

@Composable
fun AddCustomGroupOverlay(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#3b82f6") }
    val hjColors = LocalHoneyJarColors.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.7f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("New Priority Group", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = PlayfairDisplay)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Group Name (e.g. Work)", fontFamily = Outfit) },
                    label = { Text("Name", fontFamily = Outfit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(24.dp))
                Text("Select Key Colour", fontSize = 12.sp, color = hjColors.textSecondary, fontFamily = Outfit)
                Spacer(Modifier.height(12.dp))

                val swatchColors = listOf("#ef4444", "#f97316", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899")
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    swatchColors.forEach { hex ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                .border(3.dp, if (selectedColor == hex) Color.White else Color.Transparent, CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", fontFamily = Outfit)
                    }
                    Button(
                        onClick = { onAdd(label.trim(), selectedColor) },
                        enabled = label.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add", fontFamily = Outfit)
                    }
                }
            }
        }
    }
}






