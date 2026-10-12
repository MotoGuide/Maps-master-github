package org.zarroboogs.maps.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class SignalBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var dbm: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#333333")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(84f).toInt(), widthMeasureSpec)
        val h = resolveSize(dp(20f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val barCount = 4
        val barW = dp(7f)
        val gap = dp(3f)
        val minH = h * 0.25f * 0

        val barCountToShow = when {
            dbm == 0 -> 0
            dbm >= -60 -> 4
            dbm >= -70 -> 3
            dbm >= -80 -> 2
            else -> 1
        }

        val barColor = when {
            dbm == 0 -> Color.GRAY
            dbm >= -60 -> Color.parseColor("#4CAF50")
            dbm >= -80 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        activePaint.color = barColor

        for (i in 0 until barCount) {
            val left = i * (barW + gap)
            val barH = minH + (h - minH) * (i + 1) / barCount
            val top = h - barH
            val rect = RectF(left, top, left + barW, h)
            val paint = if (i < barCountToShow) activePaint else inactivePaint
            canvas.drawRoundRect(rect, dp(2f), dp(2f), paint)
        }

    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
