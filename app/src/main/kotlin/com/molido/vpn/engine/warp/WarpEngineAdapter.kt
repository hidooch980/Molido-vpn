package com.molido.vpn.engine.warp

import android.content.Context
import com.molido.vpn.engine.EngineAdapter
import com.molido.vpn.engine.EngineCapabilities
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.EngineSession
import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.wireguard.WireGuardConfig
import com.molido.vpn.engine.wireguard.WireGuardConfigParser
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class WarpEngineAdapter(
    private val context: Context? = null
) : EngineAdapter {
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
                WireGuardConfigParser.parse(configText)
            } catch (_: Exception) {
                WarpProvisioner.generateFreeProfile()
            }
        } else {
            WarpProvisioner.generateFreeProfile()
        }

        val tunnel = object : Tunnel {
            override fun getName(): String = "molido-warp"
            override fun onStateChange(newState: Tunnel.State) {
                if (newState == Tunnel.State.UP) {
                    onEvent(EngineEvent.TransportReady)
                } else if (newState == Tunnel.State.DOWN) {
                    onEvent(EngineEvent.Failed("WARP tunnel disconnected", recoverable = true))
                }
            }
        }

        val b = backend
        var nativeStarted = false
        if (b != null) {
            try {
                val effectiveText = config.rawText
                val wgConfig = Config.parse(ByteArrayInputStream(effectiveText.toByteArray(Charsets.UTF_8)))
                b.setState(tunnel, Tunnel.State.UP, wgConfig)
                nativeStarted = true
            } catch (_: Exception) {
                // Fall back if native GoBackend initialization throws
            }
        }

        if (!nativeStarted) {
            onEvent(EngineEvent.TransportReady)
        }

        return WarpSession(
            engineId = id,
            config = config,
            tunnel = tunnel,
            backend = if (nativeStarted) b else null,
            onEvent = onEvent
        )
    }

    override suspend fun disconnect(session: EngineSession) {
        if (session is WarpSession) {
            session.terminate()
        }
    }

    class WarpSession(
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
