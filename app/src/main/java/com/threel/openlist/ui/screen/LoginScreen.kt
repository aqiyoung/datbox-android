package com.threel.openlist.ui.screen

import kotlin.math.min
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.api.TokenStore
import com.threel.openlist.ui.component.LiquidGlassPrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// 老板 6/14 拍: 跟 APP 图标 mipmap 一致 - 白底 + 22% 圆角 + 跟背景融合
@Composable
fun OpenListLogo(size: androidx.compose.ui.unit.Dp = 72.dp) {
    val cornerPct = 0.22f
    Image(
        painter = androidx.compose.ui.res.painterResource(id = com.threel.openlist.R.drawable.openlist_logo_official),
        contentDescription = "OpenList Logo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * cornerPct))
    )
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val passwordVisible: Boolean = false,  // 老板 6/13 拍: 加小眼睛切换密码可见
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: OpenListRepository,
    private val tokenStore: TokenStore,  // 老板 6/13: 记住账号密码
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    init {
        // 老板 6/13 拍: 自动顶填上次账号密码 (记住页)
        viewModelScope.launch {
            val username = tokenStore.lastUsername.first()
            val password = tokenStore.lastPassword.first()
            if (username.isNotEmpty()) {
                _state.value = _state.value.copy(username = username, password = password)
            }
        }
    }

    fun onUsername(v: String) { _state.value = _state.value.copy(username = v) }
    fun onPassword(v: String) { _state.value = _state.value.copy(password = v) }
    fun togglePasswordVisible() { _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun submit() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.login(_state.value.username.trim(), _state.value.password)
                .onSuccess {
                    // 老板 6/13 拍: 登录成功 -> 记住账号密码
                    tokenStore.saveLastCredentials(
                        _state.value.username.trim(),
                        _state.value.password
                    )
                    _state.value = _state.value.copy(loading = false, success = true)
                }
                .onFailure { e ->
                    // 老板 6/14: 登录失败清掉 lastPassword 避免错的旧密码反复触发 server 限流 (5 次错锁 5 min)
                    tokenStore.clearLastCredentials()
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "登录失败"
                    )
                }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // 老板 6/14 16:35 拍: 橙色丑不拉几, 改成白玻璃
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),  // 中心白亮
                        Color(0xFFF5F4ED),                  // 边缘 Parchment
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 老板 6/13 拍: 用原版 OpenList logo 替换 "OpenList" 文字
            OpenListLogo(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "聚合你的云盘",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF87867F),
            )
            Spacer(Modifier.height(64.dp))

            // 液态玻璃登录卡
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(24.dp),
            ) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::onUsername,
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPassword,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = vm::togglePasswordVisible) {
                            Icon(
                                imageVector = if (state.passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (state.passwordVisible) "隐藏密码" else "显示密码",
                                tint = Color(0xFF87867F),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error!!,
                        color = Color(0xFFB33A3A),  // 老板拍: 错误用警示红, 不用 Terracotta
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(24.dp))
                // 老板 6/14 15:25 拍: 重新设计, 不要"灯笼框" (v0.3.15 圆角 20dp 太像灯笼)
                // v0.3.16: 用 LiquidGlassPrimaryButton (12dp 圆角 + 48dp 高度 + 素雅渐变)
                LiquidGlassPrimaryButton(
                    text = if (state.loading) "登录中..." else "登录",
                    icon = if (state.loading) Icons.Filled.Refresh else Icons.Filled.Login,
                    enabled = !state.loading && state.username.isNotBlank() && state.password.isNotBlank(),
                    onClick = vm::submit,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "v0.2.1 · 三页札记",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF87867F),
            )
        }
    }
}
