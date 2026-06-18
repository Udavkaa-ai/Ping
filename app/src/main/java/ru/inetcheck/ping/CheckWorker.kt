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
                // Focus probing is skipped: the tunnel bypasses DPI, so the
                // focus answer is trivially "reachable" and carries no
                // signal about whether RKN's grip has loosened on the raw
                // transport. We preserve the previously-recorded focus
                // status untouched.
                val defaultNet = NetworkRouter.defaultNetwork(applicationContext)
                if (defaultNet != null) {
                    // Some VPN setups (e.g. MIUI) hide the underlying network
                    // entirely, so activeType returns OTHER. Fall back to WIFI
                    // as a best guess — most VPN sessions ride on Wi-Fi — so
                    // the data still flows into a lane the user can read.
                    val underlying = when (NetworkRouter.activeType(applicationContext)) {
                        NetworkType.MOBILE -> NetworkType.MOBILE
                        else -> NetworkType.WIFI
                    }
                    val status = evaluateMain(repo, defaultNet)
                    writeViaVpnLane(repo, history, underlying, status, now)
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
                wifi?.let { (s, f) -> writeRawLane(repo, history, NetworkType.WIFI, s, f, now) }
                mobile?.let { (s, f) -> writeRawLane(repo, history, NetworkType.MOBILE, s, f, now) }
            }
        } finally {
            repo.isChecking = false
            WidgetProvider.renderAll(applicationContext)
            WidgetProviderSmall.renderAll(applicationContext)
        }
        return Result.success()
    }

    private fun writeRawLane(
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
        history.append(now, status, network, focus, viaVpn = false)
    }

    private fun writeViaVpnLane(
        repo: HostsRepository,
        history: HistoryRepository,
        network: NetworkType,
        status: Status,
        now: Long
    ) {
        when (network) {
            NetworkType.WIFI -> {
                repo.lastStatusWifi = status
                repo.lastCheckedAtWifi = now
                // lastFocusStatusWifi intentionally left alone
            }
            NetworkType.MOBILE -> {
                repo.lastStatusMobile = status
                repo.lastCheckedAtMobile = now
            }
            else -> return
        }
        history.append(now, status, network, Status.UNKNOWN, viaVpn = true)
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
