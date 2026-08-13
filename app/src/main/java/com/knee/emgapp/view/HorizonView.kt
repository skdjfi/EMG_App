package com.knee.emgapp.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.knee.emgapp.R
import com.knee.emgapp.theme.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 人工地平仪: 天/地随 ROLL/PITCH 倾斜, 圆环刻度 + YAW 指示点, 中心十字准星。
 */
class HorizonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var roll = 0f
    private var pitch = 0f
    private var yaw = 0f

    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Theme.color(context, R.attr.accent)

    private val clipPath = Path()

    init {
        skyPaint.color = Theme.color(context, R.attr.accentDim)
        groundPaint.color = Theme.color(context, R.attr.card2)
        edgePaint.color = Theme.color(context, R.attr.chipEdge)
        edgePaint.strokeWidth = 1.5f
        linePaint.color = accent
        tickPaint.color = Theme.color(context, R.attr.cardEdge)
        dotPaint.color = accent
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun setAttitude(rollDeg: Float, pitchDeg: Float, yawDeg: Float) {
        roll = rollDeg
        pitch = pitchDeg
        yaw = yawDeg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 6f

        clipPath.reset()
        clipPath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 天/地
        canvas.save()
        canvas.rotate(roll, cx, cy)
        val pitchShift = pitch / 30f * r
        canvas.translate(0f, pitchShift)
        canvas.drawRect(-r, -r, cx * 2 + r, cy, skyPaint)
        canvas.drawRect(-r, cy, cx * 2 + r, cy * 2 + r, groundPaint)
        canvas.drawLine(-r, cy, cx * 2 + r, cy, linePaint)
        canvas.restore()

        // 俯仰刻度线
        tickPaint.color = accent
        tickPaint.alpha = 140
        val step = r * 0.14f
        for (i in -2..2) {
            if (i == 0) continue
            val y = cy + i * step + pitchShift
            val len = if (i % 2 == 0) r * 0.22f else r * 0.12f
            canvas.drawLine(cx - len, y, cx + len, y, tickPaint)
        }
        tickPaint.alpha = 255
        tickPaint.color = Theme.color(context, R.attr.cardEdge)
        canvas.restore()

        // 外环 + 刻度
        canvas.drawCircle(cx, cy, r, edgePaint)
        tickPaint.strokeWidth = 2f
        tickPaint.color = accent
        for (i in 0 until 8) {
            val ang = Math.toRadians(45.0 * i + 22.5)
            val x1 = cx + cos(ang) * (r - 3)
            val y1 = cy + sin(ang) * (r - 3)
            val x2 = cx + cos(ang) * (r - 11)
            val y2 = cy + sin(ang) * (r - 11)
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // YAW 指示点(随 yaw 旋转)
        val ya = Math.toRadians(yaw.toDouble())
        val yx = cx + sin(ya) * (r - 18)
        val yy = cy - cos(ya) * (r - 18)
        canvas.drawCircle(yx.toFloat(), yy.toFloat(), 3f, dotPaint)

        // 固定中心十字准星
        val len = dp(11f)
        dotPaint.alpha = 200
        canvas.drawLine(cx - len, cy, cx + len, cy, linePaint)
        canvas.drawLine(cx, cy - len, cx, cy + len, linePaint)
        dotPaint.alpha = 255
        canvas.drawCircle(cx, cy, 4.5f, dotPaint)
    }
}