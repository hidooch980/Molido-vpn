package com.molido.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.molido.vpn.MainActivity
import com.molido.vpn.data.EncryptedConfigStore
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.engine.EngineReadiness
import com.molido.vpn.engine.EngineRuntimeCatalog
import com.molido.vpn.engine.amnezia.AmneziaWgConfigParser
import com.molido.vpn.engine.wireguard.WireGuardConfigParser
import com.molido.vpn.network.NetworkHandoverMonitor
import com.molido.vpn.policy.KillSwitchManager
import com.molido.vpn.policy.SplitTunnelManager
import java.io.IOException

class MolidoVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.molido.vpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.molido.vpn.action.DISCONNECT"
        const val EXTRA_CONFIG_TEXT = "com.molido.vpn.extra.CONFIG_TEXT"
        const val EXTRA_ENGINE_ID = "com.molido.vpn.extra.ENGINE_ID"

        const val CHANNEL_ID = "molido_vpn_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var activeEngine: String? = null
            private set

        fun startService(context: Context, engineId: String, configText: String) {
            val intent = Intent(context, MolidoVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_ENGINE_ID, engineId)
                putExtra(EXTRA_CONFIG_TEXT, configText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MolidoVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var handoverMonitor: NetworkHandoverMonitor? = null
    private lateinit var configStore: EncryptedConfigStore

    override fun onCreate() {
        super.onCreate()
        configStore = EncryptedConfigStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val engineId = intent.getStringExtra(EXTRA_ENGINE_ID) ?: "wireguard"
                val configText = intent.getStringExtra(EXTRA_CONFIG_TEXT) ?: ""
                handleConnect(engineId, configText)
            }
            ACTION_DISCONNECT -> handleDisconnect()
        }
        return START_NOT_STICKY
    }

    private fun handleConnect(engineId: String, configText: String) {
        startForeground(NOTIFICATION_ID, buildNotification("Checking $engineId runtime..."))

        val support = EngineRuntimeCatalog.find(engineId)
        if (support == null ||
            !support.selectable ||
            support.readiness != EngineReadiness.PRODUCTION
        ) {
            // Never establish an empty/default TUN or report Connected for a
            // parser-only adapter. Real packet forwarding must be integrated
            // and release-gated before an engine can be enabled in the catalog.
            isServiceRunning = false
            activeEngine = null
            closeVpnInterface()
            updateNotification("Unavailable: $engineId engine is not production-verified")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            val pfd = establishTunInterface(engineId, configText)
            if (pfd == null) {
                handleDisconnect()
                return
            }

            vpnInterface?.close()
            vpnInterface = pfd
            isServiceRunning = true
            activeEngine = engineId
            updateNotification("Tunnel interface ready; verifying engine traffic")

            handoverMonitor?.stop()
            handoverMonitor = NetworkHandoverMonitor(this) { isWifi, isCellular ->
                val netType = if (isWifi) "Wi-Fi" else if (isCellular) "Cellular" else "Network"
                updateNotification("Engine traffic verification required ($netType)")
            }.also { it.start() }
        } catch (_: Exception) {
            handleDisconnect()
        }
    }

    private fun establishTunInterface(engineId: String, configText: String): ParcelFileDescriptor? {
        val builder = Builder().setSession("MolidoVPN AI ($engineId)")

        if (configStore.isKillSwitchEnabled()) {
            KillSwitchManager.applyToVpnBuilder(builder, true)
        }

        val splitApps = configStore.getSplitTunnelApps()
        if (splitApps.isNotEmpty()) {
            SplitTunnelManager.applyToVpnBuilder(builder, splitApps)
        }

        DohDnsResolver.applyToVpnBuilder(builder, configStore.getSelectedDns())

        when (engineId) {
            "wireguard" -> {
                val parsed = WireGuardConfigParser.parse(configText)
                builder.setMtu(parsed.interfaceConfig.mtu)
                parsed.interfaceConfig.addresses.forEach { address ->
                    val parts = address.split('/')
                    require(parts.size == 2) { "Invalid interface address" }
                    builder.addAddress(parts[0], parts[1].toInt())
                }
                parsed.peerConfig.allowedIps.forEach { route ->
                    val parts = route.split('/')
                    require(parts.size == 2) { "Invalid peer route" }
                    builder.addRoute(parts[0], parts[1].toInt())
                }
            }
            "amneziawg" -> {
                val parsed = AmneziaWgConfigParser.parse(configText)
                builder.setMtu(parsed.interfaceConfig.mtu)
                parsed.interfaceConfig.addresses.forEach { address ->
                    val parts = address.split('/')
                    require(parts.size == 2) { "Invalid interface address" }
                    builder.addAddress(parts[0], parts[1].toInt())
                }
                parsed.peerConfig.allowedIps.forEach { route ->
                    val parts = route.split('/')
                    require(parts.size == 2) { "Invalid peer route" }
                    builder.addRoute(parts[0], parts[1].toInt())
                }
            }
            // These engines need their own reviewed TUN/packet bridge setup.
            // They must not inherit a generic default route.
            else -> return null
        }

        return builder.establish()
    }

    private fun closeVpnInterface() {
        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
    }

    private fun handleDisconnect() {
        handoverMonitor?.stop()
        handoverMonitor = null
        closeVpnInterface()
        isServiceRunning = false
        activeEngine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        handleDisconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        handleDisconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MolidoVPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingLaunch = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, MolidoVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MolidoVPN AI")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnect)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
