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
            val repo = HostsRepository(context)
            repo.isChecking = true
            renderAll(context)
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CheckWorker>().build()
            )
        }
    }

    companion object {
        const val ACTION_CHECK = "ru.inetcheck.ping.ACTION_CHECK"
        const val ONE_TIME_WORK = "internet_check"

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

            applyMainLane(
                views, R.id.wifiDot, R.id.wifiPercent,
                repo.lastStatusWifi,
                history.availabilityPercent(NetworkType.WIFI)
            )
            applyMainLane(
                views, R.id.mobileDot, R.id.mobilePercent,
                repo.lastStatusMobile,
                history.availabilityPercent(NetworkType.MOBILE)
            )
            applyFocusLane(views, repo, history)

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

        private fun applyMainLane(
            views: RemoteViews,
            dotId: Int,
            percentId: Int,
            status: Status,
            percent: Int?
        ) {
            views.setImageViewResource(dotId, mainDotResFor(status))
            val text = if (status == Status.UNKNOWN || percent == null) "—" else "$percent%"
            views.setTextViewText(percentId, text)
        }

        private fun applyFocusLane(
            views: RemoteViews,
            repo: HostsRepository,
            history: HistoryRepository
        ) {
            val wifi = repo.lastFocusStatusWifi
            val mobile = repo.lastFocusStatusMobile
            // Aggregate: green only when at least one transport actually had a
            // focus host respond. Anything else collapses to "blocked / no data".
            val status = when {
                wifi == Status.FULL || mobile == Status.FULL -> Status.FULL
                wifi == Status.NONE || mobile == Status.NONE -> Status.NONE
                else -> Status.UNKNOWN
            }
            val percent = listOfNotNull(
                history.focusPercent(NetworkType.WIFI),
                history.focusPercent(NetworkType.MOBILE)
            ).maxOrNull()

            views.setImageViewResource(R.id.focusDot, focusDotResFor(status))
            val text = if (status == Status.UNKNOWN || percent == null) "—" else "$percent%"
            views.setTextViewText(R.id.focusPercent, text)
        }

        private fun mainDotResFor(status: Status) = when (status) {
            Status.FULL -> R.drawable.dot_status_full
            Status.WHITELIST -> R.drawable.dot_status_whitelist
            Status.NONE -> R.drawable.dot_status_none
            Status.UNKNOWN -> R.drawable.dot_status_unknown
        }

        // For focus the colour scheme differs: blocked is the expected, neutral
        // state; reachability is the only positive signal worth highlighting.
        private fun focusDotResFor(status: Status) = when (status) {
            Status.FULL -> R.drawable.dot_status_full
            else -> R.drawable.dot_status_unknown
        }

        private const val REQ_CHECK = 1
        private const val REQ_OPEN = 2
    }
}
