package com.datawaves.animalmatch.ui.map

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.LevelRepository
import com.datawaves.animalmatch.data.Progress
import com.datawaves.animalmatch.data.Wallet
import com.datawaves.animalmatch.databinding.ActivityMapBinding
import com.datawaves.animalmatch.ui.play.GameActivity
import com.datawaves.animalmatch.ui.shop.BoosterShopSheet
import com.datawaves.animalmatch.ui.start.LevelStartDialog

/**
 * Level-select map screen. Launcher activity. Shows the 15-level vertical map, currency HUD,
 * locked-feature banner, and a bottom "Play Level N" CTA.
 */
class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_OPEN_LEVEL = "auto_open_level"
    }

    private lateinit var binding: ActivityMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        // Draw under the system bars so we can apply our own inset-aware padding —
        // the HUD strip sits below the status bar and the Play CTA above the nav bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        val catalog = LevelRepository.get(this)
        val progress = Progress.get(this)

        binding.mapView.bind(catalog.levels, progress)
        binding.mapView.listener = LevelMapView.OnNodeClickListener { levelId ->
            handleNodeClick(levelId)
        }

        binding.playBtn?.setOnClickListener {
            openStartDialog(progress.currentLevel)
        }
        binding.settingsBtn?.setOnClickListener {
            com.datawaves.animalmatch.ui.settings.SettingsBottomSheet()
                .show(supportFragmentManager, "settings")
        }
        binding.buyBtn?.setOnClickListener {
            // Open the booster shop so players can stock up on hints / bombs / shuffles.
            val sheet = BoosterShopSheet.newInstance(null)
            sheet.onWalletChanged = { refreshHud() }
            sheet.show(supportFragmentManager, "shop")
        }

        // Auto-open a level if we were brought back here from CompleteActivity.
        val auto = intent?.getIntExtra(EXTRA_AUTO_OPEN_LEVEL, 0) ?: 0
        if (auto > 0) {
            window.decorView.postDelayed({
                if (!isFinishing && Progress.get(this).isUnlocked(auto)) {
                    openStartDialog(auto)
                }
            }, 200L)
        }
    }

    /**
     * Apply system-bar insets so the HUD sits below the status bar and the bottom Play CTA
     * sits above the navigation bar. Also reserves enough scroll padding so Level 1
     * (the bottom node on the map) is never hidden behind the Play CTA.
     */
    private fun applyWindowInsets() {
        val density = resources.displayMetrics.density
        val hudStrip = binding.hudStrip
        val heroSection = binding.heroSection
        val lockedBanner = binding.lockedBanner
        val playBtn = binding.playBtn
        val mapScroll = findViewById<View>(R.id.mapScroll)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topInset = bars.top
            val bottomInset = bars.bottom

            hudStrip?.updatePadding(top = topInset + (4f * density).toInt())
            hudStrip?.requestLayout()

            // Position heroSection and lockedBanner relative to the inset-aware HUD.
            val hudHeightPx = resources.getDimensionPixelSize(R.dimen.hud_height)
            val heroParams = heroSection?.layoutParams as? FrameLayout.LayoutParams
            heroParams?.topMargin = topInset + hudHeightPx + (12f * density).toInt()
            heroSection?.layoutParams = heroParams

            val bannerParams = lockedBanner?.layoutParams as? FrameLayout.LayoutParams
            bannerParams?.topMargin = topInset + hudHeightPx + (160f * density).toInt()
            lockedBanner?.layoutParams = bannerParams

            // Bottom Play CTA sits above the nav bar.
            val playParams = playBtn?.layoutParams as? FrameLayout.LayoutParams
            val baseBottomDp = (24f * density).toInt()
            playParams?.bottomMargin = baseBottomDp + bottomInset
            playBtn?.layoutParams = playParams

            // Scroll content needs headroom so Level 1 isn't hidden behind the CTA.
            // Reserve: status bar + HUD + hero block at top, Play CTA + gap + nav bar at bottom.
            mapScroll?.updatePadding(
                top = topInset + hudHeightPx + (190f * density).toInt(),
                bottom = (56f * density).toInt() + baseBottomDp * 2 + bottomInset + (24f * density).toInt()
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val auto = intent.getIntExtra(EXTRA_AUTO_OPEN_LEVEL, 0)
        if (auto > 0) {
            window.decorView.postDelayed({
                if (!isFinishing && Progress.get(this).isUnlocked(auto)) {
                    openStartDialog(auto)
                }
            }, 200L)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHud()
        refreshLockedBanner()
        refreshPlayButton()
        // Re-bind the map so any newly-completed level + earned stars render immediately.
        val catalog = LevelRepository.get(this)
        binding.mapView.bind(catalog.levels, Progress.get(this))
    }

    private fun refreshHud() {
        val wallet = Wallet.get(this)
        binding.coinChip?.text = wallet.coins.toString()

        // Sun chip now shows the player's XP level — total sun is the XP fuel behind the curtain.
        val playerLevel = Wallet.Xp.playerLevel(wallet.sun)
        val xpInto = Wallet.Xp.xpIntoCurrentLevel(wallet.sun)
        binding.sunChip?.text = getString(R.string.xp_chip_fmt, playerLevel)

        // Hero XP detail row + progress bar.
        findViewById<android.widget.TextView>(R.id.heroXpLabel)?.text =
            getString(R.string.xp_hero_fmt, playerLevel, xpInto, Wallet.Xp.XP_PER_LEVEL)
        findViewById<android.widget.ProgressBar>(R.id.heroProgress)?.let { pb ->
            pb.max = Wallet.Xp.XP_PER_LEVEL
            pb.progress = xpInto
        }
    }

    private fun refreshLockedBanner() {
        val banner = binding.lockedBanner ?: return
        val catalog = LevelRepository.get(this)
        val progress = Progress.get(this)
        val nextLocked = catalog.levels.firstOrNull {
            it.id > progress.highestCompleted && it.unlocksFeature != null
        }
        if (nextLocked != null) {
            banner.text = getString(R.string.locked_banner_fmt, nextLocked.id)
            banner.visibility = View.VISIBLE
        } else {
            banner.visibility = View.GONE
        }
    }

    private fun refreshPlayButton() {
        val progress = Progress.get(this)
        binding.playBtn?.text = getString(R.string.play_level_fmt, progress.currentLevel)
    }

    private fun handleNodeClick(levelId: Int) {
        val progress = Progress.get(this)
        if (progress.isUnlocked(levelId)) {
            openStartDialog(levelId)
        } else {
            // Visual feedback: rattle the padlock on the tapped node + toast.
            binding.mapView.rattleLocked(levelId)
            val needed = (progress.highestCompleted + 1).coerceAtLeast(1)
            Toast.makeText(
                this,
                getString(R.string.locked_toast_fmt, needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openStartDialog(levelId: Int) {
        // Defensive: never open a locked level.
        if (!Progress.get(this).isUnlocked(levelId)) return
        if (supportFragmentManager.findFragmentByTag("start") != null) return
        LevelStartDialog.newInstance(levelId).show(supportFragmentManager, "start")
    }

    // Exposed for tests/other UI paths if needed in future. Kept here to keep references explicit.
    @Suppress("unused")
    private fun openGame(levelId: Int) {
        startActivity(GameActivity.newIntent(this, levelId))
    }
}
