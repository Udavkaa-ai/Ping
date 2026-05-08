package ru.inetcheck.ping

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.util.Calendar

object ChartRenderer {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val BUCKETS = 96 // 24h / 15min

    private const val BG = 0xFF1B1F23.toInt()
    private const val AXIS = 0xFF555555.toInt()
    private const val LABEL = 0xFFAAAAAA.toInt()

    fun render(
        width: Int,
        height: Int,
        entries: List<HistoryRepository.Entry>,
        emptyMessage: String
    ): Bitmap {
        val w = width.coerceAtLeast(240)
        val h = height.coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val now = System.currentTimeMillis()
        val start = now - DAY_MS
        val bucketMs = DAY_MS / BUCKETS

        val chartH = h * 0.72f

        if (entries.isEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LABEL
                textSize = h * 0.18f
            }
            val tw = text.measureText(emptyMessage)
            canvas.drawText(emptyMessage, (w - tw) / 2f, h * 0.5f, text)
            drawAxis(canvas, w, h, chartH, start)
            return bitmap
        }

        val bucketStatus = arrayOfNulls<Status>(BUCKETS)
        val bucketTime = LongArray(BUCKETS)
        for (e in entries) {
            if (e.time < start || e.time > now) continue
            val idx = ((e.time - start) / bucketMs).toInt().coerceIn(0, BUCKETS - 1)
            if (e.time >= bucketTime[idx]) {
                bucketStatus[idx] = e.status
                bucketTime[idx] = e.time
            }
        }

        val barW = w.toFloat() / BUCKETS
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until BUCKETS) {
            val s = bucketStatus[i] ?: continue
            cellPaint.color = colorFor(s)
            // +0.5 to avoid hairline gaps from float rounding
            canvas.drawRect(i * barW, 0f, (i + 1) * barW + 0.5f, chartH, cellPaint)
        }

        drawAxis(canvas, w, h, chartH, start)
        return bitmap
    }

    private fun drawAxis(canvas: Canvas, w: Int, h: Int, chartH: Float, start: Long) {
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AXIS
            strokeWidth = 1.5f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LABEL
            textSize = h * 0.16f
        }
        val cal = Calendar.getInstance()
        for (hourOffset in 0..24 step 6) {
            val x = (hourOffset / 24f) * w
            canvas.drawLine(x, chartH, x, chartH + h * 0.07f, tickPaint)
            cal.timeInMillis = start + hourOffset * 3_600_000L
            val label = "%02d:00".format(cal.get(Calendar.HOUR_OF_DAY))
            val tw = labelPaint.measureText(label)
            val drawX = (x - tw / 2f).coerceAtLeast(2f).coerceAtMost(w - tw - 2f)
            canvas.drawText(label, drawX, h - 6f, labelPaint)
        }
    }

    private fun colorFor(s: Status) = when (s) {
        Status.FULL -> 0xFF3DDC84.toInt()
        Status.WHITELIST -> 0xFFF1F1F1.toInt()
        Status.NONE -> 0xFFE5484D.toInt()
        Status.UNKNOWN -> 0xFF666B70.toInt()
    }
}
