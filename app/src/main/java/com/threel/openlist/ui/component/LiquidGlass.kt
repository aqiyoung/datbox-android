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
/**
 * 玻璃卡 (iOS 26 / visionOS 风格, v0.3.22 增强不透明度)
 *
 * 老板 6/14 17:35 拍: '增加一下不透明度, 效果没上来'
 * 调整: 上 0.20->0.45, 下 0.06->0.30, 边 0.30->0.55, 外层 0.10->0.18
 * 保留: 玻璃质感 (仍半透明, 能看到背景 Parchment), 渐变层级
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    background: Color = Color.White.copy(alpha = 0.18f),
    borderColor: Color = Color.White.copy(alpha = 0.55f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAF9F5).copy(alpha = 0.45f),
                        Color(0xFFFAF9F5).copy(alpha = 0.30f),
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
    // 老板 6/14 17:35 拍: 增加不透明度, 效果没上来
    // 调整: 未选 0.55 -> 0.70, 已选 0.95 保持 (已选中态几乎不透明)
    val baseAlpha = if (selected) 0.98f else 0.70f
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

/**
 * 玻璃 TopAppBar (iOS 26 风格, v0.3.22 增强不透明度)
 *
 * 老板 6/14 17:35 拍: '增加一下不透明度, 效果没上来'
 * v0.3.21 之前是单层 0.75, 看起来像 '半透明纸片', 不像玻璃
 * v0.3.22 改成: Box 包装 + 渐变背景 + 1px 边
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassTopBar(
    title: String,
    leadingIcon: ImageVector? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val topBarShape = androidx.compose.foundation.shape.RoundedCornerShape(
        bottomStart = 20.dp,
        bottomEnd = 20.dp,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAF9F5).copy(alpha = 0.85f),
                        Color(0xFFFAF9F5).copy(alpha = 0.65f),
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.25f),
                    )
                ),
                shape = topBarShape,
            )
            .clip(topBarShape),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            // 老板 6/14 16:35 拍: 不用 Terracotta, 改 NearBlack
                            tint = Color(0xFF141413),
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
                // 透明, 让外层 Box 渐变显示出来
                containerColor = Color.Transparent,
                titleContentColor = Color(0xFF141413),
                navigationIconContentColor = Color(0xFF141413),
                actionIconContentColor = Color(0xFF141413),
            ),
        )
    }
}

/**
 * 玻璃圆 FAB (老板 6/14 16:05 拍: "液态玻璃吗？透明色", "我不要橙色")
 *
 * v0.3.17 重设计: iOS 26 / visionOS 风格白玻璃
 * - 56dp 圆
 * - 白渐变 (上 0.7 alpha, 下 0.5 alpha)
 * - 1px 白边 0.8 alpha
 * - icon 黑色 #141413 (跟 LiquidGlassRow 一致)
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
                        Color.White.copy(alpha = if (enabled) 0.70f else 0.40f),
                        Color.White.copy(alpha = if (enabled) 0.50f else 0.30f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.80f), shape)
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
            tint = Color(0xFF141413),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * 玻璃主操作按钮 (老板 6/14 16:05 拍: "白就行了", "液态玻璃透明色")
 *
 * v0.3.17 重设计: iOS 26 / visionOS 风格白玻璃 (不用 Terracotta)
 * - 12dp 圆角
 * - 48dp 高度
 * - 白渐变 (上 0.70 alpha, 下 0.50 alpha)
 * - 0.5dp 白边 0.7 alpha
 * - icon + 文字 黑色 #141413
 * - fillMaxWidth
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
                        Color.White.copy(alpha = if (enabled) 0.70f else 0.40f),
                        Color.White.copy(alpha = if (enabled) 0.50f else 0.30f),
                    )
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.70f), shape)
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
            Icon(icon, contentDescription = null, tint = Color(0xFF141413), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color(0xFF141413),
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}
