package com.molido.vpn.engine.wireguard

import com.molido.vpn.engine.ValidationResult
import java.util.Base64
import java.util.Locale

object WireGuardConfigParser {
    private const val MAX_CONFIG_BYTES = 64 * 1024

    fun parse(rawText: String): WireGuardConfig {
        if (rawText.isBlank()) {
            throw IllegalArgumentException("Configuration payload is empty")
        }
        val bytes = rawText.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_CONFIG_BYTES) {
            throw IllegalArgumentException("Configuration exceeds maximum size limit ($MAX_CONFIG_BYTES bytes)")
        }

        val cleanText = rawText.replace("\uFEFF", "")
        val ifaceMap = mutableMapOf<String, String>()
        val peerMap = mutableMapOf<String, String>()
        var activeMap: MutableMap<String, String>? = null
        var sawInterface = false
        var sawPeer = false

        for (rawLine in cleanText.lines()) {
            val line = rawLine.split('#', ';')[0].trim()
            if (line.isEmpty()) continue

            if (line.equals("[Interface]", ignoreCase = true)) {
                if (sawInterface || sawPeer) {
                    throw IllegalArgumentException("Duplicate or misplaced [Interface] section")
                }
                sawInterface = true
                activeMap = ifaceMap
                continue
            }

            if (line.equals("[Peer]", ignoreCase = true)) {
                if (!sawInterface) {
                    throw IllegalArgumentException("[Peer] section must follow [Interface]")
                }
                if (sawPeer) {
                    // For now, single-peer configuration is enforced in baseline
                    throw IllegalArgumentException("Multiple [Peer] sections are not supported in baseline")
                }
                sawPeer = true
                activeMap = peerMap
                continue
            }

            val eqIndex = line.indexOf('=')
            if (activeMap == null || eqIndex < 1) {
                throw IllegalArgumentException("Invalid syntax or key outside of section: '$line'")
            }

            val key = line.substring(0, eqIndex).trim().lowercase(Locale.ROOT)
            val value = line.substring(eqIndex + 1).trim()

            if (value.isEmpty()) {
                throw IllegalArgumentException("Empty value for key: '$key'")
            }

            if (activeMap.containsKey(key)) {
                throw IllegalArgumentException("Duplicate key '$key' in section")
            }
            activeMap[key] = value
        }

        if (!sawInterface) throw IllegalArgumentException("Missing [Interface] section")
        if (!sawPeer) throw IllegalArgumentException("Missing [Peer] section")

        // Validate Interface keys
        val privateKey = ifaceMap["privatekey"]
            ?: throw IllegalArgumentException("Missing PrivateKey in [Interface]")
        validateBase64Key(privateKey, "PrivateKey")

        val addressRaw = ifaceMap["address"]
            ?: throw IllegalArgumentException("Missing Address in [Interface]")
        val addresses = addressRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (addresses.isEmpty()) {
            throw IllegalArgumentException("No addresses defined in [Interface]")
        }
        for (addr in addresses) {
            validateCidr(addr)
        }

        val dnsRaw = ifaceMap["dns"]
        val dnsServers = dnsRaw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        for (dns in dnsServers) {
            validateIp(dns)
        }

        val mtu = ifaceMap["mtu"]?.toIntOrNull() ?: 1420
        if (mtu !in 1280..9000) {
            throw IllegalArgumentException("Invalid MTU '$mtu' (must be between 1280 and 9000)")
        }

        val listenPort = ifaceMap["listenport"]?.toIntOrNull()
        if (listenPort != null && listenPort !in 1..65535) {
            throw IllegalArgumentException("Invalid ListenPort '$listenPort'")
        }

        // Validate Peer keys
        val publicKey = peerMap["publickey"]
            ?: throw IllegalArgumentException("Missing PublicKey in [Peer]")
        validateBase64Key(publicKey, "PublicKey")

        val presharedKey = peerMap["presharedkey"]
        if (presharedKey != null) {
            validateBase64Key(presharedKey, "PresharedKey")
        }

        val endpointRaw = peerMap["endpoint"]
            ?: throw IllegalArgumentException("Missing Endpoint in [Peer]")
        val (host, port) = parseEndpoint(endpointRaw)

        val allowedIpsRaw = peerMap["allowedips"] ?: "0.0.0.0/0, ::/0"
        val allowedIps = allowedIpsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        for (cidr in allowedIps) {
            validateCidr(cidr)
        }

        val keepalive = peerMap["persistentkeepalive"]?.toIntOrNull()
        if (keepalive != null && keepalive !in 0..3600) {
            throw IllegalArgumentException("Invalid PersistentKeepalive '$keepalive'")
        }

        val interfaceConfig = InterfaceConfig(
            privateKey = privateKey,
            addresses = addresses,
            dnsServers = dnsServers,
            mtu = mtu,
            listenPort = listenPort
        )

        val peerConfig = PeerConfig(
            publicKey = publicKey,
            endpointHost = host,
            endpointPort = port,
            allowedIps = allowedIps,
            persistentKeepalive = keepalive,
            presharedKey = presharedKey
        )

