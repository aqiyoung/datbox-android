package com.threel.openlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.threel.openlist.data.update.AppUpdateLauncher
import com.threel.openlist.ui.OpenListNavGraph
import com.threel.openlist.ui.theme.OpenListTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 液态玻璃 TopAppBar 需要状态栏有背景色做模糊底, 不用 edge-to-edge
        // enableEdgeToEdge()
        setContent {
            OpenListTheme {
                // 每次重组都同步状态栏图标颜色: 浅色模式深色图标, 深色模式浅色图标
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OpenListNavGraph()
                }
            }
        }
        // 启动后 500ms 检查更新, 避免主屏黑屏
        window.decorView.postDelayed({
            AppUpdateLauncher.maybeShow(this, this)
        }, 500)
    }
}
