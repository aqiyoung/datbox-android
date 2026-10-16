package com.datbox.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.datbox.app.BuildConfig
import com.datbox.app.data.model.Mount
import com.datbox.app.ui.component.GlassDecorBackground
import com.datbox.app.ui.component.LiquidGlassAlertDialog
import com.datbox.app.ui.component.LiquidGlassSurface
import com.datbox.app.data.model.Overview
import com.datbox.app.data.model.Share
import com.datbox.app.data.model.User
import com.datbox.app.ui.viewmodel.ManagementViewModel
import com.datbox.app.util.AppConfig

enum class ManagementTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    USERS("用户", Icons.Outlined.Person),
    MOUNTS("挂载", Icons.Outlined.Storage),
    SHARES("分享", Icons.Outlined.Share),
    SETTINGS("设置", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    onBack: () -> Unit,
    vm: ManagementViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val serverUrl by vm.serverUrl.collectAsState()
    var selectedTab by remember { mutableStateOf(ManagementTab.USERS) }

    GlassDecorBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("管理", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                        }
                    },
                    // 外层 MainScreen 已给状态栏 inset, 这里不再叠加 (否则返回行离状态栏双倍空隙)
                    windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent,
            // 同理: safeDrawing inset 外层已给, 内容直接贴顶
            contentWindowInsets = WindowInsets(0.dp),
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 错误提示
                if (state.error != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            state.error!!,
                            color = Color(0xFFFF3B30),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.loadAll() }) {
                            Text("刷新", color = Color(0xFF20C997))
                        }
                    }
                }

                // Tab 行
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF2A2925),
                    indicator = {},
                    divider = {}
                ) {
                    ManagementTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        Tab(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    tab.label,
                                    color = if (selected) Color(0xFF20C997) else Color(0xFF888888),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) Color(0xFF20C997) else Color(0xFF888888)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E5EA), thickness = 1.dp)

                // 内容区
                when (selectedTab) {
                    ManagementTab.USERS -> UsersTab(state.users, state.loading, vm)
                    ManagementTab.MOUNTS -> MountsTab(state.mounts, state.loading, vm)
                    ManagementTab.SHARES -> SharesTab(state.shares, state.loading, vm)
                    ManagementTab.SETTINGS -> SettingsTab(state.overview, serverUrl)
                }
            }
        }
    }
}

