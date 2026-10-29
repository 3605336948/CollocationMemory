package com.example.collocationmemory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 支持多词本（bookName）。
 *
 * 设计要点：
 * - 所有「学习/复习/生词本/统计」查询都按**当前词本**过滤，进度是每本独立的；
 * - 但 `isLearned` 表示「这个英文词你会不会」这一全局事实，所以学会时会把
 *   同英文的所有行一起标记（见 markLearnedByEnglish）——避免同一个词在另一个
 *   词本里又被当成新词背一遍。
 */
@Dao
interface CollocationDao {
    @Query("SELECT * FROM collocations WHERE bookName = :book ORDER BY id ASC")
    fun getAll(book: String): Flow<List<Collocation>>

    /** 全部词本的词条（用于小词本时补足选项干扰项） */
    @Query("SELECT * FROM collocations ORDER BY id ASC")
    fun getAllBooks(): Flow<List<Collocation>>

    /**
     * 当前词本里**还没学会**的词。
     *
     * 关键：用 EXISTS 子查询排除「任何词本里已经学会」的同英文词。
     * 于是重叠词不会在新词本里被重复背一遍 —— 它们仍会显示在列表里，
     * 只是带上「熟」标记（见 getMasteredCount / getLearnedCount）。
     */
    @Query(
        "SELECT * FROM collocations WHERE bookName = :book AND NOT EXISTS " +
            "(SELECT 1 FROM collocations c2 WHERE c2.english = collocations.english AND c2.isLearned = 1) " +
            "ORDER BY id ASC"
    )
    fun getNewToLearn(book: String): Flow<List<Collocation>>

    /** 需要复习的词：只算真正学会、且到期的（跨词本学会的也一并复习，避免遗漏） */
    @Query(
        "SELECT * FROM collocations WHERE bookName = :book AND isLearned = 1 " +
            "AND nextReviewDate <= :currentTime"
    )
    suspend fun getDueReview(book: String, currentTime: Long): List<Collocation>

    @Query("SELECT * FROM collocations WHERE bookName = :book AND isInWordBook = 1")
    fun getWordBook(book: String): Flow<List<Collocation>>

    // ==================== 当前词本的统计 ====================
    //
    // 「已学会」用 EXISTS 子查询判断：只要这个词在**任何**词本里学会过就算会。
    // 这样同一份进度在多个词本里表现一致，也不会让用户重复学习。

