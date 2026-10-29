package com.example.collocationmemory

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.collocationmemory.data.local.SessionSnapshot
import com.example.collocationmemory.ui.screens.CopyrightNoticeDialog
import com.example.collocationmemory.ui.screens.DayDetailScreen
import com.example.collocationmemory.ui.screens.HelpFeedbackScreen
import com.example.collocationmemory.ui.screens.HomeScreen
import com.example.collocationmemory.ui.screens.AnnouncementDialog
import com.example.collocationmemory.ui.screens.BookPreviewScreen
import com.example.collocationmemory.ui.screens.LearnScreen
import com.example.collocationmemory.ui.screens.NotebookScreen
import com.example.collocationmemory.ui.screens.ReviewScreen
import com.example.collocationmemory.ui.screens.SpellScreen
import com.example.collocationmemory.ui.screens.StatisticsScreen
import com.example.collocationmemory.ui.screens.SettingsScreen
import com.example.collocationmemory.ui.screens.UpdateDialog
import com.example.collocationmemory.ui.screens.WordBookScreen
import com.example.collocationmemory.ui.theme.AppThemeMode
import com.example.collocationmemory.ui.theme.CollocationMemoryTheme
import com.example.collocationmemory.ui.viewmodel.MainViewModel
import com.example.collocationmemory.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as CollocationMemoryApplication
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(app.repository, app.updateRepository)
            )
            val themeMode by viewModel.themeMode.collectAsState()

            CollocationMemoryTheme(
                themeMode = when (themeMode) {
                    0 -> AppThemeMode.LIGHT
                    1 -> AppThemeMode.DARK
                    else -> AppThemeMode.SYSTEM
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }

                // 启动时的版权与使用声明：未同意前覆盖在界面之上，必须先表态
                val showNotice by viewModel.showCopyrightNotice.collectAsState()
                if (showNotice) {
                    CopyrightNoticeDialog(
                        onAgree = { viewModel.agreeCopyrightNoticeOnce() },
                        onAgreeAndDontShowAgain = { viewModel.agreeCopyrightNoticeForever() }
                    )
                } else {
                    // 版本公告：更新后弹一次，看（关）过就不再出现。
                    // 与版权声明互斥，同时只可能有一个弹窗在最上层。
                    val notes by viewModel.announcement.collectAsState()
                    if (notes.isNotEmpty()) {
                        AnnouncementDialog(
                            notes = notes,
                            onDismiss = { viewModel.dismissAnnouncement() }
                        )
                    }
                }

                // 应用内更新：发现新版本才显示；词库热更新只弹一句轻提示
                UpdateDialogHost(viewModel)

                // 「已是最新版本」「词库已更新 N 条」这类一次性轻提示
                UpdateMessageHost(viewModel)
            }
        }
    }
}

/**
 * 挂载更新弹窗。
 *
 * 注意 [UpdateDialog] 必须**无条件**调用（内部用状态决定显不显示），
 * 因为它里面注册了「从设置页返回」的 ActivityResult 回调 ——
 * 条件调用会导致回调注册不上。
 */
@Composable
private fun UpdateDialogHost(viewModel: MainViewModel) {
    val updateState by viewModel.updateState.collectAsState()
    UpdateDialog(
        state = updateState,
        onUpdate = { viewModel.startUpdate() },
        onSkip = { viewModel.skipUpdate() },
        onDismiss = { viewModel.dismissUpdateDialog() },
        onInstall = { viewModel.installUpdatedApk() },
        onPermissionReturn = { viewModel.onInstallPermissionReturn() },
        onRetry = { viewModel.retryUpdate() }
    )
}

/** 把一次性的更新提示用 Toast 弹出来，弹完立刻清空，避免重组时重复弹 */
@Composable
private fun UpdateMessageHost(viewModel: MainViewModel) {
    val context = LocalContext.current
    val message by viewModel.updateMessage.collectAsState()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.consumeUpdateMessage()
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Learn : Screen("learn")
    data object Review : Screen("review")
    data object Spell : Screen("spell/{source}/{index}") {
        fun createRoute(source: String, index: Int) = "spell/$source/$index"
    }
    data object Statistics : Screen("statistics")
    data object Settings : Screen("settings")
    data object WordBook : Screen("wordbook")
    data object Notebook : Screen("notebook")
    data object BookPreview : Screen("bookpreview")
    data object Help : Screen("help")
    data object DayDetail : Screen("daydetail/{tab}") {
        const val TAB_TODAY = 0
        const val TAB_TOMORROW = 1
        fun createRoute(tab: Int) = "daydetail/$tab"
    }
}

/**
 * 未完成的会话该跳到哪个页面。
 *
 * 会话分三步（见 [SessionSnapshot]）：
 *   cards       还在过卡片      → 新学页 / 复习页
 *   spellChoice 卡片做完等选范围 → 复习页（它自己会弹选择面板）
 *   spell       正在拼写        → 拼写页，并且要落在断点的那个词上
 *
 * 注意拼写页的路由参数只作占位，真正的断点从 ViewModel 的 spellIndex 取，
 * 这样「从首页继续」和「系统还原页面」两条路径都落在同一个词上。
 */
fun sessionRoute(snapshot: SessionSnapshot): String = when (snapshot.source) {
    SessionSnapshot.SOURCE_LEARN ->
        if (snapshot.phase == SessionSnapshot.PHASE_CARDS) {
            Screen.Learn.route
        } else {
            Screen.Spell.createRoute(SessionSnapshot.SOURCE_LEARN, snapshot.spellIndex)
        }
    else -> when (snapshot.phase) {
        SessionSnapshot.PHASE_CARDS -> Screen.Review.route
        SessionSnapshot.PHASE_SPELL ->
            Screen.Spell.createRoute(SessionSnapshot.SOURCE_REVIEW, snapshot.spellIndex)
        // 拼写范围还没选：复习页会显示选择面板
        else -> Screen.Review.route
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Learn.route) {
            LearnScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Review.route) {
            ReviewScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Spell.route) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: "learn"
            val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
            SpellScreen(
                viewModel = viewModel,
                navController = navController,
                source = source,
                startIndex = index
            )
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Help.route) {
            HelpFeedbackScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.WordBook.route) {
            WordBookScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.Notebook.route) {
            NotebookScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.BookPreview.route) {
            BookPreviewScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.DayDetail.route) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab")?.toIntOrNull()
                ?: Screen.DayDetail.TAB_TODAY
            DayDetailScreen(
                viewModel = viewModel,
                navController = navController,
                initialTab = tab
            )
        }
    }
}
