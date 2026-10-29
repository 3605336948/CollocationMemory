package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.repository.ReviewResult
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import com.example.collocationmemory.ui.viewmodel.MainViewModel.LearnQuality
import com.example.collocationmemory.ui.theme.AppColors
import com.example.collocationmemory.util.SpeechHelper

/** 设备没有英文语音时的那句提示（学习/复习共用） */
fun showTtsUnavailableToast(context: android.content.Context) {
    android.widget.Toast.makeText(
        context,
        "这台设备没有可用的英文语音，朗读不可用（可在系统设置里安装英文语音包）",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: MainViewModel, navController: NavController) {
    val items by viewModel.sessionItems.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isAnswerRevealed by viewModel.isAnswerRevealed.collectAsState()
    val sessionComplete by viewModel.sessionComplete.collectAsState()
    val notebookEnglishes by viewModel.notebookEnglishes.collectAsState()
    val reviewWrongIds by viewModel.reviewWrongIds.collectAsState()
    // 会话存档是异步读的。读完之前不能下「没有待复习」的结论 ——
    // 否则冷启动时会先闪一句「今天没有待复习的搭配」，看着就像进度丢了
    val sessionReady by viewModel.sessionReady.collectAsState()

    // 复习完不再直接跳拼写页 —— 先问一句「拼写全部还是只拼写记错的」。
    // 全部记对时「只拼写记错的」置灰，用户可以选直接不拼。
    var showSpellChoice by remember { mutableStateOf(false) }
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    /** 读一遍当前这个词；设备没有英文语音时整进程只提示一次 */
    fun speakNow(text: String) {
        if (SpeechHelper.isUnavailable()) {
            if (SpeechHelper.consumeUnavailableWarning()) showTtsUnavailableToast(context)
            return
        }
        if (!viewModel.speak(text) && SpeechHelper.consumeUnavailableWarning()) {
            showTtsUnavailableToast(context)
        }
    }

    // 设备没有英文语音时，进学习页只提示一次，之后不再打扰
    LaunchedEffect(Unit) {
        // init 是异步的，等它给出结论再判断，别把「还没初始化完」当漏判
        SpeechHelper.awaitReady()
        if (SpeechHelper.isUnavailable() && SpeechHelper.consumeUnavailableWarning()) {
            showTtsUnavailableToast(context)
        }
    }

    // 翻到一张新卡片就自动读一遍英文（关掉「自动朗读」后不读，但仍可点「英」）
    LaunchedEffect(currentIndex, items, autoSpeak, isAnswerRevealed) {
        // 只在正面（还没翻答案）时读：翻面后用户看中文释义，再念一遍英文是打扰
        if (autoSpeak && !showSpellChoice && currentIndex < items.size && !isAnswerRevealed) {
            speakNow(items[currentIndex].english)
        }
    }

    LaunchedEffect(sessionComplete) {
        if (sessionComplete) showSpellChoice = true
    }

    Scaffold(
        // 背景交给 AppBackground 画（当日壁纸 + 磨砂），Scaffold 自身要透明
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("复习") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 「英」：再读一遍这个搭配（与新学页同一个按钮）
                    SpeakButton(
                        enabled = currentIndex < items.size,
                        onClick = { if (currentIndex < items.size) speakNow(items[currentIndex].english) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 当日壁纸背景：与首页、新学页同一张，透明度与磨砂一致
            AppBackground()

            if (!sessionReady) {
                // 上一次没做完的那组还在读盘，先什么都不说
            } else if (showSpellChoice) {
                SpellChoicePanel(
                    paddingValues = paddingValues,
                    wrongCount = reviewWrongIds.size,
                    onSpellAll = {
                        viewModel.prepareSpellAll()
                        navController.navigate(Screen.Spell.createRoute("review", 0)) {
                            popUpTo(Screen.Review.route) { inclusive = true }
                        }
                    },
                    onSpellWrong = {
                        viewModel.prepareSpellWrongOnly()
                        navController.navigate(Screen.Spell.createRoute("review", 0)) {
                            popUpTo(Screen.Review.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        // 「先不拼写」= 这一组到此为止，清掉存档，
                        // 否则下次进来又会被问一遍
                        viewModel.skipSpellAndEndSession()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Review.route) { inclusive = true }
                        }
                    }
                )
            } else if (items.isEmpty()) {
                EmptySessionState(paddingValues, "今天没有待复习的搭配")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${currentIndex + 1} / ${items.size}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 进度改成发丝线：与新学页同一套语言（极细白线，壁纸上不抢戏）
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / items.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = Color.White.copy(alpha = 0.45f),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                val currentItem = items[currentIndex]
                // 沉浸式学习卡：与新学页同一个组件（图1样式移植）——
                // 单词 + 例句直接压在壁纸上（白字），释义默认隐藏，
                // 点「看答案」才揭示（先回忆、再揭晓）。
                // 复习走间隔重复，没有「连对」机制，confirmCount 传 null 不显示。
                ImmersiveLearnCard(
                    collocation = currentItem,
                    meaningRevealed = isAnswerRevealed,
                    confirmCount = null,
                    watermark = watermarkFor(currentItem.bookName),
                    notebookActive = currentItem.english in notebookEnglishes,
                    onToggleNotebook = { viewModel.toggleNotebookEntry(currentItem) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 没揭示释义时给一个入口，让想先看一眼的人能主动查看
                if (!isAnswerRevealed) {
                    TextButton(onClick = { viewModel.revealAnswer() }) {
                        Text(
                            text = "看答案",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 三键自评：不认识 / 模糊 / 认识（与新学页同一个组件）
                RecallRatingRow(onRate = { quality ->
                    when (quality) {
                        LearnQuality.UNKNOWN -> viewModel.submitReviewResult(ReviewResult.FORGET)
                        LearnQuality.FUZZY -> viewModel.submitReviewResult(ReviewResult.VAGUE)
                        LearnQuality.KNOW -> viewModel.submitReviewResult(ReviewResult.KNOW)
                    }
                })
                Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * 复习结束后的拼写范围选择。
 *
 * 布局沿用本页既有按钮语言：圆角 16dp、56dp 高、一行两个（忘记/模糊）+ 整行（认识）。
 * 这里同样一行两个：「拼写全部」（主操作，绿色）/「只拼写记错的」（琥珀），
 * 下面给一行小字说明记错了几个；记错 0 个时该按钮置灰。
 */
@Composable
fun SpellChoicePanel(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    wrongCount: Int,
    onSpellAll: () -> Unit,
    onSpellWrong: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "复习完成！",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (wrongCount > 0) "本次记错 $wrongCount 个，拼一遍记得更牢" else "本次全部记住了，很棒！",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReviewButton(
                modifier = Modifier.weight(1f),
                text = "拼写全部",
                color = AppColors.successColor(),
                onClick = onSpellAll
            )
            ReviewButton(
                modifier = Modifier.weight(1f),
                text = if (wrongCount > 0) "只拼写记错的" else "记错 0 个",
                color = AppColors.warnColor(),
                enabled = wrongCount > 0,
                onClick = onSpellWrong
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("先不拼写，返回首页", color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun ReviewButton(
    modifier: Modifier = Modifier,
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.Unspecified else Color.White.copy(alpha = 0.6f)
        )
    }
}
