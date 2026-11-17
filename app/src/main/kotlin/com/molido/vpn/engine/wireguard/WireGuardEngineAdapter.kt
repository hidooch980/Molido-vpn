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
        val localValidation = WireGuardConfigParser.validate(text)
        if (localValidation is ValidationResult.Invalid) return localValidation
        return try {
            Config.parse(StringReader(text))
            ValidationResult.Valid
        } catch (_: Exception) {
            ValidationResult.Invalid("WIREGUARD_UPSTREAM_CONFIG_REJECTED")
        }
    }

    override suspend fun connect(
        configuration: EngineConfiguration,
        @Suppress("UNUSED_PARAMETER") protectSocket: (Int) -> Boolean,
        onEvent: (EngineEvent) -> Unit
    ): EngineSession {
        val contextReady = context
        if (contextReady == null) {
            onEvent(EngineEvent.Failed("ANDROID_CONTEXT_REQUIRED", recoverable = false))
            throw IllegalStateException("ANDROID_CONTEXT_REQUIRED")
        }

        val configText = String(configuration.opaquePayload, Charsets.UTF_8)
        val parsed = try {
            Config.parse(StringReader(configText))
        } catch (_: Exception) {
            onEvent(EngineEvent.Failed("WIREGUARD_UPSTREAM_CONFIG_REJECTED", recoverable = false))
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
            onEvent(EngineEvent.Failed("WIREGUARD_NATIVE_LIBRARY_UNAVAILABLE", recoverable = false))
            throw IllegalStateException("WIREGUARD_NATIVE_LIBRARY_UNAVAILABLE", error)
        } catch (error: Exception) {
            onEvent(EngineEvent.Failed("WIREGUARD_BACKEND_START_FAILED", recoverable = true))
            throw error
        }
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session !is WireGuardSession || !session.beginClose()) return
        withContext(Dispatchers.IO) {
            if (backend.getState(session.tunnel) == State.UP) {
                backend.setState(session.tunnel, State.DOWN, null)
            }
        }
    }

    inner class WireGuardSession internal constructor(
        internal val tunnel: Tunnel,
        private val backendActive: AtomicBoolean
    ) : EngineSession {
        override val engineId: String = id
        private val closing = AtomicBoolean(false)

        internal fun beginClose(): Boolean = closing.compareAndSet(false, true)
        val isActive: Boolean get() = backendActive.get() && !closing.get()
    }
}
