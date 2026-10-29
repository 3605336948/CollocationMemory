package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import com.example.collocationmemory.ui.theme.AppColors
import com.example.collocationmemory.util.SpellMatcher

/**
 * 拼写巩固页 —— 与新学页同一套沉浸式风格：
 * 当日模糊壁纸直接铺满，白字压在上面，没有卡片外框；输入框与按钮用发丝线，
 * 去掉旧的灰色大卡片和实心大按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellScreen(
    viewModel: MainViewModel,
    navController: NavController,
    source: String,
    startIndex: Int
) {
    // spellItems = 会话词表 − 学新时点「熟」跳过拼写的词；
    // 复习选「只拼写记错的」时，ViewModel 已把 sessionItems 收窄成子集
    val items by viewModel.spellItems.collectAsState()
    // 断点续拼：拼到第几个词由 ViewModel 存着（原来存在组件里，切后台被杀就丢回第一个）
    val currentIndex by viewModel.spellIndex.collectAsState()
    // 会话存档是否读完。没读完就跳走，会把「继续上次」的进度直接踢没
    val sessionReady by viewModel.sessionReady.collectAsState()
    var userInput by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    // 正常拼完自己走：标记一下，免得「拼完清空词表」又被下面的空列表逻辑
    // 当成异常收场，再往首页推一次（会出现两个首页，返回键要按两下）
    var finished by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(items, sessionReady) {
        if (sessionReady && items.isEmpty() && !finished) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Spell.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        // 背景交给 AppBackground 画（当日壁纸 + 磨砂），Scaffold 自身要透明，
        // 否则会盖住壁纸、露出纯色底。
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("拼写巩固") },
                navigationIcon = {
                    // 返回键**不结束**这一组：进度已经落盘，回来还能接着拼
                    IconButton(onClick = { navController.navigate(Screen.Home.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
            // 当日壁纸背景：与首页/新学/复习同一张、同样的透明度与磨砂
            AppBackground()

            if (!sessionReady) {
                // 存档读取中，先什么都不说
            } else if (items.isEmpty()) {
                EmptySessionState(paddingValues, "没有拼写内容")
            } else if (currentIndex >= items.size) {
                CompletionView(
                    paddingValues = paddingValues,
                    onFinish = {
                        finished = true
                        // 这一组真的做完了 —— 清掉存档，下次点「新学/复习」是全新的一组
                        viewModel.endSession()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Spell.route) { inclusive = true }
                        }
                    }
                )
            } else {
                val item = items[currentIndex]
                /**
                 * 交卷：空输入不算「不会」，只提示一下 —— 否则一按回车就把答案摊开，
                 * 等于白送一次提示。容错匹配保留（词库里有省略号/占位符/括号/冠词）。
                 */
                val submitSpell: () -> Unit = {
                    if (showResult) {
                        // 结果已出，回车等同于「下一个」
                        viewModel.setSpellIndex(currentIndex + 1)
                        userInput = ""
                        showResult = false
                        isCorrect = false
                    } else if (userInput.isBlank()) {
                        android.widget.Toast
                            .makeText(context, "请输入内容", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        val correct = SpellMatcher.isCorrect(userInput, item.english)
                        isCorrect = correct
                        showResult = true
                        viewModel.submitSpellResult(item.id, correct)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${currentIndex + 1} / ${items.size}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(48.dp))

                    // 中文释义：视觉主体，直接压在壁纸上（同新学页的单词位）
                    Text(
                        text = item.chinese,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // 提交后：揭晓英文 + 例句（对/错用颜色说话）
                    if (showResult) {
                        Text(
                            text = item.english,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isCorrect) AppColors.successColor() else AppColors.errorColor()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = item.example,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    } else {
                        Spacer(modifier = Modifier.height(0.dp))
                    }

                    // 输入框：透明底 + 发丝边框 + 白字（聚焦时边框提亮）
                    SpellInput(
                        value = userInput,
                        enabled = !showResult,
                        onDone = { submitSpell() },
                        onValueChange = { if (!showResult) userInput = it }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 主操作：发丝描边按钮，同新学页三键的一套风格
                    if (!showResult) {
                        HairlineButton(
                            text = "提交",
                            enabled = true,
                            onClick = submitSpell
                        )
                    } else {
                        HairlineButton(
                            text = "下一个",
                            onClick = {
                                // 推进到下一个词，并立刻落盘 —— 断点续拼靠的就是这一步
                                viewModel.setSpellIndex(currentIndex + 1)
                                userInput = ""
                                showResult = false
                                isCorrect = false
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

/** 拼写输入框：透明底、白发丝边框、光标与文字都是白的，聚焦时边框提亮 */
@Composable
private fun SpellInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor =
        if (focused) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.35f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty() && !focused) {
                    Text(
                        text = "输入英文搭配",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
                inner()
            }
        }
    )
}

/** 发丝描边按钮：透明底 + 1dp 白边框，文字白/半透明白，同新学页三键风格 */
@Composable
private fun HairlineButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) 0.45f else 0.2f),
                RoundedCornerShape(12.dp)
            ),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CompletionView(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "完成本组学习！",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "坚持复习，记忆会更牢固",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(36.dp))
        HairlineButton(text = "返回首页", onClick = onFinish)
    }
}
