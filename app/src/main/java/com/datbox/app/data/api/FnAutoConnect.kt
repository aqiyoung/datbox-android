package com.datbox.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动时重新探测 FNID，把当前服务器换成最快的一条。
 *
 * 只在当前地址本身来自 FN Connect（fn_managed）时才动，
 * 避免覆盖用户手填的地址 / 反向代理域名。
 */
@Singleton
class FnAutoConnect @Inject constructor(
    private val tokenStore: TokenStore,
    private val fnConnect: FnConnectService,
) {
    suspend fun refreshIfNeeded(): String? = withContext(Dispatchers.IO) {
        val fnId = tokenStore.fnIdSync()
        if (fnId.isBlank() || !tokenStore.fnManagedSync()) return@withContext null
        val port = tokenStore.fnPortSync().toIntOrNull() ?: return@withContext null

        val best = fnConnect.probe(fnId, port).getOrNull()
            ?.firstOrNull { it.ok } ?: return@withContext null
        if (best.url == tokenStore.serverUrlSync()) return@withContext best.url

        tokenStore.addCustomServer(best.url)
        tokenStore.saveServerUrl(best.url)
        best.url
    }
}
