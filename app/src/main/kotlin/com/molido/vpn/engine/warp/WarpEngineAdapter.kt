package com.molido.vpn.engine.warp

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.wireguard.WireGuardConfig
import java.util.concurrent.atomic.AtomicBoolean

class WarpEngineAdapter : EngineAdapter {
    override val id: String = "warp"
    override val displayName: String = "Cloudflare WARP (Free)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = true,
        supportsIpv6 = true,
        supportsUdp = true,
        supportsDnsControl = true,
        supportsPerAppRouting = true,
        supportsMtuConfiguration = true
    )

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        return ValidationResult.Valid
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val configText = try {
            String(configuration.opaquePayload, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }

        val config = if (configText != null && configText.contains("[Interface]")) {
            try {
                com.molido.vpn.engine.wireguard.WireGuardConfigParser.parse(configText)
            } catch (_: Exception) {
                WarpProvisioner.generateFreeProfile()
            }
        } else {
            WarpProvisioner.generateFreeProfile()
        }

        val session = WarpSession(id, config, onEvent)
        onEvent(EngineEvent.TransportReady)
        return session
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is WarpSession) {
            session.terminate()
        }
    }

    class WarpSession(
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
