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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.DangerRed
import com.datbox.app.ui.theme.NearBlack
import com.datbox.app.ui.theme.StoneGray
import com.datbox.app.ui.theme.TextStrong
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
    // 顶栏标题: headlineSmall (M3 默认 24sp) 降一档到 20sp, 缩小与文件名 16sp 的落差
    val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp)
    // 根目录: 左上角显示品牌 logo + 名称; 子目录显示当前目录名
    val title = if (path == "/") "" else path.substringAfterLast('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AppConfig.BRAND,
                    style = titleStyle,
                    fontWeight = FontWeight.ExtraBold,
                    color = NearBlack,
                    maxLines = 1,
                )
            }
        } else {
            // 子目录: 显示当前目录名 (放大加粗, iOS 大标题感)
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.ExtraBold,
                color = NearBlack,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 搜索入口 (从 dock 移至顶栏: 点开 SearchBarRow 内联搜索, 不弹窗)
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = NearBlack)
        }

        // 排序 (原搜索弹窗里的入口)
        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.Outlined.Sort, contentDescription = "排序", tint = NearBlack)
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
            Icon(Icons.Outlined.Add, contentDescription = "上传", tint = NearBlack)
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "退出搜索", tint = NearBlack)
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
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = StoneGray,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NearBlack),
                    cursorBrush = SolidColor(NearBlack),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("搜索全盘文件...", color = StoneGray)
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
                            tint = StoneGray,
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
                    color = StoneGray,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
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
    CircularProgressIndicator(color = NearBlack)
}

@Composable
private fun CenterMessage(msg: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LiquidGlassCard(modifier = Modifier.padding(32.dp).widthIn(max = 320.dp), cornerRadius = 24.dp, contentPadding = 24.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = NearBlack, style = MaterialTheme.typography.bodyLarge)
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
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD8D6CF)))
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                FileTypeIconBox(fileStyle)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (shareUrl == null) "文件操作" else "分享此文件", fontSize = 12.sp, color = StoneGray)
                }
            }
            HorizontalDivider(color = Color(0xFFECEAE3))
            Spacer(Modifier.height(2.dp))

            // 下载
            GlassActionItem(icon = Icons.Outlined.Download, iconTint = NearBlack, label = "下载到本地", subLabel = "保存到下载目录", onClick = onDownload)

            // 分享区块
            when {
                shareLoading -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = BrandGreen)
                        Spacer(Modifier.width(12.dp))
                        Text("正在生成分享链接…", fontSize = 14.sp, color = StoneGray)
                    }
                }
                shareUrl == null -> {
                    GlassActionItem(icon = Icons.Outlined.Share, iconTint = NearBlack, label = "分享链接", subLabel = "获取可分享短链", onClick = onRequestShare)
                }
                else -> {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(shareUrl, fontSize = 12.sp, color = Color(0xFF56544E), modifier = Modifier.fillMaxWidth().padding(16.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onCopyShare), color = BrandGreen) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("复制链接", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Surface(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = { onSystemShare(shareUrl) }), color = TextStrong) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("系统分享", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            GlassActionItem(icon = Icons.Outlined.Edit, iconTint = NearBlack, label = "重命名", subLabel = "修改文件名", onClick = onRename)
            GlassActionItem(icon = Icons.Outlined.Delete, iconTint = DangerRed, label = "删除", subLabel = "永久删除此文件", onClick = onDelete)
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
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = TextStrong, fontWeight = FontWeight.Medium)
                Text(subLabel, style = MaterialTheme.typography.bodySmall, color = StoneGray)
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
 * 文件类型图标: 真物范式 —— 文件夹画成真文件夹, 文件画成真纸页(带折角)。
 *
 * 之所以改: 上一版是「白玻璃方片 + 一个抽象几何符号」, 一个空白方块上飘一个符号,
 * 无论怎么调符号都不会有真实感。这一版画的是物体本身 ——
 * 纸页有折角、有页头、有内容缩影, 文件夹有前后板与受光。
 *
 * 15 类分三种内容画法:
 *   - 条线类 (DOC/TEXT/PDF/UNKNOWN): 文字条 / 页中之页
 *   - 实物类 (IMAGE/VIDEO/AUDIO/CODE/SHEET/SLIDE): 直接画出那个东西
 *     (照片带天空+山+太阳, 视频是黑框+播放, 音频是波形, 代码是彩色代码窗, 表格是网格, 幻灯片是 16:9)
 *   - 带底板类 (ARCHIVE/DISK/EXEC/APK): 淡彩底板 + 类型色部件 (保证任何配色下都有明度差)
 *
 * 配色 (白纸 + 彩色页头):
 *   页身 = 纸白渐变; 顶部 1/3 = 类型色页头; 折角 = 更浅的同类色; 内容 = 类型色。
 *   既有色彩身份 (扫列表看页头色), 又保住纸张真实度 (页身是纸不是色块)。
 */
private enum class FileKind(val tint: Color) {
    FOLDER(Color(0xFF007AFF)),
    DOC(Color(0xFF5856D6)),
    TEXT(Color(0xFF8E8E93)),
    IMAGE(Color(0xFFFF2D55)),
    VIDEO(Color(0xFFAF52DE)),
    AUDIO(Color(0xFFFF9500)),
    ARCHIVE(Color(0xFFA2845E)),
    CODE(Color(0xFF32ADE6)),
    SHEET(Color(0xFF00C7BE)),
    SLIDE(Color(0xFFFFB800)),
    PDF(Color(0xFFFF3B30)),
    APK(Color(0xFF16A34A)),
    DISK(Color(0xFF64748B)),
    EXEC(Color(0xFF6B7280)),
    UNKNOWN(Color(0xFFA8A29E)),
}

private data class FileStyle(val kind: FileKind)

private fun fileStyleFor(name: String, isDir: Boolean): FileStyle =
    FileStyle(fileKindFor(name, isDir))

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

/** 渲染文件类型图标: 真文件夹 / 真纸页 */
@Composable
private fun FileTypeIconBox(style: FileStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.size(46.dp)) {
        // 画布尺寸是 dp, 但 draw* 的坐标单位是 px: 图形按 46 设计单位给出,
        // 必须按画布实际像素宽归一化, 否则高密度屏上图形只占画布的 ~1/density → 显得极小, 描边细到看不见.
        val unit = size.width / 46f
        scale(unit, pivot = Offset.Zero) {
            // 纸页只占 46 空间的 27×36, 略放大让图标区更满 (纸页中心 y 与画布中心重合, 放大是对称的)
            scale(1.08f, pivot = Offset(23f, 23f)) {
                if (style.kind == FileKind.FOLDER) drawFolderIcon(style.kind.tint)
                else drawPageIcon(style.kind)
            }
        }
    }
}

