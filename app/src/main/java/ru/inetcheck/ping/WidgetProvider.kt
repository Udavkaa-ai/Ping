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
        private const val WINDOW_HOURS = 6
        private const val STRIPE_W = 600
        private const val STRIPE_H = 36

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

            val all = history.load()
            val wifiEntries = all.filter { it.network == NetworkType.WIFI }
            val mobileEntries = all.filter { it.network == NetworkType.MOBILE }

            val active = NetworkRouter.activeType(context)
            val isVpn = NetworkRouter.isVpnActive(context)

            // Wi-Fi row
            views.setTextViewText(
                R.id.wifiLabel,
                labelFor(context, NetworkType.WIFI, isVpn && active == NetworkType.WIFI)
            )
            views.setTextViewText(R.id.wifiStatus, statusLabel(context, repo.lastStatusWifi))
            views.setTextViewText(
                R.id.wifiPercent,
                percentLabel(MiniChartRenderer.availabilityPercent(wifiEntries, WINDOW_HOURS))
            )
            views.setImageViewBitmap(
                R.id.wifiStripe,
                MiniChartRenderer.render(context, STRIPE_W, STRIPE_H, WINDOW_HOURS, wifiEntries)
            )

            // Mobile row
            views.setTextViewText(
                R.id.mobileLabel,
                labelFor(context, NetworkType.MOBILE, isVpn && active == NetworkType.MOBILE)
            )
            views.setTextViewText(R.id.mobileStatus, statusLabel(context, repo.lastStatusMobile))
            views.setTextViewText(
                R.id.mobilePercent,
                percentLabel(MiniChartRenderer.availabilityPercent(mobileEntries, WINDOW_HOURS))
            )
            views.setImageViewBitmap(
                R.id.mobileStripe,
                MiniChartRenderer.render(context, STRIPE_W, STRIPE_H, WINDOW_HOURS, mobileEntries)
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

        private fun labelFor(context: Context, network: NetworkType, viaVpn: Boolean): String {
            val base = context.getString(
                when (network) {
                    NetworkType.WIFI -> R.string.network_wifi
                    NetworkType.MOBILE -> R.string.network_mobile
                    else -> R.string.network_other
                }
            )
            return if (viaVpn) "$base ${context.getString(R.string.via_vpn_suffix)}" else base
        }

        private fun statusLabel(context: Context, s: Status): String = context.getString(
            when (s) {
                Status.FULL -> R.string.status_open
                Status.WHITELIST -> R.string.status_only_whitelist_short
                Status.NONE -> R.string.status_blocked
                Status.UNKNOWN -> R.string.status_no_data
            }
        )

        private fun percentLabel(p: Int?): String = p?.let { "$it%" } ?: "—"

        private const val REQ_CHECK = 1
        private const val REQ_OPEN = 2
    }
}
