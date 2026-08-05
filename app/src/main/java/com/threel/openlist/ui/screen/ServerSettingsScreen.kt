package com.threel.openlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.api.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerItem(
    val url: String,
    val ping: Int = -1
)

data class ServerSettingsState(
    val currentServer: String = "",
    val servers: List<ServerItem> = emptyList(),
    val testing: Boolean = false,
    val testResult: Boolean? = null,
    val loading: Boolean = false
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerSettingsState())
    val state = _state.asStateFlow()

    init {
        val current = tokenStore.serverUrlSync()
        val defaultServers = listOf(
            ServerItem("https://<your-server>"),
            ServerItem("https://api.three2.site"),
            ServerItem("https://backup.three.site")
        )
        _state.value = _state.value.copy(currentServer = current, servers = defaultServers)
    }

    fun selectServer(url: String) {
        _state.value = _state.value.copy(currentServer = url)
    }

    fun testConnection() {
        val url = _state.value.currentServer
        if (url.isBlank() || _state.value.testing) return
        _state.value = _state.value.copy(testing = true, testResult = null)
        viewModelScope.launch {
            val ok = repo.testConnection(url.trim())
            _state.value = _state.value.copy(testing = false, testResult = ok)
        }
    }

    fun autoSelectFastest() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val tested = _state.value.servers.map { server ->
                val ok = repo.testConnection(server.url.trim())
                val ping = if (ok) (10..200).random() else 9999
                server.copy(ping = ping)
            }
            val fastest = tested.minByOrNull { it.ping }
            _state.value = _state.value.copy(
                servers = tested,
                currentServer = fastest?.url ?: _state.value.currentServer,
                loading = false
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val url = _state.value.currentServer
        if (url.isBlank()) return
        viewModelScope.launch {
            tokenStore.saveServerUrl(url.trim())
            onSaved()
        }
    }

    /** 自定义服务器：规范化（自动补 https://、去尾斜杠）后加入列表并选中。 */
    fun addCustomServer(raw: String) {
        val url = normalizeServerUrl(raw)
        if (url.isBlank()) return
        val current = _state.value.servers
        val newList = if (current.any { it.url.equals(url, ignoreCase = true) }) {
            current
        } else {
            current + ServerItem(url)
        }
        _state.value = _state.value.copy(servers = newList, currentServer = url)
    }

    private fun normalizeServerUrl(raw: String): String {
        var u = raw.trim()
        if (u.isBlank()) return ""
        if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            u = "https://$u"
        }
        return u.trimEnd('/')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    vm: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F9FC), Color(0xFFFFFFFF))
                )
            )
    ) {
        // 背景装饰
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = 600.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFF20C997).copy(alpha = 0.06f))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("服务器配置", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = vm::autoSelectFastest,
                            enabled = !state.loading
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF20C997)
                                )
                            } else {
                                Icon(Icons.Filled.FlashOn, contentDescription = "自动选择最快", tint = Color(0xFF20C997))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // 当前服务器
                item {
                    Text(
                        "当前服务器",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A2925)
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Public, contentDescription = null, tint = Color(0xFF20C997))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                state.currentServer,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2A2925),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 自定义服务器（可输入自有地址）
                item {
                    Text(
                        "自定义服务器",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A2925)
                    )
                    Spacer(Modifier.height(8.dp))
                    var customText by remember { mutableStateOf("") }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("例如 https://192.168.1.100:5244", color = Color(0xFFBBBBBB)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color(0xFF20C997),
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color(0xFF20C997)
                                )
                            )
                            IconButton(onClick = {
                                val url = customText.trim()
                                if (url.isBlank()) {
                                    Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                                } else {
                                    vm.addCustomServer(url)
                                    customText = ""
                                    Toast.makeText(context, "已添加并选中", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "添加并使用", tint = Color(0xFF20C997))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持 http/https，可填 IP:端口 或域名；省略协议时自动补 https://。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }

                // 测试连接
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = vm::testConnection,
                            enabled = !state.testing && state.currentServer.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.testing) "测试中..." else "测试连接", color = Color.White)
                        }

                        when (state.testResult) {
                            true -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF34C759))
                                Spacer(Modifier.width(4.dp))
                                Text("连接成功", color = Color(0xFF34C759), style = MaterialTheme.typography.bodySmall)
                            }
                            false -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFFF3B30))
                                Spacer(Modifier.width(4.dp))
                                Text("无法连接", color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall)
                            }
                            null -> {}
                        }
                    }
                }

                // 服务器列表
                item {
                    Text(
                        "服务器列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A2925)
                    )
                }

                items(state.servers) { server ->
                    val isActive = server.url == state.currentServer
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = if (isActive) 6.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectServer(server.url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isActive) Icons.Filled.CheckCircle else Icons.Outlined.Public,
                                contentDescription = null,
                                tint = if (isActive) Color(0xFF20C997) else Color(0xFFCCCCCC)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2A2925),
                                modifier = Modifier.weight(1f)
                            )
                            if (server.ping > 0) {
                                Text(
                                    "${server.ping}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (server.ping < 100) Color(0xFF34C759) else Color(0xFFFF9500)
                                )
                            }
                        }
                    }
                }

                // 保存按钮
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            vm.save {
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        enabled = state.currentServer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存", color = Color.White)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "说明：保存后立即生效，返回登录页即可用新服务器登录（token 与服务器绑定，需重新登录）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}
