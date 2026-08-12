package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var showUpdate by remember { mutableStateOf(false) }

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
        Box(Modifier.offset(x = (-60).dp, y = 560.dp).size(170.dp).clip(CircleShape).background(Color(0xFF20C997).copy(alpha = 0.05f)))
        Box(Modifier.offset(x = 250.dp, y = 110.dp).size(120.dp).clip(CircleShape).background(Color(0xFF20C997).copy(alpha = 0.04f)))

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("关于", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { Spacer(Modifier.height(20.dp)) }

                // 品牌 Hero
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 10.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.openlist_logo_official),
                                contentDescription = "OpenList Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = AppConfig.BRAND_EN,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A2925),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = AppConfig.BRAND_SUBTITLE,
                                fontSize = 13.sp,
                                color = Color(0xFF888888),
                            )
                            Spacer(Modifier.height(14.dp))
                            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF20C997).copy(alpha = 0.12f)) {
                                Text(
                                    text = AppConfig.fullVersionString(context),
                                    fontSize = 12.sp,
                                    color = Color(0xFF1A9E80),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // 主操作行
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            label = if (updateChecking) "检查中…" else "检查更新",
                            icon = Icons.Filled.Update,
                            tint = Color(0xFF20C997),
                            showProgress = updateChecking,
                            onClick = {
                                updateChecking = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
                                        updateInfo = result
                                        when {
                                            result == null ->
                                                Toast.makeText(context, "检查失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                            result.hasUpdate ->
                                                showUpdate = true
                                            else ->
                                                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "检查失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    updateChecking = false
                                }
                            },
                        )
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            label = "开源仓库",
                            customPainter = painterResource(R.drawable.ic_github_cat),
                            tint = Color(0xFF2A2925),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aqiyoung/openlist-android")))
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // 设置卡
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("启动时检查更新", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF2A2925))
                                Spacer(Modifier.height(2.dp))
                                Text("自动检测 GitHub 上的新版本", fontSize = 12.sp, color = Color(0xFF888888))
                            }
                            Switch(
                                checked = autoCheck,
                                onCheckedChange = { scope.launch { updateManager.setAutoCheckEnabled(it) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF20C997)),
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // 开源信息卡
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("开源信息", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.UPSTREAM_REPO))) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.OpenInNew, null, tint = Color(0xFF20C997), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(AppConfig.UPSTREAM_NOTE, fontSize = 13.sp, color = Color(0xFF2A2925))
                                    Spacer(Modifier.height(2.dp))
                                    Text(AppConfig.UPSTREAM_REPO, fontSize = 11.sp, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
                item { Text("© 2026 三页云盘 · 仅供学习交流", fontSize = 11.sp, color = Color(0xFFAAAAAA)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showUpdate && updateInfo != null) {
        showUpdateDialog(context, updateManager, updateInfo!!)
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector? = null,
    customPainter: Painter? = null,
    tint: Color,
    showProgress: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                showProgress -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
                customPainter != null -> Icon(painter = customPainter, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Unspecified)
                icon != null -> Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF2A2925))
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
