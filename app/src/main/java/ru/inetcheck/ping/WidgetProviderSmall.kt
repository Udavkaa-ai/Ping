package ru.inetcheck.ping

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 2x1 home-screen widget: a single coloured tile that reflects the last
 * known status of the network the device is currently using. Tap = run
 * a fresh check.
 */
class WidgetProviderSmall : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { id -> render(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHECK_SMALL) {
            val repo = HostsRepository(context)
            repo.isChecking = true
            renderAll(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                WidgetProvider.ONE_TIME_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CheckWorker>().build()
            )
        }
    }

    companion object {
        const val ACTION_CHECK_SMALL = "ru.inetcheck.ping.ACTION_CHECK_SMALL"
        private const val REQ_CHECK = 11

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WidgetProviderSmall::class.java)
            )
            ids.forEach { id -> render(context, manager, id) }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val repo = HostsRepository(context)
            val active = NetworkRouter.activeType(context)
            val viaVpn = NetworkRouter.isVpnActive(context)
            val status = repo.lastStatusFor(active)
            val views = RemoteViews(context.packageName, R.layout.widget_small)

            views.setInt(R.id.widgetSmallRoot, "setBackgroundResource", bgFor(status))
            views.setTextViewText(R.id.widgetSmallText, statusLabel(context, status))
            views.setTextViewText(R.id.widgetSmallNetwork, networkLabel(context, active, viaVpn))

            val textColor = textColorFor(status)
            views.setTextColor(R.id.widgetSmallText, textColor)
            views.setTextColor(R.id.widgetSmallNetwork, secondaryColorFor(status))

            val pi = PendingIntent.getBroadcast(
                context, REQ_CHECK,
                Intent(context, WidgetProviderSmall::class.java).setAction(ACTION_CHECK_SMALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetSmallRoot, pi)

            manager.updateAppWidget(widgetId, views)
        }

        private fun bgFor(s: Status) = when (s) {
            Status.FULL -> R.drawable.bg_status_full
            Status.WHITELIST -> R.drawable.bg_status_whitelist
            Status.NONE -> R.drawable.bg_status_none
            Status.UNKNOWN -> R.drawable.bg_status_unknown
        }

        // Whitelist sector is near-white, every other status is dark/coloured;
        // pick contrasting text so the label stays legible.
        private fun textColorFor(s: Status) = when (s) {
            Status.WHITELIST -> Color.parseColor("#1B1F23")
            else -> Color.WHITE
        }

        private fun secondaryColorFor(s: Status) = when (s) {
            Status.WHITELIST -> Color.parseColor("#666B70")
            else -> Color.parseColor("#E6FFFFFF")
        }

        private fun statusLabel(context: Context, s: Status): String = context.getString(
            when (s) {
                Status.FULL -> R.string.status_open
                Status.WHITELIST -> R.string.status_only_whitelist_short
                Status.NONE -> R.string.status_blocked
                Status.UNKNOWN -> R.string.status_no_data
            }
        )

        private fun networkLabel(context: Context, n: NetworkType, viaVpn: Boolean): String {
            val base = when (n) {
                NetworkType.WIFI -> context.getString(R.string.network_wifi)
                NetworkType.MOBILE -> context.getString(R.string.network_mobile)
                NetworkType.OTHER -> context.getString(R.string.network_other)
                NetworkType.NONE -> context.getString(R.string.network_offline)
                else -> context.getString(R.string.network_other)
            }
            // VPN status itself isn't shown — the tile colour already reflects
            // whether the tunnel provides access — but we mark the transport
            // label so the user knows the percentage is via-VPN.
            return if (viaVpn && (n == NetworkType.WIFI || n == NetworkType.MOBILE)) {
                "$base ${context.getString(R.string.via_vpn_suffix)}"
            } else base
        }
    }
}
