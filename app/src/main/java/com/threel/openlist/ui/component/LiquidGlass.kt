package com.threel.openlist.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃容器（iOS 26 风格）。
 *
 * 视觉要素（自下而上 4 层）：
 * 1. 暖色径向渐变底
 * 2. 顶部高光（半透明白）
 * 3. 1px 描边
 * 4. 内部留 padding
 *
 * 用法：
 *   LiquidGlassCard { Text("Inside") }
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    background: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAF9F5).copy(alpha = 0.20f),
                        Color(0xFFFAF9F5).copy(alpha = 0.06f),
                    )
                )
            )
            .background(background)
            .clip(shape)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}
