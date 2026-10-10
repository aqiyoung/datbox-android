package com.threel.openlist.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import com.threel.openlist.ui.theme.BorderLight
import com.threel.openlist.ui.theme.GlassBorder
import com.threel.openlist.ui.theme.GlassHighlight
import com.threel.openlist.ui.theme.GlassShadow
import com.threel.openlist.ui.theme.GlassWhite
import com.threel.openlist.ui.theme.MiSansFamily
import com.threel.openlist.ui.theme.WarmWhite
import com.threel.openlist.ui.theme.StoneGray

// ===== 底部导航 Tab 定义 =====
/**
 * 底部导航 Tab
 * 放在组件库中, MainScreen 和 LiquidGlassBottomBar 共用
 */
data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

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
        // 外层: 玻璃背景（半透明，让彩色背景透出来）
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .blur(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.58f),
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
        // 外层: 玻璃背景（半透明，让彩色背景透出来）
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .blur(6.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.58f),
                        )
                    )
                )
                .border(0.5.dp, Color(0xFFE5E5EA), shape)
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
 * 液态玻璃底部导航栏 (iOS 26 / BitChord 风格)
 *
 * 布局 (参考 BitChord 的 pill + standaloneTab):
 * - 左侧玻璃胶囊: 首页 / 我的 两个分段, 选中项绿色玻璃胶囊
 * - 右侧独立圆形玻璃搜索按钮 (standaloneTab), 点击打开搜索
 * - 半透明白玻璃 + 顶部高光 + 发丝边 + 柔和投影, 整体居中悬浮
 *
 * 视觉要素:
 * - 悬浮胶囊, 距屏幕左右各 24dp, 悬在导航栏上方
 * - 半透明白玻璃 (0.88→0.70) + 顶部高光 + 发丝边 + 柔和投影
 * - 选中项: 绿色玻璃胶囊 (图标+文字一体), 未选中仅图标+灰字
 * - 搜索按钮激活时: 图标转品牌绿 + 玻璃微微提亮
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    searchActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)
    val circleShape = CircleShape
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // ── 玻璃胶囊: 首页 / 我的 ──
            Row(
                modifier = Modifier
                    .height(58.dp)
                    .clip(pillShape)
                    .shadow(
                        elevation = 10.dp,
                        shape = pillShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.08f),
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.88f),
                                Color.White.copy(alpha = 0.70f),
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
                    .border(0.5.dp, GlassBorder, pillShape),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) Color(0xFF20C997).copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = MutableInteractionSource(),
                                indication = null,
                            ) { onSelected(index) }
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) Color(0xFF20C997) else StoneGray,
                            modifier = Modifier.size(if (selected) 22.dp else 20.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tab.label,
                            color = if (selected) Color(0xFF141413) else StoneGray,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // ── 独立圆形搜索按钮 (BitChord standaloneTab 风格) ──
            val searchTint = if (searchActive) Color(0xFF20C997) else Color(0xFF141413)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(circleShape)
                    .shadow(
                        elevation = 10.dp,
                        shape = circleShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.08f),
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (searchActive) 0.92f else 0.88f),
                                Color.White.copy(alpha = if (searchActive) 0.74f else 0.70f),
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
                    .border(0.5.dp, GlassBorder, circleShape)
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                    ) { onSearchClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "搜索",
                    tint = searchTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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

/**
 * 玻璃 Surface（替代 M3 中性白 Surface 卡）
 *
 * 与 LiquidGlassCard 同源：白玻璃背景 + 顶部高光 + 0.5dp 玻璃边框。
 * 支持 onClick（可点击卡片），内容由调用方自行 padding。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .blur(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.84f),
                            Color.White.copy(alpha = 0.68f),
                        )
                    )
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(GlassHighlight, Color.White.copy(alpha = 0.0f)),
                            startY = 0f,
                            endY = 3f,
                        ),
                        size = Size(size.width, 3f),
                    )
                }
                .border(0.5.dp, GlassBorder, shape)
        )
        content()
    }
}

/**
 * 玻璃 AlertDialog（替代裸 M3 AlertDialog）
 *
 * 容器改为半透明白玻璃（0.92 alpha），其余参数与 M3 AlertDialog 对齐。
 */
@Composable
fun LiquidGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: CornerBasedShape = RoundedCornerShape(20.dp),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        modifier = modifier,
        shape = shape,
        containerColor = Color.White.copy(alpha = 0.92f),
    )
}

/**
 * 玻璃页面装饰背景
 *
 * 玻璃要有"透"的感觉，背后必须有色彩。此背景提供:
 * 1. 极淡的垂直渐变底 (淡绿 → 白 → 暖灰)
 * 2. 数个径向渐变彩色光斑 (绿/蓝)，压在玻璃卡后面
 *
 * 用法: 作为页面最外层容器，内容叠在其上。
 */
@Composable
fun GlassDecorBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE9F6F0),  // 淡绿
                        Color(0xFFFDFDFC),  // 近白
                        Color(0xFFF5F3EC),  // 暖灰
                    )
                )
            )
    ) {
        // 光斑: 左上大绿斑
        Box(
            modifier = Modifier
                .offset(x = (-90).dp, y = (-70).dp)
                .size(300.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF20C997).copy(alpha = 0.20f),
                            Color(0xFF20C997).copy(alpha = 0.0f),
                        )
                    )
                )
        )
        // 光斑: 右侧蓝斑
        Box(
            modifier = Modifier
                .offset(x = 240.dp, y = 240.dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A7BD5).copy(alpha = 0.12f),
                            Color(0xFF3A7BD5).copy(alpha = 0.0f),
                        )
                    )
                )
        )
        // 光斑: 左下绿斑
        Box(
            modifier = Modifier
                .offset(x = (-70).dp, y = 600.dp)
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF20C997).copy(alpha = 0.14f),
                            Color(0xFF20C997).copy(alpha = 0.0f),
                        )
                    )
                )
        )
        // 光斑: 右下小绿斑
        Box(
            modifier = Modifier
                .offset(x = 270.dp, y = 700.dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF20C997).copy(alpha = 0.10f),
                            Color(0xFF20C997).copy(alpha = 0.0f),
                        )
                    )
                )
        )
        content()
    }
}