// ===== 几何 (46 设计空间) =====

// 纸页: x 9.5~36.5 (宽 27), y 5~41 (高 36), 圆角 3.2, 右上折角 8.5
private const val PAGE_L = 9.5f
private const val PAGE_T = 5f
private const val PAGE_R = 36.5f
private const val PAGE_B = 41f
private const val PAGE_CORNER = 3.2f
private const val PAGE_FOLD = 8.5f
private const val PAGE_HEAD = 16.4f          // 彩色页头下沿

// 内容区: 用归一化 0..1 描述, 再由下面四个函数换算到设计单位
private const val CB_X = 13f
private const val CB_Y = 18f
private const val CB_W = 20f
private const val CB_H = 19f

private fun bx(u: Float) = CB_X + u * CB_W
private fun by(v: Float) = CB_Y + v * CB_H
private fun bw(u: Float) = u * CB_W
private fun bh(v: Float) = v * CB_H

/** 同类色加深 (factor < 1) 或向白提亮 (factor > 1) */
private fun tintShade(c: Color, factor: Float): Color =
    if (factor <= 1f) {
        Color(c.red * factor, c.green * factor, c.blue * factor, c.alpha)
    } else {
        val t = factor - 1f
        Color(
            c.red + (1f - c.red) * t,
            c.green + (1f - c.green) * t,
            c.blue + (1f - c.blue) * t,
            c.alpha,
        )
    }

