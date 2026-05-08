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

    var lastStatus: Status
        get() = Status.values().getOrNull(prefs.getInt(KEY_STATUS, Status.UNKNOWN.ordinal))
            ?: Status.UNKNOWN
        set(value) = prefs.edit { putInt(KEY_STATUS, value.ordinal) }

    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_AT, value) }

    var isChecking: Boolean
        get() = prefs.getBoolean(KEY_CHECKING, false)
        set(value) = prefs.edit { putBoolean(KEY_CHECKING, value) }

    fun resetToDefaults() {
        globalHosts = DEFAULT_GLOBAL
        whitelistHosts = DEFAULT_WHITELIST
    }

    companion object {
        private const val PREFS = "ping_prefs"
        private const val KEY_GLOBAL = "global_hosts"
        private const val KEY_WHITELIST = "whitelist_hosts"
        private const val KEY_STATUS = "last_status"
        private const val KEY_AT = "last_at"
        private const val KEY_CHECKING = "is_checking"

        val DEFAULT_GLOBAL = listOf("google.com", "cloudflare.com", "github.com")
        val DEFAULT_WHITELIST = listOf("yandex.ru", "vk.com", "mail.ru")
    }
}
