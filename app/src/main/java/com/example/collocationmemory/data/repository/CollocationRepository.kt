package com.example.collocationmemory.data.repository

import android.net.Uri
import com.example.collocationmemory.data.local.BookCount
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.CollocationDao
import com.example.collocationmemory.data.local.Converters
import com.example.collocationmemory.data.local.DailyRecord
import com.example.collocationmemory.data.local.DailyRecordDao
import com.example.collocationmemory.data.local.NotebookEntry
import com.example.collocationmemory.data.local.SessionSnapshot
import com.example.collocationmemory.data.local.UserPreferencesDataStore
import com.example.collocationmemory.util.SpacedRepetition
import com.example.collocationmemory.util.JsonImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

class CollocationRepository(
    private val collocationDao: CollocationDao,
    private val dataStore: UserPreferencesDataStore
) {
    private var dailyRecordDao: DailyRecordDao? = null

    fun setDailyRecordDao(dao: DailyRecordDao) {
        this.dailyRecordDao = dao
    }

    /**
     * 保护「往词库里写东西」这一系列操作的锁。
     *
     * 为什么必须要有：现在有两处在改词库，而且**可能同时跑** ——
     *   1. App 启动时把 assets 里的内置词库播种进数据库（Application）
     *   2. 启动后联网做词库热更新（UpdateRepository，也可以播种 + 订正）
     *
     * 而 [Collocation] 上**没有 english 唯一索引**（同一个英文在不同词本里
     * 本来就该各存一行），所以数据库层面拦不住重复。两者若同时读到
     * 「这个词还不存在」的快照，就会各插一条 —— 词库里出现重复词条。
     *
     * 加锁之后，「读现有词 → 算增量 → 写库」整个过程是原子的，不会交错。
     * 单机单进程场景下，这把锁的开销可以忽略。
     */
    private val libraryMutex = Mutex()

    // ==================== Collocation（按当前词本 book 过滤）====================
    //
    // 进度是「每本独立」的：列表与统计全部限定在 bookName = book 的范围内。
    // 唯一的例外是「会没会」这件事——它用 isLearned 表示，学会时会把
    // 同英文的所有行一起标记（markLearnedByEnglish），这样 B 本里重叠的词
    // 会自动变成「熟」，不需要再背一遍。

    fun getAllCollocations(book: String): Flow<List<Collocation>> =
        collocationDao.getAll(book).flowOn(Dispatchers.IO)

    fun getNewToLearn(book: String): Flow<List<Collocation>> =
        collocationDao.getNewToLearn(book).flowOn(Dispatchers.IO)

    fun getWordBook(book: String): Flow<List<Collocation>> =
        collocationDao.getWordBook(book).flowOn(Dispatchers.IO)

    fun getTotalCount(book: String): Flow<Int> =
        collocationDao.getTotalCount(book).flowOn(Dispatchers.IO)

    fun getLearnedCount(book: String): Flow<Int> =
        collocationDao.getLearnedCount(book).flowOn(Dispatchers.IO)

    fun getNewCount(book: String): Flow<Int> =
        collocationDao.getNewCount(book).flowOn(Dispatchers.IO)

    fun getMasteredCount(book: String): Flow<Int> =
        collocationDao.getMasteredCount(book).flowOn(Dispatchers.IO)

    fun getDueCount(book: String, currentTime: Long = System.currentTimeMillis()): Flow<Int> =
        collocationDao.getDueCount(book, currentTime).flowOn(Dispatchers.IO)

    /** 明天需要复习的数量 */
    fun getTomorrowDueCount(book: String): Flow<Int> = collocationDao
        .getDueCountBetween(book, tomorrowStart(), tomorrowStart() + DAY_MILLIS)
        .flowOn(Dispatchers.IO)

    /** 所有词本里已经学会的英文词，用于给当前词本打「熟」标记 */
    fun getLearnedEnglish(): Flow<List<String>> =
        collocationDao.getLearnedEnglish().flowOn(Dispatchers.IO)

    /** 全部词本的词条（用于小词本时补足选项干扰项） */
    fun getAllBookCollocations(): Flow<List<Collocation>> =
        collocationDao.getAllBooks().flowOn(Dispatchers.IO)

    /** 所有词本名，给切换器用 */
    fun observeBookNames(): Flow<List<String>> =
        collocationDao.observeBookNames().flowOn(Dispatchers.IO)

    /** 每个词本的条数，给切换器用 */
    fun observeBookCounts(): Flow<List<BookCount>> =
        collocationDao.observeBookCounts().flowOn(Dispatchers.IO)

    /**
     * 取今日到期复习队列（限定在当前词本）。
     *
     * @param shuffle 盲盒模式：把顺序完全打乱。今日到期的条数不变，
     *                只是「不知道下一个抽到哪个」——用现成的随机性替代抽卡系统，
     *                不需要新建任何养成/抽奖机制。
     */
    suspend fun getDueReview(
        book: String,
        currentTime: Long = System.currentTimeMillis(),
        shuffle: Boolean = false
    ): List<Collocation> =
        withContext(Dispatchers.IO) {
            try {
                val due = collocationDao.getDueReview(book, currentTime)
                if (shuffle) {
                    due.shuffled()
                } else {
                    due.sortedByDescending { item ->
                        val overdueDays = maxOf(0L, (currentTime - item.nextReviewDate) / DAY_MILLIS)
                        val unfamiliarity = maxOf(1.0, (DEFAULT_EF - item.easinessFactor + 0.1) * 10)
                        overdueDays * unfamiliarity + item.priority
                    }
                }
            } catch (e: Exception) {
                throw RepositoryException("获取复习队列失败: ${e.message}", e)
            }
        }

    suspend fun updateCollocation(collocation: Collocation) = withContext(Dispatchers.IO) {
        try {
            collocationDao.update(collocation)
        } catch (e: Exception) {
            throw RepositoryException("更新搭配失败: ${e.message}", e)
        }
    }

    suspend fun toggleWordBook(collocation: Collocation) = withContext(Dispatchers.IO) {
        try {
            collocationDao.updateWordBookStatus(collocation.id, !collocation.isInWordBook)
        } catch (e: Exception) {
            throw RepositoryException("更新生词本状态失败: ${e.message}", e)
        }
    }

    /**
     * 只记录「这次答错了」这一件事：错误次数 +1、加入生词本、提高优先级。
     *
     * 单独抽出来是因为：用户点错选项的那一刻就该记账，
     * 不能等他点「下一个」——他可能直接退出，这次错误就丢了。
     * 返回累加后的错误次数，供界面显示真实数据。
     */
    suspend fun recordLearnFailure(collocationId: Long): Int = withContext(Dispatchers.IO) {
        try {
            val current = collocationDao.getById(collocationId) ?: return@withContext 0
            val updated = current.copy(
                isInWordBook = true,
                priority = current.priority + 50,
                errorCount = current.errorCount + 1
            )
            collocationDao.update(updated)
            updated.errorCount
        } catch (e: Exception) {
            throw RepositoryException("记录答错失败: ${e.message}", e)
        }
    }

    /**
     * 套用一次「新学」的结果。先从数据库读当前值再改，避免用界面快照覆盖排期字段。
     * 返回这条搭配累计答错的次数，供界面显示真实数据（例如「你已经错第 3 次了」）。
     *
     * @param answeredWrongThisSession 这次会话里这个词有没有答错过。答错过的词首次间隔
     *                                 会拉长到 2 天（见 [SpacedRepetition.initializeAfterLearn]），
     *                                 没答错过的照旧 1 天。默认 false，旧调用方不受影响。
     */
    suspend fun applyLearnResult(
        collocationId: Long,
        remembered: Boolean,
        answeredWrongThisSession: Boolean = false
    ): Int =
        withContext(Dispatchers.IO) {
            try {
                val current = collocationDao.getById(collocationId)
                if (current == null) {
                    return@withContext 0
                }
                val initialized = SpacedRepetition.initializeAfterLearn(
                    current,
                    hadError = answeredWrongThisSession
                )
                val updated = if (remembered) {
                    initialized
                } else {
                    initialized.copy(
                        isInWordBook = true,
                        priority = initialized.priority + 50,
                        errorCount = current.errorCount + 1
                    )
                }
                collocationDao.update(updated)

                // 跨词本同步：这个英文词在别的词本里也出现过的话，
                // 一并标记为已学会 —— 于是那些行会显示「熟」并跳过新学，
                // 用户不需要为同一个词背第二遍。
                collocationDao.markLearnedByEnglish(current.english)
                updated.errorCount
            } catch (e: Exception) {
                throw RepositoryException("写入学习结果失败: ${e.message}", e)
            }
        }

    /**
     * 用户主动确认「这个词我会了」。
     *
     * 所有词本里同英文的行一起标记为已学会，并给一个明天的复习排期 ——
     * 这样它不会马上变成「待复习」，但也不会被彻底遗忘在进度之外。
     */
    suspend fun markLearnedNow(collocationId: Long) = withContext(Dispatchers.IO) {
        try {
            val current = collocationDao.getById(collocationId) ?: return@withContext
            val now = System.currentTimeMillis()
            // 「明天零点」而不是「24 小时后」，与 SpacedRepetition 的排期口径一致
            val nextDue = SpacedRepetition.startOfTomorrow(now)
            collocationDao.update(
                current.copy(
                    isLearned = true,
                    intervalDays = 1,
                    nextReviewDate = nextDue,
                    lastReviewDate = now
                )
            )
            collocationDao.markLearnedByEnglish(current.english)
            // markLearnedByEnglish 只改 isLearned，这里把排期也同步给其它词本的同名行
            collocationDao.scheduleByEnglish(current.english, nextDue, now)
        } catch (e: Exception) {
            throw RepositoryException("标记已学会失败: ${e.message}", e)
        }
    }

    /** 只提升某条搭配的优先级（学习答错时用） */
    suspend fun addPriority(collocationId: Long, delta: Double) = withContext(Dispatchers.IO) {
        try {
            collocationDao.addPriority(collocationId, delta)
        } catch (e: Exception) {
            throw RepositoryException("更新优先级失败: ${e.message}", e)
        }
    }

    /**
     * 套用一次拼写巩固的结果。
     *
     * 必须先从数据库读出当前这条，再只改动拼写相关的字段。
     * 因为拼写页拿到的是**学习开始前**的快照（isLearned=false、nextReviewDate=0），
     * 如果整条写回，会把学习时写好的「已学习 + 明天复习」覆盖掉，
     * 导致「明日复习」永远是 0、已学习数也被清零。
     */
    suspend fun applySpellResult(collocationId: Long, isCorrect: Boolean): Int =
        withContext(Dispatchers.IO) {
            try {
                val current = collocationDao.getById(collocationId) ?: return@withContext 0
                val now = System.currentTimeMillis()
                val updated = if (isCorrect) {
                    if (current.repetitionCount >= MASTERED_REPETITION) {
                        current
                    } else {
                        current.copy(repetitionCount = current.repetitionCount + 1)
                    }
                } else {
                    current.copy(
                        repetitionCount = 0,
                        intervalDays = 1,
                        nextReviewDate = SpacedRepetition.startOfTomorrow(now),
                        lastReviewDate = now,
                        priority = 100.0,
                        isInWordBook = true,
                        errorCount = current.errorCount + 1
                    )
                }
                collocationDao.update(updated)
                updated.errorCount
            } catch (e: Exception) {
                throw RepositoryException("写入拼写结果失败: ${e.message}", e)
            }
        }

    /**
     * 套用一次复习结果。同样先读当前值，避免用界面上的旧快照覆盖别的字段。
     */
    suspend fun applyReviewResult(collocationId: Long, result: ReviewResult): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val current = collocationDao.getById(collocationId) ?: return@withContext false
                val toUpdate = if (result == ReviewResult.FORGET) {
                    current.copy(isInWordBook = true)
                } else {
                    current
                }
                collocationDao.update(SpacedRepetition.applyReview(toUpdate, result))
                true
            } catch (e: Exception) {
                throw RepositoryException("写入复习结果失败: ${e.message}", e)
            }
        }

    /**
     * 从 JSON 文件导入词库。
     *
     * @param bookName 导入到哪个词本。默认沿用当前词本，这样一个词本可以分多次导入；
     *                 传别的名字就能得到一个独立的新词本（进度独立统计）。
     */
    suspend fun importFromJson(
        uri: Uri,
        jsonContent: String,
        bookName: String
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val imported = JsonImporter.parse(jsonContent)
            if (imported.isEmpty()) {
                return@withContext ImportResult.Error("JSON 文件中没有有效的搭配数据")
            }
            val entities = imported.map { dto ->
                Collocation(
                    english = dto.english,
                    chinese = dto.chinese,
                    example = dto.example,
                    tags = dto.tags,
                    bookName = bookName
                )
            }
            val ids = collocationDao.insertAll(entities)
            val successCount = ids.count { it != -1L }
            ImportResult.Success(successCount, imported.size)
        } catch (e: Exception) {
            ImportResult.Error("导入失败: ${e.message}")
        }
    }

    /**
     * 清空学习数据：学习进度、生词本、每日统计、签到、时长全部复位，
     * 但**保留词库本身**（内置的 117 条 + 用户导入过的词库都不删）。
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            collocationDao.getIdsWithProgress().forEach { id ->
                collocationDao.resetProgress(id)
            }
            dailyRecordDao?.deleteAll()
            dataStore.setCurrentBookName("固定搭配词库")
            // 签到、时长、已学时长等累计值一并复位
            dataStore.updateSignIn(0, 0L)
            dataStore.resetTotalStudyMinutes()
        } catch (e: Exception) {
            throw RepositoryException("清空数据失败: ${e.message}", e)
        }
    }

    // ==================== Spaced Repetition ====================
    suspend fun processReviewResult(collocation: Collocation, result: ReviewResult) = withContext(Dispatchers.IO) {
        try {
            val updated = SpacedRepetition.applyReview(collocation, result)
            collocationDao.update(updated)
        } catch (e: Exception) {
            throw RepositoryException("处理复习结果失败: ${e.message}", e)
        }
    }

    suspend fun markAsLearned(collocation: Collocation) = withContext(Dispatchers.IO) {
        try {
            val learned = collocation.copy(isLearned = true)
            collocationDao.update(learned)
        } catch (e: Exception) {
            throw RepositoryException("标记学习状态失败: ${e.message}", e)
        }
    }

    // ==================== Daily Record ====================
    suspend fun ensureTodayRecord(): DailyRecord = withContext(Dispatchers.IO) {
        val today = getTodayStart()
        val dao = dailyRecordDao ?: throw RepositoryException("DailyRecordDao 未初始化")
        val existing = dao.getByDate(today)
        if (existing != null) {
            return@withContext existing
        }
        val record = DailyRecord(date = today)
        dao.insertOrUpdate(record)
        record
    }

    suspend fun incrementTodayStats(learned: Int, reviewed: Int, minutes: Int) = withContext(Dispatchers.IO) {
        val dao = dailyRecordDao ?: throw RepositoryException("DailyRecordDao 未初始化")
        val today = getTodayStart()
        try {
            val record = dao.getByDate(today)
            if (record == null) {
                dao.insertOrUpdate(
                    DailyRecord(
                        date = today,
                        learnedCount = learned,
                        reviewedCount = reviewed,
                        studyMinutes = minutes
                    )
                )
            } else {
                dao.increment(today, learned, reviewed, minutes)
            }
            dataStore.addStudyMinutes(minutes)
        } catch (e: Exception) {
            throw RepositoryException("更新每日记录失败: ${e.message}", e)
        }
    }

    fun getTodayRecordFlow(): Flow<DailyRecord?> {
        val dao = dailyRecordDao ?: throw RepositoryException("DailyRecordDao 未初始化")
        return dao.getByDateFlow(getTodayStart()).flowOn(Dispatchers.IO)
    }

    /**
     * 一次性修复异常的学习时长记录。
     *
     * 背景：v4.1 的「恢复上次会话」没有重设计时起点，会话结算时把
     * 「1970 年至今的分钟数」（约 2900 万）写进了当日记录和累计时长。
     * 真实分钟数已不可考，把被污染的记录按「正常一组学习的量级」夹到 30 分钟，
     * 累计时长按修复后的每日记录重算。只执行一次。
     */
    suspend fun repairInflatedStudyMinutes() {
        if (dataStore.durationFixDone.first()) return
        withContext(Dispatchers.IO) {
            val dao = dailyRecordDao
            if (dao != null) {
                val clamped = dao.clampStudyMinutes(maxMinutes = 30)
                if (clamped > 0) {
                    // 累计时长同样被污染过，按修复后的每日记录重新求和
                    val total = dao.getTotalMinutes().first() ?: 0
                    dataStore.setTotalStudyMinutes(total)
                }
            }
            dataStore.markDurationFixDone()
        }
    }

    fun getRecentRecords(limit: Int = 365): Flow<List<DailyRecord>> {
        val dao = dailyRecordDao ?: throw RepositoryException("DailyRecordDao 未初始化")
        return dao.getRecentRecords(limit).flowOn(Dispatchers.IO)
    }

    fun getTotalMinutesFlow(): Flow<Int> {
        val dao = dailyRecordDao ?: return dataStore.totalStudyMinutes
        return dao.getTotalMinutes().map { it ?: 0 }.flowOn(Dispatchers.IO)
    }

    fun getTotalLearnedFlow(): Flow<Int> {
        // 「累计学习」是跨词本的累计值，来自每日记录，与当前词本无关
        val dao = dailyRecordDao ?: return kotlinx.coroutines.flow.flowOf(0)
        return dao.getTotalLearned().map { it ?: 0 }.flowOn(Dispatchers.IO)
    }

    // ==================== Sign In ====================
    suspend fun checkAndUpdateSignIn(): SignInResult = withContext(Dispatchers.IO) {
        try {
            val today = getTodayStart()
            val lastDate = dataStore.lastSignInDate.first()
            val continuousDays = dataStore.continuousSignInDays.first()

            return@withContext when {
                lastDate == today -> SignInResult.AlreadySignedIn(continuousDays)
                lastDate == today - DAY_MILLIS -> {
                    val newDays = continuousDays + 1
                    dataStore.updateSignIn(newDays, today)
                    SignInResult.SignedIn(newDays)
                }
                else -> {
                    dataStore.updateSignIn(1, today)
                    SignInResult.SignedIn(1)
                }
            }
        } catch (e: Exception) {
            throw RepositoryException("签到检查失败: ${e.message}", e)
        }
    }

    // ==================== 内置词库 ====================
    /** 已内置到本地库的词库版本号；0 = 从未内置过 */
    suspend fun getLibraryVersion(): Int = dataStore.libraryVersion.first()

    /**
     * 把内置词库合并进本地数据库（增量），返回新插入的条数。
     *
     * 关键设计：只**新增**本地还没有的英文搭配，绝不更新或删除已有记录。
     * 这样老用户升级到「加了新词的版本」时：
     * - 他的学习进度、生词本、复习排期、拼写次数全部原样保留；
     * - 新增的词作为「未学习」条目追加进来，直接可以学。
     *
     * 去重以 `english` 为准：若某个英文搭配已在本地存在（包括用户自己
     * 通过 JSON 导入的），就跳过，不覆盖他的数据。
     */
    suspend fun seedBuiltInLibrary(
        version: Int,
        bookName: String,
        items: List<Collocation>
    ): Int = libraryMutex.withLock {
        seedBuiltInLibraryInternal(version, bookName, items)
    }

    private suspend fun seedBuiltInLibraryInternal(
        version: Int,
        bookName: String,
        items: List<Collocation>
    ): Int = withContext(Dispatchers.IO) {
        try {
            // 一次性归位：早于 v2 的版本里，内置词条的 bookName 还是旧名字
            // （默认 '固定搭配词库'）。把它们改到 assets 里声明的新词本名下，
            // 这样首页显示的进度、以及后续按词本过滤才正确。
            // bookName = "" 的（最早期的默认值）一并归位。
            if (version >= BUILT_IN_BOOK_RENAME_VERSION && getLibraryVersion() < BUILT_IN_BOOK_RENAME_VERSION) {
                collocationDao.renameBook(OLD_BUILT_IN_BOOK, bookName)
                collocationDao.renameBook("", bookName)
                dataStore.setCurrentBookName(bookName)
            }

            // 去重必须**按每个词条自己的词本**分别判断，不能只用词库级的 bookName。
            //
            // 曾经的 bug：这里只查了 `bookName`（词库声明的书名，如「蓝哥本」）这一个
            // 词本里已有的英文，于是**其它词本的词会被当成新词重复插入**。
            // 当年从词库 v3 直接升到 v5 的用户，往年本 1413 条就会被灌两遍（变成 2778）。
            //
            // 现在的做法：先取每个词本已有的英文，再逐条按它所属词本判断。
            // 同一个英文出现在不同词本里仍然允许（各存一行、进度独立、重复的标「熟」）。
            val bookOf: (Collocation) -> String = { it.bookName.ifBlank { bookName } }
            val booksInvolved = items.map(bookOf).distinct()
            val existingByBook: Map<String, Set<String>> = booksInvolved.associateWith { book ->
                collocationDao.getAllEnglish(book).toHashSet()
            }

            // 诊断日志：把「去重时看到的每个词本已有多少条」打出来。
            // 播种逻辑一旦出问题，光看"新增 N 条"是看不出原因的。
            android.util.Log.i(
                "CollocationMemory",
                "播种 v$version：词库共 ${items.size} 条，涉及词本 " +
                    existingByBook.entries.joinToString("；") { (b, s) -> "「$b」已有 ${s.size} 条" }
            )

            // 除了与库里已有的比对，还要防止**同一批 items 内部**在同词本里重复
            // （词库文件若手滑写了两条一样的，只应入库一条）
            val seenInBatch = mutableMapOf<String, MutableSet<String>>()
            val newItems = items.filter { item ->
                val book = bookOf(item)
                if (item.english in existingByBook[book].orEmpty()) {
                    false
                } else {
                    seenInBatch.getOrPut(book) { mutableSetOf() }.add(item.english)
                }
            }

            if (newItems.isNotEmpty()) {
                collocationDao.insertAll(newItems)
            }
            dataStore.setLibraryVersion(version)
            newItems.size
        } catch (e: Exception) {
            throw RepositoryException("内置词库写入失败: ${e.message}", e)
        }
    }

    /**
     * 应用内置词库的订正（释义修正 + 删除错误词条）。
     *
     * 等价于 [applyCorrections] 传入「内置基线 + 服务器下载的增量」的合并结果。
     * 之所以要把远端订正也算进来：改一个字义不该逼用户重装 APK。
     */
    suspend fun applyLibraryCorrections(context: android.content.Context? = null): Int =
        applyCorrections(
            if (context == null) {
                LibraryCorrections.BUILT_IN
            } else {
                // 内置基线 + 本地缓存的远端订正（离线也带着上次下到的订正）
                RemoteCorrectionsStore.merged(context)
            }
        )

    /**
     * 执行一份订正表：释义修正 + 删除错误词条。
     *
     * 与 [seedBuiltInLibrary] 的区别：**这个方法每次启动都跑**，
     * 不参与版本号比较 —— 因为词库合并是「只新增、不更新」，
     * 否则改好的释义永远到不了已安装的设备。
     *
     * 安全性：只改 `chinese` 或删除整条，**不碰任何学习进度字段**。
     * 重复执行是幂等的：改过的条数第二次就是 0，不会反复写库。
     *
     * @return 实际发生改动的条数（0 表示无需订正）
     */
    suspend fun applyCorrections(data: CorrectionsData): Int = libraryMutex.withLock {
        applyCorrectionsInternal(data)
    }

    private suspend fun applyCorrectionsInternal(data: CorrectionsData): Int =
        withContext(Dispatchers.IO) {
        var changed = 0

        // 1) 删掉提取错误造成的幽灵词条
        data.removals.forEach { (book, englishList) ->
            englishList.forEach { english ->
                changed += collocationDao.deleteByEnglish(book, english)
            }
        }

        // 2) 修正释义
        data.definitionFixes.forEach { (book, fixes) ->
            fixes.forEach { (english, chinese) ->
                changed += collocationDao.updateChinese(book, english, chinese)
            }
        }

        if (changed > 0) {
            android.util.Log.i(
                "CollocationMemory",
                "词库订正 v${data.version}：改动 $changed 条（学习进度未受影响）"
            )
        }
        changed
    }

    // ==================== 记录当天学了什么 ====================
    /** 把今天新学的搭配 id 记进当天记录（重复不会写入） */
    suspend fun recordLearnedToday(collocationId: Long) = withContext(Dispatchers.IO) {
        val dao = dailyRecordDao ?: return@withContext
        val today = getTodayStart()
        val record = dao.getByDate(today) ?: DailyRecord(date = today).also { dao.insertOrUpdate(it) }
        if (!record.learnedIds.contains(collocationId)) {
            dao.updateLearnedIds(today, Converters().fromLongList(record.learnedIds + collocationId))
        }
    }

    /** 把今天复习过的搭配 id 记进当天记录 */
    suspend fun recordReviewedToday(collocationId: Long) = withContext(Dispatchers.IO) {
        val dao = dailyRecordDao ?: return@withContext
        val today = getTodayStart()
        val record = dao.getByDate(today) ?: DailyRecord(date = today).also { dao.insertOrUpdate(it) }
        if (!record.reviewedIds.contains(collocationId)) {
            dao.updateReviewedIds(today, Converters().fromLongList(record.reviewedIds + collocationId))
        }
    }

    /** 记录一次拼写巩固：做过的 id、答对的条数、答错的 id 都记进当天记录 */
    suspend fun recordSpelledToday(collocationId: Long, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val dao = dailyRecordDao ?: return@withContext
        val today = getTodayStart()
        val record = dao.getByDate(today) ?: DailyRecord(date = today).also { dao.insertOrUpdate(it) }

        if (!record.spelledIds.contains(collocationId)) {
            dao.updateSpelledIds(today, Converters().fromLongList(record.spelledIds + collocationId))
        }
        if (isCorrect) {
            dao.incrementSpelledCorrect(today, 1)
        } else if (!record.spelledWrongIds.contains(collocationId)) {
            dao.updateSpelledWrongIds(today, Converters().fromLongList(record.spelledWrongIds + collocationId))
        }
    }

    /**
     * 首页「今日概览 / 明日复习」的明细数据。
     *
     * 今天学过/复习/拼写过的词按 id 取，不做词本过滤 —— 那是「今天真实发生过的事」，
     * 属于跨词本的记录；只有「明日待复习」按当前词本查，才和首页的复习队列一致。
     */
    suspend fun fetchDayDetail(book: String): DayDetail = withContext(Dispatchers.IO) {
        try {
            val today = getTodayStart()
            val record = dailyRecordDao?.getByDate(today)

            val learned = record?.learnedIds.orEmpty()
                .let { if (it.isEmpty()) emptyList() else collocationDao.getByIds(it) }
            val reviewed = record?.reviewedIds.orEmpty()
                .let { if (it.isEmpty()) emptyList() else collocationDao.getByIds(it) }
            val spelled = record?.spelledIds.orEmpty()
                .let { if (it.isEmpty()) emptyList() else collocationDao.getByIds(it) }

            val tomorrow = tomorrowStart()
            val dueTomorrow = collocationDao.getDueBetween(book, tomorrow, tomorrow + DAY_MILLIS)

            DayDetail(
                learnedToday = learned,
                reviewedToday = reviewed,
                spelledToday = spelled,
                spelledWrongIds = record?.spelledWrongIds.orEmpty().toSet(),
                spelledCorrectCount = record?.spelledCorrectCount ?: 0,
                dueTomorrow = dueTomorrow
            )
        } catch (e: Exception) {
            throw RepositoryException("读取学习明细失败: ${e.message}", e)
        }
    }

    // ==================== Preferences ====================
    fun currentBookName(): Flow<String> = dataStore.currentBookName.flowOn(Dispatchers.IO)
    suspend fun setCurrentBookName(name: String) = dataStore.setCurrentBookName(name)
    fun learnBatchSize(): Flow<Int> = dataStore.learnBatchSize.flowOn(Dispatchers.IO)
    fun reviewBatchSize(): Flow<Int> = dataStore.reviewBatchSize.flowOn(Dispatchers.IO)
    suspend fun setBatchSizes(learn: Int, review: Int) = dataStore.setBatchSizes(learn, review)
    fun themeMode(): Flow<Int> = dataStore.themeMode.flowOn(Dispatchers.IO)
    suspend fun setThemeMode(mode: Int) = dataStore.setThemeMode(mode)
    fun shuffleReview(): Flow<Boolean> = dataStore.shuffleReview.flowOn(Dispatchers.IO)
    suspend fun setShuffleReview(enabled: Boolean) = dataStore.setShuffleReview(enabled)
    fun autoSpeak(): Flow<Boolean> = dataStore.autoSpeak.flowOn(Dispatchers.IO)
    suspend fun setAutoSpeak(enabled: Boolean) = dataStore.setAutoSpeak(enabled)

    /** 版本公告：已经看过的版本号（0 = 一条都没看过） */
    fun announcementVersion(): Flow<Int> = dataStore.announcementVersion.flowOn(Dispatchers.IO)

    suspend fun setAnnouncementVersion(version: Int) = dataStore.setAnnouncementVersion(version)

    /** 版权声明是否已被「同意并不再提示」 */
    fun copyrightNoticeAccepted(): Flow<Boolean> =
        dataStore.copyrightNoticeAccepted.flowOn(Dispatchers.IO)

    suspend fun setCopyrightNoticeAccepted(accepted: Boolean) =
        dataStore.setCopyrightNoticeAccepted(accepted)

    /** 首页悬浮对战图标的坐标（未设置过时返回 (-1f, -1f)） */
    fun battleIconPosition(): Flow<Pair<Float, Float>> =
        dataStore.battleIconPosition.flowOn(Dispatchers.IO)

    suspend fun setBattleIconPosition(x: Float, y: Float) =
        dataStore.setBattleIconPosition(x, y)

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val DEFAULT_EF = 2.5

        /** 累计复习答对多少次算「已掌握」，与 DAO 里的判断保持一致 */
        const val MASTERED_REPETITION = 3

        /** 内置词条的旧词本名，迁移到 assets 声明的新词本名下 */
        private const val OLD_BUILT_IN_BOOK = "固定搭配词库"

        /** 从这一版词库开始，内置词条会被归位到 assets 里声明的词本名 */
        private const val BUILT_IN_BOOK_RENAME_VERSION = 3
    }

    private fun getTodayStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** 明天 00:00 的时间戳 */
    private fun tomorrowStart(): Long = getTodayStart() + DAY_MILLIS

    // ==================== 笔记本 ====================
    // 用户自己的小本子：学新词/复习时点「记」收进来，内容可随意改写。
    // 整本以 JSON 存在 DataStore 里（低频、小体量，整体读写足够），
    // 只存本机，不上传、不参与任何同步。

    private val gson = com.google.gson.Gson()
    private val notebookListType = object : com.google.gson.reflect.TypeToken<List<NotebookEntry>>() {}.type

    /** 解析失败时当空本子处理 —— 数据是用户手写的，坏了也不能崩 App */
    private fun parseNotebook(json: String): List<NotebookEntry> = try {
        if (json.isBlank()) emptyList()
        else gson.fromJson(json, notebookListType) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val notebookEntries: Flow<List<NotebookEntry>> = dataStore.notebookJson.map { json ->
        parseNotebook(json)
    }

    /** 把一个搭配收进笔记本；同一英文已在本子里时返回 false（不重复收） */
    suspend fun addToNotebook(collocation: Collocation): Boolean = withContext(Dispatchers.IO) {
        val current = parseNotebook(dataStore.notebookJson.first())
        if (current.any { it.english == collocation.english }) return@withContext false
        val entry = NotebookEntry(
            id = System.currentTimeMillis(),
            english = collocation.english,
            chinese = collocation.chinese,
            note = "",
            createdAt = System.currentTimeMillis()
        )
        // 新收的放最上面 —— 最近记的最常翻
        dataStore.setNotebookJson(gson.toJson(listOf(entry) + current))
        true
    }

    suspend fun removeFromNotebook(entryId: Long) = withContext(Dispatchers.IO) {
        val current = parseNotebook(dataStore.notebookJson.first())
        dataStore.setNotebookJson(gson.toJson(current.filterNot { it.id == entryId }))
    }

    suspend fun updateNotebookNote(entryId: Long, note: String) = withContext(Dispatchers.IO) {
        val current = parseNotebook(dataStore.notebookJson.first())
        dataStore.setNotebookJson(
            gson.toJson(current.map { if (it.id == entryId) it.copy(note = note) else it })
        )
    }

    // ==================== 未完成会话的持久化 ====================
    // 会话原本只活在内存里，切后台被系统杀进程就全丢（华为/鸿蒙尤其激进）。
    // 这里把快照写进 DataStore，重启后按 id 把词条取回来、原样接上。

    // 解析快照的逻辑见文件末尾的 [parseSession]（抽成 internal 顶层函数，
    // 方便单测直接调用，逻辑本身不变：仍逐字段兜底读取）。

    /** 当前存着的会话快照；null 表示没有 */
    val sessionSnapshot: Flow<SessionSnapshot?> = dataStore.sessionJson.map { parseSession(it) }

    suspend fun saveSession(snapshot: SessionSnapshot) = withContext(Dispatchers.IO) {
        dataStore.setSessionJson(gson.toJson(snapshot))
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        dataStore.setSessionJson("")
    }

    /**
     * 按 id 批量取词条，**并保持传入 id 的顺序**。
     *
     * SQL 的 `IN (...)` 不保证返回顺序，而会话恢复要求第 3 张卡还是第 3 张，
     * 所以这里在内存里按 id 列表重排一次。
     */
    suspend fun getCollocationsByIds(ids: List<Long>): List<Collocation> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val byId = collocationDao.getByIds(ids).associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }
}

