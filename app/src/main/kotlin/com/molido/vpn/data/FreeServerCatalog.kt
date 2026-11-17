package com.molido.vpn.data

import com.molido.vpn.engine.warp.WarpProvisioner

data class FreeServerProfile(
    val id: String,
    val name: String,
    val engineId: String,  // "xray", "warp", "amnezia", "wireguard"
    val country: String,
    val flagEmoji: String,
    val rawPayload: String,
    val isFree: Boolean = true
)

object FreeServerCatalog {

    /**
     * Community-maintained GitHub subscription URLs that provide fresh VLESS/Xray configs.
     * The app fetches these at runtime via [SubscriptionManager].
     */
    val GITHUB_COMMUNITY_SUBSCRIPTIONS = listOf(
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/base64/vless",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/splitted/vless.txt",
        "https://raw.githubusercontent.com/MahanKenway/Freedom-V2Ray/main/vless.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/mfuu/v2ray/master/v2ray",
        "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list_raw.txt",
        "https://raw.githubusercontent.com/freefq/free/master/v2",
        "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
    )

    fun getDefaultFreeServers(): List<FreeServerProfile> {
        val warpProfile = WarpProvisioner.generateFreeProfile()

        val sampleVlessReality1 = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@104.16.1.1:443?security=reality&sni=speed.cloudflare.com&pbk=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D&type=tcp&flow=xtls-rprx-vision#Xray-Reality-Frankfurt"
        val sampleVlessReality2 = "vless://a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d@188.114.97.1:443?security=reality&sni=www.speedtest.net&pbk=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D&type=tcp&flow=xtls-rprx-vision#Xray-Reality-Amsterdam"
        val sampleTrojan = "trojan://molido-free-access@104.18.2.2:443?security=tls&sni=speed.cloudflare.com#Xray-Trojan-Global"

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

        val cleanIpWarp = warpProfile.rawText.replace("Endpoint = 162.159.192.1:2408", "Endpoint = 188.114.97.1:2408")

        return listOf(
            FreeServerProfile(
                id = "free-warp-global",
                name = "Cloudflare WARP (Anycast Global)",
                engineId = "warp",
                country = "Global Anycast",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = warpProfile.rawText,
                isFree = true
            ),
            FreeServerProfile(
                id = "free-warp-clean",
                name = "Cloudflare WARP (Clean IP Anti-Filter)",
                engineId = "warp",
                country = "Clean Edge",
                flagEmoji = "\u26A1",
                rawPayload = cleanIpWarp,
                isFree = true
            ),
            FreeServerProfile(
                id = "free-xray-1",
                name = "Xray Reality (Frankfurt Anti-DPI)",
                engineId = "xray",
                country = "Germany",
                flagEmoji = "\uD83C\uDDE9\uD83C\uDDEA",
                rawPayload = sampleVlessReality1,
                isFree = true
            ),
            FreeServerProfile(
                id = "free-xray-2",
                name = "Xray Reality (Amsterdam High-Speed)",
                engineId = "xray",
                country = "Netherlands",
                flagEmoji = "\uD83C\uDDF3\uD83C\uDDF1",
                rawPayload = sampleVlessReality2,
                isFree = true
            ),
            FreeServerProfile(
                id = "free-xray-3",
                name = "Xray Trojan (Cloudflare CDN)",
                engineId = "xray",
                country = "Global CDN",
                flagEmoji = "\uD83D\uDE80",
                rawPayload = sampleTrojan,
                isFree = true
            ),
            FreeServerProfile(
                id = "free-amnezia-1",
                name = "AmneziaWG (Obfuscated Anti-Sensor)",
                engineId = "amnezia",
                country = "Finland",
                flagEmoji = "\uD83C\uDDEB\uD83C\uDDEE",
                rawPayload = sampleAmnezia,
                isFree = true
            )
        )
    }
}
