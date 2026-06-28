package com.threel.openlist.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.ui.theme.BorderLight
import com.threel.openlist.ui.theme.GlassBorder
import com.threel.openlist.ui.theme.GlassHighlight
import com.threel.openlist.ui.theme.GlassShadow
import com.threel.openlist.ui.theme.GlassWhite
import com.threel.openlist.ui.theme.MiSansFamily
import com.threel.openlist.ui.theme.WarmWhite

// ===== iOS 26 风格液态玻璃组件库 =====

/**
 * 玻璃卡 (iOS 26 / visionOS 风格)
 *
 * 视觉要素 (自下而上):
 * 1. 暖色径向渐变底 (WarmWhite 0.95 → 0.80 alpha)
 * 2. 顶部高光线 (白色 0.9 → 0)
 * 3. 0.5dp 玻璃边框 (White 0.6 alpha)
 * 4. 内容区 (清晰, 不模糊)
 *
 * iOS 26 特征:
 * - 大圆角 24dp
 * - 高 alpha 白玻璃 (0.85-0.95)
 * - 模糊半径 8dp (更柔和)
 * - 顶部暖色光晕
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier) {
        // 外层: 玻璃背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .blur(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            WarmWhite.copy(alpha = 0.95f),
                            WarmWhite.copy(alpha = 0.80f),
                        )
                    )
                )
                // 顶部高光线
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GlassHighlight,
                                Color.White.copy(alpha = 0.0f),
                            ),
                            startY = 0f,
                            endY = 3f,
                        ),
                        size = Size(size.width, 3f),
                    )
                }
                .border(0.5.dp, GlassBorder, shape)
        )
        // 内层: 内容
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * 玻璃行 (列表 item)
 *
 * iOS 26 特征:
 * - 圆角 16dp
 * - 选中时更高 alpha (0.95 vs 0.85)
 * - 模糊 6dp (更柔和)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassRow(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    leading: @Composable (() -> Unit)? = null,
    title: String = "",
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseAlpha = if (selected) 0.95f else 0.85f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // 外层: 玻璃背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .blur(6.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = baseAlpha),
                            Color.White.copy(alpha = baseAlpha * 0.85f),
                        )
                    )
                )
                .border(0.5.dp, GlassBorder.copy(alpha = 0.5f), shape)
        )
        // 内层: 内容
        Row(
            modifier = Modifier.padding(contentPadding),
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
}

/**
 * 玻璃 TopAppBar (iOS 26 风格)
 *
 * iOS 26 特征:
 * - 底部大圆角 24dp
 * - 高 alpha 白玻璃 (0.9)
 * - 模糊 8dp
 * - 标题加粗 Semibold
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassTopBar(
    title: String,
    leadingIcon: ImageVector? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val topBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val titleTextStyle = TextStyle(
        color = Color(0xFF141413),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = MiSansFamily,
        shadow = Shadow(
            color = GlassShadow,
            offset = Offset(0f, 1f),
            blurRadius = 1f,
        ),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        // 外层玻璃背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(topBarShape)
                .blur(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            WarmWhite.copy(alpha = 0.9f),
                            WarmWhite.copy(alpha = 0.75f),
                        )
                    )
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GlassHighlight,
                                Color.White.copy(alpha = 0.0f),
                            ),
                            startY = 0f,
                            endY = 3f,
                        ),
                        size = Size(size.width, 3f),
                    )
                }
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassBorder,
                            GlassBorder.copy(alpha = 0.3f),
                        )
                    ),
                    shape = topBarShape,
                )
        )
        // 内层: TopAppBar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = Color(0xFF141413).copy(alpha = 0.95f),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = titleTextStyle,
                    )
                }
            },
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color(0xFF141413),
                navigationIconContentColor = Color(0xFF141413).copy(alpha = 0.95f),
                actionIconContentColor = Color(0xFF141413).copy(alpha = 0.95f),
            ),
        )
    }
}

/**
 * 玻璃圆形 FAB
 *
 * iOS 26 特征:
 * - 56dp 圆形
 * - 高 alpha 白玻璃 (0.9)
 * - 1dp 玻璃边框
 */
@Composable
fun LiquidGlassFab(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (enabled) 0.9f else 0.5f),
                        Color.White.copy(alpha = if (enabled) 0.7f else 0.3f),
                    )
                )
            )
            .border(1.dp, GlassBorder, CircleShape)
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
 * 玻璃主操作按钮
 *
 * iOS 26 特征:
 * - 圆角 16dp
 * - 高度 48dp
 * - 高 alpha 白玻璃 (0.9)
 */
@Composable
fun LiquidGlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (enabled) 0.9f else 0.5f),
                        Color.White.copy(alpha = if (enabled) 0.7f else 0.3f),
                    )
                )
            )
            .border(0.5.dp, GlassBorder, shape)
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MiSansFamily,
        )
    }
}
