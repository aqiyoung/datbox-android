package com.datbox.app.data.api

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val CUSTOM_SERVERS_KEY = stringSetPreferencesKey("custom_servers")
    private val USERNAME_KEY = stringPreferencesKey("last_username")
    private val PASSWORD_KEY = stringPreferencesKey("last_password")
    private val FN_ID_KEY = stringPreferencesKey("fn_id")
    private val FN_PORT_KEY = stringPreferencesKey("fn_port")
    /** 当前服务器是不是「FN Connect 探测出来的地址」, 是的话启动时自动重探 */
    private val FN_MANAGED_KEY = booleanPreferencesKey("fn_managed")
    /** 最近一次 FN 探测可用的地址, 用来判断当前服务器是不是 FN 管的 */
    private val FN_URLS_KEY = stringSetPreferencesKey("fn_urls")

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedServerUrl: String? = null
    @Volatile private var cachedFnId: String? = null
    @Volatile private var cachedFnManaged: Boolean? = null

    val token: Flow<String> = ctx.dataStore.data.map { it[TOKEN_KEY] ?: "" }

    suspend fun saveToken(token: String) {
        cachedToken = token
        ctx.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        cachedToken = ""
        ctx.dataStore.edit {
            it.remove(TOKEN_KEY)
        }
    }

    fun tokenSync(): String {
        cachedToken?.let { return it }
        return runBlocking { token.first() }.also { cachedToken = it }
    }

    val serverUrl: Flow<String> = ctx.dataStore.data.map { it[SERVER_KEY] ?: DEFAULT_SERVER }

    suspend fun saveServerUrl(url: String) {
        cachedServerUrl = url
        ctx.dataStore.edit { it[SERVER_KEY] = url }
    }

    fun serverUrlSync(): String {
        cachedServerUrl?.let { return it }
        return runBlocking { serverUrl.first() }.also { cachedServerUrl = it }
    }

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

    val lastUsername: Flow<String> = ctx.dataStore.data.map { it[USERNAME_KEY] ?: "" }

    val lastPassword: Flow<String> = ctx.dataStore.data
        .map { it[PASSWORD_KEY]?.let(CredentialCipher::decrypt) ?: "" }

    suspend fun saveLastUsername(username: String) {
        ctx.dataStore.edit { it[USERNAME_KEY] = username }
    }

    suspend fun saveLastPassword(password: String) {
        val encrypted = CredentialCipher.encrypt(password) ?: return
        ctx.dataStore.edit { it[PASSWORD_KEY] = encrypted }
    }

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

    // ---- 飞牛 FN Connect ----

    val fnId: Flow<String> = ctx.dataStore.data.map { it[FN_ID_KEY] ?: "" }

    val fnPort: Flow<String> = ctx.dataStore.data.map { it[FN_PORT_KEY] ?: DEFAULT_OL_PORT }

    fun fnIdSync(): String {
        cachedFnId?.let { return it }
        return runBlocking { fnId.first() }.also { cachedFnId = it }
    }

    fun fnPortSync(): String = runBlocking { fnPort.first() }

    suspend fun saveFnConfig(fnId: String, port: String) {
        cachedFnId = fnId
        ctx.dataStore.edit {
            it[FN_ID_KEY] = fnId
            it[FN_PORT_KEY] = port
        }
    }

    fun fnManagedSync(): Boolean {
        cachedFnManaged?.let { return it }
        return runBlocking { ctx.dataStore.data.map { p -> p[FN_MANAGED_KEY] ?: false }.first() }
            .also { cachedFnManaged = it }
    }

    suspend fun saveFnManaged(managed: Boolean) {
        cachedFnManaged = managed
        ctx.dataStore.edit { it[FN_MANAGED_KEY] = managed }
    }

    fun fnUrlsSync(): Set<String> = runBlocking {
        ctx.dataStore.data.map { it[FN_URLS_KEY] ?: emptySet() }.first()
    }

    suspend fun saveFnUrls(urls: Set<String>) {
        ctx.dataStore.edit { it[FN_URLS_KEY] = urls }
    }

    companion object {
        const val DEFAULT_SERVER = "https://<your-server>"
        const val DEFAULT_OL_PORT = "5244"
    }
}
