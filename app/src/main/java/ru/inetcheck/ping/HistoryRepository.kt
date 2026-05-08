package ru.inetcheck.ping

import android.content.Context
import androidx.core.content.edit

class HistoryRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun append(time: Long, status: Status) {
        val cutoff = System.currentTimeMillis() - RETAIN_MS
        val updated = (load() + Entry(time, status))
            .filter { it.time >= cutoff }
            .sortedBy { it.time }
        prefs.edit {
            putString(KEY, updated.joinToString("\n") { "${it.time},${it.status.ordinal}" })
        }
    }

    fun load(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 2) return@mapNotNull null
            val t = parts[0].toLongOrNull() ?: return@mapNotNull null
            val s = Status.values().getOrNull(parts[1].toIntOrNull() ?: -1) ?: return@mapNotNull null
            Entry(t, s)
        }
    }

    data class Entry(val time: Long, val status: Status)

    companion object {
        private const val PREFS = "ping_history"
        private const val KEY = "entries"
        private const val RETAIN_MS = 25L * 60 * 60 * 1000L
    }
}
