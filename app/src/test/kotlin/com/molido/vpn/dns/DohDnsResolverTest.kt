package com.molido.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DohDnsResolverTest {

    @Test
    fun providersContainCloudflareAndGoogle() {
        val cloudflare = DohDnsResolver.getProvider("cloudflare")
        assertEquals("1.1.1.1", cloudflare.primaryIp)
        assertEquals("1.0.0.1", cloudflare.secondaryIp)

        val google = DohDnsResolver.getProvider("google")
        assertEquals("8.8.8.8", google.primaryIp)

        val fallback = DohDnsResolver.getProvider("unknown")
        assertNotNull(fallback)
    }
}
