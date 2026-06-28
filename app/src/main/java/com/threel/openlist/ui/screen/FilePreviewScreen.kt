package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * 图片/视频全屏预览
 * 老板 6/28 拍: "文件总得能点开看看什么样子, 图片能缩放就行"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    /** 完整远端路径 (如 /天翼云盘/image.png) */
    remotePath: String,
    fileName: String,
    onBack: () -> Unit,
) {
    val ext = fileName.substringAfterLast(".").lowercase()
    val isImage = ext in setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif"
    )
    val isVideo = ext in setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp"
    )
    val isAudio = ext in setOf(
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac"
    )
    // 非图片非视频非音频 → 不支持预览
    if (!isImage && !isVideo && !isAudio) {
        Dialog(onDismissRequest = onBack) {
            Surface(
                modifier = Modifier.padding(32.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂不支持此类型预览")
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
        }
        return
    }

    val context = LocalContext.current

    // 构建预览 URL — 走 /d/ 路由 + sign
    // 这里简单处理：直接用 serverUrl + /d/<path>?xxx，实际应由 VM 注入
    // 暂时写死，待重构时引入 preview URL builder
    val serverUrl = com.threel.openlist.util.AppConfig.PUBLIC_BASE_URL.trimEnd('/')
    // TODO: 走 VM 拿 sign

    val dismissOnBack = { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isImage -> ZoomableImage(url = "$serverUrl/d$remotePath")
            isVideo -> Box(contentAlignment = Alignment.Center) {
                // 用系统视频播放器
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    // 需要走 /api/fs/get 拿 sign → 暂时 placeholder，preview URL 由 VM 注入
                    setDataAndType(Uri.parse(serverUrl), "video/*")
                }
                // 实际用系统 player
                androidx.compose.material3.TextButton(onClick = {
                    try { context.startActivity(intent) } catch (_: Exception) {}
                    dismissOnBack()
                }) {
                    androidx.compose.material3.Text(
                        "用系统播放器打开",
                        color = Color.White,
                    )
                }
            }
            isAudio -> Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                    dismissOnBack()
                }) {
                    androidx.compose.material3.Text(
                        "用系统播放器打开",
                        color = Color.White,
                    )
                }
            }
        }

        // 顶部栏
        TopAppBar(
            title = { Text(fileName) },
            navigationIcon = { IconButton(onClick = dismissOnBack) { Icon(Icons.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White),
        )
    }
}

/**
 * 缩放手势检测包裹的 AsyncImage
 */
@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformable = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 5f)
                offsetX += pan.x
                offsetY += pan.y
            }
        }
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offsetX; translationY = offsetY
        }
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().then(transformable),
        contentScale = ContentScale.Fit,
    )
}
