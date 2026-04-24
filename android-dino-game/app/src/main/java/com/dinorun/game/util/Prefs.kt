package com.dinorun.game.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences("dinorun_prefs", Context.MODE_PRIVATE)

    var lastPlayer: String
        get() = sp.getString(KEY_LAST_PLAYER, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_PLAYER, v).apply()

    var allTimeHi: Int
        get() = sp.getInt(KEY_ALL_TIME_HI, 0)
        set(v) = sp.edit().putInt(KEY_ALL_TIME_HI, v).apply()

    var darkMode: Boolean
        get() = sp.getBoolean(KEY_DARK, false)
        set(v) = sp.edit().putBoolean(KEY_DARK, v).apply()

    var soundEnabled: Boolean
        get() = sp.getBoolean(KEY_SOUND, true)
        set(v) = sp.edit().putBoolean(KEY_SOUND, v).apply()

    var vibrationEnabled: Boolean
        get() = sp.getBoolean(KEY_VIBRATION, true)
        set(v) = sp.edit().putBoolean(KEY_VIBRATION, v).apply()

    var barkEnabled: Boolean
        get() = sp.getBoolean(KEY_BARK, true)
        set(v) = sp.edit().putBoolean(KEY_BARK, v).apply()

    companion object {
        private const val KEY_LAST_PLAYER = "last_player"
        private const val KEY_ALL_TIME_HI = "all_time_hi"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_BARK = "bark_enabled"
    }
}
