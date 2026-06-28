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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.ManagementRepository
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.model.FsItem
import com.threel.openlist.ui.component.LiquidGlassCard
import com.threel.openlist.ui.component.LiquidGlassFab
import com.threel.openlist.ui.component.LiquidGlassRow
import com.threel.openlist.ui.component.LiquidGlassTopBar
import com.threel.openlist.util.TelemetryLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.DecimalFormat
import javax.inject.Inject

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

data class FileActionState(val busy: Boolean = false, val message: String? = null, val isError: Boolean = false)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repo: OpenListRepository,
    private val managementRepo: ManagementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FileBrowserState())
    val state = _state.asStateFlow()
    private val _action = MutableStateFlow(FileActionState())
    val action = _action.asStateFlow()

    init { load("/") }

    private val hiddenRootDirs: Set<String> = emptySet()
    private val hiddenSubDirs: Set<String> = setOf("天翼云盘飞牛备份")

    val displayedItems: List<FsItem>
        get() {
            val query = _state.value.searchQuery.trim()
            val filtered = if (query.isEmpty()) _state.value.items else _state.value.items.filter { it.name.contains(query, ignoreCase = true) }
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

    fun updateSearchQuery(query: String) { _state.value = _state.value.copy(searchQuery = query) }
    fun updateSortMode(mode: SortMode) { _state.value = _state.value.copy(sortMode = mode) }

    fun load(path: String) {
        _state.value = _state.value.copy(loading = true, path = path, error = null)
        viewModelScope.launch {
            repo.list(path).onSuccess { items ->
                val visible = when {
                    path == "/" -> items.filter { it.name !in hiddenRootDirs }
                    else -> items.filter { it.name !in hiddenSubDirs }
                }
                _state.value = _state.value.copy(items = visible, loading = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun refresh() {
        val currentPath = _state.value.path
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            repo.list(currentPath).onSuccess { items ->
                val visible = when {
                    currentPath == "/" -> items.filter { it.name !in hiddenRootDirs }
                    else -> items.filter { it.name !in hiddenSubDirs }
                }
                _state.value = _state.value.copy(items = visible, isRefreshing = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(isRefreshing = false, error = e.message ?: "刷新失败")
            }
        }
    }

    fun goUp(): String {
        val p = _state.value.path.trimEnd('/')
        return if (p.isEmpty() || p == "/") "/" else p.substringBeforeLast('/').ifEmpty { "/" }
    }

    fun downloadFile(remotePath: String, fileName: String) {
        _action.value = FileActionState(busy = true, message = "下载 $fileName 中...")
        viewModelScope.launch {
            repo.download(remotePath, fileName).onSuccess { file ->
                _action.value = FileActionState(message = "已下载到 ${file.absolutePath}")
            }.onFailure { e ->
                _action.value = FileActionState(message = "下载失败: ${e.message}", isError = true)
            }
        }
    }

    fun uploadFile(localFile: File) {
        val targetDir = _state.value.path
        _action.value = FileActionState(busy = true, message = "上传 ${localFile.name} 中...")
        viewModelScope.launch {
            repo.upload(targetDir, localFile).onSuccess { resp ->
                if (resp.code == 200) {
                    _action.value = FileActionState(message = "${localFile.name} 上传成功")
                    load(targetDir)
                } else {
                    _action.value = FileActionState(message = "上传失败: ${resp.message}", isError = true)
                }
            }.onFailure { e ->
                _action.value = FileActionState(message = "上传失败: ${e.message}", isError = true)
            }
        }
    }

    suspend fun buildShareUrl(remotePath: String): String {
        return try {
            repo.buildShortShareUrl(remotePath)
        } catch (e: Throwable) {
            TelemetryLog.e("FileBrowserVM", "buildShareUrl FAIL: $remotePath", e)
            throw e
        }
    }

    fun clearMessage() { _action.value = FileActionState() }

    // v0.3.37: 文件操作
    fun mkdir(name: String) {
        val path = _state.value.path
        _action.value = FileActionState(busy = true, message = "创建 $name 中...")
        viewModelScope.launch {
            managementRepo.mkdir(path, name).onSuccess {
                _action.value = FileActionState(message = "$name 创建成功")
                load(path)
            }.onFailure {
                _action.value = FileActionState(message = "创建失败: ${it.message}", isError = true)
            }
        }
    }

    fun rename(path: String, newName: String) {
        _action.value = FileActionState(busy = true, message = "重命名中...")
        viewModelScope.launch {
            managementRepo.rename(path, newName).onSuccess {
                _action.value = FileActionState(message = "重命名为 $newName 成功")
                load(_state.value.path)
            }.onFailure {
                _action.value = FileActionState(message = "重命名失败: ${it.message}", isError = true)
            }
        }
    }

    fun delete(path: String) {
        _action.value = FileActionState(busy = true, message = "删除中...")
        viewModelScope.launch {
            managementRepo.delete(path).onSuccess {
                _action.value = FileActionState(message = "删除成功")
                load(_state.value.path)
            }.onFailure {
                _action.value = FileActionState(message = "删除失败: ${it.message}", isError = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onLogout: () -> Unit,
    onAbout: () -> Unit = {},
    onManagement: () -> Unit = {},
    onPreview: (String, String) -> Unit = { _, _ -> },
    vm: FileBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val action by vm.action.collectAsState()
    val displayedItems = vm.displayedItems
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<FsItem?>(null) }
    val menuRemotePath = menuItem?.let { item ->
        if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
    }

    // v0.3.37: 弹窗状态
    var showMkdirDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FsItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf<FsItem?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
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

    LaunchedEffect(action.message) {
        action.message?.let {
            Toast.makeText(context, it, if (action.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = vm::updateSearchQuery,
                    onBack = { searchActive = false; vm.updateSearchQuery("") },
                    sortMode = state.sortMode,
                    onSortClick = { sortMenuExpanded = true },
                    onSortSelected = { mode -> vm.updateSortMode(mode); sortMenuExpanded = false },
                    sortMenuExpanded = sortMenuExpanded,
                    onDismissSortMenu = { sortMenuExpanded = false },
                    onAbout = onAbout,
                    onLogout = onLogout,
                    onManagement = onManagement,
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
                LiquidGlassFab(icon = Icons.Filled.Refresh, enabled = false, onClick = {})
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.path != "/") {
                        SmallFloatingActionButton(
                            onClick = { showMkdirDialog = true },
                            containerColor = Color.White.copy(alpha = 0.85f),
                            contentColor = Color(0xFF141413),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                    }
                    LiquidGlassFab(icon = Icons.Filled.Add, onClick = { pickFileLauncher.launch("*/*") })
                }
            }
        },
        containerColor = Color(0xFFF5F4ED),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CenterLoading()
                state.error != null -> CenterMessage(state.error!!, "刷新")
                state.items.isEmpty() -> CenterMessage(
                    msg = if (state.path == "/") "还没有挂载任何网盘" else "空目录",
                    actionLabel = if (state.path != "/") "返回上一层" else null,
                    onAction = if (state.path != "/") ({ vm.load(vm.goUp()) }) else null,
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.path != "/") {
                            item {
                                FileRow(icon = { Icon(Icons.Outlined.Folder, null, tint = Color(0xFF141413)) }, name = "..", size = "", modified = "", onClick = { vm.load(vm.goUp()) }, onLongClick = null, onMenuClick = null)
                            }
                        }
                        items(displayedItems) { item ->
                            val onMenu = if (!item.isDir) { { menuItem = item } } else null
                            val onLongClick = { menuItem = item }
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
                                    } else {
                                        val filePath = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
                                        onPreview(filePath, item.name)
                                    }
                                },
                                onLongClick = onLongClick,
                                onMenuClick = onMenu,
                            )
                        }
                    }
                }
            }
        }

        // 文件操作弹窗
        val pending = menuItem
        if (pending != null && menuRemotePath != null) {
            GlassActionDialog(
                fileName = pending.name,
                onDownload = { vm.downloadFile(menuRemotePath, pending.name) },
                onShare = {
                    scope.launch {
                        val url = vm.buildShareUrl(menuRemotePath)
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                        context.startActivity(Intent.createChooser(intent, "分享 ${pending.name}"))
                    }
                },
                onRename = { menuItem = null; showRenameDialog = pending },
                onDelete = { menuItem = null; showDeleteDialog = pending },
                onDismiss = { menuItem = null },
            )
        }

        // 新建文件夹弹窗
        if (showMkdirDialog) {
            MkdirDialog(onDismiss = { showMkdirDialog = false }, onCreate = { name -> vm.mkdir(name); showMkdirDialog = false })
        }

        // 重命名弹窗
        showRenameDialog?.let { item ->
            val itemPath = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
            RenameDialog(currentName = item.name, onDismiss = { showRenameDialog = null }, onRename = { newName -> vm.rename(itemPath, newName); showRenameDialog = null })
        }

        // 删除弹窗
        showDeleteDialog?.let { item ->
            val itemPath = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
            DeleteDialog(fileName = item.name, onDismiss = { showDeleteDialog = null }, onDelete = { vm.delete(itemPath); showDeleteDialog = null })
        }
    }
}

