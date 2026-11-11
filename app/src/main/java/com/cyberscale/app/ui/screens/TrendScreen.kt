package com.cyberscale.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberscale.app.data.TrendRange
import com.cyberscale.app.data.formatSigned
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.ui.components.ChartPoint
import com.cyberscale.app.ui.components.EmptyStateBlock
import com.cyberscale.app.ui.components.GlowDivider
import com.cyberscale.app.ui.components.NeonCard
import com.cyberscale.app.ui.components.NeonLineChart
import com.cyberscale.app.ui.components.NeonPill
import com.cyberscale.app.ui.components.SectionHeader
import com.cyberscale.app.ui.components.StatTile
import com.cyberscale.app.ui.formatDateTime
import com.cyberscale.app.ui.theme.DataGood
import com.cyberscale.app.ui.theme.DataRise
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.NeonPurple
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import com.cyberscale.app.vm.AppUiState
import java.util.Locale

@Composable
fun TrendScreen(
    state: AppUiState,
    onRange: (TrendRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = state.activeUser?.let { Color(it.colorArgb) } ?: NeonCyan
    val goal = state.activeUser?.goalWeightKg?.takeIf { it > 0.0 }
    val points = state.trend.map { ChartPoint(it.ts, it.weightKg) }
    val totalChange = if (points.size >= 2) points.last().value - points.first().value else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader("体重趋势", "TREND", accent, "${state.trend.size} SAMPLES")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.values().forEach { range ->
                NeonPill(
                    text = range.label,
                    active = range == state.range,
                    onClick = { onRange(range) },
                    accent = accent
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                state.activeUser?.name ?: "--",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        NeonCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
            Row {
                Text("WEIGHT / 体重", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    state.unit.label.uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = Mono
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                state.stats.latest?.let { formatWeight(it, state.unit) } ?: "--.--",
                color = accent,
                fontSize = 30.sp,
                fontFamily = Mono
            )
            Spacer(Modifier.height(6.dp))
            NeonLineChart(
                points = points,
                goal = goal,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(Modifier.height(8.dp))
            GlowDivider(accent)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    state.trend.firstOrNull()?.let { formatDateTime(it.ts) } ?: "--",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
                Spacer(Modifier.weight(1f))
                Text(
                    goal?.let { "TARGET ${formatWeight(it, state.unit)}" } ?: "NO TARGET SET",
                    color = NeonMagenta,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    state.trend.lastOrNull()?.let { formatDateTime(it.ts) } ?: "--",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
        }

        if (state.trend.isEmpty()) {
            NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonPurple) {
                EmptyStateBlock(
                    title = "该时间范围内还没有记录",
                    subtitle = "站上体重秤完成一次测量，或切换到更长的时间范围",
                    accent = NeonPurple,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionHeader("区间统计", "STATISTICS", NeonPurple)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "区间最高",
                value = state.stats.max?.let { formatWeight(it, state.unit) } ?: "--",
                hint = "峰值",
                accent = DataRise,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "区间最低",
                value = state.stats.min?.let { formatWeight(it, state.unit) } ?: "--",
                hint = "谷值",
                accent = DataGood,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "平均体重",
                value = state.stats.average?.let { formatWeight(it, state.unit) } ?: "--",
                hint = "全部记录",
                accent = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "区间变化",
                value = totalChange?.let { formatSigned(it, state.unit) } ?: "--",
                hint = "首末对比",
                accent = if (totalChange == null) TextSecondary
                else if (totalChange <= 0) DataGood else DataRise,
                modifier = Modifier.weight(1f)
            )
        }

        NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonMagenta) {
            Text(
                "GOAL / 目标进度",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = Mono
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (goal == null) "未设置目标体重" else
                    String.format(Locale.US, "已完成 %d%%  ·  目标 %s %s",
                        (state.goalProgress * 100).toInt(),
                        formatWeight(goal, state.unit),
                        state.unit.label),
                color = NeonMagenta,
                fontSize = 14.sp,
                fontFamily = Mono
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "进度按 (起始体重 − 当前) / (起始体重 − 目标) 计算；起始体重由该成员的第一条记录自动确定。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}
