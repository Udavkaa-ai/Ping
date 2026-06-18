package ru.inetcheck.ping

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { id -> render(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHECK) {
            triggerCheck(context)
        }
    }

    companion object {
        const val ACTION_CHECK = "ru.inetcheck.ping.ACTION_CHECK"
        const val ONE_TIME_WORK = "internet_check"

        /**
         * Flip isChecking on, re-render both widgets so any in-progress
         * indicators show up immediately, and enqueue the worker. Used by
         * both widget classes (and the activity) so they all stay in sync.
         */
        fun triggerCheck(context: Context) {
            HostsRepository(context).isChecking = true
            renderAll(context)
            WidgetProviderSmall.renderAll(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CheckWorker>().build()
            )
        }

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WidgetProvider::class.java)
            )
            ids.forEach { id -> render(context, manager, id) }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val repo = HostsRepository(context)
            val history = HistoryRepository(context)
            val views = RemoteViews(context.packageName, R.layout.widget)

            // VPN is a modifier, not a lane. When it's currently up we tag the
            // underlying transport's label with "(VPN)" so the user can see
            // whether the percentage they're reading came from raw transport
            // or tunneled traffic.
            val active = NetworkRouter.activeType(context)
            val isVpn = NetworkRouter.isVpnActive(context)
            views.setTextViewText(
                R.id.wifiLabel,
                labelFor(context, NetworkType.WIFI, isVpn && active == NetworkType.WIFI)
            )
            views.setTextViewText(
                R.id.mobileLabel,
                labelFor(context, NetworkType.MOBILE, isVpn && active == NetworkType.MOBILE)
            )

            applyLane(
                views, R.id.wifiDot, R.id.wifiPercent,
                repo.lastStatusWifi,
                history.availabilityPercent(NetworkType.WIFI)
            )
            applyLane(
                views, R.id.mobileDot, R.id.mobilePercent,
                repo.lastStatusMobile,
                history.availabilityPercent(NetworkType.MOBILE)
            )

            val buttonText = context.getString(
                if (repo.isChecking) R.string.checking else R.string.want_internet
            )
            views.setTextViewText(R.id.refreshButton, buttonText)

            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPi)

            val checkPi = PendingIntent.getBroadcast(
                context, REQ_CHECK,
                Intent(context, WidgetProvider::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refreshButton, checkPi)

            manager.updateAppWidget(widgetId, views)
        }

        private fun labelFor(context: Context, network: NetworkType, viaVpn: Boolean): CharSequence {
            val base = context.getString(
                when (network) {
                    NetworkType.WIFI -> R.string.network_wifi
                    NetworkType.MOBILE -> R.string.network_mobile
                    else -> R.string.network_other
                }
            )
            return if (viaVpn) "$base ${context.getString(R.string.via_vpn_suffix)}" else base
        }

        private fun applyLane(
            views: RemoteViews,
            dotId: Int,
            percentId: Int,
            status: Status,
            percent: Int?
        ) {
            views.setImageViewResource(dotId, dotResFor(status))
            val text = if (status == Status.UNKNOWN || percent == null) "—" else "$percent%"
            views.setTextViewText(percentId, text)
        }

        private fun dotResFor(status: Status) = when (status) {
            Status.FULL -> R.drawable.dot_status_full
            Status.WHITELIST -> R.drawable.dot_status_whitelist
            Status.NONE -> R.drawable.dot_status_none
            Status.UNKNOWN -> R.drawable.dot_status_unknown
        }

        private const val REQ_CHECK = 1
        private const val REQ_OPEN = 2
    }
}
