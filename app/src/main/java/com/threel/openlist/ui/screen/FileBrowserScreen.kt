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

    /** 老板 6/13 v0.3.0: 分享链接 */
    fun buildShareUrl(remotePath: String): String = repo.buildShareUrl(remotePath)

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
                            )
                        }
                    }
                    items(state.items) { item ->
                        var menuVisible by remember { mutableStateOf(false) }
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
                            onLongClick = if (!item.isDir) {
                                {
                                    val remotePath = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
                                    showFileMenu(
                                        context = context,
                                        onDownload = { vm.downloadFile(remotePath, item.name) },
                                        onShare = {
                                            val url = vm.buildShareUrl(remotePath)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, url)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享 $item.name"))
                                        },
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
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
        if (onLongClick != null) {
            // 给文件加"菜单"小图标 (暗示有更多操作)
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "更多",
                tint = Color(0xFF87867F),
                modifier = Modifier.size(18.dp),
            )
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

/** 老板 6/13 v0.3.0: 文件长按菜单 (下载/分享) - 用 system AlertDialog */
private fun showFileMenu(
    context: android.content.Context,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    val items = arrayOf("下载到本地", "分享链接")
    android.app.AlertDialog.Builder(context)
        .setTitle("文件操作")
        .setItems(items) { dialog: android.content.DialogInterface?, which: Int ->
            when (which) {
                0 -> onDownload()
                1 -> onShare()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

private val sizeFmt = DecimalFormat("#,##0.#")
private fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${sizeFmt.format(b / 1024.0)} KB"
    b < 1024L * 1024 * 1024 -> "${sizeFmt.format(b / 1024.0 / 1024)} MB"
    else -> "${sizeFmt.format(b / 1024.0 / 1024 / 1024)} GB"
}
