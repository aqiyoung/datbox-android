package com.datbox.app.ui.component

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxScope
import com.datbox.app.ui.theme.GlassBorder
import com.datbox.app.ui.theme.MiSansFamily
import com.datbox.app.ui.theme.NearBlack
import com.datbox.app.ui.theme.StoneGray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs


data class BottomTab(
    val label: String,
    val icon: ImageVector,
)



@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    FrostedGlass(shape = shape, modifier = modifier, veilAlpha = 0.30f) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassRow(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
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
        veilAlpha = 0.30f,
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
                        color = StoneGray,
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
 * 液态玻璃底部 Dock — 浮动玻璃药丸条 + 滑镜 (样式对齐 FlClash dev 分支的 NavigationDock)
 *
 * 设计参数参照 aqiyoung/FlClash@dev 的 NavigationDock (GPL-3.0, fork of chen08209/FlClash),
 * 本文件为独立实现, 未移植其代码:
 * - 一条浮动玻璃药丸条承载全部条目: 图标 + 文字 (10sp w500), 选中条目转主色
 * - 条宽 = 屏宽 × 60%, 屏内居中
 * - 滑镜: 单层化 — 与药丸同高的横向渐变光斑 (无嵌套胶囊轮廓), 宽 = 步距+8, 边缘渐隐
 * - 步距取实测条宽均分 → 任何屏宽下镜片与条目严格对齐
 * - 加速液体形变 (宽度随 squash 收缩), travel 弹簧带回弹
 * backdrop 折射 (API 33+): 内容层经 [LiquidGlassBackdrop] 的 AGSL shader —
 * 边缘 bevel 折射 + 滑镜 raised-lens 折射 + 磨砂 + 色散 + 暗边线;
 * 以下系统或 backdrop 缺省时回落 v3 平玻璃材质。
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { LensMotion(scope, selectedIndex) }
    val lensTarget = selectedIndex
    LaunchedEffect(lensTarget) { motion.animateTo(lensTarget) }
    val mPos = motion.position
    val mLift = motion.lift
    val mSquash = motion.squash

    val count = tabs.size
    val barW = DOCK_ITEM_MAX * count + DOCK_BAR_PADDING * 4
    val density = LocalDensity.current
    var pitch by remember { mutableFloatStateOf((barW - DOCK_ITEM_INSET * 2) / count) }
    val lensW = (pitch + DOCK_BAR_PADDING * 2) * (1f + mSquash)
    val lensX = DOCK_ITEM_INSET + (mPos + 0.5f) * pitch - lensW / 2f
    val lensClear = 1f - 0.5f * mLift

    var pillPos by remember { mutableStateOf(Offset.Zero) }
    val glassShader = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { android.graphics.RuntimeShader(LiquidGlassBackdrop.SHADER) }.getOrNull()
        } else {
            null
        }
    }
    val backdropEffect = remember {
        if (glassShader != null) {
            runCatching { LiquidGlassBackdrop.createGlassEffect(glassShader) }.getOrNull()
        } else {
            null
        }
    }
    SideEffect {
        if (backdrop != null && backdropEffect != null && backdrop.renderEffect !== backdropEffect) {
            runCatching { backdrop.renderEffect = backdropEffect }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(DOCK_WIDTH_FRACTION),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DOCK_BAR_HEIGHT.dp)
                    .onSizeChanged { size ->
                        val w = with(density) { size.width.toDp().value }
                        val p = (w - DOCK_ITEM_INSET * 2) / count
                        if (abs(p - pitch) > 0.01f) pitch = p
                    }
                    .onGloballyPositioned { coords -> pillPos = coords.positionInRoot() }
                    .drawBehind {
                        val layer = backdrop ?: return@drawBehind
                        if (backdropEffect == null) return@drawBehind
                        val texW = layer.size.width.toFloat()
                        val texH = layer.size.height.toFloat()
                        if (texW <= 0f || texH <= 0f) return@drawBehind
                        val pillPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(Offset.Zero, Size(size.width, size.height)),
                                    cornerRadius = CornerRadius(size.height / 2f),
                                )
                            )
                        }
                        glassShader?.apply {
                            setFloatUniform("uTexSize", texW, texH)
                            setFloatUniform("uOrigin", pillPos.x - backdropOrigin.x, pillPos.y - backdropOrigin.y)
                            setFloatUniform("uPill", size.width, size.height)
                            setFloatUniform("uRadius", size.height / 2f)
                            setFloatUniform("uBevel", with(density) { 20.dp.toPx() })
                            setFloatUniform("uRefraction", with(density) { 60.dp.toPx() })
                            setFloatUniform("uBlur", 0f)
                            setFloatUniform("uHaze", 0.72f)
                            setFloatUniform("uHazeBlur", with(density) { 15.6.dp.toPx() })
                            setFloatUniform("uRim", 0.85f)
                            setFloatUniform("uDispersion", 0.12f)
                            setFloatUniform("uSaturation", 2.1f)
                            setFloatUniform("uContrast", 0.4f)
                            setFloatUniform("uLine", with(density) { 0.67.dp.toPx() })
                            setFloatUniform("uEndShade", 0.045f)
                            setFloatUniform("uEndShadeReach", with(density) { 3.dp.toPx() })
                            setFloatUniform("uLensX", with(density) { lensX.dp.toPx() })
                            setFloatUniform("uLensW", with(density) { lensW.dp.toPx() })
                            setFloatUniform("uLensBevel", with(density) { 5.5.dp.toPx() })
                            setFloatUniform("uLensDepth", with(density) { 4.3.dp.toPx() })
                            setFloatUniform("uTint", 0.957f, 0.957f, 0.957f, 0.32f)
                        }
                        clipPath(pillPath) {
                            translate(
                                left = backdropOrigin.x - pillPos.x,
                                top = backdropOrigin.y - pillPos.y,
                            ) {
                                drawLayer(layer)
                            }
                        }
                    },
            ) {
                FrostedGlass(
                    shape = RoundedCornerShape((DOCK_BAR_HEIGHT / 2f).dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DOCK_BAR_HEIGHT.dp),
                    veilAlpha = 0f,
                    optical = backdropEffect == null,
                    tintGradient = if (backdropEffect != null) {
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF4F4F4).copy(alpha = 0.50f),
                                Color(0xFFF4F4F4).copy(alpha = 0.50f),
                            )
                        )
                    },
                ) {}
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DOCK_BAR_HEIGHT.dp)
                        .clip(RoundedCornerShape((DOCK_BAR_HEIGHT / 2f).dp)),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = lensX.dp)
                            .width(lensW.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.18f to Color.White.copy(alpha = 0.55f * lensClear),
                                        0.82f to Color.White.copy(alpha = 0.55f * lensClear),
                                        1f to Color.Transparent,
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = lensX.dp)
                            .width(lensW.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.5f to Color.Black.copy(alpha = 0.06f * lensClear),
                                        1f to Color.Transparent,
                                    )
                                )
                            )
                    )
                }
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
                }
            }
        }
    }
}


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
            modifier = Modifier.size(32.dp),
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



