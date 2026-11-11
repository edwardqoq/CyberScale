package com.cyberscale.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberscale.app.data.WeightUnit
import com.cyberscale.app.data.formatWeight
import com.cyberscale.app.ui.theme.CardStroke
import com.cyberscale.app.ui.theme.CyberBg
import com.cyberscale.app.ui.theme.CyberBgBottom
import com.cyberscale.app.ui.theme.CyberBgTop
import com.cyberscale.app.ui.theme.CyberSurface
import com.cyberscale.app.ui.theme.CyberSurface2
import com.cyberscale.app.ui.theme.GridLine
import com.cyberscale.app.ui.theme.Mono
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonGreen
import com.cyberscale.app.ui.theme.NeonMagenta
import com.cyberscale.app.ui.theme.TextDim
import com.cyberscale.app.ui.theme.TextPrimary
import com.cyberscale.app.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/* ------------------------------------------------------------------ *
 * 全局背景：透视网格 + 缓慢下移的扫描线 + 呼吸光晕
 * ------------------------------------------------------------------ */

@Composable
fun CyberGridBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "grid")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(CyberBgTop, CyberBg, CyberBgBottom),
                startY = 0f,
                endY = size.height
            )
        )

        // 两团霓虹辉光
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.22f * breathe), Color.Transparent)
            ),
            radius = size.width * 0.75f,
            center = Offset(0f, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonMagenta.copy(alpha = 0.16f * breathe), Color.Transparent)
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width, size.height)
        )

        // 网格
        val step = 34.dp.toPx()
        val line = 1f
        var x = 0f
        while (x < size.width) {
            drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), line, alpha = 0.55f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(GridLine, Offset(0f, y), Offset(size.width, y), line, alpha = 0.45f)
            y += step
        }

        // 扫描线 + 拖尾
        val sy = size.height * sweep
        val tail = 90.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, NeonCyan.copy(alpha = 0.10f)),
                startY = sy - tail,
                endY = sy
            ),
            topLeft = Offset(0f, sy - tail),
            size = Size(size.width, tail)
        )
        drawLine(
            color = NeonCyan,
            start = Offset(0f, sy),
            end = Offset(size.width, sy),
            strokeWidth = 1.2.dp.toPx(),
            alpha = 0.35f
        )
    }
}

/* ------------------------------------------------------------------ *
 * 卡片 / 标题 / 分隔
 * ------------------------------------------------------------------ */

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface.copy(alpha = 0.78f))
            .drawBehind {
                val r = 16.dp.toPx()
                val s = 1.dp.toPx()
                drawRoundRect(
                    color = CardStroke,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(s)
                )
                // 顶部流光细线
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.9f), Color.Transparent)
                    ),
                    start = Offset(r, 0f),
                    end = Offset(size.width - r, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
                // 左侧能量条
                drawLine(
                    color = accent.copy(alpha = 0.55f),
                    start = Offset(0f, r * 1.4f),
                    end = Offset(0f, size.height - r * 1.4f),
                    strokeWidth = 2.dp.toPx()
                )
                // 四角锚点
                val dot = 1.6.dp.toPx()
                listOf(
                    Offset(r * 0.55f, r * 0.55f),
                    Offset(size.width - r * 0.55f, r * 0.55f),
                    Offset(r * 0.55f, size.height - r * 0.55f),
                    Offset(size.width - r * 0.55f, size.height - r * 0.55f)
                ).forEach { drawCircle(accent.copy(alpha = 0.7f), dot, it) }
            }
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    code: String,
    accent: Color = NeonCyan,
    trailing: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(15.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("[$code]", color = accent.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = Mono)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(it, color = TextDim, fontSize = 10.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun GlowDivider(accent: Color = NeonCyan) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.5f), Color.Transparent)
                )
            )
    )
}

/* ------------------------------------------------------------------ *
 * 仪表：环形刻度 + 渐变进度弧 + 旋转光点 + 数字弹性滚动
 * ------------------------------------------------------------------ */

@Composable
fun NeonRingGauge(
    weight: Double?,
    unit: WeightUnit,
    progress: Float,
    scanning: Boolean,
    locked: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "ring")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (scanning) 5_000 else 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val animatedWeight by animateFloatAsState(
        targetValue = (weight ?: 0.0).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weightRoll"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 2f - 22.dp.toPx()
            val track = 9.dp.toPx()

            // 外圈刻度
            for (i in 0 until 72) {
                val a = Math.toRadians((i * 5.0 - 90.0))
                val long = i % 6 == 0
                val r0 = radius + 8.dp.toPx()
                val r1 = r0 + if (long) 7.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = if (long) accent.copy(alpha = 0.45f) else GridLine,
                    start = Offset(cx + r0 * cos(a).toFloat(), cy + r0 * sin(a).toFloat()),
                    end = Offset(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat()),
                    strokeWidth = if (long) 1.6.dp.toPx() else 1.dp.toPx()
                )
            }

            // 底环
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(track, cap = StrokeCap.Round)
            )

            // 目标进度弧（外发光复描 + 渐变本体）
            val sweep = 360f * (0.08f + animatedProgress * 0.92f)
            drawArc(
                color = accent.copy(alpha = 0.16f * glow),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(track * 2.4f, cap = StrokeCap.Round)
            )
            drawArc(
                // 主弧颜色 = BMI 等级色（accent 由 MeasureScreen 按国标分级传入）。
                // 用同一色相的明暗扫掠保留"旋转"观感，同时让等级色纯粹可辨。
                brush = Brush.sweepGradient(
                    listOf(
                        accent.copy(alpha = 0.28f),
                        accent.copy(alpha = 0.72f),
                        accent,
                        accent.copy(alpha = 0.68f),
                        accent.copy(alpha = 0.28f)
                    ),
                    center = Offset(cx, cy)
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(track, cap = StrokeCap.Round)
            )

            // 轨道上的旋转光点
            val a = Math.toRadians((rotation - 90.0))
            val px = cx + radius * cos(a).toFloat()
            val py = cy + radius * sin(a).toFloat()
            drawCircle(accent.copy(alpha = 0.18f * glow), 10.dp.toPx(), Offset(px, py))
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(px, py))

            // 内圈虚线
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = radius - 22.dp.toPx(),
                style = Stroke(
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 9.dp.toPx()))
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CURRENT MASS / 当前体重",
                color = TextDim,
                fontSize = 9.sp,
                fontFamily = Mono,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (weight == null) "--.--" else formatWeight(animatedWeight.toDouble(), unit),
                style = TextStyle(
                    fontFamily = Mono,
                    fontWeight = FontWeight.Black,
                    fontSize = 52.sp,
                    letterSpacing = (-3).sp,
                    color = TextPrimary,
                    shadow = Shadow(accent.copy(alpha = 0.85f * glow), blurRadius = 30f)
                ),
                textAlign = TextAlign.Center
            )
            Text(
                unit.label.uppercase(),
                color = accent,
                fontSize = 12.sp,
                fontFamily = Mono,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(10.dp))
            val status = when {
                locked -> "LOCKED · 数值已锁定"
                scanning -> "SCANNING · 等待上秤"
                else -> "STANDBY · 监听已暂停"
            }
            val statusColor = if (locked) NeonGreen else if (scanning) NeonCyan else TextDim
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(status, color = statusColor, fontSize = 9.sp, fontFamily = Mono)
            }
        }
    }
}

