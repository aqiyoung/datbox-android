package com.datbox.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NearBlack,
    onPrimary = WarmIvory,
    primaryContainer = NearBlack.copy(alpha = 0.08f),
    onPrimaryContainer = NearBlack,
    secondary = StoneGray,
    background = Color.White,
    onBackground = NearBlack,
    surface = Color.White,
    onSurface = NearBlack,
    surfaceVariant = BorderCream,
    onSurfaceVariant = OliveGray,
    outline = BorderLight,
    outlineVariant = BorderWarm,
)

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
    outlineVariant = StoneGray,
)

@Composable
fun OpenListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
