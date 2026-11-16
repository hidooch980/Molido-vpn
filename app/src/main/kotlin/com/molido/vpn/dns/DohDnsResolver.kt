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

object DohDnsResolver {

    val PROVIDERS = listOf(
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare DoH",
            description = "Privacy-first, ultra-fast 1.1.1.1 DNS",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            dohEndpoint = "https://1.1.1.1/dns-query"
        ),
        DnsProvider(
            id = "google",
            name = "Google Public DNS",
            description = "Reliable, globally-distributed 8.8.8.8",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            dohEndpoint = "https://dns.google/dns-query"
        ),
        DnsProvider(
            id = "quad9",
            name = "Quad9 Secure",
            description = "Blocks malicious domains and phishing",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            dohEndpoint = "https://dns.quad9.net/dns-query"
        ),
        DnsProvider(
            id = "adguard",
            name = "AdGuard DNS",
            description = "Blocks ads and trackers natively",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            dohEndpoint = "https://dns.adguard.com/dns-query"
        )
    )

    fun getProvider(id: String): DnsProvider {
        return PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS[0]
    }

    fun applyToVpnBuilder(builder: VpnService.Builder, providerId: String) {
        val provider = getProvider(providerId)
        try {
            builder.addDnsServer(provider.primaryIp)
            builder.addDnsServer(provider.secondaryIp)
        } catch (_: Exception) {}
    }
}
