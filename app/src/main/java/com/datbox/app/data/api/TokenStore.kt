package com.datbox.app.data.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    private val CUSTOM_SERVERS_KEY = stringSetPreferencesKey("custom_servers")  // 用户自建服务器列表（预设不入库）
    private val USERNAME_KEY = stringPreferencesKey("last_username")
    // v0.3.37: 恢复密码缓存；v0.3.111: 值改为 CredentialCipher 加密后的密文，不再明文落盘
    private val PASSWORD_KEY = stringPreferencesKey("last_password")

    // 内存缓存：拦截器每次请求都读 token，runBlocking 读 DataStore 会阻塞 OkHttp 线程。
    // 登录/登出时更新缓存，避免热路径上的协程阻塞（ANR 风险）。
    @Volatile private var cachedToken: String? = null

    val token: Flow<String> = ctx.dataStore.data.map { it[TOKEN_KEY] ?: "" }

    suspend fun saveToken(token: String) {
        cachedToken = token
        ctx.dataStore.edit { it[TOKEN_KEY] = token }
    }

    /** 退出登录: 只清 token; 「记住我」存下的用户名+密码保留 — 用户要求退出后密码还在, 下次打开自动填上 */
    suspend fun clear() {
        cachedToken = ""
        ctx.dataStore.edit {
            it.remove(TOKEN_KEY)
        }
    }

    /** 同步拿 token（用于 OkHttp interceptor）。优先走内存缓存，未命中再回退到 DataStore。 */
    fun tokenSync(): String {
        cachedToken?.let { return it }
        return runBlocking { token.first() }.also { cachedToken = it }
    }

    val serverUrl: Flow<String> = ctx.dataStore.data.map { it[SERVER_KEY] ?: DEFAULT_SERVER }

    suspend fun saveServerUrl(url: String) {
        ctx.dataStore.edit { it[SERVER_KEY] = url }
    }

    fun serverUrlSync(): String = runBlocking { serverUrl.first() }

    /** 用户自建的服务器列表（与 3 个硬编码预设区分开，持久化以便下次打开仍在）。 */
    val customServers: Flow<Set<String>> =
        ctx.dataStore.data.map { it[CUSTOM_SERVERS_KEY] ?: emptySet() }

    fun customServersSync(): Set<String> = runBlocking { customServers.first() }

    suspend fun addCustomServer(url: String) {
        ctx.dataStore.edit { prefs ->
            val set = prefs[CUSTOM_SERVERS_KEY]?.toMutableSet() ?: mutableSetOf()
            set.add(url)
            prefs[CUSTOM_SERVERS_KEY] = set
        }
    }

    suspend fun removeCustomServer(url: String) {
        ctx.dataStore.edit { prefs ->
            val set = prefs[CUSTOM_SERVERS_KEY] ?: return@edit
            if (url in set) {
                prefs[CUSTOM_SERVERS_KEY] = set - url
            }
        }
    }

    /** 记住账号 */
    val lastUsername: Flow<String> = ctx.dataStore.data.map { it[USERNAME_KEY] ?: "" }

    /** 记住密码（落盘是密文，读取时解密；旧版明文与解密失败都视为空 → 重新输入一次即可） */
    val lastPassword: Flow<String> = ctx.dataStore.data
        .map { it[PASSWORD_KEY]?.let(CredentialCipher::decrypt) ?: "" }

    suspend fun saveLastUsername(username: String) {
        ctx.dataStore.edit { it[USERNAME_KEY] = username }
    }

    suspend fun saveLastPassword(password: String) {
        val encrypted = CredentialCipher.encrypt(password) ?: return
        ctx.dataStore.edit { it[PASSWORD_KEY] = encrypted }
    }

    /** 保存账号密码（密码走 Keystore 加密；加密失败则只存用户名，不落明文） */
    suspend fun saveLastCredentials(username: String, password: String) {
        val encrypted = CredentialCipher.encrypt(password)
        ctx.dataStore.edit {
            it[USERNAME_KEY] = username
            if (encrypted != null) it[PASSWORD_KEY] = encrypted else it.remove(PASSWORD_KEY)
        }
    }

    suspend fun clearLastCredentials() {
        ctx.dataStore.edit {
            it.remove(USERNAME_KEY)
            it.remove(PASSWORD_KEY)
        }
    }

    companion object {
        // 老板 6/13 11:46 拍: 默认走 https://fn.threel.site (OpenList web)
        const val DEFAULT_SERVER = "https://fn.threel.site"
    }
}
