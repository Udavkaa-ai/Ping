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
        val history = HistoryRepository(applicationContext)
        try {
            val network = NetworkProbe.current(applicationContext)
            val status = if (network == NetworkType.NONE) {
                Status.NONE
            } else {
                val global = HostChecker.anyReachable(repo.globalHosts)
                val whitelist = if (!global) HostChecker.anyReachable(repo.whitelistHosts) else false
                when {
                    global -> Status.FULL
                    whitelist -> Status.WHITELIST
                    else -> Status.NONE
                }
            }
            val now = System.currentTimeMillis()
            when (network) {
                NetworkType.WIFI -> {
                    repo.lastStatusWifi = status
                    repo.lastCheckedAtWifi = now
                }
                NetworkType.MOBILE -> {
                    repo.lastStatusMobile = status
                    repo.lastCheckedAtMobile = now
                }
                else -> Unit
            }
            history.append(now, status, network)
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
        }
        return Result.success()
    }
}
