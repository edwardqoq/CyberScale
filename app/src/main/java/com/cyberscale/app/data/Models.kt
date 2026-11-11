package com.cyberscale.app.data

import java.util.Locale

/** 显示单位。数据库里一律存 kg，只在渲染层换算。 */
enum class WeightUnit(val label: String, val factor: Double) {
    KG("kg", 1.0),
    JIN("斤", 2.0),
    LB("lb", 2.2046226)
}

enum class AppTab(val label: String, val code: String) {
    MEASURE("测量", "01"),
    TREND("趋势", "02"),
    HISTORY("档案", "03"),
    USERS("成员", "04")
}

enum class TrendRange(val label: String, val days: Int) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    YEAR("365D", 365)
}

/**
 * 成员编辑表单的提交结果。用 data class 而不是 6 个位置参数，
 * 避免回调与 ViewModel 参数顺序再次错位（v1 曾因此编译失败）。
 */
data class ProfileDraft(
    val name: String,
    val heightCm: Int,
    val goalWeightKg: Double,
    val colorArgb: Int,
    /** 0=未设置 1=男 2=女 */
    val gender: Int,
    /** yyyy-MM-dd，空串=未设置 */
    val birthDate: String
)

/** 由记录列表在内存里算出的统计值，避免写复杂 SQL。 */
data class RecordStats(
    val count: Int = 0,
    val latest: Double? = null,
    val previous: Double? = null,
    val average: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val best30d: Double? = null,
    val delta: Double? = null
)

fun formatWeight(kg: Double, unit: WeightUnit, digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f", kg * unit.factor)

fun formatSigned(kg: Double, unit: WeightUnit): String {
    val v = kg * unit.factor
    return String.format(Locale.US, "%s%.2f", if (v > 0) "+" else "", v)
}
