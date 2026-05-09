package ru.inetcheck.ping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PingAlarm.ACTION_ALARM) return
        // Reschedule first so a crash inside the worker doesn't break the chain.
        PingAlarm.scheduleNext(context)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WidgetProvider.ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CheckWorker>().build()
        )
    }
}