/** 纸页轮廓: 右上角被折掉的圆角矩形 */
private fun pagePath(): Path = Path().apply {
    moveTo(PAGE_L + PAGE_CORNER, PAGE_T)
    lineTo(PAGE_R - PAGE_FOLD, PAGE_T)
    lineTo(PAGE_R, PAGE_T + PAGE_FOLD)
    lineTo(PAGE_R, PAGE_B - PAGE_CORNER)
    arcTo(Rect(PAGE_R - PAGE_CORNER * 2, PAGE_B - PAGE_CORNER * 2, PAGE_R, PAGE_B), 0f, 90f, false)
    lineTo(PAGE_L + PAGE_CORNER, PAGE_B)
    arcTo(Rect(PAGE_L, PAGE_B - PAGE_CORNER * 2, PAGE_L + PAGE_CORNER * 2, PAGE_B), 90f, 90f, false)
    lineTo(PAGE_L, PAGE_T + PAGE_CORNER)
    arcTo(Rect(PAGE_L, PAGE_T, PAGE_L + PAGE_CORNER * 2, PAGE_T + PAGE_CORNER * 2), 180f, 90f, false)
    close()
}

/** 折角翻折面: 斜边两端 → 内直角 */
private fun foldPath(): Path = Path().apply {
    moveTo(PAGE_R - PAGE_FOLD, PAGE_T)
    lineTo(PAGE_R - PAGE_FOLD, PAGE_T + PAGE_FOLD)
    lineTo(PAGE_R, PAGE_T + PAGE_FOLD)
    close()
}

/** 内容区里的圆角块 (归一化坐标) */
private fun DrawScope.cbRect(u0: Float, v0: Float, u1: Float, v1: Float, r: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(bx(u0), by(v0)),
        size = Size(bw(u1 - u0), bh(v1 - v0)),
        cornerRadius = CornerRadius(r, r),
    )
}

// ===== 文件夹 =====

/** 真文件夹: 后板(含页签) + 前板(受光渐变) + 前板上沿高光 */
private fun DrawScope.drawFolderIcon(tint: Color) {
    val back = tintShade(tint, 0.72f)

    // 落地影: 同一轮廓下移, 两档偏移代替模糊
    translate(top = 1.0f) {
        drawRoundRect(Color.Black.copy(alpha = 0.07f), Offset(6f, 8f), Size(16f, 9f), CornerRadius(2.6f, 2.6f))
        drawRoundRect(Color.Black.copy(alpha = 0.07f), Offset(6f, 11f), Size(34f, 26f), CornerRadius(3.6f, 3.6f))
    }
    translate(top = 2.0f) {
        drawRoundRect(Color.Black.copy(alpha = 0.05f), Offset(6f, 11f), Size(34f, 26f), CornerRadius(3.6f, 3.6f))
    }

    // 后板 + 页签
    drawRoundRect(back, Offset(6f, 8f), Size(16f, 9f), CornerRadius(2.6f, 2.6f))
    drawRoundRect(back, Offset(6f, 11f), Size(34f, 26f), CornerRadius(3.6f, 3.6f))

    // 前板 (受光渐变) + 上沿高光
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to tintShade(tint, 1.18f), 1f to tint, startY = 16.6f, endY = 39f,
        ),
        topLeft = Offset(6f, 16.6f), size = Size(34f, 22.4f), cornerRadius = CornerRadius(3.6f, 3.6f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.34f), Offset(7.4f, 17.7f), Size(31.2f, 1.5f), CornerRadius(0.75f, 0.75f))
}

// ===== 纸页 =====

/** 纸页: 落地影 → 纸身 → 彩色页头 → 折角 → 内容 → 发丝边 */
private fun DrawScope.drawPageIcon(kind: FileKind) {
    val tint = kind.tint
    val page = pagePath()

    translate(top = 1.1f) { drawPath(page, Color.Black.copy(alpha = 0.06f)) }
    translate(top = 2.1f) { drawPath(page, Color.Black.copy(alpha = 0.05f)) }

    drawPath(
        page,
        brush = Brush.verticalGradient(
            0f to Color(0xFFFEFEFF), 1f to Color(0xFFF4F4F8), startY = PAGE_T, endY = PAGE_B,
        ),
    )

    // 页头裁在纸形内 (右上折角处不会溢出) + 页头下沿一道极淡分界
    clipPath(page) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to tintShade(tint, 1.14f), 1f to tintShade(tint, 0.94f),
                startY = PAGE_T, endY = PAGE_HEAD,
            ),
            topLeft = Offset(0f, PAGE_T),
            size = Size(46f, PAGE_HEAD - PAGE_T),
        )
        drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, PAGE_HEAD - 0.35f), Size(46f, 0.7f))
    }

    // 折角比页头更浅 → 看得出纸被翻折, 而不是被切掉
    drawPath(foldPath(), tintShade(tint, 1.35f))

    drawKindContent(kind)

    drawPath(page, Color.Black.copy(alpha = 0.13f), style = Stroke(width = 0.7f))
}

