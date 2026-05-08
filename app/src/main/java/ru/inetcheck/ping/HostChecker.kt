package ru.inetcheck.ping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object HostChecker {
    private const val PORT = 443
    private const val TIMEOUT_MS = 3000

    suspend fun anyReachable(hosts: List<String>): Boolean = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope false
        val deferred = hosts.map { host -> async(Dispatchers.IO) { reachable(host) } }
        deferred.any { it.await() }
    }

    private suspend fun reachable(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT_MS)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }
}
