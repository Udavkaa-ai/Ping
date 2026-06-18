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
            val now = System.currentTimeMillis()

            if (NetworkRouter.isVpnActive(applicationContext)) {
                // VPN is in front of the default route. Going through
                // requestNetwork(WIFI/CELLULAR) would bypass the tunnel and
                // hit raw DPI, falsely reporting "blocked" everywhere. Probe
                // through the system default network — the VPN tunnel — and
                // mirror the result onto both lanes so neither card shows a
                // stale "raw transport" status while VPN is up.
                val defaultNet = NetworkRouter.defaultNetwork(applicationContext)
                if (defaultNet != null) {
                    val (status, focus) = evaluate(repo, defaultNet)
                    writeLane(repo, history, NetworkType.WIFI, status, focus, now)
                    writeLane(repo, history, NetworkType.MOBILE, status, focus, now)
                }
            } else {
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
                wifi?.let { (s, f) -> writeLane(repo, history, NetworkType.WIFI, s, f, now) }
                mobile?.let { (s, f) -> writeLane(repo, history, NetworkType.MOBILE, s, f, now) }
            }
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
            WidgetProviderSmall.renderAll(applicationContext)
        }
        return Result.success()
    }

    private fun writeLane(
        repo: HostsRepository,
        history: HistoryRepository,
        network: NetworkType,
        status: Status,
        focus: Status,
        now: Long
    ) {
        when (network) {
            NetworkType.WIFI -> {
                repo.lastStatusWifi = status
                repo.lastFocusStatusWifi = focus
                repo.lastCheckedAtWifi = now
            }
            NetworkType.MOBILE -> {
                repo.lastStatusMobile = status
                repo.lastFocusStatusMobile = focus
                repo.lastCheckedAtMobile = now
            }
            else -> return
        }
        history.append(now, status, network, focus)
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