        return WireGuardConfig(
            interfaceConfig = interfaceConfig,
            peerConfig = peerConfig,
            rawText = rawText
        )
    }

    fun validate(rawText: String): ValidationResult {
        return try {
            parse(rawText)
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid(e.message ?: "Invalid WireGuard configuration")
        }
    }

    private fun validateBase64Key(key: String, fieldName: String) {
        val trimmed = key.trim()
        if (trimmed.length != 44 || !trimmed.endsWith("=")) {
            throw IllegalArgumentException("$fieldName must be a 44-character Base64 string ending with '='")
        }
        val decoded = try {
            Base64.getDecoder().decode(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("$fieldName contains invalid Base64 characters")
        }
        if (decoded.size != 32) {
            throw IllegalArgumentException("$fieldName must decode to exactly 32 bytes (got ${decoded.size})")
        }
        if (decoded.all { it == 0.toByte() }) {
            throw IllegalArgumentException("$fieldName cannot be all zeroes")
        }
    }

    private fun parseEndpoint(endpoint: String): Pair<String, Int> {
        val trimmed = endpoint.trim()
        val host: String
        val portStr: String

        if (trimmed.startsWith('[')) {
            // IPv6 [host]:port
            val closeBracket = trimmed.indexOf(']')
            if (closeBracket < 0 || !trimmed.substring(closeBracket).startsWith("]:")) {
                throw IllegalArgumentException("Malformed IPv6 endpoint format: '$endpoint'")
            }
            host = trimmed.substring(1, closeBracket)
            portStr = trimmed.substring(closeBracket + 2)
        } else {
            val colonIndex = trimmed.lastIndexOf(':')
            if (colonIndex <= 0 || colonIndex != trimmed.indexOf(':')) {
                // Must have single colon for host:port (IPv4 or hostname)
                throw IllegalArgumentException("Invalid endpoint format: '$endpoint'")
            }
            host = trimmed.substring(0, colonIndex)
            portStr = trimmed.substring(colonIndex + 1)
        }

        val port = portStr.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid endpoint port: '$portStr'")
        if (port !in 1..65535) {
            throw IllegalArgumentException("Endpoint port out of range: $port")
        }
        return Pair(host, port)
    }

    private fun validateCidr(cidr: String) {
        val parts = cidr.split('/')
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid CIDR format: '$cidr'")
        }
        val ip = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid CIDR prefix in '$cidr'")

        if (ip.contains(':')) {
            // IPv6
            if (prefix !in 0..128) throw IllegalArgumentException("IPv6 prefix out of range in '$cidr'")
        } else {
            // IPv4
            validateIp(ip)
            if (prefix !in 0..32) throw IllegalArgumentException("IPv4 prefix out of range in '$cidr'")
        }
    }

    private fun validateIp(ip: String) {
        val parts = ip.split('.')
        if (parts.size == 4) {
            for (p in parts) {
                val num = p.toIntOrNull() ?: throw IllegalArgumentException("Invalid IP: '$ip'")
                if (num !in 0..255) throw IllegalArgumentException("IP octet out of range in '$ip'")
            }
        } else if (!ip.contains(':')) {
            throw IllegalArgumentException("Malformed IP address: '$ip'")
        }
    }
}


/** Full-tunnel route guard and resolver override for the GoBackend profile. */
object WireGuardTunnelPolicy {
    fun validateFullTunnel(rawText: String): String? {
        val allowedIps = Regex("(?im)^\\s*AllowedIPs\\s*=\\s*([^\\r\\n]+)")
            .findAll(rawText)
            .flatMap { it.groupValues[1].split(',').asSequence() }
            .map { it.trim() }
            .toSet()
        if ("0.0.0.0/0" !in allowedIps) return "IPV4_DEFAULT_ROUTE_REQUIRED"
        if ("::/0" !in allowedIps) return "IPV6_DEFAULT_ROUTE_REQUIRED"
        return null
    }

    fun applyDnsServers(rawText: String, dnsServers: List<String>): String {
        if (dnsServers.isEmpty()) return rawText
        require(dnsServers.size in 1..4 && dnsServers.all(::isIpv4Literal)) { "DNS_OVERRIDE_INVALID" }
        val lines = rawText.lines().toMutableList()
        val interfaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        require(interfaceIndex >= 0) { "WIREGUARD_INTERFACE_MISSING" }
        val peerIndex = lines.indexOfFirst { it.trim().equals("[Peer]", ignoreCase = true) }
            .let { if (it < 0) lines.size else it }
        require(peerIndex > interfaceIndex) { "WIREGUARD_PEER_MISSING" }
        val dnsLine = "DNS = " + dnsServers.distinct().joinToString(", ")
        val existingDnsIndex = (interfaceIndex + 1 until peerIndex).firstOrNull { index ->
            val line = lines[index].substringBefore('#').trim()
            line.substringBefore('=').trim().equals("DNS", ignoreCase = true)
        }
        if (existingDnsIndex != null) lines[existingDnsIndex] = dnsLine
        else lines.add(interfaceIndex + 1, dnsLine)
        return lines.joinToString("\n")
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
