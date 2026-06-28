package com.threel.openlist.data.api

import android.content.Context
import com.threel.openlist.data.model.ManagementResponse
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManagementRepository @Inject constructor(
    private val api: ManagementApi,
    private val tokenStore: TokenStore,
    @ApplicationContext private val context: Context,
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> = runCatching {
        call()
    }

    // ===== 用户管理 =====
    suspend fun userList(): Result<List<User>> = safeApiCall {
        val resp = api.userList()
        if (resp.code != 200) error(resp.message)
        resp.data
    }

    suspend fun userCreate(username: String, password: String, role: Int = 1, permission: Int = 0): Result<User> = safeApiCall {
        val resp = api.userCreate(UserCreateRequest(username, password, role, permission))
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
    }

    suspend fun userUpdate(id: Int, username: String = "", password: String = "", role: Int = -1, permission: Int = -1): Result<Unit> = safeApiCall {
        val resp = api.userUpdate(UserUpdateRequest(id, username, password, role, permission))
        if (resp.code != 200) error(resp.message)
    }

    suspend fun userDelete(id: Int): Result<Unit> = safeApiCall {
        val resp = api.userDelete(id)
        if (resp.code != 200) error(resp.message)
    }

    // ===== 挂载管理 =====
    suspend fun mountList(): Result<List<Mount>> = safeApiCall {
        val resp = api.mountList()
        if (resp.code != 200) error(resp.message)
        resp.data
    }

    suspend fun mountCreate(name: String, driver: String = "Local", path: String = "", status: Int = 1): Result<Mount> = safeApiCall {
        val resp = api.mountCreate(MountCreateRequest(name, driver, path, status))
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
    }

    suspend fun mountUpdate(id: Int, name: String = "", driver: String = "", path: String = "", status: Int = -1): Result<Unit> = safeApiCall {
        val resp = api.mountUpdate(MountUpdateRequest(id, name, driver, path, status))
        if (resp.code != 200) error(resp.message)
    }

    suspend fun mountDelete(id: Int): Result<Unit> = safeApiCall {
        val resp = api.mountDelete(id)
        if (resp.code != 200) error(resp.message)
    }

    // ===== 分享管理 =====
    suspend fun shareList(): Result<List<Share>> = safeApiCall {
        val resp = api.shareList()
        if (resp.code != 200) error(resp.message)
        resp.data
    }

    suspend fun shareCreate(files: List<String>, expires: String = "", password: String = ""): Result<Share> = safeApiCall {
        val resp = api.shareCreate(ShareCreateRequest(files, expires, password))
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
    }

    suspend fun shareDelete(id: String): Result<Unit> = safeApiCall {
        val resp = api.shareDelete(id)
        if (resp.code != 200) error(resp.message)
    }

    // ===== 系统设置 =====
    suspend fun overview(): Result<Overview> = safeApiCall {
        val resp = api.overview()
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
    }

    suspend fun optionList(): Result<List<Option>> = safeApiCall {
        val resp = api.optionList()
        if (resp.code != 200) error(resp.message)
        resp.data
    }

    suspend fun optionUpdate(key: String, value: String): Result<Unit> = safeApiCall {
        val resp = api.optionUpdate(OptionUpdateRequest(key, value))
        if (resp.code != 200) error(resp.message)
    }

    // ===== 文件操作 =====
    suspend fun mkdir(path: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = """{"path":"$path","name":"$name"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("${tokenStore.serverUrlSync().trimEnd('/')}/api/fs/mkdir")
                .header("Authorization", tokenStore.tokenSync())
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    suspend fun rename(path: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = """{"path":"$path","new_name":"$newName"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("${tokenStore.serverUrlSync().trimEnd('/')}/api/fs/rename")
                .header("Authorization", tokenStore.tokenSync())
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    suspend fun move(path: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = """{"path":"$path","new_path":"$newPath"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("${tokenStore.serverUrlSync().trimEnd('/')}/api/fs/move")
                .header("Authorization", tokenStore.tokenSync())
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = """{"path":"$path"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("${tokenStore.serverUrlSync().trimEnd('/')}/api/fs/delete")
                .header("Authorization", tokenStore.tokenSync())
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }
}
