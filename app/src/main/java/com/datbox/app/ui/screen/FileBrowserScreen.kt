package com.datbox.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.datbox.app.ui.theme.StoneGray
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
import com.datbox.app.data.api.ManagementRepository
import com.datbox.app.data.api.OpenListRepository
import com.datbox.app.data.download.AppDownloadManager
import com.datbox.app.data.model.FsItem
import com.datbox.app.data.model.FsSearchNode
import com.datbox.app.ui.component.LiquidGlassAlertDialog
import com.datbox.app.ui.component.LiquidGlassCard
import com.datbox.app.ui.component.LiquidGlassPrimaryButton
import com.datbox.app.ui.component.LiquidGlassRow
import com.datbox.app.ui.component.LiquidGlassSurface
import com.datbox.app.ui.component.GlassDecorBackground
import com.datbox.app.util.TelemetryLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    val searching: Boolean = false,
    val searchResults: List<FsSearchNode> = emptyList(),
    val searchError: String? = null,
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

    private var searchJob: Job? = null

    /** 输入关键词 → 350ms 防抖 → 服务端检索 (取消上一轮在途请求, 结果不会串) */
    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _state.value = _state.value.copy(searching = false, searchResults = emptyList(), searchError = null)
            return
        }
        // 按键即置 searching, 覆盖防抖窗口, 避免在旧结果为空时闪现「没有找到」
        _state.value = _state.value.copy(searching = true, searchError = null)
        searchJob = viewModelScope.launch {
            delay(350)
            val result = repo.search(q)
            // repo 的 runCatching 会吞掉 CancellationException: 已被新输入取消就直接终止, 不写状态
            ensureActive()
            result.onSuccess { hits ->
                _state.value = _state.value.copy(searching = false, searchResults = hits)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    searching = false,
                    searchResults = emptyList(),
                    searchError = e.message ?: "搜索失败",
                )
            }
        }
    }

    /** 退出搜索态: 清查询/结果/在途请求 */
    fun closeSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searchQuery = "",
            searching = false,
            searchResults = emptyList(),
            searchError = null,
        )
    }

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
            TelemetryLog.e("FileBrowserVM", "buildShareUrl FAIL: ${remotePath.substringAfterLast('/')}", e)
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
    onPreview: (String, String) -> Unit = { _, _ -> },
    searchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    vm: FileBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val action by vm.action.collectAsState()
    val displayedItems = vm.displayedItems
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 系统返回: 搜索态先退出搜索; 子目录拦截 = 返回上一级; 根目录放行交给系统退出
    BackHandler(enabled = searchActive || state.path != "/") {
        if (searchActive) {
            onSearchActiveChange(false)
            vm.closeSearch()
        } else {
            vm.load(vm.goUp())
        }
    }

    var menuItem by remember { mutableStateOf<FsItem?>(null) }
    val menuRemotePath = menuItem?.let { item ->
        if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
    }

    var showMkdirDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FsItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf<FsItem?>(null) }
    var shareSheetUrl by remember { mutableStateOf<String?>(null) }
    var shareLoading by remember { mutableStateOf(false) }

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

    // 搜索态退出 (dock toggle / 外部关闭) 时清查询与结果, 与返回箭头路径一致
    LaunchedEffect(searchActive) {
        if (!searchActive && state.searchQuery.isNotEmpty()) {
            vm.closeSearch()
        }
    }

    LaunchedEffect(action.message) {
        action.message?.let {
            Toast.makeText(context, it, if (action.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    GlassDecorBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        // 内容区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var accX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accX = 0f },
                        onHorizontalDrag = { _, dragAmount -> accX += dragAmount },
                        onDragEnd = {
                            // 右滑超过阈值且不在根目录时, 返回上一级
                            if (accX > 100f && vm.state.value.path != "/") {
                                vm.load(vm.goUp())
                            }
                        },
                        onDragCancel = { accX = 0f }
                    )
                }
        ) {
            // 顶部栏: 搜索态换成内联搜索条 (不弹窗), 浏览态保留返回/标题/排序/上传
            if (searchActive) {
                SearchBarRow(
                    query = state.searchQuery,
                    onQueryChange = vm::updateSearchQuery,
                    onClose = {
                        onSearchActiveChange(false)
                        vm.closeSearch()
                    },
                )
            } else {
                FileBrowserTopBar(
                    path = state.path,
                    onLogout = onLogout,
                    onSearchClick = { onSearchActiveChange(true) },
                    onUpload = { pickFileLauncher.launch("*/*") },
                    onSortSelected = vm::updateSortMode,
                )
            }

            // 列表: 搜索态显示服务端检索结果, 否则文件列表
            if (searchActive) {
                SearchResultsList(
                    state = state,
                    onRetry = { vm.updateSearchQuery(state.searchQuery) },
                    modifier = Modifier.weight(1f),
                    onOpen = { path, name, isDir ->
                        if (isDir) {
                            // 点目录结果 → 退出搜索并进入该目录
                            onSearchActiveChange(false)
                            vm.closeSearch()
                            vm.load(path)
                        } else {
                            onPreview(path, name)
                        }
                    },
                )
            } else when {
                state.loading -> CenterLoading()
                state.error != null -> CenterMessage(state.error!!, "刷新")
                state.items.isEmpty() -> CenterMessage(
                    msg = if (state.path == "/") "还没有挂载任何网盘" else "空目录",
                    actionLabel = if (state.path != "/") "返回上一层" else null,
                    onAction = if (state.path != "/") ({ vm.load(vm.goUp()) }) else null,
                )
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), // bottom: dock 悬浮区留白 (内容从玻璃下滚过)
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(displayedItems) { item ->
                            val onMenu = if (!item.isDir) { { menuItem = item } } else null
                            val onLongClick = { menuItem = item }
                            val fileStyle = fileStyleFor(item.name, item.isDir)
                            FileRow(
                                icon = { FileTypeIconBox(fileStyle) },
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

        // 文件操作弹窗（底部 Sheet）
        val pending = menuItem
        if (pending != null && menuRemotePath != null) {
            val pendingStyle = fileStyleFor(pending.name, pending.isDir)
            GlassActionDialog(
                fileName = pending.name,
                fileStyle = pendingStyle,
                shareUrl = shareSheetUrl,
                shareLoading = shareLoading,
                onDownload = {
                    vm.downloadFile(menuRemotePath, pending.name)
                    menuItem = null; shareSheetUrl = null; shareLoading = false
                },
                onRequestShare = {
                    val target = menuItem        // 捕获目标文件的 state 引用, 回来时比对(读到的是最新值)
                    val requestPath = menuRemotePath
                    scope.launch {
                        shareLoading = true
                        try {
                            val url = vm.buildShareUrl(requestPath)
                            // 请求期间弹窗被关(menuItem=null)/已切到别的文件(换对象) → 丢弃结果,
                            // 否则上一个文件的链接会残给下一个文件 (串数据)
                            if (menuItem === target) shareSheetUrl = url
                        } catch (e: Exception) {
                            if (menuItem === target) shareSheetUrl = null
                            Toast.makeText(context, "生成分享链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        if (menuItem === target) shareLoading = false
                    }
                },
                onCopyShare = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("分享链接", shareSheetUrl ?: ""))
                    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                },
                onSystemShare = { url ->
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                    context.startActivity(Intent.createChooser(intent, "分享 ${pending.name}"))
                    menuItem = null
                },
                onRename = { menuItem = null; showRenameDialog = pending },
                onDelete = { menuItem = null; showDeleteDialog = pending },
                onDismiss = { menuItem = null; shareSheetUrl = null; shareLoading = false },
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

// ===== 顶部栏: 品牌标识 + 搜索 + 排序 + 上传 (无返回箭头, 上一级走系统返回) =====
@Composable
private fun FileBrowserTopBar(
    path: String,
    onLogout: () -> Unit,
    onSearchClick: () -> Unit = {},
    onUpload: () -> Unit = {},
    onSortSelected: (SortMode) -> Unit = {},
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val title = if (path == "/") "数匣" else path.substringAfterLast('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题 / 根目录即品牌标识: 放大加粗 (iOS 大标题感)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF141413),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 搜索入口 (从 dock 移至顶栏: 点开 SearchBarRow 内联搜索, 不弹窗)
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = Color(0xFF141413))
        }

        // 排序 (原搜索弹窗里的入口)
        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.Outlined.Sort, contentDescription = "排序", tint = Color(0xFF141413))
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

        // 上传按钮 (右上角加号)
        IconButton(onClick = onUpload) {
            Icon(Icons.Outlined.Add, contentDescription = "上传", tint = Color(0xFF141413))
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
        LiquidGlassSurface(
            shape = RoundedCornerShape(12.dp),
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

// ===== 内联搜索 (无弹窗) =====
/**
 * 内联搜索条 — 搜索态替换顶部栏 (不弹窗)。
 * 玻璃胶囊字段: 返回按钮 + 药丸玻璃容器 (前置搜索图标 / 透明输入框 / 清除按钮), 进入即聚焦。
 */
@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "退出搜索", tint = Color(0xFF141413))
        }
        LiquidGlassCard(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            cornerRadius = 24.dp,
            contentPadding = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color(0xFF87867F),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF141413)),
                    cursorBrush = SolidColor(Color(0xFF141413)),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("搜索全盘文件...", color = Color(0xFF87867F))
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "清除",
                            tint = Color(0xFF87867F),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索结果列表 — 服务端检索 (POST /api/fs/search, parent="/" 全盘, 350ms 防抖)。
 * 点目录 → 退出搜索并进入该目录; 点文件 → 预览。不弹任何窗。
 * 结果不带长按菜单 (菜单路径依赖当前目录, 与结果所在目录不符)。
 */
@Composable
private fun SearchResultsList(
    state: FileBrowserState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (path: String, name: String, isDir: Boolean) -> Unit,
) {
    when {
        state.searchQuery.isBlank() -> CenterMessage("输入关键词, 搜索全盘文件")
        state.searching -> CenterLoading()
        state.searchError != null -> CenterMessage("搜索失败: ${state.searchError}", "重试", onRetry)
        state.searchResults.isEmpty() -> CenterMessage("没有找到「${state.searchQuery.trim()}」")
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), // bottom: dock 悬浮区留白 (内容从玻璃下滚过)
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
        ) {
            item(key = "header") {
                Text(
                    text = "“${state.searchQuery.trim()}” · ${state.searchResults.size} 个结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                )
            }
            items(state.searchResults) { hit ->
                val hitPath = if (hit.parent.isEmpty() || hit.parent == "/") {
                    "/${hit.name}"
                } else {
                    "${hit.parent.trimEnd('/')}/${hit.name}"
                }
                FileRow(
                    icon = { FileTypeIconBox(fileStyleFor(hit.name, hit.isDir)) },
                    name = hit.name,
                    size = if (hit.isDir) "" else humanSize(hit.size),
                    modified = "",
                    onClick = { onOpen(hitPath, hit.name, hit.isDir) },
                    onLongClick = null,
                    onMenuClick = null,
                )
            }
        }
    }
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
        cornerRadius = 14.dp,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = icon,
        title = name,
        subtitle = if (modified.isNotEmpty() || size.isNotEmpty()) listOf(modified, size).filter { it.isNotEmpty() }.joinToString(" · ") else null,
        trailing = if (onMenuClick != null) {
            {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = StoneGray, modifier = Modifier.size(20.dp))
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

// ===== 文件操作弹窗（底部 Sheet）=====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassActionDialog(
    fileName: String,
    fileStyle: FileStyle,
    shareUrl: String?,
    shareLoading: Boolean,
    onDownload: () -> Unit,
    onRequestShare: () -> Unit,
    onCopyShare: () -> Unit,
    onSystemShare: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFBFAF7),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD8D6CF)))
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                FileTypeIconBox(fileStyle)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (shareUrl == null) "文件操作" else "分享此文件", fontSize = 12.sp, color = Color(0xFF87867F))
                }
            }
            HorizontalDivider(color = Color(0xFFECEAE3))
            Spacer(Modifier.height(2.dp))

            // 下载
            GlassActionItem(icon = Icons.Outlined.Download, iconTint = Color(0xFF141413), label = "下载到本地", subLabel = "保存到下载目录", onClick = onDownload)

            // 分享区块
            when {
                shareLoading -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF20C997))
                        Spacer(Modifier.width(12.dp))
                        Text("正在生成分享链接…", fontSize = 14.sp, color = Color(0xFF87867F))
                    }
                }
                shareUrl == null -> {
                    GlassActionItem(icon = Icons.Outlined.Share, iconTint = Color(0xFF141413), label = "分享链接", subLabel = "获取可分享短链", onClick = onRequestShare)
                }
                else -> {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(shareUrl, fontSize = 12.sp, color = Color(0xFF56544E), modifier = Modifier.fillMaxWidth().padding(14.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onCopyShare), color = Color(0xFF20C997)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("复制链接", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = { onSystemShare(shareUrl) }), color = Color(0xFF2A2925)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("系统分享", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            GlassActionItem(icon = Icons.Outlined.Edit, iconTint = Color(0xFF141413), label = "重命名", subLabel = "修改文件名", onClick = onRename)
            GlassActionItem(icon = Icons.Outlined.Delete, iconTint = Color(0xFFFF3B30), label = "删除", subLabel = "永久删除此文件", onClick = onDelete)
        }
    }
}

