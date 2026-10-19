package com.datbox.app.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.datbox.app.ui.viewmodel.FilePreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

private enum class PreviewKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, NONE }

private val IMAGE_EXT = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif"
)

private val VIDEO_EXT = setOf(
    "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp"
)

private val AUDIO_EXT = setOf(
    "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac"
)

private val TEXT_EXT = setOf(
    "txt", "md", "markdown", "log", "rst", "tex", "json", "xml", "yaml", "yml", "ini",
    "conf", "cfg", "csv", "tsv", "diff", "patch", "sh", "bash", "zsh", "bat", "cmd",
    "ps1", "py", "js", "jsx", "tsx", "go", "rs", "rust", "c", "cpp", "cc", "cxx",
    "h", "hpp", "java", "kt", "kts", "scala", "swift", "rb", "php", "pl", "lua", "r",
    "sql", "html", "htm", "css", "scss", "sass", "less", "vue", "svelte", "dart", "asm",
    "toml", "properties", "gradle", "gitignore", "dockerfile", "makefile", "env"
)

private const val TEXT_HEAD_BYTES = 256 * 1024

private fun previewKindOf(fileName: String): PreviewKind {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in IMAGE_EXT -> PreviewKind.IMAGE
        ext in VIDEO_EXT -> PreviewKind.VIDEO
        ext in AUDIO_EXT -> PreviewKind.AUDIO
        ext == "pdf" -> PreviewKind.PDF
        ext in TEXT_EXT -> PreviewKind.TEXT
        else -> PreviewKind.NONE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    remotePath: String,
    fileName: String,
    onBack: () -> Unit,
    vm: FilePreviewViewModel = hiltViewModel(),
) {
    val kind = remember(fileName) { previewKindOf(fileName) }
    val urlState by vm.url.collectAsState()

    LaunchedEffect(remotePath) { vm.load(remotePath) }

    val url = urlState?.getOrNull()
    val failure = urlState?.exceptionOrNull()?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            urlState == null -> CenterProgress()

            url == null -> CenterHint(failure ?: "无法获取预览地址", actionLabel = "重试") {
                vm.load(remotePath)
            }

            kind == PreviewKind.IMAGE -> ZoomableImage(url)
            kind == PreviewKind.VIDEO -> MediaPreview(url, fileName, audioOnly = false)
            kind == PreviewKind.AUDIO -> MediaPreview(url, fileName, audioOnly = true)
            kind == PreviewKind.PDF -> PdfPreview(url, fileName)
            kind == PreviewKind.TEXT -> TextPreview(url)
            else -> UnsupportedPreview(fileName, url)
        }

        TopAppBar(
            title = {
                Text(
                    fileName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }
}

// ---------------------------------------------------------------- 通用状态

@Composable
private fun CenterProgress() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Color.White)
}

@Composable
private fun CenterHint(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White.copy(alpha = 0.75f))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel, color = Color.White) }
        }
    }
}

// ---------------------------------------------------------------- 图片

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }

    val transformable = Modifier
        .pointerInput(url) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 6f)
                offsetX += pan.x
                offsetY += pan.y
            }
        }
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offsetX; translationY = offsetY
        }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(transformable),
            contentScale = ContentScale.Fit,
            onLoading = { loading = true },
            onSuccess = { loading = false },
            onError = { loading = false; failed = true },
        )
        if (loading) CircularProgressIndicator(color = Color.White)
        if (failed) Text("图片加载失败", color = Color.White.copy(alpha = 0.7f))
    }
}

// ---------------------------------------------------------------- 视频 / 音频

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaPreview(url: String, fileName: String, audioOnly: Boolean) {
    val context = LocalContext.current
    var playbackError by remember(url) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(url) {
        val dataSource = DefaultHttpDataSource.Factory()
            // OpenList 的 /d 通常 302 到对象存储，可能跨协议 (http -> https)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (audioOnly) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(132.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    fileName,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        playbackError?.let {
            CenterHint("播放失败：$it")
        }
    }
}

