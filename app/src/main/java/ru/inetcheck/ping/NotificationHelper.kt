package ru.inetcheck.ping

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val CHANNEL_ID = "internet_recovery"
    private const val NOTIFICATION_ID = 1001

    /** Idempotent — safe to call repeatedly, e.g. from App.onCreate. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notify_channel_description)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Returns true if the app currently holds the runtime POST_NOTIFICATIONS
     * permission. On API < 33 always true (no runtime grant required).
     */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyRecovery(
        context: Context,
        network: NetworkType,
        status: Status,
        viaVpn: Boolean
    ) {
        if (!HostsRepository(context).notifyOnRecovery) return
        if (!hasPermission(context)) return

        val networkName = when (network) {
            NetworkType.WIFI -> context.getString(R.string.network_wifi)
            NetworkType.MOBILE -> context.getString(R.string.network_mobile)
            else -> context.getString(R.string.network_other)
        }
        val tag = if (viaVpn) " ${context.getString(R.string.via_vpn_suffix)}" else ""
        val statusText = when (status) {
            Status.FULL -> context.getString(R.string.status_open).lowercase()
            Status.WHITELIST -> context.getString(R.string.status_only_whitelist_short).lowercase()
            else -> ""
        }
        val title = context.getString(R.string.notify_recovery_title)
        val text = context.getString(R.string.notify_recovery_text, "$networkName$tag", statusText)

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between our check and post — swallow.
        }
    }
}
