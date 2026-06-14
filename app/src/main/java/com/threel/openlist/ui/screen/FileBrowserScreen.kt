package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.ui.component.LiquidGlassCard
import com.threel.openlist.ui.component.LiquidGlassFab
import com.threel.openlist.ui.component.LiquidGlassRow
import com.threel.openlist.ui.component.LiquidGlassTopBar
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.ui.graphics.vector.ImageVector
import com.threel.openlist.data.model.FsItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import javax.inject.Inject

data class FileBrowserState(
    val path: String = "/",
    val items: List<FsItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** 老板 6/13 v0.3.0: 加 download/upload/share 状态 */
data class FileActionState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FileBrowserState())
    val state = _state.asStateFlow()

    private val _action = MutableStateFlow(FileActionState())
    val action = _action.asStateFlow()

    init { load("/") }

    fun load(path: String) {
        _state.value = _state.value.copy(loading = true, path = path, error = null)
        viewModelScope.launch {
            repo.list(path)
                .onSuccess { items ->
                    _state.value = _state.value.copy(items = items, loading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    fun goUp(): String {
        val p = _state.value.path.trimEnd('/')
        if (p.isEmpty() || p == "/") return "/"
        val parent = p.substringBeforeLast('/').ifEmpty { "/" }
        return parent
    }

    /** 老板 6/13 v0.3.0: 下载文件 */
    fun downloadFile(remotePath: String, fileName: String) {
        com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "downloadFile: path=$remotePath name=$fileName")
        _action.value = FileActionState(busy = true, message = "下载 $fileName 中...")
        viewModelScope.launch {
            repo.download(remotePath, fileName)
                .onSuccess { file ->
                    com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "downloadFile OK: ${file.absolutePath} (${file.length()}B)")
                    _action.value = FileActionState(
                        busy = false,
                        message = "已下载到 ${file.absolutePath}",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    com.threel.openlist.util.TelemetryLog.e("FileBrowserVM", "downloadFile FAIL: $remotePath", e)
                    _action.value = FileActionState(
                        busy = false,
                        message = "下载失败: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    /** 老板 6/13 v0.3.0: 上传文件 */
    fun uploadFile(localFile: File) {
        val targetDir = _state.value.path
        com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "uploadFile: ${localFile.absolutePath} (${localFile.length()}B) -> $targetDir")
        _action.value = FileActionState(busy = true, message = "上传 ${localFile.name} 中...")
        viewModelScope.launch {
            repo.upload(targetDir, localFile)
                .onSuccess { resp ->
                    if (resp.code == 200) {
                        com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "uploadFile OK: ${localFile.name}")
                        _action.value = FileActionState(
                            busy = false,
                            message = "${localFile.name} 上传成功",
                        )
                        load(targetDir)  // 刷新列表
                    } else {
                        com.threel.openlist.util.TelemetryLog.w("FileBrowserVM", "uploadFile FAIL code=${resp.code}: ${resp.message}")
                        _action.value = FileActionState(
                            busy = false,
                            message = "上传失败: ${resp.message}",
                            isError = true,
                        )
                    }
                }
                .onFailure { e ->
                    com.threel.openlist.util.TelemetryLog.e("FileBrowserVM", "uploadFile FAIL: $targetDir/${localFile.name}", e)
                    _action.value = FileActionState(
                        busy = false,
                        message = "上传失败: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    /** 老板 6/13 v0.3.0 + 6/14 修: 分享链接 (需先调 fs/get 拿 sign) */
    suspend fun buildShareUrl(remotePath: String): String {
        com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "buildShareUrl: $remotePath")
        return try {
            val url = repo.buildShareUrl(remotePath)
            com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "buildShareUrl OK: ${url.take(80)}...")
            url
        } catch (e: Throwable) {
            com.threel.openlist.util.TelemetryLog.e("FileBrowserVM", "buildShareUrl FAIL: $remotePath", e)
            throw e
        }
    }

    fun clearMessage() { _action.value = FileActionState() }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    onLogout: () -> Unit,
    onAbout: () -> Unit = {},
    vm: FileBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val action by vm.action.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 老板 6/14: 液态玻璃弹窗 - 长按文件后设置该项
    var menuItem by remember { mutableStateOf<FsItem?>(null) }
    val menuRemotePath = menuItem?.let { item ->
        if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
    }

    // 老板 6/13 v0.3.0: 文件选择器 (上传)
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        com.threel.openlist.util.TelemetryLog.i("FileBrowser", "user picked file: $uri")
        // 把 content:// URI 复制到 cacheFile (Retrofit 上传需要 File)
        scope.launch {
            val tempFile = withContext(Dispatchers.IO) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload_${System.currentTimeMillis()}"
                val out = File(context.cacheDir, name)
                FileOutputStream(out).use { os ->
                    context.contentResolver.openInputStream(uri)?.use { it.copyTo(os) }
                }
                out
            }
            vm.uploadFile(tempFile)
        }
    }

    // 老板 6/13 v0.3.0: 弹窗/Toast 反馈
    LaunchedEffect(action.message) {
        action.message?.let {
            Toast.makeText(context, it, if (action.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            LiquidGlassTopBar(
                title = "三页云盘 · ${state.path}",
                leadingIcon = Icons.Outlined.Folder,
                actions = {
                    IconButton(onClick = { vm.load(state.path) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "关于")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "退出")
                    }
                },
            )
        },
        floatingActionButton = {
            // 老板 6/14 15:25 拍: 圆形加号, 不需要文字
            if (action.busy) {
                LiquidGlassFab(
                    icon = Icons.Filled.Refresh,
                    enabled = false,
                    onClick = {},
                )
            } else {
                LiquidGlassFab(
                    icon = Icons.Filled.Add,
                    onClick = { pickFileLauncher.launch("*/*") },
                )
            }
        },
        containerColor = Color(0xFFF5F4ED),
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            when {
                state.loading -> CenterLoading()
                state.error != null -> CenterMessage(state.error!!, "刷新")
                state.items.isEmpty() -> CenterMessage(
                    msg = if (state.path == "/") "还没有挂载任何网盘" else "空目录",
                    actionLabel = if (state.path != "/") "返回上一层" else null,
                    onAction = if (state.path != "/") ({ vm.load(vm.goUp()) }) else null,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.path != "/") {
                        item {
                            FileRow(
                                icon = { Icon(Icons.Outlined.Folder, null, tint = Color(0xFFC96442)) },
                                name = "..",
                                size = "",
                                modified = "",
                                onClick = { vm.load(vm.goUp()) },
                                onLongClick = null,
                                onMenuClick = null,
                            )
                        }
                    }
                    items(state.items) { item ->
                        val onMenu = if (!item.isDir) {
                            {
                                com.threel.openlist.util.TelemetryLog.i("FileBrowser", "user tapped 3-dot menu on file: ${item.name}")
                                menuItem = item
                            }  // 老板 6/14: 点三个点弹玻璃弹窗
                        } else null
                        // 老板 6/14 拍: 根据扩展名识别文件类型, 用对应图标 + 颜色
                        val (fileIcon, fileColor) = fileIconFor(item.name, item.isDir)
                        FileRow(
                            icon = { Icon(fileIcon, null, tint = fileColor) },
                            name = item.name,
                            size = if (item.isDir) "" else humanSize(item.size),
                            modified = item.modified.take(10),
                            onClick = {
                                if (item.isDir) {
                                    val next = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
                                    vm.load(next)
                                }
                            },
                            onLongClick = null,  // 老板 6/14: 取消长按, 用三个点弹
                            onMenuClick = onMenu,
                        )
                    }
                }
            }
        }

        // 老板 6/14: 液态玻璃弹窗
        val pending = menuItem
        if (pending != null && menuRemotePath != null) {
            GlassActionDialog(
                fileName = pending.name,
                onDownload = {
                    com.threel.openlist.util.TelemetryLog.i("FileBrowser", "user clicked DOWNLOAD: ${pending.name}")
                    vm.downloadFile(menuRemotePath, pending.name)
                },
                onShare = {
                    com.threel.openlist.util.TelemetryLog.i("FileBrowser", "user clicked SHARE: ${pending.name}")
                    // 老板 6/14 修: buildShareUrl 是 suspend, 需 scope.launch
                    scope.launch {
                        val url = vm.buildShareUrl(menuRemotePath)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享 ${pending.name}"))
                    }
                },
                onDismiss = { menuItem = null },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    icon: @Composable () -> Unit,
    name: String,
    size: String,
    modified: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onMenuClick: (() -> Unit)? = null,
    selected: Boolean = false,  // 老板 6/14 拍: v0.3.14 选中态液态
) {
    // v0.3.14: 老板拍用 LiquidGlassRow 圆角 16dp + 轻 BorderCream + 选中态 alpha 0.95
    com.threel.openlist.ui.component.LiquidGlassRow(
        cornerRadius = 16.dp,
        selected = selected,
        onClick = onClick,
        leading = icon,
        title = name,
        subtitle = if (modified.isNotEmpty() || size.isNotEmpty())
            listOf(modified, size).filter { it.isNotEmpty() }.joinToString(" · ")
        else null,
        trailing = if (onMenuClick != null) {
            {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        tint = Color(0xFF87867F),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else null,
    )
}

@Composable
private fun CenterLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    com.threel.openlist.ui.component.LiquidGlassCard(cornerRadius = 24.dp, contentPadding = 32.dp) {
        CircularProgressIndicator(color = Color(0xFFC96442))
    }
}

@Composable
private fun CenterMessage(
    msg: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    com.threel.openlist.ui.component.LiquidGlassCard(
        modifier = Modifier.padding(32.dp).widthIn(max = 320.dp),
        cornerRadius = 24.dp,
        contentPadding = 24.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = Color(0xFF141413), style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                // 老板 6/14 15:25 拍: 按钮用新 LiquidGlassPrimaryButton (不再"灯笼框")
                com.threel.openlist.ui.component.LiquidGlassPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    icon = Icons.Outlined.ArrowBack,
                )
            }
        }
    }
}

/** 老板 6/14: 液态玻璃弹窗 (Material 3 AlertDialog + 半透明白)
 *
 * 风格参考:
 * - 标题/文件名为次要色
 * - 主操作（下载/分享）每项独立卡片 + 大 icon + 文字
 * - 取消按钮独立一行
 * - 圆角 20dp + containerColor 半透明白 (0.85)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassActionDialog(
    fileName: String,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.85f),  // 液态玻璃: 半透明白
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 标题
                Text(
                    text = "文件操作",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF87867F),
                    modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 4.dp),
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2A2925),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )

                // 老板 6/14: 下载按钮 (主操作 - 暖色 accent)
                GlassActionItem(
                    icon = Icons.Outlined.Download,
                    iconTint = Color(0xFFC96442),
                    label = "下载到本地",
                    subLabel = "保存到下载目录",
                    onClick = { onDownload(); onDismiss() },
                )
                // 分享链接
                GlassActionItem(
                    icon = Icons.Outlined.Share,
                    iconTint = Color(0xFFC96442),
                    label = "分享链接",
                    subLabel = "复制 / 发送短链",
                    onClick = { onShare(); onDismiss() },
                )

                // 老板 6/14: 取消独立一行 (液态玻璃感)
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDismiss() },
                    color = Color(0xFFF5F4ED).copy(alpha = 0.6f),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF2A2925),
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** 弹窗里的一个操作行 (大 icon + 标题 + 副标题) */
@Composable
private fun GlassActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    subLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color(0xFFFAF9F5).copy(alpha = 0.7f),  // 每行: 略亮一点的玻璃
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // icon 背景小圆
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF2A2925),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                )
            }
        }
    }
}

