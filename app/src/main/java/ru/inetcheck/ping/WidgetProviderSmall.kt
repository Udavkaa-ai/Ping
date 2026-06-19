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
 * 2x1 home-screen tile. One combined "Wi-Fi · Открыт" TextView on the left,
 * a refresh icon on the right. Tap the body to open the app, tap the icon
 * to run a check. Kept deliberately minimal — MIUI's RemoteViews inflation
 * has been allergic to anything fancier (ProgressBar, AnimationDrawable,
 * nested clickable FrameLayouts).
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
            WidgetProvider.triggerCheck(context)
        }
    }

    companion object {
        const val ACTION_CHECK_SMALL = "ru.inetcheck.ping.ACTION_CHECK_SMALL"
        private const val REQ_OPEN = 12
        private const val REQ_CHECK = 13

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
            views.setTextViewText(R.id.widgetSmallText, label(context, active, status, viaVpn))
            views.setTextColor(R.id.widgetSmallText, textColorFor(status))
            views.setImageViewResource(
                R.id.widgetSmallRefresh,
                if (repo.isChecking) R.drawable.ic_loading else R.drawable.ic_refresh
            )

            val openPi = PendingIntent.getActivity(
                context, REQ_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetSmallRoot, openPi)
            views.setOnClickPendingIntent(R.id.widgetSmallText, openPi)

            val checkPi = PendingIntent.getBroadcast(
                context, REQ_CHECK,
                Intent(context, WidgetProviderSmall::class.java).setAction(ACTION_CHECK_SMALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetSmallRefresh, checkPi)

            manager.updateAppWidget(widgetId, views)
        }

        /**
         * Prefer the active transport's last status. Falls back to the most
         * recent recorded check on any transport so the tile doesn't lock
         * on "—" for a brand-new install or a transient OTHER active type.
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

        private fun label(
            context: Context,
            network: NetworkType,
            status: Status,
            viaVpn: Boolean
        ): String {
            val net = when (network) {
                NetworkType.WIFI -> context.getString(R.string.network_wifi)
                NetworkType.MOBILE -> context.getString(R.string.network_mobile)
                NetworkType.OTHER -> context.getString(R.string.network_other)
                NetworkType.NONE -> context.getString(R.string.network_offline)
                else -> context.getString(R.string.network_other)
            }
            val tag = if (viaVpn && (network == NetworkType.WIFI || network == NetworkType.MOBILE)) {
                " ${context.getString(R.string.via_vpn_suffix)}"
            } else ""
            val statusText = context.getString(
                when (status) {
                    Status.FULL -> R.string.status_open
                    Status.WHITELIST -> R.string.status_only_whitelist_short
                    Status.NONE -> R.string.status_blocked
                    Status.UNKNOWN -> R.string.status_no_data
                }
            )
            return "$net$tag · $statusText"
        }

        private fun bgFor(s: Status) = when (s) {
            Status.FULL -> R.drawable.bg_status_full
            Status.WHITELIST -> R.drawable.bg_status_whitelist
            Status.NONE -> R.drawable.bg_status_none
            Status.UNKNOWN -> R.drawable.bg_status_unknown
        }

        // Whitelist sector is near-white; everything else is dark/coloured.
        // Pick a contrasting text colour so the label stays legible.
        private fun textColorFor(s: Status) = when (s) {
            Status.WHITELIST -> Color.parseColor("#1B1F23")
            else -> Color.WHITE
        }
    }
}
