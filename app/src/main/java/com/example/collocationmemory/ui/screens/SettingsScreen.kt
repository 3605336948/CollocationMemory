package com.example.collocationmemory.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.local.BookCount
import com.example.collocationmemory.data.repository.ImportResult
import com.example.collocationmemory.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val learnBatchSize by viewModel.learnBatchSize.collectAsState()
    val reviewBatchSize by viewModel.reviewBatchSize.collectAsState()
    val shuffleReview by viewModel.shuffleReview.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val checkingUpdate by viewModel.checkingUpdate.collectAsState()
    val homeStats by viewModel.homeStats.collectAsState()
    val bookList by viewModel.bookList.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    // 标记本次选择文件是「导入到当前词本」还是「新建词本」
    var importAsNewBook by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val handle: (ImportResult) -> Unit = { result ->
                importMessage = when (result) {
                    is ImportResult.Success -> "成功导入 ${result.importedCount} 条搭配"
                    is ImportResult.Error -> result.message
                }
            }
            if (importAsNewBook) {
                viewModel.importJsonAsNewBook(context, it, handle)
            } else {
                viewModel.importJson(context, it, handle)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { BottomNavBar(navController = navController, currentRoute = Screen.Settings.route) }
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
            BookInfoCard(
                bookName = homeStats.bookName,
                bookList = bookList,
                onSwitchBook = { viewModel.switchBook(it) },
                onPreview = { navController.navigate(Screen.BookPreview.route) }
            )
            ImportCard(
                onImportClick = {
                    importAsNewBook = false
                    launcher.launch(arrayOf("application/json"))
                },
                onImportAsNewBookClick = {
                    importAsNewBook = true
                    launcher.launch(arrayOf("application/json"))
                }
            )
            ThemeCard(
                themeMode = themeMode,
                onThemeSelected = { viewModel.setThemeMode(it) }
            )
            BatchSizeCard(
                title = "每组学习数量",
                value = learnBatchSize,
                onValueChange = { viewModel.setBatchSizes(it, reviewBatchSize) }
            )
            BatchSizeCard(
                title = "每组复习数量",
                value = reviewBatchSize,
                onValueChange = { viewModel.setBatchSizes(learnBatchSize, it) }
            )
            ShuffleCard(
                enabled = shuffleReview,
                onToggle = { viewModel.setShuffleReview(it) }
            )
            SpeakCard(
                enabled = autoSpeak,
                onToggle = { viewModel.setAutoSpeak(it) }
            )
            UpdateCard(
                versionName = viewModel.currentVersionName,
                versionCode = viewModel.currentVersionCode,
                checking = checkingUpdate,
                onCheck = { viewModel.checkForUpdate() }
            )
            HelpCard(onClick = { navController.navigate(Screen.Help.route) })
            DangerCard(onClearClick = { showClearDialog = true })
            // 底部导航栏是半透明浮层，留出高度避免最后一张卡片被盖住
            BottomBarHeightSpacer()
            Spacer(modifier = Modifier.height(16.dp))
        }

        importMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { importMessage = null },
                confirmButton = {
                    TextButton(onClick = { importMessage = null }) {
                        Text("确定")
                    }
                },
                title = { Text("导入结果") },
                text = { Text(message) }
            )
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllData { showClearDialog = false }
                        }
                    ) {
                        Text("确定", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("取消")
                    }
                },
                title = { Text("清空学习数据") },
                text = { Text("将清空：学习进度、生词本、学习时长、连续打卡、每日统计。\n\n已保留：词库里的全部搭配（内置 117 条 + 你导入过的词库）不会被删除。此操作不可恢复。") }
            )
        }
    }
}

/**
 * 当前正在学习的词本。进度都是按词本统计的，所以这里要显示清楚。
 *
 * 词本切换入口也在这里：点书名弹出全部词本列表，选中即切过去
 * （原来这个切换器在首页的「今日概览」里，现在统一收进设置）。
 */
