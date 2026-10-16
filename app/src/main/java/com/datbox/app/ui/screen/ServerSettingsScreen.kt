package com.datbox.app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.data.api.OpenListRepository
import com.datbox.app.ui.component.LiquidGlassAlertDialog
import com.datbox.app.ui.component.LiquidGlassSurface
import com.datbox.app.data.api.ServerProbe
import com.datbox.app.data.api.TokenStore
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.DangerRed
import com.datbox.app.ui.theme.SuccessGreen
import com.datbox.app.ui.theme.TextFainter
import com.datbox.app.ui.theme.TextGhost
import com.datbox.app.ui.theme.TextMuted
import com.datbox.app.ui.theme.TextStrong
import com.datbox.app.ui.theme.WarningOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ServerItem(
    val url: String,
    val isPreset: Boolean = false,
    val probe: ServerProbe? = null  // null = 未测
)

data class ServerSettingsState(
    val currentServer: String = "",
    val servers: List<ServerItem> = emptyList(),
    val testing: Boolean = false,
    val probe: ServerProbe? = null,  // 当前服务器最新测速结果，null=未测
    val loading: Boolean = false
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerSettingsState())
    val state = _state.asStateFlow()

    private val presetServers = listOf(
        ServerItem("https://fn.threel.site", isPreset = true),
        ServerItem("https://api.three2.site", isPreset = true),
        ServerItem("https://backup.three.site", isPreset = true)
    )

    init {
        // runBlocking 读 DataStore 不能在主线程: 挪进 IO 协程, 读完再组装初始 state
        viewModelScope.launch {
            val (current, custom) = withContext(Dispatchers.IO) {
                tokenStore.serverUrlSync() to tokenStore.customServersSync().map { ServerItem(it) }
            }
            val all = presetServers + custom
            // 若持久化的当前服务器不在列表中（例如被删），回退到首个可用服务器
            val resolved = if (current.isBlank() || all.none { it.url.equals(current, ignoreCase = true) }) {
                all.firstOrNull()?.url ?: TokenStore.DEFAULT_SERVER
            } else {
                current
            }
            _state.value = _state.value.copy(currentServer = resolved, servers = all)
        }
    }

    fun selectServer(url: String) {
        _state.value = _state.value.copy(currentServer = url)
    }

    fun testConnection() {
        val url = _state.value.currentServer
        if (url.isBlank() || _state.value.testing) return
        _state.value = _state.value.copy(testing = true, probe = null)
        viewModelScope.launch {
            val result = repo.testConnection(url.trim())
            _state.value = _state.value.copy(testing = false, probe = result)
        }
    }

    fun autoSelectFastest() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val tested = _state.value.servers.map { server ->
                val p = repo.testConnection(server.url.trim())
                server.copy(probe = p)
            }
            val fastest = tested
                .filter { it.probe?.reachable == true }
                .minByOrNull { it.probe?.bestMs ?: Long.MAX_VALUE }
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

    /** 自定义服务器：规范化（自动补 https://、去尾斜杠）后加入列表并选中，并持久化到 DataStore。 */
    fun addCustomServer(raw: String) {
        val url = normalizeServerUrl(raw)
        if (url.isBlank()) return
        val current = _state.value.servers
        if (current.any { it.url.equals(url, ignoreCase = true) }) {
            // 已存在：仅切换选中，不重复入库
            _state.value = _state.value.copy(currentServer = url)
            return
        }
        _state.value = _state.value.copy(servers = current + ServerItem(url), currentServer = url)
        viewModelScope.launch { tokenStore.addCustomServer(url) }
    }

    /** 删除自定义服务器（预设不可删）。若删掉的是当前选中，回退到首个服务器。 */
    fun deleteServer(url: String) {
        viewModelScope.launch { tokenStore.removeCustomServer(url) }
        val remaining = _state.value.servers.filter { !it.url.equals(url, ignoreCase = true) }
        val newCurrent = if (_state.value.currentServer.equals(url, ignoreCase = true)) {
            remaining.firstOrNull()?.url ?: TokenStore.DEFAULT_SERVER
        } else {
            _state.value.currentServer
        }
        _state.value = _state.value.copy(servers = remaining, currentServer = newCurrent)
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
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("服务器配置", fontWeight = FontWeight.Bold, color = TextStrong) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = TextStrong)
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
                                    color = BrandGreen
                                )
                            } else {
                                Icon(Icons.Filled.FlashOn, contentDescription = "自动选择最快", tint = BrandGreen)
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
                        color = TextStrong
                    )
                    Spacer(Modifier.height(8.dp))
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Public, contentDescription = null, tint = BrandGreen)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                state.currentServer,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextStrong,
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
                        color = TextStrong
                    )
                    Spacer(Modifier.height(8.dp))
                    var customText by remember { mutableStateOf("") }
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("例如 https://192.168.1.100:5244", color = TextFainter) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = BrandGreen,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = BrandGreen
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
                                Icon(Icons.Filled.Add, contentDescription = "添加并使用", tint = BrandGreen)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持 http/https，可填 IP:端口 或域名；省略协议时自动补 https://。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
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
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
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

                        when (val p = state.probe) {
                            null -> {}
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (p.reachable) Icons.Filled.Check else Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = if (p.reachable) SuccessGreen else DangerRed
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    buildString {
                                        if (p.reachable) {
                                            append("连接成功")
                                            val parts = mutableListOf<String>()
                                            p.ipv4Ms?.let { parts.add("IPv4 ${it}ms") }
                                            p.ipv6Ms?.let { parts.add("IPv6 ${it}ms") }
                                            if (parts.isNotEmpty()) append(" (${parts.joinToString(" / ")})")
                                        } else {
                                            append("无法连接")
                                        }
                                    },
                                    color = if (p.reachable) SuccessGreen else DangerRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // 服务器列表
                item {
                    Text(
                        "服务器列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextStrong
                    )
                }

                items(state.servers) { server ->
                    val isActive = server.url == state.currentServer
                    LiquidGlassSurface(
                        onClick = { vm.selectServer(server.url) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isActive) Icons.Filled.CheckCircle else Icons.Outlined.Public,
                                contentDescription = null,
                                tint = if (isActive) BrandGreen else TextGhost
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextStrong,
                                modifier = Modifier.weight(1f)
                            )
                            server.probe?.let { p ->
                                if (p.reachable) {
                                    Text(
                                        buildString {
                                            p.ipv4Ms?.let { append("v4:${it} ") }
                                            p.ipv6Ms?.let { append("v6:${it}") }
                                        }.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if ((p.bestMs ?: 9999) < 100) SuccessGreen else WarningOrange
                                    )
                                } else {
                                    Text(
                                        "不可达",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DangerRed
                                    )
                                }
                            }
                            if (!server.isPreset) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { pendingDelete = server.url }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除服务器",
                                        tint = DangerRed
                                    )
                                }
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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存", color = Color.White)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "说明：自定义服务器会自动保存，下次打开仍在；点击右侧删除图标可移除自定义服务器（预设不可删）。测速同时探测 IPv4/IPv6 双栈，任一可达即视为可连接。保存当前服务器后立即生效，返回登录页即可登录（token 与服务器绑定，需重新登录）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            if (pendingDelete != null) {
                LiquidGlassAlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("删除服务器") },
                    text = { Text("确定要删除 $pendingDelete 吗？删除后需重新选择当前服务器。") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteServer(pendingDelete!!)
                            pendingDelete = null
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("删除", color = DangerRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}
