package com.cyberscale.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cyberscale.app.ui.theme.GridLine
import com.cyberscale.app.ui.theme.NeonCyan
import com.cyberscale.app.ui.theme.NeonMagenta
import kotlin.math.max

data class ChartPoint(val ts: Long, val value: Double)

/**
 * 手绘发光折线图（不引入第三方图表库，避免版本 API 漂移）。
 * - 曲线从底部升起的入场动画（Animatable，切换时间范围时重播）
 * - 三次描边模拟霓虹辉光
 * - 目标体重虚线
 * - 末点呼吸脉冲
 */
@Composable
fun NeonLineChart(
    points: List<ChartPoint>,
    goal: Double?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val reveal = remember(points.size, goal) { Animatable(0f) }
    LaunchedEffect(points.size, goal) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    val infinite = rememberInfiniteTransition(label = "chart")
    val pulse by infinite.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "chartPulse"
    )

    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 14.dp.toPx()

        // 水平虚线网格
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            drawLine(
                color = GridLine,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f,
                alpha = 0.9f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 7.dp.toPx()))
            )
        }

        if (points.isEmpty()) {
            drawLine(
                color = accent.copy(alpha = 0.25f),
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val values = points.map { it.value }
        val all = values + listOfNotNull(goal)
        var lo = all.min()
        var hi = all.max()
        val padding = max(0.6, (hi - lo) * 0.2)
        lo -= padding
        hi += padding
        val span = (hi - lo).coerceAtLeast(0.1)

        fun yOf(v: Double): Float =
            bottom - ((v - lo) / span).toFloat() * (bottom - top)

        val xs = if (points.size == 1) {
            listOf((left + right) / 2f)
        } else {
            points.indices.map { left + (right - left) * it / (points.size - 1) }
        }
        val ys = values.map { v -> bottom - (bottom - yOf(v)) * reveal.value }

        // 渐变面积
        val areaPath = Path().apply {
            moveTo(xs.first(), bottom)
            xs.forEachIndexed { i, x -> lineTo(x, ys[i]) }
            lineTo(xs.last(), bottom)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                startY = top,
                endY = bottom
            )
        )

        // 折线：外发光 + 主体渐变
        val linePath = Path().apply {
            xs.forEachIndexed { i, x ->
                if (i == 0) moveTo(x, ys[i]) else lineTo(x, ys[i])
            }
        }
        drawPath(
            path = linePath,
            color = accent.copy(alpha = 0.16f),
            style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(listOf(NeonCyan, accent)),
            style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 目标线
        goal?.let { g ->
            val gy = yOf(g)
            if (gy in top..bottom) {
                drawLine(
                    color = NeonMagenta.copy(alpha = 0.75f),
                    start = Offset(left, gy),
                    end = Offset(right, gy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
                )
            }
        }

        // 数据点
        xs.forEachIndexed { i, x ->
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = 5.dp.toPx(),
                center = Offset(x, ys[i])
            )
            drawCircle(
                color = Color.White,
                radius = 2.2.dp.toPx(),
                center = Offset(x, ys[i])
            )
        }

        // 末点脉冲
        val lx = xs.last()
        val ly = ys.last()
        drawCircle(accent.copy(alpha = 0.22f * pulse), 14.dp.toPx(), Offset(lx, ly))
        drawCircle(accent, 5.dp.toPx(), Offset(lx, ly))
        drawCircle(Color.White, 2.dp.toPx(), Offset(lx, ly))
    }
}
