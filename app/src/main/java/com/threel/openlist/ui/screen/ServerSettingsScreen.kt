package com.threel.openlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.api.TokenStore
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServerSettingsEntryPoint {
    fun tokenStore(): TokenStore
    fun openListRepository(): OpenListRepository
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(context, ServerSettingsEntryPoint::class.java)
    val tokenStore = entryPoint.tokenStore()
    val repo = entryPoint.openListRepository()

    var serverUrl by remember { mutableStateOf(tokenStore.serverUrlSync()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

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
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    testResult = null
                },
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
                    onClick = {
                        if (serverUrl.isNotBlank()) {
                            testing = true
                            testResult = null
                            scope.launch {
                                val ok = repo.testConnection(serverUrl.trim())
                                testResult = ok
                                testing = false
                            }
                        }
                    },
                    enabled = !testing && serverUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2925))
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (testing) "测试中..." else "测试连接", color = Color.White)
                }
            }

            // 测试结果
            when (testResult) {
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
                    if (serverUrl.isNotBlank()) {
                        tokenStore.run {
                            kotlinx.coroutines.runBlocking { saveServerUrl(serverUrl.trim()) }
                        }
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                enabled = serverUrl.isNotBlank() && testResult == true,
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
