package com.molido.vpn.data

import com.molido.vpn.importer.SubscriptionSourceCatalog

data class FreeServerProfile(
    val id: String,
    val name: String,
    val engineId: String,
    val country: String,
    val flagEmoji: String,
    val rawPayload: String,
    val isFree: Boolean = true
)

/**
 * Compatibility facade. Public subscription sources are not individual verified servers.
 * The app must fetch, parse, deduplicate and test them before presenting a usable profile.
 */
object FreeServerCatalog {
    val GITHUB_COMMUNITY_SUBSCRIPTIONS: List<String>
        get() = SubscriptionSourceCatalog.enabledDefaultSources().map { it.url }

    fun getDefaultFreeServers(): List<FreeServerProfile> = emptyList()
}
