package com.datawaves.animalmatch.util

import android.content.Context
import android.content.SharedPreferences

class Prefs private constructor(private val prefs: SharedPreferences) {
    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) { prefs.edit().putBoolean("sound", v).apply() }
    var musicEnabled: Boolean
        get() = prefs.getBoolean("music", true)
        set(v) { prefs.edit().putBoolean("music", v).apply() }
    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) { prefs.edit().putBoolean("haptics", v).apply() }
    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}
