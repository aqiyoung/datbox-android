package com.datbox.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.datbox.app.ui.theme.StoneGray
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.R
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
import com.datbox.app.util.AppConfig
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
                val visible = if (path == "/") items else items.filter { it.name !in hiddenSubDirs }
                _state.value = _state.value.copy(items = visible, loading = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "加载失败")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onLogout: () -> Unit,
    onPreview: (String, String) -> Unit = { _, _ -> },
    homeReset: Int = 0,
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

    // 点 dock 首页 = 回根目录: 每次点击都生效 (从子目录点 = 回到顶层; 从其它 tab 切回同样落根目录)
    // 语义是"每次点首页都回根", 所以重新组合时重放也是期望行为, 无 searchTrigger 式误导
    LaunchedEffect(homeReset) {
        if (homeReset > 0 && state.path != "/") {
            vm.load("/")
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
    // 根目录: 左上角显示品牌 logo + 名称; 子目录显示当前目录名
    val title = if (path == "/") "" else path.substringAfterLast('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 根目录: 左上角放品牌 logo + 名称 (原来根目录大标题留空, 左上角显得空)
        if (path == "/") {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.datbox_logo),
                    // 紧邻的品牌文字已表达名称, 图片设为装饰, 避免 TalkBack 连读两次
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = AppConfig.BRAND,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF141413),
                    maxLines = 1,
                )
            }
        } else {
            // 子目录: 显示当前目录名 (放大加粗, iOS 大标题感)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF141413),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

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
) {
    LiquidGlassRow(
        cornerRadius = 14.dp,
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
 * 文件类型图标 (iOS App 图标风): 统一「正方形圆角块」底 + 类型符号,
 * 配色收敛到 iOS 系统系柔和色调; 类型细分靠符号而不是短码文字。
 */
private enum class FileKind(val bg: Color, val fg: Color) {
    FOLDER(Color(0xFFE7F0FE), Color(0xFF3B82F6)),
    DOC(Color(0xFFE8EFFC), Color(0xFF2563EB)),
    TEXT(Color(0xFFF1F0EC), Color(0xFF6B7280)),
    IMAGE(Color(0xFFFDE8ED), Color(0xFFE5484D)),
    VIDEO(Color(0xFFE5EBFB), Color(0xFF4C6EF5)),
    AUDIO(Color(0xFFFDF1DE), Color(0xFFC2751A)),
    ARCHIVE(Color(0xFFFBEBDA), Color(0xFFA05517)),
    CODE(Color(0xFFEDE9FB), Color(0xFF7C5CD6)),
    SHEET(Color(0xFFE2F5E9), Color(0xFF1F9D55)),
    SLIDE(Color(0xFFFCEBDA), Color(0xFFD97706)),
    PDF(Color(0xFFFDE7E4), Color(0xFFDC2626)),
    APK(Color(0xFFE1F7E9), Color(0xFF16A34A)),
    DISK(Color(0xFFE3E9FB), Color(0xFF3B5BDB)),
    EXEC(Color(0xFFE9EFF5), Color(0xFF334155)),
    UNKNOWN(Color(0xFFF0EDE5), Color(0xFF57544C)),
}

private data class FileStyle(val bg: Color, val fg: Color, val kind: FileKind)

private fun fileStyleFor(name: String, isDir: Boolean): FileStyle {
    val kind = fileKindFor(name, isDir)
    return FileStyle(kind.bg, kind.fg, kind)
}

private fun fileKindFor(name: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.FOLDER
    return when (name.substringAfterLast('.', "").lowercase()) {
        "" -> FileKind.UNKNOWN
        // 压缩包
        "zip", "7z", "rar", "jar", "tar", "tgz", "gz", "gzip", "bz2", "tbz2",
        "xz", "txz", "lz", "lzma", "zst" -> FileKind.ARCHIVE
        // 碟镜像
        "iso", "img", "dmg" -> FileKind.DISK
        // 安装包 / 可执行 / 脚本
        "apk" -> FileKind.APK
        "exe", "msi", "bat", "cmd", "sh", "bash", "zsh", "fish", "ps1",
        "deb", "rpm", "bin", "run" -> FileKind.EXEC
        // 多媒体
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif",
        "ico", "raw", "tiff", "tif" -> FileKind.IMAGE
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm",
        "ts", "m2ts", "3gp" -> FileKind.VIDEO
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac" -> FileKind.AUDIO
        "pdf" -> FileKind.PDF
        // 代码 / 配置
        "py", "js", "jsx", "ts", "tsx", "java", "kt", "kts", "go", "rs", "swift",
        "rb", "php", "c", "h", "cpp", "cc", "cxx", "hpp", "sql", "json", "xml",
        "yaml", "yml", "toml", "ini", "conf", "gradle", "dart", "html", "htm",
        "css", "scss", "sass", "less" -> FileKind.CODE
        // 文本
        "txt", "md", "markdown", "log", "rst", "tex" -> FileKind.TEXT
        // Office 三件套
        "doc", "docx", "rtf", "odt" -> FileKind.DOC
        "xls", "xlsx", "ods", "csv" -> FileKind.SHEET
        "ppt", "pptx", "odp", "key" -> FileKind.SLIDE
        else -> FileKind.UNKNOWN
    }
}

/** 渲染文件类型图标: 统一的「正方形圆角块」底 + 类型符号 (iOS App 图标语言) */
@Composable
private fun FileTypeIconBox(style: FileStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.size(46.dp)) {
        // 画布尺寸是 dp, 但 draw* 的坐标单位是 px: 图形按 46 设计单位给出,
        // 必须按画布实际像素宽归一化, 否则高密度屏上图形只占画布的 ~1/density → 显得极小, 描边细到看不见.
        val unit = size.width / 46f
        scale(unit, pivot = Offset.Zero) {
            // 归一化后再整体放大 1.3, 让方块填满图标区
            scale(1.3f, pivot = Offset(23f, 23f)) {
                // 正方形圆角底块 (所有类型统一, 不再用竖长文档页 / 横长文件夹)
                val tileTopLeft = Offset(7f, 7f)
                val tileSize = Size(32f, 32f)
                val tileCorner = CornerRadius(8f, 8f)
                drawRoundRect(style.bg, tileTopLeft, tileSize, tileCorner)
                drawRoundRect(
                    style.fg.copy(alpha = 0.18f),
                    tileTopLeft, tileSize, tileCorner,
                    style = Stroke(width = 1.2f),
                )

                // 类型符号: 符号按旧「文档页」坐标设计 (光学中心偏下 ~2.5), 上移归中后再略放大
                scale(1.2f, pivot = Offset(23f, 23f)) {
                    translate(top = -2.5f) {
                        if (style.kind == FileKind.FOLDER) {
                            drawFolderGlyph(style.fg)
                        } else {
                            drawKindGlyph(style.kind, style.bg, style.fg)
                        }
                    }
                }
            }
        }
    }
}

/** 文件夹符号 (画在方块中央) */
private fun DrawScope.drawFolderGlyph(fg: Color) {
    drawRoundRect(fg, Offset(13f, 17.5f), Size(12f, 6.5f), CornerRadius(2f, 2f))
    drawRoundRect(fg, Offset(13f, 20.5f), Size(20f, 13f), CornerRadius(3.5f, 3.5f))
}

/** 类型符号: 按设计坐标 (x 15~31, y 19~32) 绘制, 由调用方平移到方块中央 */
private fun DrawScope.drawKindGlyph(kind: FileKind, bg: Color, fg: Color) {
    val sw = 1.8f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        FileKind.IMAGE -> {
            drawCircle(fg, radius = 1.7f, center = Offset(19.5f, 21.5f))
            drawPath(
                Path().apply {
                    moveTo(15.5f, 31.5f); lineTo(20.5f, 25.5f); lineTo(23.5f, 28.5f)
                    lineTo(26.5f, 25f); lineTo(30.5f, 31.5f)
                },
                fg, style = stroke,
            )
        }
        FileKind.VIDEO -> drawPath(
            Path().apply {
                moveTo(18.5f, 20.5f); lineTo(29.5f, 25.5f); lineTo(18.5f, 30.5f); close()
            },
            fg,
        )
        FileKind.AUDIO -> {
            drawCircle(fg, radius = 2.3f, center = Offset(18.8f, 29f))
            drawLine(fg, Offset(21f, 29f), Offset(21f, 20.5f), sw, StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(21f, 20.5f); lineTo(26.5f, 22f); lineTo(26.5f, 25f); lineTo(21f, 23.5f); close()
                },
                fg,
            )
        }
        FileKind.ARCHIVE -> {
            drawRoundRect(fg, Offset(21.6f, 19f), Size(3.2f, 13f), CornerRadius(1.2f, 1.2f))
            listOf(21.3f, 24.3f, 27.3f).forEach { y ->
                drawRoundRect(bg, Offset(21.6f, y), Size(3.2f, 1.2f), CornerRadius(0.6f, 0.6f))
            }
        }
        FileKind.CODE -> {
            drawPath(
                Path().apply { moveTo(19.5f, 20.5f); lineTo(16f, 25.5f); lineTo(19.5f, 30.5f) },
                fg, style = stroke,
            )
            drawPath(
                Path().apply { moveTo(26.5f, 20.5f); lineTo(30f, 25.5f); lineTo(26.5f, 30.5f) },
                fg, style = stroke,
            )
            drawLine(fg, Offset(24.4f, 19.5f), Offset(22.6f, 31.5f), sw, StrokeCap.Round)
        }
        FileKind.SHEET -> {
            drawRoundRect(
                fg, Offset(15.5f, 20f), Size(15f, 12f), CornerRadius(2f, 2f),
                style = Stroke(width = 1.5f),
            )
            drawLine(fg, Offset(15.5f, 26f), Offset(30.5f, 26f), 1.5f)
            drawLine(fg, Offset(23f, 20f), Offset(23f, 32f), 1.5f)
        }
        FileKind.SLIDE -> {
            drawRoundRect(fg, Offset(15.5f, 26f), Size(3.4f, 6f), CornerRadius(1f, 1f))
            drawRoundRect(fg, Offset(21f, 20.5f), Size(3.4f, 11.5f), CornerRadius(1f, 1f))
            drawRoundRect(fg, Offset(26.5f, 23.5f), Size(3.4f, 8.5f), CornerRadius(1f, 1f))
        }
        FileKind.PDF -> {
            drawLine(fg, Offset(16.5f, 22.5f), Offset(29.5f, 22.5f), 2.2f, StrokeCap.Round)
            drawLine(fg, Offset(16.5f, 27f), Offset(24.5f, 27f), 2.2f, StrokeCap.Round)
        }
        FileKind.DOC -> {
            drawLine(fg, Offset(16.5f, 22f), Offset(29.5f, 22f), sw, StrokeCap.Round)
            drawLine(fg, Offset(16.5f, 26f), Offset(29.5f, 26f), sw, StrokeCap.Round)
            drawLine(fg, Offset(16.5f, 30f), Offset(24f, 30f), sw, StrokeCap.Round)
        }
        FileKind.TEXT -> {
            drawLine(fg, Offset(17f, 23.5f), Offset(29f, 23.5f), 1.7f, StrokeCap.Round)
            drawLine(fg, Offset(17f, 28.5f), Offset(25f, 28.5f), 1.7f, StrokeCap.Round)
        }
        FileKind.DISK -> {
            drawCircle(fg, radius = 5.7f, center = Offset(23f, 25.5f), style = Stroke(width = 2.4f))
            drawCircle(fg, radius = 1.8f, center = Offset(23f, 25.5f))
        }
        FileKind.EXEC -> {
            drawPath(
                Path().apply { moveTo(17.5f, 20.5f); lineTo(21.5f, 25f); lineTo(17.5f, 29.5f) },
                fg, style = stroke,
            )
            drawLine(fg, Offset(23.5f, 29.5f), Offset(28.5f, 29.5f), sw, StrokeCap.Round)
        }
        FileKind.APK -> {
            // 安卓机器人: 半圆头 + 触角 + 眼
            drawArc(
                color = fg, startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(18f, 20.5f), size = Size(10f, 10f),
            )
            drawLine(fg, Offset(19.5f, 19.5f), Offset(21f, 21.5f), 1.4f, StrokeCap.Round)
            drawLine(fg, Offset(26.5f, 19.5f), Offset(25f, 21.5f), 1.4f, StrokeCap.Round)
            drawCircle(bg, radius = 1f, center = Offset(20.7f, 24.5f))
            drawCircle(bg, radius = 1f, center = Offset(25.3f, 24.5f))
        }
        // 兜底文本 / 理论上的文件夹分支: 两条文档线
        FileKind.UNKNOWN, FileKind.FOLDER -> {
            drawLine(fg, Offset(16.5f, 23f), Offset(29.5f, 23f), 1.7f, StrokeCap.Round)
            drawLine(fg, Offset(16.5f, 28f), Offset(24f, 28f), 1.7f, StrokeCap.Round)
        }
    }
}
