package com.molido.vpn.engine

/**
 * Contract for a VPN transport implementation.
 *
 * Implementations must never log credentials, private keys, or full connection
 * configuration. The Android VpnService remains the sole TUN owner.
 */
interface EngineAdapter {
    val id: String
    val displayName: String
    val capabilities: EngineCapabilities

    suspend fun validate(configuration: EngineConfiguration): ValidationResult
    suspend fun connect(
        configuration: EngineConfiguration,
        protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession

    suspend fun disconnect(session: EngineSession)
}

data class EngineCapabilities(
    val supportsIpv4: Boolean,
    val supportsIpv6: Boolean,
    val supportsUdp: Boolean,
    val supportsDnsControl: Boolean,
    val supportsPerAppRouting: Boolean,
    val supportsMtuConfiguration: Boolean
)

data class EngineConfiguration(
    val opaquePayload: ByteArray,
    val allowIpv4: Boolean = true,
    val allowIpv6: Boolean = true,
    /** Resolver IPs to install in the WireGuard interface; empty preserves profile DNS. */
    val dnsServers: List<String> = emptyList()
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasonCode: String) : ValidationResult
}

sealed interface EngineEvent {
    data object TransportReady : EngineEvent
    data class Failed(val reasonCode: String, val recoverable: Boolean) : EngineEvent
    data class Statistics(val bytesIn: Long, val bytesOut: Long) : EngineEvent
}

interface EngineSession {
    val engineId: String
}
