package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import javax.inject.Inject

// 老板 6/13 拍: About 页面只留 2 个按钮 - '更新' + '仓库'
// 之前的 服务器日志 / GitHub 仓库 / 网盘 Web / 检查更新 全部砍了
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf<ChangelogData?>(null) }
    var changelogLoading by remember { mutableStateOf(true) }
    var changelogError by remember { mutableStateOf<String?>(null) }

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
                },
            )
        }
    ) { padding ->
        // 老板 6/13 拍: 整个页面可滑动 (顶部品牌 + 中间 2 按钮 + 底部 changelog 多可以滚)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = AppConfig.BRAND + "云盘",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = AppConfig.BRAND_SUBTITLE,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = AppConfig.fullVersionString(context),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }

            // 老板 6/13 拍: 只留 2 个按钮
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. 更新
                    OutlinedCard(
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
                                        showUpdateDialog(context, info)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "检查失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                updateChecking = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (updateChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Update, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (updateChecking) "检查中..." else "更新",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    // 2. 仓库 (GitHub)
                    OutlinedCard(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/aqiyoung/openlist-android")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("仓库", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                // 老板 6/13 拍: 顺便在底部 in-app 渲染 changelog (还是想看 changelog 的话)
                Text(
                    "更新日志",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            // changelog 内容 (嵌在 LazyColumn 里 -> 自动跟随页面滚动)
            when {
                changelogLoading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
                changelogError != null -> item {
                    Text(
                        "加载失败: $changelogError",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                changelog != null -> {
                    items(changelog!!.releases) { release ->
                        ChangelogCard(release)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChangelogCard(release: ReleaseEntry) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "v${release.version}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    release.date,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            release.changelog.forEach { line ->
                Text(
                    "• $line",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// ============== 共享: showUpdateDialog + 数据模型 ==============
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}

private fun showUpdateDialog(context: android.content.Context, info: AppUpdateInfo) {
    val title = "发现新版本 ${info.version}"
    val message = buildString {
        append("当前: ${AppConfig.fullVersionString(context)}\n")
        append("最新: ${info.version} (build ${info.versionCode})\n")
        if (info.force_update) append("\n⚠️ 强制更新\n")
        append("\n是否前往下载？")
    }
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("下载") { dialog: android.content.DialogInterface?, which: Int ->
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

@kotlinx.serialization.Serializable
data class ChangelogData(
    val current: String = "",
    val min_supported: String = "",
    val releases: List<ReleaseEntry> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ReleaseEntry(
    val version: String,
    val versionCode: Int = 0,
    val date: String = "",
    val type: String = "release",
    val applicationId: String = "",
    val apk_url: String = "",
    val changelog: List<String> = emptyList(),
)

private val changelogClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchChangelog(): ChangelogData = withContext(Dispatchers.IO) {
    val req = okhttp3.Request.Builder().url(AppConfig.CHANGELOG_URL).get().build()
    changelogClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body?.string() ?: error("empty body")
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(ChangelogData.serializer(), body)
    }
}