// ===== 通用卡片样式 =====
@Composable
private fun CardItem(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ===== Tab 1: 用户管理 =====
@Composable
private fun UsersTab(users: List<User>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var deletingUser by remember { mutableStateOf<User?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), // bottom: dock 悬浮区留白
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加用户", color = Color.White)
                }
            }
        }

        if (loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF20C997))
                }
            }
        } else if (users.isEmpty()) {
            item {
                CardItem {
                    Text("暂无用户", color = Color(0xFF888888))
                }
            }
        } else {
            items(users) { user ->
                var showMenu by remember { mutableStateOf(false) }
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.username, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(
                                when (user.role) { 0 -> "普通用户"; 1 -> "游客"; 2 -> "管理员"; else -> "未知" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                            // 权限标签 (位定义见 OpenList internal/model/user.go 的 User.Permission 注释)
                            if (user.permission != 0) {
                                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (user.permission and 1 != 0) PermissionTag("可见隐藏")
                                    if (user.permission and 8 != 0) PermissionTag("上传")
                                    if (user.permission and 128 != 0) PermissionTag("删除")
                                    if (user.permission and 256 != 0) PermissionTag("WebDAV读")
                                    if (user.permission and 16384 != 0) PermissionTag("分享")
                                }
                            } else {
                                Text("仅浏览 (无上传/分享权限)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Color(0xFF888888))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("编辑", color = Color(0xFF2A2925)) },
                                    onClick = { showMenu = false; editingUser = user },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color(0xFF888888)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = Color(0xFFFF3B30)) },
                                    onClick = { showMenu = false; deletingUser = user },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF3B30)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        UserEditDialog(
            user = null,
            onDismiss = { showAddDialog = false },
            onSave = { u, pw ->
                // 点击即关(乐观提交): 请求期间按钮不再可点, 防连点重复创建; 失败由顶部错误条提示
                showAddDialog = false
                vm.createUser(u.username, pw, u.role, u.permission)
            }
        )
    }
    editingUser?.let { user ->
        UserEditDialog(
            user = user,
            onDismiss = { editingUser = null },
            onSave = { u, pw ->
                editingUser = null
                vm.updateUser(u, pw)
            }
        )
    }
    // 删除确认: 用户删除不可逆 (账号立即失效), 二次确认防误触
    deletingUser?.let { user ->
        LiquidGlassAlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("删除用户", color = Color(0xFF2A2925)) },
            text = { Text("确定删除用户「${user.username}」？删除后该账号将无法再登录。", color = Color(0xFF888888)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteUser(user.id) { deletingUser = null } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) { Text("取消", color = Color(0xFF888888)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditDialog(user: User?, onDismiss: () -> Unit, onSave: (user: User, password: String) -> Unit) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var password by remember { mutableStateOf("") }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "添加用户" else "编辑用户", color = Color(0xFF2A2925)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it }, label = { Text("用户名") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (user == null) "密码" else "新密码") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
                // 角色只读: OpenList 服务端禁止创建游客/管理员、禁止修改角色
                // (server/handles/user.go: "admin or guest user can not be created" / "role can not be changed")
                OutlinedTextField(
                    value = roleLabel(if (user == null) 0 else user.role),
                    onValueChange = {}, readOnly = true, label = { Text("角色") },
                    supportingText = {
                        Text(if (user == null) "服务端仅允许创建普通用户" else "服务端不允许修改角色")
                    },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 新建: 服务端只接受普通用户(0); 编辑: 除用户名/密码外全字段回传, 权限/目录/状态原样保留
                    val target = user ?: User(id = 0, username = "", role = 0, permission = 0)
                    onSave(target.copy(username = username), password)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997))
            ) { Text("保存", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF888888)) }
        }
    )
}

/** OpenList 角色定义: GENERAL=0, GUEST=1, ADMIN=2 (internal/model/user.go)。 */
private fun roleLabel(role: Int): String = when (role) {
    0 -> "普通用户"
    1 -> "游客"
    2 -> "管理员"
    else -> "未知"
}

