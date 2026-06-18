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

            val all = history.load()
            val active = NetworkRouter.activeType(context)
            val isVpn = NetworkRouter.isVpnActive(context)
            val wifiLabel = labelFor(context, NetworkType.WIFI, isVpn && active == NetworkType.WIFI)
            val mobileLabel = labelFor(context, NetworkType.MOBILE, isVpn && active == NetworkType.MOBILE)

            val (bmpW, bmpH) = bitmapSizeFor(context, manager, widgetId)
            val bitmap = MiniChartRenderer.render(
                context, bmpW, bmpH, hours = WINDOW_HOURS,
                MiniChartRenderer.Lane(wifiLabel, all.filter { it.network == NetworkType.WIFI }),
                MiniChartRenderer.Lane(mobileLabel, all.filter { it.network == NetworkType.MOBILE })
            )
            views.setImageViewBitmap(R.id.widgetChart, bitmap)

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

        /**
         * Pick a bitmap size matching the actual widget chart area so fitXY
         * doesn't visibly stretch labels/bars. Falls back to a reasonable
         * default before the launcher reports widget options.
         */
        private fun bitmapSizeFor(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ): Pair<Int, Int> {
            val opts = manager.getAppWidgetOptions(widgetId)
            val minWdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val minHdp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
            val density = context.resources.displayMetrics.density
            val widthPx = (minWdp * density).toInt().coerceAtLeast(240)
            // Subtract the rough height of padding (16dp) + button (~32dp) so
            // the bitmap aspect matches the ImageView region rather than the
            // whole widget cell.
            val chartHdp = (minHdp - 48).coerceAtLeast(40)
            val heightPx = (chartHdp * density).toInt().coerceAtLeast(80)
            return widthPx to heightPx
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

        private const val REQ_CHECK = 1
        private const val REQ_OPEN = 2
    }
}
