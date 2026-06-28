package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Refresh
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

// SortMode: 搜索排序模式
enum class SortMode { DEFAULT, NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC }

data class FileBrowserState(
    val path: String = "/",
    val items: List<FsItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.DEFAULT,
    val isRefreshing: Boolean = false,
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

    /** 根目录隐藏 (默认空) */
    private val hiddenRootDirs: Set<String> = emptySet()

    /** 任意子目录隐藏 ("天翼云盘飞牛备份" 在 /天翼云盘/ 下面) */
    private val hiddenSubDirs: Set<String> = setOf(
        "天翼云盘飞牛备份",
    )

    /** 排序+过滤后的展示列表 */
    val displayedItems: List<FsItem>
        get() {
            val query = _state.value.searchQuery.trim()
            val filtered = if (query.isEmpty()) {
                _state.value.items
            } else {
                _state.value.items.filter { it.name.contains(query, ignoreCase = true) }
            }
            return when (_state.value.sortMode) {
                SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                SortMode.SIZE_ASC -> filtered.sortedBy { it.size }
                SortMode.SIZE_DESC -> filtered.sortedByDescending { it.size }
                SortMode.DATE_ASC -> filtered.sortedBy { it.modified }
                SortMode.DATE_DESC -> filtered.sortedByDescending { it.modified }
                SortMode.DEFAULT -> filtered
            }
        }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun updateSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    fun load(path: String) {
        _state.value = _state.value.copy(loading = true, path = path, error = null)
        viewModelScope.launch {
            repo.list(path)
                .onSuccess { items ->
                    val visible = when {
                        path == "/" -> items.filter { it.name !in hiddenRootDirs }
                        else -> items.filter { it.name !in hiddenSubDirs }
                    }
                    _state.value = _state.value.copy(items = visible, loading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    /** 下拉刷新 */
    fun refresh() {
        val currentPath = _state.value.path
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            repo.list(currentPath)
                .onSuccess { items ->
                    val visible = when {
                        currentPath == "/" -> items.filter { it.name !in hiddenRootDirs }
                        else -> items.filter { it.name !in hiddenSubDirs }
                    }
                    _state.value = _state.value.copy(items = visible, isRefreshing = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isRefreshing = false, error = e.message ?: "刷新失败")
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

    /**
     * v0.3.29 老板 6/14 20:00 拍: 隐藏中间目录
     * 改用 OpenList 4.x 官方短链 /sd/<id>/<filename> (不露原路径)
     */
    suspend fun buildShareUrl(remotePath: String): String {
        com.threel.openlist.util.TelemetryLog.i("FileBrowserVM", "buildShareUrl: $remotePath")
        return try {
            val url = repo.buildShortShareUrl(remotePath)
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
    onManagement: () -> Unit = {},
    vm: FileBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val action by vm.action.collectAsState()
    val displayedItems = vm.displayedItems
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 搜索栏状态
    var searchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

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
            if (searchActive) {
                // 搜索栏
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = { vm.updateSearchQuery(it) },
                    onBack = { searchActive = false; vm.updateSearchQuery("") },
                    sortMode = state.sortMode,
                    onSortClick = { sortMenuExpanded = true },
                    onSortSelected = { mode -> vm.updateSortMode(mode); sortMenuExpanded = false },
                    sortMenuExpanded = sortMenuExpanded,
                    onDismissSortMenu = { sortMenuExpanded = false },
                    onAbout = onAbout,
                    onLogout = onLogout,
                )
            } else {
                LiquidGlassTopBar(
                    title = "三页云盘 · ${state.path}",
                    leadingIcon = Icons.Outlined.Folder,
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { vm.load(state.path) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = onManagement) {
                            Icon(Icons.Outlined.Settings, contentDescription = "管理")
                        }
                        IconButton(onClick = onAbout) {
                            Icon(Icons.Outlined.Info, contentDescription = "关于")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Outlined.Logout, contentDescription = "退出")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
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
        Box(
            modifier = Modifier
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
                else -> Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.path != "/") {
                            item {
                                FileRow(
                                    icon = { Icon(Icons.Outlined.Folder, null, tint = Color(0xFF141413)) },
                                    name = "..",
                                    size = "",
                                    modified = "",
                                    onClick = { vm.load(vm.goUp()) },
                                    onLongClick = null,
                                    onMenuClick = null,
                                )
                            }
                        }
                        items(displayedItems) { item ->
                            val onMenu = if (!item.isDir) {
                                {
                                    com.threel.openlist.util.TelemetryLog.i("FileBrowser", "user tapped 3-dot menu on file: ${item.name}")
                                    menuItem = item
                                }
                            } else null
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
                                onLongClick = null,
                                onMenuClick = onMenu,
                            )
                        }
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

// ========== 搜索 + 排序 TopBar ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    sortMode: SortMode,
    onSortClick: () -> Unit,
    onSortSelected: (SortMode) -> Unit,
    sortMenuExpanded: Boolean,
    onDismissSortMenu: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F4ED))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索文件...", color = Color(0xFF87867F)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF141413),
                unfocusedBorderColor = Color(0xFFD9D8D4),
            ),
        )
        Box {
            IconButton(onClick = onSortClick) {
                Icon(Icons.Outlined.List, contentDescription = "排序")
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = onDismissSortMenu,
            ) {
                data class SortItem(val mode: SortMode, val label: String)
                val items = listOf(
                    SortItem(SortMode.DEFAULT, "默认排序"),
                    SortItem(SortMode.NAME_ASC, "名称 A → Z"),
                    SortItem(SortMode.NAME_DESC, "名称 Z → A"),
                    SortItem(SortMode.SIZE_ASC, "大小 小→大"),
                    SortItem(SortMode.SIZE_DESC, "大小 大→小"),
                    SortItem(SortMode.DATE_ASC, "日期 旧→新"),
                    SortItem(SortMode.DATE_DESC, "日期 新→旧"),
                )
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { onSortSelected(item.mode) },
                    )
                }
            }
        }
        IconButton(onClick = onManagement) {
            Icon(Icons.Outlined.Settings, contentDescription = "管理")
        }
        IconButton(onClick = onAbout) {
            Icon(Icons.Outlined.Info, contentDescription = "关于")
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.Outlined.Logout, contentDescription = "退出")
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
    selected: Boolean = false,
) {
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
        CircularProgressIndicator(color = Color(0xFF141413))
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
                com.threel.openlist.ui.component.LiquidGlassPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    icon = Icons.Outlined.ArrowBack,
                )
            }
        }
    }
}

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
            color = Color.White.copy(alpha = 0.85f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

                GlassActionItem(
                    icon = Icons.Outlined.Download,
                    iconTint = Color(0xFF141413),
                    label = "下载到本地",
                    subLabel = "保存到下载目录",
                    onClick = { onDownload(); onDismiss() },
                )
                GlassActionItem(
                    icon = Icons.Outlined.Share,
                    iconTint = Color(0xFF141413),
                    label = "分享链接",
                    subLabel = "复制 / 发送短链",
                    onClick = { onShare(); onDismiss() },
                )

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
        color = Color(0xFFFAF9F5).copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

private fun fileIconFor(name: String, isDir: Boolean): Pair<ImageVector, Color> = when {
    isDir -> Icons.Outlined.Folder to Color(0xFF141413)
    else -> {
        val ext = name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "lz", "lzma", "zst", "iso" ->
                Icons.Filled.Archive to Color(0xFFD97757)
            "apk", "exe", "msi", "bat", "cmd", "sh", "bash", "zsh", "fish", "ps1",
            "dmg", "deb", "rpm", "app", "pkg", "jar", "bin", "run" ->
                Icons.Filled.Apps to Color(0xFFD97757)
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif" ->
                Icons.Filled.Image to Color(0xFFD97757)
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp" ->
                Icons.Filled.Movie to Color(0xFFD97757)
            "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" ->
                Icons.Filled.MusicNote to Color(0xFFD97757)
            "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFD97757)
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "go", "rust", "rs",
            "c", "cpp", "cc", "cxx", "h", "hpp", "json", "xml", "yaml", "yml",
            "toml", "ini", "conf", "gradle", "dart", "swift", "rb", "php",
            "html", "htm", "css", "scss", "sass", "less", "sql" ->
                Icons.Filled.Code to Color(0xFFD97757)
            "txt", "md", "markdown", "log", "rst", "tex" ->
                Icons.Filled.TextSnippet to Color(0xFF141413)
            "doc", "docx", "rtf", "odt" ->
                Icons.Filled.Description to Color(0xFFD97757)
            "xls", "xlsx", "ods", "csv" ->
                Icons.Filled.GridView to Color(0xFFD97757)
            "ppt", "pptx", "odp", "key" ->
                Icons.Filled.Slideshow to Color(0xFFD97757)
            else -> Icons.Filled.SaveAlt to Color(0xFFD97757)
        }
    }
}
