package com.cyberscale.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberscale.app.ble.requiredBlePermissions
import com.cyberscale.app.data.AppTab
import com.cyberscale.app.data.UserEntity
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.ui.components.CyberGridBackground
import com.cyberscale.app.ui.components.LedDot
import com.cyberscale.app.ui.components.NeonActionButton
import com.cyberscale.app.ui.components.UserAvatar
import com.cyberscale.app.ui.screens.HistoryScreen
import com.cyberscale.app.ui.screens.MeasureScreen
import com.cyberscale.app.ui.screens.TrendScreen
import com.cyberscale.app.ui.screens.UsersScreen
import com.cyberscale.app.ui.theme.CardStroke
import com.cyberscale.app.ui.theme.CyberBg
import com.cyberscale.app.ui.theme.CyberSurface
import com.cyberscale.app.ui.theme.CyberSurface2
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonGreen
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import com.cyberscale.app.vm.AppUiState
import com.cyberscale.app.vm.SaveEvent
import com.cyberscale.app.vm.ScaleViewModel

@Composable
fun CyberScaleRoot(vm: ScaleViewModel) {
    PermissionGate {
        val state by vm.uiState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        var reassignEvent by remember { mutableStateOf<SaveEvent?>(null) }

        // 一次成功落库 → Snackbar 提示，并允许把记录改挂到别的成员
        LaunchedEffect(Unit) {
            vm.saveEvent.collect { event ->
                val unit = vm.uiState.value.unit
                val result = snackbarHostState.showSnackbar(
                    message = "已记录 ${formatWeight(event.weightKg, unit)} ${unit.label} → ${event.userName}",
                    actionLabel = "改归属",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    reassignEvent = event
                }
            }
        }

        reassignEvent?.let { event ->
            ReassignDialog(
                users = state.users,
                event = event,
                unit = state.unit,
                onPick = { user ->
                    vm.reassign(event.recordId, user.id)
                    reassignEvent = null
                },
                onDismiss = { reassignEvent = null }
            )
        }

        Box(Modifier.fillMaxSize().background(CyberBg)) {
            CyberGridBackground(Modifier.matchParentSize())

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = CyberSurface2,
                            contentColor = TextPrimary
                        )
                    }
                },
                topBar = { CyberTopBar(state) },
                bottomBar = { CyberBottomNav(state.tab) { vm.selectTab(it) } }
            ) { padding ->
                AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(260)) togetherWith
                                fadeOut(animationSpec = tween(160))
                    },
                    label = "tabSwitch",
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) { tab ->
                    when (tab) {
                        AppTab.MEASURE -> MeasureScreen(
                            state = state,
                            onSelectUser = { vm.activateUser(it) },
                            onToggleScan = { vm.toggleScan() },
                            onOpenUsers = { vm.selectTab(AppTab.USERS) }
                        )
                        AppTab.TREND -> TrendScreen(
                            state = state,
                            onRange = { vm.selectRange(it) }
                        )
                        AppTab.HISTORY -> HistoryScreen(
                            state = state,
                            onDeleteRecord = { vm.deleteRecord(it) },
                            onClear = { vm.clearRecords() },
                            onBackfill = { vm.backfill() }
                        )
                        AppTab.USERS -> UsersScreen(
                            state = state,
                            onAdd = { draft -> vm.addUser(draft) },
                            onUpdate = { vm.updateUser(it) },
                            onDelete = { vm.deleteUser(it) },
                            onActivate = { vm.activateUser(it) },
                            onUnit = { vm.setUnit(it) },
                            onBackfill = { vm.backfill() }
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- 权限闸门 ---------------- */

private fun hasBlePermissions(context: Context): Boolean =
    requiredBlePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasBlePermissions(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it } || hasBlePermissions(context)
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(requiredBlePermissions())
    }

    if (granted) {
        content()
    } else {
        PermissionScreen(onRequest = { launcher.launch(requiredBlePermissions()) })
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CyberBg)) {
        CyberGridBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NEON//SCALE", color = NeonCyan, fontSize = 26.sp, fontFamily = Mono, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("BROADCAST TELEMETRY TERMINAL", color = NeonMagenta, fontSize = 10.sp, fontFamily = Mono)
            Spacer(Modifier.height(30.dp))
            Text(
                "需要蓝牙扫描权限才能接收体重秤的广播数据。",
                color = TextPrimary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本 App 只扫描附近的 BLE 广播，不建立连接、不配对、不获取位置、不联网。",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "部分国产 ROM 还需要同时开启系统「定位服务」开关，BLE 扫描才能返回结果。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(26.dp))
            NeonActionButton(text = "授予权限 / GRANT", onClick = onRequest)
        }
    }
}

