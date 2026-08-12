package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.R
import com.threel.openlist.data.update.AppUpdateManager
import com.threel.openlist.data.update.GitHubUpdateResult
import com.threel.openlist.util.AppConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<GitHubUpdateResult?>(null) }
    var updateChecking by remember { mutableStateOf(false) }

    val updateManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppUpdateEntryPoint::class.java)
            .appUpdateManager()
    }
    val autoCheck by updateManager.autoCheckEnabled.collectAsState(initial = true)

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
                    title = { Text("关于", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Spacer(Modifier.height(24.dp))

                    // Logo 卡片
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.openlist_logo_official),
                                contentDescription = "OpenList Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = AppConfig.BRAND + "云盘",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A2925),
                            )
                            Text(
                                text = AppConfig.BRAND_SUBTITLE,
                                fontSize = 12.sp,
                                color = Color(0xFF888888),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = AppConfig.fullVersionString(context),
                                fontSize = 11.sp,
                                color = Color(0xFF888888),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = AppConfig.UPSTREAM_NOTE,
                                fontSize = 11.sp,
                                color = Color(0xFF888888),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // 按钮
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 更新
                        Surface(
                            onClick = {
                                updateChecking = true
                                scope.launch {
                                    try {
                                val manager = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    AppUpdateEntryPoint::class.java
                                ).appUpdateManager()
                                val result = withContext(Dispatchers.IO) { manager.checkForUpdate() }
                                updateInfo = result
                                when {
                                    result == null ->
                                        Toast.makeText(context, "检查失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                    result.hasUpdate ->
                                        showUpdateDialog(context, manager, result)
                                    else ->
                                        Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "检查失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    updateChecking = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (updateChecking) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF20C997))
                                } else {
                                    Icon(Icons.Filled.Update, null, tint = Color(0xFF20C997))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (updateChecking) "检查中..." else "更新",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2A2925)
                                )
                            }
                        }
                        // 仓库
                        Surface(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/aqiyoung/openlist-android")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_github_cat),
                                    contentDescription = "GitHub",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Unspecified,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("仓库", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF2A2925))
                            }
                        }
                    }
                }

                // 启动时自动检查更新开关
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "启动时检查更新",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2A2925),
                                )
                                Text(
                                    "自动检测 GitHub 上的新版本",
                                    fontSize = 11.sp,
                                    color = Color(0xFF888888),
                                )
                            }
                            Switch(
                                checked = autoCheck,
                                onCheckedChange = { scope.launch { updateManager.setAutoCheckEnabled(it) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF20C997)),
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}

private fun showUpdateDialog(context: android.content.Context, manager: AppUpdateManager, result: GitHubUpdateResult) {
    val message = buildString {
        append("当前: ${manager.currentVersionName}\n")
        append("最新: ${result.latestVersion}\n")
        result.releaseNotes?.let { notes ->
            // 跳过首行 P0 / critical 标记，展示其余更新内容。
            val body = notes.lines().drop(1).joinToString("\n").trim()
            if (body.isNotEmpty()) {
                append("\n更新内容:\n")
                append(if (body.length > 400) body.take(400) + "…" else body)
            }
        }
        if (result.isCritical) append("\n\n⚠️ 关键更新，建议尽快升级")
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("发现新版本 ${result.latestVersion}")
        .setMessage(message)
        .setPositiveButton("前往更新") { _, _ ->
            manager.openRelease(context, result.releaseUrl)
        }
        .setNegativeButton("稍后", null)
        .setCancelable(!result.isCritical)
        .show()
}
