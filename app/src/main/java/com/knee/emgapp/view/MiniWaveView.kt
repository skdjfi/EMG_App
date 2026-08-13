package com.knee.emgapp.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.knee.emgapp.R
import com.knee.emgapp.theme.Theme
import kotlin.math.abs

/**
 * 主页通道卡内的迷你波形条(与设计稿一致: 一组竖向发光小条)。
 */
class MiniWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val BARS = 44
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
    }

    private var waveColor = 0
    private var data: List<Float> = emptyList()

    init {
        midPaint.color = Theme.color(context, R.attr.track)
    }

    fun setColor(color: Int) {
        waveColor = color
        barPaint.color = color
        invalidate()
    }

    fun setData(list: List<Float>) {
        data = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawLine(0f, h / 2f, w, h / 2f, midPaint)

        if (data.isEmpty()) return
        val step = w / BARS
        val barW = (step * 0.55f).coerceAtLeast(2f)
        val max = data.maxOfOrNull { abs(it) } ?: 0f
        val scale = maxOf(1f, max)

        val start = maxOf(0, data.size - BARS)
        for (k in 0 until BARS) {
            val v = data.getOrNull(start + k) ?: 0f
            val rel = abs(v) / scale
            val barH = (6f + rel * (h * 0.62f)).coerceAtLeast(3f)
            val cx = k * step + step / 2f
            canvas.drawRoundRect(
                cx - barW / 2f, h / 2f - barH / 2f,
                cx + barW / 2f, h / 2f + barH / 2f,
                barW / 2f, barW / 2f, barPaint
            )
        }
    }
}