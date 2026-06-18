package ru.inetcheck.ping

import android.content.Context
import androidx.core.content.edit

class HistoryRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun append(time: Long, status: Status, network: NetworkType, viaVpn: Boolean) {
        val cutoff = System.currentTimeMillis() - RETAIN_MS
        val updated = (load() + Entry(time, status, network, Status.UNKNOWN, viaVpn))
            .filter { it.time >= cutoff }
            .sortedBy { it.time }
        prefs.edit {
            putString(KEY, updated.joinToString("\n") { encode(it) })
        }
    }

    fun load(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').mapNotNull { decode(it) }
    }

    fun availabilityPercent(network: NetworkType, windowMs: Long = DAY_MS): Int? {
        val cutoff = System.currentTimeMillis() - windowMs
        val matches = load().filter { it.network == network && it.time >= cutoff }
        if (matches.isEmpty()) return null
        val good = matches.count { it.status == Status.FULL || it.status == Status.WHITELIST }
        return good * 100 / matches.size
    }

    private fun encode(e: Entry) =
        "${e.time},${e.status.ordinal},${e.network.ordinal},${e.focus.ordinal},${if (e.viaVpn) 1 else 0}"

    private fun decode(line: String): Entry? {
        val parts = line.split(',')
        if (parts.size < 2) return null
        val t = parts[0].toLongOrNull() ?: return null
        val s = Status.values().getOrNull(parts[1].toIntOrNull() ?: -1) ?: return null
        val n = if (parts.size >= 3) {
            NetworkType.values().getOrNull(parts[2].toIntOrNull() ?: -1) ?: NetworkType.OTHER
        } else NetworkType.OTHER
        val f = if (parts.size >= 4) {
            Status.values().getOrNull(parts[3].toIntOrNull() ?: -1) ?: Status.UNKNOWN
        } else Status.UNKNOWN
        val v = if (parts.size >= 5) parts[4] == "1" else false
        return Entry(t, s, n, f, v)
    }

    /**
     * Each check produces one Entry. status is the probe verdict; network is
     * the underlying transport the check ran over (Wi-Fi or Mobile — even
     * when routed through a VPN the entry is attributed to the underlying
     * transport); viaVpn marks whether the probe went through a VPN tunnel,
     * which the chart renders as a thin overlay strip on the bar.
     *
     * The focus field is a leftover from a removed "блокированные сайты"
     * category — kept on the data class to keep older CSV rows decodable
     * (position-4 in the legacy schema), but new entries always write
     * UNKNOWN and no UI reads it.
     */
    data class Entry(
        val time: Long,
        val status: Status,
        val network: NetworkType,
        val focus: Status,
        val viaVpn: Boolean = false
    )

    companion object {
        private const val PREFS = "ping_history"
        private const val KEY = "entries"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RETAIN_MS = 25L * 60 * 60 * 1000
    }
}
