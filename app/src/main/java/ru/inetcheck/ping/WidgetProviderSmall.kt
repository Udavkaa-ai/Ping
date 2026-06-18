package ru.inetcheck.ping

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * 2x1 home-screen widget: a single coloured tile that reflects the last
 * known status of the network the device is currently using. Tap opens
 * the app — fresh data comes from the periodic AlarmManager check or
 * the big widget's button; running a check silently from a 2x1 tap
 * gave no visual feedback and was unreliable on MIUI broadcast policy.
 */
class WidgetProviderSmall : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { id -> render(context, manager, id) }
    }

    companion object {
        private const val REQ_OPEN = 12

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
            val history = HistoryRepository(context)
            val active = NetworkRouter.activeType(context)
            val viaVpn = NetworkRouter.isVpnActive(context)
            val status = statusFor(repo, history, active)
            val views = RemoteViews(context.packageName, R.layout.widget_small)

            views.setInt(R.id.widgetSmallRoot, "setBackgroundResource", bgFor(status))
            views.setTextViewText(R.id.widgetSmallText, statusLabel(context, status))
            views.setTextViewText(R.id.widgetSmallNetwork, networkLabel(context, active, viaVpn))

            views.setTextColor(R.id.widgetSmallText, textColorFor(status))
            views.setTextColor(R.id.widgetSmallNetwork, secondaryColorFor(status))

            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetSmallRoot, openPi)

            manager.updateAppWidget(widgetId, views)
        }

        /**
         * Prefer the active transport's last status. If we don't have data
         * for it (e.g. a brand-new install, or an exotic active type like
         * OTHER), fall back to the most recent recorded check on any
         * transport — better than showing "—" forever.
         */
        private fun statusFor(
            repo: HostsRepository,
            history: HistoryRepository,
            active: NetworkType
        ): Status {
            val own = repo.lastStatusFor(active)
            if (own != Status.UNKNOWN) return own
            return history.load().lastOrNull {
                it.network == NetworkType.WIFI || it.network == NetworkType.MOBILE
            }?.status ?: Status.UNKNOWN
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
            return if (viaVpn && (n == NetworkType.WIFI || n == NetworkType.MOBILE)) {
                "$base ${context.getString(R.string.via_vpn_suffix)}"
            } else base
        }
    }
}
