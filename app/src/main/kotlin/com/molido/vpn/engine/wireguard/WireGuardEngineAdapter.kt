package com.molido.vpn.engine.wireguard

import android.content.Context
import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production transport adapter backed by WireGuard's maintained Android tunnel library.
 * GoBackend owns the VPN service/TUN and protects its own UDP sockets.
 */
class WireGuardEngineAdapter(context: Context) : EngineAdapter {
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

    private val backend: Backend = GoBackend(context.applicationContext)
    private val backendLock = Any()

    private fun parse(configuration: EngineConfiguration): Config {
        require(configuration.opaquePayload.isNotEmpty()) { "WireGuard profile is empty" }
        return ByteArrayInputStream(configuration.opaquePayload).use(Config::parse).also { config ->
            require(config.peers.isNotEmpty()) { "WireGuard profile has no peers" }
            require(config.interface.addresses.isNotEmpty()) { "WireGuard profile has no interface address" }
        }
    }

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult =
        withContext(Dispatchers.IO) {
            try {
                parse(configuration)
                ValidationResult.Valid
            } catch (_: Exception) {
                // Do not echo parser details: they may contain sensitive configuration data.
                ValidationResult.Invalid("INVALID_WIREGUARD_PROFILE")
            }
        }

    override suspend fun connect(
        configuration: EngineConfiguration,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession = withContext(Dispatchers.IO) {
        val config = parse(configuration)
        val tunnel = MolidoTunnel()
        try {
            synchronized(backendLock) {
                val state = backend.setState(tunnel, Tunnel.State.UP, config)
                check(state == Tunnel.State.UP && backend.getState(tunnel) == Tunnel.State.UP) {
                    "WireGuard backend did not enter UP state"
                }
            }
            val session = WireGuardSession(tunnel)
            onEvent(EngineEvent.TransportReady)
            session
        } catch (error: Exception) {
            onEvent(EngineEvent.Failed("WIREGUARD_BACKEND_START_FAILED", recoverable = true))
            throw error
        }
    }

    override suspend fun disconnect(session: EngineSession) = withContext(Dispatchers.IO) {
        if (session !is WireGuardSession || !session.markStopped()) return@withContext
        synchronized(backendLock) {
            if (backend.getState(session.tunnel) == Tunnel.State.UP) {
                backend.setState(session.tunnel, Tunnel.State.DOWN, null)
            }
        }
    }

    private class MolidoTunnel : Tunnel {
        @Volatile private var state: Tunnel.State = Tunnel.State.DOWN

        override fun getName(): String = "molido"

        override fun onStateChange(newState: Tunnel.State) {
            state = newState
        }
    }

    private class WireGuardSession(val tunnel: MolidoTunnel) : EngineSession {
        override val engineId: String = "wireguard"
        private val stopped = AtomicBoolean(false)
        fun markStopped(): Boolean = stopped.compareAndSet(false, true)
    }
}
