package com.molido.vpn.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

data class ImportedConfig(
    val engineId: String,
    val name: String,
    val payload: String
)

data class SubscriptionSource(
    val id: String,
    val displayName: String,
    val url: String,
    val focus: String,
    val enabledByDefault: Boolean = true
)

data class SubscriptionFetchReport(
    val sourceId: String?,
    val configs: List<ImportedConfig>,
    val fetchedBytes: Int,
    val skippedLines: Int,
    val fetchedAtEpochMs: Long
)

object ConfigImporter {
    private const val MAX_CONFIG_CHARS = 64 * 1024

    fun detectAndParse(input: String): ImportedConfig {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_CONFIG_CHARS) { "CONFIG_SIZE_INVALID" }

        val scheme = trimmed.substringBefore("://", "").lowercase()
        val engine = when (scheme) {
            "hy2", "hysteria2" -> "hysteria2"
            "vless", "vmess", "trojan", "ss" -> "xray"
            else -> null
        }
        if (engine != null) {
            require(trimmed.none { it == '\u0000' || it == '\r' }) { "CONFIG_FORMAT_INVALID" }
            val name = extractRemark(trimmed) ?: when (scheme) {
                "hy2", "hysteria2" -> "Imported Hysteria 2 Server"
                "vmess" -> "Imported VMess Server"
                else -> "Imported ${scheme.uppercase()} Server"
            }
            return ImportedConfig(engineId = engine, name = sanitizeName(name), payload = trimmed)
        }

        if (trimmed.contains("[Interface]", ignoreCase = true) &&
            trimmed.contains("[Peer]", ignoreCase = true)
        ) {
            require(trimmed.contains("PrivateKey", ignoreCase = true) &&
                trimmed.contains("Endpoint", ignoreCase = true)
            ) { "WIREGUARD_CONFIG_INCOMPLETE" }
            val isAmnezia = listOf("Jc", "Jmin", "Jmax", "H1", "H2", "H3", "H4")
                .any { key -> Regex("(?im)^\\s*$key\\s*=").containsMatchIn(trimmed) }
            val engine = if (isAmnezia) "amnezia" else "wireguard"
            return ImportedConfig(
                engineId = engine,
                name = if (isAmnezia) "Imported AmneziaWG Server" else "Imported WireGuard Server",
                payload = trimmed
            )
        }
        throw IllegalArgumentException("CONFIG_FORMAT_UNSUPPORTED")
    }

    private fun extractRemark(uriStr: String): String? {
        val hash = uriStr.indexOf('#')
        if (hash < 0 || hash == uriStr.lastIndex) return null
        return try {
            URLDecoder.decode(uriStr.substring(hash + 1), "UTF-8")
        } catch (_: Exception) {
            "Imported VPN Server"
        }
    }

    private fun sanitizeName(value: String): String =
        value.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .trim().take(80).ifBlank { "Imported VPN Server" }
}

object SubscriptionManager {
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    const val MAX_CONFIGS = 500
    const val MAX_LINES = 5_000
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_REDIRECTS = 0
    private const val USER_AGENT = "MolidoVPN-SubscriptionManager/1.0"

