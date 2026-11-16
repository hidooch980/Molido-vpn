package com.molido.vpn.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class VerificationResult(
    val success: Boolean,
    val latencyMillis: Long? = null,
    val egressIp: String? = null,
    val errorMessage: String? = null
)

interface TrafficVerifier {
    suspend fun verify(timeoutMillis: Long = 3000L): VerificationResult
}

/**
 * Verifies HTTPS reachability and reads the public egress address returned by
 * the remote endpoint. A destination server address is never mistaken for the
 * client's public IP. The caller must invoke this only after tunnel startup.
 */
class NetworkTrafficVerifier(
    private val endpoint: String = "https://api.ipify.org"
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as? HttpsURLConnection)
                ?: return@withContext VerificationResult(false, errorMessage = "HTTPS_REQUIRED")
            connection.connectTimeout = timeoutMillis.toInt().coerceAtLeast(1)
            connection.readTimeout = timeoutMillis.toInt().coerceAtLeast(1)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/plain")
            connection.instanceFollowRedirects = false
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext VerificationResult(
                    success = false,
                    errorMessage = "EGRESS_HTTP_${connection.responseCode}"
                )
            }

            val body = connection.inputStream.bufferedReader(Charsets.US_ASCII).use { it.readText() }.trim()
            if (!isIpLiteral(body)) {
                return@withContext VerificationResult(false, errorMessage = "INVALID_EGRESS_IP")
            }

            VerificationResult(
                success = true,
                latencyMillis = System.currentTimeMillis() - startedAt,
                egressIp = InetAddress.getByName(body).hostAddress
            )
        } catch (e: Exception) {
            VerificationResult(false, errorMessage = e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() }) return false
        if (value.contains(':')) return value.matches(Regex("[0-9a-fA-F:.%]+"))
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}

class MockTrafficVerifier(
    var simulatedSuccess: Boolean = true,
    var simulatedLatency: Long = 45L,
    var simulatedError: String? = null
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult {
        return if (simulatedSuccess) {
            VerificationResult(success = true, latencyMillis = simulatedLatency, egressIp = "203.0.113.10")
        } else {
            VerificationResult(success = false, errorMessage = simulatedError ?: "Probe failed")
        }
    }
}
