package com.example.collocationmemory.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesDataStore(private val context: Context) {
    companion object {
        val CURRENT_BOOK_NAME = stringPreferencesKey("current_book_name")
        val LEARN_BATCH_SIZE = intPreferencesKey("learn_batch_size")
        val REVIEW_BATCH_SIZE = intPreferencesKey("review_batch_size")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LAST_SIGN_IN_DATE = longPreferencesKey("last_sign_in_date")
        val CONTINUOUS_SIGN_IN_DAYS = intPreferencesKey("continuous_sign_in_days")
        val TOTAL_STUDY_MINUTES = intPreferencesKey("total_study_minutes")

        /** 学习时长异常数据（v4.1 会话恢复 bug）的一次性修复是否已执行 */
        val DURATION_FIX_DONE = booleanPreferencesKey("duration_fix_done")

        /** 已内置进本地库的词库版本号；0 表示还没导入过内置词库 */
        val LIBRARY_VERSION = intPreferencesKey("library_version")

        /** 复习时是否打乱顺序（「知识盲盒」） */
        val SHUFFLE_REVIEW = booleanPreferencesKey("shuffle_review")

        /** 新学/复习翻到一张卡片时是否自动朗读英文，默认开启 */
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")

        /** 是否已勾选「同意并不再提示」版权声明 */
        val COPYRIGHT_NOTICE_ACCEPTED = booleanPreferencesKey("copyright_notice_accepted")

        /**
         * 首页「蓝人单词对战」悬浮图标的位置（相对屏幕左上角的像素坐标）。
         *
         * 用 Float 存原始像素：拖动是像素级的，存 dp 再换算会因密度取整而抖动。
         * 默认 -1f 表示「还没拖动过」，此时用内置的默认位置。
         */
        val BATTLE_ICON_X = floatPreferencesKey("battle_icon_x")
        val BATTLE_ICON_Y = floatPreferencesKey("battle_icon_y")

        /** 上次联网检查更新的时间戳（毫秒）。用于限制自动检查频率，别每次启动都联网 */
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")

        /**
         * 上次检查**词库**热更新的时间戳（毫秒）。
         *
         * 刻意与 [LAST_UPDATE_CHECK_AT] 分开：整包更新要弹窗打扰用户，
         * 间隔长一点（6 小时）才不烦人；词库更新是静默的、用户无感的，
         * 应该更快生效 —— 作者改个释义，不该让学生等 6 小时。
         */
        val LAST_LIBRARY_CHECK_AT = longPreferencesKey("last_library_check_at")

        /**
         * 用户点过「以后再说」的版本号。
         *
         * 存下来是为了不再反复打扰同一个人：只要服务器上的新版本号
         * 不超过这个值，自动检查就不再弹窗（手动点「检查更新」仍会提示）。
         * 强制更新不受此限制。
         */
        val SKIPPED_VERSION_CODE = intPreferencesKey("skipped_version_code")

        /**
         * 用户已经看过的「版本公告」对应的版本号；0 表示一条都没看过。
         *
         * 升级到新版本后弹一次更新说明，看完就写回当前版本号，之后不再打扰。
         */
        val ANNOUNCEMENT_VERSION = intPreferencesKey("announcement_version")

        /**
         * 笔记本（用户自己的小本子），序列化成 JSON 存本地。
         *
         * 不用 Room 建表是因为：笔记本是低频、小体量的个人内容，
         * 整体读写 JSON 足够；用 Room 反而要为它做一次数据库迁移，
         * 得不偿失。内容只存在本机，不上传、不参与同步。
         */
        val NOTEBOOK_JSON = stringPreferencesKey("notebook_json")

        /**
         * 未完成的学习/复习会话快照（JSON）。
         *
         * 存在本机是为了扛「切后台被系统杀进程」：华为 / 鸿蒙在后台会直接回收
         * App，会话原本只活在内存里，一杀就丢；而且答过的词当场排到了明天，
         * 用户重进只能拿到下一批，观感就是「刚才那组全没了」。
         */
        val SESSION_JSON = stringPreferencesKey("session_json")

        /** 主题模式取值：0=明亮, 1=深邃, 2=跟随系统 */
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }

    /** 悬浮图标的默认坐标：-1f 表示未设置过 */
    val battleIconPosition: Flow<Pair<Float, Float>> = context.dataStore.data.map { prefs ->
        (prefs[BATTLE_ICON_X] ?: -1f) to (prefs[BATTLE_ICON_Y] ?: -1f)
    }

    suspend fun setBattleIconPosition(x: Float, y: Float) {
        context.dataStore.edit { prefs ->
            prefs[BATTLE_ICON_X] = x
            prefs[BATTLE_ICON_Y] = y
        }
    }

    /**
     * 版权声明是否已被「同意并不再提示」。
     *
     * 默认 false —— 首次启动必须让用户看到声明并点击同意。
     */
    val copyrightNoticeAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[COPYRIGHT_NOTICE_ACCEPTED] ?: false
    }

    suspend fun setCopyrightNoticeAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[COPYRIGHT_NOTICE_ACCEPTED] = accepted
        }
    }

    /** 盲盒模式：复习顺序随机，默认关闭 */
    val shuffleReview: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHUFFLE_REVIEW] ?: false
    }

    suspend fun setShuffleReview(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHUFFLE_REVIEW] = enabled
        }
    }

    /** 自动朗读英文：默认开启（没写过的老用户也按开启处理） */
    val autoSpeak: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_SPEAK] ?: true
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_SPEAK] = enabled
        }
    }

    val libraryVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LIBRARY_VERSION] ?: 0
    }

    suspend fun setLibraryVersion(version: Int) {
        context.dataStore.edit { prefs ->
            prefs[LIBRARY_VERSION] = version
        }
    }

    val currentBookName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_BOOK_NAME] ?: "默认词书"
    }

    suspend fun setCurrentBookName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_BOOK_NAME] = name
        }
    }

    val learnBatchSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LEARN_BATCH_SIZE] ?: 10
    }

    val reviewBatchSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[REVIEW_BATCH_SIZE] ?: 10
    }

    suspend fun setBatchSizes(learn: Int, review: Int) {
        context.dataStore.edit { prefs ->
            prefs[LEARN_BATCH_SIZE] = learn
            prefs[REVIEW_BATCH_SIZE] = review
        }
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { prefs ->
        // 0=明亮, 1=深邃, 2=跟随系统
        // 默认取「深邃」：首页是壁纸 + 玻璃卡片的设计，深色下对比最稳、观感最好；
        // 浅色模式的壁纸亮度差异大，白字容易发虚。
        prefs[THEME_MODE] ?: THEME_DARK
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    val lastSignInDate: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_SIGN_IN_DATE] ?: 0L
    }

    val continuousSignInDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CONTINUOUS_SIGN_IN_DAYS] ?: 0
    }

    suspend fun updateSignIn(continuousDays: Int, lastDate: Long) {
        context.dataStore.edit { prefs ->
            prefs[CONTINUOUS_SIGN_IN_DAYS] = continuousDays
            prefs[LAST_SIGN_IN_DATE] = lastDate
        }
    }

    val totalStudyMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TOTAL_STUDY_MINUTES] ?: 0
    }

    suspend fun addStudyMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[TOTAL_STUDY_MINUTES] ?: 0
            prefs[TOTAL_STUDY_MINUTES] = current + minutes
        }
    }

    /** 复位累计学习时长（清空数据时用） */
    suspend fun resetTotalStudyMinutes() {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_STUDY_MINUTES] = 0
        }
    }

    /** 直接写入累计学习时长（数据修复时按每日记录重算用） */
    suspend fun setTotalStudyMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_STUDY_MINUTES] = minutes
        }
    }

    /** 学习时长异常数据（v4.1 会话恢复 bug）是否已修复 */
    val durationFixDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DURATION_FIX_DONE] ?: false
    }

    suspend fun markDurationFixDone() {
        context.dataStore.edit { prefs ->
            prefs[DURATION_FIX_DONE] = true
        }
    }

    // ==================== 应用内更新 ====================

    /** 上次检查更新的时间戳（毫秒），0 表示从没检查过 */
    val lastUpdateCheckAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_UPDATE_CHECK_AT] ?: 0L
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK_AT] = timestamp
        }
    }

    /** 用户点「以后再说」时对应的版本号，0 表示没跳过任何版本 */
    val skippedVersionCode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SKIPPED_VERSION_CODE] ?: 0
    }

    /** 上次检查词库热更新的时间戳（毫秒），0 表示从没检查过 */
    val lastLibraryCheckAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_LIBRARY_CHECK_AT] ?: 0L
    }

    suspend fun setLastLibraryCheckAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_LIBRARY_CHECK_AT] = timestamp
        }
    }

    suspend fun setSkippedVersionCode(versionCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[SKIPPED_VERSION_CODE] = versionCode
        }
    }

    // ==================== 版本公告 ====================

    /** 上次给用户看过的版本公告版本号，0 表示还没看过任何一条 */
    val announcementVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ANNOUNCEMENT_VERSION] ?: 0
    }

    /**
     * 记下「v[version] 的公告已经看过了」。
     *
     * 只往上写：传进来的版本号小于已记录的会被忽略 —— 万一某个版本既弹了、
     * 后台又因为别的原因补写了一次，也不会把记录倒退回旧版本导致重复弹窗。
     */
    suspend fun setAnnouncementVersion(version: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[ANNOUNCEMENT_VERSION] ?: 0
            prefs[ANNOUNCEMENT_VERSION] = maxOf(current, version)
        }
    }

    // ==================== 笔记本 ====================

    /** 笔记本的原始 JSON，空字符串表示还没有写过 */
    val notebookJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NOTEBOOK_JSON] ?: ""
    }

    suspend fun setNotebookJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[NOTEBOOK_JSON] = json
        }
    }

    // ==================== 未完成会话 ====================

    /** 会话快照的原始 JSON，空字符串表示当前没有未完成的会话 */
    val sessionJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SESSION_JSON] ?: ""
    }

    suspend fun setSessionJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[SESSION_JSON] = json
        }
    }
}