private const val DOCK_WIDTH_FRACTION = 0.60f


private const val DOCK_ITEM_MAX = 80f


private const val DOCK_BAR_PADDING = 4f


private const val DOCK_ITEM_INSET = 8f

private const val DOCK_BAR_HEIGHT = 62f



@Stable
internal class LensMotion(
    private val scope: CoroutineScope,
    initialIndex: Int,
) {

    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set


    var lift by mutableFloatStateOf(0f)
        private set


    var squash by mutableFloatStateOf(0f)
        private set


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


    private fun tick(dt: Double, now: Double): Boolean {
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

        squash = squashModel.track(travelPos, now, dt).toFloat()

        position = travelPos.toFloat()
        lift = liftPos.toFloat()

        return !(travelSettled && liftSettled)
    }


    private fun travelProgress(): Double {
        val span = travelTarget - travelFrom
        if (span == 0.0) return 1.0
        return ((travelPos - travelFrom) / span).coerceIn(0.0, 1.0)
    }

    companion object {
        const val TRAVEL_STIFFNESS = 280.0


        const val TRAVEL_DAMPING = 17.4
        const val LIFT_STIFFNESS = 250.0
        const val LIFT_DAMPING = 19.0
        const val HANDOVER_START = 0.92


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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrostedGlass(
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    tintGradient: Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFCFCFD), Color(0xFFE9E9EE))
    ),
    veilAlpha: Float = 0.30f,
    withShadow: Boolean = true,
    optical: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isClickable = onClick != null || onLongClick != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
    )

    Box(
        modifier = modifier
            .then(
                if (isClickable) {
                    Modifier.graphicsLayer {
                        val squeeze = 1f - 0.02f * pressProgress
                        scaleX = squeeze
                        scaleY = squeeze
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (withShadow) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.05f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (isClickable) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Box(Modifier.matchParentSize().background(tintGradient))
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = veilAlpha)))
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.0f),
                    )
                )
            )
        )
        if (optical) GlassOptics(shape)
        if (isClickable && pressProgress > 0f) {
            Box(
                Modifier.matchParentSize()
                    .background(Color.White.copy(alpha = 0.09f * pressProgress))
            )
        }
        content()
        Box(Modifier.matchParentSize().border(0.5.dp, GlassBorder, shape))
    }
}


