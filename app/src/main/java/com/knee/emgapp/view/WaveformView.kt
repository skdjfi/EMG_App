package com.knee.emgapp.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.knee.emgapp.R
import com.knee.emgapp.theme.Theme
import kotlin.math.abs

/**
 * 波形详情页大波形: 霓虹辉光(双层描边模拟) + 网格, 1 秒滚动窗口。
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val path = Path()
    private var data: List<Float> = emptyList()
    private var accent = 0

    init {
        accent = Theme.color(context, R.attr.accent)
        linePaint.color = accent
        glowPaint.color = accent
        gridPaint.color = Theme.color(context, R.attr.track)
        gridPaint.strokeWidth = 1f
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun setData(list: List<Float>) {
        data = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 网格
        for (i in 1..3) {
            val y = h * i / 4f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        for (i in 1..2) {
            val x = w * i / 3f
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }

        if (data.size < 2) return

        val max = data.maxOfOrNull { abs(it) } ?: 1f
        val scale = maxOf(40f, max)
        val pad = h * 0.12f
        val amp = (h - pad * 2f) / 2f

        path.reset()
        val n = data.size
        val stepX = w / (n - 1)
        for (i in 0 until n) {
            val x = i * stepX
            val y = h / 2f - (data[i] / scale) * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // 辉光: 先画粗的半透明底层, 再画亮线
        glowPaint.strokeWidth = dp(7f)
        glowPaint.alpha = 55
        canvas.drawPath(path, glowPaint)
        glowPaint.alpha = 255

        canvas.drawPath(path, linePaint)
    }
}