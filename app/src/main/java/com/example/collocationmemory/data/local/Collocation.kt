package com.example.collocationmemory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "collocations")
@TypeConverters(Converters::class)
data class Collocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val english: String,
    val chinese: String,
    val example: String,
    val tags: List<String> = emptyList(),
    val isLearned: Boolean = false,
    val isInWordBook: Boolean = false,
    val repetitionCount: Int = 0,
    val intervalDays: Int = 0,
    val nextReviewDate: Long = 0L,
    val lastReviewDate: Long = 0L,
    val easinessFactor: Double = 2.5,
    val priority: Double = 0.0,
    /** 累计答错次数（学习「记错了」+ 拼写错误都算），用于答错文案里的真实数据 */
    val errorCount: Int = 0,
    /**
     * 所属词本名（例如「蓝哥固定搭配单词本」）。
     *
     * 一个词条只归属一个词本，学习进度也是按词本统计的。
     * 同一个英文词若出现在两个词本里，会各存一行：行级进度互相独立，
     * 但因为 `isLearned` 是「这个词会不会」的全局事实，
     * 在 A 本背会之后，B 本那一行会显示「熟」并跳过新学 —— 不需要再背一遍。
     */
    val bookName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class StudyRecord(
    val date: Long,
    val learnedCount: Int,
    val reviewedCount: Int,
    val studyMinutes: Int
)

/** 切换器里每个词本的条数 */
data class BookCount(
    val bookName: String,
    val count: Int
)
