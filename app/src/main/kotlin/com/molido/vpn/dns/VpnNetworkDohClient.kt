package com.molido.vpn.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

/**
 * RFC 8484 client for app-owned DNS lookups.
 * Requests use Android's active VPN Network and fail closed when it is absent.
 * This does not intercept system-wide UDP/53 traffic from other apps.
 */
class VpnNetworkDohClient(
    context: Context,
    private val providerId: String = "cloudflare",
    private val timeoutMillis: Int = 5_000
) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun resolve(hostname: String, recordType: DnsRecordType = DnsRecordType.A): ByteArray =
        withContext(Dispatchers.IO) {
            val query = DnsWireMessage.createQuery(hostname, recordType)
            val network = connectivity.activeNetwork
                ?: throw IllegalStateException("VPN_NETWORK_UNAVAILABLE")
            val capabilities = connectivity.getNetworkCapabilities(network)
            check(capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                "VPN_NETWORK_NOT_ACTIVE"
            }

            val provider = DohDnsResolver.getProvider(providerId)
            var connection: HttpsURLConnection? = null
            try {
                connection = network.openConnection(URL(provider.dohEndpoint)) as? HttpsURLConnection
                    ?: throw IllegalStateException("DOH_HTTPS_REQUIRED")
                val boundedTimeout = timeoutMillis.coerceIn(1, 30_000)
                connection.connectTimeout = boundedTimeout
                connection.readTimeout = boundedTimeout
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Content-Type", "application/dns-message")
                connection.setRequestProperty("Accept", "application/dns-message")
                connection.setFixedLengthStreamingMode(query.size)
                connection.outputStream.use { it.write(query) }

                check(connection.responseCode == HttpsURLConnection.HTTP_OK) {
                    "DOH_HTTP_" + connection.responseCode
                }
                val contentType = connection.contentType.orEmpty().substringBefore(';').trim()
                check(contentType.equals("application/dns-message", ignoreCase = true)) {
                    "DOH_CONTENT_TYPE_INVALID"
                }
                val response = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= MAX_DNS_MESSAGE_BYTES) { "DOH_RESPONSE_TOO_LARGE" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                DnsWireMessage.validateResponse(query, response)
                response
            } finally {
                connection?.disconnect()
            }
        }

    private companion object {
        const val MAX_DNS_MESSAGE_BYTES = 65_535
    }
}

data class DohProbeObservation(
    val aQuerySucceeded: Boolean,
    val aaaaQuerySucceeded: Boolean,
    val errorCode: String? = null
)

enum class DnsRecordType(val wireValue: Int) {
    A(1),
    AAAA(28)
}

internal object DnsWireMessage {
    private val random = SecureRandom()

    fun createQuery(hostname: String, type: DnsRecordType): ByteArray {
        val ascii = IDN.toASCII(hostname.trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
        require(ascii.isNotBlank() && ascii.length <= 253) { "DNS_NAME_INVALID" }
        val labels = ascii.split('.')
        require(labels.all { it.isNotEmpty() && it.length <= 63 }) { "DNS_NAME_INVALID" }

        val question = ByteArrayOutputStream()
        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            question.write(bytes.size)
            question.write(bytes)
        }
        question.write(0)
        question.write(type.wireValue ushr 8)
        question.write(type.wireValue and 0xff)
        question.write(0)
        question.write(1)

        val id = random.nextInt(65_536)
        val header = byteArrayOf(
            (id ushr 8).toByte(), id.toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        return header + question.toByteArray()
    }

    fun validateResponse(query: ByteArray, response: ByteArray) {
        require(query.size >= 17 && response.size >= 17) { "DOH_DNS_MESSAGE_TRUNCATED" }
        require(response.size <= 65_535) { "DOH_RESPONSE_TOO_LARGE" }
        require(readU16(response, 0) == readU16(query, 0)) { "DOH_TRANSACTION_ID_MISMATCH" }
        val flags = readU16(response, 2)
        require((flags and 0x8000) != 0) { "DOH_NOT_A_RESPONSE" }
        require((flags and 0x7800) == 0) { "DOH_OPCODE_UNSUPPORTED" }
        require((flags and 0x0200) == 0) { "DOH_TRUNCATED_RESPONSE" }
        val responseCode = flags and 0x000f
        require(responseCode == 0 || responseCode == 3) { "DOH_DNS_ERROR" }
        require(readU16(response, 4) == 1) { "DOH_QUESTION_COUNT_INVALID" }
        val end = questionEnd(query)
        require(end == questionEnd(response)) { "DOH_QUESTION_MALFORMED" }
        require(query.copyOfRange(12, end).contentEquals(response.copyOfRange(12, end))) {
            "DOH_QUESTION_MISMATCH"
        }
    }

    private fun questionEnd(message: ByteArray): Int {
        var offset = 12
        while (true) {
            require(offset < message.size) { "DOH_QUESTION_MALFORMED" }
            val length = message[offset++].toInt() and 0xff
            if (length == 0) break
            require((length and 0xc0) == 0 && length <= 63) { "DOH_QUESTION_MALFORMED" }
            require(offset + length <= message.size) { "DOH_QUESTION_MALFORMED" }
            offset += length
        }
        require(offset + 4 <= message.size) { "DOH_QUESTION_MALFORMED" }
        return offset + 4
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