/** 三层同心波纹，扫描时从中心不断扩散。 */
@Composable
fun ScanPulse(active: Boolean, accent: Color, modifier: Modifier = Modifier) {
    if (!active) return
    val infinite = rememberInfiniteTransition(label = "pulse")
    val p1 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "p1"
    )
    val p2 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, delayMillis = 800, easing = LinearEasing), RepeatMode.Restart),
        label = "p2"
    )
    val p3 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_400, delayMillis = 1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "p3"
    )
    Canvas(modifier = modifier) {
        val maxR = size.minDimension / 2f
        listOf(p1, p2, p3).forEach { p ->
            drawCircle(
                color = accent.copy(alpha = (1f - p) * 0.20f),
                radius = maxR * p,
                style = Stroke(1.5.dp.toPx())
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 小组件
 * ------------------------------------------------------------------ */

@Composable
fun LedDot(active: Boolean, color: Color = NeonGreen, dotSize: androidx.compose.ui.unit.Dp = 7.dp) {
    val infinite = rememberInfiniteTransition(label = "led")
    val alpha by infinite.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(if (active) 900 else 2_400), RepeatMode.Reverse),
        label = "ledAlpha"
    )
    Canvas(Modifier.size(dotSize)) {
        drawCircle(color.copy(alpha = if (active) alpha else 0.25f), dotSize.toPx() / 2f)
    }
}

@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val level = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi > -100 -> 1
        else -> 0
    }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((4 + i * 3).dp)
                    .background(
                        if (i < level) NeonCyan else GridLine,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

/** 实时广播帧的 hex 流，带闪烁光标。 */
@Composable
fun HexStream(hex: String, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "cursor")
    val blink by infinite.animateFloat(
        1f, 0f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "blink"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("RX ", color = NeonMagenta, fontSize = 9.sp, fontFamily = Mono)
        Text(
            text = if (hex.isEmpty()) "WAITING FOR BROADCAST..." else hex,
            color = NeonCyan.copy(alpha = 0.72f),
            fontSize = 9.sp,
            fontFamily = Mono,
            maxLines = 2
        )
        Text("▌", color = NeonCyan.copy(alpha = blink), fontSize = 9.sp)
    }
}

@Composable
fun NeonPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan
) {
    val bg by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.16f) else Color.Transparent,
        label = "pillBg"
    )
    val border by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.75f) else CardStroke,
        label = "pillBorder"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) accent else TextSecondary,
            fontSize = 12.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NeonActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    filled: Boolean = true
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    NeonCard(modifier = modifier, accent = accent, contentPadding = PaddingValues(12.dp)) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = Mono,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                shadow = Shadow(accent.copy(alpha = 0.6f), blurRadius = 18f)
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(hint, color = TextDim, fontSize = 9.sp)
    }
}

@Composable
fun UserAvatar(
    name: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "avatar")
    val sweep by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "avatarSweep"
    )
    Box(modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color.copy(alpha = 0.14f), size.minDimension / 2f)
            if (active) {
                drawArc(
                    color = color,
                    startAngle = sweep,
                    sweepAngle = 200f,
                    useCenter = false,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = size.minDimension / 2f,
                    style = Stroke(1.dp.toPx())
                )
            }
        }
        Text(
            name.take(1),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = Mono),
        cursorBrush = SolidColor(NeonCyan),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurface2)
            .border(1.dp, CardStroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextDim, fontSize = 12.sp, fontFamily = Mono)
                }
                inner()
            }
        }
    )
}

@Composable
fun EmptyStateBlock(
    title: String,
    subtitle: String,
    accent: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "empty")
    val alpha by infinite.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "emptyAlpha"
    )
    Column(
        modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(56.dp)) {
            drawCircle(
                color = accent.copy(alpha = 0.25f * alpha),
                radius = size.minDimension / 2f,
                style = Stroke(1.5.dp.toPx())
            )
            drawCircle(
                color = accent.copy(alpha = 0.8f * alpha),
                radius = 4.dp.toPx()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
