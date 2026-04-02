package com.honeyjar.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import com.honeyjar.app.ui.theme.HoneyJarColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.foundation.clickable
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay

fun areAllPermissionsGranted(context: Context): Boolean {
    val notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val battery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    val storage = if (android.os.Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val postNotifications = if (android.os.Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return notificationAccess && battery && storage && postNotifications
}

enum class OnboardingMode {
    Full,
    PermissionsOnly
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(mode: OnboardingMode = OnboardingMode.Full, onFinished: () -> Unit) {
    if (mode == OnboardingMode.PermissionsOnly) {
        PermissionsIntroPage(
            onNext = onFinished,
            onSkip = onFinished,
            showSkip = false,
            permissionsOnly = true
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false // Disabled swipe as requested
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    onSkip = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
                1 -> PermissionsIntroPage(
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    onSkip = onFinished,
                    showSkip = true,
                    permissionsOnly = false
                )
                2 -> ProPricingPage(onFinished = onFinished)
            }
        }

    }
}

@Composable
fun PaginationDots(selectedIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        repeat(3) { i ->
            val alpha by animateFloatAsState(if (selectedIndex == i) 1f else 0.3f)
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
fun WelcomePage(onNext: () -> Unit, onSkip: () -> Unit) {
    val scrollState = rememberScrollState()
    val colors = LocalHoneyJarColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text("Skip", color = colors.textSecondary)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text("🍯", fontSize = 40.sp)
            Text("Welcome to", fontSize = 18.sp, fontWeight = FontWeight.Normal, color = colors.textPrimary, fontFamily = Outfit)
            Text("HoneyJar", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontFamily = PlayfairDisplay, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            
            Spacer(Modifier.height(4.dp))
            Text(
                "Your smart notification hub — collect, organise, and act on every buzz, all in one place.",
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
                fontFamily = Outfit
            )
            
            Spacer(Modifier.height(16.dp))
            
            OnboardingFeatureItem("⚡", "Priority Alerts", "Focus on what matters most.")
            Spacer(Modifier.height(8.dp))
            OnboardingFeatureItem("📂", "Smart History", "Categorized and searchable context.")
            Spacer(Modifier.height(8.dp))
            OnboardingFeatureItem("🔐", "Stealth Mode", "Private and secure notification data.")
        }
        
        Spacer(Modifier.height(16.dp))
        PaginationDots(0)
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(32.dp)
        ) {
            Text("Get Started →", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun OnboardingFeatureItem(icon: String, title: String, desc: String) {
    val colors = LocalHoneyJarColors.current
    GlassCard(Modifier.fillMaxWidth().height(56.dp), borderColor = colors.glassBorder) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(colors.itemBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 18.sp)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary, fontFamily = Outfit)
                Text(desc, fontSize = 9.sp, color = colors.textSecondary, fontFamily = Outfit)
            }
        }
    }
}

@Composable
fun PermissionsIntroPage(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    showSkip: Boolean = true,
    permissionsOnly: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalHoneyJarColors.current
    val scrollState = rememberScrollState()

    var notificationAccessGranted by remember { mutableStateOf(false) }
    var batteryGranted by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(false) }
    var postNotificationsGranted by remember { mutableStateOf(false) }

    fun updateStates() {
        notificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        batteryGranted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        storageGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        postNotificationsGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) { updateStates() }

    val allGranted = notificationAccessGranted && batteryGranted && storageGranted && postNotificationsGranted

    LaunchedEffect(allGranted, permissionsOnly) {
        if (permissionsOnly && allGranted) {
            onNext()
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) updateStates()
    }

    val postNotifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) updateStates()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (showSkip) {
                    TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.TopEnd)) {
                        Text("Skip", color = colors.textSecondary)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text("🔑", fontSize = 40.sp)
            Text("A few", fontSize = 24.sp, fontWeight = FontWeight.Normal, color = colors.textPrimary, fontFamily = Outfit)
            Text("permissions", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontFamily = PlayfairDisplay)
            
            Spacer(Modifier.height(4.dp))
            Text(
                "HoneyJar needs these to do its job. You can always change them later in Settings.",
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontFamily = Outfit
            )
            
            Spacer(Modifier.height(16.dp))
            
            if (!permissionsOnly || !notificationAccessGranted) {
                PermissionGrantItem("🔔", "Notification Access", "The main engine - required to gather your alerts locally.", notificationAccessGranted) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (android.os.Build.VERSION.SDK_INT >= 33 && (!permissionsOnly || !postNotificationsGranted)) {
                PermissionGrantItem("📣", "Notification Status", "Show the live capture count in your status bar.", postNotificationsGranted) {
                    postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!permissionsOnly || !batteryGranted) {
                PermissionGrantItem("🔋", "Battery Optimisation", "Prevent system from killing our hub.", batteryGranted) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!permissionsOnly || !storageGranted) {
                PermissionGrantItem("💾", "Storage Access", "Save and restore backups.", storageGranted) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        storageLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (permissionsOnly && allGranted) {
                Text(
                    text = "All permissions are ready. Returning to Home...",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = Outfit,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        if (!permissionsOnly) {
            PaginationDots(1)
        }
        Button(
            onClick = onNext,
            enabled = !permissionsOnly || allGranted,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(32.dp)
        ) {
            val buttonText = when {
                permissionsOnly && allGranted -> "Go to Home ->"
                permissionsOnly -> "Grant all permissions to continue"
                allGranted -> "Continue ->"
                else -> "Continue anyway ->"
            }
            Text(buttonText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
        }
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text("Grant later in Settings", color = colors.textSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PermissionGrantItem(icon: String, title: String, desc: String, isGranted: Boolean, onClick: () -> Unit) {
    val colors = LocalHoneyJarColors.current
    val accentColor = MaterialTheme.colorScheme.primary
    val successColor = Color(0xFF4CAF50)

    GlassCard(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick), 
        borderColor = if (isGranted) successColor.copy(0.4f) else accentColor.copy(0.1f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(if (isGranted) successColor.copy(0.1f) else accentColor.copy(0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 18.sp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary, fontFamily = Outfit)
                Text(desc, fontSize = 8.sp, color = colors.textSecondary, fontFamily = Outfit)
            }
            Box(Modifier.size(24.dp).background(if (isGranted) successColor else accentColor.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = if (isGranted) Color.White else accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProPricingPage(onFinished: () -> Unit) {
    val scrollState = rememberScrollState()
    val colors = LocalHoneyJarColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text("Skip", color = colors.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("👑", fontSize = 40.sp)
            Text("Unlock", fontSize = 24.sp, fontWeight = FontWeight.Normal, color = colors.textPrimary, fontFamily = Outfit)
            Text("HoneyJar Pro", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontFamily = PlayfairDisplay)
            
            Spacer(Modifier.height(4.dp))
            Text("One-time payment. No subscription. Yours forever.", color = colors.textSecondary, fontSize = 12.sp, fontFamily = Outfit)
            
            Spacer(Modifier.height(12.dp))
            
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp), borderColor = MaterialTheme.colorScheme.primary.copy(0.4f)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("£", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = Outfit)
                        Text("4.99", fontSize = 36.sp, fontWeight = FontWeight.Black, color = colors.textPrimary, fontFamily = PlayfairDisplay, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    Text("one-time · no recurring fees", color = colors.textSecondary, fontSize = 12.sp, fontFamily = Outfit)
                    
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colors.glassBorder)
                    Spacer(Modifier.height(12.dp))
                    
                    val features = listOf(
                        "Unlimited notification history",
                        "Smart contextual search",
                        "Advanced priority grouping",
                        "Automated cloud backups",
                        "Custom theme customization"
                    )
                    
                    features.forEach { feature ->
                        Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(feature, color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        PaginationDots(2)
        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(32.dp)
        ) {
            Text("Unlock Pro · £4.99", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
        }
        TextButton(onClick = onFinished) {
            Text("Continue with free version", color = colors.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}
