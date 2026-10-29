package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.collocationmemory.R
import androidx.compose.foundation.shape.CircleShape
import com.example.collocationmemory.Screen
import com.example.collocationmemory.sessionRoute
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.SessionSnapshot
import com.example.collocationmemory.ui.theme.DarkBase
import com.example.collocationmemory.ui.viewmodel.HomeStats
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 首页：壁纸 + 玻璃卡片风格
 *
 * 色值与结构参考用户提供的设计稿：
 * - 卡片是「半透明黑压在壁纸上」，不是纯色，所以壁纸必须先铺满全屏
 * - 数字用琥珀色 #FFA500（取自设计稿的「495」）
 * - 今日没有待复习时显示绿色对勾 #00A13C（取自设计稿）
 * - 「今日概览」在最上方，学习 / 复习卡片靠底
 */

/**
 * 卡片底色：半透明黑。
 * 设计稿实测约 0.45，这里取 0.55 —— 因为用户的壁纸亮度差异很大，
 * 更暗一点才能保证白字和彩字在每一张壁纸上都清晰。
 */
private val GlassCard = Color.Black.copy(alpha = 0.55f)

/** 数字强调色（设计稿「495」的取色） */
private val AccentAmber = Color(0xFFFFA500)

/** 完成状态（设计稿绿色对勾的取色） */
private val AccentGreen = Color(0xFF00A13C)

/**
 * 7 张壁纸轮换。
 * 索引 = (epoch 天数 / 2) % 7 —— 等价于「每隔一天换一张」，
 * 且同一天所有用户看到的都是同一张（不随机，便于你按图排查问题）。
 */
/**
 * 今天用哪张壁纸：**每天一张**，7 张循环（今天与上周同一天是同一张）。
 *
 * 之前写的是 `dayIndex / 2`，等于每 2 天才换一张，
 * 用户会觉得「第二天了图片怎么没变」。这里按自然日取模，保证每天都不一样。
 *
 * 设为 public：新学页、复习页也用它，全 App 同一天看到的是同一张背景。
 */
