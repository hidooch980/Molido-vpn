package com.molido.vpn.data

import com.molido.vpn.engine.warp.WarpProvisioner

data class FreeServerProfile(
    val id: String,
    val name: String,
    val engineId: String,
    val country: String,
    val flagEmoji: String,
    val rawPayload: String,
    val isFree: Boolean = true
)

object FreeServerCatalog {

    val GITHUB_COMMUNITY_SUBSCRIPTIONS = listOf(
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/MahanKenway/Freedom-V2Ray/main/vless.txt",
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/vless.txt",
        "https://raw.githubusercontent.com/mehdirzfx/v2ray-sub/main/vless"
    )

    fun getDefaultFreeServers(): List<FreeServerProfile> {
        val warpProfile = WarpProvisioner.generateFreeProfile()

        val sampleVless1 = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@104.16.1.1:443?security=reality&sni=speed.cloudflare.com&pbk=bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=&type=tcp&flow=xtls-rprx-vision#Free-Reality-Cloudflare"
        val sampleVless2 = "vless://a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d@188.114.97.1:443?security=reality&sni=www.speedtest.net&pbk=bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=&type=tcp&flow=xtls-rprx-vision#Free-Reality-Germany"

        val sampleAmnezia = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1360
            Jc = 5
            Jmin = 40
            Jmax = 70
            S1 = 20
            S2 = 30
            H1 = 12345
            H2 = 67890
            H3 = 11111
            H4 = 22222

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.2:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val sampleWireguard = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1420

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        return listOf(
            FreeServerProfile(
                id = "free-warp-global",
                name = "Cloudflare WARP (Anycast Global)",
                engineId = "warp",
                country = "Anycast Global",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = warpProfile.rawText
            ),
            FreeServerProfile(
                id = "free-reality-1",
                name = "VLESS Reality (Anti-DPI Frankfurt)",
                engineId = "xray",
                country = "Germany",
                flagEmoji = "\uD83C\uDDE9\uD83C\uDDEA",
                rawPayload = sampleVless1
            ),
            FreeServerProfile(
                id = "free-reality-2",
                name = "VLESS Reality (High Speed Netherlands)",
                engineId = "xray",
                country = "Netherlands",
                flagEmoji = "\uD83C\uDDF3\uD83C\uDDF1",
                rawPayload = sampleVless2
            ),
            FreeServerProfile(
                id = "free-amnezia-1",
                name = "AmneziaWG Obfuscated (Anti-Filter)",
                engineId = "amnezia",
                country = "Finland",
                flagEmoji = "\uD83C\uDDEB\uD83C\uDDEE",
                rawPayload = sampleAmnezia
            ),
            FreeServerProfile(
                id = "free-wireguard-1",
                name = "WireGuard Community FastPeer",
                engineId = "wireguard",
                country = "Sweden",
                flagEmoji = "\uD83C\uDDF8\uD83C\uDDEA",
                rawPayload = sampleWireguard
            )
        )
    }
}
