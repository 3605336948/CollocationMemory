package com.example.collocationmemory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "daily_records")
@TypeConverters(Converters::class)
data class DailyRecord(
    @PrimaryKey
    val date: Long,
    val learnedCount: Int = 0,
    val reviewedCount: Int = 0,
    val studyMinutes: Int = 0,
    /** 今天新学的搭配 id（用于首页预览今天学了哪些） */
    val learnedIds: List<Long> = emptyList(),
    /** 今天复习过的搭配 id */
    val reviewedIds: List<Long> = emptyList(),
    /** 今天拼写巩固做过的搭配 id */
    val spelledIds: List<Long> = emptyList(),
    /** 今天拼写答错的搭配 id */
    val spelledWrongIds: List<Long> = emptyList(),
    /** 今天拼写答对的条数 */
    val spelledCorrectCount: Int = 0
)
