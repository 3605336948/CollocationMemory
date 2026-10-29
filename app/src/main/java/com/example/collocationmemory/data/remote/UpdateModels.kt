package com.example.collocationmemory.data.remote

/**
 * ============================================================
 * 应用内更新 —— 远端「版本清单」的数据结构
 * ============================================================
 *
 * 服务器上只需要放一个 `manifest.json`，App 每次检查更新时拉它，
 * 就知道「有没有新版本、新版本要去哪下载、词库有没有更新」。
 *
 * 完整示例（Gitee 仓库里的 update/manifest.json）：
 *
 * ```json
 * {
 *   "app": {
 *     "versionCode": 29,
 *     "versionName": "3.8",
 *     "minSupportedVersionCode": 0,
 *     "apkUrls": [
 *       "https://gitee.com/<你的用户名>/collocation-update/releases/download/v3.8/CollocationMemory-3.8-release.apk"
 *     ],
 *     "apkSize": 21547751,
 *     "apkSha256": "3f2c...（64 位小写十六进制）",
 *     "changelog": "1. 内置词组新增 54 条\n2. 修复拼写判定",
 *     "forceUpdate": false
 *   },
 *   "library": {
 *     "version": 6,
 *     "libraryUrls": [
 *       "https://gitee.com/<你的用户名>/collocation-update/raw/master/library/app_library.json"
 *     ],
 *     "correctionsUrls": [
 *       "https://gitee.com/<你的用户名>/collocation-update/raw/master/library/corrections.json"
 *     ],
 *     "notes": "蓝哥本新增 54 条"
 *   }
 * }
 * ```
 *
 * 三个字段都可以整体省略：没有 `app` 段就不会提示整包更新，
 * 没有 `library` 段就不会做词库热更新。
 *
 * 生成这个文件不用手写 —— 跑 `python tools/update/make_manifest.py` 即可，
 * 它会自动算好 apkSize 和 apkSha256。
 */
data class UpdateManifest(
    val app: AppUpdateInfo? = null,
    val library: LibraryUpdateInfo? = null
)

/** 整包（APK）更新信息 */
data class AppUpdateInfo(
    /** 新版本的 versionCode，必须大于手机上装的这个 */
    val versionCode: Int = 0,
    /** 新版本号，仅用于显示，如 "3.8" */
    val versionName: String = "",
    /**
     * 最低可接受的旧版本 versionCode。
     *
     * - 0（默认）：不强制，用户可跳过；
     * - 大于 0 且手机上装的版本低于它：**强制更新**，不给「以后再说」。
     *
     * 什么时候用：数据库结构变了、旧版本会读写坏数据、或者有严重 bug 时。
     */
    val minSupportedVersionCode: Int = 0,
    /**
     * 清单里登记的**主线 APK 的 minSdk**（安卓系统版本门槛）。
     *
     * 给低版本兼容包用户兜底用：他们的系统装不上主线包，
     * 收到更新提示也没用 —— 下载 20MB 之后只会看到「解析包时出现问题」。
     * App 发现本机系统低于这个值时静默跳过整包更新（词库热更新不受影响）。
     * 0 = 清单里没写（旧版清单），不做这个判断。
     */
    val minSdk: Int = 0,
    /**
     * APK 下载地址，可以写多个（按顺序重试）。
     *
     * 建议第一个填国内源（Gitee），第二个填备用源（GitHub + 加速）。
     * 某个源挂了或太慢，App 会自动换下一个，用户无感。
     */
    val apkUrls: List<String> = emptyList(),
    /** APK 字节数。用于下载后校验完整性；填 0 表示不校验大小 */
    val apkSize: Long = 0L,
    /** APK 的 SHA-256（64 位小写十六进制）。非空时下载后必须匹配，否则拒绝安装 */
    val apkSha256: String = "",
    /** 更新说明，直接显示在弹窗里，用 \n 换行 */
    val changelog: String = "",
    /** 额外的强制开关：true = 本次更新必须完成才能继续用 */
    val forceUpdate: Boolean = false
)

/**
 * 词库（学习内容）热更新信息。
 *
 * 这一路**不需要重新安装 APK**：App 直接把新的 `app_library.json` 拉下来，
 * 走和内置词库完全相同的「增量合并」逻辑写进本地数据库 ——
 * 只新增没见过的词，绝不动学习进度。
 *
 * 所以「加词、改释义、删错词」这三件最常做的事，用户完全无感，
 * 下次打开 App 就已经是新词库了。
 */
data class LibraryUpdateInfo(
    /** 远端词库版本号。比本地大才更新；和 app_library.json 里的 version 保持一致 */
    val version: Int = 0,
    /** app_library.json 的下载地址（可多个，按顺序重试） */
    val libraryUrls: List<String> = emptyList(),
    /** 订正表的下载地址（可多个）。没有订正需求时可以留空 */
    val correctionsUrls: List<String> = emptyList(),
    /** 备注，仅用于日志 */
    val notes: String = ""
)

/**
 * 更新服务地址配置。
 *
 * ★ 换服务器 / 换仓库，**只改这一个文件**，其余代码都不用动。
 *
 * 为什么是「一组地址」而不是一个：国内访问 GitHub 不稳定，
 * 所以按「快的放前面」排好序，App 从上往下逐个试，第一个成功就用它。
 * 任何一条挂了都不影响更新功能。
 */
object UpdateConfig {

    /**
     * 版本清单地址（按顺序重试）。
     *
     * 已配置为 https://gitee.com/bz360/collocation-update ，
     * 清单地址即 https://gitee.com/bz360/collocation-update/raw/master/manifest.json
     *
     * 本地自测更新流程时，把第一项临时换成下面这行（配合 tools/update/serve_local.py）：
     *   "http://10.0.2.2:8000/manifest.json",        // 模拟器
     *   "http://127.0.0.1:8000/manifest.json",       // 真机 + adb reverse
     * 测完记得改回来。
     */
    val MANIFEST_URLS: List<String> = listOf(
        "https://gitee.com/bz360/collocation-update/raw/master/manifest.json",
        "https://raw.githubusercontent.com/bz360/collocation-update/master/manifest.json"
    )

    /** 建立连接超时 */
    const val CONNECT_TIMEOUT_MS = 10_000

    /** 读取超时（整包 21MB，给宽一点） */
    const val READ_TIMEOUT_MS = 30_000

    /**
     * **整包更新**自动检查的最小间隔。
     *
     * 整包更新要弹窗打扰用户，所以两次之间至少隔 6 小时 ——
     * 一天最多提示两三次，不会烦人。用户手动点「检查更新」不受此限制。
     */
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /**
     * **词库热更新**自动检查的最小间隔。
     *
     * 为什么比 [CHECK_INTERVAL_MS] 短得多：词库更新是静默的、用户完全无感，
     * 一次检查只拉一个 2KB 的清单（只有版本号真的变了才会下 700KB 词库），
     * 代价极低；而作者改一个错释义，学生不该等 6 小时才看到。
     *
     * 早先两条通道共用 6 小时间隔，结果就是「刚推送的词库，学生打开 App
     * 还是老词库」，只能干等到下一个 6 小时窗口 —— 这个设计已废弃。
     */
    const val LIBRARY_CHECK_INTERVAL_MS = 30 * 60 * 1000L
}
