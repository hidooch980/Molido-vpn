package com.molido.vpn.engine.hysteria

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import java.util.concurrent.atomic.AtomicBoolean

class Hysteria2EngineAdapter : EngineAdapter {
    override val id: String = "hysteria2"
    override val displayName: String = "Hysteria 2 (UDP / QUIC Brutal)"

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
            val uri = String(configuration.opaquePayload, Charsets.UTF_8)
            Hysteria2ConfigParser.validate(uri)
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to validate Hysteria 2 URI: ${e.message}")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val uri = String(configuration.opaquePayload, Charsets.UTF_8)
        val config = Hysteria2ConfigParser.parse(uri)

        val session = Hysteria2Session(id, config, onEvent)
        onEvent(EngineEvent.TransportReady)
        return session
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is Hysteria2Session) {
            session.terminate()
        }
    }

    class Hysteria2Session(
        override val engineId: String,
        val config: Hysteria2Config,
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
