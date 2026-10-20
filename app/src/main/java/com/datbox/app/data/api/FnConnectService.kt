package com.datbox.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FnConRequest(val fnId: String)

/** 5ddd 返回的端口 (fnOS 自己的网关端口, 这里仅作兜底默认值) */
@Serializable
data class FnPort(
    val httpPort: Int = 5666,
    val httpsPort: Int = 5667,
)

/** POST https://5ddd.com/api/v1/fn/con 的 data */
@Serializable
data class FnConData(
    /** 内网 IPv4 */
    val ipv4: List<String> = emptyList(),
    val publicIpv4: List<String> = emptyList(),
    val publicIpv6: List<String> = emptyList(),
    /** 中继域名, 形如 xxx.5ddd.com:443 (当前未使用) */
    val fn: List<String> = emptyList(),
    val port: FnPort = FnPort(),
)

@Serializable
private data class FnConResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: FnConData? = null,
)

enum class FnLinkGroup(val title: String) {
    INTERNAL("内网"),
    PUBLIC_V6("公网 IPv6"),
    PUBLIC_V4("公网 IPv4"),
}

data class FnCandidate(
    val group: FnLinkGroup,
    val url: String,
    val ok: Boolean = false,
    val ms: Long? = null,
    val error: String? = null,
)

/**
 * 飞牛 FN Connect：FNID -> NAS 的内网/公网地址列表。
 *
 * 只做地址发现和探测，不碰中继 —— 中继终点是 fnOS 网关 (443)，
 * 到不了 OpenList 自己的端口。
 *
 * 接口签名: sign = md5(PREFIX + path + nonce + timestamp + md5(body) + apiKey)
 */
@Singleton
class FnConnectService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** FNID 换成候选地址。 */
    suspend fun resolve(fnId: String): Result<FnConData> = withContext(Dispatchers.IO) {
        runCatching {
            val body = OpenListJson.encodeToString(FnConRequest(fnId.trim()))
            val nonce = (100000..999999).random().toString()
            val timestamp = System.currentTimeMillis().toString()
            val sign = md5("$AUTHX_PREFIX$FN_PATH$nonce$timestamp${md5(body)}$API_KEY")

            val request = Request.Builder()
                .url("https://5ddd.com$FN_PATH")
                .addHeader("authx", "nonce=$nonce&timestamp=$timestamp&sign=$sign")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("FNID 查询 HTTP ${resp.code}")
                val text = resp.body?.string().orEmpty()
                val parsed = OpenListJson.decodeFromString(FnConResponse.serializer(), text)
                if (parsed.code != 0 || parsed.data == null) {
                    error(parsed.msg.ifBlank { "FNID 查询失败 (code=${parsed.code})" })
                }
                parsed.data
            }
        }
    }

    /**
     * 探测 OpenList 在每条候选链路上是否可用。
     * 探测端点用无需登录的 /api/public/settings。
     */
    suspend fun probe(
        fnId: String,
        openListPort: Int,
        pathPrefix: String = "",
    ): Result<List<FnCandidate>> = resolve(fnId).mapCatching { params ->
        val prefix = pathPrefix.trim().trimEnd('/').let {
            when {
                it.isEmpty() -> ""
                it.startsWith("/") -> it
                else -> "/$it"
            }
        }
        val specs = buildCandidates(params, openListPort, prefix)
        coroutineScope {
            specs.map { spec -> async(Dispatchers.IO) { probeOne(spec) } }.awaitAll()
        }.sortedWith(compareBy({ it.group.ordinal }, { !it.ok }, { it.ms ?: Long.MAX_VALUE }))
    }

    private fun buildCandidates(params: FnConData, port: Int, prefix: String): List<FnCandidate> {
        val out = mutableListOf<FnCandidate>()
        fun add(group: FnLinkGroup, rawHost: String) {
            val host = if (rawHost.contains(':') && !rawHost.startsWith("[")) "[$rawHost]" else rawHost
            // HTTP 优先：IP 上直接开 HTTPS 多半是自签证书，校验证书必然失败
            out += FnCandidate(group, "http://$host:$port$prefix")
            out += FnCandidate(group, "https://$host:$port$prefix")
        }
        params.ipv4.distinct().forEach { add(FnLinkGroup.INTERNAL, it) }
        params.publicIpv6.distinct().forEach { add(FnLinkGroup.PUBLIC_V6, it) }
        params.publicIpv4.distinct().forEach { add(FnLinkGroup.PUBLIC_V4, it) }
        return out
    }

    private fun probeOne(candidate: FnCandidate): FnCandidate {
        val startedAt = System.nanoTime()
        return try {
            val request = Request.Builder()
                .url("${candidate.url}/api/public/settings")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                val text = resp.body?.string().orEmpty()
                val code = runCatching {
                    OpenListJson.parseToJsonElement(text).jsonObject["code"]?.jsonPrimitive?.intOrNull
                }.getOrNull()
                if (resp.isSuccessful && code == 200) {
                    candidate.copy(ok = true, ms = elapsed)
                } else {
                    candidate.copy(ms = elapsed, error = "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            candidate.copy(error = e.message?.take(60) ?: e::class.java.simpleName)
        }
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private companion object {
        const val FN_PATH = "/api/v1/fn/con"
        const val AUTHX_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
        const val API_KEY = "zIGtkc3dqZnJpd29qZXJqa2w7c"
    }
}
