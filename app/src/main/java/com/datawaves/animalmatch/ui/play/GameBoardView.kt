package com.datawaves.animalmatch.ui.play

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.datawaves.animalmatch.data.models.ConnectPath
import com.datawaves.animalmatch.data.models.MatchResult
import com.datawaves.animalmatch.data.models.TilePos
import com.datawaves.animalmatch.game.MatchEngine
import com.datawaves.animalmatch.ui.DesignTokens
import com.datawaves.animalmatch.util.AssetCache
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom view that draws and drives the connect-tile board. Pixel-perfect for the design;
 * heavy lifting (bitmap decode) goes through [AssetCache] on a background thread.
 *
 * Animations:
 *  • Tap-to-select bounce on a tile (scale 1.0→1.08→1.0 over 180ms).
 *  • Progressive green path draw (220ms) followed by tile match-burst particles.
 *  • Red attempted-path flash (~300ms) plus red border on both selected tiles.
 *  • Hint sparkle pulses along the path for ~1.5s.
 *  • Bomb white flash + larger burst.
 *  • Shuffle fade-out / fade-in with per-tile rotation jitter.
 *  • Cascade entrance (per-tile fade+scale staggered by col+row * 15ms).
 */
class GameBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle), Choreographer.FrameCallback {

    interface Listener {
        fun onInvalidPair()
        fun onMatched()
        fun onCleared()
    }

    private var engine: MatchEngine? = null
    private var tileDrawables: List<String> = emptyList()
    private var listener: Listener? = null

    private var tileSize: Int = 0
    private var boardOffsetX: Float = 0f
    private var boardOffsetY: Float = 0f

