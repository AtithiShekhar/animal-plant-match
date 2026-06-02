package com.datawaves.animalmatch.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent player wallet — sun (soft currency from levels), coins (premium-ish), and
 * power-up inventory counts. Backed by SharedPreferences so it survives process death.
 *
 * Starting balances match img.png: 1187 sun, 13 coins.
 */
class Wallet private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE = "wallet"
        private const val K_SUN = "sun"
        private const val K_COINS = "coins"
        private const val K_HINTS = "hints"
        private const val K_BOMBS = "bombs"
        private const val K_SHUFFLES = "shuffles"
        private const val K_INIT = "initialized"

        @Volatile private var instance: Wallet? = null
        fun get(context: Context): Wallet = instance ?: synchronized(this) {
            instance ?: Wallet(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
                .also { it.ensureSeeded(); instance = it }
        }
    }

    private fun ensureSeeded() {
        if (!prefs.getBoolean(K_INIT, false)) {
            prefs.edit()
                .putInt(K_SUN, 1187)
                // Enough coins for ~1 hint out of the gate so the shop is visible from
                // the first play session; further coins are earned by completing levels.
                .putInt(K_COINS, 50)
                .putInt(K_HINTS, 3)
                .putInt(K_BOMBS, 1)
                .putInt(K_SHUFFLES, 2)
                .putBoolean(K_INIT, true)
                .apply()
        }
    }

    /**
     * Sun doubles as player XP. Every level the player completes credits sun → that pushes the
     * player-XP-level bar in the hero section. XP needed per level is constant so the curve is
     * easy to reason about; level rewards are tuned so it takes ~3 levels to advance one tier.
     */
    object Xp {
        const val XP_PER_LEVEL = 400
        fun playerLevel(totalXp: Int): Int = (totalXp.coerceAtLeast(0) / XP_PER_LEVEL) + 1
        fun xpIntoCurrentLevel(totalXp: Int): Int = totalXp.coerceAtLeast(0) % XP_PER_LEVEL
    }

    object Rewards {
        /** Base coins per level completion. */
        const val COINS_BASE = 8
        /** Bonus coins when the player finishes without spending any hint/bomb/shuffle. */
        const val COINS_NO_HELP_BONUS = 5
        /** One bonus coin per this many remaining milliseconds, capped at [COINS_TIME_BONUS_MAX]. */
        const val COINS_TIME_BONUS_PER_MS = 6000L
        const val COINS_TIME_BONUS_MAX = 8

        /**
         * Compute the coin payout for a finished level. Time bonus encourages quick clears,
         * no-help bonus rewards skill over spending.
         */
        fun coinsForLevel(remainingMs: Long, boostersUsed: Boolean): Int {
            val timeBonus = (remainingMs / COINS_TIME_BONUS_PER_MS).toInt()
                .coerceIn(0, COINS_TIME_BONUS_MAX)
            val helpBonus = if (boostersUsed) 0 else COINS_NO_HELP_BONUS
            return COINS_BASE + timeBonus + helpBonus
        }
    }

    var sun: Int
        get() = prefs.getInt(K_SUN, 0)
        set(v) { prefs.edit().putInt(K_SUN, v).apply() }

    var coins: Int
        get() = prefs.getInt(K_COINS, 0)
        set(v) { prefs.edit().putInt(K_COINS, v).apply() }

    var hints: Int
        get() = prefs.getInt(K_HINTS, 0)
        set(v) { prefs.edit().putInt(K_HINTS, v).apply() }

    var bombs: Int
        get() = prefs.getInt(K_BOMBS, 0)
        set(v) { prefs.edit().putInt(K_BOMBS, v).apply() }

    var shuffles: Int
        get() = prefs.getInt(K_SHUFFLES, 0)
        set(v) { prefs.edit().putInt(K_SHUFFLES, v).apply() }

    fun addSun(n: Int) { sun = (sun + n).coerceAtLeast(0) }
    fun addCoins(n: Int) { coins = (coins + n).coerceAtLeast(0) }
    fun addHints(n: Int) { hints = (hints + n).coerceAtLeast(0) }
    fun addBombs(n: Int) { bombs = (bombs + n).coerceAtLeast(0) }
    fun addShuffles(n: Int) { shuffles = (shuffles + n).coerceAtLeast(0) }

    /** Returns true if the spend succeeded. */
    fun spendHint(): Boolean { if (hints <= 0) return false; hints -= 1; return true }
    fun spendBomb(): Boolean { if (bombs <= 0) return false; bombs -= 1; return true }
    fun spendShuffle(): Boolean { if (shuffles <= 0) return false; shuffles -= 1; return true }
    fun spendCoins(n: Int): Boolean { if (coins < n) return false; coins -= n; return true }
}
