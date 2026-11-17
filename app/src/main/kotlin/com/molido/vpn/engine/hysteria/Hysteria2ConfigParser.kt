package com.molido.vpn.engine.hysteria

import com.molido.vpn.engine.ValidationResult
import java.net.URI

/**
 * Syntax-only validator for Hysteria 2 share URIs.
 *
 * This does not create a transport or certify a server. The adapter remains
 * fail-closed until a native Hysteria 2 runtime and TUN forwarding are added.
 */
internal object Hysteria2ConfigParser {
    fun validate(raw: String): ValidationResult {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it.isISOControl() || it.isWhitespace() }) {
            return ValidationResult.Invalid("HYSTERIA2_CONFIG_INVALID")
        }

        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "hysteria2" && scheme != "hy2") {
                return ValidationResult.Invalid("HYSTERIA2_SCHEME_INVALID")
            }
            if (uri.host.isNullOrBlank() || uri.port !in 1..65535) {
                return ValidationResult.Invalid("HYSTERIA2_ENDPOINT_INVALID")
            }
            if (uri.rawUserInfo.isNullOrBlank()) {
                return ValidationResult.Invalid("HYSTERIA2_AUTH_MISSING")
            }
            ValidationResult.Valid
        } catch (_: Exception) {
            ValidationResult.Invalid("HYSTERIA2_CONFIG_INVALID")
        }
    }
}
