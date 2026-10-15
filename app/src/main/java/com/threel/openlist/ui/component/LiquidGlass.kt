package com.threel.openlist.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import com.threel.openlist.ui.theme.BorderLight
import com.threel.openlist.ui.theme.GlassBorder
import com.threel.openlist.ui.theme.GlassShadow
import com.threel.openlist.ui.theme.GlassWhite
import com.threel.openlist.ui.theme.MiSansFamily
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

// ===== iOS 26/27 风格液态玻璃组件库 =====

/**
 * 玻璃卡 (iOS 27 Clear Glass 风格)
 *
 * 视觉要素 (自下而上):
 * 1. 中性白 → 浅灰竖向渐变底 (玻璃自身不带颜色)
 * 2. 顶部环境光 + 高光发丝 (白 0.95 → 0)
 * 3. 底部内侧暗影 (透镜边缘的折射感)
 * 4. 0.5dp 玻璃发丝边 (黑 7% alpha, 纯白背景上可见)
 * 5. 内容区 (清晰)
 *
 * iOS 27 特征:
 * - 大圆角 24dp
 * - 玻璃无自身色彩, 颜色全部来自背后 (当前背景是纯色 → 玻璃即近白磨砂)
 * - 边界靠发丝边 + 柔和投影定义, 不靠色块
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    FrostedGlass(shape = shape, modifier = modifier, veilAlpha = 0.40f) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * 玻璃行 (列表 item)
 *
 * iOS 27 特征:
 * - 圆角 16dp
 * - 中性玻璃, 选中态只提高白纱浓度 (更"实"), 不引入颜色
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
    FrostedGlass(
        shape = shape,
        modifier = modifier.fillMaxWidth(),
        veilAlpha = if (selected) 0.56f else 0.44f,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding).fillMaxWidth(),
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
 * 玻璃 TopAppBar (iOS 27 风格)
 *
 * iOS 27 特征:
 * - 底部大圆角 24dp
 * - 中性磨砂白 (无色彩)
 * - 底部发丝边 + 标题加粗 Semibold
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
    FrostedGlass(
        shape = topBarShape,
        modifier = Modifier.fillMaxWidth(),
        veilAlpha = 0.50f,
        withShadow = false,
    ) {
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
 * iOS 27 特征:
 * - 56dp 圆形
 * - 中性磨砂白 (无色彩)
 * - 0.5dp 玻璃发丝边
 */
@Composable
fun LiquidGlassFab(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    enabled: Boolean = true,
) {
    FrostedGlass(
        shape = CircleShape,
        modifier = Modifier.size(size),
        veilAlpha = if (enabled) 0.50f else 0.66f,
        onClick = if (enabled) onClick else null,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF141413).copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(26.dp).align(Alignment.Center),
        )
    }
}

/**
 * 液态玻璃底部 Dock (iOS 27 悬浮胶囊)
 *
 * iOS 27 样式要点:
 * - 宽度贴合内容的悬浮胶囊, 水平居中, 不再通栏; 玻璃体半透明白, 纯白页面上呈悬浮磨砂
 * - 选中态是全 Dock 唯一的"液态滑镜": 在 Tab 之间弹簧滑行, 行进中宽度滞后 → 拉伸/回弹的液体感
 * - 镜片底是穹顶渐变 (顶受光 → 底积影), 分隔线是上下渐隐的发丝
 * - 右侧搜索为同一条胶囊内的圆钮, 激活时用同一套镜片语言, 不引入颜色
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
    val density = LocalDensity.current
    // 每个 Tab 的实测宽度: 液态滑镜据此计算起点与宽度。首帧为空 → 镜片从 0 滑入
    val tabWidths = remember { mutableStateMapOf<Int, Dp>() }
    val tabsRowStart = 8.dp
    val targetX = tabsRowStart + (0 until selectedIndex).mapNotNull { tabWidths[it] }.sumOf { it.value }.dp
    val targetW = tabWidths[selectedIndex] ?: 0.dp
    // 位置用弹簧 (滑行带回弹), 宽度用补间 (略滞后) → 行进中被拉长、落位时回弹 = 液态
    val lensX = animateDpAsState(targetX, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "lensX").value
    val lensW = animateDpAsState(targetW, tween(320, easing = FastOutSlowInEasing), label = "lensW").value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 悬浮胶囊: 宽度贴合内容 (不 fillMaxWidth), 半透明玻璃体 + 发丝边 + 折射边
        FrostedGlass(
            shape = RoundedCornerShape(29.dp),
            modifier = Modifier.height(58.dp),
            veilAlpha = 0.30f,
            tintGradient = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.82f),
                    Color(0xFFF0F1F4).copy(alpha = 0.76f),
                )
            ),
        ) {
            Box {
                // —— 液态滑镜: 唯一的选中底, 画在 Tab 内容之下 ——
                if (lensW > 0.dp) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = lensX)
                            .width(lensW)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.60f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Black.copy(alpha = 0.05f),
                                    )
                                )
                            )
                            .border(0.5.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        DockTabItem(
                            tab = tab,
                            selected = index == selectedIndex,
                            onSizeChanged = { px -> tabWidths[index] = with(density) { px.toDp() } },
                            onClick = { onSelected(index) },
                        )
                    }
                    // 发丝分隔线: 上下渐隐
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .width(1.dp)
                            .height(22.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.14f),
                                        Color.Black.copy(alpha = 0.03f),
                                    )
                                )
                            ),
                    )
                    DockSearchButton(active = searchActive, onClick = onSearchClick)
                }
            }
        }
    }
}

/**
 * Dock 单个 Tab — 纯内容 (选中底由外层液态滑镜统一绘制, 不再各自贴底)。
 * 选中时图标微涨 (20→22dp)、间距微增、文字转 SemiBold、颜色 StoneGray → 近黑, 240ms 补间。
 * [onSizeChanged] 上报实测宽度 (像素), 供滑镜计算位置。
 */
