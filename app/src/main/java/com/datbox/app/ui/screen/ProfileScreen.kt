package com.datbox.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.datbox.app.ui.component.GlassDecorBackground
import com.datbox.app.ui.component.LiquidGlassRow
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.DangerRed
import com.datbox.app.ui.theme.NearBlack
import com.datbox.app.ui.theme.StoneGray
import com.datbox.app.ui.viewmodel.ProfileViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    var showManagement by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    if (showManagement) {
        ManagementScreen(onBack = { showManagement = false })
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    val menuItems = listOf(
        MenuItem("用户管理", Icons.Outlined.Person, "管理用户权限") { showManagement = true },
        MenuItem("关于", Icons.Outlined.Info, "版本信息与更新") { showAbout = true },
    )

    GlassDecorBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = NearBlack)
                    }
                    Text(
                        text = "我的",
                        style = MaterialTheme.typography.titleMedium,
                        color = NearBlack,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        BrandGreen.copy(alpha = 0.18f),
                                        Color(0xFF3A7BD5).copy(alpha = 0.14f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            user?.username ?: "—",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = NearBlack,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (user?.role) {
                                2 -> "管理员"
                                1 -> "游客"
                                0 -> "普通用户"
                                else -> "—"
                            },
                            fontSize = 12.sp,
                            color = StoneGray,
                        )
                    }
                }
            }

            items(menuItems.size) { index ->
                val item = menuItems[index]
                LiquidGlassRow(
                    cornerRadius = 16.dp,
                    onClick = item.onClick,
                    leading = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = NearBlack,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    title = item.title,
                    subtitle = item.subtitle,
                    trailing = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = StoneGray,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                LiquidGlassRow(
                    cornerRadius = 16.dp,
                    onClick = onLogout,
                    leading = {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    title = "退出登录",
                    subtitle = "退出当前账号",
                )
            }
        }
    }
}

private data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val onClick: () -> Unit,
)