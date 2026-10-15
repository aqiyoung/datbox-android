package com.threel.openlist.ui.component

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
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
import com.threel.openlist.ui.theme.NearBlack
import com.threel.openlist.ui.theme.StoneGray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

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
 * 液态玻璃底部 Dock — 浮动玻璃药丸条 + 滑镜 (样式对齐 FlClash dev 分支的 NavigationDock)
 *
 * 设计参数参照 aqiyoung/FlClash@dev 的 NavigationDock (GPL-3.0, fork of chen08209/FlClash),
 * 本文件为独立实现, 未移植其代码:
 * - 一条浮动玻璃药丸条承载全部条目: 图标 + 文字 (10sp w500), 选中条目转主色
 * - 条宽 = 条目数×86 + 16, 屏内居中, 左右边距 21
 * - 滑镜: 宽 = 步距+8 (每侧悬出 4), 静高 54; 抬升每侧充气 8, 越出部分由药丸形裁剪 (同 FlClash)
 * - 步距取实测条宽均分 → 任何屏宽下镜片与条目严格对齐
 * - 形变纵横比 1.4 (出发拉伸 / 刹车压扁), travel 弹簧带回弹
 * - 搜索是条内第三个条目 (此前独立摆放的搜索屡被报告丢失)
 * 简化 (相对 FlClash): 无 shader 折射/色差、无悬停磁吸与按压辉光; 玻璃材质沿用 FrostedGlass v2。
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
    // 滑镜运动学: 位置弹簧(带回弹) + 点按抬升(充气) + 加速液体形变, 逐帧驱动
    val scope = rememberCoroutineScope()
    val motion = remember { LensMotion(scope, selectedIndex) }
    LaunchedEffect(selectedIndex) { motion.animateTo(selectedIndex) }
    val mPos = motion.position
    val mLift = motion.lift
    val mSquash = motion.squash

    // iOS 式排布: 步距按条目数均分; 首帧用静态条宽公式, 随后以实测条宽为准 —
    // 保证任何屏宽/约束行为下滑镜与条目严格对齐 (此前静态假设 274 曾致错位)
    val count = tabs.size + 1 // 条目 = Tab + 搜索
    val barW = DOCK_ITEM_MAX * count + DOCK_BAR_PADDING * 4
    val density = LocalDensity.current
    var pitch by remember { mutableFloatStateOf((barW - DOCK_ITEM_INSET * 2) / count) }
    val lensW = (pitch + DOCK_BAR_PADDING * 2) * (1f + mSquash)
    val lensH =
        (DOCK_BAR_HEIGHT - DOCK_BAR_PADDING * 2 + DOCK_LENS_GROWTH * 2 * mLift) *
            (1f - mSquash * DOCK_LENS_JELLY_ASPECT)
    val lensClear = 1f - 0.5f * mLift // 抬升时镜片转清 (FlClash: 升起后是清玻璃)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = DOCK_EDGE_MARGIN.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = barW.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            // 玻璃药丸条 — 三层分层: 条体 → 滑镜 → 条目
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DOCK_BAR_HEIGHT.dp)
                    .onSizeChanged { size ->
                        // 实测条宽 → 步距, 与条目行同一坐标系
                        val w = with(density) { size.width.toDp().value }
                        val p = (w - DOCK_ITEM_INSET * 2) / count
                        if (abs(p - pitch) > 0.01f) pitch = p
                    },
            ) {
                // 1) 条体: 中性玻璃药丸
                FrostedGlass(
                    shape = RoundedCornerShape((DOCK_BAR_HEIGHT / 2f).dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DOCK_BAR_HEIGHT.dp),
                    veilAlpha = 0.22f,
                    tintGradient = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.62f),
                            Color(0xFFF4F4F4).copy(alpha = 0.46f),
                        )
                    ),
                ) {}
                // 2) 液态滑镜: 在条体之上、条目之下; 裁剪在药丸形内 (同 FlClash 由 Material 裁剪) —
                //    抬升越出药丸的部分被裁掉, 端点条目不会戳出圆角
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DOCK_BAR_HEIGHT.dp)
                        .clip(RoundedCornerShape((DOCK_BAR_HEIGHT / 2f).dp)),
                ) {
                    val lensX = DOCK_ITEM_INSET + (mPos + 0.5f) * pitch - lensW / 2f
                    val lensY = (DOCK_BAR_HEIGHT - lensH) / 2f
                    val lensShape = RoundedCornerShape((lensH / 2f).dp)
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = lensX.dp, y = lensY.dp)
                            .width(lensW.dp)
                            .height(lensH.dp)
                            .clip(lensShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.60f * lensClear),
                                        Color.White.copy(alpha = 0.08f * lensClear),
                                        Color.Black.copy(alpha = 0.05f * lensClear),
                                    )
                                )
                            )
                            .border(0.5.dp, Color.Black.copy(alpha = 0.09f), lensShape),
                    )
                }
                // 3) 条目: 均分步距, 图标 + 文字
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DOCK_BAR_HEIGHT.dp)
                        .padding(horizontal = DOCK_ITEM_INSET.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        DockTabItem(
                            icon = tab.icon,
                            label = tab.label,
                            tinted = index == selectedIndex,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onSelected(index) },
                        )
                    }
                    DockTabItem(
                        icon = Icons.Outlined.Search,
                        label = "搜索",
                        tinted = searchActive,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onSearchClick,
                    )
                }
            }
        }
    }
}

