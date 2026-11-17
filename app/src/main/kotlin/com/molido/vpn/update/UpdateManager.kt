package com.molido.vpn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
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

    const val CURRENT_VERSION = "0.2.0"
    const val GITHUB_REPO_OWNER = "hidooch980"
    const val GITHUB_REPO_NAME = "Molido-vpn"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val RELEASES_HTML_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    suspend fun checkUpdate(currentVersion: String = CURRENT_VERSION): UpdateInfo = withContext(Dispatchers.IO) {
        val url = URL(RELEASES_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "MolidoVPN/$currentVersion")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            // No releases published yet
            return@withContext UpdateInfo(
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                hasUpdate = false,
                releaseNotes = "No newer release found.",
                downloadUrl = null
            )
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("Failed to check updates from GitHub (HTTP $responseCode)")
        }

        val jsonString = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val json = JSONObject(jsonString)

        val tagName = json.optString("tag_name", "").trim()
        val latestVer = tagName.removePrefix("v").removePrefix("V")
        val body = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", RELEASES_HTML_URL)

        // Find direct APK download URL from assets if present
        var apkDownloadUrl: String? = null
        val assetsArray = json.optJSONArray("assets")
        if (assetsArray != null && assetsArray.length() > 0) {
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
        }

        val finalDownloadUrl = apkDownloadUrl ?: htmlUrl
        val hasUpdate = isNewerVersion(latestVer, currentVersion)

        UpdateInfo(
            latestVersion = latestVer,
            currentVersion = currentVersion,
            hasUpdate = hasUpdate,
            releaseNotes = body,
            downloadUrl = finalDownloadUrl,
            publishedAt = publishedAt
        )
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        val latestParts = latest.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openDownloadPage(context: Context, downloadUrl: String?) {
        val targetUrl = downloadUrl ?: RELEASES_HTML_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
