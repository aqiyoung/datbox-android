package com.threel.openlist.data.api

import com.threel.openlist.data.model.LoginResponse
import com.threel.openlist.data.model.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 包装 OpenList API，处理认证头。
 */
@Singleton
class OpenListRepository @Inject constructor(
    private val api: OpenListApi,
    private val tokenStore: TokenStore,
) {
    suspend fun login(username: String, password: String): Result<String> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        if (resp.code != 200 || resp.data == null) {
            error("登录失败: ${resp.message}")
        }
        tokenStore.saveToken(resp.data.token)
        resp.data.token
    }

    suspend fun userInfo(): Result<UserInfo> = runCatching {
        val info = api.userInfo()
        if (info.disabled) error("账号已停用")
        info
    }

    suspend fun list(path: String = "/") = runCatching {
        val resp = api.list(FsListRequest(path = path))
        if (resp.code != 200) error("列表失败: ${resp.message}")
        resp.data?.content ?: emptyList()
    }

    suspend fun get(path: String) = runCatching {
        val resp = api.get(FsListRequest(path = path))
        if (resp.code != 200) error("获取失败: ${resp.message}")
        resp.data
    }

    fun isLoggedIn(): Boolean = tokenStore.tokenSync().isNotEmpty()
}
