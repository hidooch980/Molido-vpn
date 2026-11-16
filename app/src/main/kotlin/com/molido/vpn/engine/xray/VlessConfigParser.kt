package com.molido.vpn.engine.xray

import com.molido.vpn.engine.ValidationResult
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

object VlessConfigParser {

    fun parse(rawUri: String): VlessConfig {
        val trimmed = rawUri.trim()
        if (trimmed.startsWith("vless://", ignoreCase = true)) {
            return parseVless(trimmed)
        } else if (trimmed.startsWith("trojan://", ignoreCase = true)) {
            return parseTrojan(trimmed)
        } else if (trimmed.startsWith("ss://", ignoreCase = true)) {
            return parseShadowsocks(trimmed)
        } else {
            throw IllegalArgumentException("Unsupported protocol URI scheme. Expected vless://, trojan://, or ss://")
        }
    }

    private fun parseVless(uriStr: String): VlessConfig {
        val uri = URI(uriStr)
        val userInfo = uri.userInfo ?: throw IllegalArgumentException("Missing UUID in VLESS URI")
        val host = uri.host ?: throw IllegalArgumentException("Missing host in VLESS URI")
        val port = if (uri.port > 0) uri.port else 443
        val remark = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "$host:$port"

        val queryParams = parseQuery(uri.rawQuery)

        return VlessConfig(
            protocol = "vless",
            id = userInfo,
            server = host,
            port = port,
            security = queryParams["security"] ?: "none",
            sni = queryParams["sni"],
            publicKey = queryParams["pbk"],
            shortId = queryParams["sid"],
            flow = queryParams["flow"],
            transport = queryParams["type"] ?: "tcp",
            path = queryParams["path"]?.let { URLDecoder.decode(it, "UTF-8") },
            remark = remark,
            rawUri = uriStr
        )
    }

    private fun parseTrojan(uriStr: String): VlessConfig {
        val uri = URI(uriStr)
        val password = uri.userInfo ?: throw IllegalArgumentException("Missing password in Trojan URI")
        val host = uri.host ?: throw IllegalArgumentException("Missing host in Trojan URI")
        val port = if (uri.port > 0) uri.port else 443
        val remark = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "Trojan-$host"

        val queryParams = parseQuery(uri.rawQuery)

        return VlessConfig(
            protocol = "trojan",
            id = password,
            server = host,
            port = port,
            security = queryParams["security"] ?: "tls",
            sni = queryParams["sni"] ?: host,
            transport = queryParams["type"] ?: "tcp",
            path = queryParams["path"],
            remark = remark,
            rawUri = uriStr
        )
    }

    private fun parseShadowsocks(uriStr: String): VlessConfig {
        val withoutScheme = uriStr.removePrefix("ss://").removePrefix("SS://")
        val hashIndex = withoutScheme.indexOf('#')
        val encodedPart = if (hashIndex >= 0) withoutScheme.substring(0, hashIndex) else withoutScheme
        val remark = if (hashIndex >= 0) URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8") else "Shadowsocks"

        val decoded = try {
            String(Base64.getUrlDecoder().decode(encodedPart), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(Base64.getDecoder().decode(encodedPart), Charsets.UTF_8)
            } catch (_: Exception) {
                encodedPart
            }
        }

        // Format method:password@host:port
        val atIndex = decoded.lastIndexOf('@')
        if (atIndex < 0) {
            throw IllegalArgumentException("Invalid Shadowsocks credentials format")
        }
        val credentials = decoded.substring(0, atIndex)
        val hostPort = decoded.substring(atIndex + 1)
        val colon = hostPort.lastIndexOf(':')
        if (colon < 0) throw IllegalArgumentException("Invalid Shadowsocks endpoint")

        val host = hostPort.substring(0, colon)
        val port = hostPort.substring(colon + 1).toIntOrNull() ?: 8388

        return VlessConfig(
            protocol = "ss",
            id = credentials,
            server = host,
            port = port,
            security = "none",
            remark = remark,
            rawUri = uriStr
        )
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split('&').mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
    }

    fun validate(rawUri: String): ValidationResult {
        return try {
            parse(rawUri)
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("URI parsing error: ${e.message}")
        }
    }
}
