package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import com.example.collocationmemory.ui.viewmodel.MainViewModel.LearnQuality
import com.example.collocationmemory.ui.theme.AppColors
import com.example.collocationmemory.util.SpeechHelper
import com.example.collocationmemory.util.WordImageStore

/**
 * 版权水印：这两个词本的资料由湖北小蓝书教育科技有限公司提供，
 * 卡片底部盖一行浅色水印做来源标注（很淡，不干扰背单词）；其他词本不显示。
 */
internal const val BLUE_BOOK_NAME = "蓝哥固定搭配单词本"
internal const val PAST_YEARS_BOOK_NAME = "往年固定搭配单词本"
internal const val BLUE_BOOK_WATERMARK = "资料版权归湖北小蓝书教育科技有限公司"

/** 属于需要标注版权的词本时返回水印文字，否则返回 null（不显示） */
internal fun watermarkFor(bookName: String?): String? =
    BLUE_BOOK_WATERMARK.takeIf {
        bookName == BLUE_BOOK_NAME || bookName == PAST_YEARS_BOOK_NAME
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(viewModel: MainViewModel, navController: NavController) {    val items by viewModel.sessionItems.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isAnswerRevealed by viewModel.isAnswerRevealed.collectAsState()
    val answered by viewModel.answered.collectAsState()
    val learnedElsewhere by viewModel.learnedElsewhere.collectAsState()
    val sessionComplete by viewModel.sessionComplete.collectAsState()
    val notebookEnglishes by viewModel.notebookEnglishes.collectAsState()
    // 会话存档还没读完之前不要下「没有待学习」的结论
    val sessionReady by viewModel.sessionReady.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    // 当前卡是否「还要再确认一次」：连续答对不足 2 次时为 true（v4.3.2 起所有词都需连对2次）
    // 当前卡已连续自评「认识」的次数（卡片上画 ●○ 进度用）
    val currentConfirmCount by viewModel.currentConfirmCount.collectAsState()
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
    LaunchedEffect(currentIndex, items, autoSpeak) {
        if (autoSpeak && currentIndex < items.size) {
            speakNow(items[currentIndex].english)
        }
    }

    LaunchedEffect(sessionComplete) {
        if (sessionComplete) {
            navController.navigate(Screen.Spell.createRoute("learn", 0)) {
                popUpTo(Screen.Learn.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        // 背景交给 AppBackground 画（当日壁纸 + 磨砂），Scaffold 自身要透明，
        // 否则会盖住壁纸、露出纯色底。
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("新学") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 「英」：再读一遍这个搭配（与「熟」同一行、同一套胶囊样式）
                    SpeakButton(
                        enabled = currentIndex < items.size,
                        onClick = { if (currentIndex < items.size) speakNow(items[currentIndex].english) }
                    )
                    // 「熟」：这个词我已经会了 —— 直接排入复习，跳过拼写环节。
                    // v4.3.2 起所有词都可跳（取消方案3的强制拼写），顶栏文字按钮样式。
                    FamiliarButton(
                        enabled = !answered,
                        onClick = {
                            viewModel.markFamiliarAndSkip()
                        }
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
            // 当日壁纸背景：与首页同一张、同样的透明度与磨砂
            AppBackground()
            // 图2风格的深蓝沉浸蒙版：压在壁纸上把整体色调沉下来，
            // 壁纸若隐若现 —— 既有图2的深色沉浸感，又不丢掉每日壁纸
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color(0xFF0B1D36).copy(alpha = 0.80f),
                                Color(0xFF14304F).copy(alpha = 0.86f)
                            )
                        )
                    )
            )

            if (!sessionReady) {
                // 上一次没做完的那组还在读盘，先什么都不说
            } else if (items.isEmpty()) {
                EmptySessionState(paddingValues, "没有待学习的搭配，先去导入数据吧", light = true)
            } else if (currentIndex >= items.size) {
                // 卡片已经过完、正在往拼写页走。这一帧不能取 items[currentIndex]，
                // 否则会数组越界崩掉（恢复会话时正好会经过这个瞬间）
                EmptySessionState(paddingValues, "正在进入拼写…", light = true)
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
                // 进度改成发丝线：原来的 6dp 彩色条在壁纸上太抢戏，
                // 与「单词才是主角」冲突。极细白线足以表达进度，又不出戏。
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
                val learnedInOtherBook = currentItem.english in learnedElsewhere &&
                    !currentItem.isLearned

                if (learnedInOtherBook) {
                    LearnedElsewhereNotice(
                        onMarkLearned = { viewModel.markAsLearnedEverywhere(currentItem.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 沉浸式学习卡（图2风格）：单词 + 提示行 + 例句；释义默认隐藏，
                // 点「看答案」或「模糊 / 不认识」才揭示（先回忆、再揭晓）。
                // 发音入口在顶栏「英」，卡内不再重复放按钮。
                ImmersiveLearnCard(
                    collocation = currentItem,
                    meaningRevealed = false,
                    confirmCount = currentConfirmCount,
                    watermark = watermarkFor(currentItem.bookName),
                    notebookActive = currentItem.english in notebookEnglishes,
                    onToggleNotebook = { viewModel.toggleNotebookEntry(currentItem) },
                    hint = "先回想词义再选择，想不起来「看答案」",
                    wordSize = 38
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 没揭示释义时给一个入口，让想先看一眼的人能主动查看；
                // 揭示后换成释义选项卡（按「；」拆多义，图2风格）
                if (!isAnswerRevealed) {
                    TextButton(onClick = { viewModel.revealAnswer() }) {
                        Text(
                            text = "看答案",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    MeaningCards(chinese = currentItem.chinese)
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 单词配图提示：先回忆、再看图，图帮记（没有配图的词不占位）
                WordImageHint(english = currentItem.english)
                Spacer(modifier = Modifier.height(8.dp))
                // 三键自评：不认识 / 模糊 / 认识
                RecallRatingRow(onRate = { quality -> viewModel.rateLearning(quality) })
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    }
}

/**
 * 答错后的一句话反馈。
 *
 * 文案原则：**信息优先，梗第二**。
 * 答错那一刻用户最需要的是「正确的长什么样」，所以正确答案由选项格标绿承担，
 * 这里只说一句短反馈；有真实数据（错了几次）就带上——真实数据比段子更有说服力，
 * 而且不会像网络热词那样过几个月就显旧。
 *
 * 刻意不做「抄一遍」：惩罚性语言会提高流失率，
 * 而本应用已有间隔重复算法，当场重考（见「再考一次」）比抄写更贴合记忆机制。
 */
@Composable
fun ErrorHint(errorCount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when {
                errorCount >= 3 -> "这词已经错第 $errorCount 次了"
                errorCount == 2 -> "第 2 次错了，明天它还来找你"
                else -> "错一次，明天复习会再来一遍"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.errorColor()
        )
    }
}

/**
 * 「这个词你在别的词本里已经背会了」提示。
 *
 * 出现在这里是因为：这个词当前不在新学队列里（已被跨词本排除），
 * 但仍会出现在学习/复习的视图里。给一个一键标熟的入口，
 * 让用户能主动把这个词标成「会了」，之后所有词本都不再拿它当新词。
 */
@Composable
fun LearnedElsewhereNotice(onMarkLearned: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.successColor().copy(alpha = 0.14f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "熟 · 已在其他词本学会",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.successColor()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "不用再背一遍",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0B3D1A).copy(alpha = 0.75f)
                )
            }
            TextButton(onClick = onMarkLearned) {
                Text("标记为已学会", color = AppColors.successColor())
            }
        }
    }
}

@Composable
fun OptionsGrid(
    options: List<String>,
    correctAnswer: String,
    selectedOption: String?,
    isRevealed: Boolean,
    onOptionClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { option ->
                    OptionButton(
                        modifier = Modifier.weight(1f),
                        text = option,
                        isCorrect = option == correctAnswer,
                        isSelected = option == selectedOption,
                        isRevealed = isRevealed,
                        onClick = { onOptionClick(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    text: String,
    isCorrect: Boolean,
    isSelected: Boolean,
    isRevealed: Boolean,
    onClick: () -> Unit
) {
    val success = AppColors.successColor()
    val error = AppColors.errorColor()
    // 磨砂玻璃：未揭晓时半透明白，壁纸能透出来；揭晓后用绿/粉淡色标对错
    val glass = Color.White.copy(alpha = 0.42f)
    val backgroundColor = when {
        !isRevealed -> glass
        isCorrect -> success.copy(alpha = 0.55f)
        isSelected -> error.copy(alpha = 0.55f)
        else -> glass
    }
    val contentColor = when {
        // 玻璃是浅色的，文字必须用深色，否则在浅壁纸上会糊掉
        !isRevealed -> Color.Black
        isCorrect -> Color(0xFF0B3D1A)
        isSelected -> Color(0xFF5A0A22)
        else -> Color.Black
    }

    Button(
        onClick = onClick,
        enabled = !isRevealed,
        modifier = modifier.heightIn(min = 82.dp),
        // 圆角从 12dp 提到 22dp：原来那个「白框」四角太锐利，
        // 和卡片 24dp 的圆角不是一套语言。
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            // 填充改为按钮自身（不再是一层玻璃套一个直角白框），
            // 整块都是圆的、也更大，视觉上就是一个整体。
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor,
            contentColor = contentColor,
            disabledContentColor = contentColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.50f)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 14.dp
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun OutlinedActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptySessionState(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    message: String,
    light: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            // light=true 用于深色沉浸背景（新学页深蓝蒙版）：白字才看得见
            color = if (light) Color.White.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 顶栏右侧的「熟」按钮：这个词我已经会了。
 *
 * 样式参考主流背词 App 的顶栏文字按钮（图3）：一个半透明小胶囊，
 * 一个字，贴在返回键对面的顶栏末端。壁纸是暗的，所以用白字白描边。
 *
 * @param enabled 答题中禁用（先答完这一票，别让状态打架）
 */
@Composable
fun FamiliarButton(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = if (enabled) 0.16f else 0.07f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = if (enabled) 0.55f else 0.25f)
        ),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = "熟",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * 顶栏的「英」：再读一遍当前这个搭配。
 *
 * 与「熟」用同一套胶囊样式（半透明底 + 白描边 + 白字），
 * 只是角标换成「英」，位置在「熟」左边，手指够得到、不会误触。
 *
 * @param enabled 词表为空时禁用（没有可读的东西）
 */
@Composable
fun SpeakButton(enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = if (enabled) 0.16f else 0.07f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = if (enabled) 0.55f else 0.25f)
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = "英",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * 卡片右上角的「记」小签：把这个搭配收进笔记本。
 *
 * 参考图3 的笔记按钮位（卡片角落的小图标），做成磨砂小胶囊：
 * 没收 → 深色「记」；已收 → 绿底「已记」。再点一次 = 从本子里拿掉。
 */
/**
 * 沉浸式学习卡（新学/复习共用主卡）。
 *
 * 与旧版「磨砂玻璃容器 + 深色字」不同：这一版文字直接压在当日模糊壁纸上（白字），
 * 去掉卡片外框，做成极简沉浸式 —— 与已确认的 HTML 原型一致。
 * 壁纸仍是 [AppBackground] 的当日壁纸 + 磨砂，没有改动。
 *
 * 内容顺序：单词 →（可选提示行）→ 例句；释义默认隐藏，[meaningRevealed] 为 true 时
 * 在卡内显示中文（先回忆、再揭晓，避免一上来就把答案摊开）。
 *
 * 参数化差异：
 * - [confirmCount] 为 null 时不显示「连对」进度（复习页没有连对机制，走间隔重复）
 * - [hint] 非空时在单词下方加一行提示（新学页图2风格：「先回想词义再选择…」）
 * - [wordSize] 单词字号（新学页图2风格用 38，复习页沿用 34）
 *
 * 注：音标目前没法显示 —— [com.example.collocationmemory.data.local.Collocation]
 * 实体里没有 phonetic 字段，要显示音标得先给 Room 实体加字段并做数据库迁移。
 */
@Composable
fun ImmersiveLearnCard(
    collocation: Collocation,
    meaningRevealed: Boolean,
    confirmCount: Int?,
    watermark: String?,
    notebookActive: Boolean,
    onToggleNotebook: () -> Unit,
    hint: String? = null,
    wordSize: Int = 34
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = collocation.english,
                fontSize = wordSize.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (wordSize + 8).sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            // 图2风格提示行：先自己想，想不起来再看答案
            hint?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = collocation.example,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.82f)
            )
            if (meaningRevealed) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = collocation.chinese,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
            confirmCount?.let { count ->
                Spacer(modifier = Modifier.height(20.dp))
                ConfirmDots(
                    confirmCount = count,
                    required = MainViewModel.LEARN_CONFIRM_REQUIRED
                )
            }
            watermark?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
        NotebookChip(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 上移并留出右边距：默认贴在卡片右上角会压住较长词组的词尾
                // （如 get useful information），挪到单词行上方的空白处
                .offset(y = (-16).dp)
                .padding(end = 8.dp),
            active = notebookActive,
            onClick = onToggleNotebook
        )
    }
}

/**
 * 词性标记：n./v./vt./vi./adj./adv./a./ad./prep./conj./pron./num./art./int./aux./abbr./modal v.
 *
 * 注意：尾部不能带 `\s*`——「vi. n.」连排词性之间只有一个空格，
 * 被前一个匹配的 `\s*` 吃掉后，后一个词性就找不到前导分隔符而匹配失败。
 */
private val POS_REGEX = Regex("""(?:^|[\s；;，,&])((?:modal v|vt|vi|adj|adv|n|v|a|ad|prep|conj|pron|num|art|int|aux|abbr)\.)""")

/**
 * 把词库释义拆成「词性 → 释义」多段（图2 风格：每个词性一张卡）。
 *
 * 词库释义是单行多词性挤在一起的格式，例如：
 * - 「vi. n. 突然发生，爆裂」→ 连排词性合并成一组共享文本：[(vi. n., 突然发生，爆裂)]
 * - 「v. 劈开；分裂 a. 裂开的」→ [(v., 劈开；分裂), (a., 裂开的)]
 * - 无词性标记（如「被称为；以……而闻名」）→ [(null, 整句)]，单张卡无词性行
 */
private fun splitMeanings(chinese: String): List<Pair<String?, String>> {
    val matches = POS_REGEX.findAll(chinese).toList()
    if (matches.isEmpty()) return listOf(null to chinese.trim())
    val result = mutableListOf<Pair<String?, String>>()
    // 第一个词性标记之前的文本（一般没有；有的话当作无词性段保留）
    if (matches.first().range.first > 0) {
        val head = chinese.substring(0, matches.first().range.first).trim()
        if (head.isNotEmpty()) result.add(null to head)
    }
    // 合并连续词性标记：「vi. n. 爆裂」→ 词性组 [vi., n.] 共享文本「爆裂」
    var i = 0
    while (i < matches.size) {
        val posGroup = mutableListOf(matches[i].groupValues[1])
        var j = i + 1
        while (j < matches.size) {
            val between = chinese.substring(matches[j - 1].range.last + 1, matches[j].range.first)
            // 两个词性标记之间只有分隔符（空格/&/；等）才算连排
            if (between.isBlank() || between.all { it in " &；;，," }) {
                posGroup.add(matches[j].groupValues[1])
                j++
            } else break
        }
        val start = matches[j - 1].range.last + 1
        val end = if (j < matches.size) matches[j].range.first else chinese.length
        val text = chinese.substring(start, end).trim().trimEnd('；', ';', '，', ',').trim()
        if (text.isNotEmpty()) result.add(posGroup.joinToString(" ") to text)
        i = j
    }
    return result
}

/**
 * 释义选项卡（新学页图2风格）：按词性拆成多张半透明深色卡。
 * 每张卡：词性小字（浅色）+ 释义大字（白色）。
 */
@Composable
fun MeaningCards(chinese: String) {
    val parts = remember(chinese) { splitMeanings(chinese) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        parts.forEach { (pos, text) ->
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color.White.copy(alpha = 0.20f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    pos?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 「连对 ●○」进度：告诉用户这张卡还要再确认几次才算学会。
 * 满 [required] 次才落库进复习，中间任何一次「模糊/不认识」都会清零重来。
 */
@Composable
fun ConfirmDots(confirmCount: Int, required: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "连对 $confirmCount/$required",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        repeat(required) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i < confirmCount) Color.White else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

/**
 * 单词配图提示（新学页「看释义」下方）。
 *
 * 图片来自更新仓库 library/word_images/，按需下载 + 本地缓存
 * （见 [com.example.collocationmemory.util.WordImageStore]）。
 * 没有配图的词**不显示任何占位**，保持沉浸式界面的干净。
 */
@Composable
fun WordImageHint(english: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var imageFile by remember(english) { mutableStateOf<java.io.File?>(null) }
    var checked by remember(english) { mutableStateOf(false) }

    LaunchedEffect(english) {
        imageFile = WordImageStore.load(context, english)
        checked = true
    }

    if (checked) {
        imageFile?.let { file ->
            val bitmap = remember(file) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            }
            bitmap?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "配图提示",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                )
            }
        }
    }
}

/**
 * 底部三键自评：不认识 / 模糊 / 认识。
 *
 * 等权、文字化、发丝描边，不抢戏 —— 让注意力留在单词上。
 * 每次点击都会推进到下一张；没学会的词排到队尾，本轮末尾会再出现。
 */
@Composable
fun RecallRatingRow(onRate: (LearnQuality) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RatingButton(
            modifier = Modifier.weight(1f),
            text = "不认识",
            onClick = { onRate(LearnQuality.UNKNOWN) }
        )
        RatingButton(
            modifier = Modifier.weight(1f),
            text = "模糊",
            onClick = { onRate(LearnQuality.FUZZY) }
        )
        RatingButton(
            modifier = Modifier.weight(1f),
            text = "认识",
            accent = true,
            onClick = { onRate(LearnQuality.KNOW) }
        )
    }
}

@Composable
fun RatingButton(
    modifier: Modifier = Modifier,
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    // 图2风格的选项卡按钮：半透明深色底 + 白描边，压在深蓝蒙版上是一组轻量选择卡；
    // 「认识」给更亮的底和描边做强调。每次点击都会推进到下一张；
    // 没学会的词排到队尾，本轮末尾会再出现。
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = if (accent) 0.20f else 0.10f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = if (accent) 0.55f else 0.28f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (accent) Color.White else Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
fun NotebookChip(
    modifier: Modifier = Modifier,
    active: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        // 降噪：未收录时是透明底 + 发丝白描边，不再是压在壁纸上的一块白底黑字方块；
        // 收录后（active）才给一点绿色反馈，让「已记」一眼可辨。功能与点击不变。
        color = if (active) {
            AppColors.successColor().copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) AppColors.successColor().copy(alpha = 0.7f)
            else Color.White.copy(alpha = 0.3f)
        ),
        modifier = modifier
    ) {
        Text(
            text = if (active) "已记" else "记",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
