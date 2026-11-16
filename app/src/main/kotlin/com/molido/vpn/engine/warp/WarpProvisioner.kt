package com.molido.vpn.engine.warp

import com.molido.vpn.engine.wireguard.InterfaceConfig
import com.molido.vpn.engine.wireguard.PeerConfig
import com.molido.vpn.engine.wireguard.WireGuardConfig
import java.security.SecureRandom
import java.util.Base64

object WarpProvisioner {
    const val WARP_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    const val WARP_ENDPOINT_IPV4 = "162.159.192.1"
    const val WARP_ENDPOINT_PORT = 2408

    fun generateFreeProfile(privateKeyBase64: String? = null): WireGuardConfig {
        val privateKey = privateKeyBase64 ?: generateRandomPrivateKey()

        val iface = InterfaceConfig(
            privateKey = privateKey,
            addresses = listOf("172.16.0.2/32", "2606:4700:110:8735:6690:c95a:e623:8710/128"),
            dnsServers = listOf("1.1.1.1", "1.0.0.1"),
            mtu = 1280
        )

        val peer = PeerConfig(
            publicKey = WARP_PUBLIC_KEY,
            endpointHost = WARP_ENDPOINT_IPV4,
            endpointPort = WARP_ENDPOINT_PORT,
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            persistentKeepalive = 25
        )

        val rawText = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = 172.16.0.2/32, 2606:4700:110:8735:6690:c95a:e623:8710/128")
            appendLine("DNS = 1.1.1.1, 1.0.0.1")
            appendLine("MTU = 1280")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $WARP_PUBLIC_KEY")
            appendLine("Endpoint = $WARP_ENDPOINT_IPV4:$WARP_ENDPOINT_PORT")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("PersistentKeepalive = 25")
        }

        return WireGuardConfig(iface, peer, rawText)
    }

    private fun generateRandomPrivateKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        // Clamp bytes for Curve25519
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = (bytes[31].toInt() and 127 or 64).toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
