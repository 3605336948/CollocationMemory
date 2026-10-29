package com.example.collocationmemory

import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.local.SessionSnapshot
import com.example.collocationmemory.data.repository.ReviewResult
import com.example.collocationmemory.util.SpacedRepetition
import com.example.collocationmemory.util.SpellMatcher
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 核心逻辑单元测试。
 *
 * 覆盖三块最容易出事、而且历史上真的出过事的地方：
 * 1. 复习排期（隔多久再见这个词）
 * 2. 拼写判定（什么算拼对）
 * 3. 会话快照（切后台能不能接回来）
 *
 * 这些都是纯 Kotlin / 纯 JVM 的计算，不需要真机就能验证。
 */
class CoreLogicTest {

    private val defaultEf = 2.5

    private fun item(
        repetition: Int = 0,
        interval: Int = 0,
        ef: Double = defaultEf
    ) = Collocation(
        id = 1,
        english = "take part in",
        chinese = "参加",
        example = "",
        repetitionCount = repetition,
        intervalDays = interval,
        easinessFactor = ef
    )

    // ==================== 复习排期 ====================

    @Test
    fun 今天学会的词_明天零点就到期() {
        val now = System.currentTimeMillis()
        val learned = SpacedRepetition.initializeAfterLearn(item())

        val tomorrow = SpacedRepetition.startOfTomorrow(now)
        // 允许一点点时间漂移（两次调用之间过了几毫秒）
        assertTrue(
            "应排到明天零点，实际 ${learned.nextReviewDate}",
            kotlin.math.abs(learned.nextReviewDate - tomorrow) < 2000
        )
        assertTrue("还没到复习时间", learned.nextReviewDate > now)
    }

    @Test
    fun 按自然日排期_不是24小时后() {
        val tomorrow = SpacedRepetition.startOfTomorrow()
        val gap = tomorrow - System.currentTimeMillis()
        // 自然日排期：间隔一定小于 24 小时（零点到期），
        // 「24 小时后」排期会等于 24 小时 —— 那正是历史上导致「今天该复习 0 条」的写法
        assertTrue("间隔应在 24 小时以内，实际 $gap ms", gap < 24 * 60 * 60 * 1000L)
        assertTrue(gap > 0)
    }

    @Test
    fun 答认识_间隔按1_3_7递增() {
        val expected = listOf(1, 3, 7)
        var current = item()
        expected.forEach { want ->
            current = SpacedRepetition.applyReview(current, ReviewResult.KNOW)
            assertEquals("第 ${current.repetitionCount} 次「认识」的间隔", want, current.intervalDays)
        }
    }

    // ==================== 新学排期：答错过的词间隔更长 ====================

    @Test
    fun 未答错过的词_首次间隔1天_明天零点() {
        val now = System.currentTimeMillis()
        val learned = SpacedRepetition.initializeAfterLearn(item(), hadError = false)
        val tomorrow = SpacedRepetition.startOfTomorrow(now)
        assertTrue(
            "未答错过的词应排到明天零点，实际 ${learned.nextReviewDate}",
            kotlin.math.abs(learned.nextReviewDate - tomorrow) < 2000
        )
        assertEquals(1, learned.intervalDays)
    }

    @Test
    fun 缺省参数_按1天处理() {
        // 旧调用方不传 hadError 时行为不变，仍是 1 天
        val learned = SpacedRepetition.initializeAfterLearn(item())
        assertEquals(1, learned.intervalDays)
    }

    @Test
    fun 答错过的词_首次间隔2天_后天零点() {
        val now = System.currentTimeMillis()
        val learned = SpacedRepetition.initializeAfterLearn(item(), hadError = true)
        // 后天零点 = 明天零点 + 一天
        val dayAfter = SpacedRepetition.startOfTomorrow(now) + 24 * 60 * 60 * 1000L
        assertTrue(
            "答错过的词应排到后天零点，实际 ${learned.nextReviewDate}",
            kotlin.math.abs(learned.nextReviewDate - dayAfter) < 2000
        )
        assertEquals(2, learned.intervalDays)
        assertTrue(learned.nextReviewDate > now)
    }

