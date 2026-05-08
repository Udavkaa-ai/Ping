package ru.inetcheck.ping

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat

object PillRenderer {

    data class Lane(val label: String, val status: Status, val percent: Int?)

    fun render(context: Context, width: Int, height: Int, wifi: Lane, mobile: Lane): Bitmap {
        val w = width.coerceAtLeast(240)
        val h = height.coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rowH = h / 2f
        drawRow(context, canvas, 0f, 0f, w.toFloat(), rowH, wifi)
        drawRow(context, canvas, 0f, rowH, w.toFloat(), rowH, mobile)
        return bitmap
    }

    private fun drawRow(
        context: Context,
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        lane: Lane
    ) {
        val padX = w * 0.04f
        val labelW = w * 0.20f
        val percentW = w * 0.18f
        val pillX = x + padX + labelW
        val pillRight = x + w - padX - percentW - w * 0.02f
        val pillW = (pillRight - pillX).coerceAtLeast(20f)
        val pillH = h * 0.42f
        val pillY = y + (h - pillH) / 2f
        val pillRect = RectF(pillX, pillY, pillX + pillW, pillY + pillH)
        val cornerR = pillH / 2f

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.text_secondary)
            textSize = h * 0.30f
            isFakeBoldText = false
        }
        canvas.drawText(lane.label, x + padX, y + h * 0.62f, labelPaint)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.pill_track)
        }
        canvas.drawRoundRect(pillRect, cornerR, cornerR, trackPaint)

        val percent = lane.percent
        if (lane.status != Status.UNKNOWN && percent != null) {
            val fillFraction = (percent / 100f).coerceIn(0f, 1f)
            val fillW = pillW * fillFraction
            if (fillW > 0.5f) {
                val clipPath = Path().apply { addRoundRect(pillRect, cornerR, cornerR, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clipPath)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFor(context, lane.status) }
                canvas.drawRect(pillX, pillY, pillX + fillW, pillY + pillH, fillPaint)
                canvas.restore()
            }
        }

        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(
                context,
                if (lane.status == Status.UNKNOWN) R.color.text_secondary else R.color.text_primary
            )
            textSize = h * 0.40f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val percentText = if (lane.status == Status.UNKNOWN || percent == null) "—" else "$percent%"
        canvas.drawText(percentText, x + w - padX, y + h * 0.66f, percentPaint)
    }

    private fun colorFor(context: Context, status: Status): Int = ContextCompat.getColor(
        context,
        when (status) {
            Status.FULL -> R.color.status_full
            Status.WHITELIST -> R.color.status_whitelist
            Status.NONE -> R.color.status_none
            Status.UNKNOWN -> R.color.status_unknown
        }
    )
}
