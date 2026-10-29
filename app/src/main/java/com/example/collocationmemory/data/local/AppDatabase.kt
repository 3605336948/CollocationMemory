package com.example.collocationmemory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Collocation::class, DailyRecord::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collocationDao(): CollocationDao
    abstract fun dailyRecordDao(): DailyRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_records (
                        date INTEGER PRIMARY KEY NOT NULL,
                        learnedCount INTEGER NOT NULL DEFAULT 0,
                        reviewedCount INTEGER NOT NULL DEFAULT 0,
                        studyMinutes INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /** v3：daily_records 增加「今天学了/复习了哪些」的 id 列表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_records ADD COLUMN learnedIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE daily_records ADD COLUMN reviewedIds TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v4：daily_records 增加「今天拼写了哪些 / 错了哪些 / 对了几条」 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_records ADD COLUMN spelledIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE daily_records ADD COLUMN spelledWrongIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE daily_records ADD COLUMN spelledCorrectCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5：collocations 增加「累计答错次数」，用于答错文案里的真实数据 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collocations ADD COLUMN errorCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v6：collocations 增加「所属词本」，支持多个单词本。
         *
         * 已有的词条默认归入「固定搭配词库」这个旧名字，迁移后再由
         * seedBuiltInLibrary 按 assets 里的新词本名做一次归位；
         * 已导入过 JSON 的自定义词条也会落在这个名字下，不会被丢掉。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collocations ADD COLUMN bookName TEXT NOT NULL DEFAULT '固定搭配词库'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "collocation_memory_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // 故意不使用 fallbackToDestructiveMigration()：
                    // 它会在缺少迁移时【静默删除整个数据库】，把用户的学习记录清零且不给任何提示。
                    // 去掉之后，一旦漏写迁移，Room 会直接抛异常崩溃 —— 崩溃是吵闹的、能立刻发现并修，
                    // 而静默丢数据是悄悄的、等用户反馈时已经无法挽回。
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
