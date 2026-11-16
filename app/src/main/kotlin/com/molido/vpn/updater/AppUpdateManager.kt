package com.molido.vpn.updater

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: String
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: AppUpdateInfo) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

class AppUpdateManager(
    private val context: Context,
    private val repository: String = "hidooch980/Molido-vpn"
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/$repository/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "MolidoVPN-Android-Updater")
            }
            val json = try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Release service unavailable (${connection.responseCode})"
                }
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }

            val tag = json.optString("tag_name")
            val versionCode = tag.removePrefix("v").substringBefore("-").toLongOrNull()
                ?: json.optLong("target_commitish", -1L)
            require(versionCode > 0) { "Release tag must start with a numeric version code, e.g. v12" }

            val asset = json.getJSONArray("assets").let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == "app-release.apk" }
            } ?: return@withContext UpdateCheckResult.Failed("Release APK asset is missing")

            val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
            require(digest.matches(Regex("[a-f0-9]{64}"))) {
                "Release APK must publish its SHA-256 digest in GitHub asset metadata"
            }

            val installedVersion = installedVersionCode()
            if (versionCode <= installedVersion) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.Available(
                    AppUpdateInfo(
                        versionName = tag,
                        versionCode = versionCode,
                        downloadUrl = asset.getString("browser_download_url"),
                        sha256 = digest,
                        releaseNotes = json.optString("body").take(4000)
                    )
                )
            }
        } catch (error: Exception) {
            UpdateCheckResult.Failed(error.message ?: "UPDATE_CHECK_FAILED")
        }
    }

    suspend fun downloadAndVerify(update: AppUpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val destination = File(context.cacheDir, "updates/molido-${update.versionCode}.apk")
            destination.parentFile?.mkdirs()
            val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MolidoVPN-Android-Updater")
            }
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "APK download failed (${connection.responseCode})"
                }
                connection.inputStream.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            val actualHash = destination.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            check(actualHash == update.sha256) {
                destination.delete()
                "APK_SHA256_MISMATCH"
            }
            verifyInstalledSigningIdentity(destination, update.versionCode)
            destination
        }
    }

    fun requestInstall(apk: File): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            throw InstallPermissionRequired()
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installedVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun verifyInstalledSigningIdentity(apk: File, expectedVersionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val downloaded = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Downloaded APK is not a valid Android package")
        check(downloaded.packageName == context.packageName) { "APK_PACKAGE_ID_MISMATCH" }
        val downloadedVersionCode = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else @Suppress("DEPRECATION") downloaded.versionCode.toLong()
        check(downloadedVersionCode == expectedVersionCode) { "APK_VERSION_CODE_MISMATCH" }

        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = if (Build.VERSION.SDK_INT >= 28) {
            installed.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") installed.signatures
        } ?: error("Installed signing certificate unavailable")
        val downloadedSigners = if (Build.VERSION.SDK_INT >= 28) {
            downloaded.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") downloaded.signatures
        } ?: error("Downloaded signing certificate unavailable")

        val installedDigests = installedSigners.map { sha256(it.toByteArray()) }.toSet()
        val downloadedDigests = downloadedSigners.map { sha256(it.toByteArray()) }.toSet()
        check(installedDigests == downloadedDigests) { "APK_SIGNING_CERTIFICATE_MISMATCH" }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    class InstallPermissionRequired : Exception("ALLOW_INSTALL_FROM_THIS_SOURCE")
}
