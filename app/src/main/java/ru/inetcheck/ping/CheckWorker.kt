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
            wifi?.let { (status, focus) ->
                repo.lastStatusWifi = status
                repo.lastFocusStatusWifi = focus
                repo.lastCheckedAtWifi = now
                history.append(now, status, NetworkType.WIFI, focus)
            }
            mobile?.let { (status, focus) ->
                repo.lastStatusMobile = status
                repo.lastFocusStatusMobile = focus
                repo.lastCheckedAtMobile = now
                history.append(now, status, NetworkType.MOBILE, focus)
            }
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
        }
        return Result.success()
    }

    /**
     * Runs the main probe (global / whitelist) and the focus probe in parallel
     * over the given network, returning (mainStatus, focusStatus).
     */
    private suspend fun evaluate(repo: HostsRepository, network: Network): Pair<Status, Status> = coroutineScope {
        val main = async { evaluateMain(repo, network) }
        val focus = async { evaluateFocus(repo, network) }
        main.await() to focus.await()
    }

    private suspend fun evaluateMain(repo: HostsRepository, network: Network): Status {
        val global = HostChecker.anyReachable(repo.globalHosts, network)
        val whitelist = if (!global) HostChecker.anyReachable(repo.whitelistHosts, network) else false
        return when {
            global -> Status.FULL
            whitelist -> Status.WHITELIST
            else -> Status.NONE
        }
    }

    private suspend fun evaluateFocus(repo: HostsRepository, network: Network): Status {
        if (repo.focusHosts.isEmpty()) return Status.UNKNOWN
        return if (HostChecker.anyReachable(repo.focusHosts, network)) Status.FULL else Status.NONE
    }
}
