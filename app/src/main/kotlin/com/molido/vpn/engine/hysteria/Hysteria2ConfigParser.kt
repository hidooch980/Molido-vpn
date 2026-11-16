package com.molido.vpn.engine.hysteria

import com.molido.vpn.engine.ValidationResult
import java.net.URI
import java.net.URLDecoder

object Hysteria2ConfigParser {

    fun parse(rawUri: String): Hysteria2Config {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("hy2://", ignoreCase = true) && !trimmed.startsWith("hysteria2://", ignoreCase = true)) {
            throw IllegalArgumentException("Unsupported scheme. Expected hy2:// or hysteria2://")
        }

        // Normalize hysteria2:// to hy2:// for uniform URI parsing
        val normalizedUri = if (trimmed.startsWith("hysteria2://", ignoreCase = true)) {
            "hy2://" + trimmed.substring("hysteria2://".length)
        } else trimmed

        val uri = URI(normalizedUri)
        val auth = uri.userInfo ?: throw IllegalArgumentException("Missing auth/password in Hysteria 2 URI")
        val host = uri.host ?: throw IllegalArgumentException("Missing host in Hysteria 2 URI")
        val port = if (uri.port > 0) uri.port else 443

        val queryParams = parseQuery(uri.rawQuery)
        val remark = uri.fragment?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (_: Exception) {
                it
            }
        } ?: "$host:$port"

        val insecure = queryParams["insecure"]?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: false
        val sni = queryParams["sni"] ?: host
        val obfs = queryParams["obfs"]
        val obfsPassword = queryParams["obfs-password"]
        val pinSha256 = queryParams["pinSHA256"] ?: queryParams["pin-sha256"]
        val hopPorts = queryParams["mport"] ?: queryParams["ports"]
        val upMbps = queryParams["up"]?.filter { it.isDigit() }?.toIntOrNull()
        val downMbps = queryParams["down"]?.filter { it.isDigit() }?.toIntOrNull()

        return Hysteria2Config(
            auth = auth,
            server = host,
            port = port,
            sni = sni,
            insecure = insecure,
            obfsType = obfs,
            obfsPassword = obfsPassword,
            pinSha256 = pinSha256,
            hopPorts = hopPorts,
            upMbps = upMbps,
            downMbps = downMbps,
            remark = remark,
            rawUri = rawUri
        )
    }

    fun validate(rawUri: String): ValidationResult {
        return try {
            val config = parse(rawUri)
            if (config.auth.isBlank()) return ValidationResult.Invalid("Auth token cannot be empty")
            if (config.server.isBlank()) return ValidationResult.Invalid("Server address cannot be empty")
            if (config.port !in 1..65535) return ValidationResult.Invalid("Invalid server port: ${config.port}")
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("Hysteria 2 validation error: ${e.message}")
        }
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val parts = param.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0]
                val value = if (parts.size > 1) {
                    try {
                        URLDecoder.decode(parts[1], "UTF-8")
                    } catch (_: Exception) {
                        parts[1]
                    }
                } else ""
                result[key] = value
            }
        }
        return result
    }
}