/** 内容缩影分发 (归一化坐标里画) */
private fun DrawScope.drawKindContent(kind: FileKind) {
    val tint = kind.tint
    when (kind) {
        FileKind.DOC -> drawDocContent(tint)
        FileKind.TEXT -> drawTextLines(tint)
        FileKind.PDF -> drawPdfContent(tint)
        FileKind.SHEET -> drawSheetContent(tint)
        FileKind.SLIDE -> drawSlideContent(tint)
        FileKind.IMAGE -> drawImageContent()
        FileKind.VIDEO -> drawVideoContent()
        FileKind.AUDIO -> drawAudioContent(tint)
        FileKind.CODE -> drawCodeContent()
        FileKind.ARCHIVE -> drawArchiveContent(tint)
        FileKind.DISK -> drawDiskContent(tint)
        FileKind.EXEC -> drawExecContent(tint)
        FileKind.APK -> drawApkContent(tint)
        FileKind.UNKNOWN -> drawUnknownContent(tint)
        FileKind.FOLDER -> Unit   // 走 drawFolderIcon, 不会到这里
    }
}

/** 文本: 四条长短不一的文字条 */
private fun DrawScope.drawTextLines(tint: Color) {
    listOf(0.10f to 0.96f, 0.36f to 0.96f, 0.62f to 0.96f, 0.88f to 0.58f).forEach { (v, u1) ->
        cbRect(0.04f, v, u1, v + 0.075f, 0.7f, tint)
    }
}

/** 文档: 一条标题 + 三行正文 */
private fun DrawScope.drawDocContent(tint: Color) {
    cbRect(0.04f, 0.02f, 0.62f, 0.17f, 1.4f, tint)
    listOf(0.36f to 0.96f, 0.62f to 0.96f, 0.88f to 0.62f).forEach { (v, u1) ->
        cbRect(0.04f, v, u1, v + 0.075f, 0.7f, tint)
    }
}

/** PDF: 页中之页 (内页恒为纸白 + 发丝边, 任何配色下都不糊) */
private fun DrawScope.drawPdfContent(tint: Color) {
    val l = bx(0.06f)
    val t = by(0.02f)
    val w = bw(0.88f)
    val h = bh(0.96f)
    val r = 2.2f
    val f = w * 0.30f
    val inner = Path().apply {
        moveTo(l + r, t)
        lineTo(l + w - f, t)
        lineTo(l + w, t + f)
        lineTo(l + w, t + h - r)
        arcTo(Rect(l + w - r * 2, t + h - r * 2, l + w, t + h), 0f, 90f, false)
        lineTo(l + r, t + h)
        arcTo(Rect(l, t + h - r * 2, l + r * 2, t + h), 90f, 90f, false)
        lineTo(l, t + r)
        arcTo(Rect(l, t, l + r * 2, t + r * 2), 180f, 90f, false)
        close()
    }
    drawPath(inner, Color.White)
    drawPath(
        Path().apply {
            moveTo(l + w - f, t); lineTo(l + w - f, t + f); lineTo(l + w, t + f); close()
        },
        tint.copy(alpha = 0.85f),
    )
    drawPath(inner, Color.Black.copy(alpha = 0.16f), style = Stroke(width = 0.7f))
    listOf(0.46f, 0.68f).forEach { v -> cbRect(0.14f, v, 0.70f, v + 0.085f, 0.8f, tint) }
}

/** 表格: 表头填色 + 网格 + 一个高亮单元格 */
private fun DrawScope.drawSheetContent(tint: Color) {
    val l = bx(0f)
    val t = by(0f)
    val w = bw(1f)
    val h = bh(1f)
    cbRect(0f, 0f, 1f, 0.24f, 1.8f, tint)
    for (i in 1..2) {
        val x = l + w * i / 3f
        drawLine(tint.copy(alpha = 0.75f), Offset(x, t + h * 0.24f), Offset(x, t + h), 0.8f)
    }
    for (i in 1..2) {
        val y = t + h * (0.24f + 0.76f * i / 3f)
        drawLine(tint.copy(alpha = 0.75f), Offset(l, y), Offset(l + w, y), 0.8f)
    }
    cbRect(1f / 3f, 0.24f + 0.76f / 3f, 2f / 3f, 0.24f + 0.76f * 2f / 3f, 0.6f, tint)
    drawRoundRect(tint, Offset(l, t), Size(w, h), CornerRadius(1.8f, 1.8f), style = Stroke(width = 1f))
}

