package com.knee.emgapp.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.knee.emgapp.R
import com.knee.emgapp.theme.Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 科技风膝关节模型: 金属渐变骨骼 + 能量流动线 + 关节扫描环 + 雷达网格 + 发光角度弧。
 * 小腿绕膝关节旋转, 角度 = 屈曲角 flex。
 */
class KneeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var flex = 62f
    private var startMs = System.currentTimeMillis()

    private val accent = Theme.color(context, R.attr.accent)
    private val card2 = Theme.color(context, R.attr.card2)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Theme.color(context, R.attr.cardEdge)
    }
    private val dashRefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Theme.color(context, R.attr.cardEdge)
        pathEffect = DashPathEffect(floatArrayOf(12f, 18f), 0f)
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val boneCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 140
    }
    private val energyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = accent
        alpha = 200
        pathEffect = DashPathEffect(floatArrayOf(6f, 28f), 0f)
    }
    private val musclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = accent
        alpha = 150
    }
    private val jointFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val jointRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accent
    }
    private val jointRing2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accent
        alpha = 130
        pathEffect = DashPathEffect(floatArrayOf(5f, 9f), 0f)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        color = accent
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

    fun setFlex(v: Float) {
        flex = v.coerceIn(0f, 120f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val t = (System.currentTimeMillis() - startMs) / 1000f

        // 骨骼渐变
        bonePaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            Theme.color(context, R.attr.cardEdge),
            Theme.color(context, R.attr.textSecondary),
            Shader.TileMode.CLAMP
        )
        boneCorePaint.color = Theme.color(context, R.attr.textSecondary)
        jointFill.color = card2
        tipPaint.color = accent

        // 雷达网格
        gridPaint.strokeWidth = 1f
        val rr = w * 0.42f
        canvas.drawCircle(cx, cy, rr, gridPaint)
        canvas.drawCircle(cx, cy, rr * 0.68f, gridPaint)
        canvas.drawCircle(cx, cy, rr * 0.36f, gridPaint)
        canvas.drawLine(cx, cy - rr, cx, cy + rr, gridPaint)
        canvas.drawLine(cx - rr, cy, cx + rr, cy, gridPaint)
        canvas.drawLine(cx - rr * 0.7f, cy - rr * 0.7f, cx + rr * 0.7f, cy + rr * 0.7f, gridPaint)
        canvas.drawLine(cx + rr * 0.7f, cy - rr * 0.7f, cx - rr * 0.7f, cy + rr * 0.7f, gridPaint)

        // 0° 参考线
        canvas.drawLine(cx - rr, cy, cx + rr, cy, dashRefPaint)

        // 大腿(股骨)+ 肌群 + 能量流
        val thighTop = cy - h * 0.44f
        canvas.drawLine(cx, thighTop, cx, cy, bonePaint)
        canvas.drawLine(cx, thighTop, cx, cy, boneCorePaint)
        energyPaint.pathEffect = DashPathEffect(floatArrayOf(6f, 28f), -t * 34f)
        canvas.drawLine(cx, thighTop, cx, cy, energyPaint)

        musclePaint.strokeWidth = 2.5f
        val mPath1 = Path()
        mPath1.moveTo(cx - w * 0.07f, cy - h * 0.42f)
        mPath1.quadTo(cx - w * 0.16f, cy - h * 0.26f, cx - w * 0.05f, cy - h * 0.02f)
        canvas.drawPath(mPath1, musclePaint)
        val mPath2 = Path()
        mPath2.moveTo(cx + w * 0.07f, cy - h * 0.42f)
        mPath2.quadTo(cx + w * 0.16f, cy - h * 0.26f, cx + w * 0.05f, cy - h * 0.02f)
        canvas.drawPath(mPath2, musclePaint)

        // 关节部
        canvas.drawCircle(cx, cy, w * 0.062f, jointFill)
        canvas.drawCircle(cx, cy, w * 0.062f, jointRing)
        canvas.save()
        canvas.rotate(t * 24f, cx, cy)
        canvas.drawCircle(cx, cy, w * 0.062f, jointRing2)
        canvas.restore()
        canvas.save()
        canvas.rotate(-t * 16f, cx, cy)
        canvas.drawCircle(cx, cy, w * 0.10f, jointRing2)
        canvas.restore()
        canvas.drawCircle(cx, cy, w * 0.018f, tipPaint)

        // 小腿(胫骨): 绕膝关节旋转 flex 度
        canvas.save()
        canvas.rotate(flex, cx, cy)
        val ankle = cy + h * 0.44f
        canvas.drawLine(cx, cy, cx, ankle, bonePaint)
        canvas.drawLine(cx, cy, cx, ankle, boneCorePaint)
        energyPaint.pathEffect = DashPathEffect(floatArrayOf(6f, 28f), -t * 34f - 60f)
        canvas.drawLine(cx, cy, cx, ankle, energyPaint)

        musclePaint.strokeWidth = 2.5f
        val mPath3 = Path()
        mPath3.moveTo(cx - w * 0.06f, cy + h * 0.02f)
        mPath3.quadTo(cx - w * 0.14f, cy + h * 0.16f, cx - w * 0.05f, cy + h * 0.38f)
        canvas.drawPath(mPath3, musclePaint)
        val mPath4 = Path()
        mPath4.moveTo(cx + w * 0.06f, cy + h * 0.02f)
        mPath4.quadTo(cx + w * 0.14f, cy + h * 0.16f, cx + w * 0.05f, cy + h * 0.38f)
        canvas.drawPath(mPath4, musclePaint)

        // 踝关节
        jointFill.color = Theme.color(context, R.attr.cardEdge)
        canvas.drawCircle(cx, ankle, w * 0.036f, jointFill)
        canvas.drawCircle(cx, ankle, w * 0.012f, tipPaint)
        canvas.restore()

        // 发光角度弧 (屈曲角)
        val arcR = w * 0.16f
        val arcRect = android.graphics.RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, 90f, -flex, false, arcPaint)
        val rad = (90f + flex) * PI.toFloat() / 180f
        val tipX = cx + cos(rad) * arcR
        val tipY = cy - sin(rad) * arcR
        canvas.drawCircle(tipX, tipY, 2.2f * resources.displayMetrics.density, tipPaint)

        // 漂浮粒子
        particlePaint.alpha = 160
        canvas.drawCircle(cx - w * 0.24f, cy - h * 0.34f + sin(t * 1.6f) * w * 0.02f, 1.6f, particlePaint)
        canvas.drawCircle(cx + w * 0.28f, cy - h * 0.2f + cos(t * 1.3f) * w * 0.02f, 1.4f, particlePaint)
        canvas.drawCircle(cx + w * 0.26f, cy + h * 0.2f + sin(t * 1.1f) * w * 0.018f, 1.5f, particlePaint)
        particlePaint.alpha = 255
    }
}