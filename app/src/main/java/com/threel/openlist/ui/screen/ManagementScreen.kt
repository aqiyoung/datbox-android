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

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F4ED)) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F4ED))
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // iOS 26 风格 Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFFF5F4ED),
                contentColor = Color(0xFF2A2925),
                indicator = { TabRowDefaults.SecondaryIndicator(color = Color(0xFF2A2925)) }
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

            when (selectedTab) {
                ManagementTab.USERS -> UsersTab(state.users, state.loading, vm)
                ManagementTab.MOUNTS -> MountsTab(state.mounts, state.loading, vm)
                ManagementTab.SHARES -> SharesTab(state.shares, state.loading, vm)
                ManagementTab.SETTINGS -> SettingsTab(state.overview, state.options, state.loading, onBack, vm)
            }
        }
    }
    } // Surface

    state.error?.let { err -> LaunchedEffect(err) { vm.clearError() } }
}

// ===== Tab 1: 用户管理 =====
@Composable
private fun UsersTab(users: List<User>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2A2925))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LiquidGlassPrimaryButton(text = "添加用户", icon = Icons.Outlined.Add, onClick = { showAddDialog = true }, modifier = Modifier.width(140.dp))
                    }
                }
                items(users) { user ->
                    UserCard(user = user, onEdit = { editingUser = user }, onDelete = { vm.deleteUser(user.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        UserEditDialog(user = null, onDismiss = { showAddDialog = false }, onSave = { u, p, r, perm -> vm.createUser(u, p, r, perm) { showAddDialog = false } })
    }
    editingUser?.let { user ->
        UserEditDialog(user = user, onDismiss = { editingUser = null }, onSave = { u, p, r, perm -> vm.updateUser(user.id, u, p, r, perm) { editingUser = null } })
    }
}

@Composable
private fun UserCard(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF2A2925), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                Text(when (user.role) { 0 -> "管理员"; 1 -> "游客"; 2 -> "普通用户"; else -> "未知" }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5B57))
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF5C5B57)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditDialog(user: User?, onDismiss: () -> Unit, onSave: (String, String, Int, Int) -> Unit) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableIntStateOf(user?.role ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (user == null) "添加用户" else "编辑用户", color = Color(0xFF2A2925)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F), cursorColor = Color(0xFF2A2925)))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(if (user == null) "密码" else "新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F), cursorColor = Color(0xFF2A2925)))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (role) { 0 -> "管理员"; 1 -> "游客"; 2 -> "普通用户"; else -> "未知" },
                        onValueChange = {}, readOnly = true, label = { Text("角色") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F))
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("管理员", color = Color(0xFF2A2925)) }, onClick = { role = 0; expanded = false })
                        DropdownMenuItem(text = { Text("游客", color = Color(0xFF2A2925)) }, onClick = { role = 1; expanded = false })
                        DropdownMenuItem(text = { Text("普通用户", color = Color(0xFF2A2925)) }, onClick = { role = 2; expanded = false })
                    }
                }
            }
        },
        confirmButton = { LiquidGlassPrimaryButton(text = "保存", onClick = { onSave(username, password, role, 0) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF2A2925)) } }
    )
}

// ===== Tab 2: 挂载管理 =====
@Composable
private fun MountsTab(mounts: List<Mount>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<Mount?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2A2925)) }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LiquidGlassPrimaryButton(text = "添加挂载", icon = Icons.Outlined.Add, onClick = { showAddDialog = true }, modifier = Modifier.width(140.dp))
                    }
                }
                items(mounts) { mount ->
                    MountCard(mount = mount, onEdit = { editingMount = mount }, onDelete = { vm.deleteMount(mount.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        MountEditDialog(mount = null, onDismiss = { showAddDialog = false }, onSave = { n, d, p, s -> vm.createMount(n, d, p, s) { showAddDialog = false } })
    }
    editingMount?.let { mount ->
        MountEditDialog(mount = mount, onDismiss = { editingMount = null }, onSave = { n, d, p, s -> vm.updateMount(mount.id, n, d, p, s) { editingMount = null } })
    }
}

@Composable
private fun MountCard(mount: Mount, onEdit: () -> Unit, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Storage, contentDescription = null, tint = Color(0xFF2A2925), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mount.mountPath, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                Text("${mount.driver} · ${mount.path}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5B57), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF5C5B57)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MountEditDialog(mount: Mount?, onDismiss: () -> Unit, onSave: (String, String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(mount?.mountPath ?: "") }
    var driver by remember { mutableStateOf(mount?.driver ?: "Local") }
    var path by remember { mutableStateOf(mount?.mountPath ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (mount == null) "添加挂载" else "编辑挂载", color = Color(0xFF2A2925)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F), cursorColor = Color(0xFF2A2925)))
                OutlinedTextField(value = driver, onValueChange = { driver = it }, label = { Text("驱动") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F), cursorColor = Color(0xFF2A2925)))
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("路径") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2A2925), unfocusedBorderColor = Color(0xFFD9D8D4), focusedLabelColor = Color(0xFF2A2925), unfocusedLabelColor = Color(0xFF87867F), cursorColor = Color(0xFF2A2925)))
            }
        },
        confirmButton = { LiquidGlassPrimaryButton(text = "保存", onClick = { onSave(name, driver, path, 1) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF2A2925)) } }
    )
}

// ===== Tab 3: 分享管理 =====
@Composable
private fun SharesTab(shares: List<Share>, loading: Boolean, vm: ManagementViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2A2925)) }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shares) { share -> ShareCard(share = share, onDelete = { vm.deleteShare(share.id) }) }
            }
        }
    }
}

@Composable
private fun ShareCard(share: Share, onDelete: () -> Unit) {
    LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Share, contentDescription = null, tint = Color(0xFF2A2925), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(share.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                Text(share.path, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5B57), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30)) }
        }
    }
}

// ===== Tab 4: 设置 =====
@Composable
private fun SettingsTab(overview: Overview?, options: List<Option>, loading: Boolean, onBack: () -> Unit, vm: ManagementViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
                Column {
                    Text("服务器信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925))
                    Spacer(Modifier.height(8.dp))
                    Text("地址: <your-server>", color = Color(0xFF5C5B57))
                    Text("版本: OpenList v4.2.2", color = Color(0xFF5C5B57))
                    overview?.let {
                        Text("用户数: ${it.userCount}", color = Color(0xFF5C5B57))
                        Text("挂载数: ${it.mountCount}", color = Color(0xFF5C5B57))
                        Text("分享数: ${it.shareCount}", color = Color(0xFF5C5B57))
                    }
                }
            }
        }
        item {
            LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
                Column {
                    Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925))
                    Spacer(Modifier.height(8.dp))
                    Text("三页云盘 v0.3.38", color = Color(0xFF5C5B57))
                    Text("基于官方 OpenList 开发", color = Color(0xFF5C5B57))
                }
            }
        }
        item {
            LiquidGlassCard(cornerRadius = 16.dp, contentPadding = 16.dp, modifier = Modifier.clickable { onBack() }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color(0xFFFF3B30))
                    Spacer(Modifier.width(12.dp))
                    Text("退出登录", color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