@Composable
private fun DockTabItem(
    tab: BottomTab,
    selected: Boolean,
    onSizeChanged: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val t = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
    ).value
    val contentColor = if (selected) Color(0xFF141413) else StoneGray
    Row(
        modifier = Modifier
            .onSizeChanged { onSizeChanged(it.width) }
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp + 2.dp * t),
        )
        Spacer(Modifier.width(6.dp + 1.dp * t))
        Text(
            tab.label,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

/** Dock 右侧搜索圆钮 — 与 Tab 同一套中性镜片语言。 */
@Composable
private fun DockSearchButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    val t = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
    ).value
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                // 镜片穹顶: 顶部受光 → 底部积影, 浓度随选中度 t 补间 (液态镜片的"厚度感")
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.60f * t),
                        Color.White.copy(alpha = 0.08f * t),
                        Color.Black.copy(alpha = 0.05f * t),
                    )
                )
            )
            .border(0.5.dp, Color.Black.copy(alpha = 0.08f * t), CircleShape)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = "搜索",
            tint = if (active) Color(0xFF141413) else StoneGray,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 真·液态玻璃填充层 (所有玻璃件共用: 卡片 / 行 / 底部栏 / FAB / 按钮 / 顶栏)
 *
 * iOS 27 Clear Glass: 玻璃自身不带颜色, "玻璃感"由折射几何构成:
 * 1. 中性白 → 浅灰的竖向渐变体 (页面背景是纯色, 玻璃即近白磨砂)
 * 2. 顶部环境光 + 顶部中央径向受光池 (穹顶聚光)
 * 3. 白纱 veil 控制磨砂浓度
 * 4. 底部透镜厚度: 内侧暗影 (玻璃下缘最厚, 折射积影)
 * 5. 边缘 = 均匀定义发丝 + 方向性折射边(左上受光 → 右下背光)
 * 6. 底缘内侧反光发丝, 与暗影构成透镜"双线"
 *
 * [veilAlpha] = 磨砂浓度 (白纱): 越大越实、越遮背后的內容。
 * 不画彩色光斑、不加背景色 —— 颜色只能来自页面背景 (当前是纯色)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrostedGlass(
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    tintGradient: Brush = Brush.verticalGradient(
        colors = listOf(Color.White, Color(0xFFF0F1F4))
    ),
    veilAlpha: Float = 0.42f,
    withShadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (withShadow) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.08f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
    ) {
        // 1) 中性玻璃体: 白 → 浅灰竖向渐变 (无色彩)
        Box(Modifier.matchParentSize().background(tintGradient))
        // 2) 顶部环境光: 上缘受光, 向下渐隐
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.0f),
                    )
                )
            )
        )
        // 3) 磨砂白纱: 控制整体磨砂浓度
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = veilAlpha)))
        // 4) 底部透镜厚度: 内侧暗影 —— 玻璃下缘最厚, 折射积影
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.07f),
                        )
                    )
                )
        )
        // 5) 内容 (图标 / 文字 / 分段)
        content()
        // 6) 玻璃边缘: 均匀定义发丝(黑 7%) + 方向性折射边叠加
        Box(Modifier.matchParentSize().border(0.5.dp, GlassBorder, shape))
        Box(
            Modifier.matchParentSize().border(
                width = 0.5.dp,
                // 液态玻璃的"受光方向": 左上直接受光(亮白) → 右下背光(折射暗边),
                // 斜向渐变沿整个轮廓走, 圆角处随形状自然弯折
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.Black.copy(alpha = 0.03f),
                        Color.Black.copy(alpha = 0.13f),
                    ),
                ),
                shape = shape,
            )
        )
        // 7) 顶部受光池: 顶部中央的径向光斑(玻璃穹顶聚光), 取代原来的 3px 直线高光
        Box(
            Modifier.matchParentSize().drawBehind {
                val pool = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0f),
                    ),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width / 2.2f,
                )
                drawRect(brush = pool)
            }
        )
        // 8) 底缘内侧反光发丝: 与 4) 的暗影构成透镜"双线" —— 玻璃底边的迎光面
        Box(
            Modifier.matchParentSize().drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(0f, size.height - 2f),
                    end = Offset(size.width, size.height - 2f),
                    strokeWidth = 1f,
                )
            }
        )
    }
}

/**
 * 玻璃主操作按钮
 *
 * iOS 27 特征:
 * - 圆角 16dp
 * - 高度 48dp
 * - 中性磨砂白 (无色彩)
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
    FrostedGlass(
        shape = shape,
        modifier = modifier.fillMaxWidth().height(48.dp),
        veilAlpha = if (enabled) 0.52f else 0.70f,
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color(0xFF141413), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color(0xFF141413).copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = MiSansFamily,
            )
        }
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
    FrostedGlass(
        shape = shape,
        modifier = modifier,
        veilAlpha = 0.42f,
        onClick = onClick,
    ) {
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
 * 玻璃页面背景 — 纯色, 不加任何颜色
 *
 * iOS 27: 页面背景保持纯白, 玻璃件靠发丝边/投影/磨砂浓度区分层次;
 * 渐变、彩色光斑一律不画 (颜色只应来自内容本身)。
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
            .background(Color.White)
    ) {
        content()
    }
}
