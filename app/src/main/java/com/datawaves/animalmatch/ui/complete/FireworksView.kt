package com.datawaves.animalmatch.ui.complete

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simple celebratory particle system used on the Level-Complete screen.
 * Bursts at random points in the upper portion of the view; particles arc outward under gravity
 * and fade out. Particles are pooled to avoid per-frame allocation.
 */
class FireworksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var ageMs = 0f
        var lifeMs = 1400f
        var color = Color.WHITE
        var alive = false
    }

    companion object {
        private const val MAX_PARTICLES = 400
        private const val PARTICLES_PER_BURST_MIN = 30
        private const val PARTICLES_PER_BURST_MAX = 50
        private const val BURST_INTERVAL_MS_MIN = 600L
        private const val BURST_INTERVAL_MS_MAX = 900L
        private const val FRAME_INTERVAL_MIN_NS = 16_000_000L  // ~60 fps cap
    }

    private val palette = intArrayOf(
        Color.parseColor("#FF5752"),
        Color.parseColor("#FF9F3E"),
        Color.parseColor("#FFC23B"),
        Color.parseColor("#5DE34A"),
        Color.parseColor("#2EC4E6"),
        Color.parseColor("#BA75FF"),
    )

    private val particles = Array(MAX_PARTICLES) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val dp: Float = resources.displayMetrics.density
    private val particleRadius = 2f * dp           // 4dp diameter
    private val gravityPxPerSec2 = 200f * dp       // 200dp/s^2 felt right at common densities

    private var running = false
    private var lastFrameNs = 0L
    private var nextBurstAtMs = 0L
    private val rng = Random(System.nanoTime())

    init {
        setWillNotDraw(false)
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNs = 0L
        nextBurstAtMs = System.currentTimeMillis()  // immediate first burst
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        // Quietly clear particles so a restart begins clean.
        for (p in particles) p.alive = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dtNs = if (lastFrameNs == 0L) 16_000_000L else (frameTimeNanos - lastFrameNs)
        if (dtNs < FRAME_INTERVAL_MIN_NS) {
            Choreographer.getInstance().postFrameCallback(this)
            return
        }
        lastFrameNs = frameTimeNanos
        val dt = (dtNs / 1_000_000_000f).coerceAtMost(0.05f) // clamp dt for stability

        // Bursts
        val nowMs = System.currentTimeMillis()
        if (nowMs >= nextBurstAtMs && width > 0 && height > 0) {
            spawnBurst()
            nextBurstAtMs = nowMs + rng.nextLong(BURST_INTERVAL_MS_MIN, BURST_INTERVAL_MS_MAX + 1)
        }

        // Update particles
        for (p in particles) {
            if (!p.alive) continue
            p.ageMs += dt * 1000f
            if (p.ageMs >= p.lifeMs) { p.alive = false; continue }
            p.vy += gravityPxPerSec2 * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun spawnBurst() {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = rng.nextFloat() * (w * 0.8f) + (w * 0.1f)
        val cy = rng.nextFloat() * (h * 0.55f) + (h * 0.08f)
        val color = palette[rng.nextInt(palette.size)]
        val count = rng.nextInt(PARTICLES_PER_BURST_MIN, PARTICLES_PER_BURST_MAX + 1)
        var spawned = 0
        for (p in particles) {
            if (spawned >= count) break
            if (p.alive) continue
            val angle = rng.nextFloat() * (2f * Math.PI.toFloat())
            val speed = (90f + rng.nextFloat() * 220f) * dp
            p.x = cx
            p.y = cy
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - (80f * dp) // small upward bias
            p.ageMs = 0f
            p.lifeMs = 1200f + rng.nextFloat() * 400f
            p.color = color
            p.alive = true
            spawned++
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            if (!p.alive) continue
            val t = (p.ageMs / p.lifeMs).coerceIn(0f, 1f)
            val alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            paint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(p.x, p.y, particleRadius, paint)
        }
    }
}
