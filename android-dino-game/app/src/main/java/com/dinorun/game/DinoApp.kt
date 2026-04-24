package com.dinorun.game

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.dinorun.game.data.AppDatabase
import com.dinorun.game.util.Prefs

class DinoApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    companion object {
        lateinit var instance: DinoApp
            private set
    }
}
