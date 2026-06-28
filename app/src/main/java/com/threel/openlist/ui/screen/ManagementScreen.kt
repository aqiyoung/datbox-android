package com.threel.openlist.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import com.threel.openlist.ui.component.LiquidGlassCard
import com.threel.openlist.ui.component.LiquidGlassPrimaryButton
import com.threel.openlist.ui.viewmodel.ManagementViewModel

enum class ManagementTab(val label: String, val icon: ImageVector) {
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
    var selectedTab by remember { mutableStateOf(ManagementTab.USERS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5F4ED)
                )
            )
        },
        containerColor = Color(0xFFF5F4ED)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFFF5F4ED),
                contentColor = Color(0xFF141413)
            ) {
                ManagementTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) }
                    )
                }
            }

            // Content
            when (selectedTab) {
                ManagementTab.USERS -> UsersTab(state.users, state.loading, vm)
                ManagementTab.MOUNTS -> MountsTab(state.mounts, state.loading, vm)
                ManagementTab.SHARES -> SharesTab(state.shares, state.loading, vm)
                ManagementTab.SETTINGS -> SettingsTab(state.overview, state.options, state.loading, onBack, vm)
            }
        }
    }

    // Error snackbar
    state.error?.let { err ->
        LaunchedEffect(err) { vm.clearError() }
    }
}

// ===== Tab 1: 用户管理 =====
@Composable
private fun UsersTab(users: List<User>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF141413))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        LiquidGlassPrimaryButton(
                            text = "添加用户",
                            icon = Icons.Outlined.Add,
                            onClick = { showAddDialog = true },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
                items(users) { user ->
                    UserCard(
                        user = user,
                        onEdit = { editingUser = user },
                        onDelete = { vm.deleteUser(user.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        UserEditDialog(
            user = null,
            onDismiss = { showAddDialog = false },
            onSave = { username, password, role, permission ->
                vm.createUser(username, password, role, permission) { showAddDialog = false }
            }
        )
    }

    editingUser?.let { user ->
        UserEditDialog(
            user = user,
            onDismiss = { editingUser = null },
            onSave = { username, password, role, permission ->
                vm.updateUser(user.id, username, password, role, permission) { editingUser = null }
            }
        )
    }
}

@Composable
private fun UserCard(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = Color(0xFF141413),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF141413)
                )
                Text(
                    text = when (user.role) {
                        0 -> "管理员"
                        1 -> "游客"
                        2 -> "普通用户"
                        else -> "未知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF87867F))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFB33A3A))
            }
        }
    }
}

@Composable
private fun UserEditDialog(
    user: User?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit
) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableIntStateOf(user?.role ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "添加用户" else "编辑用户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (user == null) "密码" else "新密码（留空不修改）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Role selector
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (role) { 0 -> "管理员"; 1 -> "游客"; 2 -> "普通用户"; else -> "未知" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("角色") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("管理员") }, onClick = { role = 0; expanded = false })
                        DropdownMenuItem(text = { Text("游客") }, onClick = { role = 1; expanded = false })
                        DropdownMenuItem(text = { Text("普通用户") }, onClick = { role = 2; expanded = false })
                    }
                }
            }
        },
        confirmButton = {
            LiquidGlassPrimaryButton(
                text = "保存",
                onClick = { onSave(username, password, role, 0) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== Tab 2: 挂载管理 =====
@Composable
private fun MountsTab(mounts: List<Mount>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<Mount?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF141413))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        LiquidGlassPrimaryButton(
                            text = "添加挂载",
                            icon = Icons.Outlined.Add,
                            onClick = { showAddDialog = true },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
                items(mounts) { mount ->
                    MountCard(
                        mount = mount,
                        onEdit = { editingMount = mount },
                        onDelete = { vm.deleteMount(mount.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        MountEditDialog(
            mount = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, driver, path, status ->
                vm.createMount(name, driver, path, status) { showAddDialog = false }
            }
        )
    }

    editingMount?.let { mount ->
        MountEditDialog(
            mount = mount,
            onDismiss = { editingMount = null },
            onSave = { name, driver, path, status ->
                vm.updateMount(mount.id, name, driver, path, status) { editingMount = null }
            }
        )
    }
}

@Composable
private fun MountCard(mount: Mount, onEdit: () -> Unit, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Storage,
                contentDescription = null,
                tint = Color(0xFF141413),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mount.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF141413)
                )
                Text(
                    text = "${mount.driver} · ${mount.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF87867F))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFB33A3A))
            }
        }
    }
}

@Composable
private fun MountEditDialog(
    mount: Mount?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(mount?.name ?: "") }
    var driver by remember { mutableStateOf(mount?.driver ?: "Local") }
    var path by remember { mutableStateOf(mount?.path ?: "") }
    var status by remember { mutableIntStateOf(mount?.status ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mount == null) "添加挂载" else "编辑挂载") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = driver,
                    onValueChange = { driver = it },
                    label = { Text("驱动") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            LiquidGlassPrimaryButton(
                text = "保存",
                onClick = { onSave(name, driver, path, status) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== Tab 3: 分享管理 =====
@Composable
private fun SharesTab(shares: List<Share>, loading: Boolean, vm: ManagementViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF141413))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shares) { share ->
                    ShareCard(
                        share = share,
                        onDelete = { vm.deleteShare(share.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareCard(share: Share, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = null,
                tint = Color(0xFF141413),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = share.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF141413)
                )
                Text(
                    text = share.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF87867F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFB33A3A))
            }
        }
    }
}

// ===== Tab 4: 设置 =====
@Composable
private fun SettingsTab(
    overview: Overview?,
    options: List<Option>,
    loading: Boolean,
    onBack: () -> Unit,
    vm: ManagementViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 服务器信息
        item {
            LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
                Column {
                    Text(
                        "服务器信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF141413)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("地址: <your-server>", color = Color(0xFF87867F))
                    Text("版本: OpenList v4.2.2", color = Color(0xFF87867F))
                    overview?.let {
                        Text("用户数: ${it.userCount}", color = Color(0xFF87867F))
                        Text("挂载数: ${it.mountCount}", color = Color(0xFF87867F))
                        Text("分享数: ${it.shareCount}", color = Color(0xFF87867F))
                    }
                }
            }
        }

        // 关于
        item {
            LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
                Column {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF141413)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("三页云盘 v0.3.36", color = Color(0xFF87867F))
                    Text("基于官方 OpenList 开发", color = Color(0xFF87867F))
                }
            }
        }

        // 退出登录
        item {
            LiquidGlassCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp,
                modifier = Modifier.clickable { onBack() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color(0xFFB33A3A))
                    Spacer(Modifier.width(12.dp))
                    Text("退出登录", color = Color(0xFFB33A3A), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
