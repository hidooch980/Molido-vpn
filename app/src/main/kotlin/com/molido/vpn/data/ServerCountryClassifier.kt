package com.molido.vpn.data

/**
 * Resolves a country only from an explicit country marker in a human-readable profile label.
 *
 * This is display metadata, not geolocation. Hostnames, IP addresses, endpoint regions,
 * arbitrary two-letter substrings and protocol payloads must never be used to infer a country.
 * Unknown or ambiguous labels remain ZZ.
 */
data class ServerCountry(val code: String, val nameFa: String, val nameEn: String, val flag: String)

object ServerCountryClassifier {
    val UNKNOWN = ServerCountry("ZZ", "نامشخص", "Unknown", "🌐")

    private val countries = listOf(
        ServerCountry("US", "آمریکا", "United States", "🇺🇸"),
        ServerCountry("DE", "آلمان", "Germany", "🇩🇪"),
        ServerCountry("NL", "هلند", "Netherlands", "🇳🇱"),
        ServerCountry("FR", "فرانسه", "France", "🇫🇷"),
        ServerCountry("GB", "بریتانیا", "United Kingdom", "🇬🇧"),
        ServerCountry("FI", "فنلاند", "Finland", "🇫🇮"),
        ServerCountry("SE", "سوئد", "Sweden", "🇸🇪"),
        ServerCountry("CH", "سوئیس", "Switzerland", "🇨🇭"),
        ServerCountry("AT", "اتریش", "Austria", "🇦🇹"),
        ServerCountry("PL", "لهستان", "Poland", "🇵🇱"),
        ServerCountry("TR", "ترکیه", "Türkiye", "🇹🇷"),
        ServerCountry("RU", "روسیه", "Russia", "🇷🇺"),
        ServerCountry("JP", "ژاپن", "Japan", "🇯🇵"),
        ServerCountry("SG", "سنگاپور", "Singapore", "🇸🇬"),
        ServerCountry("HK", "هنگ‌کنگ", "Hong Kong", "🇭🇰"),
        ServerCountry("KR", "کره جنوبی", "South Korea", "🇰🇷"),
        ServerCountry("CA", "کانادا", "Canada", "🇨🇦"),
        ServerCountry("AU", "استرالیا", "Australia", "🇦🇺"),
        ServerCountry("IR", "ایران", "Iran", "🇮🇷"),
        ServerCountry("AE", "امارات", "United Arab Emirates", "🇦🇪"),
        ServerCountry("IN", "هند", "India", "🇮🇳"),
        ServerCountry("BR", "برزیل", "Brazil", "🇧🇷"),
        ServerCountry("IT", "ایتالیا", "Italy", "🇮🇹"),
        ServerCountry("ES", "اسپانیا", "Spain", "🇪🇸"),
        ServerCountry("CZ", "جمهوری چک", "Czechia", "🇨🇿"),
        ServerCountry("RO", "رومانی", "Romania", "🇷🇴"),
        ServerCountry("BG", "بلغارستان", "Bulgaria", "🇧🇬"),
        ServerCountry("UA", "اوکراین", "Ukraine", "🇺🇦"),
        ServerCountry("NO", "نروژ", "Norway", "🇳🇴"),
        ServerCountry("DK", "دانمارک", "Denmark", "🇩🇰")
    )

    private val aliases = mapOf(
        "USA" to "US",
        "UK" to "GB",
        "UAE" to "AE",
        "Türkiye" to "TR",
        "Turkey" to "TR"
    )

    fun resolve(explicitLabel: String?): ServerCountry {
        val label = explicitLabel?.trim().orEmpty()
        if (label.isBlank()) return UNKNOWN

        countries.firstOrNull { label.contains(it.flag) }?.let { return it }

        val normalized = label.lowercase()
        countries.firstOrNull { country ->
            containsWholeToken(normalized, country.nameEn.lowercase()) ||
                containsWholeToken(normalized, country.nameFa)
        }?.let { return it }

        aliases.entries.firstOrNull { (alias, _) ->
            containsWholeToken(normalized, alias.lowercase())
        }?.value?.let { code ->
            countries.firstOrNull { it.code == code }?.let { return it }
        }

        // Accept only uppercase, standalone ISO-like markers in a label, e.g. "Node US-01".
        // Lowercase hostname fragments and arbitrary two-letter pieces are deliberately ignored.
        val code = Regex("(?<![A-Za-z])([A-Z]{2})(?![A-Za-z])")
            .find(label)?.groupValues?.getOrNull(1)
        return countries.firstOrNull { it.code == code } ?: UNKNOWN
    }

    private fun containsWholeToken(value: String, token: String): Boolean {
        if (token.isBlank()) return false
        val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(token)}(?![\\p{L}\\p{N}])")
        return pattern.containsMatchIn(value)
    }

    fun displayName(country: ServerCountry, persian: Boolean): String =
        if (persian) country.nameFa else country.nameEn

    fun sorted(values: Collection<ServerCountry>, persian: Boolean): List<ServerCountry> =
        values.distinctBy { it.code }.sortedWith(
            compareBy<ServerCountry> { it.code == UNKNOWN.code }
                .thenBy { displayName(it, persian).lowercase() }
        )
}
