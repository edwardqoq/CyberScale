package com.cyberscale.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberscale.app.data.BmiLevel
import com.cyberscale.app.data.BodyComposition
import com.cyberscale.app.data.UserEntity
import com.cyberscale.app.data.WeightRecordEntity
import com.cyberscale.app.data.WeightUnit
import com.cyberscale.app.data.formatSigned
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.data.hasMetrics
import com.cyberscale.app.ui.components.EmptyStateBlock
import com.cyberscale.app.ui.components.GlowDivider
import com.cyberscale.app.ui.components.NeonActionButton
import com.cyberscale.app.ui.components.NeonCard
import com.cyberscale.app.ui.components.SectionHeader
import com.cyberscale.app.ui.components.StatTile
import com.cyberscale.app.ui.formatFullDateTime
import com.cyberscale.app.ui.formatMetric
import com.cyberscale.app.ui.formatSignedMetric
import com.cyberscale.app.ui.theme.CardStroke
import com.cyberscale.app.ui.theme.CyberSurface
import com.cyberscale.app.ui.theme.CyberSurface2
import com.cyberscale.app.ui.theme.DataGood
import com.cyberscale.app.ui.theme.DataRise
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonAmber
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonGreen
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import com.cyberscale.app.vm.AppUiState
import com.cyberscale.app.ui.formatDateTime

@Composable
fun HistoryScreen(
    state: AppUiState,
    onDeleteRecord: (WeightRecordEntity) -> Unit,
    onClear: () -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = state.activeUser?.let { Color(it.colorArgb) } ?: NeonCyan
    var pendingDelete by remember { mutableStateOf<WeightRecordEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WeightRecordEntity?>(null) }

    detail?.let { record ->
        RecordDetailDialog(
            record = record,
            user = state.activeUser,
            unit = state.unit,
            onBackfill = onBackfill,
            onDismiss = { detail = null }
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CyberSurface,
            title = { Text("删除这条记录？", color = TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "${formatDateTime(record.ts)}  ·  ${formatWeight(record.weightKg, state.unit)} ${state.unit.label}\n删除后不可恢复。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(record)
                    pendingDelete = null
                    detail = null
                }) { Text("删除", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CyberSurface,
            title = { Text("清空当前成员的全部记录？", color = TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "只会清空「${state.activeUser?.name ?: "当前成员"}」的数据，其他成员不受影响。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearDialog = false
                    detail = null
                }) { Text("确认清空", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("测量档案", "ARCHIVE", accent, "${state.records.size} RECORDS")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "测量次数",
                        value = state.stats.count.toString(),
                        hint = "全部时间",
                        accent = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "最新体重",
                        value = state.stats.latest?.let { formatWeight(it, state.unit) } ?: "--",
                        hint = "最近一次",
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "平均体重",
                        value = state.stats.average?.let { formatWeight(it, state.unit) } ?: "--",
                        hint = "全部记录",
                        accent = NeonMagenta,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.activeUser?.name ?: "--"} 的记录流 · 点开任意一条查看体成分",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    NeonActionButton(
                        text = "CLEAR ALL",
                        onClick = { showClearDialog = true },
                        accent = NeonMagenta,
                        filled = false
                    )
                }
            }
        }

        if (state.records.isEmpty()) {
            item {
                NeonCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
                    EmptyStateBlock(
                        title = "暂无测量记录",
                        subtitle = "站上体重秤，锁定数值后会自动归档到当前成员",
                        accent = accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        itemsIndexed(state.records, key = { _, record -> record.id }) { index, record ->
            val older = state.records.getOrNull(index + 1)
            val delta = older?.let { record.weightKg - it.weightKg }
            RecordRow(
                record = record,
                delta = delta,
                unit = state.unit,
                accent = accent,
                onOpen = { detail = record },
                onDelete = { pendingDelete = record }
            )
        }
    }
}

@Composable
private fun RecordRow(
    record: WeightRecordEntity,
    delta: Double?,
    unit: WeightUnit,
    accent: Color,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var visible by remember(record.id) { mutableStateOf(false) }
    LaunchedEffect(record.id) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 3 })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CyberSurface.copy(alpha = 0.78f))
                .border(1.dp, CardStroke, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDateTime(record.ts),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (record.hasMetrics) {
                        val level = BmiLevel.fromName(record.bmiLevel)
                        "BMI ${formatMetric(record.bmi)} · ${level.label}"
                    } else {
                        "SEQ ${record.seq}  ·  ${record.rssi} dBm  ·  ${record.mac}"
                    },
                    color = if (record.hasMetrics) Color(BmiLevel.fromName(record.bmiLevel).colorArgb)
                    else TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${formatWeight(record.weightKg, unit)} ${unit.label}",
                    color = accent,
                    fontSize = 16.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    delta?.let { formatSigned(it, unit) } ?: "首次记录",
                    color = when {
                        delta == null -> TextDim
                        delta <= 0 -> DataGood
                        else -> DataRise
                    },
                    fontSize = 10.sp,
                    fontFamily = Mono
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = TextDim, fontSize = 18.sp, fontFamily = Mono)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberSurface2)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = NeonMagenta, fontSize = 12.sp)
            }
        }
    }
}

