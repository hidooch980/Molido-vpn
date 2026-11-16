package com.molido.vpn.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class VerificationResult(
    val success: Boolean,
    val latencyMillis: Long? = null,
    val egressIp: String? = null,
    val egressCity: String? = null,
    val egressRegion: String? = null,
    val egressCountry: String? = null,
    val errorMessage: String? = null
)

interface TrafficVerifier {
    suspend fun verify(timeoutMillis: Long = 3000L): VerificationResult
}

/**
 * The HTTPS request is made by the app after WireGuard is active. The endpoint
 * therefore observes the VPN egress address. Location is approximate IP
 * geolocation, not GPS or a precise physical location.
 *
 * The provider receives the public VPN egress IP to answer this lookup; this
 * data flow is disclosed in docs/privacy-data-flows.md.
 */
class NetworkTrafficVerifier(
    private val endpoint: String = "https://ipwho.is/"
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
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            check(connection.responseCode == HttpsURLConnection.HTTP_OK) {
                "Egress verification endpoint returned HTTP ${connection.responseCode}"
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(payload)
            check(json.optBoolean("success", false)) { "Egress geolocation lookup failed" }

            val address = json.optString("ip").trim()
            require(address.length in 3..45 && address.matches(Regex("[0-9a-fA-F:.]+"))) {
                "Egress endpoint did not return a literal IP address"
            }
            val parsed = InetAddress.getByName(address)
            VerificationResult(
                success = true,
                latencyMillis = System.currentTimeMillis() - startedAt,
                egressIp = parsed.hostAddress,
                egressCity = json.optString("city").takeIf(String::isNotBlank),
                egressRegion = json.optString("region").takeIf(String::isNotBlank),
                egressCountry = json.optString("country").takeIf(String::isNotBlank)
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
