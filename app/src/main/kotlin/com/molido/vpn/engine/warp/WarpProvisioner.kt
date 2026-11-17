package com.molido.vpn.engine.warp

import com.molido.vpn.engine.wireguard.InterfaceConfig
import com.molido.vpn.engine.wireguard.PeerConfig
import com.molido.vpn.engine.wireguard.WireGuardConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Provisions Cloudflare WARP WireGuard credentials.
 *
 * [generateFreeProfile] creates a local WireGuard config with a fresh keypair
 * (used for TUN interface setup).
 *
 * [registerWithCloudflare] performs the actual device registration against the
 * Cloudflare WARP API so the generated key is authorized on their network.
 * The VPN service should call this at connect time when no cached registration exists.
 */
object WarpProvisioner {
    const val WARP_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    const val WARP_ENDPOINT_IPV4 = "162.159.192.1"
    const val WARP_ENDPOINT_PORT = 2408

    private const val REGISTRATION_URL = "https://api.cloudflareclient.com/v0a977/reg"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun generateFreeProfile(privateKeyBase64: String? = null): WireGuardConfig {
        val privateKey = privateKeyBase64 ?: generateRandomPrivateKey()

        val iface = InterfaceConfig(
            privateKey = privateKey,
            addresses = listOf("172.16.0.2/32", "2606:4700:110:8735:6690:c95a:e623:8710/128"),
            dnsServers = listOf("1.1.1.1", "1.0.0.1"),
            mtu = 1280
        )

        val peer = PeerConfig(
            publicKey = WARP_PUBLIC_KEY,
            endpointHost = WARP_ENDPOINT_IPV4,
            endpointPort = WARP_ENDPOINT_PORT,
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            persistentKeepalive = 25
        )

        val rawText = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = 172.16.0.2/32, 2606:4700:110:8735:6690:c95a:e623:8710/128")
            appendLine("DNS = 1.1.1.1, 1.0.0.1")
            appendLine("MTU = 1280")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $WARP_PUBLIC_KEY")
            appendLine("Endpoint = $WARP_ENDPOINT_IPV4:$WARP_ENDPOINT_PORT")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("PersistentKeepalive = 25")
        }

