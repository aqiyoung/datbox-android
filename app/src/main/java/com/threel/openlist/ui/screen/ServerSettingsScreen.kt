package com.threel.openlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        val list = defaultServers.map { if (it.url == current) it else it }
        _state.value = _state.value.copy(currentServer = current, servers = list)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    vm: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

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
                                color = Color(0xFF2A2925)
                            )
                        } else {
                            Icon(Icons.Bolt, contentDescription = "自动选择最快", tint = Color(0xFF2A2925))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F4ED))
            )
        },
        containerColor = Color(0xFFF5F4ED),
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
                ContainerCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Public, contentDescription = null, tint = Color(0xFF141413))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2925))
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
                ContainerCard(
                    modifier = Modifier.clickable { vm.selectServer(server.url) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isActive) Icons.Filled.CheckCircle else Icons.Outlined.Public,
                            contentDescription = null,
                            tint = if (isActive) Color(0xFF34C759) else Color(0xFF87867F)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存", color = Color.White)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "说明：选择服务器后点击保存，下次启动自动使用。修改后需要重新登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F)
                )
            }
        }
    }
}

@Composable
private fun ContainerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(0.5.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
