package com.molido.vpn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val hasUpdate: Boolean,
    val releaseNotes: String,
    val downloadUrl: String?,
    val publishedAt: String? = null
)

object UpdateManager {

    const val CURRENT_VERSION = "0.3.0"
    const val GITHUB_REPO_OWNER = "hidooch980"
    const val GITHUB_REPO_NAME = "Molido-vpn"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val RELEASES_HTML_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

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
            return@withContext UpdateInfo(
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                hasUpdate = false,
                releaseNotes = "No newer release found.",
                downloadUrl = null
            )
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("GitHub release check failed with HTTP $responseCode")
        }

        val jsonText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val json = JSONObject(jsonText)

        val tagName = json.optString("tag_name", "").removePrefix("v").trim()
        val latestVer = if (tagName.isNotBlank()) tagName else currentVersion
        val body = json.optString("body", "")
        val htmlUrl = json.optString("html_url", RELEASES_HTML_URL)
        val publishedAt = json.optString("published_at", null)

        var apkDownloadUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
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

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!downloadUrl.endsWith(".apk", ignoreCase = true) && !downloadUrl.contains("/download/")) {
                openDownloadPage(context, downloadUrl)
                return@withContext Result.success(Unit)
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MolidoVPN/$CURRENT_VERSION")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw java.io.IOException("Download failed with HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw java.io.IOException("Empty response body")
            val totalBytes = responseBody.contentLength()

            val outputFile = File(context.cacheDir, "MolidoVPN-update.apk")
            if (outputFile.exists()) outputFile.delete()

            responseBody.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied: Long = 0
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            // Launch PackageInstaller via FileProvider
            withContext(Dispatchers.Main) {
                installApkFile(context, outputFile)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApkFile(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    fun openDownloadPage(context: Context, downloadUrl: String?) {
        val targetUrl = downloadUrl ?: RELEASES_HTML_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
