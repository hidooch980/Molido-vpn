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
    private var tunPumpThread: Thread? = null
    @Volatile
    private var isPumpRunning = false

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
            ACTION_DISCONNECT -> {
                handleDisconnect()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleConnect(engineId: String, configText: String) {
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Connecting to $engineId..."))

            val pfd = establishTunInterface(engineId, configText)
            if (pfd != null) {
                vpnInterface?.close()
                vpnInterface = pfd
                isServiceRunning = true
                activeEngine = engineId
                updateNotification("Connected via $engineId")

                startTunPacketPump(pfd)

                // Start Handover Monitor
                handoverMonitor?.stop()
                handoverMonitor = NetworkHandoverMonitor(this) { isWifi, isCellular ->
                    val netType = if (isWifi) "Wi-Fi" else if (isCellular) "Cellular" else "Network"
                    updateNotification("Connected via $engineId ($netType)")
                }.also { it.start() }
            } else {
                handleDisconnect()
            }
        } catch (_: Exception) {
            handleDisconnect()
        }
    }

    private fun establishTunInterface(engineId: String, configText: String): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("MolidoVPN ($engineId)")

        // Apply Kill Switch if enabled
        if (configStore.isKillSwitchEnabled()) {
            KillSwitchManager.applyToVpnBuilder(builder, true)
        }

        // Apply Split Tunneling exclusions
        val splitApps = configStore.getSplitTunnelApps()
        if (splitApps.isNotEmpty()) {
            SplitTunnelManager.applyToVpnBuilder(builder, splitApps)
        }

        // Apply Custom / DoH DNS
        val selectedDns = configStore.getSelectedDns()
        DohDnsResolver.applyToVpnBuilder(builder, selectedDns)

        // Protocol-specific TUN interface setup
        when (engineId) {
            "wireguard", "warp" -> {
                try {
                    val parsed = WireGuardConfigParser.parse(configText)
                    builder.setMtu(parsed.interfaceConfig.mtu)

                    for (addr in parsed.interfaceConfig.addresses) {
                        val parts = addr.split('/')
                        if (parts.size == 2) {
                            val ip = parts[0]
                            val prefix = parts[1].toIntOrNull() ?: 32
                            builder.addAddress(ip, prefix)
                        }
                    }

                    for (allowedIp in parsed.peerConfig.allowedIps) {
                        val parts = allowedIp.split('/')
                        if (parts.size == 2) {
                            val ip = parts[0]
                            val prefix = parts[1].toIntOrNull() ?: 0
                            builder.addRoute(ip, prefix)
                        }
                    }
                } catch (_: Exception) {
                    applyDefaultTunParameters(builder)
                }
            }
            "amnezia" -> {
                try {
                    val parsed = AmneziaWgConfigParser.parse(configText)
                    builder.setMtu(parsed.interfaceConfig.mtu)

                    for (addr in parsed.interfaceConfig.addresses) {
                        val parts = addr.split('/')
                        if (parts.size == 2) {
                            val ip = parts[0]
                            val prefix = parts[1].toIntOrNull() ?: 32
                            builder.addAddress(ip, prefix)
                        }
                    }
                    builder.addRoute("0.0.0.0", 0)
                } catch (_: Exception) {
                    applyDefaultTunParameters(builder)
                }
            }
            "xray" -> {
                // Xray tun mode route mapping
                builder.setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
            }
            else -> {
                applyDefaultTunParameters(builder)
            }
        }

        return builder.establish()
    }

    private fun applyDefaultTunParameters(builder: Builder) {
        builder.setMtu(1420)
            .addAddress("10.200.0.2", 32)
            .addRoute("0.0.0.0", 0)
    }

    private fun handleDisconnect() {
        stopTunPacketPump()
        handoverMonitor?.stop()
        handoverMonitor = null
        try {
            vpnInterface?.close()
        } catch (_: IOException) {}
        vpnInterface = null
        isServiceRunning = false
        activeEngine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTunPacketPump(pfd: ParcelFileDescriptor) {
        stopTunPacketPump()
        isPumpRunning = true
        tunPumpThread = Thread {
            val buffer = ByteArray(32768)
            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
            try {
                while (isPumpRunning && !Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            name = "MolidoTunPump"
            start()
        }
    }

    private fun stopTunPacketPump() {
        isPumpRunning = false
        tunPumpThread?.interrupt()
        tunPumpThread = null
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
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, MolidoVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MolidoVPN")
            .setContentText(statusText)
            .setSmallIcon(com.molido.vpn.R.drawable.ic_notification)
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
