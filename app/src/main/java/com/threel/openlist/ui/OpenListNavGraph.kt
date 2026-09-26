package com.threel.openlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.threel.openlist.ui.screen.FilePreviewScreen
import com.threel.openlist.ui.screen.LoginScreen
import com.threel.openlist.ui.screen.ServerSettingsScreen
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
            val serverUrl = tokenStore.serverUrl.first()
            TelemetryLog.i("NavGraph", "saved serverUrl=$serverUrl")
            val isLogin = repo.isLoggedIn()
            TelemetryLog.i("NavGraph", "isLoggedIn=$isLogin")
            _loggedIn.value = isLogin
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            _loggedIn.value = false
        }
    }

    fun setLoggedIn() {
        _loggedIn.value = true
    }
}

@Composable
fun OpenListNavGraph(vm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val loggedIn by vm loggedIn.collectAsState()

    when (loggedIn) {
        null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        true -> NavHost(nav, startDestination = "main") {
            composable("main") {
                MainScreen(onLogout = { vm.logout() })
            }
            // 登录后不再需要单独的预览路由 — MainScreen 内部管理
            composable("preview/{path}") { backStackEntry ->
                BackHandler(enabled = true) { nav.popBackStack() }
                val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
                val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
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