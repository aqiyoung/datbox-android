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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * 玻璃卡 (iOS 26 / visionOS 真液态玻璃, v0.3.23)
 *
 * 老板 6/14 17:35 拍: "一眼看上去就是那种液态玻璃的"
 * 关键 4 要素:
 * 1. 背景模糊 (blur 20dp) — 看后面内容扭曲
 * 2. 高 alpha 白渐变 (上 0.70 下 0.55) — 明显白玻璃
 * 3. 1px 白色边 0.7
 * 4. 顶部 1px 光泽 (高光)
 * 5. 圆角 20dp
 *
 * 实现: 用 Box 分两层 — 外层背景 (clip + blur + 渐变 + 边 + 光泽), 内层 content (清晰)
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier) {
        // 外层: 玻璃背景 (模糊 + 渐变 + 边 + 光泽)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                // 背景模糊 (16dp, iOS 26 同款) — 模糊的是"后面的内容"
                .blur(16.dp)
                // 高 alpha 白渐变
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFAF9F5).copy(alpha = 0.70f),
                            Color(0xFFFAF9F5).copy(alpha = 0.55f),
                        )
                    )
                )
                // 顶部 1px 光泽 (高光)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.0f),
                            ),
                            startY = 0f,
                            endY = 2f,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, 2f),
                    )
                }
                // 1px 白边 0.7
                .border(1.dp, Color.White.copy(alpha = 0.70f), shape)
        )
        // 内层: 内容 (清晰, 不模糊)
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/** 玻璃行 (列表 item, v0.3.23 加 blur + 光泽) */
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
    // 老板 6/14 17:35 拍: 增加不透明度
    val baseAlpha = if (selected) 0.92f else 0.72f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // 外层背景: blur + 渐变 + 边
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = baseAlpha),
                        Color.White.copy(alpha = baseAlpha * 0.75f),
                    )
                )
            )
            // 背景模糊 (12dp, 比 Card 略小, 因为 row 多)
            .blur(12.dp)
            .border(0.5.dp, Color.White.copy(alpha = 0.65f), shape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            ),
    ) {
        // 内层内容 (清晰)
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
 * 玻璃 TopAppBar (iOS 26 真液态玻璃, v0.3.25 修复文字糊)
 *
 * v0.3.25 老板 18:40 拍: 状态栏不沉浸, 液态玻璃假糊, 日志没了
 * 真因: v0.3.23 blur(20dp) + 白玻璃在状态栏下面, 因为没开 edge-to-edge
 *      (上一次改错了), 状态栏区域画的是白底, blur 模糊的是白底, 看起来就是"白盖子"
 * 修复:
 * 1) 恢复 enableEdgeToEdge() (MainActivity), 状态栏透明, TopAppBar 模糊真实画布
 * 2) 标题 + icon 加 text shadow (Compose Shadow) — 黑色 0.30 alpha, 偏移 0,1, 模糊 4
 * 3) 模糊半径 20dp → 12dp (太大会把内容洗成一片, 12dp 是 iOS 26 苹果默认)
 * 4) 玻璃 alpha 略降 (0.80/0.60 → 0.55/0.40), 让底下内容更可见
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
    // v0.3.25 文字阴影: 黑色 0.30 alpha, 偏移 (0, 1), 模糊 4
    val titleTextStyle = androidx.compose.ui.text.TextStyle(
        color = Color(0xFF141413),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.30f),
            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
            blurRadius = 4f,
        ),
    )
    val iconTint = Color(0xFF141413).copy(alpha = 0.95f)
    Box(modifier = Modifier.fillMaxWidth()) {
        // 外层玻璃背景: blur + 渐变 + 边 + 光泽
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(topBarShape)
                // v0.3.25 模糊半径 20dp → 12dp (iOS 26 同款)
                .blur(12.dp)
                .background(
                    Brush.verticalGradient(
                        // v0.3.25 alpha 0.80/0.60 → 0.55/0.40 (让下面内容可见)
                        colors = listOf(
                            Color(0xFFFAF9F5).copy(alpha = 0.55f),
                            Color(0xFFFAF9F5).copy(alpha = 0.40f),
                        )
                    )
                )
                // 顶部 1px 光泽
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.0f),
                            ),
                            startY = 0f,
                            endY = 2f,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, 2f),
                    )
                }
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.25f),
                        )
                    ),
                    shape = topBarShape,
                )
        )
        // 内层: TopAppBar 本身 (透明背景, 标题图标清晰 + 阴影)
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            // 老板 6/14 16:35 拍: 不用 Terracotta, 改 NearBlack
                            tint = iconTint,
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
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon()
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                // 透明, 让外层 Box 渐变显示出来
                containerColor = Color.Transparent,
                titleContentColor = Color(0xFF141413),
                navigationIconContentColor = iconTint,
                actionIconContentColor = iconTint,
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
