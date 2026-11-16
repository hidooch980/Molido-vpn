package com.molido.vpn.engine.xray

data class VlessConfig(
    val protocol: String = "vless",
    val id: String, // UUID or password
    val server: String,
    val port: Int,
    val security: String = "none", // tls, reality, none
    val sni: String? = null,
    val publicKey: String? = null, // for Reality
    val shortId: String? = null,
    val flow: String? = null, // xtls-rprx-vision
    val transport: String = "tcp", // tcp, ws, grpc
    val path: String? = null,
    val remark: String = "VLESS Server",
    val rawUri: String = ""
)
