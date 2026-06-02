package com.datawaves.animalmatch.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.datawaves.animalmatch.data.Progress
import com.datawaves.animalmatch.data.models.Level
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vertical scrolling level-select map. Level 1 sits at the bottom; level 15 at the top.
 * Nodes zigzag across the width on a sine curve. Parent is expected to be a ScrollView.
 *
 * Renders three node kinds (completed / current / locked) and a faint dashed connector
 * between consecutive nodes. The current node pulses gently to invite the tap.
 *
 * Background flair: twinkling stars across the upper sky plus periodic shooting-star streaks.
 */
class LevelMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun interface OnNodeClickListener {
        fun onNodeClick(levelId: Int)
    }

    var listener: OnNodeClickListener? = null

    private var levels: List<Level> = emptyList()
    private var progress: Progress? = null

    private val dp: Float = resources.displayMetrics.density

    // Sizes (in px, derived from dp)
    private val nodeRadius = 30f * dp                // 60dp diameter — glass ring needs a bit more room
    private val rowSpacing = 110f * dp               // vertical spacing per level
    private val topMargin = 64f * dp
    // Generous bottom margin so Level 1 (the bottom node) stays clear of the Play CTA.
    private val bottomMargin = 160f * dp
    private val touchSlopPx = 60f * dp
    private val numberTextSize = 22f * dp
    private val iconSize = 22f * dp
    private val lockedNumberTextSize = 24f * dp

    // Colors
    private val colorCompleted = Color.parseColor("#2EC4E6")
    private val colorCurrent = Color.parseColor("#FF9F3E")
    private val colorLocked = Color.parseColor("#3A3F55")
    private val colorLockedInner = Color.parseColor("#4F4868")
    private val colorLockedOuter = Color.parseColor("#1F1A33")
    private val colorLockedBadge = Color.parseColor("#FFC23B")
    private val colorLockedBadgeDark = Color.parseColor("#B3801E")
    private val colorNodeStrokeCompleted = Color.parseColor("#1FA3C2")
    private val colorNodeStrokeCurrent = Color.parseColor("#D77A20")
    private val colorNodeStrokeLocked = Color.parseColor("#262B3D")
    private val colorPath = Color.parseColor("#4DFFFFFF")
    private val colorGlow = Color.parseColor("#66FF9F3E")
    private val colorWhite = Color.WHITE

    // Paints (allocated once)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * dp
        color = colorPath
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 12f * dp), 0f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colorGlow
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textAlign = Paint.Align.CENTER
        textSize = numberTextSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        style = Paint.Style.FILL
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Big embossed level number drawn inside locked medallions.
    private val lockedNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textAlign = Paint.Align.CENTER
        textSize = lockedNumberTextSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        alpha = 110
    }
    // Soft shadow under the embossed number for the engraved effect.
    private val lockedNumberShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
        textAlign = Paint.Align.CENTER
        textSize = lockedNumberTextSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    // Diagonal shimmer sweep across locked medallions.
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    // Lock body / shackle paints for the floating padlock badge.
    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorLockedBadge
        style = Paint.Style.FILL
    }
    private val lockBodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorLockedBadgeDark
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val lockShacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorLockedBadgeDark
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val lockKeyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorLockedBadgeDark
        style = Paint.Style.FILL
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        style = Paint.Style.FILL
    }
    private val shootingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * dp
    }

    // Reusable scratch
    private val connectPath = Path()
    private val iconPath = Path()

    // ---- Glass-ring node paints ----
    // Inner translucent fill — lets the background art glow through the node, selling the glass feel.
    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FFFFFF
    }
    // Outer ring stroke — a bright translucent rim.
    private val glassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * dp
        color = 0xCCFFFFFF.toInt()
    }
    // Inner ring stroke — slightly inset darker rim that makes the glass look beveled.
    private val glassInnerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = 0x55000000
    }
    // Crescent highlight along the upper-left quadrant of the ring.
    private val glassHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = 0xCCFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    // Number on the glass face.
    private val glassNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textAlign = Paint.Align.CENTER
        textSize = 24f * dp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val glassNumberShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        textAlign = Paint.Align.CENTER
        textSize = 24f * dp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    // Star paints — gold for earned, dim grey for missed.
    private val starEarnedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD23B")
        style = Paint.Style.FILL
    }
    private val starEarnedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3801E")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
        strokeJoin = Paint.Join.ROUND
    }
    private val starMissedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
        style = Paint.Style.FILL
    }
    private val starMissedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
        strokeJoin = Paint.Join.ROUND
    }
    private val starPath = Path()
    private val glassHighlightRect = RectF()

    // Tap tracking
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var pressedLevelId: Int = -1
    private var pressScale: Float = 1f
    private var pressAnimator: ValueAnimator? = null

    // Pulse animator for current node (alpha+scale glow)
    private var pulseScale = 1f
    private var pulseGlowAlpha = 0.6f
    private var pulseAnimator: ValueAnimator? = null

    // Locked-node ambient animation: shimmer sweep (0→1 across a 2.6s loop) + lock sway phase.
    private var lockedAmbientT: Float = 0f
    private var lockedAmbientAnimator: ValueAnimator? = null

    // One-shot rattle when the player taps a locked node — angle in degrees, decays to 0.
    private var rattleLevelId: Int = -1
    private var rattleAngle: Float = 0f
    private var rattleAnimator: ValueAnimator? = null

    // Scratch path/rect for locked-node drawing (re-used to avoid per-frame allocations).
    private val lockedClipPath = Path()
    private val lockedShimmerRect = RectF()
    private val lockedBadgeRect = RectF()

    // Stars
    private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
    private val stars = ArrayList<Star>(28)
    private var starAlphaT: Float = 0f
    private var starAnimator: ValueAnimator? = null

    // Shooting stars
    private data class Shooting(var startX: Float, var startY: Float, var dx: Float, var dy: Float, var t: Float, var lifeMs: Float)
    private val shooting = Shooting(0f, 0f, 0f, 0f, 1f, 1f)
    private var shootingActive: Boolean = false
    private var shootingAnimator: ValueAnimator? = null
    private var nextShootingAtMs: Long = 0L
    private val rng = Random(0xCAB008E5)

    init {
        // Background is supplied by the parent (gradient on the activity). We just need to draw.
        setWillNotDraw(false)
        startPulse()
        startTwinkle()
        startLockedAmbient()
    }

    fun bind(levels: List<Level>, progress: Progress) {
        this.levels = levels
        this.progress = progress
        requestLayout()
        invalidate()
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                pulseScale = 1f + 0.15f * t
                pulseGlowAlpha = (0.6f * (1f - t)).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    private fun startLockedAmbient() {
        lockedAmbientAnimator?.cancel()
        lockedAmbientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            // Long loop so the shimmer feels ambient, not flashy.
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            // Linear so the shimmer band sweeps at a steady pace; the sway uses its own sin().
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                lockedAmbientT = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startTwinkle() {
        starAnimator?.cancel()
        starAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                starAlphaT = it.animatedValue as Float
                maybeSpawnShootingStar()
                invalidate()
            }
            start()
        }
        nextShootingAtMs = System.currentTimeMillis() + 4500L
    }

    private fun maybeSpawnShootingStar() {
        if (shootingActive) return
        val now = System.currentTimeMillis()
        if (now < nextShootingAtMs) return
        val w = width
        if (w <= 0) return
        // Spawn in upper third of the visible area.
        val sx = rng.nextFloat() * w * 0.6f
        val sy = topMargin + rng.nextFloat() * (height * 0.25f)
        val len = (140f + rng.nextFloat() * 80f) * dp
        val dx = len
        val dy = len * 0.6f
        shooting.startX = sx
        shooting.startY = sy
        shooting.dx = dx
        shooting.dy = dy
        shooting.t = 0f
        shooting.lifeMs = 700f
        shootingActive = true
        shootingAnimator?.cancel()
        shootingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = shooting.lifeMs.toLong()
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                shooting.t = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    shootingActive = false
                    nextShootingAtMs = System.currentTimeMillis() + (5000L + rng.nextLong(0L, 5000L))
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        pulseAnimator = null
        starAnimator?.cancel()
        starAnimator = null
        shootingAnimator?.cancel()
        shootingAnimator = null
        pressAnimator?.cancel()
        pressAnimator = null
        lockedAmbientAnimator?.cancel()
        lockedAmbientAnimator = null
        rattleAnimator?.cancel()
        rattleAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pulseAnimator == null) startPulse()
        if (starAnimator == null) startTwinkle()
        if (lockedAmbientAnimator == null) startLockedAmbient()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val count = if (levels.isEmpty()) 15 else levels.size
        val h = (topMargin + bottomMargin + (count - 1) * rowSpacing).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildStars(w, h)
    }

    private fun rebuildStars(w: Int, h: Int) {
        stars.clear()
        if (w <= 0 || h <= 0) return
        // Stars distributed across the top 55% of the map. Stable seed so they don't reshuffle.
        val r = Random(0xA11ABE15)
        val count = 28
        val topZone = h * 0.55f
        for (i in 0 until count) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * topZone
            val rad = (1.2f + r.nextFloat() * 1.6f) * dp
            stars.add(Star(x, y, rad, r.nextFloat()))
        }
    }

    /**
     * X coordinate for the i-th level node (i=0 -> level 1 at bottom).
     * Uses a sine wave to give ~3 visible bends across 15 nodes.
     */
    private fun xForIndex(i: Int): Float {
        val n = (if (levels.isEmpty()) 15 else levels.size) - 1
        val phase = if (n <= 0) 0f else (i.toFloat() / n.toFloat()) * (2f * Math.PI.toFloat())
        val amp = width * 0.28f
        val cx = width / 2f
        // *3 gives ~3 bends across the column
        return cx + amp * sin(phase * 3f)
    }

    /** Y coordinate for the i-th level node. i=0 -> level 1 -> bottom. */
    private fun yForIndex(i: Int): Float {
        val total = (if (levels.isEmpty()) 15 else levels.size) - 1
        val fromBottom = topMargin + (total - i) * rowSpacing
        return fromBottom
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 0) Stars — draw before nodes/paths so nodes sit on top.
        drawStars(canvas)

        // 0b) Shooting star, if active.
        if (shootingActive) drawShootingStar(canvas)

        if (levels.isEmpty()) return

        // 1) Connector path
        connectPath.reset()
        for (i in levels.indices) {
            val x = xForIndex(i)
            val y = yForIndex(i)
            if (i == 0) connectPath.moveTo(x, y) else connectPath.lineTo(x, y)
        }
        canvas.drawPath(connectPath, pathPaint)

        // 2) Nodes
        val prog = progress
        for (i in levels.indices) {
            val level = levels[i]
            val x = xForIndex(i)
            val y = yForIndex(i)
            val kind: NodeKind = when {
                prog == null -> NodeKind.LOCKED
                prog.isCompleted(level.id) -> NodeKind.COMPLETED
                prog.isUnlocked(level.id) -> NodeKind.CURRENT
                else -> NodeKind.LOCKED
            }
            val scale = if (pressedLevelId == level.id) pressScale else 1f
            drawNode(canvas, x, y, level.id, kind, scale)
        }
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            // Each star has a phase offset so they don't pulse in lockstep.
            val a = (0.3f + 0.7f * (0.5f + 0.5f * sin((starAlphaT + s.phase) * Math.PI.toFloat() * 2f))).coerceIn(0f, 1f)
            starPaint.alpha = (a * 230f).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
        starPaint.alpha = 255
    }

    private fun drawShootingStar(canvas: Canvas) {
        val t = shooting.t.coerceIn(0f, 1f)
        // Head position along the trajectory.
        val headX = shooting.startX + shooting.dx * t
        val headY = shooting.startY + shooting.dy * t
        // Tail length is fixed but alpha along the line goes from 0 → 1 toward the head.
        val tailLen = 60f * dp
        val unitX = shooting.dx
        val unitY = shooting.dy
        val mag = hypot(unitX, unitY)
        if (mag <= 0f) return
        val tx = headX - (unitX / mag) * tailLen
        val ty = headY - (unitY / mag) * tailLen
        // Fade overall by life t (in & out).
        val envelope = if (t < 0.5f) (t / 0.5f) else (1f - (t - 0.5f) / 0.5f)
        shootingPaint.alpha = (envelope * 220f).toInt().coerceIn(0, 255)
        canvas.drawLine(tx, ty, headX, headY, shootingPaint)
        // Bright head dot.
        starPaint.alpha = (envelope * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(headX, headY, 2.2f * dp, starPaint)
        starPaint.alpha = 255
        shootingPaint.alpha = 255
    }

    private fun drawNode(canvas: Canvas, cx: Float, cy: Float, levelId: Int, kind: NodeKind, scale: Float) {
        val r = nodeRadius * scale
        // Current node gets a pulsing outer aura before we draw the glass ring.
        if (kind == NodeKind.CURRENT) {
            val ringR = nodeRadius * pulseScale
            glowPaint.alpha = (pulseGlowAlpha * 255f).toInt().coerceIn(0, 255)
            glowPaint.strokeWidth = 4f * dp
            glowPaint.color = colorGlow
            canvas.drawCircle(cx, cy, ringR, glowPaint)
            glowPaint.alpha = 255
        }
        // 1) Glass body — same shape for every node so the map reads as a single coherent set.
        drawGlassRing(canvas, cx, cy, r, kind)

        // 2) Overlay — lock for locked, stars + number for cleared, number for current.
        when (kind) {
            NodeKind.COMPLETED -> {
                drawGlassNumber(canvas, cx, cy, r, levelId)
                val stars = progress?.getStars(levelId)?.coerceAtLeast(1) ?: 1
                drawStarsAbove(canvas, cx, cy, r, stars)
            }
            NodeKind.CURRENT -> {
                drawGlassNumber(canvas, cx, cy, r, levelId)
            }
            NodeKind.LOCKED -> {
                drawLockBadge(canvas, cx, cy, r, levelId)
            }
        }
    }

    /**
     * The shared glass ring shape: translucent fill, two strokes (bright rim + inset bevel), and
     * a crescent highlight on the upper-left to sell the glossy feel. Drawn for every node state.
     */
    private fun drawGlassRing(canvas: Canvas, cx: Float, cy: Float, r: Float, kind: NodeKind) {
        // Soft fill — locked nodes go a touch darker so they read as inactive.
        val fillAlpha = if (kind == NodeKind.LOCKED) 0x22 else 0x44
        glassFillPaint.color = (fillAlpha shl 24) or 0x00FFFFFF
        canvas.drawCircle(cx, cy, r, glassFillPaint)
        // Bright outer rim — current/completed glow brighter than locked.
        val rimAlpha = when (kind) {
            NodeKind.CURRENT -> 0xEE
            NodeKind.COMPLETED -> 0xDD
            NodeKind.LOCKED -> 0x99
        }
        glassRingPaint.color = (rimAlpha shl 24) or 0x00FFFFFF
        canvas.drawCircle(cx, cy, r - 1.5f * dp, glassRingPaint)
        // Subtle inner bevel.
        canvas.drawCircle(cx, cy, r - 4f * dp, glassInnerRimPaint)
        // Crescent highlight on upper-left.
        glassHighlightRect.set(cx - r + 4f * dp, cy - r + 4f * dp, cx + r - 4f * dp, cy + r - 4f * dp)
        canvas.drawArc(glassHighlightRect, 200f, 70f, false, glassHighlightPaint)
    }

    /** Draws the level number centered on the glass face with a soft shadow for depth. */
    private fun drawGlassNumber(canvas: Canvas, cx: Float, cy: Float, r: Float, levelId: Int) {
        val fm = glassNumberPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(levelId.toString(), cx + 1.2f * dp, baseline + 1.6f * dp, glassNumberShadowPaint)
        canvas.drawText(levelId.toString(), cx, baseline, glassNumberPaint)
    }

    /**
     * Three-star ribbon above the glass ring. Filled gold stars represent earned, hollow grey
     * stars represent the ones the player hasn't matched yet.
     */
    private fun drawStarsAbove(canvas: Canvas, cx: Float, cy: Float, r: Float, earned: Int) {
        val starR = 5.5f * dp
        val gap = 4f * dp
        val totalW = 3 * (starR * 2) + 2 * gap
        val startX = cx - totalW / 2f + starR
        val starY = cy - r - starR - 4f * dp
        for (i in 0 until 3) {
            val sx = startX + i * (starR * 2 + gap)
            val filled = i < earned
            drawStar(canvas, sx, starY, starR, filled)
        }
    }

    /** Five-pointed star centered at (cx, cy), inner radius half of outer. */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, filled: Boolean) {
        starPath.reset()
        val innerR = outerR * 0.45f
        var angle = -Math.PI.toFloat() / 2f
        val step = Math.PI.toFloat() / 5f
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) outerR else innerR
            val px = cx + rr * cos(angle)
            val py = cy + rr * sin(angle)
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
            angle += step
        }
        starPath.close()
        if (filled) {
            canvas.drawPath(starPath, starEarnedPaint)
            canvas.drawPath(starPath, starEarnedStrokePaint)
        } else {
            canvas.drawPath(starPath, starMissedPaint)
            canvas.drawPath(starPath, starMissedStrokePaint)
        }
    }

    /**
     * Lock badge for locked nodes — a centered gold padlock with the rattle/sway animation
     * that the old medallion design used. We keep the shimmer-free, simpler look since the
     * glass body itself provides the polish.
     */
    private fun drawLockBadge(canvas: Canvas, cx: Float, cy: Float, r: Float, levelId: Int) {
        val phase = (levelId * 0.137f) % 1f
        val swayAngle = if (rattleLevelId == levelId) rattleAngle
        else 4f * sin((lockedAmbientT * 2f + phase) * Math.PI.toFloat() * 2f)
        val badgeR = r * 0.42f
        val save = canvas.save()
        canvas.rotate(swayAngle, cx, cy)
        drawPadlockBadge(canvas, cx, cy, badgeR)
        canvas.restoreToCount(save)
    }

    /**
     * Polished locked node: radial-gradient medallion, embossed level number, a small
     * gold padlock badge that swings gently, a slow diagonal shimmer sweep, and a tap rattle.
     */
    private fun drawLockedMedallion(canvas: Canvas, cx: Float, cy: Float, r: Float, levelId: Int) {
        // Per-node phase offset so the shimmer cascades across the column and the lock badges
        // don't sway in perfect lockstep.
        val phase = (levelId * 0.137f) % 1f

        // 1) Radial-gradient body — feels chiselled rather than flat.
        nodePaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.4f,
            intArrayOf(colorLockedInner, colorLocked, colorLockedOuter),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, nodePaint)
        nodePaint.shader = null

        // 2) Stroke ring.
        strokePaint.color = colorNodeStrokeLocked
        canvas.drawCircle(cx, cy, r, strokePaint)

        // 3) Embossed level number: dark shadow + faint light text.
        val fm = lockedNumberPaint.fontMetrics
        val numberBaseline = cy - (fm.ascent + fm.descent) / 2f - r * 0.08f
        lockedNumberShadowPaint.alpha = 110
        canvas.drawText(levelId.toString(), cx + 1.2f * dp, numberBaseline + 1.6f * dp, lockedNumberShadowPaint)
        lockedNumberPaint.alpha = 150
        canvas.drawText(levelId.toString(), cx, numberBaseline, lockedNumberPaint)
        lockedNumberPaint.alpha = 255

        // 4) Shimmer sweep — clip to the circle and draw a diagonal light band that
        // travels from upper-left to lower-right based on the global ambient phase + offset.
        val t = ((lockedAmbientT + phase) % 1f)
        // The band spans about 40% of the diameter and travels across ~250% of it so it fully
        // enters and exits the medallion.
        val bandWidth = r * 0.8f
        val travel = r * 5f
        val centerOffset = -r * 2.5f + travel * t
        val saveCount = canvas.save()
        lockedClipPath.reset()
        lockedClipPath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(lockedClipPath)
        // Diagonal direction (45°).
        val dx = bandWidth * 0.707f
        val dy = bandWidth * 0.707f
        val x0 = cx + centerOffset - dx
        val y0 = cy - r - dy
        val x1 = cx + centerOffset + dx
        val y1 = cy + r + dy
        shimmerPaint.shader = LinearGradient(
            x0, y0, x1, y1,
            intArrayOf(0x00FFFFFF, 0x55FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        lockedShimmerRect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawRect(lockedShimmerRect, shimmerPaint)
        shimmerPaint.shader = null
        canvas.restoreToCount(saveCount)

        // 5) Floating padlock badge in the lower-right quadrant of the medallion.
        // Sway angle uses a slow sine (different phase per level). When this is the
        // node being rattled, swap in the rattle angle.
        val swayAngle = if (rattleLevelId == levelId) rattleAngle
        else 4f * sin((lockedAmbientT * 2f + phase) * Math.PI.toFloat() * 2f)
        val badgeR = r * 0.38f
        val bcx = cx + r * 0.48f
        val bcy = cy + r * 0.48f
        val badgeSave = canvas.save()
        canvas.rotate(swayAngle, bcx, bcy)
        drawPadlockBadge(canvas, bcx, bcy, badgeR)
        canvas.restoreToCount(badgeSave)
    }

    /** Draws a small gold padlock (body + shackle + keyhole) centered at (bcx, bcy). */
    private fun drawPadlockBadge(canvas: Canvas, bcx: Float, bcy: Float, badgeR: Float) {
        // Body — rounded rectangle.
        val bodyW = badgeR * 1.6f
        val bodyH = badgeR * 1.5f
        lockedBadgeRect.set(bcx - bodyW / 2f, bcy - bodyH * 0.25f, bcx + bodyW / 2f, bcy + bodyH * 0.75f)
        canvas.drawRoundRect(lockedBadgeRect, badgeR * 0.35f, badgeR * 0.35f, lockBodyPaint)
        canvas.drawRoundRect(lockedBadgeRect, badgeR * 0.35f, badgeR * 0.35f, lockBodyStrokePaint)
        // Shackle — arc above the body.
        lockedBadgeRect.set(bcx - bodyW * 0.35f, bcy - bodyH * 0.95f, bcx + bodyW * 0.35f, bcy - bodyH * 0.15f)
        canvas.drawArc(lockedBadgeRect, 180f, 180f, false, lockShacklePaint)
        // Keyhole — tiny circle.
        canvas.drawCircle(bcx, bcy + bodyH * 0.18f, badgeR * 0.18f, lockKeyholePaint)
    }

    private fun drawNumberBadge(canvas: Canvas, cx: Float, cy: Float, levelId: Int) {
        // For current node, we already show the play arrow center; place small number below the node.
        val below = cy + nodeRadius + 14f * dp
        val fm = numberPaint.fontMetrics
        val baseline = below - (fm.ascent + fm.descent) / 2f
        canvas.drawText(levelId.toString(), cx, baseline, numberPaint)
    }

    private fun drawPlayArrow(canvas: Canvas, cx: Float, cy: Float) {
        val s = iconSize / 2f
        iconPath.reset()
        iconPath.moveTo(cx - s * 0.35f, cy - s * 0.65f)
        iconPath.lineTo(cx + s * 0.65f, cy)
        iconPath.lineTo(cx - s * 0.35f, cy + s * 0.65f)
        iconPath.close()
        iconPaint.style = Paint.Style.FILL
        canvas.drawPath(iconPath, iconPaint)
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float) {
        val s = iconSize / 2f
        iconPath.reset()
        iconPath.moveTo(cx - s * 0.55f, cy + s * 0.05f)
        iconPath.lineTo(cx - s * 0.10f, cy + s * 0.50f)
        iconPath.lineTo(cx + s * 0.65f, cy - s * 0.45f)
        canvas.drawPath(iconPath, iconStrokePaint)
    }

    /** Triggers a damped left-right wobble on the padlock badge of a single locked level. */
    fun rattleLocked(levelId: Int) {
        rattleAnimator?.cancel()
        rattleLevelId = levelId
        rattleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                // Damped oscillation: amplitude decays as the animation progresses.
                val amp = (1f - p) * 18f
                rattleAngle = amp * sin(p * Math.PI.toFloat() * 6f) * cos(p * Math.PI.toFloat())
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    rattleAngle = 0f
                    rattleLevelId = -1
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTimeMs = System.currentTimeMillis()
                val hit = hitTest(event.x, event.y)
                if (hit != null) {
                    pressedLevelId = hit.id
                    startPressAnim(true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val movedFar = hypot(dx, dy) > 18f * dp
                val tooLong = (System.currentTimeMillis() - downTimeMs) > 600
                if (pressedLevelId != -1) {
                    startPressAnim(false)
                }
                if (!movedFar && !tooLong) {
                    val hit = hitTest(event.x, event.y)
                    if (hit != null) {
                        listener?.onNodeClick(hit.id)
                        return true
                    }
                }
                pressedLevelId = -1
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressedLevelId != -1) startPressAnim(false)
                pressedLevelId = -1
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startPressAnim(pressed: Boolean) {
        pressAnimator?.cancel()
        val target = if (pressed) 0.92f else 1f
        pressAnimator = ValueAnimator.ofFloat(pressScale, target).apply {
            duration = 120L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!pressed) {
                        pressScale = 1f
                        pressedLevelId = -1
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun hitTest(x: Float, y: Float): Level? {
        var best: Level? = null
        var bestDist = Float.MAX_VALUE
        for (i in levels.indices) {
            val nx = xForIndex(i)
            val ny = yForIndex(i)
            val d = hypot(x - nx, y - ny)
            if (d < bestDist && d <= touchSlopPx) {
                bestDist = d
                best = levels[i]
            }
        }
        return best
    }

    private enum class NodeKind { COMPLETED, CURRENT, LOCKED }
}
