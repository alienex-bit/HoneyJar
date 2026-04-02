package com.honeyjar.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyjar.app.ui.theme.Outfit
import com.honeyjar.app.ui.theme.PlayfairDisplay
import com.honeyjar.app.ui.viewmodels.MainViewModel
import com.honeyjar.app.ui.components.GlassCard
import com.honeyjar.app.ui.theme.LocalHoneyJarColors
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import java.util.*
import com.honeyjar.app.utils.AppIconCache
import com.honeyjar.app.utils.ColorUtils
import com.honeyjar.app.ui.viewmodels.AppGuiltEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Categories that are background noise — de-emphasised in breakdown
private val NOISE_CATEGORIES = setOf("system", "device", "security", "connected")

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val total by viewModel.totalCount.collectAsState()
    val actioned by viewModel.actionedCount.collectAsState()
    val priorityColors by viewModel.priorityColors.collectAsState()
    val barData by viewModel.barChartData.collectAsState()
    val categoryData by viewModel.categoryBreakdown.collectAsState()
    val heatmap by viewModel.heatmapData.collectAsState()
    val avgResponseMinutes by viewModel.avgResponseMinutes.collectAsState()
    val appBreakdown by viewModel.appBreakdown.collectAsState()
    val weeklyCount by viewModel.weeklyCount.collectAsState()
    val prevWeekCount by viewModel.prevWeekCount.collectAsState()

    val actionRate = if (total > 0) (actioned * 100 / total) else 0
    val weeklyTotal = barData.sum()

    var animationKey by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { animationKey++ }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) animationKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Title ─────────────────────────────────────────────────────────
        item {
            Text(
                "Statistics",
                fontWeight = FontWeight.Black,
                fontSize = 31.sp,
                fontFamily = PlayfairDisplay,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                style = androidx.compose.ui.text.TextStyle(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                )
            )
        }

        // ── 4 stat tiles ──────────────────────────────────────────────────
        item {
            StatsHero(total, weeklyTotal, actionRate, avgResponseMinutes, animationKey)
        }

        // ── Activity Trends (bar chart) ───────────────────────────────────
        item {
            SectionHeader("Activity Trends", "last 7 days")
            Spacer(Modifier.height(12.dp))
            BarChart(barData, animationKey)
        }

        // ── App Guilt Score ───────────────────────────────────────────────
        if (appBreakdown.isNotEmpty()) {
            item {
                SectionHeader("Noisiest Apps", "last 7 days")
                Spacer(Modifier.height(12.dp))
                AppGuiltSection(appBreakdown)
            }
        }

        // ── By Category ───────────────────────────────────────────────────
        item {
            SectionHeader("By Category", "all time")
            Spacer(Modifier.height(12.dp))
            CategoryBreakdown(categoryData, priorityColors)
        }

        // ── Heatmap ───────────────────────────────────────────────────────
        item {
            SectionHeader("Activity Heatmap", "when they arrive")
            Spacer(Modifier.height(12.dp))
            Heatmap(heatmap)
        }

        // ── Weekly insight banner ─────────────────────────────────────────
        item {
            WeeklyInsightBanner(weeklyCount, prevWeekCount, categoryData, heatmap, barData)
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, subtitle: String = "") {
    val primary = MaterialTheme.colorScheme.primary
    val colors = LocalHoneyJarColors.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.textPrimary, fontFamily = Outfit)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(28.dp).height(2.dp).background(primary, RoundedCornerShape(1.dp)))
        }
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                fontSize = 10.sp,
                color = colors.textSecondary.copy(0.6f),
                fontFamily = Outfit,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

// ── 4 stat tiles ──────────────────────────────────────────────────────────────

@Composable
fun StatsHero(total: Int, weeklyTotal: Int, actionRate: Int, avgResponseMinutes: Long?, animationKey: Int) {
    var displayedTotal by remember(animationKey) { mutableStateOf(0) }
    LaunchedEffect(animationKey, total) {
        if (total == 0) return@LaunchedEffect
        for (step in 1..10) {
            displayedTotal = (total * step / 10f).toInt()
            kotlinx.coroutines.delay(80L)
        }
        displayedTotal = total
    }

    val actionRateDisplay = if (actionRate == 0) "—" else "$actionRate%"
    val actionLabel = when {
        actionRate == 0  -> "none resolved yet"
        actionRate >= 80 -> "great engagement"
        actionRate >= 50 -> "of captured"
        actionRate >= 20 -> "of captured"
        else             -> "of captured"
    }

    val avgLabel = if (avgResponseMinutes != null) {
        when {
            avgResponseMinutes < 5   -> "very quick"
            avgResponseMinutes < 30  -> "after arrival"
            avgResponseMinutes < 120 -> "after arrival"
            else                     -> "after arrival"
        }
    } else "no data yet"

    Box(Modifier.height(200.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            item { StatTile("All time",    displayedTotal.toString(), "total captured") }
            item { StatTile("This week",   "$weeklyTotal",            "last 7 days") }
            item { StatTile("Action rate", actionRateDisplay,       actionLabel) }
            item {
                StatTile(
                    "Avg response",
                    if (avgResponseMinutes != null) formatResponseTime(avgResponseMinutes) else "—",
                    avgLabel
                )
            }
        }
    }
}