    @Query("SELECT COUNT(*) FROM collocations WHERE bookName = :book")
    fun getTotalCount(book: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM collocations WHERE bookName = :book AND EXISTS " +
            "(SELECT 1 FROM collocations c2 WHERE c2.english = collocations.english AND c2.isLearned = 1)"
    )
    fun getLearnedCount(book: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM collocations WHERE bookName = :book AND NOT EXISTS " +
            "(SELECT 1 FROM collocations c2 WHERE c2.english = collocations.english AND c2.isLearned = 1)"
    )
    fun getNewCount(book: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM collocations WHERE bookName = :book AND isLearned = 1 AND nextReviewDate <= :currentTime")
    fun getDueCount(book: String, currentTime: Long): Flow<Int>

    /** 指定时间区间内到期的复习数量（用于「明天待复习」预览） */
    @Query(
        "SELECT COUNT(*) FROM collocations WHERE bookName = :book AND isLearned = 1 " +
            "AND nextReviewDate >= :start AND nextReviewDate < :end"
    )
    fun getDueCountBetween(book: String, start: Long, end: Long): Flow<Int>

    /** 指定时间区间内到期的搭配（用于「明天待复习」列表） */
    @Query(
        "SELECT * FROM collocations WHERE bookName = :book AND isLearned = 1 " +
            "AND nextReviewDate >= :start AND nextReviewDate < :end ORDER BY nextReviewDate ASC"
    )
    suspend fun getDueBetween(book: String, start: Long, end: Long): List<Collocation>

    /** 已掌握的条数（当前词本）：本词本学过且复习达标，或已在别的词本学会 */
    @Query(
        "SELECT COUNT(*) FROM collocations WHERE bookName = :book AND (" +
            "(isLearned = 1 AND repetitionCount >= 3) OR " +
            "(isLearned = 0 AND EXISTS (SELECT 1 FROM collocations c2 " +
            "WHERE c2.english = collocations.english AND c2.isLearned = 1 AND c2.repetitionCount >= 3)))"
    )
    fun getMasteredCount(book: String): Flow<Int>

    // ==================== 跨词本：判断某些词是不是已经会了 ====================

    /**
     * 取所有词本里「已经学会」的英文词集合。
     * 界面用它给当前词本里同英文的词打上「熟」标记。
     */
    @Query("SELECT DISTINCT english FROM collocations WHERE isLearned = 1")
    fun getLearnedEnglish(): Flow<List<String>>

    /**
     * 把某个英文词在所有词本里的行一起标记为已学会。
     *
     * 这是「一个词在 A 本背会了，B 本就算熟」的实现：直接用 SQL 批量更新，
     * 不需要额外学习一遍。
     */
    @Query("UPDATE collocations SET isLearned = 1 WHERE english = :english")
    suspend fun markLearnedByEnglish(english: String)

    /** 把某个英文词在所有词本里的复习排期一起设定（用于「标记为已学会」） */
    @Query(
        "UPDATE collocations SET isLearned = 1, intervalDays = 1, " +
            "nextReviewDate = :nextReviewDate, lastReviewDate = :lastReviewDate WHERE english = :english"
    )
    suspend fun scheduleByEnglish(english: String, nextReviewDate: Long, lastReviewDate: Long)

    // ==================== 其它 ====================

    /** 按 id 批量取搭配（用于「今天学了哪些」预览） */
    @Query("SELECT * FROM collocations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Collocation>

    /** 取出所有带学习进度的搭配（清空进度时按 id 逐条复位） */
    @Query("SELECT id FROM collocations WHERE isLearned = 1 OR isInWordBook = 1 OR repetitionCount > 0")
    suspend fun getIdsWithProgress(): List<Long>

    /** 只复位某条搭配的学习进度，保留词条本身 */
    @Query(
        """
        UPDATE collocations SET
            isLearned = 0,
            isInWordBook = 0,
            repetitionCount = 0,
            intervalDays = 0,
            nextReviewDate = 0,
            lastReviewDate = 0,
            easinessFactor = 2.5,
            priority = 0.0
        WHERE id = :id
        """
    )
    suspend fun resetProgress(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collocation: Collocation): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(collocations: List<Collocation>): List<Long>

    /** 取本地已有的英文搭配（用于内置词库增量合并时判断哪些是新词） */
    @Query("SELECT english FROM collocations WHERE bookName = :book")
    suspend fun getAllEnglish(book: String): List<String>

    /** 迁移用：把旧数据（默认词本名）归位到新的词本名 */
    @Query("UPDATE collocations SET bookName = :newBook WHERE bookName = :oldBook")
    suspend fun renameBook(oldBook: String, newBook: String)

    /**
     * 按「词本 + 英文」订正释义。
     *
     * 为什么需要单独一个方法：seedBuiltInLibrary 的合并策略是「只新增、不更新」
     * （这是对的，避免覆盖用户自己导入的同名词条），
     * 所以修好的释义传不到已安装的设备。订正表走这个通道。
     *
     * 只改 chinese，**不碰任何学习进度字段**。
     */
    @Query("UPDATE collocations SET chinese = :chinese WHERE bookName = :book AND english = :english")
    suspend fun updateChinese(book: String, english: String, chinese: String): Int

    /** 按 id 删除（用于清理词库里被发现是错误/重复的词条） */
    @Query("DELETE FROM collocations WHERE bookName = :book AND english = :english")
    suspend fun deleteByEnglish(book: String, english: String): Int

    @Query("SELECT DISTINCT bookName FROM collocations")
    suspend fun getAllBookNames(): List<String>

    /** 所有词本名（界面上的切换器用），按名称排序、过滤空名 */
    @Query("SELECT DISTINCT bookName FROM collocations WHERE bookName != '' ORDER BY bookName ASC")
    fun observeBookNames(): Flow<List<String>>

    /** 每个词本的条数，用于切换器上显示「蓝哥本（193）」这类信息 */
    @Query("SELECT bookName, COUNT(*) AS count FROM collocations WHERE bookName != '' GROUP BY bookName")
    fun observeBookCounts(): Flow<List<BookCount>>

    @Update
    suspend fun update(collocation: Collocation)

    @Query("UPDATE collocations SET isInWordBook = :inWordBook WHERE id = :id")
    suspend fun updateWordBookStatus(id: Long, inWordBook: Boolean)

    /** 只提升优先级一个字段（答错时用），避免整条写回覆盖其它字段 */
    @Query("UPDATE collocations SET priority = priority + :delta WHERE id = :id")
    suspend fun addPriority(id: Long, delta: Double)

    /** 累计答错次数 +1 */
    @Query("UPDATE collocations SET errorCount = errorCount + 1 WHERE id = :id")
    suspend fun incrementErrorCount(id: Long)

    @Query("SELECT errorCount FROM collocations WHERE id = :id")
    suspend fun getErrorCount(id: Long): Int?

    @Query("SELECT * FROM collocations WHERE id = :id")
    suspend fun getById(id: Long): Collocation?

    @Query("DELETE FROM collocations")
    suspend fun deleteAll()
}
