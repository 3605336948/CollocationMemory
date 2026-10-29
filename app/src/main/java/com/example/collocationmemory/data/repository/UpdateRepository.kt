package com.example.collocationmemory.data.repository

import android.content.Context
import android.util.Log
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.UserPreferencesDataStore
import com.example.collocationmemory.data.remote.AppUpdateInfo
import com.example.collocationmemory.data.remote.LibraryUpdateInfo
import com.example.collocationmemory.data.remote.RemoteFetch
import com.example.collocationmemory.data.remote.UpdateConfig
import com.example.collocationmemory.data.remote.UpdateManifest
import com.example.collocationmemory.util.ApkInstaller
import com.example.collocationmemory.util.JsonImporter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ============================================================
 * 应用内更新 —— 业务总入口
 * ============================================================
 *
 * 两条更新通道，各自解决不同的问题：
 *
 * | 通道 | 更新什么 | 用户感知 |
 * |---|---|---|
 * | **词库热更新** | 词条、释义、错词删除 | 完全无感。下次打开就是新词库，不用装任何东西 |
 * | **整包更新** | 代码、界面、新功能 | 弹窗提示 → App 内下载 → 跳系统安装页 → 点一下「安装」 |
 *
 * 判断逻辑很简单：服务器上的 `manifest.json` 里 `app.versionCode` 比本机大，
 * 就提示整包更新；`library.version` 比本机大，就静默拉词库。
 *
 * 安全性：
 * - 词库走 [CollocationRepository.seedBuiltInLibrary] 的**只新增、不更新**策略，
 *   老用户的学习进度、生词本、复习排期一律不受影响；
 * - APK 下载后要过三关：文件大小、SHA-256、**签名证书比对**，任一不符就拒绝安装；
 * - 任何一步失败都只是「这次更新没成」，绝不阻塞 App 使用。
 */
