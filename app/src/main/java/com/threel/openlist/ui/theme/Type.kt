package com.threel.openlist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.threel.openlist.R

// v0.3.27 老板 6/14 19:35 拍: '现在用的什么字体？看着有点刺眼, 用 MiSans 吧'
//
// 之前: SerifFamily (系统衬线) + SansFamily (Roboto/Noto Sans), 衬线在小字号 + 白底
// 阴影下看着扎眼. 统一用 MiSans (小米 2022, CC-BY 4.0, 中文 + 拉丁, 4 字重)
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

// 旧 API 保留, 不要再用, 改用 MiSansFamily
@Deprecated("改用 MiSansFamily", ReplaceWith("MiSansFamily"))
val SerifFamily = MiSansFamily
@Deprecated("改用 MiSansFamily", ReplaceWith("MiSansFamily"))
val SansFamily = MiSansFamily

val OpenListTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MiSansFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
)
