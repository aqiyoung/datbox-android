package com.threel.openlist.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threel.openlist.R
import com.threel.openlist.util.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            // 品牌大字
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_brand_line),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))

            // 版本信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutRow("应用名称", stringResource(R.string.app_name))
                    Divider(Modifier.padding(vertical = 8.dp))
                    AboutRow("英文名", stringResource(R.string.app_name_en))
                    Divider(Modifier.padding(vertical = 8.dp))
                    AboutRow("版本", AppConfig.fullVersionString(context))
                    Divider(Modifier.padding(vertical = 8.dp))
                    AboutRow("构建类型", AppConfig.buildType)
                    Divider(Modifier.padding(vertical = 8.dp))
                    AboutRow("Package", context.packageName)
                }
            }
            Spacer(Modifier.height(24.dp))

            // 链接
            OutlinedCard(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.CHANGELOG_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("版本更新日志", fontWeight = FontWeight.Medium)
                        Text(
                            AppConfig.CHANGELOG_URL,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedCard(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.UPDATE_CHECK_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("检查更新", fontWeight = FontWeight.Medium)
                        Text(
                            AppConfig.UPDATE_CHECK_URL,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
