package com.molido.vpn.engine.wireguard

import android.content.Context
import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import java.io.StringReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WireGuard userspace integration. GoBackend owns and establishes the only TUN
 * interface and protects its native UDP sockets through its own VpnService.
 * MolidoVpnService must not establish a second interface.
 *
 * A successful backend activation is followed by the coordinator's external
 * HTTPS egress verifier before the UI can report a verified connection.
 */
class WireGuardEngineAdapter(
    private val context: Context? = null
) : EngineAdapter {
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

    private val backend: GoBackend by lazy {
        GoBackend(requireNotNull(context) { "ANDROID_CONTEXT_REQUIRED" })
    }

    override suspend fun validate(configuration: EngineConfiguration): ValidationResult {
        val text = try {
            String(configuration.opaquePayload, Charsets.UTF_8)
        } catch (_: Exception) {
            return ValidationResult.Invalid("CONFIG_ENCODING_INVALID")
        }
        if (configuration.opaquePayload.isEmpty()) {
            return ValidationResult.Invalid("CONFIG_EMPTY")
        }
        if (configuration.opaquePayload.size > 64 * 1024) {
            return ValidationResult.Invalid("CONFIG_TOO_LARGE")
        }
        return try {
            val effectiveText = WireGuardTunnelPolicy.applyDnsServers(text, configuration.dnsServers)
            WireGuardTunnelPolicy.validateFullTunnel(effectiveText)?.let {
                return ValidationResult.Invalid(it)
            }
            // The upstream parser supports the complete WireGuard format,
            // including multiple peer sections; the legacy local parser does not.
            Config.parse(StringReader(effectiveText).buffered())
            ValidationResult.Valid
        } catch (error: Exception) {
            ValidationResult.Invalid(error.message ?: "WIREGUARD_UPSTREAM_CONFIG_REJECTED")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        @Suppress("UNUSED_PARAMETER") protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val contextReady = context
        if (contextReady == null) {
            throw IllegalStateException("ANDROID_CONTEXT_REQUIRED")
        }

        require(configuration.opaquePayload.isNotEmpty()) { "CONFIG_EMPTY" }
        require(configuration.opaquePayload.size <= 64 * 1024) { "CONFIG_TOO_LARGE" }
        val configText = String(configuration.opaquePayload, Charsets.UTF_8)
        val effectiveText = WireGuardTunnelPolicy.applyDnsServers(configText, configuration.dnsServers)
        val routeError = WireGuardTunnelPolicy.validateFullTunnel(effectiveText)
        require(routeError == null) { routeError ?: "WIREGUARD_FULL_TUNNEL_REQUIRED" }
        val parsed = try {
            Config.parse(StringReader(effectiveText).buffered())
        } catch (_: Exception) {
            throw IllegalArgumentException("WIREGUARD_UPSTREAM_CONFIG_REJECTED")
        }

        val backendActive = AtomicBoolean(false)
        val tunnel = object : Tunnel {
            override fun getName(): String = "MolidoVPN-WireGuard"
            override fun onStateChange(newState: State) {
                backendActive.set(newState == State.UP)
            }
        }
        val session = WireGuardSession(tunnel, backendActive)
        try {
            withContext(Dispatchers.IO) {
                backend.setState(tunnel, State.UP, parsed)
            }
            check(backend.getState(tunnel) == State.UP) { "WIREGUARD_BACKEND_NOT_UP" }
            backendActive.set(true)
            onEvent(EngineEvent.TransportReady)
            return session
        } catch (error: LinkageError) {
            safelyStopTunnel(tunnel)
            throw IllegalStateException("WIREGUARD_NATIVE_LIBRARY_UNAVAILABLE", error)
        } catch (error: Exception) {
            safelyStopTunnel(tunnel)
            throw IllegalStateException("WIREGUARD_START_FAILED", error)
        }
    }

    private suspend fun safelyStopTunnel(tunnel: Tunnel) {
        try {
            withContext(Dispatchers.IO) {
                if (backend.getState(tunnel) == State.UP) {
                    backend.setState(tunnel, State.DOWN, null)
                }
            }
        } catch (_: Throwable) {
            // Startup already failed; never expose native exception details.
        }
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session !is WireGuardSession || !session.beginClose()) return
        try {
            withContext(Dispatchers.IO) {
                if (backend.getState(session.tunnel) == State.UP) {
                    backend.setState(session.tunnel, State.DOWN, null)
                }
                check(backend.getState(session.tunnel) == State.DOWN) { "WIREGUARD_BACKEND_STILL_UP" }
            }
        } catch (error: Throwable) {
            // Allow the coordinator's retained session to retry a failed backend teardown.
            session.allowCloseRetry()
            throw error
        }
    }

    inner class WireGuardSession internal constructor(
        internal val tunnel: Tunnel,
        private val backendActive: AtomicBoolean
    ) : EngineSession {
        override val engineId: String = id
        private val closing = AtomicBoolean(false)

        internal fun beginClose(): Boolean = closing.compareAndSet(false, true)
        internal fun allowCloseRetry() {
            closing.compareAndSet(true, false)
        }
        val isActive: Boolean get() = backendActive.get() && !closing.get()
    }
}
