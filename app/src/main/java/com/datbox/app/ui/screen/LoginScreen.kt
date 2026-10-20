package com.datbox.app.ui.screen

import com.datbox.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.BuildConfig
import com.datbox.app.data.api.OpenListRepository
import com.datbox.app.data.api.TokenStore
import com.datbox.app.ui.component.GlassMenu
import com.datbox.app.ui.component.GlassMenuDivider
import com.datbox.app.ui.component.GlassMenuHeader
import com.datbox.app.ui.component.GlassMenuItem
import com.datbox.app.ui.component.LiquidGlassSurface
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.ErrorRedSoft
import com.datbox.app.ui.theme.FieldBg
import com.datbox.app.ui.theme.TextBody
import com.datbox.app.ui.theme.TextFaint
import com.datbox.app.ui.theme.TextFainter
import com.datbox.app.ui.theme.TextGhost
import com.datbox.app.ui.theme.TextMuted
import com.datbox.app.ui.theme.TextStrong
import com.datbox.app.ui.theme.WarmIvory
import com.datbox.app.util.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = true,
    val serverUrl: String = TokenStore.DEFAULT_SERVER,
    val savedServers: List<String> = emptyList(),
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
        }
        viewModelScope.launch {
            tokenStore.serverUrl.collect { url ->
                _state.value = _state.value.copy(serverUrl = url)
            }
        }
        viewModelScope.launch {
            tokenStore.customServers.collect { servers ->
                _state.value = _state.value.copy(savedServers = servers.sorted())
            }
        }
    }

    /** 登录页直接换服务器, 不用进设置页 */
    fun selectServer(url: String) {
        val target = url.trim().trimEnd('/')
        if (target.isBlank() || target == _state.value.serverUrl) return
        viewModelScope.launch {
            val fromFn = withContext(Dispatchers.IO) { tokenStore.fnUrlsSync().contains(target) }
            tokenStore.saveServerUrl(target)
            tokenStore.saveFnManaged(fromFn)
            _state.value = _state.value.copy(error = null)
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
            .background(WarmIvory)
    ) {

        LiquidGlassSurface(
            onClick = { onServerSettings() },
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(10f)
                .padding(top = 44.dp, end = 20.dp)
                .size(40.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "服务器设置",
                    tint = TextBody,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 100.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // logo 自身已是圆角底 + 发丝边, 不再套玻璃, 否则背景叠两层、图形又缩一圈
            Image(
                painter = painterResource(R.drawable.datbox_logo),
                contentDescription = AppConfig.BRAND,
                modifier = Modifier.size(76.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "聚合你的云盘",
                fontSize = 22.sp,
                fontWeight = FontWeight.W600,
                color = TextStrong
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "一处登录 · 畅享所有",
                fontSize = 14.sp,
                color = TextMuted
            )

            Spacer(Modifier.height(32.dp))

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
                        color = TextStrong
                    )

                    Spacer(Modifier.height(20.dp))

                    var serverMenuOpen by remember { mutableStateOf(false) }

                    // 服务器地址和用户名并排, 换服务器不用再进设置页
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(0.42f)) {
                            LiquidGlassSurface(
                                onClick = { serverMenuOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Dns,
                                        contentDescription = null,
                                        tint = TextFaint,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        serverHost(state.serverUrl),
                                        fontSize = 13.sp,
                                        color = TextBody,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = "切换服务器",
                                        tint = TextFaint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            GlassMenu(
                                expanded = serverMenuOpen,
                                onDismissRequest = { serverMenuOpen = false },
                                width = 240.dp,
                            ) {
                                GlassMenuHeader("服务器")
                                val options = (listOf(state.serverUrl) + state.savedServers)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                options.forEach { url ->
                                    GlassMenuItem(
                                        label = serverHost(url),
                                        selected = url == state.serverUrl,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Public,
                                                contentDescription = null,
                                                tint = if (url == state.serverUrl) BrandGreen else TextFaint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            vm.selectServer(url)
                                            serverMenuOpen = false
                                        },
                                    )
                                }
                                GlassMenuDivider()
                                GlassMenuItem(
                                    label = "服务器设置",
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription = null,
                                            tint = TextFaint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        serverMenuOpen = false
                                        onServerSettings()
                                    },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.username,
                            onValueChange = vm::onUsername,
                            placeholder = { Text("用户名", color = TextFainter) },
                            singleLine = true,
                            modifier = Modifier.weight(0.58f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = FieldBg,
                                unfocusedContainerColor = FieldBg,
                                focusedBorderColor = BrandGreen,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = BrandGreen
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = vm::onPassword,
                        placeholder = { Text("密码", color = TextFainter) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = TextFaint, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = vm::togglePasswordVisible) {
                                Icon(
                                    imageVector = if (state.passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (state.passwordVisible) "隐藏密码" else "显示密码",
                                    tint = TextFaint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FieldBg,
                            unfocusedContainerColor = FieldBg,
                            focusedBorderColor = BrandGreen,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = BrandGreen
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleRememberMe() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.rememberMe) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (state.rememberMe) BrandGreen else TextGhost,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "记住我",
                            fontSize = 13.sp,
                            color = if (state.rememberMe) BrandGreen else TextMuted
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(BrandGreen, Color(0xFF12B886))
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
                        Text(state.error!!, color = ErrorRedSoft, style = MaterialTheme.typography.bodySmall)
                    }

                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "v${BuildConfig.VERSION_NAME} · ${AppConfig.BRAND}",
                fontSize = 12.sp,
                color = TextGhost
            )
        }
    }
}

/** 登录页只显示 host[:port], 省掉 https:// */
private fun serverHost(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/')