fun backgroundResForToday(): Int {
    val cal = java.util.Calendar.getInstance()
    // Calendar.DAY_OF_WEEK：1=周日, 2=周一, ... 7=周六 → 转成 0=周一 ... 6=周日
    val dayIndex = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7)
    return when (dayIndex) {
        0 -> R.drawable.bg1
        1 -> R.drawable.bg2
        2 -> R.drawable.bg3
        3 -> R.drawable.bg4
        4 -> R.drawable.bg5
        5 -> R.drawable.bg6
        else -> R.drawable.bg7
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel, navController: NavController) {
    val homeStats by viewModel.homeStats.collectAsState()
    val signInMessage by viewModel.signInMessage.collectAsState()
    val battleIconPosition by viewModel.battleIconPosition.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 「还有一组没做完，但你点的是另一边」时先问一句，别默默把进度丢了。
    // 存的是用户想开的那一类（learn / review），确认后才真正开。
    var pendingNewSession by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(signInMessage) {
        signInMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSignInMessage()
        }
    }

    /** 真的开一组新的 */
    val startFresh: (String) -> Unit = { source ->
        if (source == SessionSnapshot.SOURCE_LEARN) {
            viewModel.startLearnSession()
            navController.navigate(Screen.Learn.route)
        } else {
            viewModel.startReviewSession()
            navController.navigate(Screen.Review.route)
        }
    }

    /**
     * 点「Learn / Review」卡片。
     *
     * 关键：**如果上一组还没做完，优先把它接回来**，而不是另开一组。
     * 原来的行为是每次点击都开新的一组，于是「拼写中途回首页再点复习」
     * 就会拿到下一批词，用户以为刚才那组凭空消失了。
     */
    val onActionCardClick: (String) -> Unit = { source ->
        val session = activeSession
        when {
            session == null -> startFresh(source)
            session.source == source -> navController.navigate(sessionRoute(session))
            // 点的是另一边：问清楚是继续旧的那组，还是放弃它开新的
            else -> pendingNewSession = source
        }
    }

    Scaffold(
        // 让内容延伸到状态栏下方，壁纸才能铺满整屏
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            BottomNavBar(
                navController = navController,
                currentRoute = Screen.Home.route
            )
        }
    ) { paddingValues ->
        // 壁纸铺满整屏（含状态栏与导航栏下方）——铺底色的是它，不能留边。
        Box(modifier = Modifier.fillMaxSize()) {
            // ---- 壁纸层：每隔一天轮换 ----
            Image(
                painter = painterResource(id = backgroundResForToday()),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // ---- 压暗层：保证最亮的壁纸上白字也看得清 ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )

            // ---- 内容层 ----
            // 内容仍要避开底部导航栏（否则卡片会被导航栏盖住），
            // 但壁纸铺满，所以那块露出来的是壁纸而不是黑底。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Spacer(modifier = Modifier.weight(1f))

                // 「继续上次」：切后台被系统杀进程后，这一条就是学生找回进度的入口
                activeSession?.let { session ->
                    ResumeSessionCard(
                        snapshot = session,
                        onContinue = { navController.navigate(sessionRoute(session)) },
                        onDiscard = { viewModel.endSession() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 学习 / 复习：底部一行（对战卡已移到右侧悬浮，不在这行里）
                ActionButtonsRow(
                    newCount = homeStats.newCount,
                    dueCount = homeStats.dueCount,
                    tomorrowDueCount = homeStats.tomorrowDueCount,
                    onLearnClick = { onActionCardClick(SessionSnapshot.SOURCE_LEARN) },
                    onReviewClick = { onActionCardClick(SessionSnapshot.SOURCE_REVIEW) },
                    onTomorrowClick = {
                        navController.navigate(
                            Screen.DayDetail.createRoute(Screen.DayDetail.TAB_TOMORROW)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ---- 对战入口：可拖动的悬浮图标 ----
            // 只在首页渲染（本组件属于 HomeScreen），别的页面不会出现。
            // 用 fillMaxSize 铺满内容区，内部用绝对坐标定位图标。
            DraggableBattleEntry(
                onClick = {
                    val launched = viewModel.openExternalUrl(context, BATTLE_URL)
                    if (!launched) {
                        // 没有浏览器能处理时兜底：复制链接，让用户自己粘贴打开
                        clipboardManager.setText(AnnotatedString(BATTLE_URL))
                        scope.launch { snackbarHostState.showSnackbar("没有找到浏览器，对战链接已复制") }
                    }
                },
                onPositionChange = { x, y -> viewModel.saveBattleIconPosition(x, y) },
                savedPosition = battleIconPosition
            )
        }
    }

    // 点了一边、但另一边还有没做完的 —— 先问清楚，别默默把进度丢了
    val pendingSession = activeSession
    val pendingSource = pendingNewSession
    if (pendingSession != null && pendingSource != null) {
        AlertDialog(
            onDismissRequest = { pendingNewSession = null },
            title = { Text("还有一组没做完") },
            text = {
                Text(
                    "上次的${sourceLabel(pendingSession.source)}还没做完" +
                        "（${sessionProgressText(pendingSession)}）。" +
                        "先把它做完，还是放弃、开始新的一组？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingNewSession = null
                    navController.navigate(sessionRoute(pendingSession))
                }) {
                    Text("继续上次的")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingNewSession = null
                    // 直接开新的一组：startLearn/ReviewSession 里的落盘会把旧快照覆盖掉。
                    // 这里刻意不先 endSession()——那次清空和随后的写入是两条并发任务，
                    // 顺序反了会把刚写好的新快照清掉。
                    startFresh(pendingSource)
                }) {
                    Text("放弃，开始新的")
                }
            }
        )
    }
}

/** 「学习」/「复习」的中文说法 */
private fun sourceLabel(source: String): String =
    if (source == SessionSnapshot.SOURCE_LEARN) "新学" else "复习"

/** 会话还剩多少没做，给「继续上次」这类文案用 */
private fun sessionProgressText(snapshot: SessionSnapshot): String {
    val total = snapshot.itemIds.size
    return when (snapshot.phase) {
        SessionSnapshot.PHASE_SPELL -> {
            val skipped = snapshot.spellExcludedIds.size
            val left = (total - skipped - snapshot.spellIndex).coerceAtLeast(0)
            "还剩 $left 个词没拼写"
        }
        SessionSnapshot.PHASE_SPELL_CHOICE -> "复习做完了，还没选拼写哪些"
        else -> {
            val left = (total - snapshot.index).coerceAtLeast(0)
            "还剩 $left 个词没看"
        }
    }
}

/**
 * 「继续上次的复习」入口卡片。
 *
 * 存在的理由：华为 / 鸿蒙在切后台时会直接杀进程，学生回来时 App 是冷启动的，
 * 什么线索都没有。没有这张卡，他就只能自己猜「刚才那组还在不在」——
 * 而这正是他报的 bug。所以进度没丢这件事必须显式告诉他。
 *
 * 样式沿用首页的玻璃卡片语言：半透明黑底 + 白字 + 琥珀色强调。
 */
@Composable
fun ResumeSessionCard(
    snapshot: SessionSnapshot,
    onContinue: () -> Unit,
    onDiscard: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContinue() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续上次的${sourceLabel(snapshot.source)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sessionProgressText(snapshot),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Text(
                text = "继续 ›",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AccentAmber
            )
            // 结束入口：不留的话，学生想重新开一组就只能先把旧的做完
            TextButton(onClick = onDiscard) {
                Text("结束", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

/**
 * 首页悬浮的「蓝人单词对战」入口。
 *
 * - **只有图标，没有文字**；
 * - 图标是一张方图，按 56dp 显示（比原来的卡片小）；
 * - **可以用手指拖到首页任意位置**，松手后把坐标存进偏好，重启仍在原处；
 * - 只在首页出现：组件写在 HomeScreen 里，别的页面不会渲染它。
 *
 * 拖动用 detectDragGestures（而不是 draggable），因为它能区分「拖动」与「点击」：
 * 手指移动超过 touchSlop 才算拖动，轻点仍然触发 onClick 打开对战链接。
 */
@Composable
fun DraggableBattleEntry(
    onClick: () -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    savedPosition: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { BATTLE_ICON_SIZE.toPx() }

    // 容器尺寸：用来把图标限制在屏幕内，不让它被拖出去
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxX = (constraints.maxWidth - sizePx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - sizePx).coerceAtLeast(0f)

        // 默认位置：贴右边、竖直居中再往下一点（与之前一致）
        val defaultX = maxX - with(density) { 4.dp.toPx() } - with(density) { 16.dp.toPx() }
        val defaultY = maxY / 2f + with(density) { BATTLE_CARD_DROP.toPx() }

        var offsetX by remember { mutableFloatStateOf(Float.NaN) }
        var offsetY by remember { mutableFloatStateOf(Float.NaN) }
        // 用户是否手动拖动过。拖动前，DataStore 的异步值到达时都应当被采纳；
        // 一旦用户自己拖过，就再也不让存储里的旧值把图标拽回去。
        var userMoved by remember { mutableStateOf(false) }

        // 初始化 / 采纳存储值。
        //
        // 时序上有个坑：DataStore 是异步的，首帧拿到的 savedPosition 还是初始值
        // (-1, -1)，真实坐标要过几十毫秒才到。所以这里不能用「只跑一次」的守卫
        // （那样真实值到了也不会被应用），而要在 savedPosition 每次变化时重新判断。
        LaunchedEffect(
            constraints.maxWidth, constraints.maxHeight, savedPosition, maxX, maxY, userMoved
        ) {
            if (containerSize == IntSize.Zero) {
                containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            }
            val (sx, sy) = savedPosition
            when {
                // 已经拖过了：不覆盖用户的操作
                userMoved -> Unit
                // 有有效存储值：用存储值（并夹到当前屏幕范围内）
                sx >= 0f && sy >= 0f -> {
                    offsetX = sx.coerceIn(0f, maxX)
                    offsetY = sy.coerceIn(0f, maxY)
                }
                // 还没读到存储值：先摆到默认位置，等真实值到了会重跑这里
                offsetX.isNaN() -> {
                    offsetX = defaultX.coerceIn(0f, maxX)
                    offsetY = defaultY.coerceIn(0f, maxY)
                }
                else -> Unit
            }
        }

        if (!offsetX.isNaN() && !offsetY.isNaN()) {
            // 记录最近一次已保存的坐标，避免同一位置重复落盘。
            //
            // 初值用 -1f 而不是 Float.NaN：坐标总是 >= 0，而
            // abs(x - NaN) 结果是 NaN，`NaN >= 1f` 恒为 false ——
            // 用 NaN 当哨兵会让保存条件永远不成立（踩过这个坑）。
            var lastSavedX by remember { mutableFloatStateOf(-1f) }
            var lastSavedY by remember { mutableFloatStateOf(-1f) }

            Image(
                painter = painterResource(id = R.drawable.ic_battle),
                contentDescription = "蓝人单词对战",
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(BATTLE_ICON_SIZE)
                    .clip(CircleShape)
                    // 拖动：用一个 pointerInput 同时处理横纵两个方向。
                    // 为什么不用两个 draggable：它们会互相抢手势，只有一个能拿到事件
                    // （实测纵向那个赢了，导致只能左右移动）。
                    // 为什么不用 detectDragGestures：它在手势结束事件没派发到的情况下
                    // 不触发 onDragEnd，位置就丢了。这里在拖动过程中就实时落盘。
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var lastX = down.position.x
                                var lastY = down.position.y
                                var totalX = 0f
                                var totalY = 0f
                                val slop = viewConfiguration.touchSlop
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break
                                    totalX += change.position.x - lastX
                                    totalY += change.position.y - lastY
                                    lastX = change.position.x
                                    lastY = change.position.y
                                    if (!dragging &&
                                        (kotlin.math.abs(totalX) > slop ||
                                            kotlin.math.abs(totalY) > slop)
                                    ) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        userMoved = true
                                        val stepX = change.position.x - change.previousPosition.x
                                        val stepY = change.position.y - change.previousPosition.y
                                        offsetX = (offsetX + stepX).coerceIn(0f, maxX)
                                        offsetY = (offsetY + stepY).coerceIn(0f, maxY)
                                        if (kotlin.math.abs(offsetX - lastSavedX) >= 1f ||
                                            kotlin.math.abs(offsetY - lastSavedY) >= 1f
                                        ) {
                                            lastSavedX = offsetX
                                            lastSavedY = offsetY
                                            onPositionChange(offsetX, offsetY)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .clickable { onClick() }
            )
        }
    }
}

/** 对战图标的显示尺寸 */
private val BATTLE_ICON_SIZE = 56.dp

/** 对战入口相对「竖直居中」再往下移的距离 */
private val BATTLE_CARD_DROP = 34.dp

/** 「蓝人单词对战」的跳转地址 */
private const val BATTLE_URL = "https://glx.xlsyingyu.cn/english-review/battle/"

@Composable
fun ActionButtonsRow(
    newCount: Int,
    dueCount: Int,
    tomorrowDueCount: Int,
    onLearnClick: () -> Unit,
    onReviewClick: () -> Unit,
    onTomorrowClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        GlassActionCard(
            modifier = Modifier.weight(1f),
            title = "Learn",
            value = "$newCount",
            badge = null,
            onBadgeClick = null,
            onClick = onLearnClick
        )
        GlassActionCard(
            modifier = Modifier.weight(1f),
            title = "Review",
            value = "$dueCount",
            // 写全「明日待复习」：只写「明日 10」容易被误读成今天的复习数
            badge = if (tomorrowDueCount > 0) "明日待复习 $tomorrowDueCount ›" else null,
            onBadgeClick = onTomorrowClick,
            onClick = onReviewClick,
            completed = dueCount == 0
        )
    }
}

@Composable
fun GlassActionCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    badge: String?,
    onBadgeClick: (() -> Unit)?,
    onClick: () -> Unit,
    completed: Boolean = false
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        // 模糊玻璃：半透明白 + 极淡描边。
        // 不用完全不透明的白（那会盖死壁纸，失去磨砂质感），
        // 也不用深色玻璃（在浅色壁纸上发灰、黑字看不清）。
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.30f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (completed) {
                // 今日没有待复习：显示绿色对勾，和设计稿一致
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentGreen)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    // 玻璃卡是半透明的：数字若用浅色（琥珀/主色），
                    // 在浅色壁纸上会糊掉。跟随标题用黑色，任意壁纸都读得清。
                    color = Color.Black
                )
            }
            if (badge != null && onBadgeClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.6f),
                    // 标签可能较长（"明日待复习 10 ›"），压成一行避免撑破卡片
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onBadgeClick() }
                )
            }
        }
    }
}

