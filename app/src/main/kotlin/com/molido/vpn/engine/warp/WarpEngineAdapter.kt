package com.molido.vpn.engine.warp

import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.wireguard.WireGuardConfig

class WarpEngineAdapter : EngineAdapter {
    override val id: String = "warp"
    override val displayName: String = "Cloudflare WARP (Free)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsIpv4 = false,
        supportsIpv6 = false,
        supportsUdp = false,
        supportsDnsControl = false,
        supportsPerAppRouting = false,
        supportsMtuConfiguration = false
    )

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        return ValidationResult.Valid
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
