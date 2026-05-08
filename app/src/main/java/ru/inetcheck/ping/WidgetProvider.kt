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
        private const val BITMAP_W = 600
        private const val BITMAP_H = 350

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
            val views = RemoteViews(context.packageName, R.layout.widget)

            val bitmap = GaugeRenderer.render(BITMAP_W, BITMAP_H, repo.lastStatus)
            views.setImageViewBitmap(R.id.gaugeImage, bitmap)

            val buttonText = context.getString(
                if (repo.isChecking) R.string.checking else R.string.want_internet
            )
            views.setTextViewText(R.id.refreshButton, buttonText)

            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WidgetProvider::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refreshButton, pi)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
