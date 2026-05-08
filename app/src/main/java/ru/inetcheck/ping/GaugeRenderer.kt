package ru.inetcheck.ping

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

object GaugeRenderer {

    private const val RED = 0xFFE5484D.toInt()
    private const val WHITE = 0xFFF1F1F1.toInt()
    private const val GREEN = 0xFF3DDC84.toInt()
    private const val GREY = 0xFF666B70.toInt()
    private const val PIVOT = 0xFF0E1216.toInt()

    fun render(width: Int, height: Int, status: Status): Bitmap {
        val w = width.coerceAtLeast(240)
        val h = height.coerceAtLeast(140)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = w / 2f
        val cy = h * 0.92f
        val radius = minOf(w / 2f - 12f, h * 0.92f - 12f).coerceAtLeast(40f)
        val arcWidth = radius * 0.28f

        val outer = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val innerR = radius - arcWidth
        val inner = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Top half goes 180° -> 360° (clockwise through 270° = up).
        drawSegment(canvas, outer, inner, 180f, 60f, RED, paint)
        drawSegment(canvas, outer, inner, 240f, 60f, WHITE, paint)
        drawSegment(canvas, outer, inner, 300f, 60f, GREEN, paint)

        val angleDeg = when (status) {
            Status.NONE -> 210f
            Status.WHITELIST -> 270f
            Status.FULL -> 330f
            Status.UNKNOWN -> 270f
        }
        val arrowColor = when (status) {
            Status.NONE -> RED
            Status.WHITELIST -> WHITE
            Status.FULL -> GREEN
            Status.UNKNOWN -> GREY
        }
        drawArrow(canvas, cx, cy, innerR * 0.92f, angleDeg, arrowColor, arcWidth * 0.18f)

        val pivot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PIVOT
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, arcWidth * 0.32f, pivot)

        return bitmap
    }

    private fun drawSegment(
        canvas: Canvas, outer: RectF, inner: RectF,
        startDeg: Float, sweep: Float, color: Int, paint: Paint
    ) {
        paint.color = color
        val path = Path().apply {
            arcTo(outer, startDeg, sweep, false)
            arcTo(inner, startDeg + sweep, -sweep, false)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawArrow(
        canvas: Canvas, cx: Float, cy: Float,
        length: Float, angleDeg: Float, color: Int, halfBase: Float
    ) {
        val a = Math.toRadians(angleDeg.toDouble())
        val tipX = cx + (length * cos(a)).toFloat()
        val tipY = cy + (length * sin(a)).toFloat()

        val perp = a + Math.PI / 2
        val dx = (halfBase * cos(perp)).toFloat()
        val dy = (halfBase * sin(perp)).toFloat()

        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(cx + dx, cy + dy)
            lineTo(cx - dx, cy - dy)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(cx, cy, halfBase * 1.6f, paint)

        // Subtle outline so a white arrow stays visible on the white sector.
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(80, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawPath(path, outline)
    }
}
