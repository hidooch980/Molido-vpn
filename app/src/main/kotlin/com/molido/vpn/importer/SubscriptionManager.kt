package com.molido.vpn.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class ImportedConfig(
    val engineId: String,
    val name: String,
    val payload: String
)

object ConfigImporter {

    fun detectAndParse(input: String): ImportedConfig {
        val trimmed = input.trim()

        if (trimmed.startsWith("vless://", ignoreCase = true) ||
            trimmed.startsWith("trojan://", ignoreCase = true) ||
            trimmed.startsWith("ss://", ignoreCase = true)
        ) {
            val name = extractRemark(trimmed) ?: "Imported Xray Server"
            return ImportedConfig(engineId = "xray", name = name, payload = trimmed)
        }

        if (trimmed.contains("[Interface]", ignoreCase = true)) {
            val isAmnezia = trimmed.contains("Jc", ignoreCase = true) ||
                trimmed.contains("Jmin", ignoreCase = true) ||
                trimmed.contains("H1", ignoreCase = true)

            val engine = if (isAmnezia) "amnezia" else "wireguard"
            val name = if (isAmnezia) "Imported AmneziaWG Server" else "Imported WireGuard Server"
            return ImportedConfig(engineId = engine, name = name, payload = trimmed)
        }

        throw IllegalArgumentException("Unrecognized configuration format. Please provide a WireGuard, Amnezia, VLESS, Trojan, or SS profile.")
    }

    private fun extractRemark(uriStr: String): String? {
        val hash = uriStr.indexOf('#')
        if (hash >= 0 && hash < uriStr.length - 1) {
            return try {
                java.net.URLDecoder.decode(uriStr.substring(hash + 1), "UTF-8")
            } catch (_: Exception) {
                uriStr.substring(hash + 1)
            }
        }
        return null
    }
}

object SubscriptionManager {

    suspend fun fetchSubscription(subscriptionUrl: String): List<ImportedConfig> = withContext(Dispatchers.IO) {
        val url = URL(subscriptionUrl.trim())
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "MolidoVPN/0.1.0")

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("Subscription fetch failed with HTTP response $responseCode")
        }

        val rawContent = connection.inputStream.bufferedReader().use(BufferedReader::readText)

        // Decode Base64 subscription body if base64 encoded
        val decodedLines = try {
            val cleaned = rawContent.replace("\n", "").replace("\r", "").trim()
            val decoded = String(Base64.getDecoder().decode(cleaned), Charsets.UTF_8)
            decoded.lines()
        } catch (_: Exception) {
            rawContent.lines()
        }

        val results = mutableListOf<ImportedConfig>()
        for (line in decodedLines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            try {
                results.add(ConfigImporter.detectAndParse(trimmed))
            } catch (_: Exception) {
                // Skip invalid lines
            }
        }
        results
    }

    suspend fun fetchGithubCommunitySubscriptions(): List<ImportedConfig> = withContext(Dispatchers.IO) {
        val allConfigs = mutableListOf<ImportedConfig>()
        for (url in com.molido.vpn.data.FreeServerCatalog.GITHUB_COMMUNITY_SUBSCRIPTIONS) {
            try {
                val fetched = fetchSubscription(url)
                allConfigs.addAll(fetched)
                if (allConfigs.size >= 25) break // Cap at a balanced pool
            } catch (_: Exception) {
                // Continue to next mirror if one repo is blocked or rate-limited
            }
        }
        allConfigs
    }
}
