package com.molido.vpn.engine.xray

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import java.util.concurrent.atomic.AtomicBoolean

class XrayEngineAdapter : EngineAdapter {
    override val id: String = "xray"
    override val displayName: String = "Xray-core (VLESS / Trojan / SS)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = true,
        supportsIpv6 = true,
        supportsUdp = true,
        supportsDnsControl = true,
        supportsPerAppRouting = true,
        supportsMtuConfiguration = false
    )

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

        val session = XraySession(id, config, onEvent)
        // Transport handshake ready
        onEvent(EngineEvent.TransportReady)
        return session
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is XraySession) {
            session.terminate()
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