/** 幻灯片: 16:9 画框 + 标题条 + 两条正文块 + 页脚 */
private fun DrawScope.drawSlideContent(tint: Color) {
    val l = bx(0.02f)
    val t = by(0.16f)
    val w = bw(0.96f)
    val h = bh(0.68f)
    drawRoundRect(tint, Offset(l, t), Size(w, h), CornerRadius(1.6f, 1.6f), style = Stroke(width = 1f))
    drawRoundRect(tint, Offset(l + w * 0.08f, t + h * 0.14f), Size(w * 0.40f, h * 0.16f), CornerRadius(h * 0.08f, h * 0.08f))
    drawRoundRect(tint, Offset(l + w * 0.08f, t + h * 0.48f), Size(w * 0.84f, h * 0.13f), CornerRadius(h * 0.065f, h * 0.065f))
    drawRoundRect(tint, Offset(l + w * 0.08f, t + h * 0.72f), Size(w * 0.58f, h * 0.13f), CornerRadius(h * 0.065f, h * 0.065f))
    cbRect(0.30f, 0.90f, 0.70f, 0.975f, 0.7f, tint.copy(alpha = 0.7f))
}

/** 图片: 一张真照片 (天空 + 太阳 + 两层山), 自带固有色 */
private fun DrawScope.drawImageContent() {
    val l = bx(0f)
    val t = by(0f)
    val w = bw(1f)
    val h = bh(1f)
    val clip = Path().apply { addRoundRect(RoundRect(Rect(l, t, l + w, t + h), CornerRadius(2f, 2f))) }
    clipPath(clip) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF9BD9FF), 1f to Color(0xFFE8F6FF), startY = t, endY = t + h,
            ),
            topLeft = Offset(l, t), size = Size(w, h),
        )
        drawCircle(Color(0xFFFFF6C9), radius = w * 0.13f, center = Offset(l + w * 0.74f, t + h * 0.26f))
        drawCircle(
            Color(0xFFFFD84D), radius = w * 0.13f, center = Offset(l + w * 0.74f, t + h * 0.26f),
            style = Stroke(width = 0.6f),
        )
        drawPath(
            Path().apply {
                moveTo(l, t + h); lineTo(l + w * 0.30f, t + h * 0.44f); lineTo(l + w * 0.60f, t + h); close()
            },
            Color(0xFF4E9F63),
        )
        drawPath(
            Path().apply {
                moveTo(l + w * 0.44f, t + h); lineTo(l + w * 0.76f, t + h * 0.56f); lineTo(l + w, t + h); close()
            },
            Color(0xFF3B8350),
        )
    }
    drawRoundRect(
        Color.Black.copy(alpha = 0.10f), Offset(l, t), Size(w, h), CornerRadius(2f, 2f),
        style = Stroke(width = 0.7f),
    )
}

/** 视频: 黑画框 + 播放三角 */
private fun DrawScope.drawVideoContent() {
    val l = bx(0f)
    val t = by(0.06f)
    val w = bw(1f)
    val h = bh(0.88f)
    drawRoundRect(Color(0xFF1C1C1E), Offset(l, t), Size(w, h), CornerRadius(2.2f, 2.2f))
    val cx = l + w / 2f
    val cy = t + h / 2f
    drawPath(
        Path().apply {
            moveTo(cx - w * 0.10f, cy - h * 0.20f)
            lineTo(cx + w * 0.17f, cy)
            lineTo(cx - w * 0.10f, cy + h * 0.20f)
            close()
        },
        Color.White,
    )
}

/** 音频: 六根波形条 */
private fun DrawScope.drawAudioContent(tint: Color) {
    listOf(0.30f, 0.62f, 0.92f, 0.50f, 0.22f, 0.70f).forEachIndexed { i, barH ->
        val u = 0.03f + i * 0.163f
        cbRect(u, 0.5f - barH / 2f, u + 0.085f, 0.5f + barH / 2f, 0.84f, tint)
    }
}

