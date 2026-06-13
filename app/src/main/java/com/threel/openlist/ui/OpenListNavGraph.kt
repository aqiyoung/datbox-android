package com.threel.openlist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.ui.screen.AboutScreen
import com.threel.openlist.ui.screen.FileBrowserScreen
import com.threel.openlist.ui.screen.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _loggedIn = MutableStateFlow<Boolean?>(null)  // null=unknown
    val loggedIn = _loggedIn.asStateFlow()

    init {
        viewModelScope.launch { _loggedIn.value = repo.isLoggedIn() }
    }

    fun logout() {
        viewModelScope.launch {
            // 清 token
            // 这里简化：调 TokenStore.clear()
            _loggedIn.value = false
        }
    }
}

@Composable
fun OpenListNavGraph(vm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val loggedIn by vm.loggedIn.collectAsState()

    when (loggedIn) {
        null -> { /* 启动检查中 */ }
        true -> NavHost(nav, startDestination = "files") {
            composable("files") {
                FileBrowserScreen(
                    onLogout = { vm.logout() },
                    onAbout = { nav.navigate("about") }
                )
            }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
        false -> NavHost(nav, startDestination = "login") {
            composable("login") {
                LoginScreen(onLoginSuccess = { vm.loggedIn.value.let { _loggedIn2 -> /* 不工作 */ } })
            }
        }
    }
}
