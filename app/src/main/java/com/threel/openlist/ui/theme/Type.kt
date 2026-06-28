package com.threel.openlist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.threel.openlist.R

// iOS 26 风格字体 - 使用 MiSans 作为 SF Pro 的替代
private val misans_regular = Font(R.font.misans_regular, FontWeight.Normal)
private val misans_medium = Font(R.font.misans_medium, FontWeight.Medium)
private val misans_semibold = Font(R.font.misans_semibold, FontWeight.SemiBold)
private val misans_bold = Font(R.font.misans_bold, FontWeight.Bold)

val MiSansFamily = FontFamily(
    misans_regular,
    misans_medium,
    misans_semibold,
    misans_bold,
)

// iOS 26 风格排版
// - 大标题: 34sp, Semibold, 间距 -0.5
// - 标题: 22sp, Semibold
// - 正文: 17sp, Normal
// - 说明: 13sp, Normal
val OpenListTypography = Typography(
    // 大标题 (Navigation Title Large)
    displayLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.5).sp
    ),
    // 标题 (Navigation Title)
    headlineLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    // 卡片标题
    titleLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    // 正文
    bodyLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp
    ),
    // 标签
    labelLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
)

// 旧 API 保留
@Deprecated("改用 MiSansFamily", ReplaceWith("MiSansFamily"))
val SerifFamily = MiSansFamily
@Deprecated("改用 MiSansFamily", ReplaceWith("MiSansFamily"))
val SansFamily = MiSansFamily
