package com.molido.vpn.data

/**
 * No public test endpoints or fabricated credentials are shipped as usable
 * server profiles. Profiles must be imported from a trusted provider or
 * provisioned through a verified service before appearing here.
 */
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
    fun getDefaultFreeServers(): List<FreeServerProfile> = emptyList()
}
