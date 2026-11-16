package com.molido.vpn.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Verifies public egress through HTTPS. A successful TCP connection to a
 * public DNS resolver is not proof of VPN egress and must never be displayed
 * as the user's public IP address.
 */
class NetworkTrafficVerifier(
    private val egressUrl: String = "https://api.ipify.org"
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(egressUrl).openConnection() as HttpsURLConnection).apply {
                connectTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                readTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
            }
            require(connection.responseCode == HttpsURLConnection.HTTP_OK) {
                "PUBLIC_EGRESS_HTTP_" + connection.responseCode
            }
            val ip = connection.inputStream.bufferedReader(Charsets.US_ASCII).use { it.readText().trim() }
            require(isIpLiteral(ip)) { "PUBLIC_EGRESS_INVALID_IP_RESPONSE" }
            VerificationResult(
                success = true,
                latencyMillis = (System.nanoTime() - startedAt) / 1_000_000L,
                egressIp = ip
            )
        } catch (_: Exception) {
            VerificationResult(success = false, errorMessage = "PUBLIC_EGRESS_VERIFICATION_FAILED")
        } finally {
            connection?.disconnect()
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank() || value.length > 45 || value.any { !(it.isDigit() || it in ".:") }) return false
        return try {
            val address = java.net.InetAddress.getByName(value)
            address.hostAddress == value || address.hostAddress.equals(value, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}

/** Test-only verifier. Never wire this into production dependency injection. */
class MockTrafficVerifier(
    var simulatedSuccess: Boolean = true,
    var simulatedLatency: Long = 45L,
    var simulatedError: String? = null
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult =
        if (simulatedSuccess) {
            VerificationResult(success = true, latencyMillis = simulatedLatency, egressIp = "203.0.113.1")
        } else {
            VerificationResult(success = false, errorMessage = simulatedError ?: "Probe failed")
        }
}
