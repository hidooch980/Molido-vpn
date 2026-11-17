package com.molido.vpn.verifier

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

data class AddressFamilyProbe(
    val family: String,
    val reachable: Boolean,
    val publicAddress: String? = null,
    val errorCode: String? = null
)

data class DnsResolverObservation(
    val evidenceAvailable: Boolean,
    val resolverAddresses: List<String>,
    val errorCode: String? = null
)

/**
 * Independent diagnostics bound to Android's active VPN Network.
 * DNS observation triggers a unique system-resolver lookup and asks the
 * authoritative bash.ws test endpoint which recursive resolvers queried it.
 * Empty observations are inconclusive, never a clean-leak verdict.
 */
class IndependentLeakTestRunner(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun checkIpv4AndIpv6(): List<AddressFamilyProbe> = withContext(Dispatchers.IO) {
        val network = requireVpnNetwork()
        listOf(
            probeFamily(network, "IPv4", "https://api4.ipify.org?format=text", 4),
            probeFamily(network, "IPv6", "https://api6.ipify.org?format=text", 16)
        )
    }

    suspend fun observeSystemDnsResolvers(): DnsResolverObservation = withContext(Dispatchers.IO) {
        val network = try {
            requireVpnNetwork()
        } catch (error: Exception) {
            return@withContext DnsResolverObservation(false, emptyList(), error.message ?: "VPN_NETWORK_NOT_ACTIVE")
        }
        val nonce = buildString(32) {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            bytes.forEach { append("%02x".format(it)) }
        }
        val probeHost = "$nonce.bash.ws"

        // Deliberately use Android's system resolver: this tests the OS DNS path,
        // not the separate app-scoped DoH client.
        try {
            InetAddress.getAllByName(probeHost)
        } catch (_: Exception) {
            // NXDOMAIN or a filtered answer can still leave an authoritative query record.
        }

        repeat(5) { attempt ->
            if (connectivity.activeNetwork != network) {
                return@withContext DnsResolverObservation(false, emptyList(), "VPN_NETWORK_CHANGED")
            }
            val result = fetchResolverObservation(network, nonce)
            if (result.isNotEmpty()) {
                return@withContext DnsResolverObservation(true, result.distinct())
            }
            if (attempt < 4) delay(600)
        }
        DnsResolverObservation(false, emptyList(), "DNS_OBSERVATION_INCONCLUSIVE")
    }

    private fun requireVpnNetwork(): Network {
        val network = connectivity.activeNetwork ?: throw IllegalStateException("VPN_NETWORK_UNAVAILABLE")
        val caps = connectivity.getNetworkCapabilities(network)
        check(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            "VPN_NETWORK_NOT_ACTIVE"
        }
        return network
    }

    private fun probeFamily(
        network: Network,
        family: String,
        endpoint: String,
        expectedBytes: Int
    ): AddressFamilyProbe {
        var connection: HttpsURLConnection? = null
        return try {
            connection = network.openConnection(URL(endpoint)) as? HttpsURLConnection
                ?: throw IllegalStateException("HTTPS_REQUIRED")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            check(connection.responseCode == HttpsURLConnection.HTTP_OK) { "EGRESS_HTTP_ERROR" }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(32)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 64) { "EGRESS_RESPONSE_TOO_LARGE" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.US_ASCII.name()).trim()
            }
            val address = PublicEgressIpValidator.parse(body)
                ?: throw IllegalArgumentException("EGRESS_IP_INVALID")
            check(address.address.size == expectedBytes) { "EGRESS_ADDRESS_FAMILY_MISMATCH" }
            AddressFamilyProbe(family, true, address.hostAddress)
        } catch (error: Exception) {
            AddressFamilyProbe(family, false, errorCode = error.message?.take(64) ?: "EGRESS_PROBE_FAILED")
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchResolverObservation(network: Network, nonce: String): List<String> {
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL("https://bash.ws/dnsleak/test/$nonce?json")
            connection = network.openConnection(url) as? HttpsURLConnection
                ?: return emptyList()
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            check(connection.responseCode == HttpsURLConnection.HTTP_OK) { "DNS_OBSERVATION_HTTP_ERROR" }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 16 * 1024) { "DNS_OBSERVATION_TOO_LARGE" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.optString("type") != "dns") continue
                    val ip = item.optString("ip")
                    if (isIpLiteral(ip)) add(ip)
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank() || value.length > 45 || value.any { it.isWhitespace() }) return false
        val ipv4 = value.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) &&
            value.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        if (ipv4) return true
        if (!value.contains(':') || !value.matches(Regex("[0-9a-fA-F:.]+"))) return false
        return runCatching { InetAddress.getByName(value).address.size == 16 }.getOrDefault(false)
    }
}
