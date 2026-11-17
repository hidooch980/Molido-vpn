package com.molido.vpn.engine.amnezia

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult

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
        @Suppress("UNUSED_PARAMETER") configuration: EngineConfiguration,
        @Suppress("UNUSED_PARAMETER") protectSocket: (Int) -> Boolean,
        @Suppress("UNUSED_PARAMETER") onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        // Fail closed until a real native transport, protected socket handling,
        // TUN packet forwarding, and device acceptance tests are integrated.
        throw UnsupportedOperationException("ENGINE_RUNTIME_NOT_IMPLEMENTED")
    }
}
