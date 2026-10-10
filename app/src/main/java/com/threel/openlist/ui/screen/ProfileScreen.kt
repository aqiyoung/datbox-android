package com.threel.openlist.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.ui.component.GlassDecorBackground
import com.threel.openlist.ui.component.LiquidGlassCard
import com.threel.openlist.ui.component.LiquidGlassRow
import com.threel.openlist.ui.theme.StoneGray
import com.threel.openlist.ui.theme.WarmIvory

/**
 * "我的"页面 — 合并管理 + 关于 + 上传
 *
 * 入口:
 * - 用户管理 (ManagementScreen)
 * - 关于 (AboutScreen)
 * - 上传文件
 * - 退出登录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    var showManagement by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // 子页面: 管理
    if (showManagement) {
        ManagementScreen(onBack = { showManagement = false })
        return
    }

    // 子页面: 关于
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF141413))
                    }
                    Text(
                        text = "我的",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF141413),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 用户信息卡片
            item {
                LiquidGlassCard(
                    cornerRadius = 20.dp,
                    contentPadding = 18.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF20C997).copy(alpha = 0.18f),
                                            Color(0xFF3A7BD5).copy(alpha = 0.14f),
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color(0xFF20C997),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "liyang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF141413),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "管理员",
                                fontSize = 12.sp,
                                color = StoneGray,
                            )
                        }
                    }
                }
            }

            // 菜单项
            items(menuItems.size) { index ->
                val item = menuItems[index]
                LiquidGlassRow(
                    cornerRadius = 16.dp,
                    onClick = item.onClick,
                    leading = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = Color(0xFF141413),
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

            // 退出登录
            item {
                Spacer(Modifier.height(4.dp))
                LiquidGlassRow(
                    cornerRadius = 16.dp,
                    onClick = onLogout,
                    leading = {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = null,
                            tint = Color(0xFFFF3B30),
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