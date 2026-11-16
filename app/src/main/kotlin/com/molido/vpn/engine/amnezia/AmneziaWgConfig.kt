package com.molido.vpn.engine.amnezia

import com.molido.vpn.engine.wireguard.InterfaceConfig
import com.molido.vpn.engine.wireguard.PeerConfig

data class AmneziaObfuscation(
    val jc: Int = 4,
    val jmin: Int = 40,
    val jmax: Int = 70,
    val s1: Int = 15,
    val s2: Int = 20,
    val h1: Long = 1,
    val h2: Long = 2,
    val h3: Long = 3,
    val h4: Long = 4
)

data class AmneziaWgConfig(
    val interfaceConfig: InterfaceConfig,
    val peerConfig: PeerConfig,
    val obfuscation: AmneziaObfuscation = AmneziaObfuscation(),
    val rawText: String = ""
)