@Composable
private fun GlassActionItem(icon: ImageVector, iconTint: Color, label: String, subLabel: String, onClick: () -> Unit) {
    LiquidGlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
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
    LiquidGlassAlertDialog(
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
    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { LiquidGlassPrimaryButton(text = "重命名", enabled = name.isNotBlank() && name != currentName, onClick = { onRename(name.trim()) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteDialog(fileName: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    LiquidGlassAlertDialog(
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

/**
 * 文件类型视觉描述：圆角方块底色 + 前景色 + 可选的 1~4 字符短码或 Material 图标。
 * "letter-avatar" 风格（macOS Finder / Notion / Google Drive 列表同款），
 * 让 7z vs ISO vs TAR 这类同大类细分类型也能一眼区分。
 */
private data class FileStyle(
    val bg: Color,
    val fg: Color,
    val label: String? = null,
    val icon: ImageVector? = null,
)

private fun fileStyleFor(name: String, isDir: Boolean): FileStyle {
    if (isDir) return FileStyle(bg = Color(0xFFFCEEDC), fg = Color(0xFFB0701F), icon = Icons.Outlined.Folder)
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), icon = Icons.Outlined.InsertDriveFile)
    return when (ext) {
        // ---- 压缩包：每种格式一个色相 + 短码 ----
        "zip" -> FileStyle(bg = Color(0xFFFFEAD3), fg = Color(0xFF9A4F12), label = "ZIP")
        "7z"  -> FileStyle(bg = Color(0xFFFFE0CC), fg = Color(0xFF8A3F08), label = "7Z")
        "rar" -> FileStyle(bg = Color(0xFFFFE0CC), fg = Color(0xFF8A3F08), label = "RAR")
        "jar" -> FileStyle(bg = Color(0xFFFFEAD3), fg = Color(0xFF9A4F12), label = "JAR")
        "tar", "tgz"   -> FileStyle(bg = Color(0xFFE6E0CF), fg = Color(0xFF4A2F12), label = "TAR")
        "gz", "gzip"   -> FileStyle(bg = Color(0xFFE6E0CF), fg = Color(0xFF4A2F12), label = "GZ")
        "bz2", "tbz2"  -> FileStyle(bg = Color(0xFFE6E0CF), fg = Color(0xFF4A2F12), label = "BZ2")
        "xz", "txz"    -> FileStyle(bg = Color(0xFFE6E0CF), fg = Color(0xFF4A2F12), label = "XZ")
        "lz", "lzma", "zst" -> FileStyle(bg = Color(0xFFE6E0CF), fg = Color(0xFF4A2F12), label = ext.uppercase().take(3))

        // ---- 碟镜像：深蓝 ----
        "iso", "img", "dmg" -> FileStyle(bg = Color(0xFFDDE4FB), fg = Color(0xFF2742A0), label = ext.uppercase())

        // ---- Android 包：保留 Android 机器头图标 ----
        "apk" -> FileStyle(bg = Color(0xFFDCFCE7), fg = Color(0xFF1F8A4D), icon = Icons.Outlined.Android)

        // ---- 可执行 / 脚本：石板灰 + 短码 ----
        "exe", "msi", "bat", "cmd" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "EXE")
        "sh", "bash", "zsh", "fish" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "SH")
        "ps1" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "PS1")
        "deb" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "DEB")
        "rpm" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "RPM")
        "bin", "run" -> FileStyle(bg = Color(0xFFE8EEF4), fg = Color(0xFF334155), label = "BIN")

        // ---- 多媒体：保留通用图标（图 / 胶片 / 音符） ----
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "ico", "raw", "tiff", "tif" ->
            FileStyle(bg = Color(0xFFFCE4EC), fg = Color(0xFFB23155), icon = Icons.Outlined.Image)
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm", "ts", "m2ts", "3gp" ->
            FileStyle(bg = Color(0xFFE0E8FE), fg = Color(0xFF1F3F8E), icon = Icons.Outlined.Movie)
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" ->
            FileStyle(bg = Color(0xFFFFEED4), fg = Color(0xFF8C5010), icon = Icons.Outlined.MusicNote)

        // ---- PDF：全球通用短码 ----
        "pdf" -> FileStyle(bg = Color(0xFFFCE0DC), fg = Color(0xFF8C1810), label = "PDF")

        // ---- 代码：按语言细分配色，短码即语言名 ----
        "py"  -> FileStyle(bg = Color(0xFFE1F5EE), fg = Color(0xFF0F6E56), label = "PY")
        "js", "jsx" -> FileStyle(bg = Color(0xFFFEF3C7), fg = Color(0xFF854F0B), label = "JS")
        "ts", "tsx" -> FileStyle(bg = Color(0xFFE0E8FE), fg = Color(0xFF1F3F8E), label = "TS")
        "java" -> FileStyle(bg = Color(0xFFFCE0DC), fg = Color(0xFF8C1810), label = "JAVA")
        "kt", "kts" -> FileStyle(bg = Color(0xFFE0EAFE), fg = Color(0xFF2742A0), label = "KT")
        "go"  -> FileStyle(bg = Color(0xFFE1F5EE), fg = Color(0xFF0F6E56), label = "GO")
        "rs", "rust" -> FileStyle(bg = Color(0xFFFEF3C7), fg = Color(0xFF854F0B), label = "RS")
        "swift" -> FileStyle(bg = Color(0xFFFCEAD3), fg = Color(0xFF9A4F12), label = "SWIFT")
        "rb"  -> FileStyle(bg = Color(0xFFFCE0DC), fg = Color(0xFF8C1810), label = "RB")
        "php" -> FileStyle(bg = Color(0xFFE0E0F8), fg = Color(0xFF3A2F88), label = "PHP")
        "c", "h" -> FileStyle(bg = Color(0xFFE0E0F8), fg = Color(0xFF3A2F88), label = "C")
        "cpp", "cc", "cxx", "hpp" -> FileStyle(bg = Color(0xFFE0E0F8), fg = Color(0xFF3A2F88), label = "C++")
        "sql" -> FileStyle(bg = Color(0xFFFBEED1), fg = Color(0xFF7A4D08), label = "SQL")
        "json" -> FileStyle(bg = Color(0xFFE6F0E8), fg = Color(0xFF246F44), label = "JSON")
        "xml" -> FileStyle(bg = Color(0xFFE0E8FE), fg = Color(0xFF2742A0), label = "XML")
        "yaml", "yml" -> FileStyle(bg = Color(0xFFFBEED1), fg = Color(0xFF7A4D08), label = "YML")
        "toml", "ini", "conf" -> FileStyle(bg = Color(0xFFFBEED1), fg = Color(0xFF7A4D08), label = "CFG")
        "gradle" -> FileStyle(bg = Color(0xFFE1F5EE), fg = Color(0xFF0F6E56), label = "GRD")
        "dart" -> FileStyle(bg = Color(0xFFE0E8FE), fg = Color(0xFF1F3F8E), label = "DART")
        "html", "htm" -> FileStyle(bg = Color(0xFFFCEAD3), fg = Color(0xFF9A4F12), label = "HTML")
        "css", "scss", "sass", "less" -> FileStyle(bg = Color(0xFFE0E0F8), fg = Color(0xFF3A2F88), label = "CSS")

        // ---- 文本 ----
        "txt" -> FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), label = "TXT")
        "md", "markdown" -> FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), label = "MD")
        "log" -> FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), label = "LOG")
        "rst", "tex" -> FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), label = ext.uppercase().take(3))

        // ---- Office 套件：W / X / P 通用约定 ----
        "doc", "docx", "rtf", "odt" -> FileStyle(bg = Color(0xFFE0EAFE), fg = Color(0xFF1548A0), label = "W")
        "xls", "xlsx", "ods", "csv" -> FileStyle(bg = Color(0xFFDEF4E8), fg = Color(0xFF126F44), label = "X")
        "ppt", "pptx", "odp", "key" -> FileStyle(bg = Color(0xFFFCE9DB), fg = Color(0xFF97441A), label = "P")

        // ---- 兜底：未知扩展名也走短码（最多 4 字符） ----
        else -> FileStyle(bg = Color(0xFFF0EDE5), fg = Color(0xFF57544C), label = ext.uppercase().take(4))
    }
}

/** 渲染文件类型徽章：bg 圆角色块 + 短码文字 或 Material 图标 */
@Composable
private fun FileTypeIconBox(style: FileStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(style.bg),
        contentAlignment = Alignment.Center,
    ) {
        when {
            style.label != null -> Text(
                text = style.label,
                color = style.fg,
                fontWeight = FontWeight.Bold,
                fontSize = when (style.label.length) { 1 -> 22.sp; 2 -> 15.sp; else -> 12.sp },
                maxLines = 1,
            )
            style.icon != null -> Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = style.fg,
            )
        }
    }
}
