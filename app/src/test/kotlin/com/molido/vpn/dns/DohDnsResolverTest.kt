package com.molido.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DohDnsResolverTest {

    @Test
    fun providersContainKnownPublicResolvers() {
        val cloudflare = DohDnsResolver.getProvider("cloudflare")
        assertEquals("1.1.1.1", cloudflare.primaryIp)
        assertEquals("1.0.0.1", cloudflare.secondaryIp)

        val google = DohDnsResolver.getProvider("google")
        assertEquals("8.8.8.8", google.primaryIp)

        assertEquals("9.9.9.9", DohDnsResolver.getProvider("quad9").primaryIp)
        assertEquals("94.140.14.14", DohDnsResolver.getProvider("adguard").primaryIp)
    }

    @Test
    fun unknownProviderIsRejectedInsteadOfSilentlyFallingBack() {
        try {
            DohDnsResolver.getProvider("unknown")
            fail("Unknown DNS provider must be rejected")
        } catch (error: IllegalArgumentException) {
            assertEquals("DNS_PROVIDER_UNKNOWN", error.message)
        }
    }
}