        return WireGuardConfig(iface, peer, rawText)
    }

    /**
     * Registers a device with Cloudflare WARP API and returns a [WarpRegistrationResult]
     * containing the authorized config. Call this at connect time if no cached
     * registration exists.
     *
     * @param privateKeyBase64 Optional existing private key; a fresh one is generated if null.
     * @return [WarpRegistrationResult] with server-assigned addresses, endpoint, and key pair.
     * @throws WarpRegistrationException on network or API errors.
     */
    suspend fun registerWithCloudflare(
        privateKeyBase64: String? = null
    ): WarpRegistrationResult = withContext(Dispatchers.IO) {
        val privateKey = privateKeyBase64 ?: generateRandomPrivateKey()
        val publicKey = derivePublicKey(privateKey)

        val payload = JSONObject().apply {
            put("key", publicKey)
            put("install_id", "")
            put("fcm_token", "")
            put("tos", Instant.now().toString())
            put("model", "Android")
            put("type", "Android")
        }

        val requestBody = payload.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(REGISTRATION_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("User-Agent", "okhttp/4.12.0")
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw WarpRegistrationException("Network error during WARP registration: ${e.message}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw WarpRegistrationException(
                    "WARP registration failed with HTTP ${resp.code}: ${resp.message}"
                )
            }

            val body = resp.body?.string()
                ?: throw WarpRegistrationException("Empty response from WARP API")

            val json = JSONObject(body)
            val config = json.optJSONObject("config")
                ?: throw WarpRegistrationException("Missing 'config' in WARP API response")

            val interfaceObj = config.optJSONObject("interface")
            val peerObj = config.optJSONObject("peers")?.optJSONObject("0")
                ?: config.optJSONArray("peers")?.optJSONObject(0)

            val addresses = interfaceObj?.let { iface ->
                val v4 = iface.optJSONObject("addresses")?.optString("v4", "172.16.0.2/32") ?: "172.16.0.2/32"
                val v6 = iface.optJSONObject("addresses")?.optString("v6") ?: ""
                listOfNotNull(v4, v6.ifBlank { null })
            } ?: listOf("172.16.0.2/32")

            val serverPublicKey = peerObj?.optString("public_key", WARP_PUBLIC_KEY) ?: WARP_PUBLIC_KEY
            val endpoint = peerObj?.optString("endpoint")?.let { ep ->
                val host = ep.substringBeforeLast(":")
                val port = ep.substringAfterLast(":").toIntOrNull() ?: WARP_ENDPOINT_PORT
                host to port
            } ?: (WARP_ENDPOINT_IPV4 to WARP_ENDPOINT_PORT)

            val deviceId = json.optString("id", "")
            val token = json.optString("token", "")

            val registeredConfig = generateFreeProfile(privateKey).let { base ->
                val iface = base.interfaceConfig.copy(addresses = addresses)
                val peer = base.peerConfig.copy(
                    publicKey = serverPublicKey,
                    endpointHost = endpoint.first,
                    endpointPort = endpoint.second
                )
                val rawText = buildString {
                    appendLine("[Interface]")
                    appendLine("PrivateKey = $privateKey")
                    appendLine("Address = ${addresses.joinToString(", ")}")
                    appendLine("DNS = 1.1.1.1, 1.0.0.1")
                    appendLine("MTU = 1280")
                    appendLine()
                    appendLine("[Peer]")
                    appendLine("PublicKey = $serverPublicKey")
                    appendLine("Endpoint = ${endpoint.first}:${endpoint.second}")
                    appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
                    appendLine("PersistentKeepalive = 25")
                }
                WireGuardConfig(iface, peer, rawText)
            }

            WarpRegistrationResult(
                deviceId = deviceId,
                token = token,
                privateKey = privateKey,
                publicKey = publicKey,
                config = registeredConfig
            )
        }
    }

    /**
     * Derives the Curve25519 public key from a base64-encoded private key.
     * Uses a minimal pure implementation since we only need the base point multiply.
     */
    private fun derivePublicKey(privateKeyBase64: String): String {
        // For Curve25519 public key derivation we need the private key bytes
        // and perform scalar multiplication with the base point.
        // Android API 33+ has java.security X25519, but for broader compat
        // we use the clamped private key directly — the WireGuard library
        // that runs the tunnel also handles this. For registration purposes,
        // we do a simple derivation.
        val privateBytes = Base64.getDecoder().decode(privateKeyBase64)
        // Clamp (should already be clamped from generation, but be safe)
        privateBytes[0] = (privateBytes[0].toInt() and 248).toByte()
        privateBytes[31] = (privateBytes[31].toInt() and 127 or 64).toByte()

        val publicBytes = Curve25519.scalarMultBase(privateBytes)
        return Base64.getEncoder().encodeToString(publicBytes)
    }

    private fun generateRandomPrivateKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        // Clamp bytes for Curve25519
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = (bytes[31].toInt() and 127 or 64).toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }
}

/**
 * Minimal RFC 7748 Curve25519 scalar multiplication implementation
 * using standard Java BigInteger. Works across all Android API levels and JVMs.
 */
internal object Curve25519 {
    private val P = java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19))
    private val A24 = java.math.BigInteger.valueOf(121665)
    private val U_BASE = java.math.BigInteger.valueOf(9)

    fun scalarMultBase(clampedScalar: ByteArray): ByteArray {
        var x1 = U_BASE
        var x2 = java.math.BigInteger.ONE
        var z2 = java.math.BigInteger.ZERO
        var x3 = U_BASE
        var z3 = java.math.BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val byteIdx = t / 8
            val bitIdx = t % 8
            val kt = (clampedScalar[byteIdx].toInt() shr bitIdx) and 1
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = (x2 + z2).mod(P)
            val aa = (a * a).mod(P)
            val b = (x2 - z2).mod(P)
            val bb = (b * b).mod(P)
            val e = (aa - bb).mod(P)
            val c = (x3 + z3).mod(P)
            val d = (x3 - z3).mod(P)
            val da = (d * a).mod(P)
            val cb = (c * b).mod(P)

            val daPlusCb = (da + cb).mod(P)
            val daMinusCb = (da - cb).mod(P)
            x3 = (daPlusCb * daPlusCb).mod(P)
            z3 = (x1 * (daMinusCb * daMinusCb)).mod(P)
            x2 = (aa * bb).mod(P)
            z2 = (e * (aa + A24 * e)).mod(P)
        }

        if (swap == 1) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val resultInt = (x2 * z2.modInverse(P)).mod(P)
        val resultBytes = ByteArray(32)
        val intBytes = resultInt.toByteArray()
        // BigInteger is big-endian, we need little-endian 32 bytes
        var i = 0
        var j = intBytes.size - 1
        while (j >= 0 && i < 32) {
            resultBytes[i++] = intBytes[j--]
        }
        return resultBytes
    }
}

data class WarpRegistrationResult(
    val deviceId: String,
    val token: String,
    val privateKey: String,
    val publicKey: String,
    val config: WireGuardConfig
)

class WarpRegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
