package com.molido.vpn.data

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

    fun getDefaultFreeServers(): List<FreeServerProfile> = emptyList()
}
