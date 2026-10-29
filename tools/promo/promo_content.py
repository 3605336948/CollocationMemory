# -*- coding: utf-8 -*-
"""
宣传片内容脚本。

每一个章节 = 手机里演一段界面 + 右侧显示一段真实源码 + 底部一句面向家长的文案。
代码全部取自 CollocationMemory 项目的真实文件（路径写在 file 字段里），
只在排版上做了截断，语义未改。
"""

CHAPTERS = [
    # ---------------------------------------------------------------- 1
    {
        "scene": "home",
        "file": "ui/screens/HomeScreen.kt",
        "focus_line": 3,
        "caption": "今天学了多少、复习了多少，首页一眼看得见",
        "code": [
            "// 今日概览：四个数字都是从当日记录实时统计出来的",
            'GlassStat(label = "学习&复习",',
            "    value = \"${today.learnedCount + today.reviewedCount}\")",
            'GlassStat(label = "累计学习", value = "${totalLearned}")',
            'GlassStat(label = "今日时长", value = "${today.studyMinutes}", unit = "分")',
            'GlassStat(label = "累计时长", value = "${totalMinutes}", unit = "分")',
        ],
    },
    # ---------------------------------------------------------------- 2
    {
        "scene": "learn",
        "file": "util/SpeechHelper.kt",
        "focus_line": 8,
        "caption": "翻到一张卡片自动朗读，听力和拼写一起练",
        "code": [
            "/** 朗读一段英文。返回 false 表示没读成（引擎不可用） */",
            "fun speak(text: String): Boolean {",
            "    val engine = tts",
            "    if (engine == null) return false",
            "    engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)",
            "}",
            "",
            "// 翻到一张卡片 → 自动念一遍英文",
            "LaunchedEffect(currentIndex, items, autoSpeak) {",
            "    if (autoSpeak && currentIndex < items.size) {",
            "        speakNow(items[currentIndex].english)",
            "    }",
            "}",
        ],
    },
    # ---------------------------------------------------------------- 3
    {
        "scene": "familiar",
        "file": "ui/viewmodel/MainViewModel.kt",
        "focus_line": 4,
        "caption": "会了的词点「熟」直接跳过，不浪费孩子的时间",
        "code": [
            "/** 「熟」：这个词我已经会了 —— 直接排入复习，跳过拼写 */",
            "fun markFamiliarAndSkip() {",
            "    val current = items[index]",
            "    viewModelScope.launch {",
            "        repository.markLearnedNow(current.id)",
            "        learnedCountInSession++",
            "        // 加进拼写排除名单：这一组最后不再让他拼这个词",
            "        _spellExcludedIds.value = _spellExcludedIds.value + current.id",
            "        nextCard()",
            "    }",
            "}",
        ],
    },
    # ---------------------------------------------------------------- 4
    {
        "scene": "review",
        "file": "util/SpacedRepetition.kt",
        "focus_line": 2,
        "caption": "该复习哪些、什么时候复习，App 按遗忘曲线算好了",
        "code": [
            "object SpacedRepetition {",
            "    // 答「认识」后的下次间隔（天）：1、3、7、14、30、60、120",
            "    private val KNOW_INTERVALS = listOf(1, 3, 7, 14, 30, 60, 120)",
            "    private const val MIN_EF = 1.3",
            "    private const val DEFAULT_EF = 2.5",
            "",
            "    /** 排期按「自然日」算：今天学会的，明天零点就到期 */",
            "    private fun dueAt(now: Long, days: Int): Long =",
            "        startOfToday(now) + days * ONE_DAY",
            "}",
        ],
    },
    # ---------------------------------------------------------------- 5
    {
        "scene": "spell",
        "file": "util/SpellMatcher.kt",
        "focus_line": 6,
        "caption": "拼写严格又宽容：冠词、标点写漏了不算错",
        "code": [
            "// 去掉开头的冠词：用户漏掉 the / a / an 不应算错",
            "val withoutArticle = if (kept.size > 1",
            "    && kept.first() in setOf(\"the\", \"a\", \"an\")) kept.drop(1)",
            "else kept",
            "",
            "fun isCorrect(input: String, answer: String): Boolean {",
            "    val a = normalize(input)",
            "    val b = normalize(answer)",
            "    if (a == b) return true",
            "    // 允许少量拼写误差（编辑距离容差）",
            "    return editDistance(a, b) <= tolerance",
            "}",
        ],
    },
    # ---------------------------------------------------------------- 6
    {
        "scene": "notebook",
        "file": "data/local/NotebookEntry.kt",
        "focus_line": 3,
        "caption": "笔记本只存在手机本地，不上传，隐私放心",
        "code": [
            "/**",
            " * 笔记本里的一条记录。只存本地（DataStore 的 JSON），",
            " * 不参与任何同步或上传。",
            " */",
            "data class NotebookEntry(",
            "    val id: Long,          // 本地唯一 id",
            "    val english: String,   // 收进来的搭配（去重键）",
            "    val chinese: String,   // 收进来时的释义",
            "    val note: String = \"\", // 用户自己写的内容",
            "    val createdAt: Long = 0L",
            ")",
        ],
    },
    # ---------------------------------------------------------------- 7
    {
        "scene": "stats",
        "file": "data/local/DailyRecordDao.kt",
        "focus_line": 2,
        "caption": "连续打卡、每日时长与条数，学情随时可查",
        "code": [
            "/** 每日记录：学了多少、复习多少、学了多久 */",
            "@Query(\"\"\"UPDATE daily_records",
            "    SET learnedCount = learnedCount + :learned,",
            "        reviewedCount = reviewedCount + :reviewed,",
            "        studyMinutes = studyMinutes + :minutes",
            "    WHERE date = :date\"\"\")",
            "suspend fun increment(",
            "    date: Long, learned: Int, reviewed: Int, minutes: Int",
            ")",
            "",
            "@Query(\"SELECT SUM(studyMinutes) FROM daily_records\")",
            "fun getTotalMinutes(): Flow<Int?>",
        ],
    },
    # ---------------------------------------------------------------- 8
    {
        "scene": "update",
        "file": "data/repository/UpdateRepository.kt",
        "focus_line": 4,
        "caption": "更新不用找安装包：App 内一键下载安装，学习记录不丢",
        "code": [
            "// 下载后三关校验：文件大小 → SHA-256 → 签名证书比对",
            "private fun isApkUsable(file: File, info: AppUpdateInfo): Boolean {",
            "    if (info.apkSize > 0 && file.length() != info.apkSize) return false",
            "    if (!RemoteFetch.sha256(file)",
            "        .equals(info.apkSha256, ignoreCase = true)) return false",
            "    return ApkInstaller.verifySameApp(appContext, file)",
            "}",
        ],
    },
]

OUTRO = {
    "title": "搭配记忆",
    "subtitle": "让每天背的单词都算数",
    "bullets": [
        "自动生成复习排期 · 不用自己记哪天该复习谁",
        "新学 · 复习 · 拼写 · 笔记本 · 学情统计，一条链路走完",
        "词库可远程增补，学习进度永远留在手机里",
    ],
    "version": "v4.2 · 免费使用",
}