private val sizeFmt = DecimalFormat("#,##0.#")
private fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${sizeFmt.format(b / 1024.0)} KB"
    b < 1024L * 1024 * 1024 -> "${sizeFmt.format(b / 1024.0 / 1024)} MB"
    else -> "${sizeFmt.format(b / 1024.0 / 1024 / 1024)} GB"
}

/**
 * 老板 6/14 拍: 根据扩展名识别文件类型, 返回对应图标
 *
 * | 分类 | 扩展名 | 图标 | 颜色 |
 * |---|---|---|---|
 * | 压缩包 | zip/7z/rar/tar/gz/bz2/xz/tgz | Archive | 棕 #8B6F47 |
 * | EXE 可执行 | exe/msi/apk/bat/cmd/sh/dmg/deb/rpm | Android | 紫 #7B5BA6 |
 * | 图片 | jpg/jpeg/png/gif/bmp/webp/svg/heic/ico | Image | 蓝 #5B8AC9 |
 * | 视频 | mp4/mkv/avi/mov/flv/wmv/m4v/webm | Movie | 红 #C95B5B |
 * | 音频 | mp3/flac/wav/aac/ogg/wma/m4a | MusicNote | 粉 #C95BA0 |
 * | PDF | pdf | PictureAsPdf | 朱红 #DC4A3A |
 * | 代码 | kt/java/py/js/ts/go/rust/c/cpp/h/json/xml/yaml | Code | 绿 #5B8F5B |
 * | 文本 | txt/md/log/csv | TextSnippet | 灰 #87867F |
 * | Office | doc/docx/xls/xlsx/ppt/pptx | Description | 藏青 #4A6B8A |
 * | 表格 | xls/xlsx/csv | GridView | 藏青 #4A6B8A |
 * | 演示 | ppt/pptx | Slideshow | 藏青 #4A6B8A |
 */
