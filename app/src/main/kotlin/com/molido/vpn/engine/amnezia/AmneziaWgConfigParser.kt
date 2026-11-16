package com.molido.vpn.engine.amnezia

import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.wireguard.InterfaceConfig
import com.molido.vpn.engine.wireguard.PeerConfig
import com.molido.vpn.engine.wireguard.WireGuardConfigParser
import java.util.Locale

object AmneziaWgConfigParser {

    fun parse(rawText: String): AmneziaWgConfig {
        val baseConfig = WireGuardConfigParser.parse(rawText)

        // Parse optional obfuscation parameters from [Interface]
        var jc = 4
        var jmin = 40
        var jmax = 70
        var s1 = 15
        var s2 = 20
        var h1 = 1L
        var h2 = 2L
        var h3 = 3L
        var h4 = 4L

        for (rawLine in rawText.lines()) {
            val line = rawLine.split('#', ';')[0].trim()
            val eq = line.indexOf('=')
            if (eq < 1) continue

            val key = line.substring(0, eq).trim().lowercase(Locale.ROOT)
            val value = line.substring(eq + 1).trim()

            when (key) {
                "jc" -> jc = value.toIntOrNull() ?: jc
                "jmin" -> jmin = value.toIntOrNull() ?: jmin
                "jmax" -> jmax = value.toIntOrNull() ?: jmax
                "s1" -> s1 = value.toIntOrNull() ?: s1
                "s2" -> s2 = value.toIntOrNull() ?: s2
                "h1" -> h1 = value.toLongOrNull() ?: h1
                "h2" -> h2 = value.toLongOrNull() ?: h2
                "h3" -> h3 = value.toLongOrNull() ?: h3
                "h4" -> h4 = value.toLongOrNull() ?: h4
            }
        }

        if (jmin > jmax) {
            throw IllegalArgumentException("Jmin ($jmin) cannot be greater than Jmax ($jmax)")
        }

        val obfuscation = AmneziaObfuscation(
            jc = jc.coerceIn(0, 128),
            jmin = jmin.coerceIn(0, 1420),
            jmax = jmax.coerceIn(0, 1420),
            s1 = s1.coerceIn(0, 1420),
            s2 = s2.coerceIn(0, 1420),
            h1 = h1,
            h2 = h2,
            h3 = h3,
            h4 = h4
        )

        return AmneziaWgConfig(
            interfaceConfig = baseConfig.interfaceConfig,
            peerConfig = baseConfig.peerConfig,
            obfuscation = obfuscation,
            rawText = rawText
        )
    }

    fun validate(rawText: String): ValidationResult {
        return try {
            parse(rawText)
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("AmneziaWG validation failed: ${e.message}")
        }
    }
}