    @Test
    fun 答忘记_重排到明天且置顶优先级() {
        val current = SpacedRepetition.applyReview(item(repetition = 5, interval = 30), ReviewResult.FORGET)
        assertEquals(0, current.repetitionCount)
        assertEquals(1, current.intervalDays)
        assertEquals(100.0, current.priority, 0.001)
    }

    @Test
    fun 答模糊_间隔减半_不少于1天() {
        val current = SpacedRepetition.applyReview(item(repetition = 2, interval = 3), ReviewResult.VAGUE)
        assertEquals(1, current.intervalDays)
    }

    // ==================== 拼写判定 ====================

    @Test
    fun 完全正确的拼写通过() {
        assertTrue(SpellMatcher.isCorrect("take part in", "take part in"))
    }

    @Test
    fun 大小写与多余空格不算错() {
        assertTrue(SpellMatcher.isCorrect("  Take   Part  In ", "take part in"))
        assertTrue(SpellMatcher.isCorrect("TAKE PART IN", "take part in"))
    }

    @Test
    fun 漏写冠词不算错() {
        assertTrue(SpellMatcher.isCorrect("make up for", "the make up for"))
        assertTrue(SpellMatcher.isCorrect("pay attention to", "pay attention to"))
    }

    @Test
    fun 占位符与标点不算错() {
        assertTrue(SpellMatcher.isCorrect("make up for", "make up for sth."))
        assertTrue(SpellMatcher.isCorrect("take care of", "take care of ..."))
    }

    @Test
    fun 真的拼错会被判错() {
        assertFalse(SpellMatcher.isCorrect("take part on the way", "take part in"))
        assertFalse(SpellMatcher.isCorrect("take part", "take part in"))
        assertFalse(SpellMatcher.isCorrect("abcdefg hijklmn", "take part in"))
    }

    @Test
    fun 短词一个字母都不许错() {
        // 注释里写死的例子：public / publik 长度 6，容差 0
        assertFalse(SpellMatcher.isCorrect("publik", "public"))
    }

    @Test
    fun 任何字母拼错都判错() {
        // 用户反馈定稿（2026-10-14）：宁严勿宽，同长度的字母替换也判错
        assertFalse(SpellMatcher.isCorrect("take part on", "take part in"))
        assertFalse(SpellMatcher.isCorrect("take hart in", "take part in"))
        assertFalse(SpellMatcher.isCorrect("gather jn", "gather in"))
    }

    @Test
    fun 分隔符写法差异不算错() {
        // 连字符/空格混用、漏空格：字母序列一致就放行，但一个字母都不能错
        assertTrue(SpellMatcher.isCorrect("well known", "well-known"))
        assertTrue(SpellMatcher.isCorrect("uptodate", "up-to-date"))
        assertFalse(SpellMatcher.isCorrect("wellknownn", "well-known"))
    }

    @Test
    fun 多打一个词判错() {
        // 用户反馈（2026-10-14）：多输了一个单词也算对了 → 词数不同一律判错
        assertFalse(SpellMatcher.isCorrect("pay attention to the", "pay attention to"))
        assertFalse(SpellMatcher.isCorrect("pay attention to s", "pay attention to"))
        assertFalse(SpellMatcher.isCorrect("pay to attention", "pay attention to"))
    }

    @Test
    fun 短词多打字母判错() {
        // 用户反馈：`pay attention tos`（to 多打一个 s）被判对 → 现已一律判错
        assertFalse(SpellMatcher.isCorrect("pay attention tos", "pay attention to"))
        assertFalse(SpellMatcher.isCorrect("look attos", "look at"))
        // 长词里增删字母同样判错（宁严勿宽）
        assertFalse(SpellMatcher.isCorrect("pay atention to", "pay attention to"))
    }

    // ==================== 会话快照（切后台恢复） ====================

