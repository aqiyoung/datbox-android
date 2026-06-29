package com.threel.openlist.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.threel.openlist.data.api.TokenStore
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManagementEntryPoint {
    fun tokenStore(): TokenStore
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ManagementScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(context, ManagementEntryPoint::class.java)
    val tokenStore = entryPoint.tokenStore()

    val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
    val token = tokenStore.tokenSync()
    val adminUrl = "$serverUrl/admin"

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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
        containerColor = Color(0xFFF5F4ED),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // 注入 token 到 cookie
            val cookieManager = remember {
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(null, true)
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = settings.userAgentString + " OpenListAndroid"

                        // 注入 Authorization cookie
                        val cookieDomain = serverUrl.removePrefix("https://").removePrefix("http://").split("/")[0]
                        cookieManager.setCookie("$serverUrl/", "Authorization=$token")
                        cookieManager.setCookie("https://$cookieDomain/", "Authorization=$token")
                        cookieManager.flush()

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                // 每次页面跳转都重新注入 cookie
                                cookieManager.setCookie("$serverUrl/", "Authorization=$token")
                                cookieManager.flush()
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                error = "加载失败: $description"
                                loading = false
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                // 内部跳转不拦截
                                return false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                android.util.Log.d("WebView", "${msg?.message()} -- line ${msg?.lineNumber()}")
                                return true
                            }
                        }

                        loadUrl(adminUrl)
                    }
                },
                update = { /* no-op */ }
            )

            // Loading 指示器
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF141413))
            }

            // 错误提示
            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFFF3B30),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
