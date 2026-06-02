package com.datawaves.animalmatch.ui.play

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.LevelRepository
import com.datawaves.animalmatch.data.Progress
import com.datawaves.animalmatch.data.Wallet
import com.datawaves.animalmatch.data.models.Level
import com.datawaves.animalmatch.databinding.ActivityGameBinding
import com.datawaves.animalmatch.game.MatchEngine

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL_ID = "level_id"
        // Round time generosity: scale authored seconds and add a flat floor so every level
        // gets noticeably more breathing room than the original catalog allowed.
        private const val TIME_MULTIPLIER = 1.6f
        private const val TIME_FLAT_BONUS_SEC = 20
        fun newIntent(context: Context, levelId: Int): Intent =
            Intent(context, GameActivity::class.java).putExtra(EXTRA_LEVEL_ID, levelId)
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var level: Level
    private lateinit var engine: MatchEngine
    private lateinit var wallet: Wallet

    private var countdown: CountDownTimer? = null
    private var remainingMs: Long = 0L
    private var totalDeciseconds: Int = 0
    private var timerLowTinted = false
    private var finished = false
    private var firePulseAnimator: ValueAnimator? = null
    // Track whether the player used any booster this level — controls the no-help coin bonus.
    private var boostersUsedThisLevel: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyImmersive()

        val levelId = intent.getIntExtra(EXTRA_LEVEL_ID, 1)
        val catalog = LevelRepository.get(this)
        level = try { catalog.level(levelId) } catch (t: Throwable) { catalog.level(1) }
        engine = MatchEngine(level)
        wallet = Wallet.get(this)

        initHud()
        initBoard(catalog)
        initPowerups()
        initPause()
        startTimer(roundDurationMs())
    }

    private fun roundDurationMs(): Long {
        val bumped = (level.timerSec * TIME_MULTIPLIER).toInt() + TIME_FLAT_BONUS_SEC
        return bumped * 1000L
    }

    private fun applyImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun initHud() {
        safe { binding.levelLabel.text = getString(R.string.level_label_fmt, level.id) }
        // Sun chip surfaces the player's XP level (the raw XP is shown on the map's hero bar).
        safe { binding.sunChip.text = getString(R.string.xp_chip_fmt, Wallet.Xp.playerLevel(wallet.sun)) }
        safe { binding.coinChip.text = wallet.coins.toString() }
        safe { binding.sunEnergyText.text = level.targetSunReward.toString() }
        safe {
            binding.btnHintCount.text = wallet.hints.toString()
            binding.btnBombCount.text = wallet.bombs.toString()
            binding.btnShuffleCount.text = wallet.shuffles.toString()
        }
        safe { binding.invalidToast.visibility = View.GONE }
    }

    private fun initBoard(catalog: com.datawaves.animalmatch.data.models.LevelCatalog) {
        val theme = catalog.theme(level.themeId)
        binding.boardView.bind(
            engine = engine,
            tileDrawables = theme.tiles,
            listener = object : GameBoardView.Listener {
                override fun onInvalidPair() {
                    showInvalidToast()
                }
                override fun onMatched() {
                    // no-op for now, could play SFX
                }
                override fun onCleared() {
                    onLevelComplete()
                }
            }
        )
    }

    private fun initPowerups() {
        safe {
            binding.btnHint.setOnClickListener {
                if (wallet.spendHint()) {
                    boostersUsedThisLevel = true
                    binding.boardView.showHint()
                    safe { binding.btnHintCount.text = wallet.hints.toString() }
                } else {
                    shakeView(binding.btnHint)
                    openShop(com.datawaves.animalmatch.ui.shop.BoosterShopSheet.FOCUS_HINT)
                }
            }
        }
        safe {
            binding.btnBomb.setOnClickListener {
                if (wallet.spendBomb()) {
                    boostersUsedThisLevel = true
                    binding.boardView.popPair()
                    safe { binding.btnBombCount.text = wallet.bombs.toString() }
                } else {
                    shakeView(binding.btnBomb)
                    openShop(com.datawaves.animalmatch.ui.shop.BoosterShopSheet.FOCUS_BOMB)
                }
            }
        }
        safe {
            binding.btnShuffle.setOnClickListener {
                if (wallet.spendShuffle()) {
                    boostersUsedThisLevel = true
                    binding.boardView.shuffleBoard()
                    safe { binding.btnShuffleCount.text = wallet.shuffles.toString() }
                } else {
                    shakeView(binding.btnShuffle)
                    openShop(com.datawaves.animalmatch.ui.shop.BoosterShopSheet.FOCUS_SHUFFLE)
                }
            }
        }
    }

    private fun openShop(focus: String) {
        // While the shop is open we pause the countdown so the player can browse without
        // running out of time mid-purchase.
        pauseTimer()
        val sheet = com.datawaves.animalmatch.ui.shop.BoosterShopSheet.newInstance(focus)
        sheet.onWalletChanged = {
            safe {
                binding.btnHintCount.text = wallet.hints.toString()
                binding.btnBombCount.text = wallet.bombs.toString()
                binding.btnShuffleCount.text = wallet.shuffles.toString()
                binding.coinChip.text = wallet.coins.toString()
            }
        }
        sheet.show(supportFragmentManager, "shop")
        // Resume the timer once the sheet is dismissed.
        supportFragmentManager.executePendingTransactions()
        sheet.dialog?.setOnDismissListener { resumeTimer() }
    }

    private fun shakeView(v: View?) {
        if (v == null) return
        val dp = resources.displayMetrics.density
        val shake = ObjectAnimator.ofFloat(v, "translationX", 0f, -8f * dp, 8f * dp, -6f * dp, 6f * dp, 0f)
        shake.duration = 280L
        shake.start()
    }

    private fun initPause() {
        safe {
            binding.hudPause.setOnClickListener { showPauseDialog() }
        }
    }

    private fun showPauseDialog() {
        pauseTimer()
        AlertDialog.Builder(this)
            .setTitle("Paused")
            .setMessage("Take a breather.")
            .setPositiveButton(getString(R.string.pause_resume)) { d, _ ->
                d.dismiss()
                resumeTimer()
            }
            .setNegativeButton(getString(R.string.pause_quit)) { d, _ ->
                d.dismiss()
                finish()
            }
            .setOnCancelListener { resumeTimer() }
            .show()
    }

    private fun startTimer(durationMs: Long) {
        // Total ticks must match the actual duration we're counting down, not the catalog value,
        // or the progress bar sits at "full" for the bonus seconds before it starts draining.
        if (totalDeciseconds <= 0) {
            totalDeciseconds = (roundDurationMs() / 100L).toInt().coerceAtLeast(1)
        }
        safe {
            binding.timerBar.max = totalDeciseconds
            binding.timerBar.progress = ((durationMs / 100L).toInt()).coerceAtMost(totalDeciseconds)
        }
        timerLowTinted = false
        // Reset flame icon scale and any prior pulse.
        firePulseAnimator?.cancel()
        firePulseAnimator = null
        safe { binding.timerFireIcon.scaleX = 1f; binding.timerFireIcon.scaleY = 1f }

        remainingMs = durationMs
        countdown?.cancel()
        countdown = object : CountDownTimer(durationMs, 100L) {
            override fun onTick(msLeft: Long) {
                remainingMs = msLeft
                val deci = (msLeft / 100L).toInt().coerceAtLeast(0)
                safe { binding.timerBar.progress = deci }
                if (!timerLowTinted && deci <= (totalDeciseconds / 5)) {
                    timerLowTinted = true
                    val lowColor = resolveColor("timer_low", Color.parseColor("#E04848"))
                    safe { binding.timerBar.progressTintList = ColorStateList.valueOf(lowColor) }
                    startFirePulse()
                }
            }

            override fun onFinish() {
                remainingMs = 0L
                safe { binding.timerBar.progress = 0 }
                onTimeUp()
            }
        }
        countdown?.start()
    }

    private fun startFirePulse() {
        val icon = try { binding.timerFireIcon } catch (_: Throwable) { return } ?: return
        firePulseAnimator?.cancel()
        firePulseAnimator = ValueAnimator.ofFloat(1f, 1.2f).apply {
            duration = 480L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                icon.scaleX = s
                icon.scaleY = s
            }
            start()
        }
    }

    private fun pauseTimer() {
        countdown?.cancel()
        countdown = null
        firePulseAnimator?.cancel()
    }

    private fun resumeTimer() {
        if (finished) return
        if (remainingMs > 0L) startTimer(remainingMs)
    }

    private fun onTimeUp() {
        if (finished) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.times_up))
            .setMessage(getString(R.string.retry_btn) + "?")
            .setCancelable(false)
            .setPositiveButton(getString(R.string.retry_btn)) { d, _ ->
                d.dismiss()
                val i = newIntent(this, level.id)
                startActivity(i)
                finish()
            }
            .setNegativeButton(getString(R.string.pause_quit)) { d, _ ->
                d.dismiss()
                finish()
            }
            .show()
    }

    private fun onLevelComplete() {
        if (finished) return
        finished = true
        pauseTimer()
        // Compute the coin reward before crediting so we can pass the exact value to the
        // Complete screen for the count-up animation.
        val coinReward = Wallet.Rewards.coinsForLevel(remainingMs, boostersUsedThisLevel)
        // Detect player-XP level-up by comparing the player level before and after the credit
        // — surface this on the Complete screen so XP feels meaningful.
        val preXp = wallet.sun
        val postXp = preXp + level.targetSunReward
        val preLevel = Wallet.Xp.playerLevel(preXp)
        val postLevel = Wallet.Xp.playerLevel(postXp)
        val leveledUp = postLevel > preLevel
        // Star rating: 3 if ≥66% of time remains, 2 if ≥33%, else 1. Completing the level
        // always earns at least 1 star.
        val totalMs = roundDurationMs()
        val ratio = if (totalMs > 0) remainingMs.toFloat() / totalMs.toFloat() else 0f
        val stars = when {
            ratio >= 0.66f -> 3
            ratio >= 0.33f -> 2
            else -> 1
        }
        // Persist progress + reward
        try {
            Progress.get(this).markCompleted(level.id)
            Progress.get(this).setStars(level.id, stars)
            level.unlocksFeature?.let { Progress.get(this).unlockFeature(it) }
            wallet.addSun(level.targetSunReward)
            wallet.addCoins(coinReward)
        } catch (_: Throwable) { /* defensive */ }

        val intent = Intent().apply {
            setClassName(this@GameActivity, "com.datawaves.animalmatch.ui.complete.CompleteActivity")
            putExtra("level_id", level.id)
            putExtra("sun_reward", level.targetSunReward)
            putExtra("coin_reward", coinReward)
            putExtra("badge_id", level.badgeId)
            putExtra("leveled_up", leveledUp)
            putExtra("new_player_level", postLevel)
        }
        try {
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        } catch (_: Throwable) { /* CompleteActivity may not exist yet */ }
        finish()
    }

    private fun showInvalidToast() {
        safe {
            val tv = binding.invalidToast
            tv.text = getString(R.string.tiles_cant_be_connected)
            tv.visibility = View.VISIBLE
            tv.alpha = 0f
            val dp = resources.displayMetrics.density
            tv.translationY = -10f * dp
            tv.animate().cancel()
            tv.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .withEndAction {
                    tv.animate()
                        .alpha(0f)
                        .setStartDelay(900L)
                        .setDuration(220L)
                        .withEndAction {
                            tv.visibility = View.GONE
                            tv.alpha = 1f
                            tv.translationY = 0f
                        }
                        .start()
                }
                .start()
        }
    }

    private fun resolveColor(name: String, fallback: Int): Int {
        return try {
            val id = resources.getIdentifier(name, "color", packageName)
            if (id != 0) androidx.core.content.ContextCompat.getColor(this, id) else fallback
        } catch (_: Throwable) { fallback }
    }

    private var wasRunning = false

    override fun onPause() {
        super.onPause()
        if (countdown != null) {
            wasRunning = true
            countdown?.cancel()
            countdown = null
        }
        firePulseAnimator?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (wasRunning && !finished && remainingMs > 0L) {
            wasRunning = false
            startTimer(remainingMs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
        countdown = null
        firePulseAnimator?.cancel()
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: Throwable) { /* swallow missing views */ }
    }
}
