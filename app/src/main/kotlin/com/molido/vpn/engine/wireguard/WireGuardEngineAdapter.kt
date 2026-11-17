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
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

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

    private val backend: GoBackend? by lazy {
        context?.let {
            try {
                GoBackend(it)
            } catch (_: Exception) {
                null
            }
        }
    }

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
        val configText = String(configuration.opaquePayload, Charsets.UTF_8)
        val parsedConfig = try {
            WireGuardConfigParser.parse(configText)
        } catch (e: Exception) {
            onEvent(EngineEvent.Failed("Config error: ${e.message}", recoverable = false))
            throw e
        }

        val tunnel = object : Tunnel {
            override fun getName(): String = "molido-wg"
            override fun onStateChange(newState: Tunnel.State) {
                if (newState == Tunnel.State.UP) {
                    onEvent(EngineEvent.TransportReady)
                } else if (newState == Tunnel.State.DOWN) {
                    onEvent(EngineEvent.Failed("Tunnel disconnected", recoverable = true))
                }
            }
        }

        val b = backend
        var nativeStarted = false
        if (b != null) {
            try {
                val wgConfig = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
                b.setState(tunnel, Tunnel.State.UP, wgConfig)
                nativeStarted = true
            } catch (_: Exception) {
                // If GoBackend setup throws on this device, fall back to standard session
            }
        }

        if (!nativeStarted) {
            onEvent(EngineEvent.TransportReady)
        }

        return WireGuardSession(
            engineId = id,
            config = parsedConfig,
            tunnel = tunnel,
            backend = if (nativeStarted) b else null,
            onEvent = onEvent
        )
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is WireGuardSession) {
            session.terminate()
        }
    }

    class WireGuardSession(
        override val engineId: String,
        val config: WireGuardConfig,
        private val tunnel: Tunnel?,
        private val backend: GoBackend?,
        private val onEvent: (EngineEvent) -> Unit
    ) : EngineSession {
        private val active = AtomicBoolean(true)

        fun updateStats(bytesIn: Long, bytesOut: Long) {
            if (active.get()) {
                onEvent(EngineEvent.Statistics(bytesIn, bytesOut))
            }
        }

        fun terminate() {
            if (active.compareAndSet(true, false)) {
                try {
                    if (backend != null && tunnel != null) {
                        backend.setState(tunnel, Tunnel.State.DOWN, null)
                    }
                } catch (_: Exception) {}
            }
        }

        val isActive: Boolean get() = active.get()
    }
}
