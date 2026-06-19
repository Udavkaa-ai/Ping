package ru.inetcheck.ping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat

/**
 * Renders a single horizontal stripe for the 2x2 widget — coloured buckets
 * by status with a thin accent-blue overlay where viaVpn is set. Labels,
 * status text and percentages are drawn as native TextViews next to this
 * bitmap (in widget.xml), not into the bitmap itself — keeps text crisp
 * regardless of fitXY scaling.
 */
object MiniChartRenderer {

    private const val BUCKET_MIN = 15L
    private const val MIN_TO_MS = 60L * 1000

    fun render(
        context: Context,
        width: Int,
        height: Int,
        hours: Int,
        entries: List<HistoryRepository.Entry>
    ): Bitmap {
        val w = width.coerceAtLeast(60)
        val h = height.coerceAtLeast(6)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.chart_track)
        }
        val radius = h * 0.25f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, track)

        val now = System.currentTimeMillis()
        val rangeMs = hours.toLong() * 60 * 60 * 1000
        val start = now - rangeMs
        val buckets = (rangeMs / (BUCKET_MIN * MIN_TO_MS)).toInt().coerceAtLeast(1)
        val bucketMs = rangeMs / buckets

        val statuses = arrayOfNulls<Status>(buckets)
        val viaVpn = BooleanArray(buckets)
        val times = LongArray(buckets)
        for (e in entries) {
            if (e.time < start || e.time > now) continue
            val idx = ((e.time - start) / bucketMs).toInt().coerceIn(0, buckets - 1)
            if (e.time >= times[idx]) {
                statuses[idx] = e.status
                viaVpn[idx] = e.viaVpn
                times[idx] = e.time
            }
        }

        val barW = w.toFloat() / buckets
        val cell = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until buckets) {
            val s = statuses[i] ?: continue
            cell.color = colorFor(context, s)
            // Slight overdraw on the right edge so adjacent buckets don't show
            // a hairline gap because of float rounding.
            canvas.drawRect(i * barW, 0f, (i + 1) * barW + 0.5f, h.toFloat(), cell)
        }

        val stripH = (h * 0.32f).coerceAtLeast(1.5f)
        val vpnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.accent_blue)
        }
        for (i in 0 until buckets) {
            if (statuses[i] == null || !viaVpn[i]) continue
            canvas.drawRect(i * barW, 0f, (i + 1) * barW + 0.5f, stripH, vpnPaint)
        }

        return bitmap
    }

    private fun colorFor(context: Context, s: Status): Int = ContextCompat.getColor(
        context,
        when (s) {
            Status.FULL -> R.color.status_full
            Status.WHITELIST -> R.color.status_whitelist
            Status.NONE -> R.color.status_none
            Status.UNKNOWN -> R.color.status_unknown
        }
    )

    /**
     * Share of FULL + WHITELIST checks within the same window the stripe
     * covers. Returned as 0..100, or null when there's no data yet.
     */
    fun availabilityPercent(entries: List<HistoryRepository.Entry>, hours: Int): Int? {
        val cutoff = System.currentTimeMillis() - hours.toLong() * 60 * 60 * 1000
        val recent = entries.filter { it.time >= cutoff }
        if (recent.isEmpty()) return null
        val good = recent.count { it.status == Status.FULL || it.status == Status.WHITELIST }
        return good * 100 / recent.size
    }
}
