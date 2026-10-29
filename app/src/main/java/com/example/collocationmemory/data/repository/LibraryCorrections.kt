package com.example.collocationmemory.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * 一份可序列化的「词库订正数据」。
 *
 * 内置基线（[LibraryCorrections.BUILT_IN]）和从服务器下载的订正表
 * 都归到这个结构，合并之后交给
 * [CollocationRepository.applyCorrections] 执行。
 *
 * 对应服务器上的 `corrections.json`：
 *
 * ```json
 * {
 *   "version": 3,
 *   "definitionFixes": {
 *     "往年固定搭配单词本": { "play an key / vital role in": "起重要作用" }
 *   },
 *   "removals": {
 *     "往年固定搭配单词本": ["steps to do"]
 *   }
 * }
 * ```
 */
data class CorrectionsData(
    val version: Int = 0,
    /** 释义订正：词本 -> {英文: 正确释义} */
    val definitionFixes: Map<String, Map<String, String>> = emptyMap(),
    /** 删除错误词条：词本 -> 要删掉的英文列表 */
    val removals: Map<String, List<String>> = emptyMap()
) {
    val isEmpty: Boolean
        get() = definitionFixes.isEmpty() && removals.isEmpty()
}

/**
 * 内置词库的**订正表**：修好的释义要能传到已经装过 App 的设备上。
 *
 * 为什么必须有这个：
 * [CollocationRepository.seedBuiltInLibrary] 的合并策略是
 * **只新增、不更新** —— 这是刻意的，避免覆盖用户自己导入的同名词条。
 * 但也带来一个副作用：**改好的释义永远传不到老用户那里**。
 *
 * 而且 [com.example.collocationmemory.CollocationMemoryApplication] 里有
 * `if (getLibraryVersion() >= library.version) return` 的短路，
 * 光改 assets 里的 JSON 而不升版本号，老用户连新增都收不到。
 *
 * 所以订正表**不参与版本号比较**，每次启动都对一遍（代价极小：几条 SQL）。
 *
 * 两条来源，合并后生效（同一条以远端为准）：
 * 1. [BUILT_IN] —— 写死在 APK 里的基线，保证**离线也是对的**；
 * 2. [RemoteCorrectionsStore] —— 从服务器下载的增量，不用发新版就能改释义。
 *
 * 约定：
 * - 只改 `chinese`，绝不碰学习进度（isLearned / nextReviewDate / errorCount…）；
 * - 定位用「词本 + 英文」精确匹配，匹配不到就跳过，不会误伤；
 * - 每次改动都要同步 [tools/dev/fix_entries.py]，保证两处一致。
 */
object LibraryCorrections {

    private const val BOOK_PAST = "往年固定搭配单词本"
    private const val BOOK_BLUE = "蓝哥固定搭配单词本"

    /**
     * 释义订正：词本 -> {英文: 正确释义}
     *
     * 只放「确认是错的」，不放风格偏好。
     */
    val DEFINITION_FIXES: Map<String, Map<String, String>> = mapOf(
        BOOK_PAST to mapOf(
            // 原文是 "play an important/key/vital role in 起重要作"，
            // 提取时 important 丢了，剩下 "an key" 语法不通。
            "play an key / vital role in" to "起重要作用",
        ),
    )

    /**
     * 删除错误词条：词本 -> 要删掉的英文列表
     *
     * 用于清理「提取错误产生的幽灵词条」。
     * 例：原文 "Take steps to do 采取行动/措施/步骤" 被切分丢了 Take，
     * 变成 "steps to do" —— 英文和释义对不上，必须删掉。
     */
    val REMOVALS: Map<String, List<String>> = mapOf(
        BOOK_PAST to listOf(
            "steps to do",
        ),
    )

    /** 订正表版本：每次修改订正内容都要 +1，便于日志追踪 */
    const val VERSION = 1

    /** 随 APK 分发的基线订正表（离线也必须生效） */
    val BUILT_IN: CorrectionsData = CorrectionsData(
        version = VERSION,
        definitionFixes = DEFINITION_FIXES,
        removals = REMOVALS
    )

    /**
     * 把「内置基线」和「远端订正」合并成一份最终要执行的订正表。
     *
     * 合并规则：按词本、按英文逐条合并，**远端优先**。
     * 这样老版本 App 里写死的错误订正，也能被服务器上的新订正覆盖掉，
     * 不需要用户重装。
     */
    fun merge(base: CorrectionsData, remote: CorrectionsData?): CorrectionsData {
        if (remote == null || remote.isEmpty) return base

        val fixes = mutableMapOf<String, Map<String, String>>()
        fixes.putAll(base.definitionFixes)
        remote.definitionFixes.forEach { (book, map) ->
            fixes[book] = fixes[book].orEmpty() + map
        }

        val removals = mutableMapOf<String, List<String>>()
        removals.putAll(base.removals)
        remote.removals.forEach { (book, list) ->
            removals[book] = (removals[book].orEmpty() + list).distinct()
        }

        return CorrectionsData(
            version = maxOf(base.version, remote.version),
            definitionFixes = fixes,
            removals = removals
        )
    }
}

/**
 * 把服务器下载来的订正表缓存在本地。
 *
 * 为什么要落盘：订正表下载一次就该一直生效 ——
 * 用户在地铁里打开 App（没网）时，修好的释义依然要在。
 */
object RemoteCorrectionsStore {

    private const val TAG = "CollocationMemory"
    private const val FILE_NAME = "remote_corrections.json"

    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 读取缓存的远端订正；没有或损坏都返回 null */
    fun read(context: Context): CorrectionsData? {
        return try {
            val f = file(context)
            if (!f.exists() || f.length() == 0L) return null
            gson.fromJson(f.readText(), CorrectionsData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "读取远端订正缓存失败：${e.message}")
            null
        }
    }

    /** 写入缓存；写失败不影响本次订正（只是下次要重新下） */
    fun write(context: Context, data: CorrectionsData) {
        try {
            file(context).writeText(gson.toJson(data))
        } catch (e: Exception) {
            Log.w(TAG, "写入远端订正缓存失败：${e.message}")
        }
    }

    /** 读取缓存并与内置基线合并，得到「本次启动要执行的订正表」 */
    fun merged(context: Context): CorrectionsData =
        LibraryCorrections.merge(LibraryCorrections.BUILT_IN, read(context))
}