@Composable
fun BookInfoCard(
    bookName: String,
    bookList: List<BookCount> = emptyList(),
    onSwitchBook: (String) -> Unit = {},
    onPreview: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "当前单词本",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 词本切换器：点书名弹出词本列表，选中即切过去
            Box {
                Row(
                    modifier = Modifier
                        .clickable { menuExpanded = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bookName.ifBlank { "（未命名）" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "切换 ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    bookList.forEach { book ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = book.bookName,
                                        // fill = false：书名短时不要让出全部宽度，
                                        // 否则条数会被挤到最右边、紧贴着长书名
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (book.bookName == bookName) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "${book.count} 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            trailingIcon = {
                                if (book.bookName == bookName) {
                                    Text(text = "✓", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                if (book.bookName != bookName) {
                                    onSwitchBook(book.bookName)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 词库预览入口：没学之前先翻一遍，心里有数
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPreview() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "预览词库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ImportCard(
    onImportClick: () -> Unit,
    onImportAsNewBookClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "数据导入",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择 JSON 文件导入词条",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onImportClick) {
                Text("导入到当前单词本")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onImportAsNewBookClick) {
                Text("新建一个单词本导入", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ThemeCard(themeMode: Int, onThemeSelected: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeOption("明亮", 0, themeMode, onThemeSelected)
            ThemeOption("深邃", 1, themeMode, onThemeSelected)
            ThemeOption("跟随系统", 2, themeMode, onThemeSelected)
        }
    }
}

@Composable
fun ThemeOption(label: String, value: Int, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected == value,
                onClick = { onSelected(value) }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelected(value) })
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BatchSizeCard(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$value 个",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 5f..30f,
                steps = 24
            )
        }
    }
}

@Composable
fun ShuffleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "盲盒模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (enabled) {
                            "复习顺序随机 —— 抽到哪个背哪个"
                        } else {
                            "按优先级排：最该复习的先来"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

/**
 * 「自动朗读」：新学/复习翻到一张卡片时自动念一遍英文。
 *
 * 默认开启；关掉后卡片不再自动出声，但顶栏的「英」按钮照样能手动读
 * —— 课堂上不方便出声、或者想先自己默背的场景用得上。
 */
@Composable
fun SpeakCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动朗读",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (enabled) {
                            "翻到一张卡片就自动念一遍英文，点顶栏「英」可以再听一次"
                        } else {
                            "卡片不出声；想听的时候点顶栏的「英」按钮"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

/**
 * 「检查更新」。
 *
 * 平时 App 会自己在后台悄悄检查（启动时，最多 6 小时一次），
 * 这个入口是给「我知道你更新了，但我现在就要拉」的用户用的 ——
 * 手动检查会忽略「以后再说」的记录，一定给出结果。
 *
 * 顺带把当前版本号显示出来，学生反馈问题时能直接报版本。
 */
@Composable
fun UpdateCard(
    versionName: String,
    versionCode: Int,
    checking: Boolean,
    onCheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "版本与更新",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "当前版本 v$versionName（$versionCode）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "词库更新会自动进行，无需手动操作；这里可以手动检查新版本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCheck,
                enabled = !checking
            ) {
                Text(if (checking) "正在检查…" else "检查更新")
            }
        }
    }
}

@Composable
fun HelpCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "帮助与反馈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "遇到问题或想提建议，联系作者",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFeedbackScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帮助与反馈") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "遇到问题？",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "使用中遇到问题、发现错误，或者有想要的功能，都可以直接联系作者。" +
                            "反馈时如果能说明手机型号和具体操作步骤，会更容易定位问题。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "联系方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 邮箱：点一下打开邮件应用
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.sendEmail(context) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "邮箱",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viewModel.contactEmail,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "发邮件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.copyQqNumber(context) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "QQ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viewModel.contactQq,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "点击复制",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val opened = viewModel.openQqChat(context)
                    if (!opened) {
                        // 没装 QQ 或跳转失败：复制号码，让用户手动添加
                        viewModel.copyQqNumber(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "QQ 联系作者", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }

            Text(
                text = "点击后如果没有反应，说明手机里没有安装 QQ，号码会自动复制，去 QQ 里搜索添加即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DangerCard(onClearClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "危险操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "清空所有本地数据",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClearClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("清空数据")
            }
        }
    }
}