@Composable
private fun BoxScope.GlassOptics(shape: CornerBasedShape) {
    Box(
        Modifier.matchParentSize().drawWithCache {
            val outerRadius = shape.topStart.toPx(size, this)
            val sweepCenter = Offset(size.width / 2f, size.height / 2f)
            fun rimBrush(light: Float, dark: Float) = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.30f * light),
                    0.15f to Color.Black.copy(alpha = 0.16f * dark),
                    0.38f to Color.White.copy(alpha = 0.14f * light),
                    0.62f to Color.White.copy(alpha = 0.95f * light),
                    0.85f to Color.White.copy(alpha = 0.20f * light),
                    1.00f to Color.White.copy(alpha = 0.30f * light),
                ),
                center = sweepCenter,
            )
            val rings = listOf(
                Ring(0.7.dp.toPx(), 1.2.dp.toPx(), 0.36f, 1.00f),
                Ring(2.4.dp.toPx(), 3.6.dp.toPx(), 0.16f, 0.35f),
                Ring(6.6.dp.toPx(), 5.2.dp.toPx(), 0.06f, 0.10f),
            )
            val brushes = rings.map { rimBrush(it.light, it.dark) }
            onDrawBehind {
                rings.forEachIndexed { index, ring ->
                    val inset = ring.inset
                    val innerW = size.width - inset * 2f
                    val innerH = size.height - inset * 2f
                    if (innerW <= 0f || innerH <= 0f) return@forEachIndexed
                    drawRoundRect(
                        brush = brushes[index],
                        topLeft = Offset(inset, inset),
                        size = Size(innerW, innerH),
                        cornerRadius = CornerRadius((outerRadius - inset).coerceAtLeast(0f)),
                        style = Stroke(width = ring.width),
                    )
                }
            }
        }
    )
    Box(
        Modifier.matchParentSize().background(
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.18f),
                    0.42f to Color.White.copy(alpha = 0.04f),
                    1f to Color.Transparent,
                )
            )
        )
    )
}


private data class Ring(val inset: Float, val width: Float, val light: Float, val dark: Float)


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
        veilAlpha = if (enabled) 0.56f else 0.72f,
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = NearBlack, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = NearBlack.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = MiSansFamily,
            )
        }
    }
}


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
        veilAlpha = 0.30f,
        onClick = onClick,
    ) {
        content()
    }
}


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