// ===== 搜索 + 排序 TopBar =====
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
    onManagement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F4ED)).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f),
            placeholder = { Text("搜索文件...", color = Color(0xFF87867F)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF141413), unfocusedBorderColor = Color(0xFFD9D8D4),
            ),
        )
        Box {
            IconButton(onClick = onSortClick) { Icon(Icons.Outlined.List, contentDescription = "排序") }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = onDismissSortMenu) {
                data class SortItem(val mode: SortMode, val label: String)
                val items = listOf(
                    SortItem(SortMode.DEFAULT, "默认排序"), SortItem(SortMode.NAME_ASC, "名称 A → Z"),
                    SortItem(SortMode.NAME_DESC, "名称 Z → A"), SortItem(SortMode.SIZE_ASC, "大小 小→大"),
                    SortItem(SortMode.SIZE_DESC, "大小 大→小"), SortItem(SortMode.DATE_ASC, "日期 旧→新"),
                    SortItem(SortMode.DATE_DESC, "日期 新→旧"),
                )
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { onSortSelected(item.mode) },
                        leadingIcon = if (sortMode == item.mode) { { Icon(Icons.Outlined.Check, modifier = Modifier.size(18.dp)) } } else null,
                    )
                }
            }
        }
        IconButton(onClick = onManagement) { Icon(Icons.Outlined.Settings, contentDescription = "管理") }
        IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, contentDescription = "关于") }
        IconButton(onClick = onLogout) { Icon(Icons.Outlined.Logout, contentDescription = "退出") }
    }
}

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
    LiquidGlassRow(
        cornerRadius = 16.dp,
        selected = selected,
        onClick = onClick,
        leading = icon,
        title = name,
        subtitle = if (modified.isNotEmpty() || size.isNotEmpty()) listOf(modified, size).filter { it.isNotEmpty() }.joinToString(" · ") else null,
        trailing = if (onMenuClick != null) {
            {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Color(0xFF87867F), modifier = Modifier.size(20.dp))
                }
            }
        } else null,
    )
}