// ---------------------------------------------------------------- PDF

@Composable
private fun PdfPreview(url: String, fileName: String) {
    val context = LocalContext.current
    var file by remember(url) { mutableStateOf<File?>(null) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var failure by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        try {
            file = downloadToCache(context, url, fileName) { progress = it }
        } catch (e: Exception) {
            failure = e.message ?: "下载失败"
        }
    }

    when {
        failure != null -> CenterHint("PDF 加载失败：$failure")

        file == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = progress, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "正在下载 PDF ${(progress * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        else -> PdfPages(file!!)
    }
}

@Composable
private fun PdfPages(file: File) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        .toInt()
        .coerceAtLeast(1)

    val state by produceState<Pair<PdfRenderer, Int>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                renderer to renderer.pageCount
            }.getOrNull()
        }
    }

    val loaded = state
    DisposableEffect(loaded) {
        onDispose {
            // 渲染线程可能还在跑，close 抛 IllegalStateException 时不能把主线程带崩
            runCatching { loaded?.first?.close() }
        }
    }

    if (loaded == null) {
        CenterHint("无法打开 PDF")
        return
    }

    val (renderer, pageCount) = loaded
    val mutex = remember(renderer) { Mutex() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 72.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(pageCount) { index ->
            Column {
                PdfPage(renderer, index, widthPx, mutex)
                Text(
                    "${index + 1} / $pageCount",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int, widthPx: Int, mutex: Mutex) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx, renderer) {
        value = withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val height = ((widthPx.toFloat() * page.height) / page.width)
                            .toInt()
                            .coerceAtLeast(1)
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(android.graphics.Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }

    val bitmapValue = bitmap
    if (bitmapValue == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(Color.White.copy(alpha = 0.06f))
        )
    } else {
        Image(
            bitmap = bitmapValue.asImageBitmap(),
            contentDescription = "第 ${index + 1} 页",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

private suspend fun downloadToCache(
    context: Context,
    url: String,
    fileName: String,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val safeName = fileName.substringAfterLast('/').ifBlank { "preview.pdf" }
    val out = File(context.cacheDir, "preview_${safeName.hashCode()}_$safeName")

    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body ?: error("响应为空")
        val total = body.contentLength()
        var read = 0L
        body.byteStream().use { input ->
            out.outputStream().use { os ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    os.write(buffer, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    }
    onProgress(1f)
    out
}

// ---------------------------------------------------------------- 文本

@Composable
private fun TextPreview(url: String) {
    var content by remember(url) { mutableStateOf<String?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        loading = true
        content = readTextHead(url)
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

            content != null -> SelectionContainer {
                Text(
                    text = content!!,
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 24.dp)
                )
            }

            else -> CenterHint("无法加载文件内容")
        }
    }
}

/**
 * 只读文件头部，边读边截断。
 * 旧实现用 body.string() 把整个文件读进内存再 take(100_000)，几百 MB 的日志会直接 OOM。
 */
private suspend fun readTextHead(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body ?: return@withContext null
            val buffer = ByteArray(TEXT_HEAD_BYTES)
            var total = 0
            body.byteStream().use { input ->
                while (total < TEXT_HEAD_BYTES) {
                    val n = input.read(buffer, total, TEXT_HEAD_BYTES - total)
                    if (n <= 0) break
                    total += n
                }
            }
            val text = String(buffer, 0, total, Charsets.UTF_8)
            if (total >= TEXT_HEAD_BYTES) "$text\n\n… 文件过大，仅显示前 ${TEXT_HEAD_BYTES / 1024} KB" else text
        }
    }.getOrNull()
}

// ---------------------------------------------------------------- 其它类型

@Composable
private fun UnsupportedPreview(fileName: String, url: String) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "暂不支持此类型预览",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fileName,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { openWithOtherApp(context, url) }) {
                Text("用其他应用打开", color = Color.White)
            }
        }
    }
}

private fun openWithOtherApp(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val ok = runCatching { context.startActivity(intent) }.isSuccess
    if (!ok) Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
}
