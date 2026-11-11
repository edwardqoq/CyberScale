package com.cyberscale.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, WeightRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun recordDao(): RecordDao

    companion object {
        private const val DB_NAME = "cyberscale.db"

        /**
         * v1 → v2：只加列，绝不重建表。
         * 用户真机上已有 v1 的测量记录，早期版本的 fallbackToDestructiveMigration() 会直接清库，
         * 这里改为显式迁移，保证覆盖安装后历史数据完整保留。
         *
         *  - users.birthDate：v1 只有 birthYear，用「年份-01-01」回填为完整日期（用户可在档案页修正）。
         *    birthYear 列保留不删（低版本 SQLite 不支持 DROP COLUMN），Entity 里作为兼容字段保留。
         *  - records 的 9 个指标列全部可空：v1 的历史记录没有这些值，等档案补齐后由
         *    ScaleRepository.backfillMetrics() 按当前档案补算。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN birthDate TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE users SET birthDate = CAST(birthYear AS TEXT) || '-01-01' " +
                        "WHERE birthDate = '' AND birthYear >= 1900"
                )

                db.execSQL("ALTER TABLE records ADD COLUMN bmi REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN bmiLevel TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN bodyFatPct REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN bmrKcal REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN ibwKg REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN lbmKg REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN fmKg REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN relativeWeightIndex REAL")
                db.execSQL("ALTER TABLE records ADD COLUMN obesityDegreePct REAL")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                // 不使用 fallbackToDestructiveMigration()：健康数据宁可升级失败报错，也不能静默清空。
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
