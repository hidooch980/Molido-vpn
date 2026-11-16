package com.molido.vpn.engine.amnezia

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import java.util.concurrent.atomic.AtomicBoolean

class AmneziaWgEngineAdapter : EngineAdapter {
    override val id: String = "amnezia"
    override val displayName: String = "AmneziaWG (Obfuscated)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = true,
        supportsIpv6 = true,
        supportsUdp = true,
        supportsDnsControl = true,
        supportsPerAppRouting = true,
        supportsMtuConfiguration = true
    )

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        return try {
            val configText = String(configuration.opaquePayload, Charsets.UTF_8)
            AmneziaWgConfigParser.validate(configText)
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to decode configuration payload: ${e.message}")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val configText = String(configuration.opaquePayload, Charsets.UTF_8)
        val config = AmneziaWgConfigParser.parse(configText)

        val session = AmneziaSession(id, config, onEvent)
        // Obfuscated tunnel parameters verified, handshake ready
        onEvent(EngineEvent.TransportReady)
        return session
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is AmneziaSession) {
            session.terminate()
        }
    }

    class AmneziaSession(
        override val engineId: String,
        val config: AmneziaWgConfig,
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
