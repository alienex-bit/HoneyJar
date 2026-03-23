package com.honeyjar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.*
import com.honeyjar.app.utils.ColorUtils

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val total by viewModel.totalCount.collectAsState()
    val actioned by viewModel.actionedCount.collectAsState()
    val priorityColors by viewModel.priorityColors.collectAsState()
    val barData by viewModel.barChartData.collectAsState()
    val categoryData by viewModel.categoryBreakdown.collectAsState()
    val heatmap by viewModel.heatmapData.collectAsState()
    val snoozedCount by viewModel.snoozedCount.collectAsState()
    val avgResponseMinutes by viewModel.avgResponseMinutes.collectAsState()

    val actionRate = if (total > 0) (actioned * 100 / total) else 0

    var animationKey by remember { mutableStateOf(0) }
    // Trigger on tab switch (composable re-enters composition)
    LaunchedEffect(Unit) { animationKey++ }
    // Trigger on app resume from background
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
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "Statistics",
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                fontFamily = PlayfairDisplay,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                style = androidx.compose.ui.text.TextStyle(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                )
            )
        }

        item {
            StatsInsightBanner(categoryData, heatmap, total)
        }

        item {
            StatsHero(total, actionRate, avgResponseMinutes, snoozedCount, animationKey)
        }

        item {
            SectionHeader("Activity Trends")
            Spacer(Modifier.height(16.dp))
            BarChart(barData, animationKey)
        }

        item {
            SectionHeader("By Category")
            Spacer(Modifier.height(16.dp))
            CategoryBreakdown(categoryData, priorityColors)
        }

        item {
            SectionHeader("Activity Heatmap")
            Spacer(Modifier.height(16.dp))
            Heatmap(heatmap)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    val primary = MaterialTheme.colorScheme.primary
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LocalHoneyJarColors.current.textPrimary, fontFamily = Outfit)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(2.dp)
                .background(primary, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun StatsInsightBanner(
    categoryData: Map<String, Int>,
    heatmapData: Array<IntArray>,
    total: Int
) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary

    // Top category
    val topEntry = categoryData.entries.maxByOrNull { it.value }
    val topCategoryName = topEntry?.key?.replaceFirstChar { it.uppercase() } ?: "—"
    val topCategoryPct = if (topEntry != null && total > 0) "${topEntry.value * 100 / total}%" else "—"

    // Busiest slot
    val hourBucketLabels = listOf("12am–4am", "4am–8am", "8am–12pm", "12pm–4pm", "4pm–8pm", "8pm–11pm",
        "8pm–11pm", "8pm–11pm", "8pm–11pm", "8pm–11pm", "8pm–11pm", "8pm–11pm")
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var busiestDay = -1
    var busiestBucket = -1
    var busiestVal = 0
    heatmapData.forEachIndexed { day, buckets ->
        buckets.forEachIndexed { bucket, count ->
            if (count > busiestVal) {
                busiestVal = count
                busiestDay = day
                busiestBucket = bucket
            }
        }
    }
    val busiestSlot = if (busiestDay >= 0 && busiestBucket >= 0)
        "${dayLabels[busiestDay]} ${hourBucketLabels.getOrElse(busiestBucket) { "—" }}"
    else "—"

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            // Left accent strip
            Box(
                Modifier
                    .width(3.dp)
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.4f))),
                        RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
                    )
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(vertical = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top category", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(88.dp))
                    Text(topCategoryName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(topCategoryPct, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Busiest time", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(88.dp))
                    Text(busiestSlot, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
            }
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
fun StatsHero(total: Int, actionRate: Int, avgResponseMinutes: Long?, snoozedToday: Int, animationKey: Int) {
    var displayedTotal by remember(animationKey) { mutableStateOf(0) }
    LaunchedEffect(animationKey, total) {
        for (step in 1..10) {
            displayedTotal = (total * step / 10f).toInt()
            kotlinx.coroutines.delay(100L)
        }
        displayedTotal = total
    }

    Box(Modifier.height(190.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            item { StatTile("Total captured", displayedTotal.toString(), "all time") }
            item { StatTile("Action rate", "$actionRate%", "of all captured") }
            item { StatTile("Avg response", if (avgResponseMinutes != null) "${avgResponseMinutes}m" else "—", "time to resolve") }
            item { StatTile("Snoozed", "$snoozedToday", "currently active") }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, delta: String) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            // Accent cap bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(primary, primary.copy(alpha = 0.4f))),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
            )
            Column(Modifier.padding(12.dp)) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(label, fontSize = 9.sp, color = colors.textSecondary)
                Text(delta, fontSize = 9.sp, color = colors.textSecondary.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun BarChart(sevenDayBuckets: List<Int>, animationKey: Int) {
    val colors = LocalHoneyJarColors.current
    val primary = MaterialTheme.colorScheme.primary

    val maxCount = sevenDayBuckets.maxOrNull()?.coerceAtLeast(1) ?: 1

    // Step animation: increment in 10 steps over 1 second (each step = 100ms)
    val animatedFractions = sevenDayBuckets.mapIndexed { index, count ->
        val targetFraction = (count.toFloat() / maxCount).coerceAtLeast(if (count > 0) 0.05f else 0f)
        var currentFraction by remember(animationKey) { mutableStateOf(0f) }
        LaunchedEffect(animationKey) {
            kotlinx.coroutines.delay(index * 40L)
            for (step in 1..10) {
                currentFraction = targetFraction * (step / 10f)
                kotlinx.coroutines.delay(100L)
            }
        }
        currentFraction
    }

    GlassCard(Modifier.fillMaxWidth().height(160.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Bars row
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    sevenDayBuckets.forEachIndexed { index, count ->
                        val isToday = index == 6
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isToday) {
                                Text("$count", fontSize = 8.sp, color = primary, fontWeight = FontWeight.Bold)
                            }
                            val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            val barAlpha = if (isToday) 1f else 0.22f
                            Box(
                                Modifier
                                    .width(28.dp)
                                    .fillMaxHeight(animatedFractions[index])
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                primary.copy(alpha = barAlpha * 0.45f),
                                                primary.copy(alpha = barAlpha)
                                            )
                                        ),
                                        barShape
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = primary.copy(alpha = (barAlpha * 0.6f).coerceAtMost(1f)),
                                        shape = barShape
                                    )
                            )
                        }
                    }
                }

            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val cal = Calendar.getInstance()
                val dayNames = (0..6).map {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.add(Calendar.DAY_OF_YEAR, -(6 - it))
                    when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "M"
                        Calendar.TUESDAY -> "T"
                        Calendar.WEDNESDAY -> "W"
                        Calendar.THURSDAY -> "T"
                        Calendar.FRIDAY -> "F"
                        Calendar.SATURDAY -> "S"
                        Calendar.SUNDAY -> "S"
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdown(categoryData: Map<String, Int>, priorityColors: Map<String, String>) {
    val colors = LocalHoneyJarColors.current
    val counts = categoryData.entries.sortedByDescending { it.value }
    val maxCount = counts.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            counts.forEach { (key, count) ->
                val hex = priorityColors[key] ?: "#94a3b8"
                val barColor = ColorUtils.parseHexColor(hex, colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(7.dp).background(barColor, CircleShape))
                    Text(key.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(64.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(colors.itemBg, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .background(barColor, RoundedCornerShape(5.dp))
                        )
                    }
                    Text("$count", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            if (counts.isEmpty()) {
                Text("Your category jar is empty.", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
fun Heatmap(heatmapData: Array<IntArray>) {
    val colors = LocalHoneyJarColors.current
    val maxCount = heatmapData.flatMap { it.toList() }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val heatRamp = colors.heatmapRamp

    GlassCard(Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                // Day Labels Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    dayLabels.forEach { label ->
                        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 9.sp, color = colors.textSecondary.copy(0.6f), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Heatmap Grid
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(12) { bucket ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(7) { day ->
                                    val count = heatmapData[day][bucket]
                                    val intensity = if (count == 0) 0 else (count.toFloat() / maxCount * 4).toInt().coerceIn(1, 4)
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(22.dp)
                                            .background(heatRamp[intensity], RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Time Labels Row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("12am", "4am", "8am", "12pm", "4pm", "8pm", "11pm").forEach { time ->
                            Text(time, fontSize = 8.sp, color = colors.textSecondary.copy(0.5f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Less", fontSize = 8.sp, color = colors.textSecondary.copy(0.4f))
                Spacer(Modifier.width(8.dp))
                heatRamp.forEach { rampColor ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(rampColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text("More", fontSize = 8.sp, color = colors.textSecondary.copy(0.4f))
            }
        }
    }
}