private fun formatResponseTime(minutes: Long): String = when {
    minutes < 60   -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
    else           -> "${minutes / 1440}d"
}

@Composable
fun StatTile(label: String, value: String, delta: String) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(3.dp).background(
                    Brush.horizontalGradient(listOf(primary, primary.copy(alpha = 0.4f))),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
            )
            Column(Modifier.padding(12.dp)) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontFamily = Outfit)
                Text(label, fontSize = 10.sp, color = colors.textSecondary, fontFamily = Outfit)
                Text(delta, fontSize = 9.sp, color = colors.textSecondary.copy(0.6f), fontFamily = Outfit)
            }
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

@Composable
fun BarChart(sevenDayBuckets: List<Int>, animationKey: Int) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary
    val maxCount = sevenDayBuckets.maxOrNull()?.coerceAtLeast(1) ?: 1

    val animatables = remember(animationKey) { List(7) { Animatable(0f) } }

    LaunchedEffect(animationKey, sevenDayBuckets) {
        animatables.forEachIndexed { index, animatable ->
            val target = (sevenDayBuckets.getOrElse(index) { 0 }.toFloat() / maxCount)
                .coerceAtLeast(if (sevenDayBuckets.getOrElse(index) { 0 } > 0) 0.05f else 0f)
            launch {
                kotlinx.coroutines.delay(index * 50L)
                animatable.animateTo(target, animationSpec = tween(600, easing = FastOutSlowInEasing))
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth().height(170.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    sevenDayBuckets.forEachIndexed { index, count ->
                        val isToday = index == 6
                        val barAlpha = if (isToday) 1f else 0.35f
                        val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Count above every bar, not just today
                            if (count > 0) {
                                Text(
                                    "$count",
                                    fontSize = if (isToday) 9.sp else 8.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) primary else colors.textSecondary.copy(0.5f),
                                    fontFamily = Outfit
                                )
                            } else {
                                Spacer(Modifier.height(13.dp))
                            }
                            Spacer(Modifier.height(2.dp))
                            Box(
                                Modifier
                                    .width(28.dp)
                                    .fillMaxHeight(animatables[index].value)
                                    .background(
                                        Brush.verticalGradient(listOf(
                                            primary.copy(alpha = barAlpha * 0.5f),
                                            primary.copy(alpha = barAlpha)
                                        )),
                                        barShape
                                    )
                                    .border(0.5.dp, primary.copy(alpha = (barAlpha * 0.7f).coerceAtMost(1f)), barShape)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val cal = Calendar.getInstance()
                val dayNames = (0..6).map {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.add(Calendar.DAY_OF_YEAR, -(6 - it))
                    when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY    -> "Mo"
                        Calendar.TUESDAY   -> "Tu"
                        Calendar.WEDNESDAY -> "We"
                        Calendar.THURSDAY  -> "Th"
                        Calendar.FRIDAY    -> "Fr"
                        Calendar.SATURDAY  -> "Sa"
                        Calendar.SUNDAY    -> "Su"
                        else -> ""
                    }
                }
                dayNames.forEachIndexed { index, label ->
                    val isToday = index == 6
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) primary else colors.textSecondary.copy(0.6f),
                        modifier = Modifier.width(28.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontFamily = Outfit
                    )
                }
            }
        }
    }
}

// ── Category breakdown ────────────────────────────────────────────────────────

