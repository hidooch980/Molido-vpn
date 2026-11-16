package com.molido.vpn.engine.warp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class CleanIpResult(
    val ip: String,
    val port: Int,
    val latencyMillis: Long,
    val isClean: Boolean
)

object CleanIpScanner {

    // Known Cloudflare Anycast IP candidates optimized for low-latency routing
    val CANDIDATE_IPS = listOf(
        "162.159.192.1",
        "162.159.192.5",
        "162.159.192.10",
        "162.159.192.25",
        "162.159.193.1",
        "162.159.193.10",
        "162.159.193.50",
        "162.159.195.1",
        "162.159.195.20",
        "188.114.96.1",
        "188.114.96.22",
        "188.114.97.1",
        "188.114.97.15",
        "188.114.98.1",
        "188.114.99.1",
        "198.41.200.1"
    )

    const val DEFAULT_WARP_PORT = 2408

    suspend fun scanCleanIps(
        targetIps: List<String> = CANDIDATE_IPS,
        port: Int = DEFAULT_WARP_PORT,
        timeoutMs: Int = 1200
    ): List<CleanIpResult> = withContext(Dispatchers.IO) {
        val jobs = targetIps.map { ip ->
            async {
                testCandidate(ip, port, timeoutMs)
            }
        }
        val results = jobs.awaitAll()
        results.filter { it.isClean }.sortedBy { it.latencyMillis }
    }

    fun testCandidate(ip: String, port: Int, timeoutMs: Int): CleanIpResult {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                CleanIpResult(ip, port, latency, isClean = true)
            }
        } catch (_: Exception) {
            CleanIpResult(ip, port, -1L, isClean = false)
        }
    }

    /**
     * Replaces or updates the Endpoint in a WARP WireGuard profile with the new Clean IP.
     */
    fun applyCleanIpToWarpConfig(rawConfig: String, cleanIp: String, port: Int = DEFAULT_WARP_PORT): String {
        val endpointRegex = Regex("""Endpoint\s*=\s*[^:\s]+:\d+""")
        val newEndpointLine = "Endpoint = $cleanIp:$port"
        return if (endpointRegex.containsMatchIn(rawConfig)) {
            endpointRegex.replace(rawConfig, newEndpointLine)
        } else {
            // Append under [Peer]
            rawConfig + "\n" + newEndpointLine
        }
    }
}
