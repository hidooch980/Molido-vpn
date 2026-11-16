package com.molido.vpn.engine.wireguard

data class WireGuardConfig(
    val interfaceConfig: InterfaceConfig,
    val peerConfig: PeerConfig,
    val rawText: String = ""
)

data class InterfaceConfig(
    val privateKey: String,
    val addresses: List<String>,
    val dnsServers: List<String> = emptyList(),
    val mtu: Int = 1420,
    val listenPort: Int? = null
)

data class PeerConfig(
    val publicKey: String,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
    val persistentKeepalive: Int? = 25,
    val presharedKey: String? = null
)
