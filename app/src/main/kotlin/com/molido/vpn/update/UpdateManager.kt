package com.molido.vpn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.molido.vpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val hasUpdate: Boolean,
    val releaseNotes: String,
    val downloadUrl: String?,
    val publishedAt: String? = null
)

object UpdateManager {
    const val CURRENT_VERSION = "1.0.0"
    const val GITHUB_REPO_OWNER = "hidooch980"
    const val GITHUB_REPO_NAME = "Molido-vpn"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val RELEASES_HTML_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    private const val MAX_RELEASE_METADATA_BYTES = 512 * 1024

    suspend fun checkUpdate(currentVersion: String = BuildConfig.VERSION_NAME): UpdateInfo =
        withContext(Dispatchers.IO) {
            val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MolidoVPN/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext UpdateInfo(
                        latestVersion = currentVersion,
                        currentVersion = currentVersion,
                        hasUpdate = false,
                        releaseNotes = "No stable release found.",
                        downloadUrl = null
                    )
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Update check failed (HTTP $responseCode)")
                }
                val length = connection.getHeaderFieldLong("Content-Length", -1L)
                if (length > MAX_RELEASE_METADATA_BYTES) throw IOException("Release metadata is too large")
                val jsonString = connection.inputStream.use { input ->
                    val bytes = input.readNBytes(MAX_RELEASE_METADATA_BYTES + 1)
                    if (bytes.size > MAX_RELEASE_METADATA_BYTES) throw IOException("Release metadata is too large")
                    bytes.toString(Charsets.UTF_8)
                }
                val json = JSONObject(jsonString)
                if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) {
                    throw IOException("Latest GitHub release is not a stable published release")
                }

                val tagName = json.optString("tag_name", "").trim()
                val latestVersion = tagName.removePrefix("v").removePrefix("V")
                if (parseVersion(latestVersion) == null) throw IOException("Release tag has an invalid version")
                val notes = json.optString("body", "").take(20_000)
                val publishedAt = json.optString("published_at", "")
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name") == "MolidoVPN-v$latestVersion.apk") {
                            val candidate = asset.optString("browser_download_url", "")
                            val parsed = runCatching { URL(candidate) }.getOrNull()
                            if (parsed?.protocol == "https" && parsed.host.equals("github.com", true)) {
                                apkUrl = candidate
                            }
                            break
                        }
                    }
                }
                UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    hasUpdate = isNewerVersion(latestVersion, currentVersion),
                    releaseNotes = notes,
                    downloadUrl = apkUrl,
                    publishedAt = publishedAt
                )
            } finally {
                connection.disconnect()
            }
        }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = parseVersion(latest) ?: return false
        val currentParts = parseVersion(current) ?: return false
        for (i in 0..2) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return false
    }

    private fun parseVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        if (!normalized.matches(Regex("\\d+\\.\\d+\\.\\d+"))) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }

    fun openDownloadPage(context: Context, downloadUrl: String?) {
        val targetUrl = downloadUrl ?: RELEASES_HTML_URL
        val uri = Uri.parse(targetUrl)
        require(uri.scheme == "https" && uri.host == "github.com") { "Untrusted update URL" }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
