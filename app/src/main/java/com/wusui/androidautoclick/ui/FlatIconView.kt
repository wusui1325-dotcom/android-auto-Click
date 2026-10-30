package com.wusui.androidautoclick.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Flat black/white 2D streamline icons — no emoji, no shadows.
 */
class FlatIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Kind {
        PLAY, STOP, PLUS, DRAG, DELETE, RECORD, RECORD_ON,
        COLLAPSE, EXPAND, SAVE, SCHEME, PARAMS, CLOSE
    }

    var kind: Kind = Kind.PLAY
        set(value) { field = value; invalidate() }

    var ink: Int = 0xFF1D1D1F.toInt()
        set(value) { field = value; paint.color = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ink
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ink
    }
    private val path = Path()
    private val tmp = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h)
        val pad = s * 0.18f
        val l = (w - s) / 2f + pad
        val t = (h - s) / 2f + pad
        val r = l + s - 2 * pad
        val b = t + s - 2 * pad
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val stroke = (s * 0.08f).coerceAtLeast(1.5f)
        paint.strokeWidth = stroke
        fill.color = ink
        paint.color = ink
        path.reset()

        when (kind) {
            Kind.PLAY -> {
                // ▶ triangle
                path.moveTo(l + (r - l) * 0.28f, t)
                path.lineTo(r - (r - l) * 0.12f, cy)
                path.lineTo(l + (r - l) * 0.28f, b)
                path.close()
                canvas.drawPath(path, fill)
            }
            Kind.STOP -> {
                // ■ square
                val m = (r - l) * 0.18f
                canvas.drawRect(l + m, t + m, r - m, b - m, fill)
            }
            Kind.PLUS -> {
                canvas.drawLine(cx, t + stroke, cx, b - stroke, paint)
                canvas.drawLine(l + stroke, cy, r - stroke, cy, paint)
            }
            Kind.DRAG -> {
                // ↔ two chevrons
                val midY = cy
                path.moveTo(cx - (r - l) * 0.12f, t + (b - t) * 0.28f)
                path.lineTo(l + stroke, midY)
                path.lineTo(cx - (r - l) * 0.12f, b - (b - t) * 0.28f)
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(cx + (r - l) * 0.12f, t + (b - t) * 0.28f)
                path.lineTo(r - stroke, midY)
                path.lineTo(cx + (r - l) * 0.12f, b - (b - t) * 0.28f)
                canvas.drawPath(path, paint)
                canvas.drawLine(l + (r - l) * 0.28f, cy, r - (r - l) * 0.28f, cy, paint)
            }
            Kind.DELETE -> {
                // ×
                canvas.drawLine(l + stroke, t + stroke, r - stroke, b - stroke, paint)
                canvas.drawLine(r - stroke, t + stroke, l + stroke, b - stroke, paint)
            }
            Kind.RECORD, Kind.RECORD_ON -> {
                // ○ / ● hollow or filled circle
                val rad = (r - l) / 2f * 0.72f
                if (kind == Kind.RECORD_ON) {
                    canvas.drawCircle(cx, cy, rad * 0.55f, fill)
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, rad, paint)
                } else {
                    canvas.drawCircle(cx, cy, rad, paint)
                }
            }
            Kind.COLLAPSE -> {
                // ⌃ chevron up
                path.moveTo(l + stroke, cy + (b - t) * 0.12f)
                path.lineTo(cx, t + stroke)
                path.lineTo(r - stroke, cy + (b - t) * 0.12f)
                canvas.drawPath(path, paint)
            }
            Kind.EXPAND -> {
                path.moveTo(l + stroke, cy - (b - t) * 0.12f)
                path.lineTo(cx, b - stroke)
                path.lineTo(r - stroke, cy - (b - t) * 0.12f)
                canvas.drawPath(path, paint)
            }
            Kind.SAVE -> {
                // floppy-ish: rounded rect + top tab
                tmp.set(l + stroke, t + stroke * 1.2f, r - stroke, b - stroke)
                canvas.drawRoundRect(tmp, stroke, stroke, paint)
                canvas.drawLine(l + (r - l) * 0.28f, t + stroke * 1.2f, r - (r - l) * 0.28f, t + stroke * 1.2f, paint)
                tmp.set(l + (r - l) * 0.28f, cy, r - (r - l) * 0.28f, b - stroke * 1.4f)
                canvas.drawRect(tmp, paint)
            }
            Kind.SCHEME -> {
                // stacked lines (list)
                val g = (b - t) / 4f
                for (i in 0..2) {
                    val y = t + g * (i + 1)
                    canvas.drawLine(l + stroke, y, r - stroke, y, paint)
                }
            }
            Kind.PARAMS -> {
                // gear: circle + 4 ticks
                val rad = (r - l) / 2f * 0.42f
                canvas.drawCircle(cx, cy, rad, paint)
                for (i in 0..3) {
                    val a = Math.toRadians((i * 90).toDouble())
                    val cos = Math.cos(a).toFloat()
                    val sin = Math.sin(a).toFloat()
                    canvas.drawLine(
                        cx + cos * rad * 0.85f, cy + sin * rad * 0.85f,
                        cx + cos * rad * 1.55f, cy + sin * rad * 1.55f,
                        paint
                    )
                }
            }
            Kind.CLOSE -> {
                // × in thin box optional — just ×
                canvas.drawLine(l + stroke, t + stroke, r - stroke, b - stroke, paint)
                canvas.drawLine(r - stroke, t + stroke, l + stroke, b - stroke, paint)
            }
        }
    }
}
