package ru.inetcheck.ping

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object PingAlarm {
    const val INTERVAL_MS = 15L * 60 * 1000
    const val ACTION_ALARM = "ru.inetcheck.ping.ACTION_ALARM"
    private const val REQ_ALARM = 100

    /**
     * Schedules the next inexact alarm. setAndAllowWhileIdle fires through
     * Doze (with a system-imposed ~9 min minimum gap, well below our 15 min
     * cycle) and does NOT require SCHEDULE_EXACT_ALARM permission. The chain
     * keeps itself alive: every AlarmReceiver firing schedules the following
     * tick before doing the work.
     */
    fun scheduleNext(context: Context) {
        val am = context.applicationContext
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.applicationContext
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context, REQ_ALARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
