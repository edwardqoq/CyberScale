package com.cyberscale.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cyberscale.app.data.formatSigned
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.ui.formatMetric
import com.cyberscale.app.ui.components.GlowDivider
import com.cyberscale.app.ui.components.HexStream
import com.cyberscale.app.ui.components.LedDot
import com.cyberscale.app.ui.components.NeonActionButton
import com.cyberscale.app.ui.components.NeonCard
import com.cyberscale.app.ui.components.NeonRingGauge
import com.cyberscale.app.ui.components.ScanPulse
import com.cyberscale.app.ui.components.SectionHeader
import com.cyberscale.app.ui.components.SignalBars
import com.cyberscale.app.ui.components.StatTile
import com.cyberscale.app.ui.components.UserAvatar
import com.cyberscale.app.ui.theme.CardStroke
import com.cyberscale.app.ui.theme.CyberSurface2
import com.cyberscale.app.ui.theme.DataGood
import com.cyberscale.app.ui.theme.DataRise
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonAmber
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import com.cyberscale.app.vm.AppUiState
import java.util.Locale

@Composable
fun MeasureScreen(
    state: AppUiState,
    onSelectUser: (Long) -> Unit,
    onToggleScan: () -> Unit,
    onOpenUsers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = state.activeUser?.let { Color(it.colorArgb) } ?: NeonCyan
    val shownWeight = state.liveWeight ?: state.stats.latest
    val frame = state.liveFrame
    val isLocked = frame?.isFinal == true && frame.isValidWeight

    // BMI 国标分级：实时体重优先，无实时帧时用最近一次已保存记录。
    // 等级色同时驱动中央仪表的主弧，让胖瘦状态一眼可辨。
    val bmi = shownWeight?.let { BodyComposition.bmi(it, state.activeUser?.heightCm ?: 0) }
    val level = BodyComposition.levelOf(bmi)
    val levelColor = Color(level.colorArgb)
    val gaugeAccent = if (level == BmiLevel.UNKNOWN) accent else levelColor
    val profileIncomplete = state.activeUser?.let {
        it.gender !in 1..2 || it.birthDate.isBlank()
    } ?: true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 测量对象切换 ----------
        SectionHeader("测量对象", "SUBJECT", accent, "${state.users.size} PROFILES")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.users.forEach { user ->
                UserChip(
                    user = user,
                    active = user.id == state.activeUser?.id,
                    onClick = { onSelectUser(user.id) }
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberSurface2)
                    .border(1.dp, CardStroke, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenUsers),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = NeonCyan, fontSize = 22.sp, fontFamily = Mono)
            }
        }

        // ---------- 主仪表 ----------
        Box(
            Modifier
                .fillMaxWidth()
                .height(322.dp),
            contentAlignment = Alignment.Center
        ) {
            ScanPulse(
                active = state.ble.scanning,
                accent = accent,
                modifier = Modifier.size(320.dp)
            )
            NeonRingGauge(
                weight = shownWeight,
                unit = state.unit,
                progress = state.goalProgress,
                scanning = state.ble.scanning,
                locked = isLocked,
                accent = gaugeAccent,
                modifier = Modifier.size(288.dp)
            )
        }

        // ---------- BMI 国标分级（等级色与仪表主弧一致） ----------
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = levelColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "BMI 身体质量指数 · 国标分级",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = Mono
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatMetric(bmi, 1),
                            color = levelColor,
                            fontSize = 30.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(levelColor.copy(alpha = 0.15f))
                                .border(1.dp, levelColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                level.label,
                                color = levelColor,
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    level.rangeText,
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
            Spacer(Modifier.height(10.dp))
            BmiScaleBar(level = level, color = levelColor)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    level == BmiLevel.UNKNOWN -> "在「成员」页补全身高后即可显示 BMI 分级，圆环颜色会随等级变化。"
                    profileIncomplete -> "当前等级已按身高计算；补全性别与出生日期后，档案页可查看体脂率、BMR 等 8 项估算指标。"
                    else -> "测量完成后，8 项身体成分估算值会随记录保存，可在「档案」页点开查看。"
                },
                color = if (profileIncomplete && level != BmiLevel.UNKNOWN) NeonAmber else TextDim,
                fontSize = 9.sp,
                lineHeight = 15.sp
            )
        }

        // ---------- 实时链路状态 ----------
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(active = state.ble.scanning, color = if (isLocked) DataGood else NeonCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    state.ble.message,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = Mono
                )
                Spacer(Modifier.weight(1f))
                SignalBars(rssi = state.ble.lastRssi)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.ble.lastRssi} dBm",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
            Spacer(Modifier.height(10.dp))
            GlowDivider(accent)
            Spacer(Modifier.height(8.dp))
            HexStream(hex = state.ble.lastHex, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MAC ${state.ble.lastMac}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = Mono
                )
                Spacer(Modifier.weight(1f))
                NeonActionButton(
                    text = if (state.ble.scanning) "PAUSE" else "RESUME",
                    onClick = onToggleScan,
                    accent = NeonMagenta,
                    filled = false
                )
            }
        }

        // ---------- 关键指标 ----------
        SectionHeader("关键指标", "METRICS", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val delta = state.stats.delta
            StatTile(
                label = "距上次",
                value = if (delta == null) "--" else formatSigned(delta, state.unit),
                hint = if (delta == null) "暂无对比" else if (delta <= 0) "向目标靠近" else "略有回升",
                accent = if (delta == null) TextSecondary else if (delta <= 0) DataGood else DataRise,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "30 天最佳",
                value = state.stats.best30d?.let { formatWeight(it, state.unit) } ?: "--",
                hint = "区间最低值",
                accent = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "目标进度",
                value = if (state.goalProgress > 0f)
                    String.format(Locale.US, "%d%%", (state.goalProgress * 100).toInt())
                else "--",
                hint = state.activeUser?.goalWeightKg
                    ?.takeIf { it > 0 }
                    ?.let { "目标 ${formatWeight(it, state.unit)} ${state.unit.label}" }
                    ?: "未设置目标",
                accent = NeonMagenta,
                modifier = Modifier.weight(1f)
            )
        }

        // ---------- 协议诊断 ----------
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonMagenta) {
            SectionHeader("广播遥测", "TELEMETRY", NeonMagenta, "OKOK / CHIPSEA 2.0")
            Spacer(Modifier.height(12.dp))
            TelemetryRow("SEQ / 测量序号", frame?.seq?.toString() ?: "--")
            TelemetryRow("PROPERTIES", frame?.let { "0x%02X".format(it.scaleProperty) } ?: "--")
            TelemetryRow("UNIT / 秤面单位", frame?.unitLabel ?: "--")
            TelemetryRow("R1 / 阻抗原始值", frame?.let { String.format(Locale.US, "%.3f", it.impedance) } ?: "--")
            TelemetryRow("VERSION / PRODUCT", frame?.let { "0x%02X / 0x%04X".format(it.version, it.productId) } ?: "--")
            Spacer(Modifier.height(10.dp))
            GlowDivider(NeonMagenta)
            Spacer(Modifier.height(8.dp))
            Text(
                "本秤广播帧只提供体重与测量序号，不含身体成分。测量页的 BMI 分级与档案页的" +
                    "体脂率 / BMR / IBW / LBM / FM 等，均由体重结合成员档案经公开公式估算，" +
                    "并非秤体实测值，仅供趋势参考，不作为医学诊断依据。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun UserChip(user: UserEntity, active: Boolean, onClick: () -> Unit) {
    val color = Color(user.colorArgb)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) color.copy(alpha = 0.13f) else CyberSurface2)
            .border(
                width = 1.dp,
                color = if (active) color.copy(alpha = 0.7f) else CardStroke,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = user.name, color = color, active = active)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(user.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                if (active) "● ACTIVE" else "TAP TO SWITCH",
                color = if (active) color else TextDim,
                fontSize = 8.sp,
                fontFamily = Mono
            )
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
        Spacer(Modifier.weight(1f))
        Text(value, color = NeonCyan, fontSize = 10.sp, fontFamily = Mono)
    }
}

/** 国标四级刻度条，当前等级段用等级色点亮。 */
@Composable
private fun BmiScaleBar(level: BmiLevel, color: Color) {
    val levels = listOf(BmiLevel.THIN, BmiLevel.NORMAL, BmiLevel.OVERWEIGHT, BmiLevel.OBESE)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            levels.forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (item == level) color else CardStroke)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth()) {
            levels.forEach { item ->
                Text(
                    item.label,
                    color = if (item == level) color else TextDim,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
