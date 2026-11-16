package com.molido.vpn.engine

/**
 * Truthful release gate for the six selected transport families.
 *
 * An adapter being present in source code is not evidence that it can move
 * packets. Selection is disabled until a real engine runtime is connected to
 * the app-owned TUN and passes traffic, leak, and lifecycle verification.
 */
enum class EngineReadiness {
    PLANNED,
    PROTOTYPE_ONLY,
    INTEGRATED_UNVERIFIED,
    DEVICE_VERIFIED,
    PRODUCTION
}

data class EngineSupport(
    val id: String,
    val displayName: String,
    val readiness: EngineReadiness,
    val selectable: Boolean,
    val releaseBlocker: String
)

object EngineRuntimeCatalog {
    val selectedEngines: List<EngineSupport> = listOf(
        EngineSupport(
            id = "amneziawg",
            displayName = "AmneziaWG",
            readiness = EngineReadiness.PROTOTYPE_ONLY,
            selectable = false,
            releaseBlocker = "A maintained Android runtime with Amnezia obfuscation and real TUN packet forwarding is not integrated."
        ),
        EngineSupport(
            id = "xray",
            displayName = "Xray-core",
            readiness = EngineReadiness.PROTOTYPE_ONLY,
            selectable = false,
            releaseBlocker = "The Xray native runtime, TUN packet bridge, socket protection, and lifecycle are not integrated."
        ),
        EngineSupport(
            id = "sing-box",
            displayName = "sing-box",
            readiness = EngineReadiness.PLANNED,
            selectable = false,
            releaseBlocker = "Android libbox integration, ABI packaging, license notices, and TUN ownership are pending."
        ),
        EngineSupport(
            id = "wireguard",
            displayName = "WireGuard",
            readiness = EngineReadiness.PROTOTYPE_ONLY,
            selectable = false,
            releaseBlocker = "The main branch adapter is a placeholder; the official Android backend integration remains unmerged and device-unverified."
        ),
        EngineSupport(
            id = "hysteria2",
            displayName = "Hysteria 2",
            readiness = EngineReadiness.PLANNED,
            selectable = false,
            releaseBlocker = "A pinned maintained Android client runtime and TUN bridge have not been integrated."
        ),
        EngineSupport(
            id = "shadowsocks-2022",
            displayName = "Shadowsocks 2022",
            readiness = EngineReadiness.PLANNED,
            selectable = false,
            releaseBlocker = "A maintained client runtime, UDP relay behavior, TUN bridge, and license review are pending."
        )
    )

    fun find(id: String): EngineSupport? = selectedEngines.firstOrNull { it.id == id }

    fun selectableEngines(): List<EngineSupport> =
        selectedEngines.filter { it.selectable && it.readiness == EngineReadiness.PRODUCTION }
}
