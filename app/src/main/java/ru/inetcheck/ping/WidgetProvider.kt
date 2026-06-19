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

            applyLane(
                context, views,
                R.id.wifiDot, R.id.wifiTitle, R.id.wifiPercent,
                NetworkType.WIFI, isVpn && active == NetworkType.WIFI,
                repo.lastStatusWifi,
                availabilityPercent(wifiEntries, WINDOW_HOURS)
            )
            applyLane(
                context, views,
                R.id.mobileDot, R.id.mobileTitle, R.id.mobilePercent,
                NetworkType.MOBILE, isVpn && active == NetworkType.MOBILE,
                repo.lastStatusMobile,
                availabilityPercent(mobileEntries, WINDOW_HOURS)
            )

            val buttonText = context.getString(
                if (repo.isChecking) R.string.checking else R.string.want_internet
            )
            views.setTextViewText(R.id.refreshButton, buttonText)

            // MIUI's launcher sometimes ignores setOnClickPendingIntent on the
            // root LinearLayout, so we wire the same open-app intent to each
            // row as a fallback. Whichever click target the launcher honours,
            // tapping anywhere except the refresh button opens the app.
            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPi)
            views.setOnClickPendingIntent(R.id.wifiRow, openPi)
            views.setOnClickPendingIntent(R.id.mobileRow, openPi)

            val checkPi = PendingIntent.getBroadcast(
                context, REQ_CHECK,
                Intent(context, WidgetProvider::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refreshButton, checkPi)

            manager.updateAppWidget(widgetId, views)
        }

        private fun applyLane(
            context: Context,
            views: RemoteViews,
            dotId: Int,
            titleId: Int,
            percentId: Int,
            network: NetworkType,
            viaVpn: Boolean,
            status: Status,
            percent: Int?
        ) {
            views.setImageViewResource(dotId, dotResFor(status))
            views.setTextViewText(titleId, titleFor(context, network, viaVpn, status))
            views.setTextViewText(percentId, percent?.let { "$it%" } ?: "—")
        }

        private fun titleFor(
            context: Context,
            network: NetworkType,
            viaVpn: Boolean,
            status: Status
        ): String {
            val base = context.getString(
                when (network) {
                    NetworkType.WIFI -> R.string.network_wifi
                    NetworkType.MOBILE -> R.string.network_mobile
                    else -> R.string.network_other
                }
            )
            val tag = if (viaVpn) " ${context.getString(R.string.via_vpn_suffix)}" else ""
            val statusText = context.getString(
                when (status) {
                    Status.FULL -> R.string.status_open
                    Status.WHITELIST -> R.string.status_only_whitelist_short
                    Status.NONE -> R.string.status_blocked
                    Status.UNKNOWN -> R.string.status_no_data
                }
            )
            return "$base$tag · $statusText"
        }

        private fun dotResFor(status: Status) = when (status) {
            Status.FULL -> R.drawable.dot_status_full
            Status.WHITELIST -> R.drawable.dot_status_whitelist
            Status.NONE -> R.drawable.dot_status_none
            Status.UNKNOWN -> R.drawable.dot_status_unknown
        }

        private fun availabilityPercent(
            entries: List<HistoryRepository.Entry>,
            hours: Int
        ): Int? {
            val cutoff = System.currentTimeMillis() - hours.toLong() * 60 * 60 * 1000
            val recent = entries.filter { it.time >= cutoff }
            if (recent.isEmpty()) return null
            val good = recent.count { it.status == Status.FULL || it.status == Status.WHITELIST }
            return good * 100 / recent.size
        }

        private const val REQ_CHECK = 1
        private const val REQ_OPEN = 2
    }
}
