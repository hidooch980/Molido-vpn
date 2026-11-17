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
    val publicAddresses: List<String> = emptyList(),
    val conclusion: String? = null,
    val errorCode: String? = null,
    /** The DNS API used for the unique lookup; null means the probe never started. */
    val lookupPath: String? = null,
    val lookupAttempts: Int = 0
)

/**
 * Calls Android's process-default system resolver (InetAddress), not Network.getAllByName().
 * The caller verifies that the process default network is the active VPN before and after
 * the batch. This observes the app process resolver path; it is not by itself proof that
 * every other app's DNS is intercepted by the VPN.
 */
internal object SystemDnsLookup {
    const val LOOKUP_PATH = "ANDROID_PROCESS_DEFAULT_SYSTEM_RESOLVER"

    fun resolve(hostname: String): Array<InetAddress> = InetAddress.getAllByName(hostname)
}

/**
 * Independent diagnostics bound to Android's active VPN Network.
 * HTTPS evidence collection uses the VPN Network explicitly. Unique DNS names are resolved
 * via Android's process-default system resolver so this test is distinct from app-owned DoH.
 * Empty authoritative observations are inconclusive, never a clean-leak verdict.
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
            return@withContext DnsResolverObservation(
                evidenceAvailable = false,
                resolverAddresses = emptyList(),
                errorCode = error.message ?: "VPN_NETWORK_NOT_ACTIVE"
            )
        }
        val testId = fetchText(network, URL("https://bash.ws/id"), 128)
            ?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z0-9-]{1,64}")) }
            ?: return@withContext DnsResolverObservation(false, emptyList(), errorCode = "DNS_TEST_ID_UNAVAILABLE")

        fetchText(network, URL("https://ipv4.bash.ws/myip/test/$testId"), 128)
        fetchText(network, URL("https://ipv6.bash.ws/myip/test/$testId"), 128)

        var lookupAttempts = 0
        for (index in 1..10) {
            if (!isSameActiveVpnNetwork(network)) {
                return@withContext DnsResolverObservation(
                    false, emptyList(), errorCode = "VPN_NETWORK_CHANGED",
                    lookupPath = SystemDnsLookup.LOOKUP_PATH, lookupAttempts = lookupAttempts
                )
            }
            lookupAttempts++
            try {
                // Deliberately use InetAddress (Android process-default resolver), not
                // network.getAllByName(): this exercises the system resolver API path.
                SystemDnsLookup.resolve("$index.$testId.bash.ws")
            } catch (_: Exception) {
                // NXDOMAIN/filtering can still be visible in the authoritative resolver log.
            }
            if (!isSameActiveVpnNetwork(network)) {
                return@withContext DnsResolverObservation(
                    false, emptyList(), errorCode = "VPN_NETWORK_CHANGED",
                    lookupPath = SystemDnsLookup.LOOKUP_PATH, lookupAttempts = lookupAttempts
                )
            }
        }

        repeat(5) { attempt ->
            if (!isSameActiveVpnNetwork(network)) {
                return@withContext DnsResolverObservation(
                    false, emptyList(), errorCode = "VPN_NETWORK_CHANGED",
                    lookupPath = SystemDnsLookup.LOOKUP_PATH, lookupAttempts = lookupAttempts
                )
            }
            val result = fetchResolverObservation(network, testId)
            if (result.evidenceAvailable) {
                return@withContext result.copy(
                    lookupPath = SystemDnsLookup.LOOKUP_PATH,
                    lookupAttempts = lookupAttempts
                )
            }
            if (attempt < 4) delay(600)
        }
        DnsResolverObservation(
            false, emptyList(), errorCode = "DNS_OBSERVATION_INCONCLUSIVE",
            lookupPath = SystemDnsLookup.LOOKUP_PATH, lookupAttempts = lookupAttempts
        )
    }

    private fun requireVpnNetwork(): Network {
        val network = connectivity.activeNetwork ?: throw IllegalStateException("VPN_NETWORK_UNAVAILABLE")
        val caps = connectivity.getNetworkCapabilities(network)
        check(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            "VPN_NETWORK_NOT_ACTIVE"
        }
        return network
    }

    private fun isSameActiveVpnNetwork(network: Network): Boolean {
        if (connectivity.activeNetwork != network) return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun probeFamily(
        network: Network,
        family: String,
        endpoint: String,
        expectedBytes: Int
    ): AddressFamilyProbe {
        var connection: HttpsURLConnection? = null
        return try {
            check(isSameActiveVpnNetwork(network)) { "VPN_NETWORK_CHANGED" }
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
            check(isSameActiveVpnNetwork(network)) { "VPN_NETWORK_CHANGED" }
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

    private fun fetchResolverObservation(network: Network, testId: String): DnsResolverObservation {
        val body = fetchText(
            network,
            URL("https://bash.ws/dnsleak/test/$testId?json=2"),
            16 * 1024
        ) ?: return DnsResolverObservation(false, emptyList(), errorCode = "DNS_OBSERVATION_HTTP_ERROR")
        return try {
            val array = JSONArray(body)
            val resolvers = mutableListOf<String>()
            val publicAddresses = mutableListOf<String>()
            var conclusion: String? = null
            var serviceError: String? = null
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                when (item.optString("type")) {
                    "dns" -> item.optString("ip").takeIf(::isIpLiteral)?.let(resolvers::add)
                    "ip" -> item.optString("ip").takeIf(::isIpLiteral)?.let(publicAddresses::add)
                    "conclusion" -> conclusion = item.optString("text").takeIf { it.isNotBlank() }
                    "error" -> serviceError = item.optString("text").takeIf { it.isNotBlank() }
                }
            }
            if (serviceError != null) {
                DnsResolverObservation(false, resolvers.distinct(), publicAddresses.distinct(), conclusion, serviceError)
            } else {
                DnsResolverObservation(
                    evidenceAvailable = resolvers.isNotEmpty(),
                    resolverAddresses = resolvers.distinct(),
                    publicAddresses = publicAddresses.distinct(),
                    conclusion = conclusion,
                    errorCode = if (resolvers.isEmpty()) "DNS_OBSERVATION_INCONCLUSIVE" else null
                )
            }
        } catch (_: Exception) {
            DnsResolverObservation(false, emptyList(), errorCode = "DNS_OBSERVATION_RESPONSE_INVALID")
        }
    }

    private fun fetchText(network: Network, url: URL, maxBytes: Int): String? {
        var connection: HttpsURLConnection? = null
        return try {
            connection = network.openConnection(url) as? HttpsURLConnection ?: return null
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) return null
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            null
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
