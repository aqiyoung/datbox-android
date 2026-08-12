package com.threel.openlist.data.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.threel.openlist.util.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore("openlist_update_prefs")

/**
 * 统一更新检查（对齐 sanyelive / FeiNiuMusic / synapse 的 app_update_core 引擎）。
 *
 * - 数据源：GitHub Release（不再依赖自托管 latest.json）。
 * - 代理链：[gh-llkk.cc → gh-proxy.com → 直连]，国内直连 api.github.com 被墙时自动降级。
 * - 结论语义：所有源都失败 → checkForUpdate() 返回 null（调用方必须如实报"检查失败"，绝不谎报"已是最新"）。
 * - 仅当 tag_name 比当前 versionName 更新时才 hasUpdate。
 * - 启动时是否自动检查可由设置关闭（默认开），手动检查永远执行。
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "AppUpdate"
    private val engine = GitHubUpdateEngine(
        UpdateConfig(owner = "aqiyoung", repo = "openlist-android"),
    )
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 当前安装版本（来自 PackageManager）。 */
    val currentVersionName: String get() = AppConfig.versionName(context)

    /** 启动时是否自动检查更新（设置可关，默认开）。 */
    val autoCheckEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[AUTO_CHECK_KEY] ?: true }

    suspend fun setAutoCheckEnabled(value: Boolean) {
        context.updateDataStore.edit { it[AUTO_CHECK_KEY] = value }
    }

    /**
     * 检查更新。返回 null = 所有数据源都失败（网络不可达），调用方应提示"检查失败"。
     * 返回对象但 hasUpdate=false = 确实已是最新。
     */
    suspend fun checkForUpdate(): GitHubUpdateResult? = withContext(Dispatchers.IO) {
        val current = AppConfig.versionName(context)
        engine.check(
            fetch = { url, headers ->
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                val resp = client.newCall(builder.build()).execute()
                UpdateHttpResponse(resp.code, resp.body?.string().orEmpty())
            },
            currentVersion = current,
        )
    }

    /** 跳转发布页（GitHub App → 浏览器 → 复制链接）。 */
    fun openRelease(context: Context, url: String): OpenReleaseResult =
        engine.openRelease(context, url)
}

private val AUTO_CHECK_KEY = booleanPreferencesKey("auto_check_updates")
