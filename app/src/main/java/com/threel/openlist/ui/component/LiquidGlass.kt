package com.threel.openlist.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * 液态玻璃组件库 (iOS 26 风格, v0.3.14 增强版)
 *
 * 视觉要素 (自下而上 4 层):
 * 1. 暖色径向渐变底 (Parchment 0.20 → 0.06 alpha)
 * 2. 顶部高光 (半透明白)
 * 3. 1px 描边 (BorderCream alpha 0.30)
 * 4. 内部 padding
 *
 * 老板 6/14: 7+2 项 UI 优化都用这个组件, 视觉一致
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    background: Color = Color.White.copy(alpha = 0.10f),
    borderColor: Color = Color.White.copy(alpha = 0.30f),
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
            .border(1.dp, borderColor, shape)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/** 玻璃行 (列表 item) */
@Composable
fun LiquidGlassRow(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    selected: Boolean = false,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    leading: @Composable (() -> Unit)? = null,
    title: String = "",
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseAlpha = if (selected) 0.95f else 0.55f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = baseAlpha),
                        Color.White.copy(alpha = baseAlpha * 0.6f),
                    )
                )
            )
            .border(0.5.dp, Color(0xFFFAF9F5).copy(alpha = 0.6f), shape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 玻璃 TopAppBar */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassTopBar(
    title: String,
    leadingIcon: ImageVector? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = Color(0xFFC96442),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = navigationIcon ?: {},
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFFAF9F5).copy(alpha = 0.75f),
            titleContentColor = Color(0xFF141413),
            navigationIconContentColor = Color(0xFF141413),
            actionIconContentColor = Color(0xFF141413),
        ),
    )
}

/**
 * 玻璃圆 FAB (老板 6/14 15:25 拍: "圆形的加号设计一下, 不需要文字")
 *
 * 设计: 56dp 圆 + 加号 icon + Terracotta 渐变 + 1px BorderCream
 */
@Composable
fun LiquidGlassFab(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFC96442).copy(alpha = if (enabled) 0.95f else 0.5f),
                        Color(0xFF8A3A20).copy(alpha = if (enabled) 0.95f else 0.5f),
                    )
                )
            )
            .border(1.dp, Color(0xFFFAF9F5).copy(alpha = 0.4f), shape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * 玻璃登录/主操作按钮 (老板 6/14 15:25 拍: 重新设计, 不像"灯笼框")
 *
 * 修正:
 * - 圆角 12dp (不是 20dp 灯笼, 也不是 0dp 死板矩形)
 * - 渐变更素雅: Terracotta 0.85 -> 0.7 (v0.3.15 0.95+0.95 太艳)
 * - 细 1px BorderCream (v0.3.15 Terracotta 1.6 太黑)
 * - 高度 48dp (v0.3.15 56dp 太像灯笼) + 内容居中
 * - 文字 15sp medium 白色 (v0.3.15 文字 16sp + 文字大写 "登 录" 中间空格)
 * - 全面使用 fillMaxWidth
 */
@Composable
fun LiquidGlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFC96442).copy(alpha = if (enabled) 0.85f else 0.4f),
                        Color(0xFFA04F30).copy(alpha = if (enabled) 0.85f else 0.4f),
                    )
                )
            )
            .border(0.5.dp, Color(0xFFFAF9F5).copy(alpha = 0.5f), shape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}
