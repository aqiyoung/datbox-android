package com.threel.openlist.data.api

import android.content.Context
import com.threel.openlist.data.model.ManagementResponse
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import com.threel.openlist.data.model.UserInfo
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

    // ===== 当前用户 =====
    suspend fun currentUser(): Result<UserInfo> = safeApiCall {
        val resp = api.currentUser()
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
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

    suspend fun mountCreate(driver: String = "Local", mountPath: String, order: Int = 0, remark: String = ""): Result<Mount> = safeApiCall {
        val resp = api.mountCreate(MountCreateRequest(driver, mountPath, order, remark))
        if (resp.code != 200 || resp.data == null) error(resp.message)
        resp.data
    }

    suspend fun mountUpdate(id: Int, driver: String = "Local", mountPath: String = "", order: Int = -1, remark: String = ""): Result<Unit> = safeApiCall {
        val resp = api.mountUpdate(MountUpdateRequest(id, driver, mountPath, order, remark))
        if (resp.code != 200) error(resp.message)
    }

    suspend fun mountDelete(id: Int): Result<Unit> = safeApiCall {
        val resp = api.mountDelete(id)
        if (resp.code != 200) error(resp.message)
    }

    suspend fun mountEnable(id: Int): Result<Unit> = safeApiCall {
        val resp = api.mountEnable(id)
        if (resp.code != 200) error(resp.message)
    }

    suspend fun mountDisable(id: Int): Result<Unit> = safeApiCall {
        val resp = api.mountDisable(id)
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
    suspend fun optionList(): Result<List<Option>> = safeApiCall {
        val resp = api.optionList()
        if (resp.code != 200) error(resp.message)
        resp.data
    }

    suspend fun optionSave(key: String, value: String): Result<Unit> = safeApiCall {
        val resp = api.optionSave(OptionUpdateRequest(key, value))
        if (resp.code != 200) error(resp.message)
    }

    suspend fun optionDelete(key: String): Result<Unit> = safeApiCall {
        val resp = api.optionDelete(key)
        if (resp.code != 200) error(resp.message)
    }

    // ===== 文件操作 (OkHttp 直发) =====
    private suspend fun fsPost(path: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBody = body.toRequestBody(mediaType)
            val req = Request.Builder()
                .url("${tokenStore.serverUrlSync().trimEnd('/')}/api/fs/$path")
                .header("Authorization", tokenStore.tokenSync())
                .post(reqBody)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    suspend fun mkdir(path: String, name: String): Result<Unit> = fsPost("mkdir", """{"path":"$path","name":"$name"}""")
    suspend fun rename(path: String, newName: String): Result<Unit> = fsPost("rename", """{"path":"$path","name":"$newName"}""")
    suspend fun move(path: String, newPath: String): Result<Unit> = fsPost("move", """{"path":"$path","new_path":"$newPath"}""")
    suspend fun delete(dir: String, name: String): Result<Unit> = fsPost("remove", """{"dir":"$dir","names":["$name"]}""")
}
