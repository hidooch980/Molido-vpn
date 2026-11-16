package com.molido.vpn.ping

import com.molido.vpn.data.FreeServerProfile
import com.molido.vpn.engine.amnezia.AmneziaWgConfigParser
import com.molido.vpn.engine.hysteria.Hysteria2ConfigParser
import com.molido.vpn.engine.wireguard.WireGuardConfigParser
import com.molido.vpn.engine.xray.VlessConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val serverId: String,
    val latencyMillis: Long,
    val isReachable: Boolean,
    val endpoint: String
)

object BatchPingTester {

    suspend fun pingAll(
        servers: List<FreeServerProfile>,
        timeoutMs: Int = 1500
    ): Map<String, PingResult> = withContext(Dispatchers.IO) {
        val deferredResults = servers.map { server ->
            async {
                val endpoint = extractEndpoint(server.engineId, server.rawPayload)
                val result = pingEndpoint(server.id, endpoint, timeoutMs)
                server.id to result
            }
        }
        deferredResults.awaitAll().toMap()
    }

    fun pingEndpoint(
        serverId: String,
        endpoint: Pair<String, Int>,
        timeoutMs: Int = 1500
    ): PingResult {
        val (host, port) = endpoint
        val start = System.currentTimeMillis()
        var reachable = false
        var latency = -1L

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                reachable = true
            }
        } catch (_: Exception) {
            reachable = false
            latency = -1L
        }

        return PingResult(
            serverId = serverId,
            latencyMillis = latency,
            isReachable = reachable,
            endpoint = "$host:$port"
        )
    }

    fun extractEndpoint(engineId: String, rawPayload: String): Pair<String, Int> {
        return try {
            when (engineId) {
                "warp", "wireguard", "amnezia" -> {
                    val match = Regex("""Endpoint\s*=\s*([^:\s]+):(\d+)""", RegexOption.IGNORE_CASE).find(rawPayload)
                    if (match != null) {
                        Pair(match.groupValues[1], match.groupValues[2].toInt())
                    } else if (engineId == "warp") {
                        Pair("162.159.192.1", 2408)
                    } else {
                        Pair("1.1.1.1", 443)
                    }
                }
                "xray" -> {
                    val config = VlessConfigParser.parse(rawPayload)
                    Pair(config.server, config.port)
                }
                "hysteria2" -> {
                    val config = Hysteria2ConfigParser.parse(rawPayload)
                    Pair(config.server, config.port)
                }
                else -> Pair("1.1.1.1", 443)
            }
        } catch (_: Exception) {
            Pair("1.1.1.1", 443)
        }
    }
}
