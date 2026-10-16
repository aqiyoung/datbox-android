package com.datbox.app.ui.screen

import com.datbox.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.BuildConfig
import com.datbox.app.data.api.OpenListRepository
import com.datbox.app.data.api.TokenStore
import com.datbox.app.ui.component.LiquidGlassSurface
import com.datbox.app.util.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = true,
    val serverUrl: String = "https://fn.threel.site",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: OpenListRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val username = tokenStore.lastUsername.first()
            val password = tokenStore.lastPassword.first()
            if (username.isNotEmpty()) {
                _state.value = _state.value.copy(username = username, password = password)
            }
            // 实时跟随 DataStore 里的服务器地址：从「服务器设置」页返回后自动刷新显示
            launch {
                tokenStore.serverUrl.collect { saved ->
                    _state.value = _state.value.copy(serverUrl = saved)
                }
            }
        }
    }

    fun onUsername(v: String) { _state.value = _state.value.copy(username = v) }
    fun onPassword(v: String) { _state.value = _state.value.copy(password = v) }
    fun togglePasswordVisible() { _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible) }
    fun toggleRememberMe() { _state.value = _state.value.copy(rememberMe = !_state.value.rememberMe) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun submit() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.login(_state.value.username.trim(), _state.value.password)
                .onSuccess {
                    val st = _state.value
                    if (st.rememberMe) {
                        tokenStore.saveLastCredentials(st.username.trim(), st.password)
                    } else {
                        tokenStore.clearLastCredentials()
                    }
                    _state.value = st.copy(loading = false, success = true)
                }
                .onFailure { e ->
                    tokenStore.clear()
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "登录失败")
                }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onServerSettings: () -> Unit = {},
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        // ===== 服务器按钮（右上角） =====
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(10f)
                .padding(top = 44.dp, end = 20.dp),
            horizontalAlignment = Alignment.End
        ) {
            LiquidGlassSurface(
                onClick = { onServerSettings() },
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
                    Text("服务器", fontSize = 13.sp, color = Color(0xFF333333))
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                fontSize = 11.sp,
                color = Color(0xFF999999),
                maxLines = 1
            )
        }

        // ===== 主内容 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 100.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo 卡片
            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(72.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.datbox_logo),
                        contentDescription = AppConfig.BRAND,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "聚合你的云盘",
                fontSize = 22.sp,
                fontWeight = FontWeight.W600,
                color = Color(0xFF1A1A1A)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "一处登录 · 畅享所有",
                fontSize = 14.sp,
                color = Color(0xFF999999)
            )

            Spacer(Modifier.height(32.dp))

            // ===== 登录卡片 =====
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        "欢迎回来",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF1A1A1A)
                    )

                    Spacer(Modifier.height(18.dp))

                    // 用户名
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = vm::onUsername,
                        placeholder = { Text("用户名", color = Color(0xFFBBBBBB)) },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F7FB),
                            unfocusedContainerColor = Color(0xFFF5F7FB),
                            focusedBorderColor = Color(0xFF20C997),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color(0xFF20C997)
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // 密码
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = vm::onPassword,
                        placeholder = { Text("密码", color = Color(0xFFBBBBBB)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = vm::togglePasswordVisible) {
                                Icon(
                                    imageVector = if (state.passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (state.passwordVisible) "隐藏密码" else "显示密码",
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F7FB),
                            unfocusedContainerColor = Color(0xFFF5F7FB),
                            focusedBorderColor = Color(0xFF20C997),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color(0xFF20C997)
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    // 记住我（可点击切换）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleRememberMe() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.rememberMe) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (state.rememberMe) Color(0xFF20C997) else Color(0xFFCCCCCC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "记住我",
                            fontSize = 13.sp,
                            color = if (state.rememberMe) Color(0xFF20C997) else Color(0xFF888888)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // 登录按钮（渐变绿）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF20C997), Color(0xFF12B886))
                                )
                            )
                            .clickable(enabled = !state.loading && state.username.isNotBlank() && state.password.isNotBlank()) { vm.submit() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("登录", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(state.error!!, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
                    }

                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "v${BuildConfig.VERSION_NAME} · ${AppConfig.BRAND}",
                fontSize = 12.sp,
                color = Color(0xFFCCCCCC)
            )
        }
    }
}
