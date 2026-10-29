package com.example.collocationmemory.util

/**
 * 拼写判定。
 *
 * 背景：词库里大量词条带有**用户无法输入**的部分，原来的
 * `userInput.trim().equals(item.english, ignoreCase = true)` 严格全等
 * 会把它们全判错。实测受影响的词条约 184 条：
 *
 * | 类型 | 例子 | 用户能输入的 |
 * |---|---|---|
 * | 省略号占位 | `the capital of ……` | the capital of |
 * | 变量占位符 | `allow sb. to do sth.` | allow to do / allow sb to do |
 * | 可省略括号 | `help sb. (to) do sth.` | help sb do sth. |
 * | 冠词 | `the public` | public |
 *
 * 判定策略（2026-10-14 定稿：**宁严勿宽**）：
 * 1. 两侧归一化：去掉省略号/括号/占位符、统一大小写与标点、压缩空格；
 * 2. 归一化后完全相等 → 对；
 * 3. 分隔符写法差异（连字符/空格混用，如 `well-known` vs `well known`）→ 对；
 *    —— 只原谅「分隔符摆在哪」，一个字母都不会放过。
 *
 * 历史：v4.3.3 及之前还按整串编辑距离放行 1~2 个字母的笔误，
 * 用户实测连续翻车（`pay attention tos`、`gather jn` 都被判对），
 * 明确反馈「拼错就该判错」，故整串容错已移除。
 *
 * 注意：这里只收紧拼写判定，不影响「记不记得住」的语义 ——
 * 复习间隔仍按正常流程走。
 */
object SpellMatcher {

    /** 变量占位符：这些是「填空」标记，不属于要背的固定内容 */
    private val PLACEHOLDERS = setOf(
        "sb", "sb's", "sth", "sbs", "sths",
        "one's", "oneself", "somebody", "something", "someone",
        "a", "b", "c",                       // 句型模板里的 A/B/C
    )

    /** 省略号：…、……、...、。。。等 */
    private val ELLIPSIS = Regex("[…\u2026]|\\.{2,}|。{2,}|·{2,}")

    /** 括号（中英文），连同内容一起删除 */
    private val PARENS = Regex("[(（\\[［][^)）\\]］]*[)）\\]］]")

    /**
     * 归一化：把「用户不必输入」的部分全部去掉。
     *
     * 例：
     * - `the capital of ……`        → `capital of`
     * - `allow sb. to do sth.`      → `allow to do`
     * - `help sb. (to) do sth.`     → `help do`
     * - `in common (with)`          → `in common`
     * - `not only ... but also`     → `not only but also`
     */
    fun normalize(raw: String): String {
        var s = raw.lowercase()

        s = ELLIPSIS.replace(s, " ")
        s = PARENS.replace(s, " ")

        // 去掉标点（保留字母、数字、空格、连字符、撇号——撇号用于 one's 这类）
        s = s.replace(Regex("[^a-z0-9\\s'\\-]"), " ")

        // 把 "sb." "sth." 这类带点的占位符去点后再按词过滤
        s = s.replace(".", " ")

        val kept = s.split(Regex("\\s+"))
            .map { it.trim('\'', '-') }
            .filter { it.isNotEmpty() && it !in PLACEHOLDERS }

        // 去掉开头的冠词：用户漏掉 the/a/an 不应算错
        val withoutArticle = if (kept.size > 1 && kept.first() in setOf("the", "a", "an")) {
            kept.drop(1)
        } else {
            kept
        }
        return withoutArticle.joinToString(" ")
    }

    /**
     * 判断用户输入是否正确。
     *
     * @param input  用户输入
     * @param answer 词库里的标准答案
     */
    fun isCorrect(input: String, answer: String): Boolean {
        val a = normalize(input)
        val b = normalize(answer)

        // 两边都归一化后为空（例如答案只有占位符）→ 无法判定，按对处理，
        // 免得出现「怎么打都错」的死局
        if (b.isEmpty()) return true

        // 归一化后完全相等 → 对
        if (a == b) return true

        // 分隔符写法差异（well-known vs well known、漏了空格）→ 对：
        // 只比较「去掉一切分隔符后」的字母序列，字母层面仍要求一个不差
        return squash(a) == squash(b)
    }

    /** 去掉所有非字母数字字符，只剩纯字母序列（用于分隔符写法差异的比较） */
    private fun squash(s: String): String =
        s.filter { it in 'a'..'z' || it in '0'..'9' }
}