    // Cached paints/path/rect — all allocated once.
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F000000.toInt() }
    private val paintTileBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paintTileBmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val paintSelectBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2EC4E6")
    }
    private val paintInvalidBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF5752")
    }
    // Three-layer neon path: outer blurred halo + mid stroke + inner highlight
    private val paintPathGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.PathOuterGlow
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintPathUnder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.darker(DesignTokens.PathMid, 0.55f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintPathOver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.PathMid
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintPathInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.PathInner
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintPathSpark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DesignTokens.PathHead
        style = Paint.Style.FILL
    }
    private val paintPathSparkGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DesignTokens.PathHead
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintInvalidPathGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.InvalidPathOuter
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintInvalidPath = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.InvalidPath
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintInvalidCross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.InvalidPath
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Cyan glow halo behind a selected tile (BlurMaskFilter for true neon halo).
    private val paintSelectGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DesignTokens.TileSelectGlow
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    // Shockwave ring (drawn at burst centers).
    private val paintShockwave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintSparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE96B")
    }
    private val paintFlash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val paintParticle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val tmpRect = RectF()
    private val tmpPath = Path()
    private val tmpDrawPath = Path()
    private val pathMeasure = PathMeasure()
    private val tmpPos = FloatArray(2)
    private val tmpTan = FloatArray(2)

    private val dp: Float = context.resources.displayMetrics.density

    private var cornerRadiusPx: Float = DesignTokens.TileCornerDp * dp
    private var shadowOffsetPx: Float = DesignTokens.TileShadowOffsetDp * dp
    private var selectStrokePx: Float = DesignTokens.TileSelectStrokeDp * dp
    private var pathGlowStrokePx: Float = DesignTokens.PathGlowStrokeDp * dp
    private var pathUnderStrokePx: Float = DesignTokens.PathMidStrokeDp * dp + 3f * dp
    private var pathOverStrokePx: Float = DesignTokens.PathMidStrokeDp * dp
    private var pathInnerStrokePx: Float = DesignTokens.PathInnerStrokeDp * dp
    private var pathHeadRadiusPx: Float = DesignTokens.PathHeadRadiusDp * dp

    /** Per-level accent color, set in [bind] from `engine.level.themeId`. */
    private var accentColor: Int = DesignTokens.PathMid

    // ---- Animation state ----
    private var matchPath: ConnectPath? = null
    /** 0..1 = progressive reveal of the green path. */
    private var matchProgress: Float = 0f
    /** Fade out after reveal completes. */
    private var matchAlpha: Float = 0f
    private var matchAnimator: ValueAnimator? = null

    private var invalidPair: Pair<TilePos, TilePos>? = null
    private var invalidAlpha: Float = 0f
    /** Number of times the red border flashes (we toggle border visibility per pulse). */
    private var invalidFlashT: Float = 0f
    private var invalidAnimator: ValueAnimator? = null

    private var hintPair: Triple<TilePos, TilePos, ConnectPath>? = null
    private var hintPulse: Float = 0f
    private var hintAnimator: ValueAnimator? = null

    private var shuffleFade: Float = 1f
    private var shuffleAnimator: ValueAnimator? = null
    /** Per-tile rotation jitter angles, set when shuffle starts. */
    private var shuffleJitterDeg: FloatArray = FloatArray(0)
    private var shuffleJitterCols: Int = 0

    private var boardShakeOffset: Float = 0f
    private var shakeAnimator: ValueAnimator? = null

    // Bomb flash overlay
    private var bombFlashAlpha: Float = 0f
    private var bombFlashAnimator: ValueAnimator? = null

    // Tile selection bounce: keyed by encoded position (col + row*cols).
    private var selectBounceKey: Int = -1
    private var selectBounceScale: Float = 1f
    private var selectBounceAnimator: ValueAnimator? = null

    // Match burst: tile fade-and-grow on a (col, row).
    private data class TileBurst(
        val col: Int, val row: Int,
        var bmp: Bitmap?, var startMs: Long, var durationMs: Long
    )
    private val tileBursts = ArrayList<TileBurst>()

    // Particle system for matches/bomb.
    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var ageMs = 0f
        var lifeMs = 500f
        var color = Color.WHITE
        var radius = 2f
        var alive = false
    }
    private val particles = Array(180) { Particle() }
    private class Shockwave {
        var cx = 0f; var cy = 0f
        var ageMs = 0f
        var lifeMs = 360f
        var startR = 0f
        var endR = 0f
        var color = Color.WHITE
        var alive = false
    }
    private val shockwaves = Array(8) { Shockwave() }
    private val particlePalette = intArrayOf(
        Color.parseColor("#FFC23B"),
        Color.parseColor("#5DE34A"),
        Color.parseColor("#FF9F3E"),
        Color.parseColor("#2EC4E6"),
        Color.parseColor("#FFE96B"),
    )
    private var lastFrameNs: Long = 0L
    private var frameLoopActive: Boolean = false
    private val rng = Random(System.nanoTime())
    private val gravityPxPerSec2: Float = 200f * dp

    // Cascade entrance
    private var cascadeStartMs: Long = 0L
    private var cascadeActive: Boolean = false
    private val cascadeTileDurationMs: Long = 280L
    private val cascadeStepMs: Long = 15L

    // Async preload
    private val ioExec = Executors.newSingleThreadExecutor()

    init {
        setWillNotDraw(false)
        // BlurMaskFilter requires software rendering on pre-API-28 hardware-accelerated views.
        if (android.os.Build.VERSION.SDK_INT < 28) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    fun bind(engine: MatchEngine, tileDrawables: List<String>, listener: Listener) {
        this.engine = engine
        this.tileDrawables = tileDrawables
        this.listener = listener
        // Resolve per-level accent and recolor path paints so every level feels distinct.
        accentColor = DesignTokens.themeAccent(engine.level.themeId)
        paintPathGlow.color = DesignTokens.withAlpha(DesignTokens.lighter(accentColor, 0.10f), 0.55f)
        paintPathUnder.color = DesignTokens.darker(accentColor, 0.60f)
        paintPathOver.color = accentColor
        paintPathInner.color = DesignTokens.lighter(accentColor, 0.55f)
        cascadeStartMs = System.currentTimeMillis() + 80L  // small initial delay
        cascadeActive = true
        ensureFrameLoop()
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeMetrics(w, h)
        preloadTiles()
    }

    private fun recomputeMetrics(w: Int, h: Int) {
        val e = engine ?: return
        val cols = e.level.grid.cols
        val rows = e.level.grid.rows
        if (cols <= 0 || rows <= 0 || w <= 0 || h <= 0) return
        // padded grid: cols + 2 wide, rows + 2 tall
        val byW = w / (cols + 2)
        val byH = h / (rows + 2)
        tileSize = min(byW, byH).coerceAtLeast(1)
        val playW = tileSize * cols
        val playH = tileSize * rows
        boardOffsetX = (w - playW) / 2f
        boardOffsetY = (h - playH) / 2f
        // Resize jitter buffer if needed.
        if (shuffleJitterDeg.size != cols * rows) {
            shuffleJitterDeg = FloatArray(cols * rows)
            shuffleJitterCols = cols
        }
    }

    private fun preloadTiles() {
        if (tileSize <= 0 || tileDrawables.isEmpty()) return
        val ctx = context.applicationContext
        val sz = tileSize
        val names = tileDrawables.toList()
        ioExec.execute {
            for (n in names) {
                try { AssetCache.getTile(ctx, n, sz) } catch (_: Throwable) { /* missing drawables OK */ }
            }
            post { invalidate() }
        }
    }

    // ---- Frame loop (drives particles + bursts + cascade) ----

    private fun ensureFrameLoop() {
        if (frameLoopActive) return
        frameLoopActive = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopFrameLoopIfIdle() {
        // Keep loop alive while anything visible is animating.
        if (tileBursts.isNotEmpty()) return
        if (anyParticleAlive()) return
        if (anyShockwaveAlive()) return
        if (cascadeActive) return
        frameLoopActive = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun anyParticleAlive(): Boolean {
        for (p in particles) if (p.alive) return true
        return false
    }

    private fun anyShockwaveAlive(): Boolean {
        for (s in shockwaves) if (s.alive) return true
        return false
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!frameLoopActive) return
        val dtNs = if (lastFrameNs == 0L) 16_000_000L else (frameTimeNanos - lastFrameNs)
        lastFrameNs = frameTimeNanos
        val dt = (dtNs / 1_000_000_000f).coerceAtMost(0.05f)

        // Advance particles
        for (p in particles) {
            if (!p.alive) continue
            p.ageMs += dt * 1000f
            if (p.ageMs >= p.lifeMs) { p.alive = false; continue }
            p.vy += gravityPxPerSec2 * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        // Advance shockwaves
        for (sw in shockwaves) {
            if (!sw.alive) continue
            sw.ageMs += dt * 1000f
            if (sw.ageMs >= sw.lifeMs) sw.alive = false
        }
        // Expire bursts
        val now = System.currentTimeMillis()
        var i = 0
        while (i < tileBursts.size) {
            val tb = tileBursts[i]
            if (now - tb.startMs >= tb.durationMs) {
                tileBursts.removeAt(i)
            } else {
                i++
            }
        }
        // Cascade timeout
        val e = engine
        if (cascadeActive && e != null) {
            val cols = e.level.grid.cols
            val rows = e.level.grid.rows
            val total = cascadeTileDurationMs + (cols + rows) * cascadeStepMs
            if (now - cascadeStartMs >= total) cascadeActive = false
        }

        invalidate()
        if (frameLoopActive) {
            if (tileBursts.isEmpty() && !anyParticleAlive() && !anyShockwaveAlive() && !cascadeActive) {
                stopFrameLoopIfIdle()
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val e = engine ?: return
        if (tileSize <= 0) return

        canvas.save()
        canvas.translate(boardShakeOffset, 0f)

        val cols = e.level.grid.cols
        val rows = e.level.grid.rows
        val sel = e.selected

        // Tiles
        val inset = tileSize * 0.10f
        val nowMs = System.currentTimeMillis()
        val shuffleAlpha = shuffleFade.coerceIn(0f, 1f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = e.board.get(c, r)
                if (v <= 0) continue
                val left = boardOffsetX + c * tileSize
                val top = boardOffsetY + r * tileSize

                // Compute per-tile alpha & scale for cascade entrance, selection bounce, etc.
                var tileAlpha = shuffleAlpha
                var tileScale = 1f
                if (cascadeActive) {
                    val startAt = cascadeStartMs + (c + r) * cascadeStepMs
                    val t = ((nowMs - startAt).toFloat() / cascadeTileDurationMs).coerceIn(0f, 1f)
                    if (t <= 0f) continue   // not yet visible
                    tileAlpha *= t
                    tileScale = 0.5f + 0.5f * t
                }
                if (selectBounceKey == c + r * cols) {
                    tileScale *= selectBounceScale
                }

                // Per-tile rotation jitter during shuffle (mid-fade only).
                val jitterDeg = if (shuffleJitterDeg.isNotEmpty() && shuffleAlpha < 0.999f) {
                    shuffleJitterDeg[r * shuffleJitterCols + c] * (1f - shuffleAlpha)
                } else 0f

                drawTile(
                    canvas, left.toFloat(), top.toFloat(), v, inset,
                    tileAlpha, tileScale, jitterDeg
                )

                // Selection: blurred cyan halo + bright cyan border on the selected tile.
                val isSelected = sel != null && sel.col == c && sel.row == r
                if (isSelected) {
                    val haloPad = 5f * dp
                    tmpRect.set(left - haloPad, top - haloPad, left + tileSize + haloPad, top + tileSize + haloPad)
                    paintSelectGlow.strokeWidth = 10f * dp
                    paintSelectGlow.alpha = 220
                    canvas.drawRoundRect(tmpRect, cornerRadiusPx + haloPad, cornerRadiusPx + haloPad, paintSelectGlow)
                    paintSelectGlow.alpha = 255

                    paintSelectBorder.strokeWidth = selectStrokePx
                    val pad = selectStrokePx / 2f + 1f
                    tmpRect.set(left + pad, top + pad, left + tileSize - pad, top + tileSize - pad)
                    canvas.drawRoundRect(tmpRect, cornerRadiusPx, cornerRadiusPx, paintSelectBorder)
                }

                // Invalid flash border on both selected tiles. Pulse on/off via cos.
                val inv = invalidPair
                if (inv != null && (inv.first.col == c && inv.first.row == r || inv.second.col == c && inv.second.row == r)) {
                    val pulse = (0.5f + 0.5f * cos(invalidFlashT * Math.PI.toFloat() * 6f)) * invalidAlpha
                    paintInvalidBorder.strokeWidth = selectStrokePx
                    paintInvalidBorder.alpha = (pulse.coerceIn(0f, 1f) * 255f).toInt()
                    val pad = selectStrokePx / 2f + 1f
                    tmpRect.set(left + pad, top + pad, left + tileSize - pad, top + tileSize - pad)
                    canvas.drawRoundRect(tmpRect, cornerRadiusPx, cornerRadiusPx, paintInvalidBorder)
                    paintInvalidBorder.alpha = 255
                }
            }
        }

        // Tile bursts (the matched tiles' fade-grow ghost)
        drawTileBursts(canvas, nowMs, inset)

        // Match path overlay (progressive reveal + traveling spark + post-reveal fade)
        val mp = matchPath
        if (mp != null && matchProgress > 0f) {
            drawConnectPath(canvas, mp, matchProgress, matchAlpha)
        }

        // Invalid attempted path (straight red line between the two pos)
        val inv = invalidPair
        if (inv != null && invalidAlpha > 0f) {
            drawInvalidPath(canvas, inv.first, inv.second, invalidAlpha)
        }

        // Hint sparkles
        val hp = hintPair
        if (hp != null) {
            drawHintSparkles(canvas, hp.third, hintPulse)
        }

        // Shockwaves (drawn before particles so particles pop on top)
        drawShockwaves(canvas)

        // Particles
        drawParticles(canvas)

        // Bomb flash overlay
        if (bombFlashAlpha > 0f) {
            paintFlash.alpha = (bombFlashAlpha * 200f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintFlash)
            paintFlash.alpha = 255
        }

        canvas.restore()
    }

    private fun drawTile(
        canvas: Canvas, left: Float, top: Float, faceVal: Int,
        inset: Float, alpha: Float, scale: Float, rotationDeg: Float
    ) {
        val faceAlphaI = (alpha.coerceIn(0f, 1f) * 255f).toInt()

        // Apply scale/rotation about tile center.
        val saved = canvas.save()
        val cx = left + tileSize / 2f
        val cy = top + tileSize / 2f
        if (scale != 1f || rotationDeg != 0f) {
            canvas.translate(cx, cy)
            if (rotationDeg != 0f) canvas.rotate(rotationDeg)
            if (scale != 1f) canvas.scale(scale, scale)
            canvas.translate(-cx, -cy)
        }

        // Shadow
        tmpRect.set(
            left + 1f, top + shadowOffsetPx,
            left + tileSize - 1f, top + tileSize + shadowOffsetPx
        )
        paintShadow.alpha = (0x1F * alpha).toInt().coerceIn(0, 0x1F)
        canvas.drawRoundRect(tmpRect, cornerRadiusPx, cornerRadiusPx, paintShadow)

        // White tile bg
        tmpRect.set(left + 1f, top + 1f, left + tileSize - 1f, top + tileSize - 1f)
        paintTileBg.alpha = faceAlphaI
        canvas.drawRoundRect(tmpRect, cornerRadiusPx, cornerRadiusPx, paintTileBg)

        // Face bitmap
        val name = tileDrawables.getOrNull(faceVal - 1)
        val bmp: Bitmap? = name?.let {
            try { AssetCache.getTile(context.applicationContext, it, tileSize) } catch (_: Throwable) { null }
        }
        if (bmp != null) {
            paintTileBmp.alpha = faceAlphaI
            tmpRect.set(left + inset, top + inset, left + tileSize - inset, top + tileSize - inset)
            canvas.drawBitmap(bmp, null, tmpRect, paintTileBmp)
        }

        // Restore paint alphas to defaults for subsequent draws
        paintShadow.alpha = 0x1F
        paintTileBg.alpha = 255
        paintTileBmp.alpha = 255

        canvas.restoreToCount(saved)
    }

    private fun drawTileBursts(canvas: Canvas, nowMs: Long, inset: Float) {
        if (tileBursts.isEmpty()) return
        for (tb in tileBursts) {
            val t = ((nowMs - tb.startMs).toFloat() / tb.durationMs).coerceIn(0f, 1f)
            val alpha = (1f - t)
            val scale = 1f + 0.3f * t
            val left = boardOffsetX + tb.col * tileSize
            val top = boardOffsetY + tb.row * tileSize
            val cx = left + tileSize / 2f
            val cy = top + tileSize / 2f
            val saved = canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale, scale)
            canvas.translate(-cx, -cy)
            val bmp = tb.bmp
            if (bmp != null) {
                paintTileBmp.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
                tmpRect.set(left + inset, top + inset, left + tileSize - inset, top + tileSize - inset)
                canvas.drawBitmap(bmp, null, tmpRect, paintTileBmp)
                paintTileBmp.alpha = 255
            }
            canvas.restoreToCount(saved)
        }
    }

    private fun drawConnectPath(canvas: Canvas, p: ConnectPath, progress: Float, fadeAlpha: Float) {
        if (p.points.size < 2) return
        tmpPath.reset()
        var first = true
        for (pt in p.points) {
            val cx = boardOffsetX + (pt.col + 0.5f) * tileSize
            val cy = boardOffsetY + (pt.row + 0.5f) * tileSize
            if (first) { tmpPath.moveTo(cx, cy); first = false } else tmpPath.lineTo(cx, cy)
        }
        pathMeasure.setPath(tmpPath, false)
        val total = pathMeasure.length
        val end = total * progress.coerceIn(0f, 1f)
        tmpDrawPath.reset()
        pathMeasure.getSegment(0f, end, tmpDrawPath, true)
        // Workaround for some devices that need a moveTo before getSegment renders.
        tmpDrawPath.rLineTo(0f, 0f)

        val a = (fadeAlpha.coerceIn(0f, 1f) * 255f).toInt()

        // Three-layer neon stack: blurred outer glow, mid stroke, bright inner highlight.
        paintPathGlow.strokeWidth = pathGlowStrokePx
        paintPathGlow.alpha = (a * 0.80f).toInt().coerceIn(0, 255)
        canvas.drawPath(tmpDrawPath, paintPathGlow)

        paintPathUnder.strokeWidth = pathUnderStrokePx
        paintPathUnder.alpha = a
        canvas.drawPath(tmpDrawPath, paintPathUnder)

        paintPathOver.strokeWidth = pathOverStrokePx
        paintPathOver.alpha = a
        canvas.drawPath(tmpDrawPath, paintPathOver)

        paintPathInner.strokeWidth = pathInnerStrokePx
        paintPathInner.alpha = (a * 0.85f).toInt().coerceIn(0, 255)
        canvas.drawPath(tmpDrawPath, paintPathInner)

        // Travelling head: blurred halo + bright comet + 6-dot trail behind it.
        if (progress < 1f) {
            // Comet trail — sample 6 positions BEHIND the head, radii and alpha fading.
            val trailGap = pathOverStrokePx * 0.9f
            for (i in 1..6) {
                val pos = end - i * trailGap
                if (pos <= 0f) break
                if (pathMeasure.getPosTan(pos, tmpPos, tmpTan)) {
                    val factor = 1f - (i / 7f)
                    val r = pathHeadRadiusPx * factor
                    val pa = (a * 0.50f * factor).toInt().coerceIn(0, 255)
                    paintPathSpark.alpha = pa
                    canvas.drawCircle(tmpPos[0], tmpPos[1], r, paintPathSpark)
                }
            }
            if (pathMeasure.getPosTan(end, tmpPos, tmpTan)) {
                // Outer halo
                paintPathSparkGlow.alpha = (a * 0.90f).toInt().coerceIn(0, 255)
                canvas.drawCircle(tmpPos[0], tmpPos[1], pathHeadRadiusPx * 1.4f, paintPathSparkGlow)
                // Bright core
                paintPathSpark.alpha = a
                canvas.drawCircle(tmpPos[0], tmpPos[1], pathHeadRadiusPx, paintPathSpark)
            }
        }
        paintPathGlow.alpha = 255
        paintPathUnder.alpha = 255
        paintPathOver.alpha = 255
        paintPathInner.alpha = 255
        paintPathSpark.alpha = 255
        paintPathSparkGlow.alpha = 255
    }

    private fun drawInvalidPath(canvas: Canvas, a: TilePos, b: TilePos, alpha: Float) {
        // Crisper "blocked" affordance: a red X cross drawn on top of each selected tile.
        // The X scale animates with `invalidFlashT` (already pulsing in onDraw via cos).
        val crossAlpha = (alpha.coerceIn(0f, 1f) * 220f).toInt().coerceIn(0, 255)
        val crossW = pathOverStrokePx * 0.55f
        val inset = tileSize * 0.28f
        paintInvalidCross.strokeWidth = crossW
        // Outer red halo
        paintInvalidPathGlow.strokeWidth = crossW * 2.2f
        paintInvalidPathGlow.alpha = (crossAlpha * 0.8f).toInt().coerceIn(0, 255)
        for (pos in listOf(a, b)) {
            val left = boardOffsetX + pos.col * tileSize
            val top = boardOffsetY + pos.row * tileSize
            val l = left + inset
            val t = top + inset
            val r = left + tileSize - inset
            val b2 = top + tileSize - inset
            // Halo
            canvas.drawLine(l, t, r, b2, paintInvalidPathGlow)
            canvas.drawLine(l, b2, r, t, paintInvalidPathGlow)
            // Bright cross
            paintInvalidCross.alpha = crossAlpha
            canvas.drawLine(l, t, r, b2, paintInvalidCross)
            canvas.drawLine(l, b2, r, t, paintInvalidCross)
        }
        paintInvalidCross.alpha = 255
        paintInvalidPathGlow.alpha = 255
    }

    private fun drawHintSparkles(canvas: Canvas, p: ConnectPath, pulse: Float) {
        if (p.points.size < 2) return
        val baseR = tileSize * 0.12f
        val r = baseR * (0.7f + 0.5f * pulse.coerceIn(0f, 1f))
        for (i in p.points.indices) {
            val pt = p.points[i]
            val cx = boardOffsetX + (pt.col + 0.5f) * tileSize
            val cy = boardOffsetY + (pt.row + 0.5f) * tileSize
            paintSparkle.alpha = (180 * pulse.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, paintSparkle)
        }
        paintSparkle.alpha = 255
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            if (!p.alive) continue
            val t = (p.ageMs / p.lifeMs).coerceIn(0f, 1f)
            val a = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            paintParticle.color = (p.color and 0x00FFFFFF) or (a shl 24)
            canvas.drawCircle(p.x, p.y, p.radius, paintParticle)
        }
    }

    private fun drawShockwaves(canvas: Canvas) {
        for (sw in shockwaves) {
            if (!sw.alive) continue
            val t = (sw.ageMs / sw.lifeMs).coerceIn(0f, 1f)
            val r = sw.startR + (sw.endR - sw.startR) * t
            val a = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            val sw1 = (4f * dp) * (1f - t)
            paintShockwave.color = (sw.color and 0x00FFFFFF) or (a shl 24)
            paintShockwave.strokeWidth = sw1.coerceAtLeast(1f)
            canvas.drawCircle(sw.cx, sw.cy, r, paintShockwave)
        }
    }

    private fun spawnShockwave(cx: Float, cy: Float, color: Int, big: Boolean) {
        for (sw in shockwaves) {
            if (sw.alive) continue
            sw.cx = cx; sw.cy = cy
            sw.ageMs = 0f
            sw.lifeMs = if (big) 500f else 360f
            sw.startR = tileSize * 0.20f
            sw.endR = tileSize * (if (big) 1.8f else 1.3f)
            sw.color = color
            sw.alive = true
            break
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val e = engine ?: return false
        if (tileSize <= 0) return false
        if (cascadeActive) return false  // ignore taps until intro finishes
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                performClick()
                val c = ((event.x - boardOffsetX) / tileSize).toInt()
                val r = ((event.y - boardOffsetY) / tileSize).toInt()
                if (c < 0 || r < 0 || c >= e.level.grid.cols || r >= e.level.grid.rows) return true
                if (e.board.get(c, r) <= 0) return true
                handleTap(TilePos(c, r))
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(pos: TilePos) {
        val e = engine ?: return
        // Capture the face id of both tiles BEFORE tap() in case the engine clears them.
        val prevSel = e.selected
        val faceA: Int = prevSel?.let { e.board.get(it.col, it.row) } ?: 0
        val faceB: Int = e.board.get(pos.col, pos.row)
        when (val res = e.tap(pos)) {
            MatchResult.Selected -> {
                playSelectBounce(pos)
            }
            MatchResult.Deselected -> {
                cancelSelectBounce()
                invalidate()
            }
            is MatchResult.Matched -> {
                val face = if (faceA > 0) faceA else faceB
                animateMatch(res.a, res.b, res.path, face)
            }
            is MatchResult.InvalidPair -> animateInvalid(res.a, res.b)
        }
    }

    private fun playSelectBounce(pos: TilePos) {
        val e = engine ?: return
        cancelSelectBounce()
        val cols = e.level.grid.cols
        selectBounceKey = pos.col + pos.row * cols
        selectBounceScale = 1f
        selectBounceAnimator = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
            duration = 180L
            addUpdateListener {
                selectBounceScale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    selectBounceKey = -1
                    selectBounceScale = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun cancelSelectBounce() {
        selectBounceAnimator?.cancel()
        selectBounceAnimator = null
        selectBounceKey = -1
        selectBounceScale = 1f
    }

    private fun animateMatch(a: TilePos, b: TilePos, path: ConnectPath, faceId: Int) {
        matchAnimator?.cancel()
        matchPath = path
        matchProgress = 0f
        matchAlpha = 1f
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()

        // Recover the cleared tiles' bitmap from the face id captured before tap().
        val bmpA: Bitmap? = lookupBitmapByFace(faceId)
        val bmpB: Bitmap? = bmpA

        // Spawn a burst (particles + scale-fade ghost) at each endpoint after the path reveal.
        // Schedule burst via the path animator's end.
        matchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            addUpdateListener {
                matchProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Path is fully drawn. Trigger the burst at both tile centers.
                    val cxA = boardOffsetX + (a.col + 0.5f) * tileSize
                    val cyA = boardOffsetY + (a.row + 0.5f) * tileSize
                    val cxB = boardOffsetX + (b.col + 0.5f) * tileSize
                    val cyB = boardOffsetY + (b.row + 0.5f) * tileSize
                    spawnParticleBurst(cxA, cyA, count = 12, big = false)
                    spawnParticleBurst(cxB, cyB, count = 12, big = false)
                    spawnShockwave(cxA, cyA, accentColor, big = false)
                    spawnShockwave(cxB, cyB, DesignTokens.lighter(accentColor, 0.25f), big = false)
                    val now = System.currentTimeMillis()
                    if (bmpA != null) tileBursts.add(TileBurst(a.col, a.row, bmpA, now, 220L))
                    if (bmpB != null) tileBursts.add(TileBurst(b.col, b.row, bmpB, now, 220L))
                    ensureFrameLoop()
                    // Now fade the path out.
                    val fade = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = 200L
                        addUpdateListener { a2 ->
                            matchAlpha = a2.animatedValue as Float
                            invalidate()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                matchPath = null
                                matchProgress = 0f
                                matchAlpha = 0f
                                setLayerType(LAYER_TYPE_NONE, null)
                                invalidate()
                                listener?.onMatched()
                                val eng = engine
                                if (eng != null && eng.isCleared) listener?.onCleared()
                            }
                        })
                    }
                    fade.start()
                }
            })
        }
        matchAnimator?.start()
    }

    private fun lookupBitmapByFace(faceId: Int): Bitmap? {
        if (faceId <= 0) return null
        val name = tileDrawables.getOrNull(faceId - 1) ?: return null
        return try {
            AssetCache.getTile(context.applicationContext, name, tileSize)
        } catch (_: Throwable) { null }
    }

    private fun animateInvalid(a: TilePos, b: TilePos) {
        invalidAnimator?.cancel()
        invalidPair = a to b
        invalidAlpha = 1f
        invalidFlashT = 0f
        invalidate()
        invalidAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                val t = it.animatedValue as Float
                invalidFlashT = t
                // Fade out the path/borders during the last 25%.
                invalidAlpha = if (t < 0.75f) 1f else (1f - (t - 0.75f) / 0.25f)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    invalidPair = null
                    invalidAlpha = 0f
                    invalidFlashT = 0f
                    invalidate()
                }
            })
        }
        invalidAnimator?.start()
        listener?.onInvalidPair()
    }

    fun showHint() {
        val e = engine ?: return
        val hint = e.findHint()
        if (hint == null) {
            startShake()
            return
        }
        hintAnimator?.cancel()
        hintPair = hint
        hintPulse = 1f
        invalidate()
        hintAnimator = ValueAnimator.ofFloat(0.2f, 1f).apply {
            duration = 600L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 2
            addUpdateListener {
                hintPulse = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    hintPair = null
                    hintPulse = 0f
                    invalidate()
                }
            })
        }
        hintAnimator?.start()
    }

    fun popPair() {
        val e = engine ?: return
        // Pick a solvable pair first (so we know the face), then clear both cells.
        val hint = e.findHint() ?: run { startShake(); return }
        val (a, b, _) = hint
        val faceId = e.board.get(a.col, a.row)
        e.board.set(a.col, a.row, 0)
        e.board.set(b.col, b.row, 0)
        // Defensive: clear any stray selection.
        if (e.selected != null) e.clearSelection()

        val cxA = boardOffsetX + (a.col + 0.5f) * tileSize
        val cyA = boardOffsetY + (a.row + 0.5f) * tileSize
        val cxB = boardOffsetX + (b.col + 0.5f) * tileSize
        val cyB = boardOffsetY + (b.row + 0.5f) * tileSize
        spawnParticleBurst(cxA, cyA, count = 24, big = true)
        spawnParticleBurst(cxB, cyB, count = 24, big = true)
        spawnShockwave(cxA, cyA, accentColor, big = true)
        spawnShockwave(cxB, cyB, DesignTokens.lighter(accentColor, 0.25f), big = true)
        val bmp = lookupBitmapByFace(faceId)
        val now = System.currentTimeMillis()
        if (bmp != null) {
            tileBursts.add(TileBurst(a.col, a.row, bmp, now, 260L))
            tileBursts.add(TileBurst(b.col, b.row, bmp, now, 260L))
        }
        triggerBombFlash()
        ensureFrameLoop()
        invalidate()
        // Inform listener after the burst settles a bit.
        postDelayed({
            listener?.onMatched()
            if (e.isCleared) listener?.onCleared()
        }, 280L)
    }

    private fun triggerBombFlash() {
        bombFlashAnimator?.cancel()
        bombFlashAlpha = 1f
        bombFlashAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220L  // brief
            addUpdateListener {
                bombFlashAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    bombFlashAlpha = 0f
                    invalidate()
                }
            })
        }
        bombFlashAnimator?.start()
    }

    fun shuffleBoard() {
        val e = engine ?: return
        shuffleAnimator?.cancel()
        // Seed a random jitter per cell on the way out.
        val cols = e.level.grid.cols
        val rows = e.level.grid.rows
        if (shuffleJitterDeg.size != cols * rows) {
            shuffleJitterDeg = FloatArray(cols * rows)
            shuffleJitterCols = cols
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            shuffleJitterDeg[r * cols + c] = (rng.nextFloat() - 0.5f) * 30f  // ±15°
        }
        shuffleAnimator = ValueAnimator.ofFloat(1f, 0f, 1f).apply {
            duration = 500L
            var shuffled = false
            addUpdateListener {
                val f = it.animatedValue as Float
                shuffleFade = f
                if (!shuffled && it.animatedFraction >= 0.5f) {
                    e.shuffle()
                    // New jitter for the way in (smaller).
                    for (r in 0 until rows) for (c in 0 until cols) {
                        shuffleJitterDeg[r * cols + c] = (rng.nextFloat() - 0.5f) * 16f
                    }
                    shuffled = true
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!shuffled) {
                        e.shuffle()
                    }
                    shuffleFade = 1f
                    invalidate()
                }
            })
        }
        shuffleAnimator?.start()
    }

    private fun startShake() {
        shakeAnimator?.cancel()
        val amplitude = 6f * dp
        shakeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                val t = it.animatedFraction
                val damped = (1f - t) * sin(t * Math.PI.toFloat() * 6f)
                boardShakeOffset = amplitude * damped
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    boardShakeOffset = 0f
                    invalidate()
                }
            })
        }
        shakeAnimator?.start()
    }

    private fun spawnParticleBurst(cx: Float, cy: Float, count: Int, big: Boolean) {
        var spawned = 0
        val maxSpeed = if (big) 320f * dp else 220f * dp
        val minSpeed = if (big) 140f * dp else 80f * dp
        val life = if (big) 700f else 500f
        val radius = if (big) 3f * dp else 2f * dp
        for (p in particles) {
            if (spawned >= count) break
            if (p.alive) continue
            val angle = rng.nextFloat() * (2f * Math.PI.toFloat())
            val speed = minSpeed + rng.nextFloat() * (maxSpeed - minSpeed)
            p.x = cx
            p.y = cy
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - (40f * dp) // slight upward bias
            p.ageMs = 0f
            p.lifeMs = life + rng.nextFloat() * 200f
            // 50% accent-themed, 50% from the burst palette for variety.
            p.color = if (rng.nextFloat() < 0.5f) accentColor else particlePalette[rng.nextInt(particlePalette.size)]
            p.radius = radius
            p.alive = true
            spawned++
        }
    }

    override fun onDetachedFromWindow() {
        matchAnimator?.cancel()
        invalidAnimator?.cancel()
        hintAnimator?.cancel()
        shuffleAnimator?.cancel()
        shakeAnimator?.cancel()
        bombFlashAnimator?.cancel()
        selectBounceAnimator?.cancel()
        Choreographer.getInstance().removeFrameCallback(this)
        frameLoopActive = false
        try { ioExec.shutdownNow() } catch (_: Throwable) {}
        super.onDetachedFromWindow()
    }
}
