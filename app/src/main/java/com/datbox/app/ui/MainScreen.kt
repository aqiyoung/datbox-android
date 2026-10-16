package com.datbox.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.datbox.app.ui.component.LiquidGlassBottomBar
import com.datbox.app.ui.component.BottomTab
import com.datbox.app.ui.screen.FileBrowserScreen
import com.datbox.app.ui.screen.FilePreviewScreen
import com.datbox.app.ui.screen.ProfileScreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewName by remember { mutableStateOf("") }
    var homeReset by remember { mutableIntStateOf(0) }
    var searchActive by remember { mutableStateOf(false) }

    if (previewPath != null) {
        BackHandler(enabled = true) { previewPath = null }
        FilePreviewScreen(
            remotePath = previewPath!!,
            fileName = previewName,
            onBack = { previewPath = null },
        )
        return
    }

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
                        homeReset = homeReset,
                        searchActive = searchActive,
                        onSearchActiveChange = { searchActive = it },
                    )
                    1 -> ProfileScreen(
                        onBack = { selectedTab = 0 },
                        onLogout = onLogout,
                    )
                }
            }
            LiquidGlassBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { tab ->
                    if (searchActive) searchActive = false
                    if (tab == 0) homeReset++
                    selectedTab = tab
                },
                backdrop = glassLayer,
                backdropOrigin = backdropOrigin,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
