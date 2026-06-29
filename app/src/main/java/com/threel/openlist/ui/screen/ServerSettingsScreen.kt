package com.threel.openlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Error
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

data class ServerSettingsState(
    val serverUrl: String = "",
    val testing: Boolean = false,
    val testResult: Boolean? = null,
    val saved: Boolean = false
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerSettingsState())
    val state = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(serverUrl = tokenStore.serverUrlSync())
    }

    fun onServerUrl(v: String) {
        _state.value = _state.value.copy(serverUrl = v, testResult = null)
    }

    fun testConnection() {
        if (_state.value.testing || _state.value.serverUrl.isBlank()) return
        _state.value = _state.value.copy(testing = true, testResult = null)
        viewModelScope.launch {
            val ok = repo.testConnection(_state.value.serverUrl.trim())
            _state.value = _state.value.copy(testing = false, testResult = ok)
        }
    }

    fun save(onSaved: () -> Unit) {
        if (_state.value.serverUrl.isBlank()) return
        viewModelScope.launch {
            tokenStore.saveServerUrl(_state.value.serverUrl.trim())
            _state.value = _state.value.copy(saved = true)
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
                title = { Text("服务器设置", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F4ED))
            )
        },
        containerColor = Color(0xFFF5F4ED),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务器地址输入
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::onServerUrl,
                label = { Text("服务器地址") },
                placeholder = { Text("https://your-openlist-server.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2A2925),
                    unfocusedBorderColor = Color(0xFFD9D8D4),
                    focusedLabelColor = Color(0xFF2A2925),
                    unfocusedLabelColor = Color(0xFF87867F),
                    cursorColor = Color(0xFF2A2925)
                )
            )

            // 测试连接按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = vm::testConnection,
                    enabled = !state.testing && state.serverUrl.isNotBlank(),
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
            }

            // 测试结果
            when (state.testResult) {
                true -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF34C759))
                    Spacer(Modifier.width(8.dp))
                    Text("连接成功", color = Color(0xFF34C759))
                }
                false -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Error, contentDescription = null, tint = Color(0xFFFF3B30))
                    Spacer(Modifier.width(8.dp))
                    Text("无法连接，请检查地址是否正确", color = Color(0xFFFF3B30))
                }
                null -> {}
            }

            // 保存按钮
            Button(
                onClick = {
                    vm.save {
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                enabled = state.serverUrl.isNotBlank() && state.testResult == true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
            ) {
                Text("保存", color = Color.White)
            }

            // 说明
            Text(
                "说明：服务器地址是你 OpenList 服务的网址，例如 https://<your-server>。修改后需要重新登录。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF87867F)
            )
        }
    }
}
