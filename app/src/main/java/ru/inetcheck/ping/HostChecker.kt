package ru.inetcheck.ping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object HostChecker {
    private const val TIMEOUT_MS = 4000

    suspend fun anyReachable(hosts: List<String>): Boolean = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope false
        val deferred = hosts.map { host -> async(Dispatchers.IO) { reachable(host) } }
        deferred.any { it.await() }
    }

    private suspend fun reachable(host: String): Boolean = withContext(Dispatchers.IO) {
        val cleaned = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        if (cleaned.isEmpty()) return@withContext false

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://$cleaned/")
            connection = (url.openConnection() as HttpURLConnection).apply {
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
