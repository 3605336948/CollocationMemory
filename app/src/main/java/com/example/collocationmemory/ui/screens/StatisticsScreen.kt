package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.collocationmemory.ui.theme.DarkBase
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.local.DailyRecord
import com.example.collocationmemory.ui.viewmodel.HomeStats
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import com.example.collocationmemory.util.DateUtil
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(viewModel: MainViewModel, navController: NavController) {
    val homeStats by viewModel.homeStats.collectAsState()
    val recentRecords by viewModel.recentRecords.collectAsState()
    val continuousDays by viewModel.continuousDays.collectAsState()
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == DarkBase

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                actions = {
                    IconButton(
                        onClick = {
                            // 直接按当前数据画一张分享图，再交给系统分享面板
                            val bmp = buildStatsBitmap(
                                continuousDays = continuousDays,
                                totalMinutes = homeStats.totalMinutes,
                                masteredCount = homeStats.masteredCount,
                                learnedCount = homeStats.learnedCount,
                                totalCount = homeStats.totalCount,
                                darkTheme = isDark
                            )
                            shareStatsImage(context, bmp, "搭配记忆")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享学习战绩"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { BottomNavBar(navController = navController, currentRoute = Screen.Statistics.route) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 今日概览：从首页挪过来的四项核心数据，点卡片看今日明细
            TodayOverviewCard(
                homeStats = homeStats,
                onDetailClick = {
                    navController.navigate(
                        Screen.DayDetail.createRoute(Screen.DayDetail.TAB_TODAY)
                    )
                }
            )

            Text(
                text = "点右上角可把战绩分享成图片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                StatLargeCard(
                    modifier = Modifier.weight(1f),
                    label = "已掌握",
                    value = "${homeStats.masteredCount}",
                    unit = "条"
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatLargeCard(
                    modifier = Modifier.weight(1f),
                    label = "生词本",
                    value = "${homeStats.wordBookCount}",
                    unit = "条"
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatLargeCard(
                    modifier = Modifier.weight(1f),
                    label = "连续打卡",
                    value = "$continuousDays",
                    unit = "天"
                )
            }
            HeatMapCard(records = recentRecords)
            SignInCalendarCard()
            // 底部导航栏是半透明浮层，这里必须留出它的高度，
            // 否则最后一张卡片会被导航栏盖住
            BottomBarHeightSpacer()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 今日概览：原来在首页顶部的四项数据，现在并入统计页。
 *
 * 结构刻意保持克制：一行标题 + 一行四个数字 + 一行词本进度，
 * 沿用本页 surface 卡片的样子，不另起一套玻璃风格。
 * 整张卡可点，进「今日学习明细」。
 */
@Composable
fun TodayOverviewCard(homeStats: HomeStats, onDetailClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetailClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日概览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "查看明细 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "学习&复习",
                    value = "${(homeStats.todayRecord?.learnedCount ?: 0) + (homeStats.todayRecord?.reviewedCount ?: 0)}"
                )
                OverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "累计学习",
                    value = "${homeStats.totalLearned}"
                )
                OverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "今日时长",
                    value = "${homeStats.todayRecord?.studyMinutes ?: 0}",
                    unit = "分"
                )
                OverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "累计时长",
                    value = "${homeStats.totalMinutes}",
                    unit = "分"
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = homeStats.bookName.ifBlank { "默认词书" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "已学 ${homeStats.learnedCount} / ${homeStats.totalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun OverviewStat(modifier: Modifier = Modifier, label: String, value: String, unit: String? = null) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 生成「学习战绩」分享图。
 *
 * 实现选择说明：没有用 Compose 的 rememberGraphicsLayer 捕获布局，
 * 因为那需要 Compose 1.7+，而本项目是 1.6.1。改成**直接用 Android Canvas 画一张位图**：
 * 不依赖布局捕获、不需要第三方截图库，输出尺寸还能精确控制（1080×1620 竖版，适合朋友圈/小红书）。
 */
fun buildStatsBitmap(
    continuousDays: Int,
    totalMinutes: Int,
    masteredCount: Int,
    learnedCount: Int,
    totalCount: Int,
    darkTheme: Boolean
): android.graphics.Bitmap {
    val w = 1080
    val h = 1620
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    val bgTop = if (darkTheme) 0xFF1B1D2B.toInt() else 0xFFF7F4EE.toInt()
    val bgBottom = if (darkTheme) 0xFF0E0F1A.toInt() else 0xFFEDE7DC.toInt()
    val accent = if (darkTheme) 0xFFE0B084.toInt() else 0xFF8B5A2B.toInt()
    val titleColor = if (darkTheme) 0xFFEDEDF2.toInt() else 0xFF14151F.toInt()
    val mutedColor = if (darkTheme) 0xFF9A9AAD.toInt() else 0xFF5C5D6B.toInt()

    // 背景：竖向渐变
    val bgPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            bgTop, bgBottom, android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    val center = w / 2f
    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 64f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = 128f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val unitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = 44f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val footPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    canvas.drawText("我的学习战绩", center, 220f, titlePaint)
    canvas.drawText("搭配记忆", center, 286f, labelPaint)

    // 三块核心数据
    val items = listOf(
        Triple("$continuousDays", "天", "连续打卡"),
        Triple("$totalMinutes", "分", "累计时长"),
        Triple("$masteredCount", "条", "已掌握")
    )
    var y = 560f
    items.forEach { (value, unit, label) ->
        canvas.drawText(value, center - 30f, y, valuePaint)
        canvas.drawText(unit, center + 120f, y, unitPaint)
        canvas.drawText(label, center, y + 56f, labelPaint)
        y += 280f
    }

    // 底部
    canvas.drawText("词库进度 $learnedCount / $totalCount", center, 1400f, footPaint)
    canvas.drawText("每天一点点 · 没断过就算赢", center, 1460f, footPaint)

    return bmp
}

/**
 * 把位图写到 cache/share，再用 FileProvider 生成 content:// 交给分享面板。
 * 直接传 file:// 在 Android 7 以上会抛 FileUriExposedException，必须走 FileProvider。
 */
fun shareStatsImage(context: android.content.Context, bitmap: android.graphics.Bitmap, appName: String) {
    try {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val file = java.io.File(dir, "stats_${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, "$appName —— 每天一点点")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            android.content.Intent.createChooser(send, "分享学习战绩")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context, "分享失败：${e.message}", android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun StatLargeCard(modifier: Modifier = Modifier, label: String, value: String, unit: String) {    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
fun HeatMapCard(records: List<DailyRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "学习热力图",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            HeatMapGrid(records = records)
        }
    }
}

@Composable
fun HeatMapGrid(records: List<DailyRecord>) {
    val today = DateUtil.getTodayStart()
    val days = (0 until 84).map { index ->
        today - index * 24 * 60 * 60 * 1000L
    }.reversed()

    val recordMap = records.associateBy { it.date }
    val maxCount = (records.maxOfOrNull { it.learnedCount + it.reviewedCount } ?: 0).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val record = recordMap[day]
                    val count = record?.let { it.learnedCount + it.reviewedCount } ?: 0
                    val intensity = if (count > 0) {
                        (0.3f + 0.7f * count / maxCount).coerceAtMost(1f)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (count == 0) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = intensity)
                                }
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignInCalendarCard() {
    val today = Calendar.getInstance()
    val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = today.get(Calendar.DAY_OF_MONTH)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "签到日历",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..daysInMonth).forEach { day ->
                    val isToday = day == currentDay
                    val isPast = day <= currentDay
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isToday -> MaterialTheme.colorScheme.primary
                                    isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$day",
                            fontSize = 12.sp,
                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
