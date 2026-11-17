package com.molido.vpn.engine.hysteria

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult

class Hysteria2EngineAdapter : EngineAdapter {
    override val id: String = "hysteria2"
    override val displayName: String = "Hysteria 2 (UDP / QUIC Brutal)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = false,
        supportsIpv6 = false,
        supportsUdp = false,
        supportsDnsControl = false,
        supportsPerAppRouting = false,
        supportsMtuConfiguration = false
    )

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        return try {
            val uri = String(configuration.opaquePayload, Charsets.UTF_8)
            Hysteria2ConfigParser.validate(uri)
        } catch (e: Exception) {
            ValidationResult.Invalid("HYSTERIA2_CONFIG_INVALID")
        }
    }

    override suspend fun connect(
        @Suppress("UNUSED_PARAMETER") configuration: EngineConfiguration,
        @Suppress("UNUSED_PARAMETER") protectSocket: (Int) -> Boolean,
        @Suppress("UNUSED_PARAMETER") onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        // Fail closed until a real native transport, protected socket handling,
        // TUN packet forwarding, and device acceptance tests are integrated.
        throw UnsupportedOperationException("ENGINE_RUNTIME_NOT_IMPLEMENTED")
    }

    override suspend fun disconnect(@Suppress("UNUSED_PARAMETER") session: EngineSession) = Unit
}
