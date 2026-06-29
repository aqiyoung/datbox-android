package com.threel.openlist.ui.screen

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import com.threel.openlist.ui.viewmodel.ManagementViewModel

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
    var selectedTab by remember { mutableStateOf(ManagementTab.USERS) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F9FC), Color(0xFFFFFFFF))
                )
            )
    ) {
        // 背景装饰
        Box(
            modifier = Modifier
                .offset(x = 120.dp, y = (-80).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color(0xFF20C997).copy(alpha = 0.06f))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("管理", fontWeight = FontWeight.Bold, color = Color(0xFF2A2925)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A2925))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent,
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
                    ManagementTab.SETTINGS -> SettingsTab(state.overview, state.options, state.loading, onBack, vm)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ===== Tab 1: 用户管理 =====
@Composable
private fun UsersTab(users: List<User>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
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
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.username, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(
                                when (user.role) { 0 -> "管理员"; 1 -> "游客"; 2 -> "普通用户"; else -> "未知" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                        }
                        IconButton(onClick = { editingUser = user }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF888888))
                        }
                        IconButton(onClick = { vm.deleteUser(user.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                        }
                    }
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
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (role) { 0 -> "管理员"; 1 -> "游客"; 2 -> "普通用户"; else -> "未知" },
                        onValueChange = {}, readOnly = true, label = { Text("角色") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                            focusedLabelColor = Color(0xFF20C997)
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("管理员", color = Color(0xFF2A2925)) }, onClick = { role = 0; expanded = false })
                        DropdownMenuItem(text = { Text("游客", color = Color(0xFF2A2925)) }, onClick = { role = 1; expanded = false })
                        DropdownMenuItem(text = { Text("普通用户", color = Color(0xFF2A2925)) }, onClick = { role = 2; expanded = false })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, password, role, 0) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C997))
            ) { Text("保存", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF888888)) }
        }
    )
}

// ===== Tab 2: 挂载管理 =====
@Composable
private fun MountsTab(mounts: List<Mount>, loading: Boolean, vm: ManagementViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMount by remember { mutableStateOf<Mount?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
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
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mount.mountPath, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(
                                "${mount.driver} · ${mount.path}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editingMount = mount }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = Color(0xFF888888))
                        }
                        IconButton(onClick = { vm.deleteMount(mount.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                        }
                    }
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
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("名称") },
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF20C997), unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedLabelColor = Color(0xFF20C997), cursorColor = Color(0xFF20C997)
                    )
                )
                OutlinedTextField(
                    value = path, onValueChange = { path = it }, label = { Text("路径") },
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
                onClick = { onSave(name, driver, path, 1) },
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
        contentPadding = PaddingValues(16.dp),
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
                CardItem {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color(0xFF20C997), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(share.name, fontWeight = FontWeight.Medium, color = Color(0xFF2A2925))
                            Text(share.path, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.deleteShare(share.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                        }
                    }
                }
            }
        }
    }
}

// ===== Tab 4: 设置 =====
@Composable
private fun SettingsTab(overview: Overview?, options: List<Option>, loading: Boolean, onBack: () -> Unit, vm: ManagementViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardItem {
                Text("服务器信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2A2925))
                Spacer(Modifier.height(8.dp))
                Text("地址: <your-server>", color = Color(0xFF888888))
                Text("版本: OpenList v4.2.2", color = Color(0xFF888888))
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
                Text("三页云盘 v0.3.7", color = Color(0xFF888888))
                Text("基于官方 OpenList 开发", color = Color(0xFF888888))
            }
        }

        item {
            CardItem(
                modifier = Modifier
                    .clickable { onBack() }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color(0xFFFF3B30))
                    Spacer(Modifier.width(12.dp))
                    Text("退出登录", color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
