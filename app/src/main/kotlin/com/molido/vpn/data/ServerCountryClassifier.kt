package com.molido.vpn.data

/**
 * Resolves country only from an explicit country marker in a profile label.
 * It intentionally does not infer geography from IP ranges or server hostnames.
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
        ServerCountry("UK", "بریتانیا", "United Kingdom", "🇬🇧"),
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

    fun resolve(explicitLabel: String?): ServerCountry {
        val label = explicitLabel?.trim().orEmpty()
        if (label.isBlank()) return UNKNOWN
        val normalized = label.lowercase()
        val byName = countries.firstOrNull { country ->
            normalized.contains(country.nameEn.lowercase()) ||
                normalized.contains(country.nameFa.lowercase())
        }
        if (byName != null) return byName
        val code = Regex("(?:^|[^A-Za-z])([A-Za-z]{2})(?:$|[^A-Za-z])")
            .find(label)?.groupValues?.getOrNull(1)?.uppercase()
        return countries.firstOrNull { it.code == code } ?: UNKNOWN
    }

    fun displayName(country: ServerCountry, persian: Boolean): String =
        if (persian) country.nameFa else country.nameEn

    fun sorted(values: Collection<ServerCountry>, persian: Boolean): List<ServerCountry> =
        values.distinctBy { it.code }.sortedWith(
            compareBy<ServerCountry> { it.code == UNKNOWN.code }
                .thenBy { displayName(it, persian).lowercase() }
        )
}
