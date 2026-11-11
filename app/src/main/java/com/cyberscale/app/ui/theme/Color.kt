package com.cyberscale.app.ui.theme

import androidx.compose.ui.graphics.Color

// 画布 / 表面
val CyberBg = Color(0xFF05060F)
val CyberBgTop = Color(0xFF0A0F22)
val CyberBgBottom = Color(0xFF02030A)
val CyberSurface = Color(0xFF0B1020)
val CyberSurface2 = Color(0xFF101A33)
val GridLine = Color(0xFF14203A)
val CardStroke = Color(0xFF1D2C4E)

// 霓虹主色
val NeonCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF2E97)
val NeonPurple = Color(0xFF8B5CF6)
val NeonGreen = Color(0xFF39FF88)
val NeonAmber = Color(0xFFFFB020)
val NeonBlue = Color(0xFF4D7CFF)

// 文字
val TextPrimary = Color(0xFFE9F6FF)
val TextSecondary = Color(0xFF8194BC)
val TextDim = Color(0xFF4A5B7F)

// 数据语义：向好(下降/达成)=荧光绿，上升=琥珀。只表示体重变化方向，不表示 BMI 等级。
val DataGood = NeonGreen
val DataRise = NeonAmber

// BMI 国标四级的语义色集中在 BmiLevel.colorArgb（data 层），此处仅作语义说明：
// 偏瘦=冷蓝 38BDF8 / 正常=荧光绿 39FF88 / 超重=琥珀 FFB020 / 肥胖=品红 FF2E97

/** 成员主题色，也用于生成几何头像。 */
val UserColors = listOf(
    NeonCyan,
    NeonMagenta,
    NeonPurple,
    NeonGreen,
    NeonAmber,
    NeonBlue
)
