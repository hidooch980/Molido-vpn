package com.molido.vpn.tile

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.molido.vpn.MainActivity
import com.molido.vpn.data.EncryptedConfigStore
import com.molido.vpn.data.FreeServerCatalog
import com.molido.vpn.vpn.MolidoVpnService

@RequiresApi(Build.VERSION_CODES.N)
class MolidoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (MolidoVpnService.isServiceRunning) {
            // Stop VPN
            MolidoVpnService.stopService(this)
            updateTileState(isActive = false)
        } else {
            // Connect VPN
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Permission required: launch app
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivityAndCollapse(launchIntent)
            } else {
                // Permission already granted: start VPN directly
                val configStore = EncryptedConfigStore(this)
                val engineId = configStore.getSelectedEngine() ?: "warp"
                val configText = configStore.getActiveConfig()
                    ?: FreeServerCatalog.getDefaultFreeServers().firstOrNull()?.rawPayload
                    ?: ""
                MolidoVpnService.startService(this, engineId, configText)
                updateTileState(isActive = true)
            }
        }
    }

    private fun updateTileState(isActive: Boolean? = null) {
        val tile = qsTile ?: return
        val running = isActive ?: MolidoVpnService.isServiceRunning

        if (running) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "MolidoVPN"
            tile.contentDescription = "MolidoVPN Connected"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "MolidoVPN"
            tile.contentDescription = "MolidoVPN Disconnected"
        }
        tile.updateTile()
    }
}
