package com.datbox.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.DangerRed
import com.datbox.app.ui.theme.NearBlack
import com.datbox.app.ui.theme.StoneGray
import com.datbox.app.ui.theme.TextStrong
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    val searchLocalWalk: Boolean = false,
    val searchScannedDirs: Int = 0,
    val searchTruncated: Boolean = false,
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

    /** 每次输入自增，异步回调靠它丢弃过期结果（取消不是瞬时的）。 */
    @Volatile private var searchGeneration = 0


    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _state.value = _state.value.copy(
                searching = false,
                searchResults = emptyList(),
                searchError = null,
                searchLocalWalk = false,
                searchScannedDirs = 0,
                searchTruncated = false,
            )
            return
        }
        val generation = ++searchGeneration
        _state.value = _state.value.copy(
            searching = true,
            searchError = null,
            searchResults = emptyList(),
            searchLocalWalk = false,
            searchScannedDirs = 0,
            searchTruncated = false,
        )
        searchJob = viewModelScope.launch {
            delay(350)

            // 先走服务端索引：开了 search_index 的服务器这一步就够了。
            val server = repo.search(q)
            if (generation != searchGeneration) return@launch
            if (server.isSuccess) {
                _state.value = _state.value.copy(
                    searching = false,
                    searchResults = server.getOrThrow(),
                )
                return@launch
            }

            // 服务端默认 search_index=none，/api/fs/search 直接 500，退回本地扫描。
            _state.value = _state.value.copy(searchLocalWalk = true)
            val local = repo.searchLocal(q) { hits, scanned ->
                if (generation != searchGeneration) return@searchLocal
                _state.value = _state.value.copy(
                    searchResults = hits,
                    searchScannedDirs = scanned,
                    searching = true,
                )
            }
            if (generation != searchGeneration) return@launch
            local.onSuccess { (hits, truncated) ->
                _state.value = _state.value.copy(
                    searching = false,
                    searchResults = hits,
                    searchTruncated = truncated,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    searching = false,
                    searchResults = emptyList(),
                    searchError = e.message ?: "搜索失败",
                    searchLocalWalk = false,
                )
            }
        }
    }


    fun closeSearch() {
        searchJob?.cancel()
        searchGeneration++
        _state.value = _state.value.copy(
            searchQuery = "",
            searching = false,
            searchResults = emptyList(),
            searchError = null,
            searchLocalWalk = false,
            searchScannedDirs = 0,
            searchTruncated = false,
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

    LaunchedEffect(homeReset) {
        if (homeReset > 0 && state.path != "/") {
            vm.load("/")
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var accX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accX = 0f },
                        onHorizontalDrag = { _, dragAmount -> accX += dragAmount },
                        onDragEnd = {
                            if (accX > 100f && vm.state.value.path != "/") {
                                vm.load(vm.goUp())
                            }
                        },
                        onDragCancel = { accX = 0f }
                    )
                }
        ) {
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

            if (searchActive) {
                SearchResultsList(
                    state = state,
                    onRetry = { vm.updateSearchQuery(state.searchQuery) },
                    modifier = Modifier.weight(1f),
                    onOpen = { path, name, isDir ->
                        if (isDir) {
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
                    val target = menuItem
                    val requestPath = menuRemotePath
                    scope.launch {
                        shareLoading = true
                        try {
                            val url = vm.buildShareUrl(requestPath)
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

        if (showMkdirDialog) {
            MkdirDialog(onDismiss = { showMkdirDialog = false }, onCreate = { name -> vm.mkdir(name); showMkdirDialog = false })
        }

        showRenameDialog?.let { item ->
            val itemPath = if (state.path == "/") "/${item.name}" else "${state.path}/${item.name}"
            RenameDialog(currentName = item.name, onDismiss = { showRenameDialog = null }, onRename = { newName -> vm.rename(itemPath, newName); showRenameDialog = null })
        }

        showDeleteDialog?.let { item ->
            val dir = state.path
            val name = item.name
            DeleteDialog(fileName = item.name, onDismiss = { showDeleteDialog = null }, onDelete = { vm.delete(dir, name); showDeleteDialog = null })
        }
    }
}

@Composable
private fun FileBrowserTopBar(
    path: String,
    onLogout: () -> Unit,
    onSearchClick: () -> Unit = {},
    onUpload: () -> Unit = {},
    onSortSelected: (SortMode) -> Unit = {},
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp)
    val title = if (path == "/") "" else path.substringAfterLast('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (path == "/") {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.datbox_logo),
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

        IconButton(onClick = onSearchClick) {
            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = NearBlack)
        }

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

        IconButton(onClick = onUpload) {
            Icon(Icons.Outlined.Add, contentDescription = "上传", tint = NearBlack)
        }
    }
}


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


@Composable
private fun SearchResultsList(
    state: FileBrowserState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (path: String, name: String, isDir: Boolean) -> Unit,
) {
    when {
        state.searchQuery.isBlank() -> CenterMessage("输入关键词, 搜索全盘文件")

        state.searching && state.searchResults.isEmpty() ->
            if (state.searchLocalWalk) {
                CenterMessage("服务端未启用搜索索引, 正在本地扫描…\n已扫描 ${state.searchScannedDirs} 个文件夹")
            } else {
                CenterLoading()
            }

        state.searchError != null -> CenterMessage("搜索失败: ${state.searchError}", "重试", onRetry)

        state.searchResults.isEmpty() -> CenterMessage(
            if (state.searchLocalWalk) {
                "本地扫描 ${state.searchScannedDirs} 个文件夹, 没有找到「${state.searchQuery.trim()}」"
            } else {
                "没有找到「${state.searchQuery.trim()}」"
            }
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
        ) {
            item(key = "header") {
                Text(
                    text = buildString {
                        append("“${state.searchQuery.trim()}” · ${state.searchResults.size} 个结果")
                        if (state.searchLocalWalk) append(" · 本地扫描 ${state.searchScannedDirs} 个文件夹")
                    },
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
            if (state.searching || state.searchTruncated) {
                item(key = "footer") {
                    Text(
                        text = if (state.searching) {
                            "仍在扫描… 已找到 ${state.searchResults.size} 个"
                        } else {
                            "已达扫描上限, 结果可能不完整"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = StoneGray,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                }
            }
        }
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

            GlassActionItem(icon = Icons.Outlined.Download, iconTint = NearBlack, label = "下载到本地", subLabel = "保存到下载目录", onClick = onDownload)

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


/** 颜色烤在 drawable 里, 见 res/drawable/ic_file_type_*.xml */
private enum class FileKind(@DrawableRes val icon: Int) {
    FOLDER(R.drawable.ic_file_type_folder),
    DOC(R.drawable.ic_file_type_doc),
    TEXT(R.drawable.ic_file_type_text),
    IMAGE(R.drawable.ic_file_type_image),
    VIDEO(R.drawable.ic_file_type_video),
    AUDIO(R.drawable.ic_file_type_audio),
    ARCHIVE(R.drawable.ic_file_type_archive),
    CODE(R.drawable.ic_file_type_code),
    SHEET(R.drawable.ic_file_type_sheet),
    SLIDE(R.drawable.ic_file_type_slide),
    PDF(R.drawable.ic_file_type_pdf),
    APK(R.drawable.ic_file_type_apk),
    DISK(R.drawable.ic_file_type_disk),
    EXEC(R.drawable.ic_file_type_exec),
    UNKNOWN(R.drawable.ic_file_type_unknown),
}

private data class FileStyle(val kind: FileKind)

private fun fileStyleFor(name: String, isDir: Boolean): FileStyle =
    FileStyle(fileKindFor(name, isDir))

private fun fileKindFor(name: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.FOLDER
    return when (name.substringAfterLast('.', "").lowercase()) {
        "" -> FileKind.TEXT
        "zip", "7z", "rar", "jar", "tar", "tgz", "gz", "gzip", "bz2", "tbz2",
        "xz", "txz", "lz", "lzma", "zst", "cab", "war", "ear", "cpio", "lz4",
        "br", "z", "ace", "alz", "cbz", "cbr" -> FileKind.ARCHIVE
        "iso", "img", "dmg", "vhd", "vdi", "vmdk", "qcow2", "cue", "mdf", "nrg",
        "toast" -> FileKind.DISK
        "apk", "xapk", "apks", "apkm" -> FileKind.APK
        "exe", "msi", "bat", "cmd", "sh", "bash", "zsh", "fish", "ps1",
        "deb", "rpm", "bin", "run", "appimage", "com", "scr" -> FileKind.EXEC
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif",
        "ico", "raw", "tiff", "tif", "psd", "ai", "xcf", "dng", "cr2", "nef",
        "arw", "avif", "apng", "eps", "tga", "svgz", "jfif" -> FileKind.IMAGE
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "m4v", "webm", "rmvb", "rm",
        "ts", "m2ts", "3gp", "mpg", "mpeg", "vob", "f4v", "ogv", "asf", "divx",
        "mts", "mxf", "3g2", "m2v" -> FileKind.VIDEO
        "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac",
        "mid", "midi", "aiff", "aif", "amr", "mka", "ac3", "dts", "caf" -> FileKind.AUDIO
        "pdf", "xps", "oxps", "djvu" -> FileKind.PDF
        "py", "js", "jsx", "ts", "tsx", "java", "kt", "kts", "go", "rs", "swift",
        "rb", "php", "c", "h", "cpp", "cc", "cxx", "hpp", "sql", "json", "xml",
        "yaml", "yml", "toml", "ini", "conf", "gradle", "dart", "html", "htm",
        "css", "scss", "sass", "less", "vue", "svelte", "lua", "r", "pl", "pm",
        "scala", "clj", "ex", "erl", "hs", "ml", "fs", "vb", "asm", "groovy",
        "ipynb", "proto", "graphql", "tf", "hcl", "cs", "jl", "zig" -> FileKind.CODE
        "txt", "md", "markdown", "log", "rst", "tex", "text", "nfo", "srt",
        "vtt", "ass", "ssa", "sub", "epub", "mobi", "azw", "azw3", "fb2" -> FileKind.TEXT
        "doc", "docx", "rtf", "odt", "pages", "wps", "dot", "dotx", "wpd", "ott",
        "fodt" -> FileKind.DOC
        "xls", "xlsx", "ods", "csv", "tsv", "xlsm", "xlsb", "numbers", "xltx",
        "fods" -> FileKind.SHEET
        "ppt", "pptx", "odp", "key", "pps", "ppsx", "sldx", "fodp" -> FileKind.SLIDE
        else -> FileKind.UNKNOWN
    }
}


/**
 * 文件类型图标: Material Icon Theme (MIT, 见 THIRD_PARTY_NOTICES.md)。
 * 颜色已经烤在 drawable 里, 所以 tint 用 Color.Unspecified 关掉 Icon 的默认着色,
 * 否则会被 LocalContentColor 压成单色。
 * 外层保持 46dp 占位, 内层 28dp —— 列表行的排版不变。
 */
@Composable
private fun FileTypeIconBox(style: FileStyle, modifier: Modifier = Modifier) {
    Box(modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(style.kind.icon),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp),
        )
    }
}