/**
 * Dock 条目 — 图标 + 文字 (FlClash/iOS 式), 选中时整体转主色 (240ms 补间)。
 */
@Composable
private fun DockTabItem(
    icon: ImageVector,
    label: String,
    tinted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = animateFloatAsState(
        targetValue = if (tinted) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
    ).value
    val tint = lerp(StoneGray, NearBlack, t)
    Column(
        modifier = modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
        ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            color = tint,
        )
    }
}

// ===== Dock 排布 (参数对齐 FlClash dev 的 NavigationDock) =====

/** 条目最大宽度: 条宽 = 条目数×86 + 内边距×4, 屏内居中 */
private const val DOCK_ITEM_MAX = 86f

/** 条内边距: 滑镜静止时悬出条目每侧 4 */
private const val DOCK_BAR_PADDING = 4f

/** 条目行内边距 = 外 4 + 内 4 (FlClash 双层内缩) → 端点镜片与药丸边缘留 4 */
private const val DOCK_ITEM_INSET = 8f

private const val DOCK_BAR_HEIGHT = 62f

/** 条体距屏幕边缘 */
private const val DOCK_EDGE_MARGIN = 21f

/** 滑镜抬升时每侧充气高度 (分层绘制, 镜片可越出条体) */
private const val DOCK_LENS_GROWTH = 8f

/** 形变纵横比: 出发时宽 ×(1+d), 高 ×(1-d×1.4) — FlClash jellyAspect */
private const val DOCK_LENS_JELLY_ASPECT = 1.4f

// ===== 液态滑镜运动学 (移植) =====

/**
 * 滑镜运动学 — 移植自 BitChord (github.com/kushagrasinghx/BitChord) 的 GlassPillMotion,
 * 其物理源自 liquid_glass_easy 的 LiquidGlassLensMotion (MIT, Copyright (c) 2025 Ahmed Gamil)。
 *
 * 三路弹簧/模型:
 * - travel: 位置弹簧 (欠阻尼 → 落位回弹)
 * - lift: 点按即"抬起"镜片 (弹簧过冲再回落), 旅程中保持, 行进 92% 后释放 → 落地放气
 * - squash: 对位置二次差分 → 出发拉伸 / 刹车压扁的液体形变, 宽 ×(1+d) 高 ×(1-d)
 *
 * 相对 BitChord 的简化: 不支持拖拽 (Tab 只点按); 无背景折射 (需 vendored backdrop 库, 另立工作项)。
 *
 * @param initialIndex 初始选中 Tab (位置以 tab 步长为单位, 0 = 第一个)
 */
