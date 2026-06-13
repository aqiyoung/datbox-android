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

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = WarmIvory,
    primaryContainer = Terracotta.copy(alpha = 0.1f),
    onPrimaryContainer = TerracottaDeep,
    secondary = OliveGray,
    background = Parchment,
    onBackground = NearBlack,
    surface = WarmIvory,
    onSurface = NearBlack,
    surfaceVariant = BorderCream,
    onSurfaceVariant = OliveGray,
    outline = BorderWarm,
)

private val DarkColors = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = NearBlack,
    primaryContainer = TerracottaDeep,
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
