package ru.inetcheck.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine

object NetworkRouter {
    private const val WAIT_TIMEOUT_MS = 6000

    /**
     * Returns the transport currently used by the system default network.
     * Used by the 2x1 widget to pick which lane's last status to show.
     */
    fun activeType(context: Context): NetworkType {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkType.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkType.NONE
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
