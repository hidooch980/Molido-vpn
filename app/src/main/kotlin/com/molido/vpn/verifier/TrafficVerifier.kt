package com.molido.vpn.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Uses an HTTPS IP-echo endpoint. Because the app's sockets are not protected/excluded,
 * Android routes this request through the active VPN. The returned value is the observed
 * public egress address, never the probe server's address.
 */
class NetworkTrafficVerifier(
    private val endpoint: String = "https://api.ipify.org"
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
                connectTimeout = timeoutMillis.coerceIn(500L, 15_000L).toInt()
                readTimeout = timeoutMillis.coerceIn(500L, 15_000L).toInt()
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/plain")
                setRequestProperty("Cache-Control", "no-cache")
            }
            check(connection.responseCode == HttpsURLConnection.HTTP_OK) {
                "Egress verification endpoint returned HTTP ${connection.responseCode}"
            }
            val address = connection.inputStream.bufferedReader(Charsets.US_ASCII).use {
                it.readText().trim()
            }
            require(address.length in 3..45 && address.matches(Regex("[0-9a-fA-F:.]+"))) {
                "Egress endpoint did not return a literal IP address"
            }
            val parsed = InetAddress.getByName(address)
            VerificationResult(
                success = true,
                latencyMillis = System.currentTimeMillis() - startedAt,
                egressIp = parsed.hostAddress
            )
        } catch (_: Exception) {
            VerificationResult(
                success = false,
                errorMessage = "PUBLIC_EGRESS_VERIFICATION_FAILED"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
