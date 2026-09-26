package com.threel.openlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.threel.openlist.ui.component.LiquidGlassBottomBar
import com.threel.openlist.ui.component.BottomTab
import com.threel.openlist.ui.screen.FileBrowserScreen
import com.threel.openlist.ui.screen.FilePreviewScreen
import com.threel.openlist.ui.screen.ProfileScreen

/**
 * 主页面壳 — 底部导航 + 文件预览全屏模式
 *
 * 底部常驻 2 个 Tab: 首页/我的
 * 搜索和上传放右上角顶部栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewName by remember { mutableStateOf("") }

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

    Scaffold(
        bottomBar = {
            LiquidGlassBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> FileBrowserScreen(
                    onLogout = onLogout,
                    onPreview = { path, name ->
                        previewPath = path
                        previewName = name
                    },
                )
                1 -> ProfileScreen(
                    onBack = { selectedTab = 0 },
                    onLogout = onLogout,
                )
            }
        }
    }
}
