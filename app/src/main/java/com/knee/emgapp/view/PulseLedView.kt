package com.knee.emgapp.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.knee.emgapp.R

/**
 * 呼吸状态灯: 实心圆点 + 外圈扩散波纹 + 柔光晕。
 * 激活时按 2s 周期循环 (对应设计稿 .led.on 的 pulse/ripple)。
 */
class PulseLedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private var glow: Paint? = null

    private var active = false
    private var color = resolveAttr(R.attr.danger)

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }

    private fun resolveAttr(attr: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        val c = ta.getColor(0, 0xFFE5484D.toInt())
        ta.recycle()
        return c
    }

    fun setActive(on: Boolean) {
        if (active == on) return
        active = on
        color = if (on) resolveAttr(R.attr.accent) else resolveAttr(R.attr.danger)
        dot.color = color
        ring.color = color
        glow = null
        if (on && !anim.isStarted) anim.start() else anim.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        if (r <= 0f) return

        if (active) {
            if (glow == null) {
                glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx, cy, r * 2.6f,
                        intArrayOf(color, 0x00FFFFFF),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawCircle(cx, cy, r * 2.6f, glow!!)
        }

        canvas.drawCircle(cx, cy, r, dot)

        if (active) {
            val t = anim.animatedValue as Float
            ring.alpha = ((1f - t) * 165).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r + t * r * 2.0f, ring)
        }
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}
