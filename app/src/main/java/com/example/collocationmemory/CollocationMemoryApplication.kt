package com.example.collocationmemory

import android.app.Application
import android.util.Log
import com.example.collocationmemory.data.local.AppDatabase
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.UserPreferencesDataStore
import com.example.collocationmemory.data.repository.CollocationRepository
import com.example.collocationmemory.data.repository.UpdateRepository
import com.example.collocationmemory.util.JsonImporter
import com.example.collocationmemory.util.SpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CollocationMemoryApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val dataStore by lazy { UserPreferencesDataStore(this) }
    val repository: CollocationRepository by lazy {
        CollocationRepository(database.collocationDao(), dataStore).apply {
            setDailyRecordDao(database.dailyRecordDao())
        }
    }

    /**
     * 应用内更新（整包 + 词库热更新）。
     *
     * 依赖 repository：词库热更新要复用它的「只新增、不更新」合并逻辑，
     * 这样老用户的学习进度无论如何都不会被更新动作碰到。
     */
    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(this, repository, dataStore)
    }

    /** 应用级协程作用域，用于启动时写入内置词库 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 提前把语音引擎拉起来：TTS 初始化是异步的，等翻到第一张卡片才初始化
        // 会错过那一次的自动朗读
        SpeechHelper.init(this)
        seedBuiltInLibraryIfNeeded()
    }

    /**
     * 首次启动（或清空数据后）把打包在 APK 里的内置词库写入本地数据库，
     * 这样使用者装完就能直接学习，不需要手动导入 JSON。
     *
     * 安全性：
     * - 只在本地数据库为空时写入，绝不覆盖或重复已有搭配与学习进度；
     * - 用版本号记录，同一版本只写入一次，将来词库更新时提升 assets 里的 version 即可。
     */
    private fun seedBuiltInLibraryIfNeeded() {
        applicationScope.launch {
            try {
                // 先跑词库订正。
                //
                // 必须放在版本号判断**之前**：下面那句
                // `getLibraryVersion() >= library.version` 会直接 return，
                // 如果订正放在后面，老用户永远收不到修好的释义。
                // 订正不参与版本号比较，每次启动对一遍（几条 SQL，代价可忽略）。
                //
                // 传入 context：订正表 = 内置基线 + 上次从服务器下载并缓存的增量。
                // 这样即使这次离线，之前下到的订正在下次启动依然生效。
                try {
                    repository.applyLibraryCorrections(this@CollocationMemoryApplication)
                } catch (e: Exception) {
                    // 订正失败不能拖垮词库初始化
                    Log.e(TAG, "应用词库订正失败", e)
                }

                val library = assets.open(LIBRARY_ASSET).use { stream ->
                    JsonImporter.parseLibrary(stream.bufferedReader().readText())
                }
                if (library == null) {
                    Log.w(TAG, "内置词库文件缺失或格式不正确: $LIBRARY_ASSET")
                    return@launch
                }
                if (repository.getLibraryVersion() >= library.version) {
                    return@launch
                }
                val items = library.items
                    .filter { it.english.isNotBlank() && it.chinese.isNotBlank() }
                    .map { dto ->
                        Collocation(
                            english = dto.english,
                            chinese = dto.chinese,
                            example = dto.example,
                            tags = dto.tags,
                            // 词条自带所属词本；没写才回退到词库级的 bookName。
                            // 一个 app_library.json 里可以放多个词本，靠这个字段区分。
                            bookName = dto.bookName?.takeIf { it.isNotBlank() }
                                ?: library.bookName
                        )
                    }
                val added = repository.seedBuiltInLibrary(library.version, library.bookName, items)
                if (added > 0) {
                    Log.i(TAG, "内置词库更新到 v${library.version}，新增 $added 条（已有记录和学习进度未改动）")
                } else {
                    Log.i(TAG, "内置词库已是 v${library.version}，无需新增")
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入内置词库失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "CollocationMemory"
        private const val LIBRARY_ASSET = "app_library.json"
    }
}
