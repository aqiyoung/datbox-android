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
    private val CUSTOM_SERVERS_KEY = stringSetPreferencesKey("custom_servers")
    private val USERNAME_KEY = stringPreferencesKey("last_username")
    private val PASSWORD_KEY = stringPreferencesKey("last_password")

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedServerUrl: String? = null

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

    companion object {
        const val DEFAULT_SERVER = "https://fn.threel.site"
    }
}
