package com.molido.vpn.data

import com.molido.vpn.engine.warp.WarpProvisioner

data class FreeServerProfile(
    val id: String,
    val name: String,
    val engineId: String,  // "xray", "warp", "amnezia"
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
        // Alirewa — large aggregated V2Ray config set, VLESS split
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        // yebekhe — Telegram channel aggregator, VLESS split
        "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/base64/vless",
        // mahdibland — V2RayAggregator, merged subscriptions
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/splitted/vless.txt",
        // MahanKenway — Freedom-V2Ray project
        "https://raw.githubusercontent.com/MahanKenway/Freedom-V2Ray/main/vless.txt",
        // barry-far — V2ray-Configs, regularly updated
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        // mfuu — v2ray subscription collection
        "https://raw.githubusercontent.com/mfuu/v2ray/master/v2ray",
        // peasoft — NoMoreWalls aggregator
        "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list_raw.txt",
        // freefq — free V2Ray nodes
        "https://raw.githubusercontent.com/freefq/free/master/v2",
        // Pawdroid — Free-servers collection
        "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
    )

    fun getDefaultFreeServers(): List<FreeServerProfile> {
        val warpProfile = WarpProvisioner.generateFreeProfile()

        return listOf(
            // ── WARP — always available, Cloudflare anycast ──
            FreeServerProfile(
                id = "free-warp-global",
                name = "Cloudflare WARP (Anycast Global)",
                engineId = "warp",
                country = "Anycast Global",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = warpProfile.rawText
            ),

            // ── Xray — community subscription placeholder entries ──
            // These are runtime-fetched; the entries below are well-known sample
            // links that the SubscriptionManager refreshes on first launch.
            FreeServerProfile(
                id = "free-xray-sub-1",
                name = "Xray Community Nodes (Auto-fetch)",
                engineId = "xray",
                country = "Mixed",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = "subscription://${GITHUB_COMMUNITY_SUBSCRIPTIONS[0]}"
            ),
            FreeServerProfile(
                id = "free-xray-sub-2",
                name = "Xray Telegram Aggregator (Auto-fetch)",
                engineId = "xray",
                country = "Mixed",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = "subscription://${GITHUB_COMMUNITY_SUBSCRIPTIONS[1]}"
            ),
            FreeServerProfile(
                id = "free-xray-sub-3",
                name = "Xray V2Ray Aggregator (Auto-fetch)",
                engineId = "xray",
                country = "Mixed",
                flagEmoji = "\uD83C\uDF10",
                rawPayload = "subscription://${GITHUB_COMMUNITY_SUBSCRIPTIONS[2]}"
            ),

            // ── AmneziaWG — requires user-supplied config ──
            FreeServerProfile(
                id = "free-amnezia-placeholder",
                name = "AmneziaWG (Needs User Config)",
                engineId = "amnezia",
                country = "User-provided",
                flagEmoji = "\uD83D\uDD12",
                rawPayload = "# No free AmneziaWG servers available.\n" +
                    "# Import your own AmneziaWG config to use this engine.\n" +
                    "# Paste a valid [Interface] + [Peer] AmneziaWG config here.",
                isFree = false
            )
        )
    }
}
