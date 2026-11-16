package com.molido.vpn.engine.hysteria

data class Hysteria2Config(
    val auth: String,
    val server: String,
    val port: Int,
    val sni: String? = null,
    val insecure: Boolean = false,
    val obfsType: String? = null, // e.g., "salamander"
    val obfsPassword: String? = null,
    val pinSha256: String? = null,
    val hopPorts: String? = null, // e.g., "443,10000-20000"
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    val remark: String = "Hysteria 2 Server",
    val rawUri: String = ""
)
