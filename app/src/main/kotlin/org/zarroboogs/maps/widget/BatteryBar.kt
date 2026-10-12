package org.zarroboogs.maps.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BatteryBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var level: Int = -1
        set(value) {
            field = value.coerceIn(-1, 100)
            invalidate()
        }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#808080")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(140f).toInt(), widthMeasureSpec)
        val h = resolveSize(dp(24f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val terminalW = dp(4f)
        val terminalH = dp(8f)
        val corner = dp(4f)
        val pad = dp(2f)

        val bodyLeft = 0f
        val bodyRight = w - terminalW
        val body = RectF(bodyLeft, 0f, bodyRight, h)

        canvas.drawRoundRect(body, corner, corner, bodyPaint)

        val terminal = RectF(bodyRight, (h - terminalH) / 2f, w, (h + terminalH) / 2f)
        bodyPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(terminal, corner / 2, corner / 2, bodyPaint)
        bodyPaint.style = Paint.Style.STROKE

        if (level >= 0) {
            val fillColor = when {
                level < 20 -> Color.parseColor("#F44336")
                level < 60 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#4CAF50")
            }
            fillPaint.color = fillColor
            val fillWidth = pad + (body.width() - pad * 2) * level / 100f
            val fillRect = RectF(bodyLeft + pad, pad, fillWidth, h - pad)
            canvas.drawRoundRect(fillRect, corner - 1, corner - 1, fillPaint)
        }

    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
