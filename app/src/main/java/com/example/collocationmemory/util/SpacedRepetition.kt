package com.example.collocationmemory.util

import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.data.repository.ReviewResult
import kotlin.math.max
import kotlin.math.roundToInt

object SpacedRepetition {
    // Standard intervals for "know" responses (in days)
    private val KNOW_INTERVALS = listOf(1, 3, 7, 14, 30, 60, 120)
    private const val MIN_EF = 1.3
    private const val DEFAULT_EF = 2.5
    private const val ONE_DAY = 24 * 60 * 60 * 1000L

    /**
     * 普通词「刚学会」的首次复习间隔：明天零点到期（1 天）。
     */
    private const val FIRST_INTERVAL_DAYS = 1

    /**
     * 本次会话答错过的词，首次复习间隔拉长到 2 天（后天零点到期）。
     *
     * 为什么要更长：答错说明这次没真记住，给 1 天太松，容易「明天看一眼又忘了」；
     * 多放一天缓冲，让间隔重复算法从更稳的起点开始。
     */
    private const val FIRST_INTERVAL_DAYS_AFTER_ERROR = 2

    /**
     * 当天零点的时间戳（本地时区）。
     *
     * 复习排期必须按「自然日」算，不能按「24 小时后」算：
     * 昨晚 21:55 学会的词如果排到今晚 21:55，用户第二天早上打开 App
     * 会看到「今日复习 0」，以为记录丢了。按自然日算，第二天零点就到期。
     */
    private fun startOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * `days` 天后的零点。days=1 表示「明天零点到期」——
     * 也就是今天学的词，明天一整天都算待复习。
     */
    private fun dueAt(now: Long, days: Int): Long = startOfToday(now) + days * ONE_DAY

    /**
     * 明天零点的时间戳。
     *
     * 供「标记为已学会」等场景复用 —— 全项目只在这里定义一次
     * 「明天」的含义，避免各处又写成「24 小时后」。
     */
    fun startOfTomorrow(now: Long = System.currentTimeMillis()): Long = dueAt(now, 1)

    /**
     * Apply review result to a collocation and return updated entity.
     * Uses a modified Ebbinghaus schedule:
     * - Know: follow standard intervals then exponential growth
     * - Vague: halve the next interval
     * - Forget: reset to 1 day and mark high priority
     */
    fun applyReview(collocation: Collocation, result: ReviewResult): Collocation {
        val now = System.currentTimeMillis()

        return when (result) {
            ReviewResult.KNOW -> {
                val newRepetition = collocation.repetitionCount + 1
                val nextIntervalDays = when {
                    newRepetition <= KNOW_INTERVALS.size -> KNOW_INTERVALS[newRepetition - 1]
                    else -> {
                        val previousInterval = collocation.intervalDays
                        (previousInterval * collocation.easinessFactor).roundToInt()
                    }
                }
                val newEf = max(MIN_EF, collocation.easinessFactor + 0.1)
                collocation.copy(
                    repetitionCount = newRepetition,
                    intervalDays = nextIntervalDays,
                    nextReviewDate = dueAt(now, nextIntervalDays),
                    lastReviewDate = now,
                    easinessFactor = newEf,
                    priority = 0.0
                )
            }
            ReviewResult.VAGUE -> {
                val halvedInterval = max(1, collocation.intervalDays / 2)
                val newEf = max(MIN_EF, collocation.easinessFactor - 0.15)
                val nextDue = dueAt(now, halvedInterval)
                collocation.copy(
                    intervalDays = halvedInterval,
                    nextReviewDate = nextDue,
                    lastReviewDate = now,
                    easinessFactor = newEf,
                    priority = calculatePriority(now, nextDue, newEf)
                )
            }
            ReviewResult.FORGET -> {
                collocation.copy(
                    repetitionCount = 0,
                    intervalDays = 1,
                    nextReviewDate = dueAt(now, 1),
                    lastReviewDate = now,
                    easinessFactor = max(MIN_EF, collocation.easinessFactor - 0.3),
                    priority = 100.0
                )
            }
        }
    }

    /**
     * Calculate priority score: overdue degree * unfamiliarity degree.
     * Higher score means should be reviewed earlier.
     */
    fun calculatePriority(
        currentTime: Long,
        nextReviewDate: Long,
        easinessFactor: Double
    ): Double {
        val overdueDays = max(0, (currentTime - nextReviewDate) / ONE_DAY)
        val unfamiliarity = max(1.0, (DEFAULT_EF - easinessFactor + 0.1) * 10)
        return overdueDays * unfamiliarity
    }

    /**
     * 刚学会一个词：明天零点起进入复习队列。
     *
     * 用「明天零点」而不是「24 小时后」，这样用户第二天打开就能看到它待复习。
     *
     * @param hadError 这次会话里这个词有没有答错过。
     *                 答错过的词首次间隔拉长到 2 天（[FIRST_INTERVAL_DAYS_AFTER_ERROR]），
     *                 没答错过的照旧 1 天（[FIRST_INTERVAL_DAYS]）。
     */
    fun initializeAfterLearn(collocation: Collocation, hadError: Boolean = false): Collocation {
        val now = System.currentTimeMillis()
        val intervalDays = if (hadError) FIRST_INTERVAL_DAYS_AFTER_ERROR else FIRST_INTERVAL_DAYS
        return collocation.copy(
            isLearned = true,
            repetitionCount = 0,
            intervalDays = intervalDays,
            nextReviewDate = dueAt(now, intervalDays),
            lastReviewDate = now,
            easinessFactor = DEFAULT_EF,
            priority = 0.0
        )
    }
}