@Composable
fun CategoryBreakdown(categoryData: Map<String, Int>, priorityColors: Map<String, String>) {
    val colors = LocalHoneyJarColors.current

    // Split into signal categories and noise categories
    val allCounts = categoryData.entries.sortedByDescending { it.value }
    val signalCounts = allCounts.filter { it.key !in NOISE_CATEGORIES }.take(8)
    val noiseCounts  = allCounts.filter { it.key in NOISE_CATEGORIES }

    val maxCount = allCounts.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            if (signalCounts.isEmpty() && noiseCounts.isEmpty()) {
                Text("Your category jar is empty.", color = colors.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp), fontFamily = Outfit)
                return@Column
            }

            // Signal categories — full colour, full size
            signalCounts.forEach { (key, count) ->
                CategoryRow(key, count, maxCount, priorityColors, colors, dimmed = false)
            }

            // Divider before noise if both present
            if (signalCounts.isNotEmpty() && noiseCounts.isNotEmpty()) {
                Divider(color = colors.glassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    "Background",
                    fontSize = 9.sp,
                    color = colors.textSecondary.copy(0.4f),
                    fontFamily = Outfit,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Noise categories — dimmed
            noiseCounts.forEach { (key, count) ->
                CategoryRow(key, count, maxCount, priorityColors, colors, dimmed = true)
            }
        }
    }
}

@Composable
private fun CategoryRow(
    key: String,
    count: Int,
    maxCount: Int,
    priorityColors: Map<String, String>,
    colors: com.honeyjar.app.ui.theme.HoneyJarColors,
    dimmed: Boolean
) {
    val hex = priorityColors[key] ?: "#94a3b8"
    val baseColor = ColorUtils.parseHexColor(hex, colors.textSecondary)
    val barColor = if (dimmed) baseColor.copy(alpha = 0.4f) else baseColor
    val textAlpha = if (dimmed) 0.45f else 1f

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (dimmed) 5.dp else 7.dp).background(barColor, CircleShape))
        Text(
            key.replaceFirstChar { it.uppercase() },
            fontSize = if (dimmed) 9.sp else 10.sp,
            color = colors.textSecondary.copy(textAlpha),
            modifier = Modifier.widthIn(min = 64.dp, max = 80.dp),
            fontFamily = Outfit
        )
        Box(
            Modifier.weight(1f).height(if (dimmed) 6.dp else 10.dp)
                .background(colors.itemBg, RoundedCornerShape(5.dp))
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(count.toFloat() / maxCount)
                    .background(barColor, RoundedCornerShape(5.dp))
            )
        }
        Text(
            "$count",
            fontSize = if (dimmed) 9.sp else 10.sp,
            color = colors.textSecondary.copy(textAlpha),
            modifier = Modifier.widthIn(min = 28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontFamily = Outfit
        )
    }
}

// ── Heatmap ───────────────────────────────────────────────────────────────────

