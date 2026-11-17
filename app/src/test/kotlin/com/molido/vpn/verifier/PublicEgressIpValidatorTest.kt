package com.molido.vpn.verifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicEgressIpValidatorTest {
    @Test
    fun acceptsPublicIpv4AndIpv6Literals() {
        assertEquals("8.8.8.8", PublicEgressIpValidator.parse("8.8.8.8")?.hostAddress)
        assertEquals("2001:4860:4860:0:0:0:0:8888", PublicEgressIpValidator.parse("2001:4860:4860::8888")?.hostAddress)
    }

    @Test
    fun rejectsPrivateLoopbackLinkLocalAndDocumentationIpv4() {
        listOf(
            "10.0.0.1", "172.16.0.1", "192.168.1.1", "127.0.0.1",
            "169.254.1.1", "192.0.2.10", "198.51.100.10", "203.0.113.10",
            "224.0.0.1", "0.0.0.0", "100.64.0.1"
        ).forEach { assertNull("Must reject $it", PublicEgressIpValidator.parse(it)) }
    }

    @Test
    fun rejectsNonGlobalIpv6AndMalformedValues() {
        listOf(
            "::1", "fe80::1", "fc00::1", "ff02::1",
            "2001:db8::1", "2001:2::1", "not-an-ip", "8.8.8.8\n"
        ).forEach { assertNull("Must reject $it", PublicEgressIpValidator.parse(it)) }
    }
}
