package com.datawaves.animalmatch.ui

import android.content.Context
import android.graphics.Color
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt

/**
 * Shared design tokens. Mirrored from `res/values/colors.xml` for use inside Canvas draws.
 * Adopted from ballpathpuzzle's reference style guide and tuned for animalmatch's sunset palette.
 * Every theme-able paint, interpolator and dimension lives here so a single change propagates
 * everywhere.
 */
object DesignTokens {

    // ── Surfaces ──────────────────────────────────────────────────
    @ColorInt val BackgroundDark = Color.parseColor("#121226")
    @ColorInt val SurfaceDark    = Color.parseColor("#1E1E2E")
    @ColorInt val TileUnvisited  = Color.parseColor("#2E2E42")

    @ColorInt val SkyTop    = Color.parseColor("#1A1746")
    @ColorInt val SkyMid    = Color.parseColor("#3B2858")
    @ColorInt val SkyBottom = Color.parseColor("#B25034")
    @ColorInt val Mountain  = Color.parseColor("#221C3D")
    @ColorInt val Panel     = Color.parseColor("#241B45")
    @ColorInt val PanelDark = Color.parseColor("#1A1340")

    // ── Tiles ─────────────────────────────────────────────────────
    @ColorInt val TileBg          = Color.parseColor("#FFFFFF")
    @ColorInt val TileShadow      = Color.parseColor("#1F000000")
    @ColorInt val TileSelectRing  = Color.parseColor("#2EC4E6")
    @ColorInt val TileSelectGlow  = Color.parseColor("#3D90DBFF")
    @ColorInt val TileInvalidRing = Color.parseColor("#FF5752")
    @ColorInt val TileInvalidGlow = Color.parseColor("#3DFF5752")

    // ── Path (success) ────────────────────────────────────────────
    @ColorInt val PathOuterGlow = Color.parseColor("#665DE34A")
    @ColorInt val PathMid       = Color.parseColor("#5DE34A")
    @ColorInt val PathInner     = Color.parseColor("#E9FFE5")
    @ColorInt val PathHead      = Color.parseColor("#FFFFFFFF")
    @ColorInt val PathTrail     = Color.parseColor("#80FFFFFF")

    // ── Path (invalid attempt) ────────────────────────────────────
    @ColorInt val InvalidPathOuter = Color.parseColor("#66FF5752")
    @ColorInt val InvalidPath      = Color.parseColor("#FF5752")
    @ColorInt val InvalidPathInner = Color.parseColor("#FFE0D7")

    // ── Hint sparkles ─────────────────────────────────────────────
    @ColorInt val HintGlow = Color.parseColor("#88FFE96B")
    @ColorInt val HintCore = Color.parseColor("#FFE96B")
    @ColorInt val HintEdge = Color.parseColor("#FFC23B")

    // ── Burst palette ─────────────────────────────────────────────
    val BurstPalette = intArrayOf(
        Color.parseColor("#FFC23B"),
        Color.parseColor("#5DE34A"),
        Color.parseColor("#FF9F3E"),
        Color.parseColor("#2EC4E6"),
        Color.parseColor("#FF6BA7"),
        Color.parseColor("#B377FF"),
        Color.parseColor("#FFFFFF"),
    )
    val ConfettiPalette = intArrayOf(
        Color.parseColor("#E8734A"),
        Color.parseColor("#4CAF7D"),
        Color.parseColor("#5B8DD9"),
        Color.parseColor("#9B59B6"),
        Color.parseColor("#FFD700"),
        Color.parseColor("#E91E8C"),
        Color.parseColor("#00BCD4"),
        Color.parseColor("#FF5722"),
        Color.parseColor("#FFEB3B"),
    )

    // ── Buttons / chips ──────────────────────────────────────────
    @ColorInt val ButtonGreen     = Color.parseColor("#4FBE3C")
    @ColorInt val ButtonGreenDark = Color.parseColor("#2F8E22")
    @ColorInt val ButtonBlue      = Color.parseColor("#2EC4E6")
    @ColorInt val ButtonBlueDark  = Color.parseColor("#1399B7")
    @ColorInt val TextLight       = Color.parseColor("#FFFFFFFF")
    @ColorInt val TextDark        = Color.parseColor("#2E3552")
    @ColorInt val TextMuted       = Color.parseColor("#C8C2E6")
    @ColorInt val AccentYellow    = Color.parseColor("#FFC23B")
    @ColorInt val CoinGold        = Color.parseColor("#FFD700")
    @ColorInt val StarYellow      = Color.parseColor("#FFC107")
    @ColorInt val HintPink        = Color.parseColor("#E91E8C")
    @ColorInt val SuccessGreen    = Color.parseColor("#4CAF50")

