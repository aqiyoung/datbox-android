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
        _action.value = FileActionState(busy = true, message = "下载 $fileName 中...")
        viewModelScope.launch {
            repo.download(remotePath, fileName)
                .onSuccess { file ->
                    _action.value = FileActionState(
                        busy = false,
                        message = "已下载到 ${file.absolutePath}",
                        isError = false,
                    )
                }
                .onFailure { e ->
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
        _action.value = FileActionState(busy = true, message = "上传 ${localFile.name} 中...")
        viewModelScope.launch {
            repo.upload(targetDir, localFile)
                .onSuccess { resp ->
                    if (resp.code == 200) {
                        _action.value = FileActionState(
                            busy = false,
                            message = "${localFile.name} 上传成功",
                        )
                        load(targetDir)  // 刷新列表
                    } else {
                        _action.value = FileActionState(
                            busy = false,
                            message = "上传失败: ${resp.message}",
                            isError = true,
                        )
                    }
                }
                .onFailure { e ->
                    _action.value = FileActionState(
                        busy = false,
                        message = "上传失败: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    /** 老板 6/13 v0.3.0 + 6/14 修: 分享链接 (需先调 fs/get 拿 sign) */
    suspend fun buildShareUrl(remotePath: String): String = repo.buildShareUrl(remotePath)

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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "三页云盘",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            state.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF87867F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFAF9F5).copy(alpha = 0.7f),
                ),
            )
        },
        floatingActionButton = {
            // 老板 6/13 v0.3.0: 上传 FAB
            ExtendedFloatingActionButton(
                onClick = { pickFileLauncher.launch("*/*") },
                containerColor = Color(0xFFC96442),
                contentColor = Color.White,
                expanded = !action.busy,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (action.busy) "处理中..." else "上传") },
            )
        },
        containerColor = Color(0xFFF5F4ED),
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            when {
                state.loading -> CenterLoading()
                state.error != null -> CenterMessage(state.error!!)
                state.items.isEmpty() -> CenterMessage("空目录")
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
                            { menuItem = item }  // 老板 6/14: 点三个点弹玻璃弹窗
                        } else null
                        FileRow(
                            icon = {
                                Icon(
                                    if (item.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                    null,
                                    tint = if (item.isDir) Color(0xFFC96442) else Color(0xFF87867F),
                                )
                            },
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
                    vm.downloadFile(menuRemotePath, pending.name)
                },
                onShare = {
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .let {
                if (onLongClick != null) {
                    it.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    it.clickable(onClick = onClick)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (modified.isNotEmpty() || size.isNotEmpty()) {
                Text(
                    listOf(modified, size).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                )
            }
        }
        if (onMenuClick != null) {
            // 老板 6/14: 点三个点弹玻璃弹窗 (按钮独立点击区, 不触发行的 onClick)
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
    }
}

@Composable
private fun CenterLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Color(0xFFC96442))
}

@Composable
private fun CenterMessage(msg: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(msg, color = Color(0xFF87867F))
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