/**
 * 解析快照（抽成 internal 顶层函数，方便单测直接调用，逻辑与原来一致）。
 *
 * 刻意逐字段读取、而不是直接把 JSON 反序列化成对象：
 * Gson 反序列化会绕过 Kotlin 的默认值（走 Unsafe），老版本写下的、
 * 缺字段的 JSON 会得到一堆 null，反而更难排查。逐字段读带上兜底值最稳。
 *
 * 新增 [SessionSnapshot.wrongIds] 后必须同步这里的逐字段读取；旧快照没有这一项
 * 时 [longList] 兜底返回空列表，按「本次会话没答错过」处理，安全降级不崩。
 */
internal fun parseSession(json: String): SessionSnapshot? {
    if (json.isBlank()) return null
    return try {
        val obj = com.google.gson.JsonParser.parseString(json).asJsonObject

        fun str(key: String, fallback: String): String =
            obj.get(key)?.takeIf { !it.isJsonNull }?.asString ?: fallback

        fun int(key: String, fallback: Int): Int =
            obj.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: fallback

        fun longList(key: String): List<Long> =
            obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { runCatching { it.asLong }.getOrNull() }
                ?: emptyList()

        SessionSnapshot(
            source = str("source", ""),
            phase = str("phase", SessionSnapshot.PHASE_CARDS),
            itemIds = longList("itemIds"),
            index = int("index", 0),
            spellExcludedIds = longList("spellExcludedIds"),
            reviewWrongIds = longList("reviewWrongIds"),
            wrongIds = longList("wrongIds"),
            spellIndex = int("spellIndex", 0),
            updatedAt = obj.get("updatedAt")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        )
    } catch (e: Exception) {
        // 快照坏了就当没有会话，绝不因此崩 App
        null
    }
}

/** 首页明细页的数据：今天学了什么、复习了什么、拼写了什么、明天要复习多少 */
data class DayDetail(
    val learnedToday: List<Collocation> = emptyList(),
    val reviewedToday: List<Collocation> = emptyList(),
    val spelledToday: List<Collocation> = emptyList(),
    val spelledWrongIds: Set<Long> = emptySet(),
    val spelledCorrectCount: Int = 0,
    val dueTomorrow: List<Collocation> = emptyList()
)

sealed class ImportResult {
    data class Success(val importedCount: Int, val totalCount: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

enum class ReviewResult {
    FORGET, VAGUE, KNOW
}

sealed class SignInResult {
    data class SignedIn(val continuousDays: Int) : SignInResult()
    data class AlreadySignedIn(val continuousDays: Int) : SignInResult()
}

class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
