package ru.inetcheck.ping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat

/**
 * Compact two-lane chart for the 2x2 widget. No axis, no surrounding text —
 * just a left-side network label, the coloured bucket strip, and the
 * "available time" percentage on the right. Window size is configurable
 * (the widget asks for 6 hours).
 */
object MiniChartRenderer {

    private const val BUCKET_MIN = 15L
    private const val MIN_TO_MS = 60L * 1000

    data class Lane(val label: String, val entries: List<HistoryRepository.Entry>)

    fun render(
        context: Context,
        width: Int,
        height: Int,
        hours: Int,
        wifi: Lane,
        mobile: Lane
    ): Bitmap {
        val w = width.coerceAtLeast(120)
        val h = height.coerceAtLeast(40)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_secondary)
            textSize = h * 0.22f
        }
        val labelMaxW = maxOf(
            labelPaint.measureText(wifi.label),
            labelPaint.measureText(mobile.label)
        )
        val labelAreaW = labelMaxW + h * 0.10f

        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_primary)
            textSize = h * 0.24f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val percentAreaW = percentPaint.measureText("100%") + h * 0.10f

        val chartLeft = labelAreaW
        val chartRight = w - percentAreaW
        val chartW = (chartRight - chartLeft).coerceAtLeast(20f)

        val gap = h * 0.08f
        val laneH = (h - gap) / 2f

        val now = System.currentTimeMillis()
        val rangeMs = hours.toLong() * 60 * 60 * 1000
        val start = now - rangeMs
        val buckets = (rangeMs / (BUCKET_MIN * MIN_TO_MS)).toInt().coerceAtLeast(1)

        drawLane(context, canvas, chartLeft, 0f, chartW, laneH, buckets, wifi.entries, start, now, rangeMs)
        drawLane(context, canvas, chartLeft, laneH + gap, chartW, laneH, buckets, mobile.entries, start, now, rangeMs)

        // Lane labels
        canvas.drawText(wifi.label, h * 0.04f, laneH / 2 + labelPaint.textSize / 3, labelPaint)
        canvas.drawText(mobile.label, h * 0.04f, laneH + gap + laneH / 2 + labelPaint.textSize / 3, labelPaint)

        // Lane percentages
        val rightEdge = w - h * 0.04f
        val wifiPct = formatPercent(availabilityPercent(wifi.entries, start, now))
        val mobilePct = formatPercent(availabilityPercent(mobile.entries, start, now))
        canvas.drawText(wifiPct, rightEdge, laneH / 2 + percentPaint.textSize / 3, percentPaint)
        canvas.drawText(mobilePct, rightEdge, laneH + gap + laneH / 2 + percentPaint.textSize / 3, percentPaint)

        return bitmap
    }

    private fun availabilityPercent(
        entries: List<HistoryRepository.Entry>,
        start: Long,
        now: Long
    ): Int? {
        val recent = entries.filter { it.time in start..now }
        if (recent.isEmpty()) return null
        val good = recent.count { it.status == Status.FULL || it.status == Status.WHITELIST }
        return good * 100 / recent.size
    }

    private fun formatPercent(p: Int?): String = p?.let { "$it%" } ?: "—"

    private fun drawLane(
        context: Context,
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        buckets: Int,
        entries: List<HistoryRepository.Entry>,
        start: Long, now: Long, rangeMs: Long
    ) {
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.chart_track)
        }
        canvas.drawRect(x, y, x + w, y + h, track)

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

        val barW = w / buckets
        val cell = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until buckets) {
            val s = statuses[i] ?: continue
            cell.color = colorFor(context, s)
            canvas.drawRect(x + i * barW, y, x + (i + 1) * barW + 0.5f, y + h, cell)
        }

        // VPN-marker strip — same idea as in ChartRenderer.
        val stripH = (h * 0.22f).coerceAtLeast(2f)
        val vpnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.accent_blue)
        }
        for (i in 0 until buckets) {
            if (statuses[i] == null || !viaVpn[i]) continue
            canvas.drawRect(x + i * barW, y, x + (i + 1) * barW + 0.5f, y + stripH, vpnPaint)
        }
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
}
