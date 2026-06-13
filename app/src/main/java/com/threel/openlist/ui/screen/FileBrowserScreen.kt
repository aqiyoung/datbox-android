package com.threel.openlist.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.OpenListRepository
import com.threel.openlist.data.model.FsItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.DecimalFormat

data class FileBrowserState(
    val path: String = "/",
    val items: List<FsItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repo: OpenListRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FileBrowserState())
    val state = _state.asStateFlow()

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onLogout: () -> Unit,
    onAbout: () -> Unit = {},
    vm: FileBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

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
                            )
                        }
                    }
                    items(state.items) { item ->
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
                                // 文件下载 / 预览下一期
                            },
                        )
                    }
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
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

private val sizeFmt = DecimalFormat("#,##0.#")
private fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${sizeFmt.format(b / 1024.0)} KB"
    b < 1024L * 1024 * 1024 -> "${sizeFmt.format(b / 1024.0 / 1024)} MB"
    else -> "${sizeFmt.format(b / 1024.0 / 1024 / 1024)} GB"
}
