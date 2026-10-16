package com.datbox.app.data.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.datbox.app.util.AppConfig
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

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "AppUpdate"
    private val engine = GitHubUpdateEngine(
        UpdateConfig(owner = "aqiyoung", repo = "datbox-android"),
    )
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val currentVersionName: String get() = AppConfig.versionName(context)

    val autoCheckEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[AUTO_CHECK_KEY] ?: true }

    suspend fun setAutoCheckEnabled(value: Boolean) {
        context.updateDataStore.edit { it[AUTO_CHECK_KEY] = value }
    }

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

    fun openRelease(context: Context, url: String): OpenReleaseResult =
        engine.openRelease(context, url)
}

private val AUTO_CHECK_KEY = booleanPreferencesKey("auto_check_updates")