    @Test
    fun 会话快照_JSON往返不丢字段() {
        val snap = SessionSnapshot(
            source = SessionSnapshot.SOURCE_REVIEW,
            phase = SessionSnapshot.PHASE_SPELL,
            itemIds = listOf(3, 1, 2),
            index = 2,
            spellExcludedIds = listOf(9),
            reviewWrongIds = listOf(3, 5),
            wrongIds = listOf(7, 8),
            spellIndex = 1,
            updatedAt = 1_700_000_000_000L
        )
        val json = Gson().toJson(snap)
        val back = Gson().fromJson(json, SessionSnapshot::class.java)

        assertEquals(snap.source, back.source)
        assertEquals(snap.phase, back.phase)
        assertEquals(snap.itemIds, back.itemIds)
        assertEquals(snap.index, back.index)
        assertEquals(snap.spellExcludedIds, back.spellExcludedIds)
        assertEquals(snap.reviewWrongIds, back.reviewWrongIds)
        assertEquals(snap.wrongIds, back.wrongIds)
        assertEquals(snap.spellIndex, back.spellIndex)
        assertEquals(snap.updatedAt, back.updatedAt)
    }

    @Test
    fun 快照含wrongIds_JSON往返不丢() {
        val snap = SessionSnapshot(
            source = SessionSnapshot.SOURCE_LEARN,
            phase = SessionSnapshot.PHASE_CARDS,
            itemIds = listOf(1, 2, 3),
            index = 1,
            wrongIds = listOf(11, 12, 13),
            updatedAt = 1_700_000_000_000L
        )
        val json = Gson().toJson(snap)
        val back = Gson().fromJson(json, SessionSnapshot::class.java)
        assertEquals(listOf(11L, 12L, 13L), back.wrongIds)
    }

    @Test
    fun 旧快照缺wrongIds_解析为空列表() {
        // 模拟方案3上线前写下的旧快照：没有 wrongIds 这个字段。
        // parseSession 逐字段兜底读，缺字段应返回空列表，按「本次会话没答错过」处理，不崩。
        val oldJson = """{
            "source":"learn",
            "phase":"cards",
            "itemIds":[1,2,3],
            "index":0,
            "spellExcludedIds":[],
            "reviewWrongIds":[],
            "spellIndex":0,
            "updatedAt":1700000000000
        }"""
        val snap = com.example.collocationmemory.data.repository.parseSession(oldJson)
        assertEquals(emptyList<Long>(), snap?.wrongIds)
    }

    @Test
    fun 刚写下的快照可以续() {
        val now = System.currentTimeMillis()
        val snap = SessionSnapshot(
            source = SessionSnapshot.SOURCE_LEARN,
            itemIds = listOf(1, 2, 3),
            updatedAt = now
        )
        assertTrue(snap.isActiveNow(now))
    }

    @Test
    fun 超过12小时的快照不再打扰用户() {
        val now = System.currentTimeMillis()
        val stale = SessionSnapshot(
            source = SessionSnapshot.SOURCE_LEARN,
            itemIds = listOf(1, 2, 3),
            updatedAt = now - SessionSnapshot.TTL_MILLIS - 1000
        )
        assertFalse(stale.isActiveNow(now))
    }

    @Test
    fun 没有词条的空会话不算可续() {
        val empty = SessionSnapshot(source = SessionSnapshot.SOURCE_LEARN, itemIds = emptyList())
        assertFalse(empty.isActive)
        assertFalse(empty.isActiveNow())
    }

    // ==================== 新学状态机：所有卡片连续答对 2 次才算学会（v4.3.2） ====================

    @Test
    fun 所有新学卡片_连对不足2次需再确认() {
        // v4.3.2：不论本次会话是否答错过，连续答对都需满 2 次才算学会。
        // 连续答对 0 次、1 次都还要再确认（重新排队），满 2 次才放行。
        assertTrue(com.example.collocationmemory.ui.viewmodel.needsAnotherConfirm(0))
        assertTrue(com.example.collocationmemory.ui.viewmodel.needsAnotherConfirm(1))
        assertFalse(com.example.collocationmemory.ui.viewmodel.needsAnotherConfirm(2))
    }
}