    // ── Map nodes ────────────────────────────────────────────────
    @ColorInt val NodeCompleted = Color.parseColor("#2EC4E6")
    @ColorInt val NodeCurrent   = Color.parseColor("#FF9F3E")
    @ColorInt val NodeLocked    = Color.parseColor("#3A3F55")
    @ColorInt val NodeStroke    = Color.parseColor("#FFFFFF")
    @ColorInt val NodeGlowRing  = Color.parseColor("#80FFE596")

    // ── Per-level theme colors (drive path + node + complete dialog) ──────────────
    /** Maps a level's `themeId` to a vivid accent. Each themeId is one of "fruit_a", "fruit_b",
     *  "birds", "veges" — see assets/levels.json. */
    fun themeAccent(themeId: String): Int = when (themeId) {
        "fruit_a" -> Color.parseColor("#FF7A1A")  // orange-red — orchard at sunset
        "fruit_b" -> Color.parseColor("#FFC23B")  // golden harvest
        "birds"   -> Color.parseColor("#2EC4E6")  // sky cyan
        "veges"   -> Color.parseColor("#4CAF7D")  // garden green
        else      -> Color.parseColor("#FF7A1A")
    }

    // ── Geometry (dp values; convert via dp() helper) ────────────
    const val TileCornerDp        = 12f
    const val TileShadowOffsetDp  = 3.5f
    const val TileSelectStrokeDp  = 3f
    const val TileSelectGlowDp    = 18f

    const val PathGlowStrokeDp    = 22f
    const val PathMidStrokeDp     = 11f
    const val PathInnerStrokeDp   = 5.5f
    const val PathHeadRadiusDp    = 6f

    const val ChipCornerDp        = 22f
    const val ButtonCornerDp      = 28f
    const val ButtonElevationDp   = 4f
    const val PopupCornerDp       = 28f

    // ── Durations (ms) ───────────────────────────────────────────
    const val SelectBounceMs   = 200L
    const val PathRevealMs     = 280L
    const val PathSettleMs     = 140L
    const val PathFadeMs       = 220L
    const val MatchBurstMs     = 620L
    const val ShockwaveMs      = 360L
    const val InvalidPulseMs   = 360L
    const val InvalidShakeMs   = 360L
    const val HintBlinkMs      = 1500L
    const val BombFlashMs      = 240L
    const val ShuffleMs        = 520L
    const val CascadeTileMs    = 320L
    const val CascadeStepMs    = 14L

    // ── Interpolators ────────────────────────────────────────────
    val EaseStandard: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    val EaseEmphasized: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val EaseDecelerate: Interpolator = DecelerateInterpolator(1.5f)
    val EaseAccelerateDecelerate: Interpolator = AccelerateDecelerateInterpolator()
    val EaseOvershootSoft: Interpolator = OvershootInterpolator(1.4f)
    val EaseOvershootStrong: Interpolator = OvershootInterpolator(2.4f)
    val EaseAnticipate: Interpolator = AnticipateInterpolator(1.2f)
    /** Approximates a critically-damped spring for short entrance motion (140-280ms ranges). */
    val EaseSpringEntrance: Interpolator = PathInterpolator(0.16f, 1.0f, 0.3f, 1.0f)

    // ── Util ────────────────────────────────────────────────────
    fun dp(context: Context, dpVal: Float): Float =
        dpVal * context.resources.displayMetrics.density

    /** Returns a darker variant of [color] by multiplying RGB by [factor]. */
    fun darker(@ColorInt color: Int, factor: Float = 0.70f): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /** Returns a lighter variant by adding [amount] to each channel (0..1). */
    fun lighter(@ColorInt color: Int, amount: Float = 0.18f): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + amount * 255).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + amount * 255).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + amount * 255).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /** Returns [color] with its alpha set to [alpha] (0f..1f). */
    fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
