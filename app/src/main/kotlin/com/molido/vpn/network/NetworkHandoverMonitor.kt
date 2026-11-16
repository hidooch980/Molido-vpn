package com.molido.vpn.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.atomic.AtomicBoolean

class NetworkHandoverMonitor(
    context: Context,
    private val onNetworkChanged: (isWifi: Boolean, isCellular: Boolean) -> Unit
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val registered = AtomicBoolean(false)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (isValidated) {
                onNetworkChanged(isWifi, isCellular)
            }
        }

        override fun onLost(network: Network) {
            // Signal connection degradation
        }
    }

    fun start() {
        if (registered.compareAndSet(false, true)) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (registered.compareAndSet(true, false)) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }
}
