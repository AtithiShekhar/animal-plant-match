package com.datawaves.animalmatch.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-level completion progress. Level 1 is always unlocked. Level N is unlocked once N-1 is
 * completed. Also tracks which "features" have been unlocked (bomb at level 3, shuffle at
 * level 5 in our catalog) — used by the locked-feature banner on the map.
 */
class Progress private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE = "progress"
        private const val K_HIGHEST_COMPLETED = "highest_completed"
        private const val K_UNLOCKED_FEATURES = "unlocked_features"   // comma-separated
        private const val K_STARS_PREFIX = "stars_"                   // per-level star rating 0..3

        /** Hard cap so the play button never shows a level beyond the catalog. */
        const val MAX_LEVELS = 30

        @Volatile private var instance: Progress? = null
        fun get(context: Context): Progress = instance ?: synchronized(this) {
            instance ?: Progress(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }

    /** Highest level number the player has completed. 0 = none yet. */
    var highestCompleted: Int
        get() = prefs.getInt(K_HIGHEST_COMPLETED, 0)
        set(v) { prefs.edit().putInt(K_HIGHEST_COMPLETED, v).apply() }

    /** The level number the player would play next (highestCompleted + 1), capped at the total
     *  catalog size. Pass the actual total when known to avoid running off the end. */
    val currentLevel: Int get() = (highestCompleted + 1).coerceAtMost(MAX_LEVELS)

    fun currentLevel(totalLevels: Int): Int = (highestCompleted + 1).coerceAtMost(totalLevels)

    fun isCompleted(levelId: Int): Boolean = levelId <= highestCompleted
    fun isUnlocked(levelId: Int): Boolean = levelId <= highestCompleted + 1

    fun markCompleted(levelId: Int) {
        if (levelId > highestCompleted) highestCompleted = levelId
    }

    /** Star rating earned for a level, 0..3. 0 = not yet completed. */
    fun getStars(levelId: Int): Int = prefs.getInt(K_STARS_PREFIX + levelId, 0).coerceIn(0, 3)

    /** Persists the best star result for a level — never downgrades a prior better run. */
    fun setStars(levelId: Int, stars: Int) {
        val capped = stars.coerceIn(0, 3)
        val current = getStars(levelId)
        if (capped > current) prefs.edit().putInt(K_STARS_PREFIX + levelId, capped).apply()
    }

    private val features: HashSet<String> get() {
        val raw = prefs.getString(K_UNLOCKED_FEATURES, "") ?: ""
        return if (raw.isEmpty()) HashSet() else HashSet(raw.split(","))
    }

    fun unlockFeature(name: String) {
        val s = features
        if (s.add(name)) prefs.edit().putString(K_UNLOCKED_FEATURES, s.joinToString(",")).apply()
    }

    fun hasFeature(name: String): Boolean = features.contains(name)
}
