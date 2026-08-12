package com.threel.openlist.ui.screen

import android.content.Context
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
                item { Spacer(Modifier.height(8.dp)) }

                // 紧凑品牌区（不套大卡）
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.openlist_logo_official),
                            contentDescription = "OpenList Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = AppConfig.BRAND_EN,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2A2925),
                        )
                        Text(
                            text = AppConfig.BRAND_SUBTITLE,
                            fontSize = 12.sp,
                            color = Color(0xFF888888),
                        )
                        Spacer(Modifier.height(10.dp))
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

                item { Spacer(Modifier.height(20.dp)) }

                // 主操作行
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            label = if (updateChecking) "检查中…" else "检查更新",
                            icon = Icons.Filled.SystemUpdate,
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

                item { Spacer(Modifier.height(12.dp)) }

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

                item { Spacer(Modifier.height(12.dp)) }

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
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(20.dp))
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

                item { Spacer(Modifier.height(20.dp)) }
                item { Text("© 2026 三页云盘 · 仅供学习交流", fontSize = 11.sp, color = Color(0xFFAAAAAA)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showUpdate && updateInfo != null) {
        UpdateFoundDialog(
            context = context,
            manager = updateManager,
            result = updateInfo!!,
            onDismiss = { showUpdate = false },
        )
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

@Composable
private fun UpdateFoundDialog(
    context: Context,
    manager: AppUpdateManager,
    result: GitHubUpdateResult,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // 图标 + 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF20C997).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFF20C997),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("发现新版本", fontSize = 13.sp, color = Color(0xFF888888))
                        Text(
                            text = result.latestVersion,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2A2925),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 左右版本对比卡
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VersionCompareCard(
                        modifier = Modifier.weight(1f),
                        title = "当前版本",
                        version = manager.currentVersionName,
                        accent = Color(0xFF888888),
                    )
                    VersionCompareCard(
                        modifier = Modifier.weight(1f),
                        title = "最新版本",
                        version = result.latestVersion,
                        accent = Color(0xFF20C997),
                    )
                }

                // 关键更新警示
                if (result.isCritical) {
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF6B6B).copy(alpha = 0.1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("关键更新，建议尽快升级", fontSize = 13.sp, color = Color(0xFFD32F2F))
                        }
                    }
                }

                // 更新说明
                result.releaseNotes?.let { notes ->
                    val body = notes.lines().drop(1).joinToString("\n").trim()
                    if (body.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("更新内容", fontSize = 12.sp, color = Color(0xFF888888))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (body.length > 400) body.take(400) + "…" else body,
                            fontSize = 13.sp,
                            color = Color(0xFF4A4A4A),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 按钮
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!result.isCritical) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("稍后")
                        }
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            manager.openRelease(context, result.releaseUrl)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997)),
                    ) {
                        Text("前往更新", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCompareCard(
    modifier: Modifier = Modifier,
    title: String,
    version: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        ) {
            Text(title, fontSize = 12.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
            Text(
                text = version,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}
