package com.molido.vpn.verifier

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
 * Verifies HTTPS reachability over Android's active VPN Network and accepts
 * only a bounded, globally routable egress-IP response. This is an egress
 * reachability check, not a complete DNS/IPv6 leak or kill-switch test.
 */
class NetworkTrafficVerifier(
    private val context: Context,
    private val endpoint: String = "https://api.ipify.org"
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var connection: HttpsURLConnection? = null
        try {
            val endpointUrl = URL(endpoint)
            require(endpointUrl.protocol.equals("https", ignoreCase = true)) { "HTTPS_REQUIRED" }
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivity.activeNetwork
                ?: return@withContext VerificationResult(false, errorMessage = "NO_ACTIVE_NETWORK")
            val capabilities = connectivity.getNetworkCapabilities(activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true) {
                return@withContext VerificationResult(false, errorMessage = "VPN_NETWORK_NOT_ACTIVE")
            }
            connection = (activeNetwork.openConnection(endpointUrl) as? HttpsURLConnection)
                ?: return@withContext VerificationResult(false, errorMessage = "HTTPS_REQUIRED")
            val boundedTimeout = timeoutMillis.coerceIn(1L, 30_000L).toInt()
            connection.connectTimeout = boundedTimeout
            connection.readTimeout = boundedTimeout
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

            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(32)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_EGRESS_BODY_BYTES) {
                        throw IllegalArgumentException("EGRESS_RESPONSE_TOO_LARGE")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.US_ASCII.name()).trim()
            }
            val address = parsePublicIpLiteral(body)
                ?: return@withContext VerificationResult(false, errorMessage = "INVALID_OR_NON_PUBLIC_EGRESS_IP")

            VerificationResult(
                success = true,
                latencyMillis = System.currentTimeMillis() - startedAt,
                egressIp = address.hostAddress
            )
        } catch (e: Exception) {
            VerificationResult(false, errorMessage = e.message?.take(80) ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parsePublicIpLiteral(value: String): InetAddress? {
        if (value.isBlank() || value.length > 45 || value.any { it.isWhitespace() }) return null
        val looksLikeIpv4 = value.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}"))
        val looksLikeIpv6 = value.contains(':') && value.matches(Regex("[0-9a-fA-F:.]+"))
        if (!looksLikeIpv4 && !looksLikeIpv6) return null
        val address = try { InetAddress.getByName(value) } catch (_: Exception) { return null }
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return null

        val bytes = address.address
        if (bytes.size == 16) {
            // Accept global-unicast 2000::/3 only; reject documentation and
            // benchmarking prefixes that platform InetAddress flags may miss.
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            val fourth = bytes[3].toInt() and 0xff
            if ((first and 0xe0) != 0x20) return null
            if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return null
            if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x02) return null
            return address
        }
        if (bytes.size != 4) return null
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        val isReserved = a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && (b == 0 || b == 2 || b == 168)) ||
            (a == 198 && (b in 18..19 || b == 51)) ||
            (a == 203 && b == 0) ||
            (a == 192 && b == 88 && c == 99)
        return if (isReserved) null else address
    }

    private companion object {
        const val MAX_EGRESS_BODY_BYTES = 64
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
