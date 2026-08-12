package com.threel.openlist.data.update

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 统一更新检查引擎（Kotlin 版）—— 对齐 sanyelive / FeiNiuMusic / synapse 共用的
 * app_update_core.dart 行为。
 *
 * 解决的真实问题（与其一一对应）：
 *  1) 国内 / 移动宽带直连 api.github.com 被墙 → 代理链 [gh-llkk.cc → gh-proxy.com → 直连]
 *     逐层尝试，任一层 403 / 超时 / 拿到 HTML 都静默跳下一层，不会因代理抽风整体失败。
 *  2) 只比 tag_name vs PackageInfo.versionName，不依赖 APK 文件名格式。
 *  3) 所有数据源都不可达时 check() 返回 null，调用方必须如实报"检查失败"——不谎报"已是最新"。
 *  4) 跳转发布页：GitHub App（com.github.android）→ 系统浏览器 → 复制链接。
 *  5) release 正文首非空行含 **P0** / **critical** → isCritical，UI 走强制更新。
 *
 * 本仓无 meta 分支，useMetaFallback 默认 false（避免白白多一次超时）。
 */

/** HTTP 取数适配器的响应：状态码 + 原始 body。解析交给引擎。 */
data class UpdateHttpResponse(val statusCode: Int, val body: String)

/** HTTP 取数适配器。网络异常直接 throw，引擎会捕获并继续尝试下一条路径。 */
typealias UpdateFetch = suspend (url: String, headers: Map<String, String>) -> UpdateHttpResponse

data class UpdateConfig(
    val owner: String,
    val repo: String,
    // 代理前缀链：gh-llkk.cc（老板实测可用）优先，gh-proxy.com（统一引擎默认）兜底，空串=直连。
    val proxyPrefixes: List<String> = listOf("https://gh.llkk.cc/", "https://gh-proxy.com/", ""),
    val useMetaFallback: Boolean = false,
    val metaBranch: String = "meta",
)

/** openRelease 的结果，供调用方决定是否提示"已复制链接"。 */
enum class OpenReleaseResult { OPENED_APP, OPENED_BROWSER, COPIED }

/** 检查结果。 */
data class GitHubUpdateResult(
    val tagName: String,
    val latestVersion: String,
    val releaseUrl: String,
    val hasUpdate: Boolean,
    val releaseName: String = tagName,
    val releaseNotes: String? = null,
    val isCritical: Boolean = false,
    val source: String = "github",
    val versionCode: Int = 0,
    val apkAssetName: String? = null,
    val apkDownloadUrl: String? = null,
)

private val GITHUB_JSON = Json { ignoreUnknownKeys = true }

/** GitHub Release JSON（只取我们需要的字段）。 */
@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

class GitHubUpdateEngine(private val config: UpdateConfig) {

    private val apiHeaders = mapOf(
        // 缺 User-Agent 时 GitHub API 直接 403。
        "User-Agent" to "openlist-android",
        "Accept" to "application/vnd.github+json",
    )

    val apiLatestUrl: String
        get() = "https://api.github.com/repos/${config.owner}/${config.repo}/releases/latest"
    val releaseTagUrl: String
        get() = "https://github.com/${config.owner}/${config.repo}/releases/tag"

    /**
     * 检查更新。返回 null = 所有数据源都失败（调用方应提示"网络不可达"，不要误报"已是最新"）。
     * [channel] 暂只用 'stable'（本仓单一发布渠道，latest release 即正式版）。
     */
    suspend fun check(
        fetch: UpdateFetch,
        currentVersion: String,
        channel: String = "stable",
    ): GitHubUpdateResult? {
        val failures = mutableListOf<String>()
        val base = apiLatestUrl

        // ── 1) GitHub API：代理链（gh-llkk.cc 优先，直连兜底）──
        for (prefix in config.proxyPrefixes) {
            val url = if (prefix.isEmpty()) base else "$prefix$base"
            try {
                val resp = fetch(url, apiHeaders)
                if (resp.statusCode != 200) {
                    failures.add("api $url → HTTP ${resp.statusCode}")
                    continue
                }
                val release = runCatching { GITHUB_JSON.decodeFromString<GhRelease>(resp.body) }
                    .getOrNull()
                if (release == null || release.tag_name.isEmpty()) {
                    failures.add("api $url → 解析失败 / 非 JSON")
                    continue
                }
                return toResult(release, currentVersion)
            } catch (e: Exception) {
                failures.add("api $url → ${e.message}")
            }
        }

        // ── 2) jsDelivr meta 兜底（本仓无 meta 分支，默认关闭）──
        if (config.useMetaFallback) {
            val metaUrl =
                "https://cdn.jsdelivr.net/gh/${config.owner}/${config.repo}@${config.metaBranch}/version.json"
            for (prefix in config.proxyPrefixes) {
                val url = if (prefix.isEmpty()) metaUrl else "$prefix$metaUrl"
                try {
                    val resp = fetch(url, apiHeaders)
                    if (resp.statusCode != 200) continue
                    // meta 分支未实现，简单跳过解析。
                    failures.add("meta $url → 未启用")
                } catch (e: Exception) {
                    failures.add("meta $url → ${e.message}")
                }
            }
        }

        android.util.Log.w("GitHubUpdate", "全部数据源失败\n${failures.joinToString("\n")}")
        return null
    }

