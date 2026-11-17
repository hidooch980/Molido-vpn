package com.molido.vpn.policy

import android.content.Intent
import android.provider.Settings

/**
 * Opens Android's system VPN settings so the user can configure
 * Always-on VPN and "Block connections without VPN" (Lockdown).
 *
 * The app cannot enable or verify those system-owned settings itself.
 */
object KillSwitchManager {
    fun createVpnSettingsIntent(): Intent = Intent(Settings.ACTION_VPN_SETTINGS)
}
