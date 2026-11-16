package com.molido.vpn.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Base64

class EncryptedConfigStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveActiveConfig(configText: String) {
        val encoded = Base64.getEncoder().encodeToString(configText.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_ACTIVE_CONFIG, encoded).apply()
    }

    fun getActiveConfig(): String? {
        val encoded = prefs.getString(KEY_ACTIVE_CONFIG, null) ?: return null
        return try {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: Exception) {
            encoded
        }
    }

    fun saveSelectedEngine(engineId: String) {
        prefs.edit().putString(KEY_ENGINE_ID, engineId).apply()
    }

    fun getSelectedEngine(): String {
        return prefs.getString(KEY_ENGINE_ID, "wireguard") ?: "wireguard"
    }

    fun saveKillSwitchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    fun isKillSwitchEnabled(): Boolean {
        return prefs.getBoolean(KEY_KILL_SWITCH, false)
    }

    fun saveSelectedDns(dnsId: String) {
        prefs.edit().putString(KEY_SELECTED_DNS, dnsId).apply()
    }

    fun getSelectedDns(): String {
        return prefs.getString(KEY_SELECTED_DNS, "cloudflare") ?: "cloudflare"
    }

    fun saveSplitTunnelApps(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_SPLIT_APPS, packageNames).apply()
    }

    fun getSplitTunnelApps(): Set<String> {
        return prefs.getStringSet(KEY_SPLIT_APPS, emptySet()) ?: emptySet()
    }

    fun saveSubscriptions(urls: List<String>) {
        prefs.edit().putStringSet(KEY_SUBSCRIPTIONS, urls.toSet()).apply()
    }

    fun getSubscriptions(): List<String> {
        return prefs.getStringSet(KEY_SUBSCRIPTIONS, emptySet())?.toList() ?: emptyList()
    }

    companion object {
        private const val PREFS_NAME = "molido_secure_config"
        private const val KEY_ACTIVE_CONFIG = "active_config_payload"
        private const val KEY_ENGINE_ID = "selected_engine_id"
        private const val KEY_KILL_SWITCH = "kill_switch_enabled"
        private const val KEY_SELECTED_DNS = "selected_dns_provider"
        private const val KEY_SPLIT_APPS = "split_tunnel_apps"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_urls"
    }
}
