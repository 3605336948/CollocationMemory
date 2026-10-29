package com.example.collocationmemory.data.local

/**
 * 「还没做完的一组学习/复习」的快照，持久化在本机。
 *
 * 为什么需要它：整个学习会话原本只活在内存里（连拼写到第几个词都是界面组件的临时状态）。
 * 华为 / 荣耀 / 鸿蒙这类系统在切后台时会直接杀掉 App 进程，一杀，词表、进度、
 * 拼写位置就全没了；更糟的是答过的词当场就排到了明天，所以重新点「复习」
 * 只能拿到下一批 —— 用户看到的就是「之前那一组全不见了」。
 *
 * 因此每推进一步就把快照写进 DataStore，重新启动后原样接上。
 *
 * 只存本机，不上传、不参与任何同步。
 *
 * @param source            这一组来自哪儿：[SOURCE_LEARN] / [SOURCE_REVIEW]；空串 = 没有会话
 * @param phase             做到哪一步了：[PHASE_CARDS] / [PHASE_SPELL_CHOICE] / [PHASE_SPELL]
 * @param itemIds           这一组的词条 id（按会话顺序）。存 id 而不是整条词，
 *                          这样释义在期间被热更新订正过也能拿到最新的
 * @param index             卡片推进到第几张
 * @param spellExcludedIds  学新时点了「熟」、跳过拼写的词
 * @param reviewWrongIds    复习时点了「忘记」的词（供「只拼写记错的」使用）
 * @param wrongIds          本次会话新学流程里答错过的词（驱动「连续答对 2 次才算学会 /
 *                         强制拼写 / 首次间隔 2 天」三件事）；旧快照没有这一项时按空列表处理
 * @param spellIndex        拼写推进到第几个词（断点续拼的关键）
 * @param updatedAt         最后一次变动的时间，用来判断快照是否过期
 */
data class SessionSnapshot(
    val source: String = "",
    val phase: String = PHASE_CARDS,
    val itemIds: List<Long> = emptyList(),
    val index: Int = 0,
    val spellExcludedIds: List<Long> = emptyList(),
    val reviewWrongIds: List<Long> = emptyList(),
    val wrongIds: List<Long> = emptyList(),
    val spellIndex: Int = 0,
    val updatedAt: Long = 0L
) {

    /** 有没有可续的会话（不看新鲜度） */
    val isActive: Boolean
        get() = source.isNotEmpty() && itemIds.isNotEmpty()

    /**
     * 现在还能不能续。
     *
     * 超过 [TTL_MILLIS] 没动过的就算过期 —— 否则昨天没拼完的那几个词，
     * 今天一开 App 又蹦出来，反而更让人困惑。
     */
    fun isActiveNow(now: Long = System.currentTimeMillis()): Boolean =
        isActive && updatedAt > 0L && (now - updatedAt) < TTL_MILLIS

    companion object {
        const val SOURCE_LEARN = "learn"
        const val SOURCE_REVIEW = "review"

        const val PHASE_CARDS = "cards"
        const val PHASE_SPELL_CHOICE = "spellChoice"
        const val PHASE_SPELL = "spell"

        /** 快照有效期：12 小时 */
        const val TTL_MILLIS = 12 * 60 * 60 * 1000L
    }
}
