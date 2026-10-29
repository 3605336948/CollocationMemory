package com.example.collocationmemory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records WHERE date = :date")
    suspend fun getByDate(date: Long): DailyRecord?

    @Query("SELECT * FROM daily_records WHERE date = :date")
    fun getByDateFlow(date: Long): Flow<DailyRecord?>

    @Query("SELECT * FROM daily_records ORDER BY date DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<DailyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: DailyRecord)

    @Query("UPDATE daily_records SET learnedCount = learnedCount + :learned, reviewedCount = reviewedCount + :reviewed, studyMinutes = studyMinutes + :minutes WHERE date = :date")
    suspend fun increment(date: Long, learned: Int, reviewed: Int, minutes: Int)

    @Query("SELECT SUM(studyMinutes) FROM daily_records")
    fun getTotalMinutes(): Flow<Int?>

    @Query("SELECT SUM(learnedCount) FROM daily_records")
    fun getTotalLearned(): Flow<Int?>

    /** 追加今天新学的搭配 id（已存在则不重复记录） */
    @Query("UPDATE daily_records SET learnedIds = :ids WHERE date = :date")
    suspend fun updateLearnedIds(date: Long, ids: String)

    /** 追加今天复习过的搭配 id */
    @Query("UPDATE daily_records SET reviewedIds = :ids WHERE date = :date")
    suspend fun updateReviewedIds(date: Long, ids: String)

    /** 追加今天拼写巩固做过的搭配 id */
    @Query("UPDATE daily_records SET spelledIds = :ids WHERE date = :date")
    suspend fun updateSpelledIds(date: Long, ids: String)

    /** 追加今天拼写答错的搭配 id */
    @Query("UPDATE daily_records SET spelledWrongIds = :ids WHERE date = :date")
    suspend fun updateSpelledWrongIds(date: Long, ids: String)

    /** 累计今天拼写答对的条数 */
    @Query("UPDATE daily_records SET spelledCorrectCount = spelledCorrectCount + :count WHERE date = :date")
    suspend fun incrementSpelledCorrect(date: Long, count: Int)

    /** 清空全部每日统计（清空数据时用，不影响词库） */
    @Query("DELETE FROM daily_records")
    suspend fun deleteAll()

    /**
     * 把异常大的学习时长夹到上限以内，返回受影响的行数。
     * 用于修复历史 bug 写入的毫秒级数值（一天不可能学超过 24 小时 = 1440 分钟）。
     */
    @Query("UPDATE daily_records SET studyMinutes = :maxMinutes WHERE studyMinutes > :maxMinutes")
    suspend fun clampStudyMinutes(maxMinutes: Int): Int
}