/** 点开单条记录查看 8 项估算指标。 */
@Composable
private fun RecordDetailDialog(
    record: WeightRecordEntity,
    user: UserEntity?,
    unit: WeightUnit,
    onBackfill: () -> Unit,
    onDismiss: () -> Unit
) {
    val level = BmiLevel.fromName(record.bmiLevel)
    val levelColor = Color(level.colorArgb)
    val age = user?.let { BodyComposition.ageAt(it.birthDate, record.ts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = {
            Column {
                Text("测量详情 / RECORD", color = TextPrimary, fontSize = 15.sp)
                Text(
                    formatFullDateTime(record.ts),
                    color = TextDim,
                    fontSize = 10.sp,
                    fontFamily = Mono
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 体重 + BMI 等级
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("体重", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                        Text(
                            "${formatWeight(record.weightKg, unit)} ${unit.label}",
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (record.hasMetrics) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(levelColor.copy(alpha = 0.14f))
                                .border(1.dp, levelColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "BMI ${formatMetric(record.bmi)}",
                                    color = levelColor,
                                    fontSize = 15.sp,
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${level.label} · 国标 ${level.rangeText}",
                                    color = levelColor.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontFamily = Mono
                                )
                            }
                        }
                    }
                }

                GlowDivider(levelColor)

                if (record.hasMetrics) {
                    val rows = listOf(
                        "体脂率  BF%" to "${formatMetric(record.bodyFatPct)} %",
                        "基础代谢  BMR" to "${formatMetric(record.bmrKcal, 0)} kcal",
                        "理想体重  IBW" to "${formatMetric(record.ibwKg)} kg",
                        "瘦体重  LBM" to "${formatMetric(record.lbmKg)} kg",
                        "脂肪重量  FM" to "${formatMetric(record.fmKg)} kg",
                        "相对体重指数" to "${formatMetric(record.relativeWeightIndex)} %",
                        "肥胖度" to "${formatSignedMetric(record.obesityDegreePct)} %"
                    )
                    rows.forEach { (label, value) -> MetricRow(label, value) }

                    Spacer(Modifier.height(4.dp))
                    GlowDivider(NeonCyan)
                    Spacer(Modifier.height(2.dp))
                    val genderText = when (user?.gender) {
                        1 -> "男"
                        2 -> "女"
                        else -> "未设置"
                    }
                    Text(
                        "计算档案：${user?.heightCm ?: "--"} cm · $genderText · " +
                            "${age?.let { "${it} 岁" } ?: "年龄 --"}（按测量当天）",
                        color = TextDim,
                        fontSize = 9.sp,
                        fontFamily = Mono
                    )
                    Text(
                        BodyComposition.SOURCE_NOTE,
                        color = NeonAmber.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        lineHeight = 15.sp
                    )
                } else {
                    NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonAmber) {
                        Text(
                            "这条记录生成时档案尚未完善，没有体成分数据。",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "请先在「成员」页补全性别与出生年月日，再回来补算。" +
                                "补算使用当前档案的身高与性别，年龄按本条记录的时间计算。",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        NeonActionButton(
                            text = "按当前档案补算",
                            onClick = onBackfill,
                            accent = NeonGreen,
                            filled = false
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "SEQ ${record.seq} · ${record.rssi} dBm · MAC ${record.mac}",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = NeonCyan) }
        }
    )
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold
        )
    }
}
