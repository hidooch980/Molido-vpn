package com.molido.vpn.verifier

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SystemDnsLookupTest {
    @Test
    fun delegatesToAndroidProcessDefaultResolverApi() {
        var requestedName: String? = null
        val expected = InetAddress.getByAddress(
            byteArrayOf(192.toByte(), 0.toByte(), 2.toByte(), 10.toByte())
        )

        val actual = SystemDnsLookup.resolve("7.unique-test.bash.ws") { hostname ->
            requestedName = hostname
            arrayOf(expected)
        }

        assertEquals("7.unique-test.bash.ws", requestedName)
        assertArrayEquals(arrayOf(expected), actual)
        assertEquals("ANDROID_PROCESS_DEFAULT_SYSTEM_RESOLVER", SystemDnsLookup.LOOKUP_PATH)
    }
}
