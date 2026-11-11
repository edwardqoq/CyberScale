package com.cyberscale.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun pattern(fmt: String) = SimpleDateFormat(fmt, Locale.getDefault())

fun formatDateTime(ts: Long): String = pattern("MM.dd  HH:mm").format(Date(ts))

fun formatFullDateTime(ts: Long): String = pattern("yyyy.MM.dd  HH:mm").format(Date(ts))

fun formatDay(ts: Long): String = pattern("yyyy.MM.dd").format(Date(ts))

fun formatShortDay(ts: Long): String = pattern("MM/dd").format(Date(ts))

fun formatClock(ts: Long): String = pattern("HH:mm:ss").format(Date(ts))

/** 体成分指标统一格式化，缺值显示 -- 而不是 0，避免被误读为真实测量值。 */
fun formatMetric(value: Double?, digits: Int = 1): String =
    value?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "--"

/** 带正负号的指标（肥胖度、相对体重偏差）。 */
fun formatSignedMetric(value: Double?, digits: Int = 1): String =
    value?.let { String.format(Locale.US, "%+.${digits}f", it) } ?: "--"

fun formatBirthDateCn(iso: String): String =
    if (iso.isBlank()) "未设置" else iso.replace('-', '.')