@Composable
private fun CenterLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Color(0xFF141413))
}

@Composable
private fun CenterMessage(msg: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LiquidGlassCard(modifier = Modifier.padding(32.dp).widthIn(max = 320.dp), cornerRadius = 24.dp, contentPadding = 24.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = Color(0xFF141413), style = MaterialTheme.typography.bodyLarge)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(12.dp))
                    LiquidGlassPrimaryButton(text = actionLabel, onClick = onAction, icon = Icons.Outlined.ArrowBack)
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
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("文件操作", style = MaterialTheme.typography.labelMedium, color = Color(0xFF87867F), modifier = Modifier.padding(start = 4.dp))
                Text(fileName, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2A2925), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                GlassActionItem(icon = Icons.Outlined.Download, iconTint = Color(0xFF141413), label = "下载到本地", subLabel = "保存到下载目录", onClick = { onDownload(); onDismiss() })
                GlassActionItem(icon = Icons.Outlined.Share, iconTint = Color(0xFF141413), label = "分享链接", subLabel = "复制 / 发送短链", onClick = { onShare(); onDismiss() })
                GlassActionItem(icon = Icons.Outlined.Edit, iconTint = Color(0xFF141413), label = "重命名", subLabel = "修改文件名", onClick = onRename)
                GlassActionItem(icon = Icons.Outlined.Delete, iconTint = Color(0xFFFF3B30), label = "删除", subLabel = "永久删除此文件", onClick = onDelete)
                Spacer(Modifier.height(4.dp))
                Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss() }, color = Color(0xFFF5F4ED).copy(alpha = 0.8f)) {
                    Text("取消", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF2A2925), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun GlassActionItem(icon: ImageVector, iconTint: Color, label: String, subLabel: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), color = Color(0xFFFAF9F5).copy(alpha = 0.8f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF2A2925), fontWeight = FontWeight.Medium)
                Text(subLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF87867F))
            }
        }
    }
}

// ===== v0.3.37: 文件操作弹窗 =====

@Composable
private fun MkdirDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("文件夹名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { LiquidGlassPrimaryButton(text = "创建", enabled = name.isNotBlank(), onClick = { onCreate(name.trim()) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { LiquidGlassPrimaryButton(text = "重命名", enabled = name.isNotBlank() && name != currentName, onClick = { onRename(name.trim()) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteDialog(fileName: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除文件") },
        text = { Text("确认删除「$fileName」？此操作不可撤销。") },
        confirmButton = { LiquidGlassPrimaryButton(text = "删除", onClick = onDelete) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "lz", "lzma", "zst", "iso" -> Icons.Filled.Archive to Color(0xFFD97757)
            "apk", "exe", "msi", "bat", "cmd", "sh", "bash", "zsh", "fish", "ps1", "dmg", "deb", "rpm", "jar", "bin", "run" -> Icons.Filled.Apps to Color(0xFFD97757)
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif" -> Icons.Filled.Image to Color(0xFFD97757)
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp" -> Icons.Filled.Movie to Color(0xFFD97757)
            "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" -> Icons.Filled.MusicNote to Color(0xFFD97757)
            "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFD97757)
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "go", "rust", "rs", "c", "cpp", "cc", "cxx", "h", "hpp", "json", "xml", "yaml", "yml", "toml", "ini", "conf", "gradle", "dart", "swift", "rb", "php", "html", "htm", "css", "scss", "sass", "less", "sql" -> Icons.Filled.Code to Color(0xFFD97757)
            "txt", "md", "markdown", "log", "rst", "tex" -> Icons.Filled.TextSnippet to Color(0xFF141413)
            "doc", "docx", "rtf", "odt" -> Icons.Filled.Description to Color(0xFFD97757)
            "xls", "xlsx", "ods", "csv" -> Icons.Filled.GridView to Color(0xFFD97757)
            "ppt", "pptx", "odp", "key" -> Icons.Filled.Slideshow to Color(0xFFD97757)
            else -> Icons.Filled.SaveAlt to Color(0xFFD97757)
        }
    }
}
