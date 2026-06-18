package ru.inetcheck.ping

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object HostChecker {
    private const val TIMEOUT_MS = 5000

    /**
     * Returns true if at least one host responds to an HTTPS HEAD request.
     * If [network] is non-null, the connection is forced through that
     * specific network (e.g. cellular while the device is on Wi-Fi).
     */
    suspend fun anyReachable(hosts: List<String>, network: Network? = null): Boolean = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope false
        val deferred = hosts.map { host -> async(Dispatchers.IO) { reachable(host, network) } }
        deferred.any { it.await() }
    }

    private suspend fun reachable(host: String, network: Network?): Boolean = withContext(Dispatchers.IO) {
        val cleaned = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        if (cleaned.isEmpty()) return@withContext false

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://$cleaned/")
            val raw = network?.openConnection(url) ?: url.openConnection()
            connection = (raw as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "PingRU/0.1")
            }
            // Triggers DNS, TCP, TLS handshake, and reads the status line.
            // DPI typically breaks TLS, which throws SSLException here.
            val code = connection.responseCode
            code in 100..599
        } catch (_: Throwable) {
            false
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }
}