class UpdateRepository(
    private val appContext: Context,
    private val libraryRepository: CollocationRepository,
    private val dataStore: UserPreferencesDataStore
) {

    private val gson = Gson()

    // ==================== 本机版本 ====================
    //
    // 刻意用 PackageManager 读而不是 BuildConfig.VERSION_CODE：
    // 后者在 AGP 8.x 里的生成情况随配置变化，读系统里真实装的版本最可靠。

    fun currentVersionCode(): Int = try {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .longVersionCode
            .toInt()
    } catch (e: Exception) {
        Log.w(TAG, "读取 versionCode 失败：${e.message}")
        0
    }

    fun currentVersionName(): String = try {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .versionName
            ?: ""
    } catch (e: Exception) {
        Log.w(TAG, "读取 versionName 失败：${e.message}")
        ""
    }

    // ==================== 频率限制 ====================

    suspend fun lastCheckAt(): Long = dataStore.lastUpdateCheckAt.first()

    suspend fun markChecked() = dataStore.setLastUpdateCheckAt(System.currentTimeMillis())

    suspend fun markSkipped(versionCode: Int) = dataStore.setSkippedVersionCode(versionCode)

    /** 距上次检查是否已超过 [UpdateConfig.CHECK_INTERVAL_MS]（整包更新用） */
    suspend fun isCheckDue(): Boolean =
        System.currentTimeMillis() - lastCheckAt() >= UpdateConfig.CHECK_INTERVAL_MS

    /**
     * 距上次检查词库是否已超过 [UpdateConfig.LIBRARY_CHECK_INTERVAL_MS]。
     *
     * 与 [isCheckDue] 刻意分开：整包检查 6 小时一次（要弹窗，不能勤），
     * 词库检查 30 分钟一次（静默、代价低，要快）。
     */
    suspend fun isLibraryCheckDue(): Boolean =
        System.currentTimeMillis() - dataStore.lastLibraryCheckAt.first() >=
            UpdateConfig.LIBRARY_CHECK_INTERVAL_MS

    suspend fun markLibraryChecked() =
        dataStore.setLastLibraryCheckAt(System.currentTimeMillis())

    // ==================== 主流程 ====================

    /**
     * 检查更新（只拉一次清单，两条通道共用）。
     *
     * 两条通道的频率互相独立，所以这里把「算哪条通道」交给调用方决定：
     * 启动时可能只该查词库（整包还没到 6 小时），此时 `includeApp = false`，
     * 就不会因为「没有整包更新」而顺手把词库检查也跳过去。
     *
     * @param manual 用户手动点的「检查更新」。为 true 时忽略「以后再说」的记录、
     *               也不受时间间隔限制，用户点了就得给结果。
     * @param includeApp 是否处理整包更新（弹窗那条通道）
     * @param includeLibrary 是否处理词库热更新（静默那条通道）
     */
    suspend fun check(
        manual: Boolean,
        includeApp: Boolean = true,
        includeLibrary: Boolean = true
    ): UpdateSnapshot = withContext(Dispatchers.IO) {
        val manifest = fetchManifest()
            ?: return@withContext UpdateSnapshot(
                app = AppCheckResult.Failed("暂时连不上更新服务器，请检查网络后重试"),
                library = LibrarySyncResult.Skipped
            )

        UpdateSnapshot(
            app = if (includeApp) evaluateAppUpdate(manifest, manual) else AppCheckResult.UpToDate,
            library = if (includeLibrary) syncLibrary(manifest) else LibrarySyncResult.Skipped
        )
    }

    /** 拉取并解析版本清单；多个镜像地址依次尝试，全挂返回 null */
    suspend fun fetchManifest(): UpdateManifest? {
        val text = RemoteFetch.getTextFromAny(UpdateConfig.MANIFEST_URLS)
        if (text.isNullOrBlank()) {
            Log.w(TAG, "版本清单拉取失败（所有镜像都没通）")
            return null
        }
        return try {
            val manifest = gson.fromJson(text, UpdateManifest::class.java)
            if (manifest == null) {
                Log.w(TAG, "版本清单解析失败：内容为空")
            }
            manifest
        } catch (e: Exception) {
            Log.w(TAG, "版本清单解析失败：${e.message}")
            null
        }
    }

    // ==================== 整包更新 ====================

    private suspend fun evaluateAppUpdate(
        manifest: UpdateManifest,
        manual: Boolean
    ): AppCheckResult {
        val info = manifest.app ?: return AppCheckResult.UpToDate
        if (info.versionCode <= 0 || info.apkUrls.isEmpty()) {
            // 清单里写了 app 段但信息不全，当成没更新，别用错数据去引导用户
            return AppCheckResult.UpToDate
        }

        val current = currentVersionCode()
        if (info.versionCode <= current) return AppCheckResult.UpToDate

        // 兼容包用户兜底：主线 APK 有 minSdk 门槛（当前 35 = Android 15+），
        // 本机系统够不到的更新包「提示了也装不上」。静默跳过，别让人
        // 下载 20MB 之后看「解析包时出现问题」。词库热更新不受影响。
        if (info.minSdk > 0 && info.minSdk > android.os.Build.VERSION.SDK_INT) {
            Log.i(TAG, "清单要求 minSdk ${info.minSdk}，本机 API ${android.os.Build.VERSION.SDK_INT}，跳过整包更新")
            return AppCheckResult.UpToDate
        }

        val forced = info.forceUpdate ||
            (info.minSupportedVersionCode > 0 && current < info.minSupportedVersionCode)

        // 自动检查时尊重用户的「以后再说」：同一个版本不再反复骚扰。
        // 手动点检查、以及强制更新，都不受这个限制。
        if (!manual && !forced) {
            val skipped = dataStore.skippedVersionCode.first()
            if (skipped >= info.versionCode) {
                Log.i(TAG, "版本 ${info.versionName} 已被用户跳过，本次不提示")
                return AppCheckResult.UpToDate
            }
        }

        Log.i(TAG, "发现新版本 ${info.versionName}（本机 $current → ${info.versionCode}，强制=$forced）")
        return AppCheckResult.Available(info, forced)
    }

    /** 下载更新包并校验完整性；成功返回文件，失败返回 null */
    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, APK_DIR).apply { mkdirs() }
        val target = File(dir, "update-${info.versionCode}.apk")

        // 上次已经下好并且校验通过，直接复用，不重复下 21MB
        if (target.exists() && isApkUsable(target, info)) {
            Log.i(TAG, "复用已下载的更新包：${target.name}")
            return@withContext target
        }

        for (url in info.apkUrls) {
            if (url.isBlank()) continue
            val temp = File(dir, "update-${info.versionCode}.part")
            if (temp.exists()) temp.delete()

            Log.i(TAG, "开始下载更新包：$url")
            val ok = RemoteFetch.download(url, temp, onProgress)
            if (!ok) {
                temp.delete()
                Log.w(TAG, "下载失败，尝试下一个镜像")
                continue
            }
            if (!isApkUsable(temp, info)) {
                temp.delete()
                Log.w(TAG, "更新包校验未通过，尝试下一个镜像")
                continue
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                Log.w(TAG, "重命名更新包失败")
                temp.delete()
                continue
            }
            Log.i(TAG, "更新包就绪：${target.name}（${target.length()} 字节）")
            return@withContext target
        }

        Log.e(TAG, "所有镜像都下载失败")
        null
    }

    /**
     * 更新包是否可信可用。三道关，任一不过就丢弃重下。
     *
     * 1. 文件大小对不对（防下载被截断）；
     * 2. SHA-256 对不对（防传输损坏、防内容被替换）；
     * 3. **签名证书与本机是否一致**（防有人用一个同包名的恶意包来冒充更新）。
     */
    private fun isApkUsable(file: File, info: AppUpdateInfo): Boolean {
        if (!file.exists() || file.length() <= 0L) return false

        if (info.apkSize > 0 && file.length() != info.apkSize) {
            Log.w(TAG, "更新包大小不符：期望 ${info.apkSize}，实际 ${file.length()}")
            return false
        }

        if (info.apkSha256.isNotBlank()) {
            val actual = RemoteFetch.sha256(file)
            if (!actual.equals(info.apkSha256, ignoreCase = true)) {
                Log.w(TAG, "更新包 SHA-256 不符：期望 ${info.apkSha256}，实际 $actual")
                return false
            }
        }

        return ApkInstaller.verifySameApp(appContext, file)
    }

    /** 本应用现在有没有「安装未知应用」的权限 */
    fun canInstallApk(): Boolean = ApkInstaller.canInstall(appContext)

    /** 唤起系统安装界面 */
    fun installApk(apk: File): Boolean = ApkInstaller.install(appContext, apk)

    /** 清掉下载缓存的更新包（用户取消更新、或点「以后再说」时顺手释放空间） */
    fun clearDownloadedApks() {
        try {
            File(appContext.cacheDir, APK_DIR).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "清理更新包缓存失败：${e.message}")
        }
    }

    // ==================== 词库热更新 ====================

    /**
     * 词库热更新：拉远端词库 + 订正表，合并进本地数据库。
     *
     * 完全复用内置词库那套经过验证的安全逻辑：
     * - 词条：只**新增**本地没有的英文，已有的一律跳过 —— 学习进度毫发无损；
     * - 释义订正 / 删错词：走订正通道，只改 `chinese` 或删整条，不碰进度字段。
     *
     * 所以「加词、改释义、删错词」这三件事，以后都不用发新版 APK 了。
     */
    private suspend fun syncLibrary(manifest: UpdateManifest): LibrarySyncResult {
        val info = manifest.library ?: return LibrarySyncResult.Skipped

        // ---- 1) 订正表：先下载落盘，这样没网时也一直有效 ----
        var correctionsApplied = 0
        try {
            val remoteText = RemoteFetch.getTextFromAny(info.correctionsUrls)
            if (!remoteText.isNullOrBlank()) {
                val remote = try {
                    gson.fromJson(remoteText, CorrectionsData::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "远端订正表解析失败：${e.message}")
                    null
                }
                if (remote != null && !remote.isEmpty) {
                    RemoteCorrectionsStore.write(appContext, remote)
                    Log.i(TAG, "远端订正表已缓存 v${remote.version}")
                }
            }
            // 用「内置基线 + 本地缓存」的合并结果执行 —— 即使这次没下到新订正，
            // 也要保证之前缓存的订正在重装/清缓存后能补上
            correctionsApplied = libraryRepository.applyCorrections(
                RemoteCorrectionsStore.merged(appContext)
            )
        } catch (e: Exception) {
            // 订正失败不能影响词库本体更新
            Log.w(TAG, "应用词库订正失败：${e.message}")
        }

        // ---- 2) 词库本体：只新增 ----
        var added = 0
        val localVersion = libraryRepository.getLibraryVersion()
        if (info.version > localVersion && info.libraryUrls.isNotEmpty()) {
            val text = RemoteFetch.getTextFromAny(info.libraryUrls)
            if (text.isNullOrBlank()) {
                Log.w(TAG, "词库下载失败，保持本地 v$localVersion 不变")
                return LibrarySyncResult.Failed("词库下载失败")
            }
            val library = JsonImporter.parseLibrary(text)
            if (library == null) {
                Log.w(TAG, "词库文件格式不正确")
                return LibrarySyncResult.Failed("词库文件格式不正确")
            }
            val items = library.items
                .filter { it.english.isNotBlank() && it.chinese.isNotBlank() }
                .map { dto ->
                    Collocation(
                        english = dto.english,
                        chinese = dto.chinese,
                        example = dto.example,
                        tags = dto.tags,
                        bookName = dto.bookName?.takeIf { it.isNotBlank() } ?: library.bookName
                    )
                }
            added = libraryRepository.seedBuiltInLibrary(library.version, library.bookName, items)
            Log.i(TAG, "远端词库合并完成：v$localVersion → v${library.version}，新增 $added 条")
        }

        return LibrarySyncResult.Done(added = added, correctionsApplied = correctionsApplied)
    }

    companion object {
        private const val TAG = "CollocationMemory"

        /** 更新包下载目录（相对 cacheDir），与 res/xml/file_paths.xml 里的声明必须一致 */
        const val APK_DIR = "updates"
    }
}

/** 一次检查的完整结果：整包 + 词库 */
data class UpdateSnapshot(
    val app: AppCheckResult = AppCheckResult.UpToDate,
    val library: LibrarySyncResult = LibrarySyncResult.Skipped
)

/** 整包更新的检查结果 */
sealed interface AppCheckResult {
    /** 已是最新（或用户跳过了这一版） */
    data object UpToDate : AppCheckResult

    /** 有新版本 */
    data class Available(val info: AppUpdateInfo, val forced: Boolean) : AppCheckResult

    /** 检查失败（网络问题等），[message] 可直接展示给用户 */
    data class Failed(val message: String) : AppCheckResult
}

/** 词库热更新的结果 */
sealed interface LibrarySyncResult {
    /** 清单里没有 library 段，或本次不需要更新 */
    data object Skipped : LibrarySyncResult

    /** 走完了流程 */
    data class Done(val added: Int, val correctionsApplied: Int) : LibrarySyncResult

    /** 词库下载或解析失败 */
    data class Failed(val message: String) : LibrarySyncResult
}
