package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.data.update.AppUpdateInfo
import com.threel.openlist.data.update.AppUpdateManager
import com.threel.openlist.util.AppConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 老板 6/13 拍: About 页面 in-app 渲染 changelog (不再跳 GitHub)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changelog by remember { mutableStateOf<ChangelogData?>(null) }
    var changelogLoading by remember { mutableStateOf(true) }
    var changelogError by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { fetchChangelog() }
                changelog = data
                changelogLoading = false
            } catch (e: Exception) {
                changelogError = e.message
                changelogLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // 品牌 header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppConfig.BRAND + "云盘",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = AppConfig.BRAND_SUBTITLE,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = AppConfig.fullVersionString(context),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 检查更新 + 日志 按钮 (用 LiquidGlass 风格)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        title = "检查更新",
                        subtitle = if (updateInfo == null) "点击检查新版本" else "发现新版本 ${updateInfo?.versionName}，点击下载",
                        icon = Icons.Filled.Update,
                        onClick = {
                            updateChecking = true
                            scope.launch {
                                try {
                                    val manager = EntryPointAccessors.fromApplication(
                                        context.applicationContext,
                                        AppUpdateEntryPoint::class.java
                                    ).appUpdateManager()
                                    val info = withContext(Dispatchers.IO) { manager.checkForUpdate() }
                                    updateInfo = info
                                    if (info == null) {
                                        Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // 弹下载对话框 (复用 AppUpdateLauncher 逻辑)
                                        showUpdateDialog(context, info)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "检查失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                updateChecking = false
                            }
                        },
                        loading = updateChecking,
                        modifier = Modifier.weight(1f),
                    )
                    ActionCard(
                        title = "服务器日志",
                        subtitle = "查看 OpenList 实时日志",
                        icon = Icons.Filled.Description,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("${com.threel.openlist.data.api.TokenStore.DEFAULT_SERVER}/@manage/log")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "请用 web 端访问", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 链接: GitHub releases + 服务端
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        title = "GitHub 仓库",
                        subtitle = "aqiyoung/openlist-android",
                        icon = Icons.Filled.OpenInBrowser,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/aqiyoung/openlist-android")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ActionCard(
                        title = "网盘 Web",
                        subtitle = "<your-server>",
                        icon = Icons.Filled.CloudDownload,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(com.threel.openlist.data.api.TokenStore.DEFAULT_SERVER)
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 分割线
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
                Text(
                    text = "版本更新日志",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // 加载/错误
            when {
                changelogLoading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                changelogError != null -> item {
                    ErrorCard(changelogError!!) {
                        changelogLoading = true
                        changelogError = null
                        scope.launch {
                            try {
                                changelog = withContext(Dispatchers.IO) { fetchChangelog() }
                                changelogLoading = false
                            } catch (e: Exception) {
                                changelogError = e.message
                                changelogLoading = false
                            }
                        }
                    }
                }
                changelog != null -> {
                    items(changelog!!.releases) { release ->
                        ChangelogCard(release)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ChangelogCard(release: ReleaseEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v${release.version}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = release.date,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            release.changelog.forEach { line ->
                Text(
                    text = "• $line",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("加载失败: $msg", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

// ============== changelog 数据模型 ==============
@Serializable
data class ChangelogData(
    val current: String = "",
    val min_supported: String = "",
    val releases: List<ReleaseEntry> = emptyList(),
)

@Serializable
data class ReleaseEntry(
    val version: String,
    val versionCode: Int = 0,
    val date: String = "",
    val type: String = "release",
    val applicationId: String = "",
    val apk_url: String = "",
    val changelog: List<String> = emptyList(),
)

// ============== 拉 changelog (后台 URL) ==============
private val client by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchChangelog(): ChangelogData = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(AppConfig.CHANGELOG_URL).get().build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body?.string() ?: error("empty body")
        Json { ignoreUnknownKeys = true }.decodeFromString(ChangelogData.serializer(), body)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}

/** 老板 6/13 拍: About 页面检查更新弹窗 */
private fun showUpdateDialog(context: android.content.Context, info: AppUpdateInfo) {
    val title = "发现新版本 ${info.version}"
    val message = buildString {
        append("当前: ${AppConfig.fullVersionString(context)}\n")
        append("最新: ${info.version} (build ${info.versionCode})\n")
        if (info.force_update) append("\n⚠️ 强制更新\n")
        append("\n是否前往下载？")
    }
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("下载") { _, _ ->
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(info.apk_url)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        .setNegativeButton("稍后", null)
        .setCancelable(!info.force_update)
        .show()
}
