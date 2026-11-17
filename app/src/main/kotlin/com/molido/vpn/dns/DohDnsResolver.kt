package com.molido.vpn.dns

import android.net.VpnService

data class DnsProvider(
    val id: String,
    val name: String,
    val description: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dohEndpoint: String
)

/**
 * Supplies resolver IPs for the VPN interface and provider metadata for the
 * separate RFC 8484 app-owned DoH client. Builder DNS remains ordinary DNS;
 * this class does not intercept or convert system-wide UDP/53 packets.
 */
object DohDnsResolver {

    val PROVIDERS = listOf(
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare DNS",
            description = "DoH for app-owned lookups; system-wide DNS interception is not enabled.",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            dohEndpoint = "https://cloudflare-dns.com/dns-query"
        ),
        DnsProvider(
            id = "google",
            name = "Google Public DNS",
            description = "DoH for app-owned lookups; system-wide DNS interception is not enabled.",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            dohEndpoint = "https://dns.google/dns-query"
        ),
        DnsProvider(
            id = "quad9",
            name = "Quad9 DNS",
            description = "Quad9 DoH for app-owned lookups; system-wide DNS interception is not enabled.",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            dohEndpoint = "https://dns.quad9.net/dns-query"
        ),
        DnsProvider(
            id = "adguard",
            name = "AdGuard DNS",
            description = "AdGuard DoH for app-owned lookups; system-wide DNS interception is not enabled.",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            dohEndpoint = "https://dns.adguard-dns.com/dns-query"
        )
    )

    fun getProvider(id: String): DnsProvider {
        return PROVIDERS.firstOrNull { it.id == id } ?: throw IllegalArgumentException("DNS_PROVIDER_UNKNOWN")
    }

    fun applyToVpnBuilder(builder: VpnService.Builder, providerId: String) {
        val provider = getProvider(providerId)
        builder.addDnsServer(provider.primaryIp)
        builder.addDnsServer(provider.secondaryIp)
    }
}
