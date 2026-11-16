package com.molido.vpn.engine.wireguard

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import java.util.concurrent.atomic.AtomicBoolean

class WireGuardEngineAdapter : EngineAdapter {
    override val id: String = "wireguard"
    override val displayName: String = "WireGuard"

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
            WireGuardConfigParser.validate(configText)
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to decode configuration payload: ${e.message}")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        // Fail closed until the official WireGuard GoBackend is integrated as
        // the sole TUN owner and real socket protection/packet forwarding exist.
        val validation = validate(configuration)
        if (validation is ValidationResult.Invalid) {
            onEvent(EngineEvent.Failed(validation.reasonCode, recoverable = false))
            throw IllegalArgumentException(validation.reasonCode)
        }
        onEvent(EngineEvent.Failed("WIREGUARD_BACKEND_NOT_INTEGRATED", recoverable = false))
        throw UnsupportedOperationException("WIREGUARD_BACKEND_NOT_INTEGRATED")
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is WireGuardSession) {
            session.terminate()
        }
    }

    class WireGuardSession(
        override val engineId: String,
        val config: WireGuardConfig,
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
