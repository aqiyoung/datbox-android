package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import com.threel.openlist.R
import com.threel.openlist.ui.component.LiquidGlassTopBar
import com.threel.openlist.ui.component.LiquidGlassCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Scaffold(
        topBar = {
            LiquidGlassTopBar(
                title = "关于",
                leadingIcon = Icons.Filled.Info,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        // 老板 6/13 拍: 整个页面可滑动 (顶部品牌 + 中间 2 按钮)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                // 老板 6/14 拍: v0.3.14 About 顶部大号 LiquidGlassCard (装 LOGO + 版本 + 标注)
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    cornerRadius = 24.dp,
                    contentPadding = 24.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        // LOGO 图 (白底 SVG, 36dp 圆角)
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
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = AppConfig.BRAND_SUBTITLE,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = AppConfig.fullVersionString(context),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        // 老板 6/14 拍: 标注 "基于官方 OpenList 开发"
                        Text(
                            text = AppConfig.UPSTREAM_NOTE,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
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
                    // 2. 仓库 (GitHub) - 老板 6/14 拍: 加猫猫 logo
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
                            // 老板 6/14 拍: GitHub 官方 Octocat 猫猫 (drawable/ic_github_cat)
                            Icon(
                                painter = painterResource(R.drawable.ic_github_cat),
                                contentDescription = "GitHub",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("仓库", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
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
    // 老板 6/14 拍: 加 '复制链接' 按钮 (除了 下载, 还能复制纯链接)
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
        .setNeutralButton("复制链接") { dialog: android.content.DialogInterface?, which: Int ->
            // 老板 6/14 拍: 复制 APK 链接到剪贴板
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("APK 下载链接", info.apk_url)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "链接已复制: ${info.apk_url}", android.widget.Toast.LENGTH_LONG).show()
        }
        .setNegativeButton("稍后", null)
        .setCancelable(!info.force_update)
        .show()
}