    private fun toResult(release: GhRelease, currentVersion: String): GitHubUpdateResult {
        val tagName = release.tag_name.trim()
        val newer = compareVersions(tagName, currentVersion) > 0
        // 在 /d 路由里挑 arm64-v8a 优先的 apk（仅作展示 / 诊断，主流程走发布页）。
        var apkName: String? = null
        var apkUrl: String? = null
        for (a in release.assets) {
            if (!a.name.endsWith(".apk")) continue
            apkName = a.name
            apkUrl = a.browser_download_url
            if (a.name.contains("arm64-v8a")) break
        }
        val body = release.body ?: ""
        return GitHubUpdateResult(
            tagName = tagName,
            latestVersion = stripV(tagName),
            releaseName = release.name?.takeIf { it.isNotBlank() } ?: tagName,
            releaseUrl = "$releaseTagUrl/$tagName",
            releaseNotes = body.takeIf { it.isNotBlank() },
            isCritical = isCritical(body),
            hasUpdate = newer,
            source = "github",
            versionCode = versionCodeFromTag(tagName),
            apkAssetName = apkName,
            apkDownloadUrl = apkUrl,
        )
    }

    /**
     * 跳转发布页：GitHub App 优先 → 系统浏览器 → 复制链接。
     * 返回结果供调用方决定是否提示"已复制"。
     */
    fun openRelease(context: Context, releaseUrl: String): OpenReleaseResult {
        val uri = Uri.parse(releaseUrl)
        val githubPkg = "com.github.android"
        val hasGitHubApp = try {
            context.packageManager.getPackageInfo(githubPkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (hasGitHubApp) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(githubPkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return OpenReleaseResult.OPENED_APP
            } catch (_: Exception) {
                // 未安装 / 拉不起，继续回退浏览器
            }
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return OpenReleaseResult.OPENED_BROWSER
        } catch (_: Exception) {
            // 没有任何 App 能处理 https，复制到剪贴板
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("GitHub Release", releaseUrl))
        Toast.makeText(context, "无法打开，链接已复制", Toast.LENGTH_LONG).show()
        return OpenReleaseResult.COPIED
    }

    // ────────────────────────────────────────────────────────────
    // 纯函数（版本比较 / 解析）—— 与统一引擎语义一致
    // ────────────────────────────────────────────────────────────

    /**
     * 版本比较。>0 = a 比 b 新, 0 = 相同, <0 = a 更旧。
     * 规则：去前导 v/V；剩余 '+'/'-' 视为分隔符截断；按 '.' 切数字段逐位比。
     */
    fun compareVersions(a: String, b: String): Int {
        fun release(v: String): List<Int> {
            var s = normalizeVersion(v)
            s = s.replace('+', '.')
            val cut = s.indexOfFirst { it == '+' || it == '-' }
            if (cut >= 0) s = s.substring(0, cut)
            return s.split('.').map { it.trim().toIntOrNull() ?: 0 }
        }
        val left = release(a)
        val right = release(b)
        val len = maxOf(left.size, right.size)
        for (i in 0 until len) {
            val av = left.getOrElse(i) { 0 }
            val bv = right.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    /** release 正文首非空行含 "**P0**" / "**critical**"（大小写不敏感）→ 重要更新。 */
    fun isCritical(body: String): Boolean {
        val firstLine = body.split('\n').map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: ""
        val lower = firstLine.lowercase()
        return lower.contains("**p0**") || lower.contains("**critical**")
    }

    private fun normalizeVersion(version: String): String {
        val value = version.trim()
        return if (value.startsWith('v') || value.startsWith('V')) value.substring(1) else value
    }

    private fun stripV(tag: String): String = normalizeVersion(tag)

    /** 从 tag（如 "v0.3.12.184"）取末段数字作为 versionCode；仅供展示 / 诊断。 */
    private fun versionCodeFromTag(tag: String): Int {
        val segs = normalizeVersion(tag).split('.')
        for (i in segs.lastIndex downTo 0) {
            segs[i].trim().toIntOrNull()?.let { return it }
        }
        return 0
    }
}
