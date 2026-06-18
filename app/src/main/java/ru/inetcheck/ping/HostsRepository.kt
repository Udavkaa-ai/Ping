package ru.inetcheck.ping

import android.content.Context
import androidx.core.content.edit

class HostsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var globalHosts: List<String>
        get() = readList(KEY_GLOBAL, DEFAULT_GLOBAL)
        set(value) = writeList(KEY_GLOBAL, value)

    var whitelistHosts: List<String>
        get() = readList(KEY_WHITELIST, DEFAULT_WHITELIST)
        set(value) = writeList(KEY_WHITELIST, value)

    var focusHosts: List<String>
        get() = readList(KEY_FOCUS, DEFAULT_FOCUS)
        set(value) = writeList(KEY_FOCUS, value)

    var lastStatusWifi: Status
        get() = readStatus(KEY_STATUS_WIFI)
        set(value) = prefs.edit { putInt(KEY_STATUS_WIFI, value.ordinal) }

    var lastStatusMobile: Status
        get() = readStatus(KEY_STATUS_MOBILE)
        set(value) = prefs.edit { putInt(KEY_STATUS_MOBILE, value.ordinal) }

    var lastCheckedAtWifi: Long
        get() = prefs.getLong(KEY_AT_WIFI, 0L)
        set(value) = prefs.edit { putLong(KEY_AT_WIFI, value) }

    var lastCheckedAtMobile: Long
        get() = prefs.getLong(KEY_AT_MOBILE, 0L)
        set(value) = prefs.edit { putLong(KEY_AT_MOBILE, value) }

    var lastStatusVpn: Status
        get() = readStatus(KEY_STATUS_VPN)
        set(value) = prefs.edit { putInt(KEY_STATUS_VPN, value.ordinal) }

    var lastCheckedAtVpn: Long
        get() = prefs.getLong(KEY_AT_VPN, 0L)
        set(value) = prefs.edit { putLong(KEY_AT_VPN, value) }

    var lastFocusStatusWifi: Status
        get() = readStatus(KEY_FOCUS_STATUS_WIFI)
        set(value) = prefs.edit { putInt(KEY_FOCUS_STATUS_WIFI, value.ordinal) }

    var lastFocusStatusMobile: Status
        get() = readStatus(KEY_FOCUS_STATUS_MOBILE)
        set(value) = prefs.edit { putInt(KEY_FOCUS_STATUS_MOBILE, value.ordinal) }

    var isChecking: Boolean
        get() = prefs.getBoolean(KEY_CHECKING, false)
        set(value) = prefs.edit { putBoolean(KEY_CHECKING, value) }

    fun lastStatusFor(network: NetworkType): Status = when (network) {
        NetworkType.WIFI -> lastStatusWifi
        NetworkType.MOBILE -> lastStatusMobile
        NetworkType.VPN -> lastStatusVpn
        else -> Status.UNKNOWN
    }

    fun lastFocusStatusFor(network: NetworkType): Status = when (network) {
        NetworkType.WIFI -> lastFocusStatusWifi
        NetworkType.MOBILE -> lastFocusStatusMobile
        // Focus through VPN trivially passes (the tunnel bypasses DPI), so we
        // don't bother tracking it — always UNKNOWN.
        else -> Status.UNKNOWN
    }

    fun resetToDefaults() {
        globalHosts = DEFAULT_GLOBAL
        whitelistHosts = DEFAULT_WHITELIST
        focusHosts = DEFAULT_FOCUS
    }

    private fun readList(key: String, default: List<String>): List<String> =
        prefs.getString(key, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: default

    private fun writeList(key: String, value: List<String>) =
        prefs.edit { putString(key, value.joinToString("\n")) }

    private fun readStatus(key: String): Status =
        Status.values().getOrNull(prefs.getInt(key, Status.UNKNOWN.ordinal)) ?: Status.UNKNOWN

    companion object {
        private const val PREFS = "ping_prefs"
        private const val KEY_GLOBAL = "global_hosts"
        private const val KEY_WHITELIST = "whitelist_hosts"
        private const val KEY_FOCUS = "focus_hosts"
        private const val KEY_STATUS_WIFI = "last_status_wifi"
        private const val KEY_STATUS_MOBILE = "last_status_mobile"
        private const val KEY_AT_WIFI = "last_at_wifi"
        private const val KEY_AT_MOBILE = "last_at_mobile"
        private const val KEY_STATUS_VPN = "last_status_vpn"
        private const val KEY_AT_VPN = "last_at_vpn"
        private const val KEY_FOCUS_STATUS_WIFI = "last_focus_status_wifi"
        private const val KEY_FOCUS_STATUS_MOBILE = "last_focus_status_mobile"
        private const val KEY_CHECKING = "is_checking"

        val DEFAULT_GLOBAL = listOf("google.com", "cloudflare.com", "github.com")
        val DEFAULT_WHITELIST = listOf("yandex.ru", "vk.com", "mail.ru")
        val DEFAULT_FOCUS = listOf("telegram.org", "youtube.com", "instagram.com")
    }
}
