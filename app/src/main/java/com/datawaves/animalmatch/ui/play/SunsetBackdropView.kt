package com.datawaves.animalmatch.ui.play

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Sky → mountain → twinkles backdrop. Used as a foreground (or behind boardView) when the
 * window background alone isn't enough to give the play area its sunset look.
 */
class SunsetBackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val skyTop = Color.parseColor("#F8B86A")    // warm peach
    private val skyMid = Color.parseColor("#F26F5A")    // coral
    private val skyBottom = Color.parseColor("#7A3F8E") // dusk purple
    private val mountainColor = Color.parseColor("#2A1B3D")

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mountainColor }
    private val twinklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val mountainPath = Path()
    private data class Twinkle(val x: Float, val y: Float, val r: Float, val phase: Float)
    private val twinkles = ArrayList<Twinkle>()

    private var twinkleAlpha: Float = 0.6f
    private val twinkleAnimator: ValueAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
        duration = 2400L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            twinkleAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(skyTop, skyMid, skyBottom),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        skyPaint.shader = shader

        rebuildMountains(w, h)
        rebuildTwinkles(w, h)
    }

    private fun rebuildMountains(w: Int, h: Int) {
        mountainPath.reset()
        val baseY = h.toFloat()
        val topPeak1Y = h * 0.82f
        val topPeak2Y = h * 0.78f
        val saddleY = h * 0.88f
        mountainPath.moveTo(0f, baseY)
        mountainPath.lineTo(0f, h * 0.86f)
        mountainPath.lineTo(w * 0.22f, topPeak1Y)
        mountainPath.lineTo(w * 0.40f, saddleY)
        mountainPath.lineTo(w * 0.60f, topPeak2Y)
        mountainPath.lineTo(w * 0.78f, saddleY)
        mountainPath.lineTo(w * 0.92f, h * 0.85f)
        mountainPath.lineTo(w.toFloat(), h * 0.88f)
        mountainPath.lineTo(w.toFloat(), baseY)
        mountainPath.close()
    }

    private fun rebuildTwinkles(w: Int, h: Int) {
        twinkles.clear()
        val rng = Random(0xA11ABE15) // stable seed so it doesn't shimmer on rotate
        val count = 26
        val dp = resources.displayMetrics.density
        for (i in 0 until count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h * 0.55f
            val r = (1f + rng.nextFloat() * 1.4f) * dp
            val phase = rng.nextFloat()
            twinkles.add(Twinkle(x, y, r, phase))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!twinkleAnimator.isStarted) twinkleAnimator.start()
    }

    override fun onDetachedFromWindow() {
        twinkleAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Sky
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // Twinkles
        for (t in twinkles) {
            val localAlpha = (twinkleAlpha * (0.5f + t.phase * 0.5f)).coerceIn(0f, 1f)
            twinklePaint.alpha = (localAlpha * 230f).toInt().coerceIn(0, 255)
            canvas.drawCircle(t.x, t.y, t.r, twinklePaint)
        }
        twinklePaint.alpha = 255

        // Mountain silhouette
        canvas.drawPath(mountainPath, mountainPaint)
    }
}
