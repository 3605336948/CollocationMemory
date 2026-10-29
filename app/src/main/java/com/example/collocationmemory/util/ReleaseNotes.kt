package com.example.collocationmemory.util

/**
 * 版本公告：每个版本号留一份「这次改了什么」。
 *
 * 展示规则写在 MainViewModel 里：启动时拿记录在偏好里的「上次看过的版本号」
 * （见 UserPreferencesDataStore#ANNOUNCEMENT_VERSION），把之后的版本都列出来，
 * 用户看完点关闭就写回当前版本号 —— 同一条公告不会出现第二次。
 *
 * 发布新版时：在这里追加一条 [Note]，versionCode 必须与 app/build.gradle.kts
 * 里的 versionCode 一致，写成看得懂的一句话一条，别抄 git log。
 */
object ReleaseNotes {

    data class Note(
        val versionCode: Int,
        val versionName: String,
        val items: List<String>
    )

    private val all: List<Note> = listOf(
        Note(
            versionCode = 38,
            versionName = "4.3.3",
            items = listOf(
                "新学页改成沉浸式卡片：字直接压在每日壁纸上，去掉卡片框，看得更清爽",
                "判断改成自评：认识 / 模糊 / 不认识，连对 2 次才算这张过了",
                "去掉了每次点卡片都会蹦出来的「再确认一次」提示",
                "修好了「切后台再回来，刚才那组词全没了」的问题",
                "词库更新：修正部分释义与拼写的错漏"
            )
        ),
        Note(
            versionCode = 39,
            versionName = "4.3.4",
            items = listOf(
                "拼写判定收紧：多打、漏打、错打字母一律算错",
                "拼写页换成和新学一样的沉浸式卡片；没写字就提交只提示「请输入内容」，不再直接给答案",
                "首页瘦身：「今日概览」挪到「统计」页顶部",
                "单词本切换挪到「设置 → 当前单词本」，点书名即可换",
                "底部「生词本」不再误点：「笔记本」入口移到生词本页右上角",
                "卡片「记」角标上移，长词组不再和它挤在一起",
                "蓝哥固定搭配单词本补齐《固定搭配7》，现共 453 条",
                "新增版本公告：升级后进 App 告诉你这次改了什么，看完就不再出现"
            )
        ),
        Note(
            versionCode = 40,
            versionName = "4.3.5",
            items = listOf(
                "新增「词库预览」：设置 → 当前单词本 → 预览词库，没学之前先翻一遍，还能搜英文或中文",
                "学习页焕新：新学页换成深蓝沉浸风格，看答案后释义按词性拆成选项卡；复习页也换成同款沉浸式样式",
                "新学页加配图：看答案时配一张示意图帮助记忆，陆续补充中",
                "修好了复习页反复提示「没有英文语音」的问题，现在只提醒一次",
                "词库更新：新增《固定搭配8》《固定搭配9》，蓝哥固定搭配单词本现共 556 条"
            )
        )
    )

    /** 版本号大于 [seenVersion] 的全部公告，新的排前面 */
    fun since(seenVersion: Int): List<Note> =
        all.filter { it.versionCode > seenVersion }
            .sortedByDescending { it.versionCode }
}