private fun fileIconFor(name: String, isDir: Boolean): Pair<ImageVector, Color> = when {
    isDir -> Icons.Outlined.Folder to Color(0xFFC96442)  // 文件夹 始终 Terracotta
    else -> {
        val ext = name.substringAfterLast('.', "").lowercase()
        when (ext) {
            // 压缩包
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "lz", "lzma", "zst" ->
                Icons.Outlined.Archive to Color(0xFF8B6F47)
            // EXE 可执行
            "exe", "msi", "apk", "bat", "cmd", "sh", "dmg", "deb", "rpm", "app", "jar", "bin", "run" ->
                Icons.Outlined.Android to Color(0xFF7B5BA6)
            // 图片
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif" ->
                Icons.Outlined.Image to Color(0xFF5B8AC9)
            // 视频
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp" ->
                Icons.Outlined.Movie to Color(0xFFC95B5B)
            // 音频
            "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" ->
                Icons.Outlined.MusicNote to Color(0xFFC95BA0)
            // PDF
            "pdf" -> Icons.Outlined.PictureAsPdf to Color(0xFFDC4A3A)
            // 代码 (kt 优先突出本项目)
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "go", "rust", "rs", "c", "cpp", "cc", "cxx", "h", "hpp",
            "json", "xml", "yaml", "yml", "toml", "ini", "conf", "gradle", "dart", "swift", "rb", "php", "sh", "bash",
            "zsh", "ps1", "html", "htm", "css", "scss", "sass", "less", "sql" ->
                Icons.Outlined.Code to Color(0xFF5B8F5B)
            // 文本
            "txt", "md", "markdown", "log", "rst", "tex" ->
                Icons.Outlined.TextSnippet to Color(0xFF87867F)
            // Word
            "doc", "docx", "rtf", "odt" ->
                Icons.Outlined.Description to Color(0xFF4A6B8A)
            // Excel / CSV
            "xls", "xlsx", "ods", "csv" ->
                Icons.Outlined.GridView to Color(0xFF4A6B8A)
            // PowerPoint
            "ppt", "pptx", "odp", "key" ->
                Icons.Outlined.Slideshow to Color(0xFF4A6B8A)
            // 表格文件
            "iso" ->
                Icons.Outlined.FolderZip to Color(0xFF8B6F47)
            // 终端脚本
            "sh", "bash", "zsh", "fish", "ps1" ->
                Icons.Outlined.Terminal to Color(0xFF5B8F5B)
            // 默认: 未知文件
            else -> Icons.Outlined.InsertDriveFile to Color(0xFF87867F)
        }
    }
}
