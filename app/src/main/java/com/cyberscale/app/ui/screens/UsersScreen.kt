package com.cyberscale.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberscale.app.data.BodyComposition
import com.cyberscale.app.data.ProfileDraft
import com.cyberscale.app.data.UserEntity
import com.cyberscale.app.data.WeightUnit
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.data.hasMetrics
import com.cyberscale.app.ui.components.GlowDivider
import com.cyberscale.app.ui.components.NeonActionButton
import com.cyberscale.app.ui.components.NeonCard
import com.cyberscale.app.ui.components.NeonPill
import com.cyberscale.app.ui.components.NeonTextField
import com.cyberscale.app.ui.components.SectionHeader
import com.cyberscale.app.ui.components.UserAvatar
import com.cyberscale.app.ui.formatBirthDateCn
import com.cyberscale.app.ui.theme.CardStroke
import com.cyberscale.app.ui.theme.CyberSurface
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonAmber
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonGreen
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.NeonPurple
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import com.cyberscale.app.ui.theme.UserColors
import com.cyberscale.app.vm.AppUiState
import java.util.Calendar

@Composable
fun UsersScreen(
    state: AppUiState,
    onAdd: (ProfileDraft) -> Unit,
    onUpdate: (UserEntity) -> Unit,
    onDelete: (UserEntity) -> Unit,
    onActivate: (Long) -> Unit,
    onUnit: (WeightUnit) -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<UserEntity?>(null) }

    if (editorOpen) {
        UserEditorDialog(
            initial = editing,
            onDismiss = { editorOpen = false; editing = null },
            onConfirm = { draft ->
                val current = editing
                if (current == null) {
                    onAdd(draft)
                } else {
                    onUpdate(
                        current.copy(
                            name = draft.name,
                            heightCm = draft.heightCm,
                            goalWeightKg = draft.goalWeightKg,
                            colorArgb = draft.colorArgb,
                            gender = draft.gender,
                            birthDate = draft.birthDate
                        )
                    )
                }
                editorOpen = false
                editing = null
            }
        )
    }

    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CyberSurface,
            title = { Text("删除成员「${user.name}」？", color = TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "该成员的全部体重记录会一并删除，且不可恢复。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(user)
                    pendingDelete = null
                }) { Text("删除", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("成员档案", "PROFILES", NeonPurple, modifier = Modifier.weight(1f))
            NeonActionButton(text = "+ NEW", onClick = {
                editing = null
                editorOpen = true
            }, accent = NeonPurple)
        }

        state.users.forEach { user ->
            UserCard(
                user = user,
                active = user.id == state.activeUser?.id,
                unit = state.unit,
                onActivate = { onActivate(user.id) },
                onEdit = {
                    editing = user
                    editorOpen = true
                },
                onDelete = { pendingDelete = user }
            )
        }

        // ---------- 体成分估算说明 + 补全引导 ----------
        val active = state.activeUser
        val profileIncomplete = active != null &&
            (active.gender !in 1..2 || active.birthDate.isBlank())
        val missing = state.records.count { !it.hasMetrics }
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonGreen) {
            SectionHeader("体成分估算", "BODY COMP", NeonGreen, "FORMULA BASED")
            Spacer(Modifier.height(10.dp))
            Text(
                "每次测量完成，会按当前成员的性别 / 身高 / 出生日期计算并保存 8 项指标：" +
                    "BMI、体脂率、基础代谢 BMR、理想体重 IBW、瘦体重 LBM、脂肪重量 FM、" +
                    "相对体重指数、肥胖度。在「档案」页点开任意记录即可查看。",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            GlowDivider(NeonGreen)
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ 这些是由公开经验公式推算的估算值，不是秤体实测。" +
                    "本秤广播帧只提供体重，不提供身体成分；估算结果仅供趋势参考，不作为医学诊断依据。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
            if (profileIncomplete) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "当前测量对象「${active?.name}」尚未补全性别或出生日期，暂时无法估算。" +
                        "点上方成员卡片的「编辑」补齐后，历史记录会自动补算。",
                    color = NeonAmber,
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
            if (missing > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$missing 条历史记录尚无体成分数据",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    NeonActionButton(
                        text = "按当前档案补算",
                        onClick = onBackfill,
                        accent = NeonGreen,
                        filled = false
                    )
                }
            }
        }

        // ---------- 单位设置 ----------
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonCyan) {
            SectionHeader("显示单位", "UNIT", NeonCyan, "LOCAL SETTING")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.values().forEach { unit ->
                    NeonPill(
                        text = unit.label,
                        active = unit == state.unit,
                        onClick = { onUnit(unit) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "数据库始终以 kg 存储，切换单位只影响界面显示，不会改动历史数值。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }

        // ---------- 设备与协议 ----------
        NeonCard(modifier = Modifier.fillMaxWidth(), accent = NeonMagenta) {
            SectionHeader("设备与协议", "DEVICE", NeonMagenta, "BROADCAST ONLY")
            Spacer(Modifier.height(12.dp))
            DeviceRow("秤型", "KONKA / OKOK Chipsea 2.0")
            DeviceRow("连接方式", "仅 BLE 广播扫描（不连接 / 不配对）")
            DeviceRow("公司 ID", "0x20CA")
            DeviceRow("有效字段", "体重 · 序号 · 单位 · 阻抗 R1 · MAC")
            DeviceRow("最近 MAC", state.ble.lastMac)
            DeviceRow("最近信号", "${state.ble.lastRssi} dBm")
            DeviceRow("最近序号", state.ble.lastSeq.takeIf { it >= 0 }?.toString() ?: "--")
            Spacer(Modifier.height(10.dp))
            GlowDivider(NeonMagenta)
            Spacer(Modifier.height(8.dp))
            Text(
                "所有数据保存在本机 SQLite（cyberscale.db），无账号、无云同步、无网络请求。删除 App 即删除全部数据。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun UserCard(
    user: UserEntity,
    active: Boolean,
    unit: WeightUnit,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = Color(user.colorArgb)
    val age = remember(user.birthDate) {
        BodyComposition.ageAt(user.birthDate, System.currentTimeMillis())
    }
    val genderText = when (user.gender) {
        1 -> "男"
        2 -> "女"
        else -> "性别未设置"
    }
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = user.name, color = color, active = active)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "$genderText  ·  ${age?.let { "${it} 岁" } ?: "年龄待补"}  ·  ${user.heightCm} cm",
                    color = if (active) color else TextDim,
                    fontSize = 10.sp,
                    fontFamily = Mono
                )
            }
            if (active) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.copy(alpha = 0.16f))
                        .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("ACTIVE", color = color, fontSize = 9.sp, fontFamily = Mono)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text(
                "目标  ${if (user.goalWeightKg > 0) formatWeight(user.goalWeightKg, unit) + " " + unit.label else "未设置"}",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = Mono
            )
            Spacer(Modifier.weight(1f))
            Text(
                "起始  ${if (user.startWeightKg > 0) formatWeight(user.startWeightKg, unit) else "待首次测量"}",
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = Mono
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "出生  ${formatBirthDateCn(user.birthDate)}",
            color = TextDim,
            fontSize = 10.sp,
            fontFamily = Mono
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonActionButton(
                text = if (active) "CURRENT" else "设为当前",
                onClick = onActivate,
                accent = color,
                filled = active
            )
            NeonActionButton(text = "编辑", onClick = onEdit, accent = NeonCyan, filled = false)
            Spacer(Modifier.weight(1f))
            NeonActionButton(text = "删除", onClick = onDelete, accent = NeonMagenta, filled = false)
        }
    }
}

