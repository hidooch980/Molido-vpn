package com.molido.vpn.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks validated physical networks only (never the VPN Network itself).
 * On a validated handover, the owner should re-verify its active tunnel.
 */
class NetworkHandoverMonitor(
    context: Context,
    private val onNetworkChanged: (isWifi: Boolean, isCellular: Boolean) -> Unit,
    private val onNoUsableNetwork: () -> Unit = {}
) {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val registered = AtomicBoolean(false)
    private val lock = Any()
    private val usableNetworks = LinkedHashMap<Network, Pair<Boolean, Boolean>>()
    private var lastPublished: Pair<Boolean, Boolean>? = null
    private var lastPublishedNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            val snapshot = if (usable) {
                Pair(
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                ).takeIf { it.first || it.second }
            } else null
            publish(network, snapshot)
        }

        override fun onLost(network: Network) {
            publish(network, null)
        }
    }

    fun start(): Boolean {
        if (!registered.compareAndSet(false, true)) return true
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        return try {
            cm.registerNetworkCallback(request, callback)
            true
        } catch (_: Exception) {
            registered.set(false)
            false
        }
    }

    private fun publish(network: Network, snapshot: Pair<Boolean, Boolean>?) {
        val result = synchronized(lock) {
            val hadUsableNetwork = usableNetworks.isNotEmpty()
            if (snapshot == null) usableNetworks.remove(network)
            else usableNetworks[network] = snapshot
            val selectedNetwork = usableNetworks.keys.lastOrNull()
            val selected = selectedNetwork?.let { usableNetworks[it] }
            val changed = selectedNetwork != lastPublishedNetwork || selected != lastPublished
            lastPublishedNetwork = selectedNetwork
            lastPublished = selected
            Triple(selected, changed, hadUsableNetwork && selected == null && changed)
        }
        if (result.second && result.first != null) {
            runCatching { onNetworkChanged(result.first!!.first, result.first!!.second) }
        } else if (result.third) {
            runCatching { onNoUsableNetwork() }
        }
    }

    fun stop() {
        if (registered.compareAndSet(true, false)) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                // Registration is still cleared locally so a later start can retry.
            }
        }
        synchronized(lock) {
            usableNetworks.clear()
            lastPublished = null
            lastPublishedNetwork = null
        }
    }
}
