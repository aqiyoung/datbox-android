package com.datbox.app.ui.theme

import androidx.compose.ui.graphics.Color

// 背景色 - 暖白 (iOS 26 风格)
val WarmIvory = Color(0xFFFAF9F5)

// 深色
val NearBlack = Color(0xFF141413)
val DarkSurface = Color(0xFF2C2C2E)

// 灰色阶 (iOS 26 风格)
val StoneGray = Color(0xFF87867F)
val OliveGray = Color(0xFF5E5D59)

// 边框
val BorderCream = Color(0xFFF0EEE6)
val BorderWarm = Color(0xFFE8E6DC)
val BorderLight = Color(0xFFE5E5EA)

// 玻璃效果色
val GlassShadow = Color.Black.copy(alpha = 0.08f)
// 玻璃发丝边: 背景是纯白, 白边不可见 → 用极淡的中性灰定义边界 (iOS 27 做法)
val GlassBorder = Color.Black.copy(alpha = 0.07f)

// Dark variants
val WarmSilver = Color(0xFFB0AEA5)

// ===== 语义色: 从散落的 inline Color(0xFF…) 收敛而来, 值与原处逐字一致 (观感不变) =====
val BrandGreen = Color(0xFF20C997)      // 主品牌绿 (按钮 / 选中 / 强调)
val SuccessGreen = Color(0xFF34C759)    // 成功
val WarningOrange = Color(0xFFFF9500)   // 警告
val DangerRed = Color(0xFFFF3B30)       // 危险 / 删除
val ErrorRedSoft = Color(0xFFFF6B6B)    // 错误文案
val FieldBg = Color(0xFFF5F7FB)         // 输入框浅底
val TextStrong = Color(0xFF2A2925)      // 主文字
val TextBody = Color(0xFF333333)        // 正文
val TextMuted = Color(0xFF888888)       // 次要文字
val TextFaint = Color(0xFFAAAAAA)       // 更弱 (图标 / 单位)
val TextFainter = Color(0xFFBBBBBB)     // 占位符
val TextGhost = Color(0xFFCCCCCC)       // 极弱 (版本号等)
// 注: TextMuted / TextFaint / TextFainter / TextGhost 是四个相近灰阶, 值不同故暂各自保留, 后续可合并为两档

// ===== 文件类型图标 (暖灰阶: 零彩色, 类型只靠符号形状区分) =====
val IconTileTop = Color(0xFFEFEDE7)     // 方块玻璃渐变顶
val IconTileBottom = Color(0xFFE3E0D8)  // 方块玻璃渐变底
val IconTileBorder = Color(0xFFD5D2C9)  // 方块发丝边
val IconInk = Color(0xFF6E6C64)         // 符号墨色
val IconCutout = Color(0xFFE9E6DF)      // 符号镂空色 (对齐方块中部底色)