@Composable
private fun DeviceRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = NeonCyan,
            fontSize = 10.sp,
            fontFamily = Mono,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UserEditorDialog(
    initial: UserEntity?,
    onDismiss: () -> Unit,
    onConfirm: (ProfileDraft) -> Unit
) {
    val context = LocalContext.current
    val key = initial?.id ?: -1L
    var name by remember(key) { mutableStateOf(initial?.name ?: "") }
    var height by remember(key) { mutableStateOf(initial?.heightCm?.toString() ?: "170") }
    var goal by remember(key) {
        mutableStateOf(initial?.goalWeightKg?.takeIf { it > 0.0 }?.toString().orEmpty())
    }
    var gender by remember(key) { mutableStateOf(initial?.gender ?: 0) }
    var birthDate by remember(key) { mutableStateOf(initial?.birthDate ?: "") }
    var colorIndex by remember(key) {
        mutableStateOf(
            UserColors.indexOfFirst { it.toArgb() == initial?.colorArgb }.coerceAtLeast(0)
        )
    }
    var dateError by remember(key) { mutableStateOf(false) }

    val age = remember(birthDate) {
        BodyComposition.ageAt(birthDate, System.currentTimeMillis())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = {
            Text(
                if (initial == null) "新增成员" else "编辑「${initial.name}」",
                color = TextPrimary,
                fontSize = 15.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("昵称", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                NeonTextField(value = name, onValueChange = { name = it }, placeholder = "例如：小林")

                Text("性别（用于体成分公式）", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "未设置", 1 to "男", 2 to "女").forEach { (value, label) ->
                        NeonPill(
                            text = label,
                            active = gender == value,
                            onClick = { gender = value },
                            accent = NeonCyan
                        )
                    }
                }

                Text("身高 (cm)", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                NeonTextField(
                    value = height,
                    onValueChange = { height = it },
                    placeholder = "170",
                    numeric = true
                )

                Text("出生年月日（自动计算年龄）", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                NeonActionButton(
                    text = if (birthDate.isBlank()) "选择出生日期" else formatBirthDateCn(birthDate),
                    onClick = {
                        val cal = Calendar.getInstance()
                        val parsed = BodyComposition.parseBirthDate(birthDate)
                        if (parsed != null) {
                            cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
                        } else {
                            cal.set(1995, 0, 1)
                        }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                birthDate = BodyComposition.formatBirthDate(y, m, d)
                                dateError = false
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                            setOnCancelListener { dateError = false }
                        }.show()
                    },
                    accent = if (birthDate.isBlank()) NeonAmber else NeonCyan,
                    filled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (age != null) "年龄  $age 岁 · 按测量当天自动计算"
                        else "未设置 · 补全后才能估算体脂率 / BMR / IBW",
                        color = if (age != null) NeonGreen else TextDim,
                        fontSize = 10.sp,
                        fontFamily = Mono
                    )
                }
                if (dateError) {
                    Text("日期格式无效，请重新选择", color = NeonMagenta, fontSize = 10.sp)
                }

                Text("目标体重 (kg，可留空)", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                NeonTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    placeholder = "60.0",
                    numeric = true
                )

                Text("主题色", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserColors.forEachIndexed { index, c ->
                        val selected = index == colorIndex
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(c.copy(alpha = if (selected) 1f else 0.35f))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.White else CardStroke,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { colorIndex = index }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = height.toIntOrNull()?.coerceIn(50, 250) ?: 170
                val g = goal.toDoubleOrNull()?.coerceIn(20.0, 300.0) ?: 0.0
                val validDate = birthDate.isBlank() ||
                    BodyComposition.parseBirthDate(birthDate) != null
                dateError = !validDate
                if (!validDate) return@TextButton
                onConfirm(
                    ProfileDraft(
                        name = name.ifBlank { "新成员" },
                        heightCm = h,
                        goalWeightKg = g,
                        colorArgb = UserColors[colorIndex].toArgb(),
                        gender = gender,
                        birthDate = birthDate
                    )
                )
            }) { Text("保存", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}
