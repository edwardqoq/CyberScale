package com.cyberscale.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 家庭成员档案。一条体重记录必须归属某一个用户。 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** 该成员的霓虹主色（ARGB Int），用于头像芯片与图表着色。 */
    val colorArgb: Int,
    val heightCm: Int,
    /** v1 遗留列：只存年份。v2 起改用 birthDate，此字段仅为兼容旧数据库而保留。 */
    val birthYear: Int = 0,
    /** 0=未设置 1=男 2=女 */
    val gender: Int,
    val goalWeightKg: Double,
    /** 起始体重：目标进度用 (start - current) / (start - goal) 计算，0 表示由首条记录自动填充。 */
    val startWeightKg: Double,
    val createdAt: Long,
    val isActive: Boolean = false,
    /** 出生年月日 yyyy-MM-dd；空串=未设置。年龄一律由它 + 测量时间戳实时推算。 */
    // defaultValue 必须与 MIGRATION_1_2 的 ALTER TABLE 完全一致，否则 Room 运行时 schema 校验会失败。
    @ColumnInfo(defaultValue = "''")
    val birthDate: String = ""
)

@Entity(
    tableName = "records",
    indices = [Index("userId"), Index("ts")],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WeightRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val ts: Long,
    val weightKg: Double,
    /** 广播帧里的测量序号，便于排查重复/丢帧。 */
    val seq: Int,
    val rssi: Int,
    val impedance: Float,
    val rawHex: String,
    val mac: String,
    val note: String = "",
    // ---- v2 新增：由体重 + 档案经公式估算的指标（全部为 nullable，旧记录没有这些值）----
    val bmi: Double? = null,
    val bmiLevel: String? = null,
    /** 体脂率 % */
    val bodyFatPct: Double? = null,
    /** 基础代谢 kcal/day */
    val bmrKcal: Double? = null,
    /** 理想体重 kg */
    val ibwKg: Double? = null,
    /** 去脂体重 kg */
    val lbmKg: Double? = null,
    /** 脂肪重量 kg */
    val fmKg: Double? = null,
    /** 相对体重指数 % */
    val relativeWeightIndex: Double? = null,
    /** 肥胖度 % */
    val obesityDegreePct: Double? = null
)

/** 是否已完成体成分估算（BMI 存在即代表全套已算）。放在类外，避免 Room 误判为列。 */
val WeightRecordEntity.hasMetrics: Boolean get() = bmi != null