// ==================== 明细页 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(viewModel: MainViewModel, navController: NavController, initialTab: Int) {
    val detail by viewModel.dayDetail.collectAsState()
    var tab by remember { mutableStateOf(initialTab) }

    LaunchedEffect(Unit) { viewModel.loadDayDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Screen.DayDetail.TAB_TODAY) "今日学习明细" else "明日待复习") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 同一条搭配可能今天既新学又被拼写，这里按 id 去重，避免重复计数
                val todayCount = (
                    detail.learnedToday.map { it.id } +
                        detail.reviewedToday.map { it.id } +
                        detail.spelledToday.map { it.id }
                    ).distinct().size
                TabPill(
                    modifier = Modifier.weight(1f),
                    text = "今日学习 $todayCount",
                    selected = tab == Screen.DayDetail.TAB_TODAY,
                    onClick = { tab = Screen.DayDetail.TAB_TODAY }
                )
                TabPill(
                    modifier = Modifier.weight(1f),
                    text = "明日复习 ${detail.dueTomorrow.size}",
                    selected = tab == Screen.DayDetail.TAB_TOMORROW,
                    onClick = { tab = Screen.DayDetail.TAB_TOMORROW }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (tab == Screen.DayDetail.TAB_TODAY) {
                DetailSection(
                    title = "今天新学",
                    emptyText = "今天还没有新学的搭配",
                    items = detail.learnedToday,
                    onToggleWordBook = { viewModel.toggleWordBook(it) }
                )
                DetailSection(
                    title = "今天复习",
                    emptyText = "今天还没有复习记录",
                    items = detail.reviewedToday,
                    onToggleWordBook = { viewModel.toggleWordBook(it) }
                )
                DetailSection(
                    title = "今天拼写",
                    emptyText = "今天还没有拼写记录",
                    items = detail.spelledToday,
                    spelledWrongIds = detail.spelledWrongIds,
                    onToggleWordBook = { viewModel.toggleWordBook(it) }
                )
            } else {
                if (detail.dueTomorrow.isNotEmpty()) {
                    Text(
                        text = "这些搭配的复习时间落在明天，今天可以先看一眼",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                DetailSection(
                    title = "明日待复习",
                    emptyText = "明天没有安排复习",
                    items = detail.dueTomorrow,
                    onToggleWordBook = { viewModel.toggleWordBook(it) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TabPill(modifier: Modifier = Modifier, text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        TextButton(
            onClick = onClick,
            modifier = modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Text(text = text, fontSize = 14.sp)
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    emptyText: String,
    items: List<Collocation>,
    spelledWrongIds: Set<Long> = emptySet(),
    onToggleWordBook: (Collocation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${items.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            items.forEach { item ->
                DetailRow(
                    item = item,
                    spelledWrong = spelledWrongIds.contains(item.id),
                    onToggleWordBook = { onToggleWordBook(item) }
                )
            }
        }
    }
}

@Composable
fun DetailRow(item: Collocation, spelledWrong: Boolean = false, onToggleWordBook: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleWordBook() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.english,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.chinese,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                if (item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (spelledWrong) {
                Text(
                    text = "拼错",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (item.isInWordBook) "已在生词本" else "加入生词本",
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isInWordBook) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
        }
    }
}

// ==================== 底部导航 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String
) {
    val items = listOf(
        NavItem("首页", Icons.Default.Home, Screen.Home.route),
        NavItem("生词本", Icons.Default.Edit, Screen.WordBook.route),
        NavItem("统计", Icons.Default.Star, Screen.Statistics.route),
        NavItem("设置", Icons.Default.Settings, Screen.Settings.route)
    )

    // 深色主题的底是极暗，浅色主题的底是米白 —— 导航栏必须跟着变，
    // 否则浅色模式下会压一条突兀的黑色横条。
    val darkTheme = MaterialTheme.colorScheme.background == DarkBase
    // 透明度要足够低，让壁纸能透出来：
    // 之前用 0.92/0.94，视觉上就是一条纯黑/纯白的实心横条，
    // 和铺满整屏的壁纸割裂，看起来像底没铺满。
    val barColor = if (darkTheme) {
        Color.Black.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    val onBar = if (darkTheme) Color.White else Color(0xFF14151F)
    // 选中项统一用琥珀色（与首页 Learn/Review 的强调色一致）
    val selectedTint = AccentAmber

    NavigationBar(containerColor = barColor, contentColor = onBar) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val itemColor = if (selected) selectedTint else onBar.copy(alpha = 0.7f)
            NavigationBarItem(
                icon = {
                    Icon(item.icon, contentDescription = item.label)
                },
                label = {
                    Text(text = item.label, fontSize = 11.sp, color = itemColor)
                },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedTint,
                    unselectedIconColor = onBar.copy(alpha = 0.7f),
                    indicatorColor = if (darkTheme) {
                        Color.White.copy(alpha = 0.12f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    }
                ),
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomBarHeightSpacer() {
    // NavigationBar 默认高度 80dp，这里多留 12dp 余量。
    // 导航栏现在是半透明浮层，留白不足会让最后一张卡片被盖住（设置页、统计页都遇到过）。
    Spacer(modifier = Modifier.height(92.dp))
}