@Composable
fun Heatmap(heatmapData: Array<IntArray>) {
    val colors = LocalHoneyJarColors.current
    val heatRamp = colors.heatmapRamp

    val allValues = heatmapData.flatMap { it.toList() }.filter { it > 0 }.sorted()
    val cap = if (allValues.isEmpty()) 1
              else allValues[(allValues.size * 0.95).toInt().coerceAtMost(allValues.lastIndex)].coerceAtLeast(1)

    val cellHeight = 20.dp
    val cellSpacing = 5.dp
    val colSpacing = 4.dp
    val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    val timeLabels = listOf("12am", "3am", "6am", "9am", "12pm", "3pm", "6pm", "9pm")

    GlassCard(Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                    dayLabels.forEach { label ->
                        Box(Modifier.height(cellHeight).width(20.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary.copy(0.55f))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(colSpacing)) {
                        repeat(8) { bucket ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                                repeat(7) { day ->
                                    val count = heatmapData[day][bucket]
                                    val intensity = if (count == 0) 0 else (count.toFloat() / cap * 4f).toInt().coerceIn(1, 4)
                                    Box(Modifier.fillMaxWidth().height(cellHeight).background(heatRamp[intensity], RoundedCornerShape(3.dp)))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(colSpacing)) {
                        repeat(8) { bucket ->
                            Box(Modifier.weight(1f)) {
                                if (bucket % 2 == 0) {
                                    Text(timeLabels[bucket], fontSize = 7.sp, color = colors.textSecondary.copy(0.45f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Less", fontSize = 8.sp, color = colors.textSecondary.copy(0.4f))
                Spacer(Modifier.width(6.dp))
                heatRamp.forEachIndexed { i, rampColor ->
                    Box(Modifier.size(11.dp).background(rampColor, RoundedCornerShape(2.dp)))
                    if (i < heatRamp.lastIndex) Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("More", fontSize = 8.sp, color = colors.textSecondary.copy(0.4f))
            }
        }
    }
}

// ── App Guilt (renamed Noisiest Apps) ────────────────────────────────────────

@Composable
fun AppGuiltSection(entries: List<AppGuiltEntry>) {
    val context = LocalContext.current
    val colors = LocalHoneyJarColors.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = colors.textSecondary.copy(0.5f),
                        modifier = Modifier.width(20.dp)
                    )
                    val icon by produceState(
                        initialValue = AppIconCache.peek(entry.packageName),
                        key1 = entry.packageName
                    ) {
                        if (value == null) {
                            value = withContext(Dispatchers.IO) { AppIconCache.get(entry.packageName, context) }
                        }
                    }
                    val iconBitmap = icon
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = entry.label,
                            modifier = Modifier.size(28.dp).background(Color.Transparent, RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(0.2f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(entry.label.firstOrNull()?.uppercase() ?: "?", fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary, fontFamily = Outfit)
                        if (entry.streak >= 3) {
                            Text("🔥 ${entry.streak} days in a row", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary, fontFamily = Outfit)
                        }
                    }
                    // Count chip with "this week" label
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(0.15f)) {
                            Text(
                                "${entry.count7Days}",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontFamily = Outfit
                            )
                        }
                        Text("this week", fontSize = 8.sp, color = colors.textSecondary.copy(0.4f),
                            fontFamily = Outfit, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (index < entries.lastIndex) {
                    Divider(color = colors.glassBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Weekly insight banner (moved to bottom) ───────────────────────────────────

@Composable
fun WeeklyInsightBanner(
    weeklyCount: Int,
    prevWeekCount: Int,
    categoryData: Map<String, Int>,
    heatmapData: Array<IntArray>,
    sevenDayTotals: List<Int>
) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary

    // Week-over-week trend — suppress when capped (no prior week data) or equal
    val weekTrend: String? = when {
        prevWeekCount == 0 -> null
        weeklyCount > prevWeekCount -> {
            val pct = ((weeklyCount - prevWeekCount) * 100f / prevWeekCount).toInt()
            if (pct >= 999) null else "↑ ${pct}% vs last week"
        }
        weeklyCount < prevWeekCount -> {
            val pct = ((prevWeekCount - weeklyCount) * 100f / prevWeekCount).toInt()
            if (pct >= 999) null else "↓ ${pct}% vs last week"
        }
        else -> null
    }

    // Busiest slot
    val hourBucketLabels = listOf(
        "12am–3am", "3am–6am", "6am–9am", "9am–12pm",
        "12pm–3pm", "3pm–6pm", "6pm–9pm", "9pm–12am"
    )
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val recentDayIndices = sevenDayTotals.indices.filter { sevenDayTotals[it] > 0 }
    val searchIndices = if (recentDayIndices.isNotEmpty()) recentDayIndices else heatmapData.indices.toList()
    var busiestDay = -1; var busiestBucket = -1; var busiestVal = 0
    searchIndices.forEach { day ->
        heatmapData[day].forEachIndexed { bucket, count ->
            if (count > busiestVal) { busiestVal = count; busiestDay = day; busiestBucket = bucket }
        }
    }
    val busiestSlot = if (busiestDay >= 0 && busiestBucket >= 0)
        "${dayLabels[busiestDay]} ${hourBucketLabels.getOrElse(busiestBucket) { "—" }}" else "—"

    // Top signal category (excluding noise)
    val topEntry = categoryData.entries
        .filter { it.key !in NOISE_CATEGORIES }
        .maxByOrNull { it.value }
    val topCategoryName = topEntry?.key?.replaceFirstChar { it.uppercase() } ?: "—"

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.width(3.dp).height(86.dp).background(
                    Brush.verticalGradient(listOf(primary, primary.copy(0.4f))),
                    RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
                )
            )
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier.padding(vertical = 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Week trend
                if (weekTrend != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("This week", fontSize = 10.sp, color = colors.textSecondary,
                            modifier = Modifier.width(88.dp))
                        Text("$weeklyCount notifications", fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        val trendColor = when {
                            weekTrend.startsWith("↑") -> Color(0xFFEF4444)
                            weekTrend.startsWith("↓") -> Color(0xFF22C55E)
                            else -> colors.textSecondary
                        }
                        Text(
                            weekTrend,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = trendColor,
                            maxLines = 1
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Busiest slot", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(88.dp))
                    Text(busiestSlot, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top category", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(88.dp))
                    Text(topCategoryName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
            }
            Spacer(Modifier.width(14.dp))
        }
    }
}
