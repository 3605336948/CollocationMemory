package com.example.collocationmemory.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collocationmemory.data.local.BookCount
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.DailyRecord
import com.example.collocationmemory.data.local.NotebookEntry
import com.example.collocationmemory.data.local.SessionSnapshot
import com.example.collocationmemory.data.local.UserPreferencesDataStore
import com.example.collocationmemory.data.remote.AppUpdateInfo
import com.example.collocationmemory.data.repository.AppCheckResult
import com.example.collocationmemory.data.repository.CollocationRepository
import com.example.collocationmemory.data.repository.DayDetail
import com.example.collocationmemory.data.repository.ImportResult
import com.example.collocationmemory.data.repository.LibrarySyncResult
import com.example.collocationmemory.data.repository.ReviewResult
import com.example.collocationmemory.data.repository.SignInResult
import com.example.collocationmemory.data.repository.UpdateRepository
import com.example.collocationmemory.util.ReleaseNotes
import com.example.collocationmemory.util.SpacedRepetition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: CollocationRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    // ==================== Home Stats ====================
    //
    // 统计全部按「当前词本」计算：flatMapLatest 订阅当前词本名，
    // 词本一变就切换到新词本的各项统计，首页数字自动刷新。
    private val countStats = repository.currentBookName().flatMapLatest { book ->
        combine(
            repository.getTotalCount(book),
            repository.getLearnedCount(book),
            repository.getNewCount(book),
            repository.getDueCount(book),
            repository.getTomorrowDueCount(book),
            repository.getMasteredCount(book)
        ) { values ->
            CountStats(
                bookName = book,
                totalCount = values[0] as Int,
                learnedCount = values[1] as Int,
                newCount = values[2] as Int,
                dueCount = values[3] as Int,
                tomorrowDueCount = values[4] as Int,
                masteredCount = values[5] as Int
            )
        }
    }

    private val recordStats = repository.currentBookName().flatMapLatest { book ->
        combine(
            repository.getTodayRecordFlow(),
            repository.getTotalMinutesFlow(),
            repository.getTotalLearnedFlow(),
            repository.getWordBook(book)
        ) { todayRecord, totalMinutes, totalLearned, wordBook ->
            RecordStats(todayRecord, totalMinutes, totalLearned, wordBook.size)
        }
    }

    val homeStats = combine(countStats, recordStats) { counts, records ->
        HomeStats(
            bookName = counts.bookName,
            totalCount = counts.totalCount,
            learnedCount = counts.learnedCount,
            newCount = counts.newCount,
            dueCount = counts.dueCount,
            tomorrowDueCount = counts.tomorrowDueCount,
            masteredCount = counts.masteredCount,
            todayRecord = records.todayRecord,
            totalMinutes = records.totalMinutes,
            totalLearned = records.totalLearned,
            wordBookCount = records.wordBookCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats())

    /** 所有词本里已经学会的英文词，用于给当前词本的学习项打「熟」标记 */
    val learnedElsewhere: StateFlow<Set<String>> = repository.getLearnedEnglish()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** 全部词本（名 + 条数），首页切换器用 */
    val bookList: StateFlow<List<BookCount>> = repository.observeBookCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 当前词本的全部词条（含已学/未学），设置页「预览词库」用。
     *
     * 与生词本不同：这里是**整个词本**的清单，让用户在学之前就能翻一遍
     * 心里有数；已学的词在列表里标出来。
     */
    val bookPreviewItems: StateFlow<List<Collocation>> = repository.currentBookName()
        .flatMapLatest { book -> repository.getAllCollocations(book) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 全部词本的全部词条，词库预览页**跨词本搜索**用。
     *
     * 搜索词为空时预览页只显示当前词本；一旦输入搜索词，就改在这份全量数据里
     * 过滤（英文或中文），让用户能搜到其他词本里的词，结果卡上标注所属词本。
     */
    val allBooksPreviewItems: StateFlow<List<Collocation>> = repository.getAllBookCollocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 切换当前学习的词本；首页的进度、新学/复习队列都会跟着切过去 */
    fun switchBook(bookName: String) {
        viewModelScope.launch {
            repository.setCurrentBookName(bookName)
            // 换词本了，上一本的半截会话没有意义（词也对不上了），一并清掉
            endSession()
        }
    }

    // ==================== 版本公告 ====================
    //
    // 每个版本的改动写在 util/ReleaseNotes.kt 里。启动时按「上次看过的版本号」
    // 取没看过的那几条弹给用户，点关闭就把当前版本号写进去，之后不再打扰。
    //
    // 注意：这两个字段要在版权声明/更新弹窗**之前**声明 —— 它们的 init 会跑到
    // maybeShowPendingUpdate()，那里要读 _announcement 判断有没有公告压在上面。
    private val _announcement = MutableStateFlow<List<ReleaseNotes.Note>>(emptyList())

    /** 待展示的版本公告；空列表表示没有要展示的 */
    val announcement: StateFlow<List<ReleaseNotes.Note>> = _announcement

    init {
        viewModelScope.launch {
            val seenVersion = repository.announcementVersion().first()
            _announcement.value = ReleaseNotes.since(seenVersion)
        }
    }

    /** 关掉公告：记下当前版本号，同一条以后不再出现 */
    fun dismissAnnouncement() {
        viewModelScope.launch {
            repository.setAnnouncementVersion(currentVersionCode)
            _announcement.value = emptyList()
            // 公告期间攒下的更新提示，关掉后再弹
            maybeShowPendingUpdate()
        }
    }

    // ==================== 版权声明 ====================
    private val _showCopyrightNotice = MutableStateFlow(false)

    /** 是否展示「版权与使用声明」弹窗（首次启动必须看到） */
    val showCopyrightNotice: StateFlow<Boolean> = _showCopyrightNotice

    /**
     * 版权声明是否已经问过用户了。
     *
     * 更新弹窗必须等它 —— 不然首次启动时两个弹窗会叠在一起，
     * 而且「必须先同意声明」的前置条件也会被绕过。
     */
    private var copyrightDecided = false

    init {
        viewModelScope.launch {
            _showCopyrightNotice.value = !repository.copyrightNoticeAccepted().first()
            copyrightDecided = true
            // 声明已经处理完了，如果启动检查时攒下了待提示的更新，这时候弹出来
            maybeShowPendingUpdate()
        }
    }

    /** 点了「我同意」：本次进入软件，但下次启动仍会提示 */
    fun agreeCopyrightNoticeOnce() {
        _showCopyrightNotice.value = false
        maybeShowPendingUpdate()
    }

    /** 点了「同意，下次不再提示」：写偏好，之后启动不再弹 */
    fun agreeCopyrightNoticeForever() {
        viewModelScope.launch {
            repository.setCopyrightNoticeAccepted(true)
            _showCopyrightNotice.value = false
            maybeShowPendingUpdate()
        }
    }

    // ==================== 应用内更新 ====================
    //
    // 分两条通道（详见 UpdateRepository）：
    //   · 词库热更新：静默进行，用户完全无感，结果只用一句 Toast 带过；
    //   · 整包更新：弹窗提示 → App 内下载 → 跳系统安装页。
    //
    // 检查时机：每次冷启动后台静默检查一次（受 CHECK_INTERVAL_MS 限流），
    // 用户在设置页点「检查更新」则立刻检查、且忽略「以后再说」的记录。

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)

    /** 更新弹窗的状态；[UpdateUiState.Hidden] 表示不显示 */
    val updateState: StateFlow<UpdateUiState> = _updateState

    private val _updateMessage = MutableStateFlow<String?>(null)

    /** 一次性的轻提示（「已是最新版本」「词库已更新」），界面弹完即清空 */
    val updateMessage: StateFlow<String?> = _updateMessage

    /** 设置页「检查更新」按钮的转圈状态 */
    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate

    /** 本机版本号/版本名，设置页显示用 */
    val currentVersionName: String = updateRepository.currentVersionName()
    val currentVersionCode: Int = updateRepository.currentVersionCode()

    /** 待处理的整包更新（检查出来但可能还没轮到大弹窗） */
    private var pendingUpdate: Pair<AppUpdateInfo, Boolean>? = null

    /** 已下载并校验通过的更新包 */
    private var downloadedApk: java.io.File? = null

    init {
        viewModelScope.launch { silentUpdateCheck() }
    }

    private fun maybeShowPendingUpdate() {
        if (!copyrightDecided || _showCopyrightNotice.value) return
        // 版本公告还摊在屏幕上时不要压第二个弹窗出来
        if (_announcement.value.isNotEmpty()) return
        // 正在下载/准备安装时不要被覆盖回「发现新版本」
        if (_updateState.value is UpdateUiState.Downloading ||
            _updateState.value is UpdateUiState.ReadyToInstall ||
            _updateState.value is UpdateUiState.NeedPermission
        ) {
            return
        }
        val pending = pendingUpdate ?: return
        _updateState.value = UpdateUiState.Available(pending.first, pending.second)
    }

    /**
     * 冷启动时的静默检查：拉清单 → 悄悄更新词库 → 有新版本才弹窗。
     *
     * 全程不打扰用户；任何失败都只写日志，不影响 App 使用。
     */
    private suspend fun silentUpdateCheck() {
        try {
            // 两条通道各自限流：整包 6 小时（要弹窗，不能勤），词库 30 分钟（静默，要快）。
            // 早先两者共用 6 小时，导致刚推送的词库学生要等最多 6 小时才看到，已拆分。
            val appDue = updateRepository.isCheckDue()
            val libraryDue = updateRepository.isLibraryCheckDue()
            if (!appDue && !libraryDue) return

            // 先记时间再干活：即使后面失败了，也不会因为一直失败而每次启动都重试
            if (appDue) updateRepository.markChecked()
            if (libraryDue) updateRepository.markLibraryChecked()

            val snapshot = updateRepository.check(
                manual = false,
                includeApp = appDue,
                includeLibrary = libraryDue
            )
            handleLibraryResult(snapshot.library)

            when (val app = snapshot.app) {
                is AppCheckResult.Available -> {
                    pendingUpdate = app.info to app.forced
                    maybeShowPendingUpdate()
                }
                // 静默检查时「已是最新」和「失败」都不打扰用户
                else -> Unit
            }
        } catch (e: Exception) {
            android.util.Log.w("CollocationMemory", "静默检查更新失败", e)
        }
    }

    /** 用户在设置页点「检查更新」 */
    fun checkForUpdate() {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            try {
                val snapshot = updateRepository.check(manual = true, includeLibrary = true)
                updateRepository.markChecked()
                handleLibraryResult(snapshot.library)

                when (val app = snapshot.app) {
                    is AppCheckResult.Available -> {
                        pendingUpdate = app.info to app.forced
                        _updateState.value = UpdateUiState.Available(app.info, app.forced)
                    }
                    is AppCheckResult.UpToDate -> {
                        _updateMessage.value = "已是最新版本 v$currentVersionName"
                    }
                    is AppCheckResult.Failed -> {
                        _updateState.value = UpdateUiState.Failed(app.message)
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateUiState.Failed("检查更新失败：${e.message}")
            } finally {
                _checkingUpdate.value = false
            }
        }
    }

    /**
     * 词库热更新的结果 → 一句轻提示。
     *
     * 只有真的加了词/改了释义才提示，而且是「已自动更新」的口径 ——
     * 让用户知道内容变新了，但不需要他做任何事。
     */
    private fun handleLibraryResult(result: LibrarySyncResult) {
        if (result !is LibrarySyncResult.Done) return
        val parts = mutableListOf<String>()
        if (result.added > 0) parts += "新增 ${result.added} 条搭配"
        if (result.correctionsApplied > 0) parts += "订正 ${result.correctionsApplied} 条释义"
        if (parts.isNotEmpty()) {
            _updateMessage.value = "词库已更新：${parts.joinToString("，")}"
        }
    }

    /** 用户点「立即更新」：先确认安装权限，再开始下载 */
    fun startUpdate() {
        if (_updateState.value is UpdateUiState.Downloading) return
        val pending = pendingUpdate ?: return
        if (!updateRepository.canInstallApk()) {
            _updateState.value = UpdateUiState.NeedPermission(pending.first)
            return
        }
        beginDownload(pending.first)
    }

    /** 用户从「安装未知应用」设置页返回后回调 */
    fun onInstallPermissionReturn() {
        val pending = pendingUpdate ?: return
        if (updateRepository.canInstallApk()) {
            beginDownload(pending.first)
        }
        // 没开开关就什么都不做，弹窗停在 NeedPermission 让用户再看一次说明
    }

    private fun beginDownload(info: AppUpdateInfo) {
        _updateState.value = UpdateUiState.Downloading(info, downloaded = 0L, total = info.apkSize)
        viewModelScope.launch {
            val apk = updateRepository.downloadApk(info) { downloaded, total ->
                _updateState.value = UpdateUiState.Downloading(
                    info = info,
                    downloaded = downloaded,
                    total = if (total > 0) total else info.apkSize
                )
            }
            if (apk == null) {
                _updateState.value = UpdateUiState.Failed("更新包下载失败，请检查网络后重试")
            } else {
                downloadedApk = apk
                _updateState.value = UpdateUiState.ReadyToInstall(info)
            }
        }
    }

    /** 用户点「开始安装」：唤起系统安装界面 */
    fun installUpdatedApk() {
        val apk = downloadedApk
        val pending = pendingUpdate
        if (apk == null || pending == null) return
        if (!updateRepository.canInstallApk()) {
            _updateState.value = UpdateUiState.NeedPermission(pending.first)
            return
        }
        if (!updateRepository.installApk(apk)) {
            _updateState.value = UpdateUiState.Failed("没能唤起系统安装界面，请手动到文件管理里安装")
        }
        // 成功唤起后弹窗留着，用户装完切回来时还能看到「开始安装」按钮
    }

    /** 用户点「以后再说」 */
    fun skipUpdate() {
        val pending = pendingUpdate ?: return
        if (pending.second) return // 强制更新不给跳过
        viewModelScope.launch {
            updateRepository.markSkipped(pending.first.versionCode)
            // 顺手清掉下载缓存，别占着 21MB 空间
            updateRepository.clearDownloadedApks()
        }
        downloadedApk = null
        pendingUpdate = null
        _updateState.value = UpdateUiState.Hidden
    }

    /** 关掉弹窗 / 失败后关闭：不记「跳过」，下次启动该提示还会提示 */
    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Hidden
    }

    /** 下载失败后点「重试」 */
    fun retryUpdate() {
        val pending = pendingUpdate ?: return
        updateRepository.clearDownloadedApks()
        downloadedApk = null
        if (!updateRepository.canInstallApk()) {
            _updateState.value = UpdateUiState.NeedPermission(pending.first)
            return
        }
        beginDownload(pending.first)
    }

    /** 界面已经弹出过这条提示了 */
    fun consumeUpdateMessage() {
        _updateMessage.value = null
    }

    // ==================== 首页悬浮对战图标 ====================
    /** 拖动后的坐标（-1f 表示还没拖过，界面用默认位置） */
    val battleIconPosition: StateFlow<Pair<Float, Float>> = repository.battleIconPosition()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1f to -1f)

    /** 记住拖动后的位置，重启 App 仍在原处 */
    fun saveBattleIconPosition(x: Float, y: Float) {
        viewModelScope.launch { repository.setBattleIconPosition(x, y) }
    }

    // ==================== 今日 / 明日明细 ====================
    private val _dayDetail = MutableStateFlow(DayDetail())
    val dayDetail: StateFlow<DayDetail> = _dayDetail

    fun loadDayDetail() {
        viewModelScope.launch {
            val book = repository.currentBookName().first()
            _dayDetail.value = repository.fetchDayDetail(book)
        }
    }

    // ==================== Settings ====================
    // 默认「深邃」，与 UserPreferencesDataStore 的默认值保持一致
    private val _themeMode = MutableStateFlow(UserPreferencesDataStore.THEME_DARK)
    val themeMode: StateFlow<Int> = _themeMode

    private val _learnBatchSize = MutableStateFlow(10)
    val learnBatchSize: StateFlow<Int> = _learnBatchSize

    private val _reviewBatchSize = MutableStateFlow(10)
    val reviewBatchSize: StateFlow<Int> = _reviewBatchSize

    /** 盲盒模式：复习顺序随机 */
    private val _shuffleReview = MutableStateFlow(false)
    val shuffleReview: StateFlow<Boolean> = _shuffleReview

    /** 翻到一张卡片时自动朗读英文；关掉后仍可点顶栏的「英」手动读 */
    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak

    /**
     * 朗读一段英文（点「英」按钮时用）。
     *
     * 返回 false 表示这台设备没有可用的英文语音引擎 —— 界面据此提示一次，
     * 而不是静默无声让人以为按钮坏了。
     */
    fun speak(text: String): Boolean = com.example.collocationmemory.util.SpeechHelper.speak(text)

    // ==================== Sign In ====================
    private val _signInMessage = MutableStateFlow<String?>(null)
    val signInMessage: StateFlow<String?> = _signInMessage

    private val _continuousDays = MutableStateFlow(0)
    val continuousDays: StateFlow<Int> = _continuousDays

    // ==================== Learn / Review Session ====================
    private val _sessionItems = MutableStateFlow<List<Collocation>>(emptyList())
    val sessionItems: StateFlow<List<Collocation>> = _sessionItems

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isAnswerRevealed = MutableStateFlow(false)
    val isAnswerRevealed: StateFlow<Boolean> = _isAnswerRevealed

    /** 用户是否真的选了一个选项（false = 直接点了「看答案」） */
    private val _answered = MutableStateFlow(false)
    val answered: StateFlow<Boolean> = _answered

    /** 当前这条搭配累计答错的次数，用于答错文案里的真实数据 */
    private val _lastErrorCount = MutableStateFlow(0)
    val lastErrorCount: StateFlow<Int> = _lastErrorCount

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption: StateFlow<String?> = _selectedOption

    private val _sessionComplete = MutableStateFlow(false)
    val sessionComplete: StateFlow<Boolean> = _sessionComplete

    /**
     * 当前会话来自哪儿："learn" / "review"；空串表示**没有未完成的会话**。
     *
     * 之所以允许空串：会话快照要靠它判断「有没有东西可续」，以及要不要
     * 往本机写快照。默认 "learn" 会让「刚启动时也存在一个 learn 会话」成立，
     * 从而写出一份空快照。
     */
    private val _sessionSource = MutableStateFlow("")

    /** 当前会话来自哪儿；空串 = 没有会话 */
    val sessionSource: StateFlow<String> = _sessionSource

    /**
     * 会话做到哪一步了（cards / spellChoice / spell）。
     *
     * 单独拎出来是因为「卡片做完之后该去哪儿」由它决定：
     * 复习是弹「拼写全部 / 只拼写记错的」选择面板，新学是直接进拼写。
     */
    private val _sessionPhase = MutableStateFlow(SessionSnapshot.PHASE_CARDS)

    /** 拼写推进到第几个词。放在 ViewModel 里是为了断点续拼（原来存在组件里，切后台就丢） */
    private val _spellIndex = MutableStateFlow(0)
    val spellIndex: StateFlow<Int> = _spellIndex

    /**
     * 会话快照是否已经读完本机存档。
     *
     * 界面必须等它为 true 再下「没有内容」的结论 —— 存档是异步读的，
     * 抢跑会先闪一下「今天没有待复习的搭配」，甚至把用户直接踢回首页。
     */
    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady

    /**
     * 当前未完成的会话（没有则为 null）。
     * 首页拿它显示「继续上次…」入口，也用它判断按钮该续做还是开新的一组。
     */
    private val _activeSession = MutableStateFlow<SessionSnapshot?>(null)
    val activeSession: StateFlow<SessionSnapshot?> = _activeSession

    private val _optionPool = MutableStateFlow<List<String>>(emptyList())
    val optionPool: StateFlow<List<String>> = _optionPool

    /**
     * 本次会话里被点「熟」的词：直接排入复习、跳过拼写环节。
     *
     * 拼写页读的是 [spellItems]（sessionItems 过滤掉这些 id），
     * 「熟」标记本身走 [CollocationRepository.markLearnedNow]，
     * 排到明天零点复习，所以它不会凭空消失，只是不用当场拼写。
     */
    private val _spellExcludedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 本组要拼写的词 = 会话词表 − 点了「熟」的词 */
    val spellItems: StateFlow<List<Collocation>> =
        kotlinx.coroutines.flow.combine(_sessionItems, _spellExcludedIds) { items, excluded ->
            if (excluded.isEmpty()) items else items.filterNot { it.id in excluded }
        }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            emptyList()
        )

    /**
     * 本次新学会话里答错过的词（按 id 记录）。
     *
     * 驱动两件事：① 所有新学卡片都要连续答对 2 次才算学会（不论是否答错）；
     * ② 答错过的词首次复习间隔拉长到 2 天（hadError）。
     * 拼写不再强制（顶栏「熟」所有词都可跳过，v4.3.2 取消方案3的强制拼写）。
     * 随快照持久化，恢复后连对计数从 0 重新累计，2 天间隔仍然生效。
     */
    private val _sessionWrongIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 本次会话答错过的词（用于首次复习间隔 2 天的 hadError 判断），UI 已不再直接消费 */
    val sessionWrongIds: StateFlow<Set<Long>> = _sessionWrongIds

    /**
     * 每个新学卡片连续答对几次了（按 id 记录），满 [LEARN_CONFIRM_REQUIRED] 才算学会。
     *
     * v4.3.2 起对所有新学卡片生效（不再区分是否答错）。
     * 不持久化：会话恢复后从 0 重新累计 —— 恢复时已经攒下的「确认进度」丢了，
     * 重新答对 2 次最稳妥，避免恢复后一张卡被直接放行而漏掉强制确认。
     */
    private val _confirmCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * 当前这张卡还需要「再确认一次」吗？
     *
     * v4.3.2 起对所有新学卡片生效：连续答对还不足 [LEARN_CONFIRM_REQUIRED] 次时为 true。
     * 界面据此把「记住了」按钮换成「再确认一次」，点下去不落库、只把卡排到队尾重考。
     */
    val currentCardNeedsConfirm: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(
            _sessionItems, _currentIndex, _confirmCounts
        ) { items, index, counts ->
            if (index >= items.size) {
                false
            } else {
                needsAnotherConfirm(counts[items[index].id] ?: 0)
            }
        }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            false
        )

    /**
     * 本次复习里点了「忘记」的词：供复习结束后的「只拼写记错的」使用。
     * 「模糊」算没记牢但不算记错，只跟「记错」走。
     */
    private val _reviewWrongIds = MutableStateFlow<Set<Long>>(emptySet())
    val reviewWrongIds: StateFlow<Set<Long>> = _reviewWrongIds

    private var sessionStartTime: Long = 0L
    private var learnedCountInSession: Int = 0
    private var reviewedCountInSession: Int = 0

    init {
        viewModelScope.launch {
            repository.themeMode().collect { _themeMode.value = it }
        }
        viewModelScope.launch {
            repository.learnBatchSize().collect { _learnBatchSize.value = it }
        }
        viewModelScope.launch {
            repository.reviewBatchSize().collect { _reviewBatchSize.value = it }
        }
        viewModelScope.launch {
            repository.shuffleReview().collect { _shuffleReview.value = it }
        }
        viewModelScope.launch {
            repository.autoSpeak().collect { _autoSpeak.value = it }
        }
        viewModelScope.launch {
            checkSignIn()
        }
        // 修复 v4.1「恢复会话没重设计时起点」写坏的学习时长（只跑一次）
        viewModelScope.launch {
            try {
                repository.repairInflatedStudyMinutes()
            } catch (e: Exception) {
                android.util.Log.w("CollocationMemory", "修复学习时长数据失败", e)
            }
        }
        // 把上一次没做完的那一组接回来（切后台被系统杀进程后靠它兜底）
        viewModelScope.launch {
            try {
                val snapshot = repository.sessionSnapshot.first()
                if (snapshot != null && snapshot.isActiveNow()) {
                    restoreSession(snapshot)
                } else if (snapshot != null) {
                    // 过期/损坏的快照顺手清掉，别一直躺着
                    repository.clearSession()
                }
            } catch (e: Exception) {
                android.util.Log.w("CollocationMemory", "恢复未完成会话失败", e)
            } finally {
                _sessionReady.value = true
            }
        }
    }

    // ==================== 会话持久化（断点续做）====================
    //
    // 会话原本只活在内存里：切后台被系统杀进程后，词表、进度、拼写位置全丢。
    // 更糟的是答过的词在答题那一刻就排到了明天，所以重进 App 点「复习」
    // 只能拿到下一批 —— 用户看到的就是「刚才那组全不见了」。
    //
    // 现在每推进一步就把快照落盘，重启后按 id 把词条取回来接着做。

    /** 把当前会话状态写进本机存档 */
    private fun persistSession() {
        val source = _sessionSource.value
        val items = _sessionItems.value
        if (source.isEmpty() || items.isEmpty()) {
            // 没东西可续（比如这一组本来就取不到词）→ 别把上一组的存档留在盘上
            _activeSession.value = null
            viewModelScope.launch { runCatching { repository.clearSession() } }
            return
        }
        val snapshot = SessionSnapshot(
            source = source,
            phase = _sessionPhase.value,
            itemIds = items.map { it.id },
            index = _currentIndex.value,
            spellExcludedIds = _spellExcludedIds.value.toList(),
            reviewWrongIds = _reviewWrongIds.value.toList(),
            wrongIds = _sessionWrongIds.value.toList(),
            spellIndex = _spellIndex.value,
            updatedAt = System.currentTimeMillis()
        )
        _activeSession.value = snapshot
        viewModelScope.launch {
            runCatching { repository.saveSession(snapshot) }
        }
    }

    /** 从存档恢复会话：按 id 把词条取回来，并还原到当时那一步 */
    private suspend fun restoreSession(snapshot: SessionSnapshot) {
        val items = repository.getCollocationsByIds(snapshot.itemIds)
        if (items.isEmpty()) {
            // 词被删了/换了词本，这个快照没法续，清掉
            repository.clearSession()
            return
        }
        _sessionSource.value = snapshot.source
        _sessionPhase.value = snapshot.phase
        _sessionItems.value = items
        // 夹在最后一张之内：会话里卡片是「停在最后一张 + sessionComplete」，
        // 越界会让界面直接数组越界崩溃
        _currentIndex.value = snapshot.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        _spellExcludedIds.value = snapshot.spellExcludedIds.toSet()
        _reviewWrongIds.value = snapshot.reviewWrongIds.toSet()
        // wrongIds 随快照恢复（恢复后「熟」仍强制拼写、通过仍给 2 天间隔）；
        // confirmCounts 不恢复，恢复后从 0 重新累计 2 次（A2）
        _sessionWrongIds.value = snapshot.wrongIds.toSet()
        _spellIndex.value = snapshot.spellIndex.coerceAtLeast(0)
        // 仅「复习快照停在拼写范围未选」时置 true：接回复习页要靠它弹出选择面板。
        // 其余一律 false —— sessionComplete 只被 Learn/Review 页用来感知
        // 「本 session 刚做完」；恢复路径若误置 true（如 SPELL 阶段），用户点
        // Learn 开新会话后，晚到的恢复会把刚打开的新学页瞬间"推"进拼写页。
        // SPELL 直达由 sessionRoute 按快照路由，不依赖此标志。
        _sessionComplete.value =
            snapshot.source == SessionSnapshot.SOURCE_REVIEW &&
            snapshot.phase == SessionSnapshot.PHASE_SPELL_CHOICE
        _activeSession.value = snapshot
        // 恢复的会话从这一刻重新计时。不补这条的话 sessionStartTime 还是 0，
        // 结算时 (now - 0)/60000 会把「从 1970 年至今的分钟数」写进学习时长
        sessionStartTime = System.currentTimeMillis()
        generateOptions(items, _currentIndex.value)
    }

    /**
     * 结束这一组：清掉存档，之后「新学 / 复习」就是全新的一组。
     *
     * 只在用户**明确做完**时才调用（拼写完成回首页、选了「先不拼写」）。
     * 中途返回键离开不算结束 —— 那正是要靠快照接回来的场景。
     */
    fun endSession() {
        _sessionSource.value = ""
        _sessionPhase.value = SessionSnapshot.PHASE_CARDS
        _sessionItems.value = emptyList()
        _spellExcludedIds.value = emptySet()
        _reviewWrongIds.value = emptySet()
        _sessionWrongIds.value = emptySet()
        _confirmCounts.value = emptyMap()
        _currentIndex.value = 0
        _spellIndex.value = 0
        _sessionComplete.value = false
        _activeSession.value = null
        viewModelScope.launch {
            runCatching { repository.clearSession() }
        }
    }

    /** 拼写页推进到下一个词（断点续拼用） */
    fun setSpellIndex(index: Int) {
        if (_spellIndex.value == index) return
        _spellIndex.value = index
        persistSession()
    }

    // ==================== Import ====================
    /**
     * 导入 JSON 词库。
     *
     * 导入的词会归入**当前词本**，并且不再改写当前词本名 ——
     * 否则"从 A 本切到 B 本再导一次"会把词本名冲掉、进度也就对不上了。
     * 想新建独立词本，走 [setCurrentBookName] 显式切换。
     */
    fun importJson(context: Context, uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            try {
                val content = readTextFromUri(context, uri)
                val book = repository.currentBookName().first()
                val result = repository.importFromJson(uri, content, book)
                onResult(result)
            } catch (e: Exception) {
                onResult(ImportResult.Error("读取文件失败: ${e.message}"))
            }
        }
    }

    /**
     * 导入 JSON 并**新建一个独立词本**（词本名默认取文件名）。
     *
     * 用于「再加一个单词本背别的单词」：新词本有自己的进度统计，
     * 与旧词本里重复的词会自动显示为「熟」并跳过新学。
     */
    fun importJsonAsNewBook(context: Context, uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            try {
                val content = readTextFromUri(context, uri)
                val newBook = queryDisplayName(context, uri)?.takeIf { it.isNotBlank() } ?: "新词本"
                val result = repository.importFromJson(uri, content, newBook)
                if (result is ImportResult.Success && result.importedCount > 0) {
                    // 导入成功才切到新词本，让用户立刻看到它
                    repository.setCurrentBookName(newBook)
                }
                onResult(result)
            } catch (e: Exception) {
                onResult(ImportResult.Error("读取文件失败: ${e.message}"))
            }
        }
    }

    /**
     * 从 content uri 里取文件显示名（如 collocations_all.json）。
     * 保留给「按文件名新建词本」这类功能使用。
     */
    @Suppress("unused")
    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.removeSuffix(".json") else null
                    } else null
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): String {
        val builder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    builder.append(line)
                    line = reader.readLine()
                }
            }
        } ?: throw IllegalStateException("无法打开文件")
        return builder.toString()
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            endSession()
            repository.clearAllData()
            onComplete()
        }
    }

    // ==================== Sign In ====================
    private suspend fun checkSignIn() {
        when (val result = repository.checkAndUpdateSignIn()) {
            is SignInResult.SignedIn -> {
                _continuousDays.value = result.continuousDays
                _signInMessage.value = "连续签到 ${result.continuousDays} 天"
            }
            is SignInResult.AlreadySignedIn -> {
                _continuousDays.value = result.continuousDays
            }
        }
    }

    fun dismissSignInMessage() {
        _signInMessage.value = null
    }

    // ==================== Learn Session ====================
    fun startLearnSession() {
        // 同步先重置：上一轮 session 的 sessionComplete=true 若残留到首帧，
        // LearnScreen 的 LaunchedEffect 会在进入新学页的瞬间立刻跳去拼写页（竞态）
        _sessionComplete.value = false
        viewModelScope.launch {
            val batchSize = repository.learnBatchSize().first()
            val book = repository.currentBookName().first()
            val allNew = repository.getNewToLearn(book).first()
            val selected = allNew.take(batchSize)
            _sessionItems.value = selected
            _currentIndex.value = 0
            _isAnswerRevealed.value = false
            _selectedOption.value = null
            _sessionComplete.value = false
            _sessionSource.value = SessionSnapshot.SOURCE_LEARN
            _sessionPhase.value = SessionSnapshot.PHASE_CARDS
            _spellIndex.value = 0
            _spellExcludedIds.value = emptySet()
            _reviewWrongIds.value = emptySet()
            _sessionWrongIds.value = emptySet()
            _confirmCounts.value = emptyMap()
            learnedCountInSession = 0
            reviewedCountInSession = 0
            sessionStartTime = System.currentTimeMillis()
            generateOptions(selected, 0)
            // 立刻落一份快照：哪怕这一秒就被系统杀掉，重启也能接着做
            persistSession()
        }
    }

    // ==================== Review Session ====================
    fun startReviewSession() {
        // 同步先重置，理由同 startLearnSession（避免残留 true 导致进页即跳转）
        _sessionComplete.value = false
        viewModelScope.launch {
            val batchSize = repository.reviewBatchSize().first()
            val shuffle = repository.shuffleReview().first()
            val book = repository.currentBookName().first()
            val allDue = repository.getDueReview(book = book, shuffle = shuffle)
            val selected = allDue.take(batchSize)
            _sessionItems.value = selected
            _currentIndex.value = 0
            _isAnswerRevealed.value = false
            _selectedOption.value = null
            _sessionComplete.value = false
            _sessionSource.value = SessionSnapshot.SOURCE_REVIEW
            _sessionPhase.value = SessionSnapshot.PHASE_CARDS
            _spellIndex.value = 0
            _spellExcludedIds.value = emptySet()
            _reviewWrongIds.value = emptySet()
            _sessionWrongIds.value = emptySet()
            _confirmCounts.value = emptyMap()
            learnedCountInSession = 0
            reviewedCountInSession = 0
            sessionStartTime = System.currentTimeMillis()
            generateOptions(selected, 0)
            persistSession()
        }
    }

    private suspend fun generateOptions(items: List<Collocation>, index: Int) {
        if (items.isEmpty() || index >= items.size) {
            _optionPool.value = emptyList()
            return
        }
        val current = items[index]
        // 干扰项优先从当前词本里取（同词本的释义更有迷惑性）；
        // 词本太小导致不够 3 个时，再补上其它词本的词，保证选项永远有 4 个。
        val book = repository.currentBookName().first()
        val inBook = repository.getAllCollocations(book).first()
        val pool = if (inBook.size >= 4) inBook else inBook + repository.getAllBookCollocations().first()

        val distractors = pool
            .filter { it.chinese != current.chinese }
            .map { it.chinese }
            .distinct()
            .shuffled()
            .take(3)
            .toMutableList()

        while (distractors.size < 3) {
            distractors.add("选项 ${distractors.size + 1}")
        }
        val options = (distractors + current.chinese).shuffled()
        _optionPool.value = options
    }

    fun revealAnswer() {
        _answered.value = false
        _isAnswerRevealed.value = true
    }

    /**
     * 选中一个选项。
     *
     * 关键：**答错的那一刻就记账**（累加 errorCount、加入生词本、记入当天统计）。
     * 不能等到点「下一个」才记 —— 否则用户答错后直接退出，
     * 这次错误就丢了（这正是之前漏记的原因）。
     */
    fun selectOption(option: String) {
        _selectedOption.value = option
        _answered.value = true
        _isAnswerRevealed.value = true

        val items = _sessionItems.value
        val index = _currentIndex.value
        if (index >= items.size) return
        val current = items[index]
        if (option == current.chinese) {
            // 选对了：累计「连续答对次数」，满 [LEARN_CONFIRM_REQUIRED] 次才算学会。
            // v4.3.2 起对所有新学卡片生效（不论是否答错），确保真正掌握才进复习。
            val next = (_confirmCounts.value[current.id] ?: 0) + 1
            _confirmCounts.value = _confirmCounts.value + (current.id to next)
            return
        }

        viewModelScope.launch {
            // 只累加错误次数；「今日学习」的登记统一交给 submitLearnResult，
            // 避免同一条同时进 learnedIds 和 spelledIds 造成计数口径混乱
            _lastErrorCount.value = repository.recordLearnFailure(current.id)
            // 答错：记下这个词（驱动确认机制 / 强制拼写 / 2 天间隔），确认次数清零重新累计
            _sessionWrongIds.value = _sessionWrongIds.value + current.id
            _confirmCounts.value = _confirmCounts.value - current.id
        }
    }

    /**
     * 「再考一次」：把当前这张卡重新排到本次队列末尾，稍后再考一遍。
     *
     * 这是「答错后强化」的替代方案 —— 不做罚抄。
     * 罚抄对学生是惩罚性语言，会提高流失率；而且本应用已经有间隔重复，
     * 当场重考一遍比抄写更贴合记忆机制，也不会打断学习节奏。
     */
    fun requeueCurrentCard() {
        val items = _sessionItems.value.toMutableList()
        val index = _currentIndex.value
        if (index >= items.size) return
        val current = items.removeAt(index)
        items.add(current)
        _sessionItems.value = items
        _isAnswerRevealed.value = false
        _selectedOption.value = null
        _answered.value = false
        // 队列顺序变了，同步落盘（否则重启后重考的那张会回到原位）
        persistSession()
        viewModelScope.launch { generateOptions(items, index) }
    }

    private fun nextCard() {
        val items = _sessionItems.value
        val next = _currentIndex.value + 1
        if (next >= items.size) {
            _sessionComplete.value = true
            // 卡片做完了，进入下一阶段：
            //   复习 → 先弹「拼写全部 / 只拼写记错的」选择面板
            //   新学 → 直接进拼写
            val phase =
                if (_sessionSource.value == SessionSnapshot.SOURCE_REVIEW) {
                    SessionSnapshot.PHASE_SPELL_CHOICE
                } else {
                    SessionSnapshot.PHASE_SPELL
                }
            _sessionPhase.value = phase
            _spellIndex.value = 0
            if (phase == SessionSnapshot.PHASE_SPELL && items.all { it.id in _spellExcludedIds.value }) {
                // 整组都点了「熟」，一个词都不用拼 —— 直接收尾，
                // 不然首页会挂着一条「还剩 0 个词没拼写」的空会话
                endSession()
            } else {
                persistSession()
            }
            finishSession()
        } else {
            _currentIndex.value = next
            _isAnswerRevealed.value = false
            _selectedOption.value = null
            _answered.value = false
            persistSession()
            viewModelScope.launch { generateOptions(items, next) }
        }
    }

    /**
     * 答错后点「下一个」：错误已在 selectOption 记过（errorCount / 生词本 / priority），
     * 这里不落学会、不排复习、不计入今日学习（A3 口径修正），直接推进到下一张。
     * 明天它仍是新学队列里的一员，重新来过。
     */
    fun finishCurrentCardAfterError() {
        nextCard()
    }

    /**
     * 把某个词标记为「已在其他词本学会」：所有词本里同英文的行一起标记。
     *
     * 用于用户看到「熟」提示后主动确认 —— 标记后这个词在所有词本都不再算新词。
     * 复习排期排到明天，所以它仍会进入正常的复习循环，不会凭空消失。
     */
    fun markAsLearnedEverywhere(collocationId: Long) {
        viewModelScope.launch {
            repository.markLearnedNow(collocationId)
        }
    }

    /**
     * 答「记住了 / 再确认一次 / 记错了」。
     *
     * 答错的情况在 selectOption 里已经记过账（errorCount / 生词本 / priority），
     * 这里只负责「确认学会」这一步。
     *
     * 关键点（v4.3.2 状态机）：所有新学卡片都要连续答对 [LEARN_CONFIRM_REQUIRED] 次才算学会。
     * 当「自评记住了」但连续答对还不足时，不落库、不推进，直接把卡排到队尾重考；
     * 满次数才真正落库。其中「答错过」的词额外拿到 2 天的首次复习间隔（hadError）。
     * 「自评记错了」的词，正常走落库路径（连带答错账一起）。
     */
    fun submitLearnResult(isCorrect: Boolean) {
        val items = _sessionItems.value
        val index = _currentIndex.value
        if (index < items.size) {
            val current = items[index]
            val alreadyRecorded = answered.value && selectedOption.value != current.chinese
            val wrongThisSession = current.id in _sessionWrongIds.value
            val confirmCount = _confirmCounts.value[current.id] ?: 0
            // 仅当「自评记住了」且连续答对还不足 2 次时，才重新排队、不落库。
            // （「记错了」自评不算记住，走下面正常落库分支。）
            if (isCorrect && needsAnotherConfirm(confirmCount)) {
                // 还没确认够次数：卡片去队尾稍后再考，不写入任何进度
                requeueCurrentCard()
                return
            }
            viewModelScope.launch {
                if (alreadyRecorded) {
                    // 错误已在选中时记过，这里只推进，避免重复计数
                    repository.applyLearnResult(current.id, true, answeredWrongThisSession = wrongThisSession)
                } else {
                    val newErrorCount = repository.applyLearnResult(
                        current.id, isCorrect, answeredWrongThisSession = wrongThisSession
                    )
                    _lastErrorCount.value = newErrorCount
                    repository.recordLearnedToday(current.id)
                    learnedCountInSession++
                }
                nextCard()
            }
        }
    }

    /** 新学自评档位：页面底部「认识 / 模糊 / 不认识」三键 */
    enum class LearnQuality { KNOW, FUZZY, UNKNOWN }

    /** 当前卡已连续自评「认识」的次数（卡片上画 ●○ 进度用） */
    val currentConfirmCount: StateFlow<Int> = combine(
        _sessionItems, _currentIndex, _confirmCounts
    ) { items, index, counts ->
        if (index >= items.size) 0 else (counts[items[index].id] ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * 新学「认识 / 模糊 / 不认识」三键自评。
     *
     * 沿用 v4.3.2 的确认口径：连续自评「认识」满 [LEARN_CONFIRM_REQUIRED](=2) 次才算初学通过；
     * 通过后落库，复习间隔交给现有 SpacedRepetition 按艾宾浩斯曲线动态排
     * （其中本次答错过的词首间隔 2 天）。
     *
     * - KNOW：满 2 次 → 落库 + 今日登记 + 推进；不足 2 次 → 记一次连对、卡片去队尾等再确认，不落库。
     * - FUZZY：连对清零、揭示释义、排到队尾重考，不记错误（首次复习仍 1 天）。
     * - UNKNOWN：连对清零、揭示释义、记一次错误（首次复习 2 天）、排到队尾重考。
     */
    fun rateLearning(quality: LearnQuality) {
        val items = _sessionItems.value
        val index = _currentIndex.value
        if (index >= items.size) return
        val current = items[index]

        when (quality) {
            LearnQuality.KNOW -> {
                val confirmCount = _confirmCounts.value[current.id] ?: 0
                if (needsAnotherConfirm(confirmCount)) {
                    // 还不到 2 次：先记连对，卡片去队尾等第二次确认，本轮不落库
                    // （卡片上的「连对 ●○」进度已在提示，不再弹 Toast 打扰）
                    _confirmCounts.value = _confirmCounts.value + (current.id to (confirmCount + 1))
                    requeueCurrentCard()
                    return
                }
                // 满 2 次：初学通过 → 落库，复习排期由 SpacedRepetition 负责
                viewModelScope.launch {
                    val wrongThisSession = current.id in _sessionWrongIds.value
                    repository.applyLearnResult(
                        current.id, true, answeredWrongThisSession = wrongThisSession
                    )
                    repository.recordLearnedToday(current.id)
                    learnedCountInSession++
                    nextCard()
                }
            }

            LearnQuality.FUZZY, LearnQuality.UNKNOWN -> {
                // 没记住：连对清零，先看一眼释义再走，随后排到队尾重考
                _confirmCounts.value = _confirmCounts.value - current.id
                _isAnswerRevealed.value = true
                viewModelScope.launch {
                    if (quality == LearnQuality.UNKNOWN) {
                        _lastErrorCount.value = repository.recordLearnFailure(current.id)
                        _sessionWrongIds.value = _sessionWrongIds.value + current.id
                    }
                    delay(600)
                    requeueCurrentCard()
                }
            }
        }
    }

    fun submitReviewResult(result: ReviewResult) {
        val items = _sessionItems.value
        val index = _currentIndex.value
        if (index < items.size) {
            val current = items[index]
            viewModelScope.launch {
                // 只传 id，由仓库层重新读取当前值，避免用旧快照覆盖其他字段
                repository.applyReviewResult(current.id, result)
                repository.recordReviewedToday(current.id)
                reviewedCountInSession++
                // 「忘记」记入本次的记错名单，复习结束后可供「只拼写记错的」使用
                if (result == ReviewResult.FORGET) {
                    _reviewWrongIds.value = _reviewWrongIds.value + current.id
                }
                nextCard()
            }
        }
    }

    /**
     * 新学卡片上点「熟」：这个词我已经会了。
     *
     * 效果与「记住了」一样排入复习（明天零点到期），但**跳过拼写环节** ——
     * 会拼的词没必要再罚一遍键盘。当天统计照记，队列推进到下一张卡。
     */
    fun markFamiliarAndSkip() {
        val items = _sessionItems.value
        val index = _currentIndex.value
        if (index >= items.size) return
        val current = items[index]
        // 顶栏「熟」：标记学会并跳过拼写环节（v4.3.2 起所有词都可跳，取消方案3的强制拼写）。
        // 会拼的词没必要再罚一遍键盘；当天统计照记，队列推进到下一张卡。
        viewModelScope.launch {
            repository.markLearnedNow(current.id)
            repository.recordLearnedToday(current.id)
            learnedCountInSession++
            _spellExcludedIds.value = _spellExcludedIds.value + current.id
            nextCard()
        }
    }

    /** 复习结束后点「拼写全部」：整组都拼（维持 sessionItems 不动即可） */
    fun prepareSpellAll() {
        _spellExcludedIds.value = emptySet()
        _spellIndex.value = 0
        _sessionPhase.value = SessionSnapshot.PHASE_SPELL
        persistSession()
    }

    /**
     * 复习结束后点「只拼写记错的」：把会话词表收窄成本次点过「忘记」的子集。
     * 之后拼写页读到的 sessionItems 就是这个小集合。
     */
    fun prepareSpellWrongOnly() {
        val wrong = _reviewWrongIds.value
        if (wrong.isEmpty()) return
        _sessionItems.value = _sessionItems.value.filter { it.id in wrong }
        _spellExcludedIds.value = emptySet()
        _spellIndex.value = 0
        _sessionPhase.value = SessionSnapshot.PHASE_SPELL
        persistSession()
    }

    /**
     * 复习做完、用户在拼写面板上点了「先不拼写，返回首页」。
     *
     * 这是**明确结束**这一组，要清掉快照；否则下次进来还会被问一遍。
     */
    fun skipSpellAndEndSession() {
        endSession()
    }

    /**
     * 拼写巩固的结果。
     * - 拼对：复习次数 +1（累计 3 次即算「已掌握」）
     * - 拼错：复习次数归零、明天重新复习，并加入生词本
     * 两种情况都会计入当天的拼写统计。
     *
     * 注意：这里只传 id，由仓库层重新读取数据库当前值再改。
     * 因为 item 是学习开始前的旧快照，整条写回会把「已学习 / 下次复习时间」覆盖成旧值。
     */
    fun submitSpellResult(collocationId: Long, isCorrect: Boolean) {
        viewModelScope.launch {
            try {
                repository.applySpellResult(collocationId, isCorrect)
                repository.recordSpelledToday(collocationId, isCorrect)
            } catch (e: Exception) {
                // 拼写结果写库失败不影响继续拼写
            }
        }
    }

    private fun finishSession() {
        viewModelScope.launch {
            // 防御：起点没设过（异常路径进来结算）按最少 1 分钟算；
            // 再夹一个 12 小时上限，任何异常都不能把学习时长写成天文数字
            val elapsed = if (sessionStartTime <= 0L) {
                60000L
            } else {
                System.currentTimeMillis() - sessionStartTime
            }
            val minutes = (elapsed / 60000).toInt().coerceIn(1, 720)
            repository.incrementTodayStats(learnedCountInSession, reviewedCountInSession, minutes)
        }
    }

    // ==================== Word Book ====================
    fun toggleWordBook(collocation: Collocation) {
        viewModelScope.launch {
            repository.toggleWordBook(collocation)
        }
    }

    // ==================== 笔记本 ====================
    /** 笔记本全部内容（本地存储，仅本机可见） */
    val notebookEntries: StateFlow<List<NotebookEntry>> = repository.notebookEntries
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /** 本子里已有的英文集合：卡片上的「记」按钮据此显示「记 / 已记」 */
    val notebookEnglishes: StateFlow<Set<String>> = notebookEntries
        .map { entries -> entries.map { it.english }.toSet() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptySet()
        )

    /** 点卡片上的「记」：已在本子里就移除，没在就收进来（开关式） */
    fun toggleNotebookEntry(collocation: Collocation, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val added = repository.addToNotebook(collocation)
            if (!added) {
                // 已存在 → 视为取消收藏，按英文找到对应条目删掉
                repository.notebookEntries.first()
                    .filter { it.english == collocation.english }
                    .forEach { repository.removeFromNotebook(it.id) }
            }
            onResult(added)
        }
    }

    fun updateNotebookNote(entryId: Long, note: String) {
        viewModelScope.launch {
            repository.updateNotebookNote(entryId, note)
        }
    }

    fun removeNotebookEntry(entryId: Long) {
        viewModelScope.launch {
            repository.removeFromNotebook(entryId)
        }
    }

    // ==================== Settings ====================
    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setBatchSizes(learn: Int, review: Int) {
        viewModelScope.launch {
            repository.setBatchSizes(learn, review)
        }
    }

    fun setShuffleReview(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShuffleReview(enabled)
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSpeak(enabled)
        }
    }

    // ==================== Stats ====================
    val recentRecords = repository.getRecentRecords(365)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== 帮助与反馈 ====================
    /** 作者的 QQ 号 */
    val contactQq: String = "3605336948"

    /** 作者的邮箱 */
    val contactEmail: String = "3605336948@qq.com"

    /** 复制 QQ 号到剪贴板（没装 QQ 或跳转失败时的兜底） */
    fun copyQqNumber(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ", contactQq))
            Toast.makeText(context, "已复制 QQ 号：$contactQq", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "复制失败，QQ 号：$contactQq", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 跳转到作者的 QQ 聊天窗口。
     * 找不到 QQ 时返回 false，由界面提示用户复制号码手动添加。
     *
     * 注意：这里**不用** resolveActivity 判断，因为 Android 11+ 的包可见性限制
     * 会让它误判成「没有应用能处理」，即使 QQ 已安装也会跳不出去。
     * 正确做法是直接 startActivity，用 ActivityNotFoundException 做兜底。
     */
    fun openQqChat(context: Context): Boolean {
        // 依次尝试几种 QQ 的 scheme：直接聊天 → 唤起 QQ → 加好友卡片
        val candidates = listOf(
            "mqqwpa://im/chat?chat_type=wpa&uin=$contactQq&version=1",
            "mqq://im/chat?chat_type=wpa&uin=$contactQq&version=1",
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$contactQq&card_type=person&source=qrcode"
        )
        for (uri in candidates) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: android.content.ActivityNotFoundException) {
                // 这个 scheme 没有应用能处理，试下一个
            } catch (e: Exception) {
                // 其它异常同样换下一个
            }
        }
        return false
    }

    /**
     * 用系统浏览器打开一个外部链接（如「蓝人单词对战」）。
     *
     * 和 QQ 跳转一样，**不用** resolveActivity 预判：Android 11+ 的包可见性
     * 限制会让它误判成「没有应用能处理」，即使有浏览器也跳不出去。
     * 直接 startActivity + ActivityNotFoundException 兜底才是可靠做法。
     */
    fun openExternalUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 发邮件给作者。同样直接用 startActivity，失败则复制邮箱。 */
    fun sendEmail(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$contactEmail")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "搭配记忆 反馈")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            copyEmail(context)
            false
        }
    }

    private fun copyEmail(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Email", contactEmail))
            Toast.makeText(context, "没有找到邮件应用，邮箱已复制：$contactEmail", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "邮箱：$contactEmail", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== Helpers ====================
    fun getWordBookFlow() = repository.currentBookName().flatMapLatest { book ->
        repository.getWordBook(book)
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /** 复习累计答对多少次算「已掌握」，与 DAO 里的判断保持一致 */
        private const val MASTERED_REPETITION = 3

        /**
         * 答错过的词，需要连续答对几次才算学会（方案3 需求①）。
         * 放在 companion 里，与排期阈值（SpacedRepetition）区分开：这是会话规则阈值。
         */
        const val LEARN_CONFIRM_REQUIRED = 2
    }
}

/**
 * 当前这张卡是否还需要「再确认一次」（连续答对还不足 [MainViewModel.LEARN_CONFIRM_REQUIRED] 次）。
 *
 * 抽成纯函数，是为了能在单元测试里直接验证状态机规则，不依赖 ViewModel 的协程 / Repository。
 * v4.3.2 起对所有新学卡片生效：不论本次会话有没有答过错，连续答对都需满 2 次才算学会。
 *
 * @param consecutiveCorrect 本次会话里它连续答对的次数
 */
internal fun needsAnotherConfirm(consecutiveCorrect: Int): Boolean =
    consecutiveCorrect < MainViewModel.LEARN_CONFIRM_REQUIRED

data class HomeStats(
    val bookName: String = "默认词书",
    val totalCount: Int = 0,
    val learnedCount: Int = 0,
    val newCount: Int = 0,
    val dueCount: Int = 0,
    val tomorrowDueCount: Int = 0,
    val masteredCount: Int = 0,
    val todayRecord: DailyRecord? = null,
    val totalMinutes: Int = 0,
    val totalLearned: Int = 0,
    val wordBookCount: Int = 0
)

private data class CountStats(
    val bookName: String,
    val totalCount: Int,
    val learnedCount: Int,
    val newCount: Int,
    val dueCount: Int,
    val tomorrowDueCount: Int,
    val masteredCount: Int
)

private data class RecordStats(
    val todayRecord: DailyRecord?,
    val totalMinutes: Int,
    val totalLearned: Int,
    val wordBookCount: Int
)

/**
 * 更新弹窗的状态机。
 *
 * 一个状态对应弹窗的一种样子，界面只负责「照着渲染」，
 * 所有跳转逻辑都收敛在 MainViewModel 里。
 */
sealed interface UpdateUiState {

    /** 不显示弹窗 */
    data object Hidden : UpdateUiState

    /** 发现新版本，等用户决定 */
    data class Available(val info: AppUpdateInfo, val forced: Boolean) : UpdateUiState

    /** 正在下载更新包 */
    data class Downloading(
        val info: AppUpdateInfo,
        val downloaded: Long,
        val total: Long
    ) : UpdateUiState {
        /** 0f..1f；服务器没给总大小时返回 0f，界面改用不确定进度条 */
        val progress: Float
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** 需要用户先去系统设置里打开「允许安装未知应用」 */
    data class NeedPermission(val info: AppUpdateInfo) : UpdateUiState

    /** 下载并校验完成，等用户点「开始安装」 */
    data class ReadyToInstall(val info: AppUpdateInfo) : UpdateUiState

    /** 出错了，可重试 */
    data class Failed(val message: String) : UpdateUiState
}

@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(
    private val repository: CollocationRepository,
    private val updateRepository: UpdateRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, updateRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
