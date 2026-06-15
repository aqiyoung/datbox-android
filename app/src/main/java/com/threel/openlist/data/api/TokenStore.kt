package com.threel.openlist.data.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("openlist_prefs")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val TOKEN_KEY = stringPreferencesKey("jwt_token")
    private val SERVER_KEY = stringPreferencesKey("server_url")
    private val USERNAME_KEY = stringPreferencesKey("last_username")  // 老板 6/13: 记住账号
    // 老板 6/15 拍: 干掉明文密码缓存 (6/13 加的, 6/15 发现老板改密码后 APP 登不上 + 安全考虑)
    // 不再缓存 password, 改由用户每次手动输入

    val token: Flow<String> = ctx.dataStore.data.map { it[TOKEN_KEY] ?: "" }

    suspend fun saveToken(token: String) {
        ctx.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    /** 同步拿 token（用于 OkHttp interceptor，避免每次都 await） */
    fun tokenSync(): String = runBlocking { token.first() }

    val serverUrl: Flow<String> = ctx.dataStore.data.map { it[SERVER_KEY] ?: DEFAULT_SERVER }

    suspend fun saveServerUrl(url: String) {
        ctx.dataStore.edit { it[SERVER_KEY] = url }
    }

    fun serverUrlSync(): String = runBlocking { serverUrl.first() }

    /** 老板 6/13 拍: 记住账号 (不要密码) */
    val lastUsername: Flow<String> = ctx.dataStore.data.map { it[USERNAME_KEY] ?: "" }

    /** 老板 6/15 拍: 不再缓存密码, 只缓存账号 */
    suspend fun saveLastUsername(username: String) {
        ctx.dataStore.edit { it[USERNAME_KEY] = username }
    }

    /** 兼容老代码调用, 只清账号, 不再有密码要清 */
    suspend fun saveLastCredentials(username: String, @Suppress("UNUSED_PARAMETER") password: String) {
        // password 参数保留是为不破坏旧调用点, 但不再写入 DataStore
        saveLastUsername(username)
    }

    suspend fun clearLastCredentials() {
        ctx.dataStore.edit { it.remove(USERNAME_KEY) }
    }

    companion object {
        // 老板 6/13 11:46 拍: 默认走 https://<your-server> (OpenList web)
        const val DEFAULT_SERVER = "https://<your-server>"
    }
}
