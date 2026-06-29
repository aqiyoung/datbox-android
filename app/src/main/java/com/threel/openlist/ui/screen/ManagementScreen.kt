package com.threel.openlist.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
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
    val tokenStore = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ManagementEntryPoint::class.java).tokenStore()
    }

    val serverUrl = remember { tokenStore.serverUrlSync().trimEnd('/') }
    val token = remember { tokenStore.tokenSync() }
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "$userAgentString OpenListAndroid"
                        }

                        // 注入 Authorization cookie
                        try {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            val host = serverUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
                            cm.setCookie(serverUrl, "Authorization=$token")
                            cm.setCookie("https://$host", "Authorization=$token")
                            cm.flush()
                        } catch (e: Exception) {
                            Log.e("ManagementScreen", "Cookie inject failed", e)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                error = "加载失败: $description"
                                loading = false
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                Log.d("WebView", "${msg?.message()} -- line ${msg?.lineNumber()}")
                                return true
                            }
                        }

                        loadUrl(adminUrl)
                    }
                }
            )

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF141413))
            }

            error?.let {
                Text(text = it, color = Color(0xFFFF3B30))
            }
        }
    }
}
