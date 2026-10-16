package com.datbox.app.data.update

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


data class UpdateHttpResponse(val statusCode: Int, val body: String)

typealias UpdateFetch = suspend (url: String, headers: Map<String, String>) -> UpdateHttpResponse

data class UpdateConfig(
    val owner: String,
    val repo: String,
    val proxyPrefixes: List<String> = listOf("https://gh.llkk.cc/", "https://gh-proxy.com/", ""),
    val useMetaFallback: Boolean = false,
    val metaBranch: String = "meta",
)

enum class OpenReleaseResult { OPENED_APP, OPENED_BROWSER, COPIED }

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
        "User-Agent" to "openlist-android",
        "Accept" to "application/vnd.github+json",
    )

    val apiLatestUrl: String
        get() = "https://api.github.com/repos/${config.owner}/${config.repo}/releases/latest"
    val releaseTagUrl: String
        get() = "https://github.com/${config.owner}/${config.repo}/releases/tag"

    suspend fun check(
        fetch: UpdateFetch,
        currentVersion: String,
        channel: String = "stable",
    ): GitHubUpdateResult? {
        val failures = mutableListOf<String>()
        val base = apiLatestUrl

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

        if (config.useMetaFallback) {
            val metaUrl =
                "https://cdn.jsdelivr.net/gh/${config.owner}/${config.repo}@${config.metaBranch}/version.json"
            for (prefix in config.proxyPrefixes) {
                val url = if (prefix.isEmpty()) metaUrl else "$prefix$metaUrl"
                try {
                    val resp = fetch(url, apiHeaders)
                    if (resp.statusCode != 200) continue
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
            }
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return OpenReleaseResult.OPENED_BROWSER
        } catch (_: Exception) {
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("GitHub Release", releaseUrl))
        Toast.makeText(context, "无法打开，链接已复制", Toast.LENGTH_LONG).show()
        return OpenReleaseResult.COPIED
    }


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

    private fun versionCodeFromTag(tag: String): Int {
        val segs = normalizeVersion(tag).split('.')
        for (i in segs.lastIndex downTo 0) {
            segs[i].trim().toIntOrNull()?.let { return it }
        }
        return 0
    }
}