/** 代码: 深色代码窗 + 彩色语法行, 自带固有色 */
private fun DrawScope.drawCodeContent() {
    val l = bx(0f)
    val t = by(0f)
    val w = bw(1f)
    val h = bh(1f)
    drawRoundRect(Color(0xFF26262B), Offset(l, t), Size(w, h), CornerRadius(2.2f, 2.2f))
    listOf(
        Color(0xFF7CE38B) to 0.52f,
        Color(0xFF82AAFF) to 0.74f,
        Color(0xFFFFB86C) to 0.40f,
        Color(0xFF7CE38B) to 0.60f,
    ).forEachIndexed { i, (lineColor, u1) ->
        drawRoundRect(
            lineColor,
            Offset(l + w * 0.12f, t + h * (0.13f + i * 0.21f)),
            Size(w * (u1 - 0.12f), h * 0.085f),
            CornerRadius(h * 0.042f, h * 0.042f),
        )
    }
    drawRoundRect(
        Color(0xFF5A5A63),
        Offset(l + w * 0.05f, t + h * 0.13f),
        Size(w * 0.035f, h * 0.085f),
        CornerRadius(h * 0.042f, h * 0.042f),
    )
}

/** 压缩包: 淡彩底板 + 类型色拉链 + 拉环 */
private fun DrawScope.drawArchiveContent(tint: Color) {
    val panel = tintShade(tint, 1.62f)
    cbRect(0f, 0f, 1f, 1f, 2f, panel)
    cbRect(0.40f, 0f, 0.60f, 1f, 0f, tint)
    for (i in 0 until 5) {
        cbRect(0.40f, 0.06f + i * 0.20f, 0.60f, 0.15f + i * 0.20f, 0.85f, panel)
    }
    drawCircle(
        color = tint,
        radius = bw(0.075f),
        center = Offset(bx(0.5f), by(0.88f)),
        style = Stroke(width = 0.9f),
    )
}

/** 光盘: 淡彩底板 + 同心圆盘 */
private fun DrawScope.drawDiskContent(tint: Color) {
    cbRect(0f, 0f, 1f, 1f, 2f, tintShade(tint, 1.62f))
    val center = Offset(bx(0.5f), by(0.5f))
    drawCircle(tint.copy(alpha = 0.18f), radius = bw(0.46f), center = center)
    drawCircle(tint, radius = bw(0.46f), center = center, style = Stroke(width = 1.6f))
    drawCircle(tint.copy(alpha = 0.70f), radius = bw(0.28f), center = center, style = Stroke(width = 1.0f))
    drawCircle(tint, radius = bw(0.11f), center = center)
}

/** 可执行: 淡色终端窗 + 提示符 + 光标条 */
private fun DrawScope.drawExecContent(tint: Color) {
    cbRect(0f, 0.04f, 1f, 0.96f, 2f, tint.copy(alpha = 0.14f))
    drawPath(
        Path().apply {
            moveTo(bx(0.14f), by(0.26f))
            lineTo(bx(0.30f), by(0.50f))
            lineTo(bx(0.14f), by(0.74f))
        },
        tint,
        style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    cbRect(0.40f, 0.68f, 0.84f, 0.78f, 0.95f, tint)
}

/** 安装包: 淡彩底板 + 安卓机器人头 */
private fun DrawScope.drawApkContent(tint: Color) {
    val panel = tintShade(tint, 1.62f)
    cbRect(0f, 0f, 1f, 1f, 2f, panel)
    val l = bx(0.10f)
    val t = by(0.30f)
    val w = bw(0.80f)
    val h = bh(0.72f)
    // 触角
    drawLine(tint, Offset(l + w * 0.20f, t - h * 0.60f), Offset(l + w * 0.32f, t - h * 0.04f), 1.4f, StrokeCap.Round)
    drawLine(tint, Offset(l + w * 0.80f, t - h * 0.60f), Offset(l + w * 0.68f, t - h * 0.04f), 1.4f, StrokeCap.Round)
    // 半圆头
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(l, t),
        size = Size(w, h * 2f),
    )
    // 眼
    drawCircle(panel, radius = w * 0.085f, center = Offset(l + w * 0.30f, t + h * 0.44f))
    drawCircle(panel, radius = w * 0.085f, center = Offset(l + w * 0.70f, t + h * 0.44f))
}

/** 未知类型: 两条短文字条 */
private fun DrawScope.drawUnknownContent(tint: Color) {
    cbRect(0.04f, 0.30f, 0.70f, 0.375f, 0.7f, tint)
    cbRect(0.04f, 0.62f, 0.46f, 0.695f, 0.7f, tint)
}
