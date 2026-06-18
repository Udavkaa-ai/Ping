package ru.inetcheck.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine

object NetworkRouter {
    private const val WAIT_TIMEOUT_MS = 5000

    /**
     * True when the system default route currently goes through a VPN tunnel.
     * Detected via TRANSPORT_VPN on the active network, with a fallback to the
     * NOT_VPN capability for older / non-standard Android builds.
     */
    fun isVpnActive(context: Context): Boolean {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    /**
     * The system default Network — what an unbound socket would use. When a
     * VPN is up, this is the VPN tunnel itself; otherwise it's the underlying
     * Wi-Fi or cellular network.
     */
    fun defaultNetwork(context: Context): Network? {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork
    }

    /**
     * Returns the underlying transport the system default network rides on.
     * When a VPN is in front of the default route, looks past it to find the
     * non-VPN network so we attribute checks to Wi-Fi or Mobile (the VPN
     * itself isn't a "lane" — viaVpn on each history Entry is the modifier).
     */
    fun activeType(context: Context): NetworkType {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkType.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkType.NONE
        }
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        if (isVpn) {
            for (net in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(net) ?: continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return NetworkType.WIFI
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return NetworkType.MOBILE
            }
            return NetworkType.OTHER
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> NetworkType.OTHER
        }
    }

    /**
     * Asks the system for a Network with the given transport (Wi-Fi or Cellular)
     * and runs [block] on it. Returns null if the network can't be obtained
     * within the timeout (no SIM, airplane mode, Wi-Fi off, etc.). Always
     * unregisters the callback so the modem / radio can spin down.
     *
     * NOTE: this bypasses any active VPN. CheckWorker therefore only calls it
     * when VPN is NOT in front, otherwise we'd report the underlying DPI-blocked
     * raw transport instead of the user's actual VPN-tunneled experience.
     */
    suspend fun <T> probe(
        context: Context,
        type: NetworkType,
        block: suspend (Network) -> T
    ): T? {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val transport = when (type) {
            NetworkType.WIFI -> NetworkCapabilities.TRANSPORT_WIFI
            NetworkType.MOBILE -> NetworkCapabilities.TRANSPORT_CELLULAR
            else -> return null
        }
        val request = NetworkRequest.Builder()
            .addTransportType(transport)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var callback: ConnectivityManager.NetworkCallback? = null
        return try {
            val network = suspendCancellableCoroutine<Network?> { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resumeWith(Result.success(network))
                    }
                    override fun onUnavailable() {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
                callback = cb
                try {
                    cm.requestNetwork(request, cb, WAIT_TIMEOUT_MS)
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
                cont.invokeOnCancellation {
                    runCatching { cm.unregisterNetworkCallback(cb) }
                }
            } ?: return null
            block(network)
        } finally {
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        }
    }
}
