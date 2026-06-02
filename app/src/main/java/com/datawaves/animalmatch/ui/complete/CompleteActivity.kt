package com.datawaves.animalmatch.ui.complete

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.LevelRepository
import com.datawaves.animalmatch.databinding.ActivityCompleteBinding
import com.datawaves.animalmatch.ui.map.MapActivity
import com.datawaves.animalmatch.ui.play.GameActivity
import com.datawaves.animalmatch.util.Prefs

/**
 * Level-complete celebration screen. Displays the awarded sun, the badge that the level
 * contributed to, and the player's new total count for that badge. Hosts a continuous fireworks
 * animation in the background.
 *
 * Sun reward is presumed to have already been credited to the wallet by GameActivity. The
 * `sun_reward` intent extra is for display only.
 *
 * Entrance animation sequence:
 *   • Title         : 0    ms — scale 0.5 → 1 + alpha 0 → 1 (overshoot, 600ms).
 *   • Sun reward    : 400  ms — fade in; count-up integer 0 → reward over 600ms.
 *   • Badge icon    : 800  ms — scale 0 → 1 (overshoot 1.6, 500ms).
 *   • Label + count : 1300 ms — fade in (260ms).
 *   • Replay btn    : 1200 ms — translate up + fade in (280ms).
 *   • Next btn      : 1260 ms — translate up + fade in (280ms).
 */
class CompleteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL_ID = "level_id"
        const val EXTRA_SUN_REWARD = "sun_reward"
        const val EXTRA_COIN_REWARD = "coin_reward"
        const val EXTRA_BADGE_ID = "badge_id"
        const val EXTRA_LEVELED_UP = "leveled_up"
        const val EXTRA_NEW_PLAYER_LEVEL = "new_player_level"

        fun newIntent(context: Context, levelId: Int, sunReward: Int, coinReward: Int, badgeId: String): Intent =
            Intent(context, CompleteActivity::class.java).apply {
                putExtra(EXTRA_LEVEL_ID, levelId)
                putExtra(EXTRA_SUN_REWARD, sunReward)
                putExtra(EXTRA_COIN_REWARD, coinReward)
                putExtra(EXTRA_BADGE_ID, badgeId)
            }
    }

    private lateinit var binding: ActivityCompleteBinding
    private val pendingAnimators = ArrayList<android.animation.Animator>()
    private var winPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val levelId = intent.getIntExtra(EXTRA_LEVEL_ID, 1)
        val sunReward = intent.getIntExtra(EXTRA_SUN_REWARD, 0)
        val coinReward = intent.getIntExtra(EXTRA_COIN_REWARD, 0)
        val leveledUp = intent.getBooleanExtra(EXTRA_LEVELED_UP, false)
        val newPlayerLevel = intent.getIntExtra(EXTRA_NEW_PLAYER_LEVEL, 1)
        val badgeIdExtra = intent.getStringExtra(EXTRA_BADGE_ID) ?: ""

        val catalog = LevelRepository.get(this)
        val level = catalog.level(levelId)
        // Prefer the level's badge but tolerate the extra mismatching (level wins).
        val badgeId = if (badgeIdExtra.isNotEmpty()) badgeIdExtra else level.badgeId
        val badge = catalog.badges[badgeId] ?: catalog.badge(level.badgeId)

        binding.titleText?.text = getString(R.string.level_complete)
        // The chip text is driven by the count-up animator below; start at "+0".
        binding.sunRewardChip?.text = getString(R.string.sun_plus_fmt, 0)
        binding.coinRewardChip?.text = getString(R.string.sun_plus_fmt, 0)
        // Pre-set the level-up banner; runEntrance() decides whether to reveal it.
        binding.levelUpBanner?.text = getString(R.string.xp_level_up_fmt, newPlayerLevel)

        val badgeDrawableId = resources.getIdentifier(badge.drawableName, "drawable", packageName)
        if (badgeDrawableId != 0) {
            binding.badgeIcon?.setImageResource(badgeDrawableId)
        }
        binding.badgeLabelChip?.text = badge.label
        binding.badgeCountText?.text = level.badgeCount.toString()

        binding.fireworksView?.start()
        playWinSound()

        binding.replayBtn?.setOnClickListener {
            startActivity(GameActivity.newIntent(this, levelId))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
        binding.nextBtn?.setOnClickListener {
            val next = levelId + 1
            if (next > com.datawaves.animalmatch.data.Progress.MAX_LEVELS) {
                finish()
            } else {
                val intent = Intent(this, MapActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MapActivity.EXTRA_AUTO_OPEN_LEVEL, next)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
        }

        prepareEntrance()
        binding.root.post { runEntrance(sunReward, coinReward, leveledUp) }

        binding.root.postDelayed({
            binding.badgeIcon?.let { iv ->
                val pulse = android.animation.ObjectAnimator.ofFloat(iv, "scaleX", 1.0f, 1.05f).apply {
                    duration = 1100L
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                val pulseY = android.animation.ObjectAnimator.ofFloat(iv, "scaleY", 1.0f, 1.05f).apply {
                    duration = 1100L
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                pulse.start()
                pulseY.start()
                pendingAnimators.add(pulse)
                pendingAnimators.add(pulseY)
            }
        }, 2000L)
    }

    private fun prepareEntrance() {
        binding.titleText?.apply { alpha = 0f; scaleX = 0.5f; scaleY = 0.5f }
        binding.sunRewardChip?.apply { alpha = 0f }
        binding.coinRewardChip?.apply { alpha = 0f }
        binding.badgeIcon?.apply { scaleX = 0f; scaleY = 0f }
        binding.badgeLabelChip?.apply { alpha = 0f }
        binding.badgeCountText?.apply { alpha = 0f }
        val dp = resources.displayMetrics.density
        binding.replayBtn?.apply { alpha = 0f; translationY = 60f * dp }
        binding.nextBtn?.apply { alpha = 0f; translationY = 60f * dp }
    }

    private fun runEntrance(sunReward: Int, coinReward: Int, leveledUp: Boolean) {
        // Title — scale + alpha with overshoot.
        binding.titleText?.let { tv ->
            val sx = ObjectAnimator.ofFloat(tv, "scaleX", 0.5f, 1f)
            val sy = ObjectAnimator.ofFloat(tv, "scaleY", 0.5f, 1f)
            val a  = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f)
            val set = AnimatorSet()
            set.duration = 600L
            set.interpolator = OvershootInterpolator(1.4f)
            set.playTogether(sx, sy, a)
            set.start()
            pendingAnimators.add(set)
        }

        // Sun reward chip — fade in + count up.
        binding.sunRewardChip?.let { tv ->
            val fade = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f).apply {
                startDelay = 400L
                duration = 280L
                interpolator = DecelerateInterpolator()
                start()
            }
            pendingAnimators.add(fade)
            val countUp = ValueAnimator.ofInt(0, sunReward).apply {
                startDelay = 420L
                duration = 800L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    tv.text = getString(R.string.sun_plus_fmt, it.animatedValue as Int)
                }
                start()
            }
            pendingAnimators.add(countUp)
        }

        // Coin reward chip — same treatment, slightly staggered so the eye reads sun → coins.
        binding.coinRewardChip?.let { tv ->
            val fade = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f).apply {
                startDelay = 560L
                duration = 280L
                interpolator = DecelerateInterpolator()
                start()
            }
            pendingAnimators.add(fade)
            val countUp = ValueAnimator.ofInt(0, coinReward).apply {
                startDelay = 580L
                duration = 800L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    tv.text = getString(R.string.sun_plus_fmt, it.animatedValue as Int)
                }
                start()
            }
            pendingAnimators.add(countUp)
        }

        // Level-up banner — only revealed when the player crossed an XP tier this round.
        // Pop it in with a scale + alpha overshoot after the count-ups land.
        if (leveledUp) {
            binding.levelUpBanner?.let { tv ->
                tv.visibility = android.view.View.VISIBLE
                tv.alpha = 0f
                tv.scaleX = 0.6f
                tv.scaleY = 0.6f
                val sx = ObjectAnimator.ofFloat(tv, "scaleX", 0.6f, 1f)
                val sy = ObjectAnimator.ofFloat(tv, "scaleY", 0.6f, 1f)
                val a = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f)
                val set = AnimatorSet()
                set.startDelay = 1450L
                set.duration = 460L
                set.interpolator = OvershootInterpolator(2f)
                set.playTogether(sx, sy, a)
                set.start()
                pendingAnimators.add(set)
            }
        }

        // Badge icon — scale 0 → 1 with overshoot, delayed 800ms.
        binding.badgeIcon?.let { iv ->
            val sx = ObjectAnimator.ofFloat(iv, "scaleX", 0f, 1f)
            val sy = ObjectAnimator.ofFloat(iv, "scaleY", 0f, 1f)
            val set = AnimatorSet()
            set.duration = 500L
            set.startDelay = 800L
            set.interpolator = OvershootInterpolator(1.6f)
            set.playTogether(sx, sy)
            set.start()
            pendingAnimators.add(set)
        }

        // Badge label + count — fade in after badge.
        binding.badgeLabelChip?.let { tv ->
            val a = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f)
            a.startDelay = 1300L
            a.duration = 260L
            a.interpolator = DecelerateInterpolator()
            a.start()
            pendingAnimators.add(a)
        }
        binding.badgeCountText?.let { tv ->
            val a = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f)
            a.startDelay = 1300L
            a.duration = 260L
            a.interpolator = DecelerateInterpolator()
            a.start()
            pendingAnimators.add(a)
        }

        // Buttons — slide up + fade in, staggered.
        binding.replayBtn?.let { b ->
            val t = ObjectAnimator.ofFloat(b, "translationY", b.translationY, 0f)
            val a = ObjectAnimator.ofFloat(b, "alpha", 0f, 1f)
            val set = AnimatorSet()
            set.startDelay = 1200L
            set.duration = 280L
            set.interpolator = DecelerateInterpolator()
            set.playTogether(t, a)
            set.start()
            pendingAnimators.add(set)
        }
        binding.nextBtn?.let { b ->
            val t = ObjectAnimator.ofFloat(b, "translationY", b.translationY, 0f)
            val a = ObjectAnimator.ofFloat(b, "alpha", 0f, 1f)
            val set = AnimatorSet()
            set.startDelay = 1260L
            set.duration = 280L
            set.interpolator = DecelerateInterpolator()
            set.playTogether(t, a)
            set.start()
            pendingAnimators.add(set)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.fireworksView?.stop()
    }

    override fun onResume() {
        super.onResume()
        binding.fireworksView?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        for (a in pendingAnimators) {
            try { a.cancel() } catch (_: Throwable) {}
        }
        pendingAnimators.clear()
        releaseWinPlayer()
    }

    private fun playWinSound() {
        // SoundPool clips out longer mp3s; use MediaPlayer so the full jingle plays. Respects
        // the user's sound preference.
        if (!Prefs.get(this).soundEnabled) return
        releaseWinPlayer()
        try {
            winPlayer = MediaPlayer.create(this, R.raw.win_sound)?.apply {
                setVolume(0.85f, 0.85f)
                setOnCompletionListener { releaseWinPlayer() }
                start()
            }
        } catch (_: Throwable) {
            // Silent failure — sound is non-critical.
        }
    }

    private fun releaseWinPlayer() {
        try { winPlayer?.release() } catch (_: Throwable) {}
        winPlayer = null
    }

    override fun onStop() {
        super.onStop()
        // If the user navigates away mid-jingle, stop the sound so it doesn't continue in bg.
        releaseWinPlayer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
        // Just returns to whatever launched us; usually MapActivity is already underneath.
    }
}
