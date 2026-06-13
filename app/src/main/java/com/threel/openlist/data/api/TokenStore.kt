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
    private val PASSWORD_KEY = stringPreferencesKey("last_password")  // 老板 6/13: 记住密码

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

    /** 老板 6/13 拍: 记住账号 + 密码 (不要 token) */
    val lastUsername: Flow<String> = ctx.dataStore.data.map { it[USERNAME_KEY] ?: "" }
    val lastPassword: Flow<String> = ctx.dataStore.data.map { it[PASSWORD_KEY] ?: "" }

    suspend fun saveLastCredentials(username: String, password: String) {
        ctx.dataStore.edit {
            it[USERNAME_KEY] = username
            it[PASSWORD_KEY] = password
        }
    }

    suspend fun clearLastCredentials() {
        ctx.dataStore.edit {
            it.remove(USERNAME_KEY)
            it.remove(PASSWORD_KEY)
        }
    }

    companion object {
        // 老板 6/13 11:46 拍: 默认走 https://<your-server> (OpenList web)
        const val DEFAULT_SERVER = "https://<your-server>"
    }
}
