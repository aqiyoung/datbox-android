package com.threel.openlist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 老板 6/14 16:35 拍: "橙色丑不拉几, 改成透明白色, 液态玻璃风格"
private val LightColors = lightColorScheme(
    primary = NearBlack,            // 老板拍: 黑色 (液态玻璃原色), 不用 Terracotta
    onPrimary = WarmIvory,
    primaryContainer = NearBlack.copy(alpha = 0.08f),
    onPrimaryContainer = NearBlack,
    secondary = OliveGray,
    background = Parchment,
    onBackground = NearBlack,
    surface = WarmIvory,
    onSurface = NearBlack,
    surfaceVariant = BorderCream,
    onSurfaceVariant = OliveGray,
    outline = BorderWarm,
)

// 老板拍: 夜间模式也用液态玻璃 (黑底白玻璃)
private val DarkColors = darkColorScheme(
    primary = WarmIvory,
    onPrimary = NearBlack,
    primaryContainer = WarmIvory.copy(alpha = 0.12f),
    onPrimaryContainer = WarmIvory,
    secondary = WarmSilver,
    background = NearBlack,
    onBackground = WarmIvory,
    surface = DarkSurface,
    onSurface = WarmIvory,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = WarmSilver,
    outline = OliveGray,
)

@Composable
fun OpenListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // 关掉 dynamic color 保持品牌色
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenListTypography,
        content = content,
    )
}
