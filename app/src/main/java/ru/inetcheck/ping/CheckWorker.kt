package ru.inetcheck.ping

import android.content.Context
import android.net.Network
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class CheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = HostsRepository(applicationContext)
        val history = HistoryRepository(applicationContext)
        try {
            val (wifi, mobile) = coroutineScope {
                val w = async {
                    NetworkRouter.probe(applicationContext, NetworkType.WIFI) { net ->
                        evaluate(repo, net)
                    }
                }
                val m = async {
                    NetworkRouter.probe(applicationContext, NetworkType.MOBILE) { net ->
                        evaluate(repo, net)
                    }
                }
                w.await() to m.await()
            }

            val now = System.currentTimeMillis()
            wifi?.let {
                repo.lastStatusWifi = it
                repo.lastCheckedAtWifi = now
                history.append(now, it, NetworkType.WIFI)
            }
            mobile?.let {
                repo.lastStatusMobile = it
                repo.lastCheckedAtMobile = now
                history.append(now, it, NetworkType.MOBILE)
            }
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
        }
        return Result.success()
    }

    private suspend fun evaluate(repo: HostsRepository, network: Network): Status {
        val global = HostChecker.anyReachable(repo.globalHosts, network)
        val whitelist = if (!global) HostChecker.anyReachable(repo.whitelistHosts, network) else false
        return when {
            global -> Status.FULL
            whitelist -> Status.WHITELIST
            else -> Status.NONE
        }
    }
}
