package com.threel.openlist.data.model

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

private val Context.serverConfigDataStore by preferencesDataStore("server_config")

@Singleton
class ServerConfig @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val SERVER_KEY = stringPreferencesKey("server_url")

    val serverUrl: Flow<String> = ctx.serverConfigDataStore.data.map { prefs ->
        prefs[SERVER_KEY] ?: DEFAULT_SERVER
    }

    suspend fun saveServerUrl(url: String) {
        ctx.serverConfigDataStore.edit { prefs ->
            prefs[SERVER_KEY] = url
        }
    }

    fun serverUrlSync(): String = runBlocking { serverUrl.first() }

    companion object {
        const val DEFAULT_SERVER = "https://<your-server>"
    }
}
