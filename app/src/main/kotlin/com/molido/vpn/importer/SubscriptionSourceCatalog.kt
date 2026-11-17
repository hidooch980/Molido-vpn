package com.molido.vpn.importer

/**
 * Curated public feeds observed in open-source subscription projects.
 * These feeds are untrusted inputs: importing a profile never marks it safe or production-ready.
 * Iran-specific ranking is deliberately not assigned here; it requires on-device carrier tests.
 */
object SubscriptionSourceCatalog {
    val sources = listOf(
        SubscriptionSource(
            id = "ss44-gold",
            displayName = "SS44 GOLD (traffic-tested upstream)",
            url = "https://raw.githubusercontent.com/ZendanTeam/SS44-sub/main/SS44-gold.txt",
            focus = "Iran-oriented community feed"
        ),
        SubscriptionSource(
            id = "ss44-standard",
            displayName = "SS44 Standard",
            url = "https://raw.githubusercontent.com/ZendanTeam/SS44-sub/main/SS44.txt",
            focus = "Iran-oriented community feed"
        ),
        SubscriptionSource(
            id = "community-vless",
            displayName = "Community VLESS",
            url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/vless.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "community-vmess",
            displayName = "Community VMess",
            url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/vmess.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "community-trojan",
            displayName = "Community Trojan",
            url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/trojan.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "community-shadowsocks",
            displayName = "Community Shadowsocks",
            url = "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Splitted-By-Protocol/ss.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "community-hysteria2",
            displayName = "Community Hysteria 2",
            url = "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/protocols/hysteria2.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "mahdi-community",
            displayName = "Mahdi Community Proxies",
            url = "https://raw.githubusercontent.com/Mahdi0024/ProxyCollector/master/sub/proxies.txt",
            focus = "General public feed"
        ),
        SubscriptionSource(
            id = "10ium-highspeed",
            displayName = "10ium HighSpeed",
            url = "https://raw.githubusercontent.com/10ium/free-config/refs/heads/main/HighSpeed.txt",
            focus = "General public feed"
        )
    )

    fun enabledDefaultSources(): List<SubscriptionSource> =
        sources.filter { it.enabledByDefault }

    fun find(id: String): SubscriptionSource? = sources.firstOrNull { it.id == id }
}
