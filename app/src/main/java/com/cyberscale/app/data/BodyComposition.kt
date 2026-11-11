package com.cyberscale.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 中国成人 BMI 分级。
 * 依据：《中国成人超重和肥胖症预防控制指南》/ WS/T 428-2013，与 WHO 国际标准不同。
 * colorArgb 用 Long 保存，data 层保持纯 Kotlin，不依赖 Compose。
 */
enum class BmiLevel(
    val label: String,
    val code: String,
    val rangeText: String,
    val colorArgb: Long
) {
    UNKNOWN("待评估", "N/A", "需补全身高", 0xFF00F0FF),
    THIN("偏瘦", "UNDER", "< 18.5", 0xFF38BDF8),
    NORMAL("正常", "NORMAL", "18.5 ~ 23.9", 0xFF39FF88),
    OVERWEIGHT("超重", "OVER", "24.0 ~ 27.9", 0xFFFFB020),
    OBESE("肥胖", "OBESE", "≥ 28.0", 0xFFFF2E97);

    companion object {
        fun fromName(name: String?): BmiLevel =
            values().firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** 计算身体成分所需的档案输入，与 Room Entity 解耦，便于单独校验公式。 */
data class BodyProfile(
    val heightCm: Int,
    /** 0=未设置 1=男 2=女 */
    val gender: Int,
    /** yyyy-MM-dd；空串表示未设置 */
    val birthDate: String
)

/**
 * 一次测量推算出的 8 项指标。
 * 重要：全部是由「体重 + 身高 + 年龄 + 性别」经公式估算，并非秤体实测的阻抗体成分。
 */
data class BodyMetrics(
    val bmi: Double,
    val level: BmiLevel,
    /** 体脂率 % */
    val bodyFatPct: Double,
    /** 基础代谢 kcal/day */
    val bmrKcal: Double,
    /** 理想体重 kg */
    val ibwKg: Double,
    /** 去脂体重 / 瘦体重 kg */
    val lbmKg: Double,
    /** 脂肪重量 kg */
    val fmKg: Double,
    /** 相对体重指数 % = 实际体重 / 理想体重 × 100 */
    val relativeWeightIndex: Double,
    /** 肥胖度 % = (实际体重 − 理想体重) / 理想体重 × 100 */
    val obesityDegreePct: Double
)

/**
 * 公式口径（全部为公开经验公式，常量集中在此，替换算法只改这一处）：
 *  - BMI            = 体重(kg) / 身高(m)²
 *  - 体脂率          = Deurenberg: 1.20×BMI + 0.23×年龄 − 10.8×性别(男1/女0) − 5.4
 *  - BMR            = Mifflin-St Jeor: 10×体重 + 6.25×身高 − 5×年龄 + (男 +5 / 女 −161)
 *  - IBW            = Devine: (男 50.0 / 女 45.5) + 0.9 × (身高cm − 152.4)
 *  - LBM            = 体重 × (1 − 体脂率/100)
 *  - FM             = 体重 − LBM
 *  - 相对体重指数    = 体重 / IBW × 100
 *  - 肥胖度          = (体重 − IBW) / IBW × 100
 */
object BodyComposition {

    const val SOURCE_NOTE =
        "以上指标由体重与档案信息经公开公式估算，非秤体实测值，仅供趋势参考，不作为医学诊断依据。"

    /** 解析 yyyy-MM-dd，失败返回 null（旧数据或用户手工输入错误时不允许崩）。 */
    fun parseBirthDate(value: String): LocalDate? =
        if (value.isBlank()) null else try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            null
        }

    /** 按测量发生的时间点计算周岁，历史记录用记录时间戳而不是"现在"，更准确。 */
    fun ageAt(birthDate: String, atMillis: Long): Int? {
        val birth = parseBirthDate(birthDate) ?: return null
        val at = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (birth.isAfter(at)) return null
        return Period.between(birth, at).years.takeIf { it in 1..120 }
    }

    fun formatBirthDate(year: Int, monthZeroBased: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, day)

    fun bmi(weightKg: Double, heightCm: Int): Double? {
        if (weightKg <= 0.0 || heightCm !in 50..250) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun levelOf(bmi: Double?): BmiLevel = when {
        bmi == null -> BmiLevel.UNKNOWN
        bmi < 18.5 -> BmiLevel.THIN
        bmi < 24.0 -> BmiLevel.NORMAL
        bmi < 28.0 -> BmiLevel.OVERWEIGHT
        else -> BmiLevel.OBESE
    }

    /** 档案不完整（身高/性别/出生日期缺失）时返回 null，绝不编造成分数据。 */
    fun compute(
        weightKg: Double,
        profile: BodyProfile,
        atMillis: Long
    ): BodyMetrics? {
        val bmi = bmi(weightKg, profile.heightCm) ?: return null
        val age = ageAt(profile.birthDate, atMillis) ?: return null
        if (profile.gender !in 1..2 || weightKg !in 2.0..400.0) return null

        val male = profile.gender == 1
        val h = profile.heightCm.toDouble()

        val bodyFatPct = (1.20 * bmi + 0.23 * age - (if (male) 10.8 else 0.0) - 5.4)
            .coerceIn(3.0, 65.0)
        val bmrKcal = 10.0 * weightKg + 6.25 * h - 5.0 * age + if (male) 5.0 else -161.0
        val ibwKg = ((if (male) 50.0 else 45.5) + 0.9 * (h - 152.4))
            .coerceIn(25.0, 200.0)
        val fmKg = weightKg * bodyFatPct / 100.0
        val lbmKg = weightKg - fmKg
        val relativeWeightIndex = weightKg / ibwKg * 100.0
        val obesityDegreePct = (weightKg - ibwKg) / ibwKg * 100.0

        return BodyMetrics(
            bmi = bmi,
            level = levelOf(bmi),
            bodyFatPct = bodyFatPct,
            bmrKcal = bmrKcal,
            ibwKg = ibwKg,
            lbmKg = lbmKg,
            fmKg = fmKg,
            relativeWeightIndex = relativeWeightIndex,
            obesityDegreePct = obesityDegreePct
        )
    }

    fun profileOf(user: UserEntity): BodyProfile = BodyProfile(
        heightCm = user.heightCm,
        gender = user.gender,
        birthDate = user.birthDate
    )
}