    /**
     * HTTPS only. Direct IPs and hostnames resolving to non-public addresses are rejected.
     * Redirects are intentionally disabled to prevent a trusted URL redirecting to an unsafe host.
     */
    fun validateSubscriptionUrl(raw: String, resolveDns: Boolean = true): URL {
        require(raw.length in 1..2_048) { "SUBSCRIPTION_URL_INVALID" }
        val uri = try { URI(raw.trim()).normalize() } catch (_: Exception) {
            throw IllegalArgumentException("SUBSCRIPTION_URL_INVALID")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) { "SUBSCRIPTION_HTTPS_REQUIRED" }
        require(uri.userInfo == null && uri.fragment == null) { "SUBSCRIPTION_URL_INVALID" }
        val host = uri.host?.trimEnd('.')?.lowercase()
            ?: throw IllegalArgumentException("SUBSCRIPTION_HOST_INVALID")
        require(host.isNotBlank() && !host.equals("localhost", true) &&
            !host.endsWith(".localhost") && !host.endsWith(".local")
        ) { "SUBSCRIPTION_HOST_BLOCKED" }
        val port = if (uri.port == -1) 443 else uri.port
        require(port == 443) { "SUBSCRIPTION_PORT_BLOCKED" }

        val literal = host.removePrefix("[").removeSuffix("]")
        val isIpLiteral = literal.contains(':') ||
            literal.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}"))
        if (isIpLiteral) {
            val address = try { InetAddress.getByName(literal) } catch (_: Exception) {
                throw IllegalArgumentException("SUBSCRIPTION_HOST_INVALID")
            }
            require(!isNonPublicAddress(address)) { "SUBSCRIPTION_HOST_BLOCKED" }
        } else if (resolveDns) {
            // Check every A/AAAA answer, not just the first one.
            val resolved = try { InetAddress.getAllByName(host) } catch (_: Exception) {
                throw IllegalArgumentException("SUBSCRIPTION_DNS_FAILED")
            }
            require(resolved.isNotEmpty() && resolved.none(::isNonPublicAddress)) {
                "SUBSCRIPTION_HOST_BLOCKED"
            }
        }
        return URL(uri.toASCIIString())
    }

    /**
     * SSLSocketFactory that connects only to the DNS answers validated for this
     * request. The layered TLS socket receives the original hostname, preserving
     * SNI and normal HTTPS certificate hostname checks in HttpsURLConnection.
     */
    private class PinnedAddressSocketFactory(
        private val hostname: String,
        private val addresses: Array<InetAddress>
    ) : SSLSocketFactory() {
        private val delegate = SSLSocketFactory.getDefault() as SSLSocketFactory

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(): Socket = delegate.createSocket()
        override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            delegate.createSocket(socket, host, port, autoClose)

        override fun createSocket(host: String, port: Int): Socket =
            connectPinned(hostname, port, null, 0)

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            connectPinned(hostname, port, localHost, localPort)

        override fun createSocket(address: InetAddress, port: Int): Socket =
            connectPinned(hostname, port, null, 0)

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int
        ): Socket = connectPinned(hostname, port, localAddress, localPort)

        private fun connectPinned(
            host: String,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket {
            require(host.equals(hostname, ignoreCase = true) && port == 443) {
                "SUBSCRIPTION_PIN_MISMATCH"
            }
            var lastError: Exception? = null
            for (address in addresses) {
                val raw = Socket()
                try {
                    if (localAddress != null) raw.bind(InetSocketAddress(localAddress, localPort))
                    raw.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                    return delegate.createSocket(raw, hostname, port, true)
                } catch (error: Exception) {
                    lastError = error
                    try { raw.close() } catch (_: Exception) { }
                }
            }
            throw IOException("SUBSCRIPTION_CONNECT_FAILED", lastError)
        }
    }

    private fun isNonPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true
        val bytes = address.address
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            // Accept only IPv6 global-unicast 2000::/3. This rejects unspecified,
            // loopback, link-local, unique-local, multicast and reserved ranges.
            if ((first and 0xe0) != 0x20) return true
            // Reject documentation (2001:db8::/32), benchmarking (2001:2::/48),
            // and IPv4-mapped/translated special forms even if platform flags miss them.
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            val fourth = bytes[3].toInt() and 0xff
            if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return true
            if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x02) return true
            if (bytes.take(12).all { it.toInt() == 0 } &&
                bytes[10].toInt() == -1 && bytes[11].toInt() == -1
            ) {
                return isNonPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
            return false
        }
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            return a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 100 && b in 64..127) ||
                (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && (b == 168 || b == 0)) ||
                (a == 198 && (b in 18..19 || b == 51)) ||
                (a == 203 && b == 0)
        }
        return true
    }

    suspend fun fetchSubscription(subscriptionUrl: String): List<ImportedConfig> =
        fetchSubscriptionReport(subscriptionUrl).configs

    suspend fun fetchSubscriptionReport(
        subscriptionUrl: String,
        sourceId: String? = null
    ): SubscriptionFetchReport = withContext(Dispatchers.IO) {
        // Resolve once, validate every answer, then pin the TCP connection to
        // those exact addresses. TLS still uses the original hostname for SNI
        // and certificate hostname verification.
        val url = validateSubscriptionUrl(subscriptionUrl, resolveDns = false)
        val host = url.host.removePrefix("[").removeSuffix("]")
        val pinnedAddresses = try { InetAddress.getAllByName(host) } catch (_: Exception) {
            throw IllegalArgumentException("SUBSCRIPTION_DNS_FAILED")
        }
        require(pinnedAddresses.isNotEmpty() && pinnedAddresses.none(::isNonPublicAddress)) {
            "SUBSCRIPTION_HOST_BLOCKED"
        }
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = PinnedAddressSocketFactory(host, pinnedAddresses)
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/plain, application/octet-stream")
            useCaches = false
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode in 300..399 && MAX_REDIRECTS == 0) {
                throw IOException("SUBSCRIPTION_REDIRECT_REJECTED")
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("SUBSCRIPTION_HTTP_ERROR")
            }
            val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
            require(declaredLength <= MAX_RESPONSE_BYTES) { "SUBSCRIPTION_RESPONSE_TOO_LARGE" }
            val bytes = readBounded(connection.inputStream, MAX_RESPONSE_BYTES)
            val body = bytes.toString(Charsets.UTF_8)
            val parsed = parseSubscriptionBody(body)
            SubscriptionFetchReport(
                sourceId = sourceId,
                configs = parsed.first,
                fetchedBytes = bytes.size,
                skippedLines = parsed.second,
                fetchedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Parses URI-per-line and Base64-wrapped URI lists. YAML/JSON are rejected, never guessed. */
    fun parseSubscriptionBody(rawContent: String): Pair<List<ImportedConfig>, Int> {
        require(rawContent.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES) {
            "SUBSCRIPTION_RESPONSE_TOO_LARGE"
        }
        val normalized = rawContent.removePrefix("\uFEFF").trim()
        require(normalized.isNotEmpty()) { "SUBSCRIPTION_EMPTY" }
        val decoded = decodeIfBase64(normalized)
        val lines = decoded.lineSequence().take(MAX_LINES + 1).toList()
        require(lines.size <= MAX_LINES) { "SUBSCRIPTION_TOO_MANY_LINES" }

        val configs = LinkedHashMap<String, ImportedConfig>()
        var skipped = 0
        for (line in lines) {
            val item = line.trim()
            if (item.isEmpty() || item.startsWith("#") || item.startsWith("//") ||
                item.startsWith("<!--") || item.startsWith("proxies:") ||
                item.startsWith("proxy-groups:") || item.startsWith("rules:")
            ) continue
            try {
                val config = ConfigImporter.detectAndParse(item)
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(config.payload.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                configs.putIfAbsent(digest, config)
                require(configs.size <= MAX_CONFIGS) { "SUBSCRIPTION_TOO_MANY_CONFIGS" }
            } catch (_: IllegalArgumentException) {
                skipped++
            }
        }
        require(configs.isNotEmpty()) { "SUBSCRIPTION_NO_SUPPORTED_CONFIGS" }
        return configs.values.toList() to skipped
    }

    private fun decodeIfBase64(content: String): String {
        val compact = content.filterNot { it.isWhitespace() }
        if (compact.length < 16 || !compact.matches(Regex("[A-Za-z0-9+/=_-]+"))) return content
        return try {
            val standard = compact.replace('-', '+').replace('_', '/')
            val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
            val decoded = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            if (decoded.contains("://") || decoded.contains("[Interface]")) decoded else content
        } catch (_: Exception) {
            content
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > maxBytes) {
                    throw IOException("SUBSCRIPTION_RESPONSE_TOO_LARGE")
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    suspend fun fetchCuratedCommunitySubscriptions(): List<SubscriptionFetchReport> =
        withContext(Dispatchers.IO) {
            val reports = mutableListOf<SubscriptionFetchReport>()
            for (source in SubscriptionSourceCatalog.sources.filter { it.enabledByDefault }) {
                try {
                    reports += fetchSubscriptionReport(source.url, source.id)
                } catch (_: Exception) {
                    // A failed source never invalidates a previously persisted good snapshot.
                    // Persistence and scheduling are handled by the repository/worker layer.
                }
            }
            reports
        }

    /** Backward-compatible facade used by the current UI. */
    suspend fun fetchGithubCommunitySubscriptions(): List<ImportedConfig> =
        fetchCuratedCommunitySubscriptions().flatMap { it.configs }
}
