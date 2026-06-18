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
                // VPN is in front of the default route. Probe through the
                // tunnel (default network) and attribute the result to the
                // underlying transport's lane with viaVpn = true so the
                // chart can mark this bucket as "checked through VPN".
                val defaultNet = NetworkRouter.defaultNetwork(applicationContext)
                if (defaultNet != null) {
                    // MIUI sometimes hides the underlying transport while a
                    // VPN is up — activeType then returns OTHER. Fall back
                    // to WIFI (most common case) so data still lands in a
                    // lane the UI reads.
                    val underlying = when (NetworkRouter.activeType(applicationContext)) {
                        NetworkType.MOBILE -> NetworkType.MOBILE
                        else -> NetworkType.WIFI
                    }
                    val status = evaluate(repo, defaultNet)
                    writeLane(repo, history, underlying, status, now, viaVpn = true)
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
                wifi?.let { s -> writeLane(repo, history, NetworkType.WIFI, s, now, viaVpn = false) }
                mobile?.let { s -> writeLane(repo, history, NetworkType.MOBILE, s, now, viaVpn = false) }
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
        now: Long,
        viaVpn: Boolean
    ) {
        when (network) {
            NetworkType.WIFI -> {
                repo.lastStatusWifi = status
                repo.lastCheckedAtWifi = now
            }
            NetworkType.MOBILE -> {
                repo.lastStatusMobile = status
                repo.lastCheckedAtMobile = now
            }
            else -> return
        }
        history.append(now, status, network, viaVpn)
    }

    /**
     * Returns Status.FULL when at least one "global" host responds,
     * Status.WHITELIST when only the Russian whitelist answers, Status.NONE
     * when nothing reachable.
     */
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
