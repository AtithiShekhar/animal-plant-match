package com.datawaves.animalmatch

import android.app.Application
import com.datawaves.animalmatch.data.LevelRepository

/**
 * Application entry point. Warms up the level catalog on a background thread so the first
 * MapActivity render does not block on JSON parse.
 */
class AnimalMatchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Eagerly parse levels.json on a background thread so MapActivity has it instantly.
        Thread(
            {
                try {
                    LevelRepository.get(this)
                } catch (_: Throwable) {
                    // Swallow — MapActivity will re-attempt synchronously and surface any real error.
                }
            },
            "level-warmup"
        ).start()

        com.datawaves.animalmatch.util.Prefs.get(this)
        com.datawaves.animalmatch.util.SoundManager.init(this)
        com.datawaves.animalmatch.util.HapticsManager.init(this)
    }
}
