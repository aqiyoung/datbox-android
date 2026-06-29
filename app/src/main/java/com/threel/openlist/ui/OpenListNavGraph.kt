package com.threel.openlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.api.TokenStore
import com.threel.openlist.ui.screen.AboutScreen
import com.threel.openlist.ui.screen.FileBrowserScreen
import com.threel.openlist.ui.screen.FilePreviewScreen
import com.threel.openlist.ui.screen.LoginScreen
import com.threel.openlist.ui.screen.ManagementScreen
import com.threel.openlist.ui.screen.ServerSettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import com.threel.openlist.util.TelemetryLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val repo: OpenListRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    val loggedIn = _loggedIn.asStateFlow()

    init {
        TelemetryLog.i("NavGraph", "OpenListNavGraph init, isLoggedIn check...")
        viewModelScope.launch {
            // v0.3.36: 读取持久化的服务器地址
            val serverUrl = tokenStore.serverUrl.first()
            TelemetryLog.i("NavGraph", "saved serverUrl=$serverUrl")
            val isLogin = repo.isLoggedIn()
            TelemetryLog.i("NavGraph", "isLoggedIn=$isLogin")
            _loggedIn.value = isLogin
        }
    }

    fun logout() {
        viewModelScope.launch { _loggedIn.value = false }
    }

    fun setLoggedIn() {
        _loggedIn.value = true
    }
}

@Composable
fun OpenListNavGraph(vm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val loggedIn by vm.loggedIn.collectAsState()

    when (loggedIn) {
        null -> {
            // 启动检查中 - 居中 Loading
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        true -> NavHost(nav, startDestination = "files") {
            composable("files") {
                FileBrowserScreen(
                    onLogout = { vm.logout() },
                    onAbout = { nav.navigate("about") },
                    onManagement = { nav.navigate("management") },
                    onPreview = { path, name ->
                        nav.navigate("preview/${URLEncoder.encode(path, "UTF-8")}")
                    }
                )
            }
            composable("about") {
                // 老板 6/14 拍: 加手势返回 / 系统返回键支持
                // AboutScreen onBack 是按钮回调, BackHandler 走系统返回手势
                BackHandler(enabled = true) {
                    if (!nav.popBackStack()) {
                        // 栈底了, 让系统默认处理 (退出)
                    }
                }
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable("management") {
                BackHandler(enabled = true) {
                    if (!nav.popBackStack()) {
                        // 栈底了, 让系统默认处理 (退出)
                    }
                }
                ManagementScreen(onBack = { nav.popBackStack() })
            }
            composable("server_settings") {
                BackHandler(enabled = true) { nav.popBackStack() }
                ServerSettingsScreen(onBack = { nav.popBackStack() })
            }
            // 文件预览: path 作为参数 (URL encoded)
            composable("preview/{path}") { backStackEntry ->
                BackHandler(enabled = true) { nav.popBackStack() }
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = URLDecoder.decode(encodedPath, "UTF-8")
                val fileName = path.substringAfterLast('/')
                FilePreviewScreen(
                    remotePath = path,
                    fileName = fileName,
                    onBack = { nav.popBackStack() }
                )
            }
        }
        false -> NavHost(nav, startDestination = "login") {
            composable("login") {
                LoginScreen(
                    onLoginSuccess = { vm.setLoggedIn() },
                    onServerSettings = { nav.navigate("server_settings") }
                )
            }
            composable("server_settings") {
                BackHandler(enabled = true) { nav.popBackStack() }
                ServerSettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
