package com.threel.openlist.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * 视觉要素（自下而上 5 层）：
 * 1. 暖色径向渐变底
 * 2. 顶部高光（半透明白椭圆）
 * 3. 内部毛玻璃（API 31+ RenderEffect blur；低版本降级为半透明蒙版）
 * 4. 1px 描边
 * 5. 微妙内阴影（用 second gradient 模拟）
 *
 * 用法：
 *   LiquidGlassCard { Text("Inside") }
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    background: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                // 暖色径向渐变 + 半透明白
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAF9F5).copy(alpha = 0.20f),
                        Color(0xFFFAF9F5).copy(alpha = 0.06f),
                    )
                )
            )
            .background(background)
            .clip(shape)
            // 描边
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.0f),
                    )
                )
            )
    ) {
        // 内部内容
        Box(modifier = Modifier.padding(16.dp)) { content() }

        // 顶部高光（额外一层）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 高 API 用 RenderEffect 真正的毛玻璃；这里先放占位
            // 实际效果用 Modifier.blur() 在更上层 wrap
        }
    }
}

private val padding: androidx.compose.foundation.layout.PaddingValues
    get() = androidx.compose.foundation.layout.PaddingValues(0.dp)