// ===== Tab 2: 挂载管理 =====
@Composable
private fun MountsTab(mounts: List<Mount>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<Mount?>(null) }
    var deletingMount by remember { mutableStateOf<Mount?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), // bottom: dock 悬浮区留白
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加挂载", color = Color.White)
                }
            }
        }

        if (loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF20C997))
                }
            }
        } else if (mounts.isEmpty()) {
            item {
                CardItem { Text("暂无挂载", color = Color(0xFF888888)) }
            }
        } else {
            items(mounts) { mount ->
                var showMenu by remember { mutableStateOf(false) }
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mount.mountPath, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(
                                mount.driver,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                            // 状态标签
                            if (mount.status == "work") {
                                Text("● 工作中", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34C759))
                            } else {
                                Text("● ${mount.status}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9500))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Color(0xFF888888))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("编辑", color = Color(0xFF2A2925)) },
                                    onClick = { showMenu = false; editingMount = mount },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color(0xFF888888)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("启用", color = Color(0xFF34C759)) },
                                    onClick = { showMenu = false; vm.enableMount(mount.id) },
                                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color(0xFF34C759)) },
                                    enabled = mount.disabled
                                )
                                DropdownMenuItem(
                                    text = { Text("禁用", color = Color(0xFFFF9500)) },
                                    onClick = { showMenu = false; vm.disableMount(mount.id) },
                                    leadingIcon = { Icon(Icons.Outlined.Pause, contentDescription = null, tint = Color(0xFFFF9500)) },
                                    enabled = !mount.disabled
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = Color(0xFFFF3B30)) },
                                    onClick = { showMenu = false; deletingMount = mount },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF3B30)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MountEditDialog(
            mount = null,
            onDismiss = { showAddDialog = false },
            onSave = { d, mp, _, r ->
                showAddDialog = false   // 乐观关闭, 防连点重复创建
                vm.createMount(d, mp, 0, r)
            },
        )
    }
    editingMount?.let { mount ->
        MountEditDialog(
            mount = mount,
            onDismiss = { editingMount = null },
            onSave = { d, mp, ord, r ->
                editingMount = null     // 乐观关闭, 防连点重复提交
                vm.updateMount(mount, driver = d, mountPath = mp, order = ord, remark = r)
            },
        )
    }
    // 删除确认: 挂载配置删除后需重建 (存储数据不受影响), 二次确认防误触
    deletingMount?.let { mount ->
        LiquidGlassAlertDialog(
            onDismissRequest = { deletingMount = null },
            title = { Text("删除挂载", color = Color(0xFF2A2925)) },
            text = { Text("确定删除挂载「${mount.mountPath}」？仅移除挂载配置, 不会删除存储上的数据。", color = Color(0xFF888888)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteMount(mount.id) { deletingMount = null } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMount = null }) { Text("取消", color = Color(0xFF888888)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MountEditDialog(mount: Mount?, onDismiss: () -> Unit, onSave: (String, String, Int, String) -> Unit) {
    var driver by remember { mutableStateOf(mount?.driver ?: "Local") }
    var mountPath by remember { mutableStateOf(mount?.mountPath ?: "") }
    // 之前初始化为 "" → 编辑已有挂载一保存就把备注清空
    var remark by remember { mutableStateOf(mount?.remark ?: "") }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mount == null) "添加挂载" else "编辑挂载", color = Color(0xFF2A2925)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mountPath, onValueChange = { mountPath = it }, label = { Text("挂载路径") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
                OutlinedTextField(
                    value = driver, onValueChange = { driver = it }, label = { Text("驱动") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    // 上游 op.UpdateStorage 显式拒绝改驱动 ("driver cannot be changed"), 编辑时锁死
                    enabled = mount == null,
                    supportingText = { if (mount != null) Text("编辑时不可修改驱动", style = MaterialTheme.typography.bodySmall) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
                OutlinedTextField(
                    value = remark, onValueChange = { remark = it }, label = { Text("备注") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                // 透传原 order(之前写死 0, 编辑一次排序就被清零)
                onClick = { onSave(driver, mountPath, mount?.order ?: 0, remark) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997))
            ) { Text("保存", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF888888)) }
        }
    )
}

// ===== Tab 3: 分享管理 =====
@Composable
private fun SharesTab(shares: List<Share>, loading: Boolean, vm: ManagementViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), // bottom: dock 悬浮区留白
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF20C997))
                }
            }
        } else if (shares.isEmpty()) {
            item {
                CardItem { Text("暂无分享", color = Color(0xFF888888)) }
            }
        } else {
            items(shares) { share ->
                var showMenu by remember { mutableStateOf(false) }
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(share.name, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(share.path, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (share.expires.isNotEmpty()) {
                                Text("过期: ${share.expires}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9500))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = Color(0xFF888888))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("删除", color = Color(0xFFFF3B30)) },
                                    onClick = { showMenu = false; vm.deleteShare(share.id) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF3B30)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== Tab 4: 设置 =====
@Composable
private fun SettingsTab(overview: Overview?, serverUrl: String) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), // bottom: dock 悬浮区留白
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardItem {
                Text("服务器信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925))
                Spacer(Modifier.height(8.dp))
                Text("地址: $serverUrl", color = Color(0xFF888888))
                overview?.let {
                    Text("用户数: ${it.userCount}", color = Color(0xFF888888))
                    Text("挂载数: ${it.mountCount}", color = Color(0xFF888888))
                    Text("分享数: ${it.shareCount}", color = Color(0xFF888888))
                }
            }
        }

        item {
            CardItem {
                Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925))
                Spacer(Modifier.height(8.dp))
                Text("${AppConfig.BRAND} v${BuildConfig.VERSION_NAME}", color = Color(0xFF888888))
                Text("基于官方 OpenList 开发", color = Color(0xFF888888))
            }
        }

    }
}

@Composable
private fun PermissionTag(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF20C997).copy(alpha = 0.1f)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = Color(0xFF20C997),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
