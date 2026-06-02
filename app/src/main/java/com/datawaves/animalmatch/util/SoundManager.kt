package com.datawaves.animalmatch.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundManager {
    private const val VOLUME = 0.6f
    private const val MAX_STREAMS = 4

    private var pool: SoundPool? = null
    private val soundIds: MutableMap<String, Int> = mutableMapOf()
    private var enabled: Boolean = true

    private val sfxNames = listOf(
        "sfx_tap",
        "sfx_select",
        "sfx_match",
        "sfx_invalid",
        "sfx_win",
        "sfx_hint",
        "sfx_bomb",
        "sfx_shuffle"
    )

    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        enabled = Prefs.get(app).soundEnabled

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sp = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attrs)
            .build()

        val pkg = app.packageName
        val res = app.resources
        for (name in sfxNames) {
            val resId = res.getIdentifier(name, "raw", pkg)
            if (resId != 0) {
                try {
                    soundIds[name] = sp.load(app, resId, 1)
                } catch (_: Throwable) {
                    // ignore — keep silent
                }
            }
        }
        pool = sp
    }

    fun setEnabled(b: Boolean) { enabled = b }

    private fun play(name: String) {
        if (!enabled) return
        val sp = pool ?: return
        val id = soundIds[name] ?: return
        try {
            sp.play(id, VOLUME, VOLUME, 1, 0, 1f)
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun playTap() = play("sfx_tap")
    fun playSelect() = play("sfx_select")
    fun playMatch() = play("sfx_match")
    fun playInvalid() = play("sfx_invalid")
    fun playWin() = play("sfx_win")
    fun playHint() = play("sfx_hint")
    fun playBomb() = play("sfx_bomb")
    fun playShuffle() = play("sfx_shuffle")

    fun release() {
        try {
            pool?.release()
        } catch (_: Throwable) {
            // ignore
        }
        pool = null
        soundIds.clear()
    }
}