@Stable
internal class LensMotion(
    private val scope: CoroutineScope,
    initialIndex: Int,
) {
    /** 镜片位置, 以 tab 步长为单位 (0 = 第一个) */
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    /** 抬升度 0..1 (欠阻尼弹簧, 会过冲) */
    var lift by mutableFloatStateOf(0f)
        private set

    /** 加速形变: 宽 ×(1+squash), 高 ×(1-squash); 出发为正 (拉伸), 刹车为负 (压扁) */
    var squash by mutableFloatStateOf(0f)
        private set

    /** 当前选中 Tab */
    var index: Int = initialIndex
        private set

    private var travelPos = initialIndex.toDouble()
    private var travelVel = 0.0
    private var travelFrom = travelPos
    private var travelTarget = travelPos

    private var lifted = false
    private var liftPos = 0.0
    private var liftVel = 0.0

    private var running = false
    private val squashModel = PillSquashModel()

    /** 选中了新 Tab: 抬起镜片并弹簧滑行过去。重选当前 Tab 是 no-op。 */
    fun animateTo(next: Int) {
        if (next == index) return
        index = next
        lifted = true
        travelFrom = travelPos
        travelTarget = next.toDouble()
        squashModel.start()
        start()
    }

    private fun start() {
        if (running) return
        running = true
        scope.launch {
            var last = -1L
            try {
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = if (last < 0) 0.0 else (now - last) / 1e9
                    last = now
                    if (!tick(dt, now / 1e9)) break
                }
            } finally {
                running = false
            }
        }
    }

    /** 一帧的全部。全部沉降后返回 false 结束循环。 */
    private fun tick(dt: Double, now: Double): Boolean {
        // 1) 位置弹簧
        var travelSettled = true
        if (travelPos != travelTarget) {
            val r = springStep(travelPos, travelVel, travelTarget, dt, TRAVEL_STIFFNESS, TRAVEL_DAMPING)
            travelPos = r.first
            travelVel = r.second
            travelSettled = abs(travelPos - travelTarget) < 0.003 && abs(travelVel) < 0.05
            if (travelSettled) {
                travelPos = travelTarget
                travelVel = 0.0
            }
        }

        // 2) 抬升: 旅程保持, 行进 >=92% 落地释放 → 弹簧回落时先瘪后涨
        if (travelSettled || travelProgress() >= HANDOVER_START) lifted = false
        val liftTarget = if (lifted) 1.0 else 0.0
        springStep(liftPos, liftVel, liftTarget, dt, LIFT_STIFFNESS, LIFT_DAMPING).let {
            liftPos = it.first
            liftVel = it.second
        }
        if (!lifted && abs(liftPos) < 0.0008 && abs(liftVel) < 0.01) {
            liftPos = 0.0
            liftVel = 0.0
        }
        val liftSettled = liftPos == 0.0

        // 3) 液体形变: 采样本帧位置, 二次差分平滑
        squash = squashModel.track(travelPos, now, dt).toFloat()

        // 发布
        position = travelPos.toFloat()
        lift = liftPos.toFloat()

        return !(travelSettled && liftSettled)
    }

    /** 本次行程的完成度 0..1 */
    private fun travelProgress(): Double {
        val span = travelTarget - travelFrom
        if (span == 0.0) return 1.0
        return ((travelPos - travelFrom) / span).coerceIn(0.0, 1.0)
    }

    companion object {
        const val TRAVEL_STIFFNESS = 280.0

        /** FlClash travel (400ms, 回弹 0.15 ≈ ζ0.52) — 比临界阻尼更活, 落位带回弹 */
        const val TRAVEL_DAMPING = 17.4
        const val LIFT_STIFFNESS = 250.0
        const val LIFT_DAMPING = 19.0
        const val HANDOVER_START = 0.92

        /** 半隐式欧拉弹簧积分, 240Hz 子步 (帧间隔大时切细步保稳定) */
        fun springStep(
            x: Double,
            vel: Double,
            target: Double,
            dt: Double,
            stiffness: Double,
            damping: Double,
        ): Pair<Double, Double> {
            var t = dt
            var px = x
            var pv = vel
            while (t > 0) {
                val step = if (t > 1 / 240.0) 1 / 240.0 else t
                val accel = -stiffness * (px - target) - damping * pv
                pv += accel * step
                px += pv * step
                t -= step
            }
            return px to pv
        }
    }
}

/**
 * 位置的二次差分 → 加速形变。窗口内平均加速度 × 灵敏度, 限幅后按响应时间趋近。
 * 出发时位置加速 → 形变为正 (宽拉伸/高压扁); 刹车时为负 (宽压扁/高拉伸)。
 * 常量同 BitChord/liquid_glass_easy。
 */
private class PillSquashModel {
    private val history = ArrayList<Pair<Double, Double>>()
    private var tracking = false
    private var value = 0.0

    fun start() {
        history.clear()
        value = 0.0
        tracking = true
    }

    fun track(x: Double, now: Double, dt: Double): Double {
        if (!tracking) return value
        history.add(x to now)
        history.removeAll { it.second < now - SAMPLE_WINDOW }
        val raw = (averageAcceleration() * SENSITIVITY).coerceIn(-MAX_DEFORMATION, MAX_DEFORMATION)
        value += (raw - value) * (dt / RESPONSE_TIME).coerceIn(0.0, 1.0)
        return value
    }

    private fun averageAcceleration(): Double {
        if (history.size < 3) return 0.0
        val velocities = ArrayList<Pair<Double, Double>>(history.size)
        for (i in 1 until history.size) {
            val dt = history[i].second - history[i - 1].second
            if (dt <= 0) continue
            velocities.add(
                (history[i].first - history[i - 1].first) / dt to
                    (history[i].second + history[i - 1].second) / 2,
            )
        }
        if (velocities.size < 2) return 0.0
        var total = 0.0
        var count = 0
        for (i in 1 until velocities.size) {
            val dt = velocities[i].second - velocities[i - 1].second
            if (dt <= 0) continue
            total += (velocities[i].first - velocities[i - 1].first) / dt
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    companion object {
        const val SAMPLE_WINDOW = 0.3
        const val SENSITIVITY = 0.00007
        const val MAX_DEFORMATION = 0.18
        const val RESPONSE_TIME = 0.18
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
