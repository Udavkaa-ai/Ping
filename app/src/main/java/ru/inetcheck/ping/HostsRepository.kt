package ru.inetcheck.ping

import android.content.Context
import androidx.core.content.edit

class HostsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var globalHosts: List<String>
        get() = prefs.getString(KEY_GLOBAL, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_GLOBAL
        set(value) = prefs.edit { putString(KEY_GLOBAL, value.joinToString("\n")) }

    var whitelistHosts: List<String>
        get() = prefs.getString(KEY_WHITELIST, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_WHITELIST
        set(value) = prefs.edit { putString(KEY_WHITELIST, value.joinToString("\n")) }

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

    var isChecking: Boolean
        get() = prefs.getBoolean(KEY_CHECKING, false)
        set(value) = prefs.edit { putBoolean(KEY_CHECKING, value) }

    fun lastStatusFor(network: NetworkType): Status = when (network) {
        NetworkType.WIFI -> lastStatusWifi
        NetworkType.MOBILE -> lastStatusMobile
        else -> Status.UNKNOWN
    }

    fun resetToDefaults() {
        globalHosts = DEFAULT_GLOBAL
        whitelistHosts = DEFAULT_WHITELIST
    }

    private fun readStatus(key: String): Status =
        Status.values().getOrNull(prefs.getInt(key, Status.UNKNOWN.ordinal)) ?: Status.UNKNOWN

    companion object {
        private const val PREFS = "ping_prefs"
        private const val KEY_GLOBAL = "global_hosts"
        private const val KEY_WHITELIST = "whitelist_hosts"
        private const val KEY_STATUS_WIFI = "last_status_wifi"
        private const val KEY_STATUS_MOBILE = "last_status_mobile"
        private const val KEY_AT_WIFI = "last_at_wifi"
        private const val KEY_AT_MOBILE = "last_at_mobile"
        private const val KEY_CHECKING = "is_checking"

        val DEFAULT_GLOBAL = listOf("google.com", "cloudflare.com", "github.com")
        val DEFAULT_WHITELIST = listOf("yandex.ru", "vk.com", "mail.ru")
    }
}
