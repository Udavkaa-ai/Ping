package ru.inetcheck.ping

import android.app.Application
import androidx.work.WorkManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cancel the legacy WorkManager periodic schedule from older builds.
        // PeriodicWorkRequest gets aggressively deferred by Doze on stock
        // Android and outright killed by MIUI's battery saver overnight.
        runCatching {
            WorkManager.getInstance(this).cancelUniqueWork(LEGACY_PERIODIC_WORK)
        }
        PingAlarm.scheduleNext(this)
    }

    companion object {
        private const val LEGACY_PERIODIC_WORK = "periodic_check"
    }
}