/* ---------------- 顶栏 / 底栏 ---------------- */

@Composable
private fun CyberTopBar(state: AppUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CyberBg.copy(alpha = 0.88f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NEON", color = NeonCyan, fontSize = 17.sp, fontFamily = Mono, fontWeight = FontWeight.Black)
            Text("//", color = NeonMagenta, fontSize = 17.sp, fontFamily = Mono, fontWeight = FontWeight.Black)
            Text("SCALE", color = TextPrimary, fontSize = 17.sp, fontFamily = Mono, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text("v1.1", color = TextDim, fontSize = 9.sp, fontFamily = Mono)
            Spacer(Modifier.weight(1f))
            LedDot(active = state.ble.scanning, color = if (state.ble.scanning) NeonGreen else TextDim)
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.ble.scanning) "BLE ON" else "BLE OFF",
                color = if (state.ble.scanning) NeonGreen else TextDim,
                fontSize = 9.sp,
                fontFamily = Mono
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color.Transparent, NeonCyan.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun CyberBottomNav(current: AppTab, onSelect: (AppTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CyberBg.copy(alpha = 0.94f))
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CardStroke)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { tab ->
                val active = tab == current
                val color = if (active) NeonCyan else TextDim
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) NeonCyan.copy(alpha = 0.09f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(
                                if (active) NeonCyan else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    TabIcon(tab, color)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        color = if (active) NeonCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(tab.code, color = TextDim, fontSize = 8.sp, fontFamily = Mono)
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: AppTab, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val stroke = 1.6.dp.toPx()
        when (tab) {
            AppTab.MEASURE -> {
                drawCircle(color, s * 0.36f, style = Stroke(stroke))
                drawCircle(color, s * 0.09f)
                drawLine(
                    color,
                    Offset(s * 0.5f, s * 0.5f),
                    Offset(s * 0.74f, s * 0.29f),
                    stroke,
                    cap = StrokeCap.Round
                )
            }
            AppTab.TREND -> {
                val p = Path().apply {
                    moveTo(s * 0.1f, s * 0.75f)
                    lineTo(s * 0.36f, s * 0.48f)
                    lineTo(s * 0.58f, s * 0.62f)
                    lineTo(s * 0.9f, s * 0.24f)
                }
                drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            AppTab.HISTORY -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { i, y ->
                    drawLine(
                        color,
                        Offset(s * 0.16f, s * y),
                        Offset(s * (if (i == 2) 0.62f else 0.84f), s * y),
                        stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            AppTab.USERS -> {
                drawCircle(color, s * 0.14f, center = Offset(s * 0.38f, s * 0.36f), style = Stroke(stroke))
                drawCircle(color, s * 0.10f, center = Offset(s * 0.72f, s * 0.42f), style = Stroke(stroke))
                drawArc(
                    color = color,
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(s * 0.08f, s * 0.52f),
                    size = androidx.compose.ui.geometry.Size(s * 0.84f, s * 0.8f),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/* ---------------- 改归属对话框 ---------------- */

@Composable
private fun ReassignDialog(
    users: List<UserEntity>,
    event: SaveEvent,
    unit: com.cyberscale.app.data.WeightUnit,
    onPick: (UserEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = { Text("改归属 / REASSIGN", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    "选择这条 ${formatWeight(event.weightKg, unit)} ${unit.label} 记录应归属的成员：",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                users.forEach { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberSurface2)
                            .border(1.dp, CardStroke, RoundedCornerShape(10.dp))
                            .clickable { onPick(user) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = user.name,
                            color = Color(user.colorArgb),
                            active = user.id == event.userId
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(user.name, color = TextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        if (user.id == event.userId) {
                            Text("CURRENT", color = NeonCyan, fontSize = 9.sp, fontFamily = Mono)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}
