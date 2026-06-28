package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.threel.openlist.data.api.TokenStore
import com.threel.openlist.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 文件预览页面
 * - 图片: 缩放预览 (pinch-to-zoom)
 * - PDF: 文本提取预览
 * - 音视频: 系统播放器打开
 * - 文本: 直接显示内容
 */
@HiltViewModel
class FilePreviewViewModel @Inject constructor(
    private val tokenStore: TokenStore,
) : androidx.lifecycle.ViewModel() {
    val tokenSync: String get() = tokenStore.tokenSync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    remotePath: String,
    fileName: String,
    onBack: () -> Unit,
    vm: FilePreviewViewModel = hiltViewModel(),
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
    val isText = ext in setOf(
        "txt", "md", "markdown", "log", "rst", "tex", "json", "xml", "yaml", "yml", "ini",
        "conf", "cfg", "csv", "tsv", "diff", "patch", "sh", "bash", "zsh", "bat", "cmd",
        "ps1", "py", "js", "ts", "jsx", "tsx", "go", "rs", "rust", "c", "cpp", "cc", "cxx",
        "h", "hpp", "java", "kt", "kts", "scala", "swift", "rb", "php", "pl", "lua", "r",
        "sql", "html", "htm", "css", "scss", "sass", "less", "vue", "svelte", "dart", "asm",
        "toml", "properties", "gradle", "gitignore", "dockerfile", "makefile", "log", "env"
    )
    val isPdf = ext == "pdf"

    val context = LocalContext.current
    val serverUrl = AppConfig.PUBLIC_BASE_URL.trimEnd('/')

    // 预览状态
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var textLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val authToken = vm.tokenSync

    // 获取预览 URL (需要 sign)
    LaunchedEffect(remotePath) {
        if (isImage || isVideo || isAudio) {
            previewUrl = buildPreviewUrl(serverUrl, remotePath, authToken)
        }
        if (isText) {
            textLoading = true
            val content = downloadTextContent("$serverUrl/d$remotePath", authToken)
            textContent = content
            textLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isImage && previewUrl != null -> ZoomableImage(url = previewUrl!!)
            isVideo -> VideoPreview(url = previewUrl, fileName = fileName)
            isAudio -> AudioPreview(url = previewUrl, fileName = fileName)
            isPdf -> PdfPreview(url = previewUrl)
            isText -> TextPreview(textContent, textLoading)
            else -> UnsupportedPreview(fileName)
        }

        // 顶部栏
        TopAppBar(
            title = {
                Text(
                    fileName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        )

        // 错误提示
        error?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { error = null }) { Text("关闭") } }
            ) { Text(it) }
        }
    }
}

/** 构建预览 URL: 走 /api/fs/get 拿 sign → /d/<path>?sign=hmac */
private suspend fun buildPreviewUrl(serverUrl: String, remotePath: String, authToken: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            // 1. 拿 sign
            val getBody = """{"path":"$remotePath"}"""
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = getBody.toRequestBody(mediaType)
            val getRequest = Request.Builder()
                .url("$serverUrl/api/fs/get")
                .addHeader("Authorization", authToken)
                .post(requestBody)
                .build()
            val sign = client.newCall(getRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("fs/get HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw IllegalStateException("empty body")
                val match = Regex("\"sign\"\\s*:\\s*\"([^\"]+)\"").find(body)
                    ?: throw IllegalStateException("no sign in response")
                match.groupValues[1]
            }
            "$serverUrl/d$remotePath?sign=$sign"
        } catch (e: Exception) {
            null
        }
    }
}

/** 下载文本文件内容 */
private suspend fun downloadTextContent(url: String, authToken: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(url).addHeader("Authorization", authToken).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string()?.take(100_000) ?: ""  // 限制 100KB
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ===== 图片预览 =====

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

// ===== 视频预览 =====

@Composable
private fun VideoPreview(url: String?, fileName: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("视频预览", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(fileName, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            if (url != null) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(url), "video/*")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text("用系统播放器打开", color = Color.White)
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ===== 音频预览 =====

@Composable
private fun AudioPreview(url: String?, fileName: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("音频预览", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(fileName, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            if (url != null) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(url), "audio/*")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text("用系统播放器打开", color = Color.White)
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ===== PDF 预览 =====

@Composable
private fun PdfPreview(url: String?) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PDF 预览", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            if (url != null) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(url), "application/pdf")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text("用系统 PDF 阅读器打开", color = Color.White)
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ===== 文本预览 =====

@Composable
private fun TextPreview(content: String?, loading: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else if (content != null) {
            SelectionContainer {
                Text(
                    text = content,
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        } else {
            Text(
                "无法加载文件内容",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ===== 不支持预览 =====

@Composable
private fun UnsupportedPreview(fileName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂不支持此类型预览", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(fileName, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
