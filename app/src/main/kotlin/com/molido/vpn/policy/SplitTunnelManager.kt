package com.molido.vpn.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

object SplitTunnelManager {

    fun applyToVpnBuilder(builder: VpnService.Builder, disallowedPackages: Set<String>) {
        for (pkg in disallowedPackages) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
                // Package not found or not eligible
            }
        }
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        return packages.mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val name = pm.getApplicationLabel(appInfo).toString()
            AppInfo(info.packageName, name, isSystem)
        }.sortedBy { it.appName.lowercase() }
    }
}

object KillSwitchManager {

    fun applyToVpnBuilder(builder: VpnService.Builder, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBlocking(true)
        }
    }

    fun createVpnSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VPN_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
