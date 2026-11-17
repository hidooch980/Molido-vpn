package com.molido.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnNetworkDohClientTest {
    @Test
    fun buildsAAndAaaaQueriesWithOneQuestion() {
        val a = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        val aaaa = DnsWireMessage.createQuery("example.com", DnsRecordType.AAAA)

        assertEquals(1, readU16(a, 4))
        assertEquals(1, readU16(aaaa, 4))
        assertEquals(1, readU16(a, a.size - 4))
        assertEquals(28, readU16(aaaa, aaaa.size - 4))
        assertEquals(1, readU16(a, a.size - 2))
        assertEquals(1, readU16(aaaa, aaaa.size - 2))
    }

    @Test
    fun encodesInternationalDomainNamesAsAsciiLabels() {
        val query = DnsWireMessage.createQuery("bücher.example", DnsRecordType.A)
        val wire = query.copyOfRange(12, query.size).toString(Charsets.ISO_8859_1)
        assertTrue(wire.contains("xn--bcher-kva"))
    }

    @Test
    fun acceptsMatchingSuccessfulDnsResponse() {
        val query = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        val response = query.copyOf().apply {
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
        }
        DnsWireMessage.validateResponse(query, response)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsResponseWithDifferentTransactionId() {
        val query = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        val response = query.copyOf().apply {
            this[0] = (this[0].toInt() xor 1).toByte()
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
        }
        DnsWireMessage.validateResponse(query, response)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonResponsePacket() {
        val query = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        DnsWireMessage.validateResponse(query, query)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsQuestionMismatch() {
        val query = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        val other = DnsWireMessage.createQuery("example.net", DnsRecordType.A)
        val response = other.copyOf().apply {
            this[0] = query[0]
            this[1] = query[1]
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
        }
        DnsWireMessage.validateResponse(query, response)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDnsErrorResponse() {
        val query = DnsWireMessage.createQuery("example.com", DnsRecordType.A)
        val response = query.copyOf().apply {
            this[2] = 0x81.toByte()
            this[3] = 0x83.toByte()
        }
        DnsWireMessage.validateResponse(query, response)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedNames() {
        DnsWireMessage.createQuery("bad..example", DnsRecordType.A)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
