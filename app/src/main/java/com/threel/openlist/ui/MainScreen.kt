package com.threel.openlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.threel.openlist.ui.component.LiquidGlassBottomBar
import com.threel.openlist.ui.component.BottomTab
import com.threel.openlist.ui.screen.FileBrowserScreen
import com.threel.openlist.ui.screen.FilePreviewScreen
import com.threel.openlist.ui.screen.ProfileScreen

/**
 * 主页面壳 — 底部 Dock + 文件预览全屏模式
 *
 * 底部 Dock (iOS 27 悬浮胶囊):
 * - 宽度贴合内容、水平居中的玻璃胶囊, 内含 [首页 / 我的] + 搜索
 * - 玻璃无自身颜色, 选中态是中性"液态镜片"补间动画
 * 顶部的搜索已移除, 搜索入口统一到底部 Dock。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewName by remember { mutableStateOf("") }
    var searchTrigger by remember { mutableIntStateOf(0) }
    var searchActive by remember { mutableStateOf(false) }

    // ── 文件预览全屏模式: 隐藏底部栏 ──
    if (previewPath != null) {
        BackHandler(enabled = true) { previewPath = null }
        FilePreviewScreen(
            remotePath = previewPath!!,
            fileName = previewName,
            onBack = { previewPath = null },
        )
        return
    }

    // ── 系统返回键: 非首页 Tab → 回到首页 ──
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val tabs = listOf(
        BottomTab("首页", Icons.Outlined.Home),
        BottomTab("我的", Icons.Outlined.Person),
    )

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 玻璃取景层: 只录制给 Dock 折射采样用; 主内容直接绘制 (drawContent) —
            // 主画面不经过任何 effect, shader/管线异常只可能影响 dock 药丸区, 拖不垮整屏;
            // 内容从 dock 玻璃下方滚过, shader 才有真实画面可折射 (对齐 FlClash backdrop 机制)
            val glassLayer = rememberGraphicsLayer()
            var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
                    .drawWithContent {
                        glassLayer.record(
                            density = this@drawWithContent,
                            layoutDirection = layoutDirection,
                            size = IntSize(size.width.toInt(), size.height.toInt()),
                        ) {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    },
            ) {
                when (selectedTab) {
                    0 -> FileBrowserScreen(
                        onLogout = onLogout,
                        onPreview = { path, name ->
                            previewPath = path
                            previewName = name
                        },
                        searchTrigger = searchTrigger,
                        searchActive = searchActive,
                        onSearchActiveChange = { searchActive = it },
                    )
                    1 -> ProfileScreen(
                        onBack = { selectedTab = 0 },
                        onLogout = onLogout,
                    )
                }
            }
            // Dock 悬浮于内容之上 (不再由 Scaffold 预留空间)
            LiquidGlassBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
                onSearchClick = {
                    if (searchActive) {
                        // 再点搜索 = 关闭搜索回首页 (toggle)
                        searchActive = false
                    } else {
                        if (selectedTab != 0) selectedTab = 0
                        searchActive = true
                        searchTrigger++
                    }
                },
                searchActive = searchActive,
                backdrop = glassLayer,
                backdropOrigin = backdropOrigin,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
