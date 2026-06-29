package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.ManagementRepository
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.download.AppDownloadManager
import com.threel.openlist.data.model.FsItem
import com.threel.openlist.ui.component.LiquidGlassCard
import com.threel.openlist.ui.component.LiquidGlassPrimaryButton
import com.threel.openlist.ui.component.LiquidGlassRow
import com.threel.openlist.util.TelemetryLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private val downloadManager: AppDownloadManager,
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
        _action.value = FileActionState(busy = true, message = "开始下载 $fileName...")
        downloadManager.enqueue(remotePath, fileName)
        _action.value = FileActionState(message = "已加入下载队列")
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

    fun delete(dir: String, name: String) {
        _action.value = FileActionState(busy = true, message = "删除中...")
        viewModelScope.launch {
            managementRepo.delete(dir, name).onSuccess {
                _action.value = FileActionState(message = "删除成功")
                delay(300)
                load(_state.value.path)
            }.onFailure {
                _action.value = FileActionState(message = "删除失败: ${it.message}", isError = true)
            }
        }
    }
}

// ===== iOS 26 风格 FAB 菜单项 =====
data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

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

    var fabExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<FsItem?>(null) }
    val menuRemotePath = menuItem?.let { item ->
        if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
    }

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

    // 搜索弹窗
    if (searchActive) {
        SearchDialog(
            query = state.searchQuery,
            onQueryChange = vm::updateSearchQuery,
            onSortSelected = { mode -> vm.updateSortMode(mode) },
            sortMode = state.sortMode,
            onDismiss = { searchActive = false; vm.updateSearchQuery("") },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F9FC), Color(0xFFFFFFFF))
                )
            )
    ) {
        // 内容区
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏: 仅退出按钮
            FileBrowserTopBar(
                path = state.path,
                onBack = if (state.path != "/") ({ vm.load(vm.goUp()) }) else null,
                onLogout = onLogout,
            )

            // 文件列表
            when {
                state.loading -> CenterLoading()
                state.error != null -> CenterMessage(state.error!!, "刷新")
                state.items.isEmpty() -> CenterMessage(
                    msg = if (state.path == "/") "还没有挂载任何网盘" else "空目录",
                    actionLabel = if (state.path != "/") "返回上一层" else null,
                    onAction = if (state.path != "/") ({ vm.load(vm.goUp()) }) else null,
                )
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.path != "/") {
                            item {
                                FileRow(
                                    icon = { Icon(Icons.Outlined.Folder, null, tint = Color(0xFF141413)) },
                                    name = "..", size = "", modified = "",
                                    onClick = { vm.load(vm.goUp()) },
                                    onLongClick = null, onMenuClick = null,
                                )
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

        // 悬浮按钮区域 (右下角)
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 展开的子菜单
                if (fabExpanded) {
                    val menuItems = listOf(
                        FabMenuItem(Icons.Outlined.Search, "搜索") { searchActive = true; fabExpanded = false },
                        FabMenuItem(Icons.Outlined.Refresh, "刷新") { vm.refresh(); fabExpanded = false },
                        FabMenuItem(Icons.Outlined.CreateNewFolder, "新建文件夹") { showMkdirDialog = true; fabExpanded = false },
                        FabMenuItem(Icons.Outlined.Upload, "上传文件") { pickFileLauncher.launch("*/*"); fabExpanded = false },
                        FabMenuItem(Icons.Outlined.Settings, "管理") { onManagement(); fabExpanded = false },
                        FabMenuItem(Icons.Outlined.Info, "关于") { onAbout(); fabExpanded = false },
                    )
                    menuItems.forEachIndexed { index, item ->
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 200,
                                    delayMillis = index * 30,
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 200,
                                    delayMillis = index * 30,
                                ),
                                initialOffsetY = { it },
                            ),
                            exit = fadeOut(animationSpec = tween(150)),
                        ) {
                            FabMenuItemRow(item)
                        }
                    }
                }

                // 主 FAB 按钮（绿色渐变）
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = Color(0xFF20C997),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.MoreHoriz,
                        contentDescription = "菜单",
                        modifier = Modifier.size(26.dp),
                    )
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
            val dir = state.path
            val name = item.name
            DeleteDialog(fileName = item.name, onDismiss = { showDeleteDialog = null }, onDelete = { vm.delete(dir, name); showDeleteDialog = null })
        }
    }
}

// ===== 顶部栏: 仅退出 =====
@Composable
private fun FileBrowserTopBar(
    path: String,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    val title = if (path == "/") "三页云盘" else path.substringAfterLast('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回按钮 (仅非根目录显示)
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF141413))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }

        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF141413),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 退出按钮
        IconButton(onClick = onLogout) {
            Icon(Icons.Outlined.Logout, contentDescription = "退出", tint = Color(0xFF141413))
        }
    }
}

// ===== FAB 菜单项行 =====
@Composable
private fun FabMenuItemRow(item: FabMenuItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // 标签
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = item.label,
                color = Color(0xFF141413),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 小圆形图标按钮
        SmallFloatingActionButton(
            onClick = item.onClick,
            containerColor = Color.White,
            contentColor = Color(0xFF20C997),
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp))
        }
    }
}

// ===== 搜索弹窗 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onSortSelected: (SortMode) -> Unit,
    sortMode: SortMode,
    onDismiss: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索文件", color = Color(0xFF141413)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = onQueryChange,
                    placeholder = { Text("输入文件名...", color = Color(0xFF87867F)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF141413),
                        unfocusedBorderColor = Color(0xFFD9D8D4),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // 排序选择
                Box {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = null, tint = Color(0xFF141413), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("排序: ${sortModeLabel(sortMode)}", color = Color(0xFF141413))
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        listOf(
                            SortMode.DEFAULT to "默认排序",
                            SortMode.NAME_ASC to "名称 A→Z",
                            SortMode.NAME_DESC to "名称 Z→A",
                            SortMode.SIZE_ASC to "大小 小→大",
                            SortMode.SIZE_DESC to "大小 大→小",
                            SortMode.DATE_ASC to "日期 旧→新",
                            SortMode.DATE_DESC to "日期 新→旧",
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onSortSelected(mode); sortMenuExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Color(0xFF141413))
            }
        },
    )
}

private fun sortModeLabel(mode: SortMode) = when (mode) {
    SortMode.DEFAULT -> "默认"
    SortMode.NAME_ASC, SortMode.NAME_DESC -> "名称"
    SortMode.SIZE_ASC, SortMode.SIZE_DESC -> "大小"
    SortMode.DATE_ASC, SortMode.DATE_DESC -> "日期"
}

// ===== 文件行 =====
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
        onLongClick = onLongClick,
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

// ===== 文件操作弹窗 =====
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
                    Text("取消", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF2A2925), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
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

// ===== 文件操作弹窗 =====

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
