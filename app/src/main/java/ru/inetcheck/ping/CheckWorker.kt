package ru.inetcheck.ping

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = HostsRepository(applicationContext)
        try {
            val global = HostChecker.anyReachable(repo.globalHosts)
            val whitelist = if (!global) HostChecker.anyReachable(repo.whitelistHosts) else false
            repo.lastStatus = when {
                global -> Status.FULL
                whitelist -> Status.WHITELIST
                else -> Status.NONE
            }
            repo.lastCheckedAt = System.currentTimeMillis()
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
        }
        return Result.success()
    }
}
