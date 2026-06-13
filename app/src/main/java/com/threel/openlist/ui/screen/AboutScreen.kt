package com.threel.openlist.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 老板 6/13 拍: About 页面极简化 - 只留 in-app 渲染 changelog
// 之前的 4 个按钮 (检查更新/服务器日志/GitHub仓库/网盘Web) 全部删掉
// 检查更新 -> 启动时 AppUpdateLauncher 自动弹
// 服务器日志 -> web 端看 (<your-server>/@manage/log)
// GitHub/网盘 -> 用户不需要
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changelog by remember { mutableStateOf<ChangelogData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            changelog = withContext(Dispatchers.IO) { fetchChangelog() }
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "未知错误"
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 极简品牌 header
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
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            // 标题 + 刷新
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "更新日志",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = { scope.launch { reload() } },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // changelog 列表
            when {
                error != null -> Text(
                    "加载失败: $error",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                )
                changelog != null -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(changelog!!.releases) { release -> ChangelogCard(release) }
                }
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "v${release.version}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    release.date,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            release.changelog.forEach { line ->
                Text(
                    "• $line",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
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
private val changelogClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchChangelog(): ChangelogData = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(AppConfig.CHANGELOG_URL).get().build()
    changelogClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body?.string() ?: error("empty body")
        Json { ignoreUnknownKeys = true }.decodeFromString(ChangelogData.serializer(), body)
    }
}
