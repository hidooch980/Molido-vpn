package com.molido.vpn.engine.xray

import android.content.Context
import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class XrayEngineAdapter(
    private val appContext: Context? = null
) : EngineAdapter {
    override val id: String = "xray"
    override val displayName: String = "Xray"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = true,
        supportsIpv6 = true,
        supportsUdp = true,
        supportsDnsControl = true,
        supportsPerAppRouting = true,
        supportsMtuConfiguration = false
    )

    companion object {
        private const val SOCKS_PORT = 10808
        private const val HTTP_PORT = 10809
        private const val TCP_PROBE_TIMEOUT_MS = 5000
        private const val CONFIG_FILENAME = "xray_config.json"
    }

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        return try {
            val uri = String(configuration.opaquePayload, Charsets.UTF_8)
            VlessConfigParser.validate(uri)
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to validate Xray URI: ${e.message}")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val uri = String(configuration.opaquePayload, Charsets.UTF_8)
        val config = VlessConfigParser.parse(uri)

        // 1. Generate Xray JSON config from the parsed URI
        val xrayJson = buildXrayJsonConfig(config)

        // 2. Write config to app internal storage
        writeConfigToStorage(xrayJson)

        // 3. TCP probe to verify the server is reachable before declaring ready
        val probeSuccess = withContext(Dispatchers.IO) {
            tcpProbe(config.server, config.port)
        }

        val session = XraySession(id, config, onEvent)

        if (probeSuccess) {
            // Server is reachable — in production the native libv2ray would
            // start a local SOCKS5 proxy here. For now we signal TransportReady.
            onEvent(EngineEvent.TransportReady)
        } else {
            session.terminate()
            onEvent(EngineEvent.Failed("TCP probe to ${config.server}:${config.port} failed — server unreachable", false))
        }

        return session
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is XraySession) {
            session.terminate()
        }
    }

    /**
     * Build an Xray-core compatible JSON config from the parsed VLESS/Trojan/SS URI.
     */
    private fun buildXrayJsonConfig(config: VlessConfig): String {
        val json = JSONObject()

        // ── Inbounds (local SOCKS5 + HTTP proxy) ──
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            })
        }

        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", HTTP_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        }

        json.put("inbounds", JSONArray().apply {
            put(socksInbound)
            put(httpInbound)
        })

        // ── Outbounds ──
        val outbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", config.protocol)

            val serverObj = JSONObject().apply {
                put("address", config.server)
                put("port", config.port)

                when (config.protocol) {
                    "vless" -> {
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", config.id)
                                put("encryption", "none")
                                config.flow?.let { put("flow", it) }
                            })
                        })
                    }
                    "trojan" -> {
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("password", config.id)
                            })
                        })
                    }
                    "ss" -> {
                        // Shadowsocks credentials are "method:password"
                        val parts = config.id.split(":", limit = 2)
                        if (parts.size == 2) {
                            put("method", parts[0])
                            put("password", parts[1])
                        }
                    }
                }
            }

            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply { put(serverObj) })
            })

            // ── Stream settings ──
            val streamSettings = JSONObject().apply {
                put("network", config.transport)

                when (config.security) {
                    "tls" -> {
                        put("security", "tls")
                        put("tlsSettings", JSONObject().apply {
                            config.sni?.let { put("serverName", it) }
                            put("allowInsecure", false)
                        })
                    }
                    "reality" -> {
                        put("security", "reality")
                        put("realitySettings", JSONObject().apply {
                            config.sni?.let { put("serverName", it) }
                            config.publicKey?.let { put("publicKey", it) }
                            config.shortId?.let { put("shortId", it) }
                            put("fingerprint", "chrome")
                        })
                    }
                    else -> put("security", "none")
                }

                // Transport-specific settings
                when (config.transport) {
                    "ws" -> {
                        put("wsSettings", JSONObject().apply {
                            config.path?.let { put("path", it) }
                            config.sni?.let { host ->
                                put("headers", JSONObject().apply {
                                    put("Host", host)
                                })
                            }
                        })
                    }
                    "grpc" -> {
                        put("grpcSettings", JSONObject().apply {
                            config.path?.let { put("serviceName", it) }
                        })
                    }
                }
            }

            put("streamSettings", streamSettings)
        }

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        json.put("outbounds", JSONArray().apply {
            put(outbound)
            put(directOutbound)
            put(blockOutbound)
        })

        // ── DNS ──
        json.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("1.1.1.1")
                put("8.8.8.8")
            })
        })

        return json.toString(2)
    }

    /**
     * Write the generated Xray JSON config to the app's internal storage.
     */
    private fun writeConfigToStorage(jsonConfig: String) {
        val ctx = appContext ?: return
        val configFile = File(ctx.filesDir, CONFIG_FILENAME)
        configFile.writeText(jsonConfig, Charsets.UTF_8)
    }

    /**
     * Attempt a TCP connection to the server to verify reachability.
     * Returns true if a TCP handshake completes within the timeout.
     */
    private fun tcpProbe(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_PROBE_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    class XraySession(
        override val engineId: String,
        val config: VlessConfig,
        private val onEvent: (EngineEvent) -> Unit
    ) : EngineSession {
        private val active = AtomicBoolean(true)

        fun updateStats(bytesIn: Long, bytesOut: Long) {
            if (active.get()) {
                onEvent(EngineEvent.Statistics(bytesIn, bytesOut))
            }
        }

        fun terminate() {
            active.set(false)
        }

        val isActive: Boolean get() = active.get()
    }
}
