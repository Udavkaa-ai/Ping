package ru.inetcheck.ping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import java.util.Calendar

object ChartRenderer {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val BUCKETS = 96 // 24h / 15min

    data class Lane(val label: String, val entries: List<HistoryRepository.Entry>)

    fun render(
        context: Context,
        width: Int,
        height: Int,
        wifi: Lane,
        mobile: Lane,
        vpn: Lane
    ): Bitmap {
        val w = width.coerceAtLeast(240)
        val h = height.coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.bg_dark)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_secondary)
            textSize = h * 0.10f
        }
        val labelMaxW = maxOf(
            labelPaint.measureText(wifi.label),
            labelPaint.measureText(mobile.label),
            labelPaint.measureText(vpn.label)
        )
        val labelAreaW = labelMaxW + h * 0.10f
        val chartLeft = labelAreaW
        val chartW = w - chartLeft - h * 0.04f

        val axisH = h * 0.18f
        val laneArea = h - axisH
        val gap = h * 0.04f
        val laneH = (laneArea - 2 * gap) / 3f

        val wifiTop = 0f
        val mobileTop = wifiTop + laneH + gap
        val vpnTop = mobileTop + laneH + gap
        val vpnBottom = vpnTop + laneH

        val now = System.currentTimeMillis()
        val start = now - DAY_MS

        drawLane(context, canvas, chartLeft, wifiTop, chartW, laneH, wifi.entries, start, now)
        drawLane(context, canvas, chartLeft, mobileTop, chartW, laneH, mobile.entries, start, now)
        drawLane(context, canvas, chartLeft, vpnTop, chartW, laneH, vpn.entries, start, now)

        // Lane labels on the left, vertically centred in their lane
        canvas.drawText(wifi.label, h * 0.04f, wifiTop + laneH / 2 + labelPaint.textSize / 3, labelPaint)
        canvas.drawText(mobile.label, h * 0.04f, mobileTop + laneH / 2 + labelPaint.textSize / 3, labelPaint)
        canvas.drawText(vpn.label, h * 0.04f, vpnTop + laneH / 2 + labelPaint.textSize / 3, labelPaint)

        drawAxis(context, canvas, chartLeft, chartW, vpnBottom, h.toFloat(), start)

        return bitmap
    }

    private fun drawLane(
        context: Context,
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        entries: List<HistoryRepository.Entry>,
        start: Long, now: Long
    ) {
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.chart_track)
        }
        canvas.drawRect(x, y, x + w, y + h, track)

        val bucketMs = DAY_MS / BUCKETS
        val statuses = arrayOfNulls<Status>(BUCKETS)
        val times = LongArray(BUCKETS)
        for (e in entries) {
            if (e.time < start || e.time > now) continue
            val idx = ((e.time - start) / bucketMs).toInt().coerceIn(0, BUCKETS - 1)
            if (e.time >= times[idx]) {
                statuses[idx] = e.status
                times[idx] = e.time
            }
        }

        val barW = w / BUCKETS
        val cell = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until BUCKETS) {
            val s = statuses[i] ?: continue
            cell.color = colorFor(context, s)
            canvas.drawRect(x + i * barW, y, x + (i + 1) * barW + 0.5f, y + h, cell)
        }
    }

    private fun drawAxis(
        context: Context,
        canvas: Canvas,
        chartLeft: Float, chartW: Float,
        topY: Float, h: Float,
        start: Long
    ) {
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_secondary)
            strokeWidth = 1f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_secondary)
            textSize = (h - topY) * 0.55f
        }
        val cal = Calendar.getInstance()
        val tickTop = topY + (h - topY) * 0.05f
        val tickBottom = topY + (h - topY) * 0.30f
        for (hourOffset in 0..24 step 6) {
            val xRel = (hourOffset / 24f) * chartW
            val x = chartLeft + xRel
            canvas.drawLine(x, tickTop, x, tickBottom, tickPaint)
            cal.timeInMillis = start + hourOffset * 3_600_000L
            val label = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
            val tw = labelPaint.measureText(label)
            val drawX = (x - tw / 2f).coerceAtLeast(chartLeft).coerceAtMost(chartLeft + chartW - tw)
            canvas.drawText(label, drawX, h - 4f, labelPaint)
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
